#!/usr/bin/env python3
"""Serve the agentflow app. Usage: server.py [port]  (default 8765). Stdlib only, listens on 127.0.0.1.

Endpoints: state.json, requests.json, approvals.json, textures.json, autorun.json, /tex/(cur|old)/<kind>/<name>.png,
POST /api/request | /api/approval | /api/reset | /api/autorun | /api/autorun/run | /api/autorun/stop.
Sheets (画像生成 tab, same functions as the sheets.py CLI): GET sheets.json | /api/sheet/prompt?name= |
/api/sheet/image?name=; POST /api/sheet/upload?name= (raw image body, max 25MB) | /api/sheet/detect |
/api/sheet/import | /api/sheet/undo (JSON bodies: name, region [x0,y0,x1,y1], merge, ids, dry_run).
Texture generator (テクスチャ生成 tab, same functions as tools/texture_gen.py): GET /api/texgen/meta |
/api/texgen/sources | /api/texgen/candidates?batch= | /api/texgen/image?batch=&name= | /api/texgen/contact?batch=;
POST /api/texgen/preview {gen, params, seeds} | /api/texgen/add {gen, params, seeds, batch} |
/api/texgen/apply {items:[{batch,name,as?}], lock, force_locked, dry_run} | /api/texgen/undo {dry_run} |
/api/texgen/profile {category}.

Auto-run: a new request starts a headless `claude -p` in the project root (tools/agentflow/autorun.json).
"""
import hashlib, http.server, json, os, re, shutil, subprocess, sys, threading, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(1, str(HERE.parent))  # tools/: texture_gen (imported lazily, needs numpy/pillow)
import flow  # noqa: E402
import hook  # noqa: E402  (stage verdicts shared with the hook)  (state file helpers shared with the CLI)
import sheets  # noqa: E402  (sheet presets / detect / import shared with the CLI)
from urllib.parse import parse_qs, urlsplit  # noqa: E402

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
    "max_workers": 3,
    "model": "opus",
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
        active = {rid: p for rid, p in self.workers.items() if p.poll() is None}
        return {"enabled": cfg["enabled"], "running": bool(active), "workers": len(active),
                "started": self.started, "last_exit": self.last_exit, "last_end": self.last_end,
                "pending": self.pending(), "model": cfg["model"],
                "claude": bool(shutil.which("claude")), "tail": tail}

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
            flow.logev(st, rid, f"worker 終了 (exit={result})", "status" if result == 0 else "error", "claude")
            flow.save(st)
            del self.workers[rid]

    def _start_worker(self, request):
        if not shutil.which("claude"):
            return
        return self._run_once()

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
        exe = shutil.which("claude")
        RUNLOG.parent.mkdir(parents=True, exist_ok=True)
        st = flow.load()
        if not exe:
            flow.logev(st, "main", "自動実行できません: claude CLI が見つかりません", "error"); flow.save(st)
            return
        cmd = [exe, "-p", "--model", cfg["model"], "--permission-mode", cfg["permission_mode"],
               "--allowedTools", *cfg["allowed_tools"]]
        env = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE_CODE") and k != "CLAUDECODE"}
        flow.logev(st, "main", f"自動実行を開始 (claude, 依頼 {self.pending()} 件)", "tool", cfg["model"])
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


LIVE = Path(os.environ.get("AGENTFLOW_LIVE", ROOT / "inbox" / "flow" / "live.jsonl"))  # written by hook.py
AGENT_DEFS = ROOT / ".claude" / "agents"


def agent_models():
    """subagent_type -> model, from .claude/agents/*.md frontmatter (built-in types inherit the main model)."""
    out = {}
    for f in AGENT_DEFS.glob("*.md") if AGENT_DEFS.exists() else []:
        for line in f.read_text(encoding="utf-8").splitlines()[1:12]:
            if line.startswith("model:"):
                out[f.stem] = line.split(":", 1)[1].strip().replace("claude-", "").replace("-5-5", "-5.5")
            if line.strip() == "---":
                break
    return out


_repo_cache = {}


