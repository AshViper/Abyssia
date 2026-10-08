"""Excerpt-only lookup in the Obsidian vault (project/), so a note is never read whole by default.

    python tools/memory.py search <terms...> [-n 5] [--all] [--json]   # ranked notes: description + matching lines
    python tools/memory.py heads <note>                                # the headings of one note
    python tools/memory.py show <note> [<heading words>] [--max 60]    # one section (or the first lines) of a note

Notes are found by name (the file stem, as in [[links]]).  history/ (level 4) is left out of ``search`` unless --all;
_archive/ is never read.  The vault is ``ABYSSIA_VAULT`` or G:/Obsidian/Abyssia Vault/project.  Output is capped, so a
lookup costs a few hundred tokens instead of a whole note.  Read-only; no side effects.
"""
import argparse
import json
import os
import re
import sys

VAULT = os.environ.get("ABYSSIA_VAULT", r"G:\Obsidian\Abyssia Vault\project")
LINE_MAX = 140


def notes(include_history=False):
    for root, dirs, files in os.walk(VAULT):
        dirs[:] = [d for d in dirs if d != "_archive" and (include_history or d != "history")]
        for f in files:
            if f.endswith(".md"):
                yield os.path.join(root, f)


def stem(path):
    return os.path.splitext(os.path.basename(path))[0]


def read(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def description(text):
    m = re.search(r'^description:\s*"?(.*?)"?\s*$', text, re.M)
    return m.group(1) if m else ""


def cut(s, n=LINE_MAX):
    s = s.strip()
    return s if len(s) <= n else s[: n - 1] + "…"


def search(terms, limit, include_history):
    terms = [t.lower() for t in terms]
    hits = []
    for path in notes(include_history):
        text = read(path)
        low = text.lower()
        name = stem(path).lower()
        desc = description(text).lower()
        score = 0
        for t in terms:
            score += 6 * (t in name) + 4 * (t in desc) + min(low.count(t), 5)
        # every term must appear somewhere in the note
        if score and all(t in low or t in name for t in terms):
            lines = [ln for ln in text.splitlines() if any(t in ln.lower() for t in terms)
                     and not ln.startswith(("name:", "description:", "---"))]
            hits.append((score, path, description(text), lines))
    hits.sort(key=lambda h: -h[0])
    return hits[:limit]


def sections(text):
    """[(level, title, start_line_index)] of the markdown headings and bold-led paragraphs (code fences ignored)."""
    out, fence = [], False
    for i, ln in enumerate(text.splitlines()):
        if ln.startswith("```"):
            fence = not fence
        m = None if fence else re.match(r"^(#{1,6})\s+(.*)", ln)
        if m:
            out.append((len(m.group(1)), m.group(2).strip(), i))
        elif not fence and (b := re.match(r"^\*\*(.+?)\*\*", ln)):
            out.append((7, b.group(1).strip(" .:"), i))     # a paragraph led by bold text counts as a sub-heading
    return out


def find(name):
    for path in notes(True):
        if stem(path).lower() == name.lower():
            return path
    sys.exit(f"no note named {name!r} (try: memory.py search {name})")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("search")
    s.add_argument("terms", nargs="+")
    s.add_argument("-n", type=int, default=5)
    s.add_argument("--all", action="store_true", help="include history/")
    s.add_argument("--json", action="store_true")
    h = sub.add_parser("heads")
    h.add_argument("note")
    sh = sub.add_parser("show")
    sh.add_argument("note")
    sh.add_argument("heading", nargs="*")
    sh.add_argument("--max", type=int, default=60, help="most lines printed")
    a = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    if a.cmd == "search":
        hits = search(a.terms, a.n, a.all)
        if a.json:
            print(json.dumps([{"note": stem(p), "path": os.path.relpath(p, VAULT).replace("\\", "/"), "description": d,
                               "lines": [cut(x) for x in ls[:3]]} for _, p, d, ls in hits], ensure_ascii=False))
            return
        if not hits:
            print("(no match)")
        for _, p, d, ls in hits:
            print(f"{stem(p)}  [{os.path.relpath(p, VAULT).replace(chr(92), '/')}]  {cut(d, 100)}")
            for x in ls[:2]:
                print("    " + cut(x))
    elif a.cmd == "heads":
        text = read(find(a.note))
        for lvl, title, i in sections(text):
            print(f"{'  ' * min(lvl - 1, 2)}{title}  (L{i + 1})")
    else:
        path = find(a.note)
        text = read(path)
        lines = text.splitlines()
        secs = sections(text)
        if a.heading:
            want = " ".join(a.heading).lower()
            for k, (lvl, title, i) in enumerate(secs):
                if want in title.lower():
                    end = len(lines)
                    for lvl2, _, j in secs[k + 1:]:
                        if lvl2 <= lvl:
                            end = j
                            break
                    chunk = lines[i:end]
                    break
            else:
                sys.exit(f"no heading containing {want!r}; headings: " + "; ".join(t for _, t, _ in secs))
        else:
            body = [i for i, ln in enumerate(lines) if ln.strip() == "---"]
            start = body[1] + 1 if len(body) >= 2 else 0     # skip the frontmatter
            chunk = lines[start:]
        print("\n".join(chunk[: a.max]))
        if len(chunk) > a.max:
            print(f"… ({len(chunk) - a.max} more lines; heads/show <heading> or --max)")


if __name__ == "__main__":
    main()
