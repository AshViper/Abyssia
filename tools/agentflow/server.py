#!/usr/bin/env python3
"""Serve the agentflow app. Usage: server.py [port]  (default 8765). Stdlib only, listens on 127.0.0.1.

Endpoints: state.json, requests.json, approvals.json, textures.json, autorun.json, /tex/(cur|old)/<kind>/<name>.png,
POST /api/request | /api/approval | /api/reset | /api/autorun | /api/autorun/run | /api/autorun/stop.

Auto-run: a new request starts the selected headless runner in the project root. Set
`runner` to `codex` or `claude` in tools/agentflow/autorun.json (or use the UI).
"""
import hashlib, http.server, json, os, shutil, subprocess, sys, threading, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import flow  # noqa: E402  (state file helpers shared with the CLI)

STATE = flow.STATE
REQ = flow.REQ
APR = flow.APR
CFG = HERE / "autorun.json"
RUNLOG = ROOT / "inbox" / "flow" / "autorun.log"
TEX = ROOT / "src" / "main" / "resources" / "assets" / "abyssia" / "textures"
OLD = ROOT / "inbox" / "backup" / "textures-20260930"
LOCKS = ROOT / "tools" / "texture_locks" / "assets" / "textures"
CHATGPT = ROOT / "inbox" / "textures"

DEFAULT_CFG = {
    "enabled": False,  # unattended `claude -p` runs stay off until the user turns them on (checkbox in the UI or here)
    "runner": "claude",
    "codex_sandbox": "workspace-write",
    "codex_approval": "never",
    "codex_model": "",
    "max_workers": 3,
    "model": "sonnet",
    "permission_mode": "acceptEdits",
    "allowed_tools": ["Read", "Write", "Edit", "Glob", "Grep", "Bash", "Agent", "Skill", "ToolSearch",
                      "mcp__claude-in-chrome__*"],
    "prompt": ("inbox/requests の status=new の依頼を処理して。手順は CLAUDE.md、inbox/README.md、"
               "Vault project/decisions/autonomous-request-pipeline.md に従い、確認の質問はせず自律的に進める"
               "(破壊的操作・課金・セキュリティ影響のみ `python tools/agentflow/flow.py ask` でサイト承認を取る)。"
               "進行は flow.py で記録し、全て終わったら `python tools/agentflow/flow.py finish` を実行して待機に戻す。"),
    "max_reruns": 3,
}


def load_cfg():
    if not CFG.exists():
        CFG.write_text(json.dumps(DEFAULT_CFG, ensure_ascii=False, indent=2), encoding="utf-8")
    try:
        return {**DEFAULT_CFG, **json.loads(CFG.read_text(encoding="utf-8"))}
    except json.JSONDecodeError:
        return dict(DEFAULT_CFG)


def read_json_dir(d, limit=None):
    out = []
    for f in (sorted(d.glob("*.json")) if d.exists() else [])[-(limit or 10**6):]:
        try:
            out.append(json.loads(f.read_text(encoding="utf-8")))
        except json.JSONDecodeError:
            pass
    return out