def repo_info(cwd, ttl=30):
    """{repo, branch} for a session's cwd (git toplevel folder name + current branch), cached."""
    hit = _repo_cache.get(cwd)
    if hit and time.time() - hit[0] < ttl:
        return hit[1]
    info = {"repo": Path(cwd).name, "branch": ""}
    try:
        out = subprocess.run(["git", "-C", cwd, "rev-parse", "--show-toplevel", "--abbrev-ref", "HEAD"],
                             capture_output=True, text=True, timeout=3).stdout.split()
        if len(out) >= 2:
            info = {"repo": Path(out[0]).name, "branch": out[1] if out[1] != "HEAD" else "detached"}
    except (OSError, subprocess.SubprocessError):
        pass
    _repo_cache[cwd] = (time.time(), info)
    return info


STAGE_NOTE = {"build": "gradle: ", "git": "", "memory": "Obsidian: ", "texture": "", "chatgpt": "ChatGPT: ", "verify": "verify: "}


def merged_state():
    """state.json with the hook-detected stages laid over it (whichever was updated last wins)."""
    st = flow.load()
    for k, v in live().get("stages", {}).items():
        cur = st["stages"].setdefault(k, {"status": "idle", "note": ""})
        if v.get("t", 0) > cur.get("t", 0):
            st["stages"][k] = v
            st["updated"] = max(st.get("updated", 0), v["t"])
    return st


