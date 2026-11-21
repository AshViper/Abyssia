"""Checks the translated lang files against en_us (read-only unless --prune).

en_us.json and ja_jp.json are generator output (gen_deep_assets.lang, guide_text.py).  Every other locale
(zh_cn, ru_ru, ...) is a hand-maintained file next to them:

    src/main/resources/assets/abyssia/lang/<locale>.json          item / block / message texts
    src/main/resources/assets/abyssia/guide/lang/<locale>.json    guide book texts

The generators never touch those files, so after adding or renaming keys run this to see what is left to
translate.  A key missing from a locale is not an error in game (Minecraft and the guide book fall back to
en_us), it only shows English.

For each locale it reports
* missing      keys of en_us the locale lacks (shown in English in game)
* stale        keys the locale has but en_us no longer has
* placeholders keys whose %s / %1$s / %d differ from en_us (these can crash or garble a formatted message)
* empty        keys translated to an empty string

    python tools/lang_check.py                  # JSON report with counts, exit code 1 on any problem
    python tools/lang_check.py --verbose        # ... with the key lists
    python tools/lang_check.py --locale zh_cn   # one locale only
    python tools/lang_check.py --prune          # WRITES: drops the stale keys from the locale files
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia"))
SETS = {"lang": os.path.join(ASSETS, "lang"), "guide": os.path.join(ASSETS, "guide", "lang")}
BASE = "en_us"
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?([A-Za-z])")   # any letter: a stray "%x" breaks vanilla's formatter too


def placeholders(text: str) -> list[str]:
    """Format specifiers as a sorted list; positional (%1$s) and sequential (%s) forms compare equal."""
    out, seq = [], 0
    for m in PLACEHOLDER.finditer(text.replace("%%", "")):
        if m.group(1):
            out.append(m.group(1) + m.group(2))
        else:
            seq += 1
            out.append(str(seq) + m.group(2))
    return sorted(out)


def load(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def check(folder: str, only: str | None, prune: bool) -> dict:
    base = load(os.path.join(folder, BASE + ".json"))
    report = {}
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        code = os.path.splitext(os.path.basename(path))[0]
        if code == BASE or (only and code != only):
            continue
        data = load(path)
        stale = sorted(k for k in data if k not in base)
        report[code] = {
            "keys": len(data),
            "missing": sorted(k for k in base if k not in data),
            "stale": stale,
            "placeholders": sorted(k for k in data if k in base and placeholders(data[k]) != placeholders(base[k])),
            "empty": sorted(k for k, v in data.items() if not v.strip()),
        }
        if prune and stale:
            kept = {k: v for k, v in data.items() if k in base}
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(dict(sorted(kept.items())), f, ensure_ascii=False, indent=2)
                f.write("\n")
            report[code]["pruned"] = len(stale)
            report[code]["stale"] = []
    return report


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--locale", help="check one locale only (e.g. zh_cn)")
    ap.add_argument("--verbose", action="store_true", help="list the keys instead of counts")
    ap.add_argument("--prune", action="store_true", help="WRITES: remove stale keys from the locale files")
    args = ap.parse_args()
    report = {name: check(folder, args.locale, args.prune) for name, folder in SETS.items()}
    bad = any(r[f] for locales in report.values() for r in locales.values()
              for f in ("missing", "stale", "placeholders", "empty"))
    if not args.verbose:
        for locales in report.values():
            for r in locales.values():
                for f in ("missing", "stale", "placeholders", "empty"):
                    r[f] = len(r[f])
    json.dump({"ok": not bad, **report}, sys.stdout, ensure_ascii=False, indent=2)
    print()
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
