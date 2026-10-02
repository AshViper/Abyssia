#!/usr/bin/env python3
"""agentflow hook - records what every Claude Code agent is doing, for the agent tree view.

Wired in .claude/settings.json for PreToolUse / PostToolUse / SubagentStart / SubagentStop /
UserPromptSubmit / Stop. Reads the hook JSON on stdin and appends one compact line to
inbox/flow/live.jsonl (override with AGENTFLOW_LIVE). server.py folds that file into /live.json.

Never blocks or fails the agent: no stdout, always exit 0. Also usable by hand:
  echo '{"hook_event_name":"PreToolUse","tool_name":"Read","tool_input":{"file_path":"x"}}' | python hook.py
"""
import json, os, sys, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LIVE = Path(os.environ.get("AGENTFLOW_LIVE", ROOT / "inbox" / "flow" / "live.jsonl"))
MAX_BYTES = 2_000_000  # file is trimmed to its newest half past this


def clip(s, n=400):
    s = s if isinstance(s, str) else json.dumps(s, ensure_ascii=False)
    s = " ".join(s.split())
    return s if len(s) <= n else s[:n - 1] + "…"


def rel(p):
    """Project-relative path with forward slashes (what the UI shows as 'where')."""
    if not p:
        return ""
    p = str(p).replace("\\", "/")
    r = str(ROOT).replace("\\", "/")
    return p[len(r) + 1:] if p.lower().startswith(r.lower() + "/") else p


def target(tool, ti):
    """The one thing a tool call is working on: a file, pattern, command or prompt."""
    if not isinstance(ti, dict):
        return ""
    for k in ("file_path", "notebook_path", "path"):
        if ti.get(k):
            out = rel(ti[k])
            if ti.get("pattern"):
                out = f"{ti['pattern']} @ {out}"
            return out
    if ti.get("pattern"):
        return ti["pattern"]
    if ti.get("command"):
        return clip(ti["command"], 160)
    if ti.get("url"):
        return ti["url"]
    if ti.get("description"):
        return clip(ti["description"], 160)
    if ti.get("query"):
        return clip(ti["query"], 160)
    return ""


def result_text(resp):
    """Best-effort text of a tool response (Agent results are a list of content blocks)."""
    if isinstance(resp, str):
        return resp
    if isinstance(resp, dict):
        c = resp.get("content", resp.get("result", resp.get("output", "")))
        if isinstance(c, list):
            return " ".join(b.get("text", "") for b in c if isinstance(b, dict))
        return c if isinstance(c, str) else json.dumps(c, ensure_ascii=False)
    if isinstance(resp, list):
        return " ".join(b.get("text", "") for b in resp if isinstance(b, dict))
    return ""


def event(d):
    ev = d.get("hook_event_name", "")
    sid = (d.get("session_id") or "")[:8]
    aid = d.get("agent_id") or ""
    who = aid or f"main:{sid}"
    e = {"t": time.time(), "ev": ev, "who": who, "sid": sid, "type": d.get("agent_type") or ("" if aid else "main")}
    tool, ti = d.get("tool_name", ""), d.get("tool_input") or {}
    if ev in ("PreToolUse", "PostToolUse"):
        e["tool"] = tool
        e["target"] = target(tool, ti)
        e["tid"] = d.get("tool_use_id", "")
        if tool in ("Agent", "Task"):  # main -> subagent hand-off and the answer coming back
            e["sub"] = ti.get("subagent_type") or "general-purpose"
            e["desc"] = clip(ti.get("description", ""), 120)
            if ev == "PreToolUse":
                e["msg"] = clip(ti.get("prompt", ""), 600)
            else:
                e["msg"] = clip(result_text(d.get("tool_response")), 600)
        elif tool == "SendMessage":
            e["to"] = ti.get("to", "")
            e["msg"] = clip(ti.get("message", ""), 600)
        elif ev == "PostToolUse":
            r = d.get("tool_response")
            if isinstance(r, dict) and (r.get("is_error") or r.get("error")):
                e["err"] = clip(r.get("error") or result_text(r), 200)
    elif ev == "SubagentStart":
        e["msg"] = clip(d.get("prompt", ""), 600)
    elif ev == "SubagentStop":
        e["msg"] = clip(d.get("last_assistant_message", "") or "", 600)
    elif ev == "UserPromptSubmit":
        e["msg"] = clip(d.get("prompt", ""), 300)
    elif ev == "Stop":
        e["msg"] = clip(d.get("last_assistant_message", "") or "", 300)
    return e


def append(e):
    LIVE.parent.mkdir(parents=True, exist_ok=True)
    line = (json.dumps(e, ensure_ascii=False) + "\n").encode("utf-8")
    with open(LIVE, "ab") as f:  # one write per line; parallel agents append whole lines
        f.write(line)
    if LIVE.stat().st_size > MAX_BYTES:
        data = LIVE.read_bytes()
        cut = data.find(b"\n", len(data) // 2) + 1
        tmp = LIVE.with_suffix(".tmp")
        tmp.write_bytes(data[cut:])
        os.replace(tmp, LIVE)


def main():
    try:
        d = json.loads(sys.stdin.buffer.read().decode("utf-8") or "{}")
        append(event(d))
    except Exception:  # noqa: BLE001 - a broken hook must never stop the agent
        pass


if __name__ == "__main__":
    main()
