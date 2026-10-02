#!/usr/bin/env python3
"""agentflow CLI - agent/task state for the flowchart app (tools/agentflow/index.html).

State file: inbox/flow/state.json (override with AGENTFLOW_STATE). JSON in/out, no deps.

  flow.py add <id> --tier light|standard|heavy [--title T] [--files F] [--parent ID] [--model M]
  flow.py set <id> queued|running|done|failed|escalated [--note N] [--model M]
  flow.py stage chatgpt|router|texture|verify|build|memory|git idle|running|done|failed [--note N]
  flow.py main --model M [--note N]       # model the main agent is running as
  flow.py escalate <id> [--to standard|heavy]   # mark escalated + add child task in higher tier
  flow.py req [list [--all]] | req set <id> new|specced|running|done   # UI requests (inbox/requests)
  flow.py log <id|main|stage> "what I'm doing" [--kind info|file|tool|decision|error] [--model M]
  flow.py ask "question" [--who W] [--wait SEC]   # post an approval card on the site; exit 0 approved / 3 denied / 4 pending
  flow.py answer <id> [--wait SEC]                # check/wait for a posted approval
  flow.py finish                                  # end of run: stages idle, finished tasks archived
  flow.py show                      # print state JSON
  flow.py reset
"""
import argparse, json, os, sys, tempfile, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE = Path(os.environ.get("AGENTFLOW_STATE", ROOT / "inbox" / "flow" / "state.json"))
TIERS = ["light", "standard", "heavy"]
# 管理(main)は Opus 5.5。作業 tier: light/standard は Sonnet 5.5、heavy は Opus 5.5 のサブエージェントが実行する。
MODEL = {"light": "sonnet-5.5", "standard": "sonnet-5.5", "heavy": "opus-5.5"}
REQ = Path(os.environ.get("AGENTFLOW_REQUESTS", ROOT / "inbox" / "requests"))
APR = Path(os.environ.get("AGENTFLOW_APPROVALS", ROOT / "inbox" / "approvals"))
REQ_ST = ["new", "specced", "running", "done"]
STAGES = ["request", "router", "chatgpt", "texture", "verify", "build", "memory", "git"]
TASK_ST = ["queued", "running", "done", "failed", "escalated"]
STAGE_ST = ["idle", "running", "done", "failed"]


def fresh():
    return {"updated": 0, "main": {"model": "", "note": ""}, "tasks": [], "log": [],
            "stages": {s: {"status": "idle", "note": ""} for s in STAGES}}


def load():
    try:
        st = json.loads(STATE.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return fresh()
    st.setdefault("log", [])
    for k in STAGES:  # states written before a stage existed
        st.setdefault("stages", {}).setdefault(k, {"status": "idle", "note": ""})
    return st


def logev(st, who, msg, kind="info", model=""):
    """Append one activity entry (who = task id, stage name or 'main'); keeps the last 500."""
    st.setdefault("log", []).append({"t": time.time(), "who": who, "kind": kind, "model": model, "msg": msg})
    del st["log"][:-500]


def save(st):
    st["updated"] = time.time()
    STATE.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=STATE.parent, suffix=".tmp")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(st, f, ensure_ascii=False, indent=1)
    os.replace(tmp, STATE)  # atomic: the app never reads a half-written file


def find(st, tid):
    for t in st["tasks"]:
        if t["id"] == tid:
            return t
    sys.exit(json.dumps({"error": f"no such task: {tid}"}))


def add(st, tid, tier, title="", files="", parent="", model=""):
    if any(t["id"] == tid for t in st["tasks"]):
        sys.exit(json.dumps({"error": f"duplicate task: {tid}"}))
    st["tasks"].append({"id": tid, "title": title, "tier": tier, "model": model or MODEL[tier],
                        "status": "queued", "files": files, "parent": parent, "note": "",
                        "started": None, "ended": None})