def live(window=1800, lines=4000):
    """Fold hook.py events into: agents (who is working where), msgs (agent <-> agent hand-offs), events (tail)."""
    try:
        raw = LIVE.read_bytes().splitlines()[-lines:]
    except FileNotFoundError:
        raw = []
    now, models = time.time(), agent_models()
    main_model = flow.load().get("main", {}).get("model") or "opus-5.5"
    agents, msgs, events, pending, by_tid, stages = {}, [], [], [], {}, {}

    def ag(e):
        a = agents.get(e["who"])
        if not a:
            typ = e.get("type") or ("main" if e["who"].startswith("main:") else "fork")  # agent_id without a type = fork
            a = agents[e["who"]] = {"id": e["who"], "type": typ, "sid": e.get("sid", ""), "parent": "", "desc": "",
                                    "model": main_model if typ == "main" else models.get(typ, main_model),
                                    "status": "running", "tool": "", "target": "", "started": e["t"], "t": e["t"],
                                    "ended": None, "calls": 0, "files": [], "err": 0, "cwd": "", "topic": "", "ask": "", "asked": None}
        a["t"] = e["t"]
        return a

    for ln in raw:
        try:
            e = json.loads(ln)
        except ValueError:
            continue
        ev = e.get("ev")
        a = ag(e)
        if e.get("cwd"):
            a["cwd"] = e["cwd"]
        if ev == "PreToolUse":
            a.update(status="running", tool=e.get("tool", ""), target=e.get("target", ""), ended=None)
            a["calls"] += 1
            tg = e.get("target", "")
            if tg and e.get("tool") in ("Read", "Edit", "Write", "MultiEdit", "NotebookEdit", "Grep", "Glob"):
                a["files"] = ([tg] + [f for f in a["files"] if f != tg])[:8]
            if e.get("tool") in ("Agent", "Task"):
                m = {"t": e["t"], "from": e["who"], "to": "", "to_type": e.get("sub", ""), "kind": "task",
                     "desc": e.get("desc", ""), "text": e.get("msg", ""), "tid": e.get("tid", "")}
                msgs.append(m)
                pending.append(m)
            elif e.get("tool") == "SendMessage":
                msgs.append({"t": e["t"], "from": e["who"], "to": e.get("to", ""), "kind": "send", "text": e.get("msg", "")})
        elif ev == "PostToolUse":
            if e.get("err"):
                a["err"] += 1
            if e.get("tool") in ("Agent", "Task"):
                sub = by_tid.get(e.get("tid", ""), "")
                msgs.append({"t": e["t"], "from": sub or e.get("sub", "subagent"), "to": e["who"], "kind": "result",
                             "desc": e.get("desc", ""), "text": e.get("msg", "")})
                if sub in agents:
                    agents[sub].update(status="done", ended=agents[sub]["ended"] or e["t"])
        elif ev == "SubagentStart":
            m = next((p for p in pending if p["to_type"] == a["type"] and not p["to"]), None) \
                or next((p for p in pending if not p["to"]), None)
            if m:
                m["to"] = a["id"]
                pending.remove(m)
                a.update(parent=m["from"], desc=m["desc"])
                by_tid[m["tid"]] = a["id"]
            a.update(status="running", started=e["t"])
        elif ev == "SubagentStop":
            a.update(status="done", ended=e["t"], tool="", target=a["target"])
            if a["type"] == "verify":  # the verify agent's final answer carries PASS / FAIL
                ok = hook.verdict("verify", e.get("msg", ""))
                stages["verify"] = {"status": "failed" if ok is False else "done", "t": e["t"], "who": a["id"], "auto": True,
                                    "note": ("verify: " + (a["desc"] or "")) + (" — FAIL" if ok is False else " — PASS" if ok else "")}
        elif ev == "Stop":
            a.update(status="idle", tool="")
        elif ev == "UserPromptSubmit":
            a.update(status="running")
            txt = e.get("msg", "")
            src = re.search(r'<agent-message from="([^"]+)"', txt) or re.search(r"<task-id>([^<]+)</task-id>", txt)
            if src and (src.group(1) in agents or "agent-message" in txt):  # background subagent reporting back
                sub = src.group(1)
                if sub in agents:
                    agents[sub].update(status="done", ended=agents[sub]["ended"] or e["t"])
                msgs.append({"t": e["t"], "from": sub, "to": e["who"], "kind": "result", "desc": "", "text": txt})
            else:
                msgs.append({"t": e["t"], "from": "user", "to": e["who"], "kind": "prompt", "text": txt})
                if txt and not txt.lstrip().startswith("<"):  # what the user asked this session (not system notices)
                    a["topic"] = a["topic"] or txt
                    a.update(ask=txt, asked=e["t"])
        st = e.get("stage")
        if st and ev in ("PreToolUse", "PostToolUse"):
            cur = stages.get(st, {})
            if ev == "PreToolUse":
                stages[st] = {"status": "running", "note": STAGE_NOTE.get(st, "") + (e.get("target") or "")[:90],
                              "t": e["t"], "who": e["who"], "auto": True}
            elif e.get("ok") is False:
                stages[st] = {**cur, "status": "failed", "note": (e.get("out") or cur.get("note", ""))[-120:], "t": e["t"], "auto": True}
            elif st in ("build", "git", "verify") and not e.get("bg"):
                stages[st] = {**cur, "status": "done", "t": e["t"], "auto": True}
            else:
                stages[st] = {**cur, "t": e["t"], "auto": True}  # chatgpt / texture / memory: still busy, decays below
        events.append(e)

    for a in agents.values():  # a background verify agent outlives its Agent tool call
        if a["type"] == "verify" and a["status"] == "running" and a["t"] >= stages.get("verify", {}).get("t", 0) - 1:
            stages["verify"] = {"status": "running", "t": a["t"], "who": a["id"], "auto": True, "note": "verify: " + (a["desc"] or "")}
    for k, v in stages.items():  # bursty stages: quiet for a while = finished
        if v["status"] == "running" and now - v["t"] > (90 if k in ("chatgpt", "texture", "memory") else 1800):
            v["status"] = "done"
    for a in agents.values():  # started before the hook was installed / unmatched: hang it under its session's main
        if a["type"] != "main" and not a["parent"]:
            a["parent"] = f"main:{a['sid']}"
    for a in agents.values():  # a crashed/killed agent never sends Stop; let it go quiet
        if a["status"] == "running" and now - a["t"] > (900 if a["type"] != "main" else 300):
            a["status"] = "stale" if a["type"] != "main" else "idle"
    for a in agents.values():
        if a["cwd"]:
            a.update(repo_info(a["cwd"]))
    keep = [a for a in agents.values() if a["status"] == "running" or now - a["t"] < window]
    keep.sort(key=lambda a: (a["type"] != "main", a["started"]))
    return {"now": now, "agents": keep, "stages": stages, "msgs": [m for m in msgs if now - m["t"] < window * 4][-120:],
            "events": [{k: v for k, v in e.items() if k in ("t", "ev", "who", "tool", "target", "err", "stage", "ok", "out")}
                       for e in events if e.get("ev") in ("PreToolUse", "SubagentStart", "SubagentStop")
                       or (e.get("ev") == "PostToolUse" and e.get("stage"))][-200:]}


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

    def _query(self):
        return {k: v[0] for k, v in parse_qs(urlsplit(self.path).query).items()}

    def _sheet(self, fn, *a, **k):
        try:
            return self._json(200, fn(*a, **k))
        except sheets.SheetError as e:
            return self._json(400, {"error": str(e)})
        except ImportError as e:
            return self._json(500, {"error": f"missing python package: {e.name} (pip install pillow numpy scipy)"})
        except Exception as e:  # noqa: BLE001 - report to the UI instead of dropping the connection
            return self._json(500, {"error": f"{type(e).__name__}: {e}"})

    def _sheet_post(self, path):
        # writes files: only same-machine pages (Host check vs DNS rebinding) and non-simple content types
        # (image/* or application/json need a CORS preflight, which this server never grants)
        host = (self.headers.get("Host") or "").rsplit(":", 1)[0]
        ctype = (self.headers.get("Content-Type") or "").split(";")[0].strip().lower()
        if host not in ("127.0.0.1", "localhost"):
            return self._json(403, {"error": "bad host"})
        if not (ctype.startswith("image/") if path == "/api/sheet/upload" else ctype == "application/json"):
            return self._json(415, {"error": "Content-Type must be image/* (upload) or application/json"})
        if path == "/api/sheet/upload":
            n = int(self.headers.get("Content-Length", 0) or 0)
            if n <= 0 or n > sheets.MAX_UPLOAD:
                return self._json(413, {"error": "image missing or larger than 25MB"})
            data = self.rfile.read(n)
            return self._sheet(sheets.save_upload, self._query().get("name", ""), data)
        try:
            d = self._body(200_000)
        except (ValueError, json.JSONDecodeError):
            return self._json(400, {"error": "bad json"})
        ids = d.get("ids")
        if ids is not None and not (isinstance(ids, list) and all(isinstance(i, str) for i in ids)):
            return self._json(400, {"error": "ids must be a list of strings"})
        args = (str(d.get("name", "")), "auto", d.get("region"), d.get("merge"), ids)
        if path == "/api/sheet/detect":
            return self._sheet(sheets.detect, *args)
        if path == "/api/sheet/import":
            res = self._sheet(sheets.import_sheet, *args, dry_run=bool(d.get("dry_run")))
            _tex_cache["t"] = 0
            return res
        if path == "/api/sheet/undo":
            res = self._sheet(sheets.undo, dry_run=bool(d.get("dry_run")))
            _tex_cache["t"] = 0
            return res
        return self._json(404, {"error": "not found"})

    def _texgen(self, fn, *a, **k):
        try:
            import texture_gen
        except ImportError as e:
            return self._json(500, {"error": f"missing python package: {e.name} (pip install pillow numpy scipy)"})
        try:
            return self._json(200, fn(texture_gen, *a, **k))
        except texture_gen.TexGenError as e:
            return self._json(400, {"error": str(e)})
        except Exception as e:  # noqa: BLE001 - report to the UI instead of dropping the connection
            return self._json(500, {"error": f"{type(e).__name__}: {e}"})

    def _texgen_post(self, path):
        # same rules as the sheet endpoints: same-machine Host, JSON only (CORS preflight never granted), size limit
        host = (self.headers.get("Host") or "").rsplit(":", 1)[0]
        ctype = (self.headers.get("Content-Type") or "").split(";")[0].strip().lower()
        if host not in ("127.0.0.1", "localhost"):
            return self._json(403, {"error": "bad host"})
        if ctype != "application/json":
            return self._json(415, {"error": "Content-Type must be application/json"})
        if int(self.headers.get("Content-Length", 0) or 0) > 200_000:
            return self._json(413, {"error": "body too large"})
        try:
            d = self._body(200_000)
        except (ValueError, json.JSONDecodeError):
            return self._json(400, {"error": "bad json"})
        if not isinstance(d, dict):
            return self._json(400, {"error": "bad json"})
        params = d.get("params") or {}
        seeds = d.get("seeds") or [0]
        if not isinstance(params, dict) or not (isinstance(seeds, list) and all(isinstance(s, int) for s in seeds)):
            return self._json(400, {"error": "params must be an object, seeds a list of integers"})
        gen = str(d.get("gen", ""))
        if path == "/api/texgen/preview":
            return self._texgen(lambda t: t.preview(gen, params, seeds))
        if path == "/api/texgen/add":
            if len(seeds) > 12:
                return self._json(400, {"error": "max 12 seeds"})
            return self._texgen(lambda t: {"batch": d.get("batch"), "candidates": t.run_job(
                gen, params, seeds, str(d.get("batch") or "gui"), suffix_seed=bool(d.get("suffix_seed", len(seeds) > 1)))})
        if path == "/api/texgen/apply":
            items = d.get("items")
            if not isinstance(items, list) or not 0 < len(items) <= 64:
                return self._json(400, {"error": "items: 1..64 candidates"})
            res = self._texgen(lambda t: t.apply(items, bool(d.get("lock")), bool(d.get("force_locked")),
                                                 bool(d.get("dry_run"))))
            _tex_cache["t"] = 0
            return res
        if path == "/api/texgen/undo":
            res = self._texgen(lambda t: t.undo(bool(d.get("dry_run"))))
            _tex_cache["t"] = 0
            return res
        if path == "/api/texgen/profile":
            return self._texgen(lambda t: (lambda p: {"name": p["name"], "sources": [x["ref"] for x in p["sources"]],
                                                      "aggregate": {k: v for k, v in p["aggregate"].items()
                                                                    if k != "spectrum"}})(
                t.profile(category=str(d.get("category", "")))))
        return self._json(404, {"error": "not found"})

    def _png(self, f):
        body = f.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        return self.wfile.write(body)

    def _texgen_get(self, path):
        q = self._query()
        if path == "/api/texgen/meta":
            return self._texgen(lambda t: t.meta())
        if path == "/api/texgen/sources":
            return self._texgen(lambda t: t.sources())
        if path == "/api/texgen/candidates":
            return self._texgen(lambda t: t.list_candidates(q.get("batch") or None))
        if path in ("/api/texgen/image", "/api/texgen/contact"):
            try:
                import texture_gen as t
                if path.endswith("image"):
                    f = t.candidate_file(q.get("batch", ""), q.get("name", ""))
                else:
                    f = t.P.generated / t.check_batch(q.get("batch", "")) / "contact.png"
                    if not f.is_file():
                        raise t.TexGenError("no contact sheet")
            except ImportError as e:
                return self._json(500, {"error": f"missing python package: {e.name}"})
            except ValueError as e:
                return self._json(404, {"error": str(e)})
            return self._png(f)
        return self._json(404, {"error": "not found"})

    def do_POST(self):
        path = self.path.split("?")[0]
        if path.startswith("/api/sheet/"):
            return self._sheet_post(path)
        if path.startswith("/api/texgen/"):
            return self._texgen_post(path)
        try:
            d = self._body()
        except (ValueError, json.JSONDecodeError):
            return self._json(400, {"error": "bad json"})
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
             "via": d.get("via") if d.get("via") in ("claude", "chatgpt") else "claude",
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
        if path == "/live.json":
            return self._json(200, live())
        if path == "/textures.json":
            return self._json(200, textures())
        if path == "/sheets.json":
            return self._sheet(sheets.status)
        if path.startswith("/api/texgen/"):
            return self._texgen_get(path)
        if path == "/api/sheet/prompt":
            name = self._query().get("name", "")
            return self._sheet(lambda: {"name": name, "prompt": sheets.prompt(name)})
        if path == "/api/sheet/image":
            try:
                img, _src = sheets.image_for(sheets.preset(self._query().get("name", "")))
            except sheets.SheetError as e:
                return self._json(400, {"error": str(e)})
            if not img:
                return self._json(404, {"error": "no image"})
            body = img.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", {".png": "image/png", ".webp": "image/webp"}.get(img.suffix, "image/jpeg"))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return self.wfile.write(body)
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
            return self._json(200, merged_state())
        super().do_GET()

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    load_cfg()
    print(f"agentflow: http://127.0.0.1:{port}/  (autorun {'on' if load_cfg()['enabled'] else 'off'})", flush=True)
    http.server.ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
