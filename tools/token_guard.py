"""PreToolUse hook (matcher Read): stops whole-file reads that cost a lot of context, and says what to do instead.

Blocked (exit 2, the message goes back to Claude):
  - a file over LIMIT_BYTES read without ``limit`` (use Grep, or Read with offset + limit);
  - generated / bulky files whatever their size, unless a ``limit`` is given: *.bbmodel, *Mesh.java (baked quads),
    build/, .gradle/, node_modules/, .git/, run/logs, run/saves.
Images and PDFs are never blocked.  Set TOKEN_GUARD=off to switch it off.  Any error lets the call through.

Stdin: the hook JSON (tool_name, tool_input).  CLI check:  python tools/token_guard.py <path> [limit]
"""
import json
import os
import sys

LIMIT_BYTES = 40_000
FREE_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".ipynb"}
BULKY_DIRS = ("/build/", "/.gradle/", "/node_modules/", "/.git/", "/run/logs/", "/run/saves/", "/run/crash-reports/")
BULKY_SUFFIX = (".bbmodel", "Mesh.java")


def verdict(path, limit):
    """None to allow, else the message for Claude."""
    if limit:
        return None
    norm = path.replace("\\", "/")
    if os.path.splitext(norm)[1].lower() in FREE_EXT:
        return None
    if any(d in norm for d in BULKY_DIRS) or norm.endswith(BULKY_SUFFIX):
        return (f"token-guard: {os.path.basename(norm)} is generated/bulky. Read only the part you need "
                "(Grep for it, then Read with offset+limit), or regenerate it from its tool instead of reading it.")
    try:
        size = os.path.getsize(path)
    except OSError:
        return None
    if size > LIMIT_BYTES:
        return (f"token-guard: {os.path.basename(norm)} is {size // 1000} KB. Grep for the symbol, then Read with "
                "offset+limit (or `python tools/memory.py show` for a vault note) instead of the whole file.")
    return None


def main():
    if os.environ.get("TOKEN_GUARD", "").lower() == "off":
        return 0
    if len(sys.argv) > 1:
        msg = verdict(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 0)
        print(msg or "allow")
        return 2 if msg else 0
    try:
        data = json.load(sys.stdin)
        if data.get("tool_name") != "Read":
            return 0
        args = data.get("tool_input", {})
        msg = verdict(args.get("file_path", ""), args.get("limit"))
    except Exception:
        return 0
    if msg:
        print(msg, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
