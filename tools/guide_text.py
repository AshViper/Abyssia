"""GB01 guide book text: ChatGPT's chapters/pages/ja/en (inbox/specs/GB01-text.json) -> assets/abyssia/guide/.

The ChatGPT source names icons and pictures by bare name; this expands them to the paths the loader reads
(icons/<name>.png, textures/gui/guide/pages/<name>.png).  EXTRA pages (the guide book's own item / recipe pages)
are kept after ChatGPT's pages of their chapter, with their lang lines.

    python tools/guide_text.py            # rewrite chapters.json, pages/*.json, lang/*.json
    python tools/guide_text.py --dry-run  # only print counts
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "..", "inbox", "specs", "GB01-text.json")
OUT = os.path.join(ROOT, "..", "src", "main", "resources", "assets", "abyssia", "guide")

EXTRA = {
    "intro": [{"type": "item", "id": "intro.book", "chapter": "intro", "item": "abyssia:abyss_guide_book",
               "description": "guide.abyssia.intro.book.desc", "obtaining": "guide.abyssia.intro.book.obtaining",
               "recipe": "abyssia:abyss_guide_book"}],
    "encyclopedia": [{"type": "recipe", "id": "encyclopedia.guide_recipe", "chapter": "encyclopedia",
                      "title": "guide.abyssia.encyclopedia.guide_recipe.title", "recipe": "abyssia:abyss_guide_book",
                      "description": "guide.abyssia.encyclopedia.guide_recipe.desc"}],
}
EXTRA_LANG = {
    "guide.abyssia.ui.contents": ("目次", "Contents"),
    "guide.abyssia.ui.obtaining": ("入手方法", "Obtaining"),
    "guide.abyssia.ui.prev": ("前のページ", "Previous page"),
    "guide.abyssia.ui.next": ("次のページ", "Next page"),
    "guide.abyssia.ui.close": ("閉じる", "Close"),
    "guide.abyssia.ui.recipe_missing": ("レシピが見つかりません", "Recipe not found"),
    "guide.abyssia.ui.image_missing": ("(画像なし)", "(no image)"),
    "guide.abyssia.intro.book.desc": ("いま読んでいるこの本です。困ったときはいつでも開いて、ゲームの流れやアイテムを確かめましょう。",
                                      "The book you are reading now. Open it whenever you are stuck to check the flow of the game and its items."),
    "guide.abyssia.intro.book.obtaining": ("初めてログインしたときに1冊もらえます。なくしたら、本と深海海藻葉で作れます。",
                                           "You receive one on your first login. If you lose it, craft one from a Book and a Deep Kelp Leaf."),
    "guide.abyssia.encyclopedia.guide_recipe.title": ("深海ガイドブックの作り方", "Crafting the Abyssia Guide"),
    "guide.abyssia.encyclopedia.guide_recipe.desc": ("本と深海海藻葉を組み合わせると作れます。", "Combine a Book with a Deep Kelp Leaf."),
}


# pages whose item has no item form (scan_console is placed by the habitat constructor only)
DROP = {"encyclopedia_10"}


def expand(page):
    p = dict(page)
    img = p.get("image")
    if img and "/" not in img:
        p["image"] = f"textures/gui/guide/pages/{img}.png"
    return p


def main(dry):
    src = json.load(open(SRC, encoding="utf-8"))
    chapters = []
    pages = {}
    for ch in sorted(src["chapters"], key=lambda c: c["order"]):
        cid = ch["id"]
        extra = EXTRA.get(cid, [])
        pages[cid] = [expand(p) for p in src["pages"][cid] if p["id"] not in DROP] + extra
        known = {p["id"] for p in pages[cid]}
        missing = [i for i in ch["pages"] if i not in known and i not in DROP]
        if missing:
            raise SystemExit(f"{cid}: chapter lists pages with no data: {missing}")
        icon = ch["icon"] if "/" in ch["icon"] else f"icons/{ch['icon']}.png"
        chapters.append({**ch, "icon": icon, "pages": [i for i in ch["pages"] if i not in DROP] + [p["id"] for p in extra]})
    lang = {code: dict(src[code]) for code in ("ja_jp", "en_us")}
    for k, (ja, en) in EXTRA_LANG.items():
        lang["ja_jp"][k] = ja
        lang["en_us"][k] = en
    print({"chapters": len(chapters), "pages": sum(len(v) for v in pages.values()),
           "keys": {c: len(v) for c, v in lang.items()}, "dry_run": dry})
    if dry:
        return

    def dump(path, obj):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
            f.write("\n")

    dump(os.path.join(OUT, "chapters.json"), {"chapters": chapters})
    for cid, ps in pages.items():
        dump(os.path.join(OUT, "pages", cid + ".json"), ps)
    for code, d in lang.items():
        dump(os.path.join(OUT, "lang", code + ".json"), dict(sorted(d.items())))


if __name__ == "__main__":
    main("--dry-run" in sys.argv)