def main():
    sys.stdout.reconfigure(encoding="utf-8")  # Windows console default is cp932
    p =argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = p.add_subparsers(dest="cmd", required=True)
    a = sp.add_parser("add"); a.add_argument("id"); a.add_argument("--tier", choices=TIERS, required=True)
    a.add_argument("--title", default=""); a.add_argument("--files", default="")
    a.add_argument("--parent", default=""); a.add_argument("--model", default="")
    s = sp.add_parser("set"); s.add_argument("id"); s.add_argument("status", choices=TASK_ST)
    s.add_argument("--note"); s.add_argument("--model")
    g = sp.add_parser("stage"); g.add_argument("name", choices=STAGES)
    g.add_argument("status", choices=STAGE_ST); g.add_argument("--note", default="")
    m = sp.add_parser("main"); m.add_argument("--model", required=True); m.add_argument("--note", default="")
    e = sp.add_parser("escalate"); e.add_argument("id"); e.add_argument("--to", choices=TIERS[1:])
    q = sp.add_parser("req"); qs = q.add_subparsers(dest="rcmd")
    ql = qs.add_parser("list"); ql.add_argument("--all", action="store_true")
    qt = qs.add_parser("set"); qt.add_argument("id"); qt.add_argument("status", choices=REQ_ST)
    ak = sp.add_parser("ask"); ak.add_argument("question"); ak.add_argument("--who", default="main")
    ak.add_argument("--wait", type=int, default=0, help="seconds to wait for the answer (0 = just post it)")
    aw = sp.add_parser("answer"); aw.add_argument("id"); aw.add_argument("--wait", type=int, default=0)
    lg = sp.add_parser("log"); lg.add_argument("who"); lg.add_argument("msg"); lg.add_argument("--model", default="")
    lg.add_argument("--kind", default="info", choices=["info", "file", "tool", "decision", "error"])
    sp.add_parser("show"); sp.add_parser("reset")
    sp.add_parser("finish")
    x = p.parse_args()

    if x.cmd in ("ask", "answer"):
        # Approval requested from the web UI. Exit code: 0 approved, 3 denied, 4 still pending/timeout.
        if x.cmd == "ask":
            APR.mkdir(parents=True, exist_ok=True)
            aid = time.strftime("%Y%m%d-%H%M%S")
            while (APR / f"{aid}.json").exists():
                aid += "x"
            (APR / f"{aid}.json").write_text(json.dumps({"id": aid, "who": x.who, "question": x.question, "status": "pending",
                                                          "created": time.time()}, ensure_ascii=False, indent=1), encoding="utf-8")
            st = load(); logev(st, x.who, f"承認待ち: {x.question}", "decision"); save(st)
        else:
            aid = x.id
        end = time.time() + x.wait
        while True:
            a = json.loads((APR / f"{aid}.json").read_text(encoding="utf-8"))
            if a["status"] != "pending" or time.time() >= end:
                break
            time.sleep(2)
        print(json.dumps({"id": aid, "status": a["status"]}, ensure_ascii=False))
        sys.exit({"approved": 0, "denied": 3}.get(a["status"], 4))

    if x.cmd == "req":
        rs = [json.loads(f.read_text(encoding="utf-8")) for f in (sorted(REQ.glob("*.json")) if REQ.exists() else [])]
        if x.rcmd == "set":
            r = next((r for r in rs if r["id"] == x.id), None)
            if not r:
                sys.exit(json.dumps({"error": f"no such request: {x.id}"}))
            r["status"] = x.status
            (REQ / f"{x.id}.json").write_text(json.dumps(r, ensure_ascii=False, indent=1), encoding="utf-8")
            print(json.dumps({"ok": True})); return
        show = rs if getattr(x, "all", False) else [r for r in rs if r["status"] != "done"]
        print(json.dumps(show, ensure_ascii=False, indent=1)); return

    st = load()
    if x.cmd == "show":
        print(json.dumps(st, ensure_ascii=False, indent=1)); return
    if x.cmd == "reset":
        st = fresh()
    elif x.cmd == "finish":
        # End of a pipeline run: every stage and the main agent back to idle, finished tasks archived (the log keeps them).
        for k in st["stages"]:
            st["stages"][k] = {"status": "idle", "note": "", "t": time.time()}
        st["main"] = {"model": st["main"].get("model", ""), "note": ""}
        st["tasks"] = [t for t in st["tasks"] if t["status"] in ("queued", "running")]
        logev(st, "main", "全作業完了 - 待機に戻しました", "status")
    elif x.cmd == "add":
        add(st, x.id, x.tier, x.title, x.files, x.parent, x.model)
        logev(st, x.id, f"追加 [{x.tier}] {x.title}", "add", x.model or MODEL[x.tier])
    elif x.cmd == "log":
        logev(st, x.who, x.msg, x.kind, x.model)
    elif x.cmd == "set":
        t = find(st, x.id); now = time.time()
        if x.status == "running" and not t["started"]:
            t["started"] = now
        if x.status in ("done", "failed", "escalated"):
            t["ended"] = now
        t["status"] = x.status
        if x.note is not None: t["note"] = x.note
        if x.model: t["model"] = x.model
        logev(st, x.id, x.status + (f" — {x.note}" if x.note else ""), "status", t["model"])
    elif x.cmd == "stage":
        st["stages"][x.name] = {"status": x.status, "note": x.note, "t": time.time()}
        logev(st, x.name, x.status + (f" — {x.note}" if x.note else ""), "status")
    elif x.cmd == "main":
        st["main"] = {"model": x.model, "note": x.note}
        logev(st, "main", f"model={x.model}" + (f" — {x.note}" if x.note else ""), "status", x.model)
    elif x.cmd == "escalate":
        t = find(st, x.id)
        nxt = x.to or TIERS[min(TIERS.index(t["tier"]) + 1, 2)]
        if nxt == t["tier"]:
            sys.exit(json.dumps({"error": "already at heavy"}))
        t["status"] = "escalated"; t["ended"] = time.time()
        add(st, f"{x.id}+", nxt, t["title"], t["files"], parent=x.id)
        logev(st, x.id, f"格上げ → {nxt} ({x.id}+)", "esc", t["model"])
        logev(st, f"{x.id}+", f"追加 [{nxt}] {t['title']}", "add", MODEL[nxt])
    save(st)
    print(json.dumps({"ok": True, "tasks": len(st["tasks"])}))


if __name__ == "__main__":
    main()