class AutoRun:
    def __init__(self):
        self.lock = threading.Lock()
        self.proc = None
        self.workers = {}
        self.started = None
        self.last_exit = None
        self.last_end = None
        self.thread = None
        self.again = False
        self.had_workers = False

    def pending(self):
        return sum(1 for r in read_json_dir(REQ) if r.get("status") == "new")

    def pending_records(self):
        return [r for r in read_json_dir(REQ) if r.get("status") == "new"]

    @staticmethod
    def claims(request):
        raw = str(request.get("files", "")).strip()
        if not raw:
            return {"*"}
        return {part.strip().replace("\\", "/").rstrip("/") for part in raw.split(",") if part.strip()}

    @staticmethod
    def conflicts(left, right):
        if "*" in left or "*" in right:
            return True
        for a in left:
            for b in right:
                a, b = a.removesuffix("/**"), b.removesuffix("/**")
                if a == b or a.startswith(b + "/") or b.startswith(a + "/"):
                    return True
        return False

    def status(self):
        cfg = load_cfg()
        tail = []
        try:
            tail = RUNLOG.read_text(encoding="utf-8", errors="replace").splitlines()[-14:]
        except OSError:
            pass
        runner = cfg.get("runner", "claude")
        active = {rid: p for rid, p in self.workers.items() if p.poll() is None}
        return {"enabled": cfg["enabled"], "running": bool(active), "workers": len(active),
                "started": self.started, "last_exit": self.last_exit, "last_end": self.last_end,
                "pending": self.pending(), "runner": runner, "model": cfg["model"],
                "claude": bool(shutil.which("claude")), "codex": bool(shutil.which("codex")), "tail": tail}

    def trigger(self, force=False):
        cfg = load_cfg()
        if not (cfg["enabled"] or force):
            return
        with self.lock:
            if self.thread and self.thread.is_alive():
                self.again = True  # the running pass re-checks for new requests when it finishes
                return
            self.had_workers = False
            self.thread = threading.Thread(target=self._loop, daemon=True)
            self.thread.start()

    def stop(self):
        for proc in self.workers.values():
            if proc.poll() is None:
                proc.terminate()

    def _loop(self):
        while True:
            self._reap_workers()
            pending = self.pending_records()
            if not pending and not self.workers:
                if self.had_workers:
                    self._verify_and_build()
                break
            cfg = load_cfg()
            used = [self.claims(r) for rid, r in self._worker_requests().items()
                    if rid in self.workers and self.workers[rid].poll() is None]
            slots = max(0, int(cfg.get("max_workers", 3)) - len(self.workers))
            for request in pending:
                if not slots:
                    break
                claim = self.claims(request)
                if any(self.conflicts(claim, other) for other in used):
                    continue
                self._start_worker(request)
                used.append(claim)
                slots -= 1
            time.sleep(0.5)

    def _worker_requests(self):
        out = {}
        for path in REQ.glob("*.json"):
            try:
                record = json.loads(path.read_text(encoding="utf-8"))
                out[record.get("id")] = record
            except (OSError, json.JSONDecodeError):
                pass
        return out

    def _reap_workers(self):
        for rid, proc in list(self.workers.items()):
            result = proc.poll()
            if result is None:
                continue
            self.last_exit, self.last_end = result, time.time()
            st = flow.load()
            flow.logev(st, rid, f"Codex worker 終了 (exit={result})", "status" if result == 0 else "error", "codex")
            flow.save(st)
            del self.workers[rid]

    def _start_worker(self, request):
        cfg = load_cfg()
        runner = cfg.get("runner", "claude")
        exe = shutil.which(runner)
        rid = request["id"]
        if not exe:
            return
        if runner != "codex":
            return self._run_once()
        cmd = [exe, "--sandbox", cfg.get("codex_sandbox", "workspace-write"),
               "--ask-for-approval", cfg.get("codex_approval", "never"),
               "exec", "-C", str(ROOT), "--skip-git-repo-check"]
        if cfg.get("codex_model"):
            cmd.extend(["--model", cfg["codex_model"]])
        request_path = REQ / f"{rid}.json"
        request["status"] = "running"
        request_path.write_text(json.dumps(request, ensure_ascii=False, indent=1), encoding="utf-8")
        st = flow.load()
        flow.logev(st, rid, "Codex worker を開始", "tool", "codex")
        flow.save(st)
        prompt = (cfg["prompt"] + f"\n\n対象は依頼 {rid} のみ。{request_path.as_posix()} を読み、"
                  "その依頼だけを処理する。他の worker の編集対象には触れない。")
        RUNLOG.parent.mkdir(parents=True, exist_ok=True)
        with open(RUNLOG, "a", encoding="utf-8") as log:
            log.write(f"\n=== {time.strftime('%F %T')} worker {rid} start ===\n")
            log.flush()
            proc = subprocess.Popen(cmd, cwd=str(ROOT), stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            proc.stdin.write(prompt.encode("utf-8")); proc.stdin.close()
        self.proc = proc
        self.workers[rid] = proc
        self.had_workers = True
        self.started, self.last_exit = time.time(), None

    @staticmethod
    def _set_stage(stage, status, note):
        st = flow.load()
        st["stages"][stage] = {"status": status, "note": note, "t": time.time()}
        flow.logev(st, stage, note, "status")
        flow.save(st)

    def _verify_and_build(self):
        """Run only after every worker in the current batch has exited."""
        self._set_stage("verify", "running", "生成スクリプトの構文を検証中")
        RUNLOG.parent.mkdir(parents=True, exist_ok=True)
        with open(RUNLOG, "a", encoding="utf-8") as log:
            log.write(f"\n=== {time.strftime('%F %T')} verification start ===\n")
            verify = subprocess.run([sys.executable, "-m", "py_compile",
                                     "tools/gen_deep_assets.py", "tools/mineral_textures.py",
                                     "tools/agentflow/server.py"], cwd=str(ROOT), stdout=log,
                                    stderr=subprocess.STDOUT, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            log.write(f"verification exit={verify.returncode}\n")
            if verify.returncode:
                self._set_stage("verify", "failed", "構文検証に失敗")
                self._set_stage("build", "failed", "検証失敗のため未実行")
                return
            self._set_stage("verify", "done", "生成スクリプトの構文検証を通過")
            self._set_stage("build", "running", "Gradle build を実行中")
            log.write(f"\n=== {time.strftime('%F %T')} gradle build start ===\n")
            build = subprocess.run([str(ROOT / "gradlew.bat"), "build", "--no-daemon"], cwd=str(ROOT), stdout=log,
                                   stderr=subprocess.STDOUT, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            log.write(f"gradle build exit={build.returncode}\n")
        if build.returncode:
            self._set_stage("build", "failed", "Gradle build に失敗。ログを確認")
        else:
            self._set_stage("build", "done", "Gradle build を通過")

    def _run_once(self):
        cfg = load_cfg()
        runner = cfg.get("runner", "claude")
        exe = shutil.which(runner)
        RUNLOG.parent.mkdir(parents=True, exist_ok=True)
        st = flow.load()
        if not exe:
            flow.logev(st, "main", f"自動実行できません: {runner} CLI が見つかりません", "error"); flow.save(st)
            return
        if runner == "codex":
            cmd = [exe, "--sandbox", cfg.get("codex_sandbox", "workspace-write"),
                   "--ask-for-approval", cfg.get("codex_approval", "never"),
                   "exec", "-C", str(ROOT), "--skip-git-repo-check"]
            if cfg.get("codex_model"):
                cmd.extend(["--model", cfg["codex_model"]])
        else:
            cmd = [exe, "-p", "--model", cfg["model"], "--permission-mode", cfg["permission_mode"],
                   "--allowedTools", *cfg["allowed_tools"]]
        env = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE_CODE") and k != "CLAUDECODE"}
        flow.logev(st, "main", f"自動実行を開始 (runner={runner}, 依頼 {self.pending()} 件)", "tool", cfg.get("codex_model") or cfg["model"])
        st["stages"]["router"] = {"status": "running", "note": "自動実行を起動", "t": time.time()}
        flow.save(st)
        self.started, self.last_exit = time.time(), None
        with open(RUNLOG, "a", encoding="utf-8") as lg:
            lg.write(f"\n=== {time.strftime('%F %T')} autorun start ===\n")
            lg.flush()
            self.proc = subprocess.Popen(cmd, cwd=str(ROOT), env=env, stdin=subprocess.PIPE, stdout=lg,
                                         stderr=subprocess.STDOUT, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            try:
                self.proc.stdin.write(cfg["prompt"].encode("utf-8"))
                self.proc.stdin.close()
            except OSError:
                pass
            self.last_exit = self.proc.wait()
        self.last_end = time.time()
        st = flow.load()
        flow.logev(st, "main", f"自動実行が終了 (exit={self.last_exit})", "status" if self.last_exit == 0 else "error")
        flow.save(st)


AUTO = AutoRun()
_tex_cache = {"t": 0, "v": []}


def md5(p):
    return hashlib.md5(p.read_bytes()).hexdigest()


def textures():
    if time.time() - _tex_cache["t"] < 8:
        return _tex_cache["v"]
    ai = {p.stem for p in CHATGPT.glob("*.png")}
    out = []
    for kind in ("block", "item", "entity"):
        base = TEX / kind
        for p in sorted(base.rglob("*.png")) if base.exists() else []:
            rel = p.relative_to(TEX).as_posix()
            old = OLD / rel
            status = "new" if not old.exists() else ("same" if md5(old) == md5(p) else "changed")
            out.append({"path": rel, "kind": kind, "name": p.stem, "status": status,
                        "locked": (LOCKS / rel).exists(), "chatgpt": kind == "block" and p.stem in ai})
    _tex_cache.update(t=time.time(), v=out)
    return out


class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=str(HERE), **k)

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self, limit=100_000):
        n = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(min(n, limit)) or b"{}")

    def do_POST(self):
        try:
            d = self._body()
        except (ValueError, json.JSONDecodeError):
            return self._json(400, {"error": "bad json"})
        path = self.path.split("?")[0]
        if path == "/api/approval":
            try:
                f = APR / (Path(str(d["id"])).name + ".json")  # name only: no path traversal
                a = json.loads(f.read_text(encoding="utf-8"))
            except (KeyError, OSError, json.JSONDecodeError):
                return self._json(400, {"error": "bad request"})
            if a.get("status") != "pending" or d.get("answer") not in ("approve", "deny"):
                return self._json(409, {"error": "not pending or bad answer"})
            a["status"] = "approved" if d["answer"] == "approve" else "denied"
            a["answered"] = time.time()
            f.write_text(json.dumps(a, ensure_ascii=False, indent=1), encoding="utf-8")
            return self._json(200, a)
        if path == "/api/reset":
            st = flow.fresh()
            st["log"] = flow.load().get("log", [])[-60:]
            flow.logev(st, "main", "サイトからリセット (全ステージ待機)", "status")
            flow.save(st)
            return self._json(200, {"ok": True})
        if path == "/api/autorun":
            cfg = load_cfg()
            if "enabled" in d:
                cfg["enabled"] = bool(d["enabled"])
            if d.get("runner") in ("claude", "codex"):
                cfg["runner"] = d["runner"]
            CFG.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
            if cfg["enabled"]:
                AUTO.trigger()
            return self._json(200, AUTO.status())
        if path == "/api/autorun/run":
            AUTO.trigger(force=True)
            return self._json(200, AUTO.status())
        if path == "/api/autorun/stop":
            AUTO.stop()
            return self._json(200, AUTO.status())
        if path != "/api/request":
            return self._json(404, {"error": "not found"})
        title, body = str(d.get("title", "")).strip(), str(d.get("body", "")).strip()
        if not title:
            return self._json(400, {"error": "title required"})
        REQ.mkdir(parents=True, exist_ok=True)
        rid = time.strftime("%Y%m%d-%H%M%S")
        while (REQ / f"{rid}.json").exists():
            rid += "x"
        r = {"id": rid, "title": title, "body": body, "files": str(d.get("files", "")).strip(),
             "tier": d.get("tier") if d.get("tier") in ("auto", "light", "standard", "heavy") else "auto",
             "via": d.get("via") if d.get("via") in ("claude", "codex", "chatgpt") else "claude",
             "status": "new", "created": time.time()}
        (REQ / f"{rid}.json").write_text(json.dumps(r, ensure_ascii=False, indent=1), encoding="utf-8")
        st = flow.load()
        st["stages"]["request"] = {"status": "done", "note": title, "t": time.time()}
        flow.logev(st, "request", f"依頼を受信: {title}", "add")
        flow.save(st)
        AUTO.trigger()
        self._json(200, {**r, "autorun": AUTO.status()})

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/requests.json", "/approvals.json"):
            return self._json(200, read_json_dir(REQ if path.startswith("/req") else APR, 40))
        if path == "/autorun.json":
            return self._json(200, AUTO.status())
        if path == "/textures.json":
            return self._json(200, textures())
        if path.startswith("/tex/"):
            which, _, rel = path[5:].partition("/")
            base = {"cur": TEX, "old": OLD}.get(which)
            f = (base / rel).resolve() if base else None
            if not f or base.resolve() not in f.parents or f.suffix != ".png" or not f.is_file():
                return self._json(404, {"error": "not found"})
            body = f.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Cache-Control", "max-age=5")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return self.wfile.write(body)
        if path == "/state.json":
            try:
                body = STATE.read_bytes()
            except FileNotFoundError:
                body = json.dumps(flow.fresh()).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return self.wfile.write(body)
        super().do_GET()

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    load_cfg()
    print(f"agentflow: http://127.0.0.1:{port}/  (autorun {'on' if load_cfg()['enabled'] else 'off'})", flush=True)
    http.server.ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
