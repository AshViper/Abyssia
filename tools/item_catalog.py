"""Item & drop catalog: every Abyssia item ID with its icon, names, drops and recipes, in one file.

Reads the current sources only (ModItems/ModPlants Java, item models, lang, loot tables, recipes) and writes
  <out>/item_catalog.html  self-contained page (icons embedded as data: URIs)
  <out>/item_catalog.json  the same data, machine-readable
Vanilla icons (raw_iron, spawn egg base, ...) come from the ForgeGradle client-extra.jar.
Read-only on the project; safe to rerun any time.

  python tools/item_catalog.py [--out build/item_catalog] [--json-only]
"""
import argparse, base64, copy, glob, html, io, json, os, re, sys, zipfile
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "abyssia", "registry")
NS = "abyssia"
VANILLA_JAR = os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
RARITY = {"COMMON": "common", "UNCOMMON": "uncommon", "RARE": "rare", "EPIC": "epic"}

_jar = None
def vanilla(path):
    global _jar
    if _jar is None:
        _jar = zipfile.ZipFile(VANILLA_JAR) if os.path.exists(VANILLA_JAR) else False
    if not _jar: return None
    try: return _jar.read(path)
    except KeyError: return None

def asset(ns, path):
    """Bytes of assets/<ns>/<path> from the mod resources or the vanilla jar."""
    if ns == NS:
        p = os.path.join(RES, "assets", ns, *path.split("/"))
        return open(p, "rb").read() if os.path.exists(p) else None
    return vanilla(f"assets/{ns}/{path}")

def split_id(rl, default=NS):
    return rl.split(":", 1) if ":" in rl else (default if default else "minecraft", rl)

# ---------------------------------------------------------------- icons
def load_model(rl):
    ns, path = split_id(rl, "minecraft")
    raw = asset(ns, f"models/{path}.json")
    return json.loads(raw) if raw else None

def resolve_textures(rl):
    """Walk the parent chain; return (merged textures, parent ids seen)."""
    tex, parents = {}, []
    while rl:
        m = load_model(rl)
        if m is None: break
        for k, v in m.get("textures", {}).items(): tex.setdefault(k, v)
        parents.append(rl)
        rl = m.get("parent")
    def deref(v, depth=0):
        while isinstance(v, str) and v.startswith("#") and depth < 8:
            v = tex.get(v[1:]); depth += 1
        return v
    return {k: deref(v) for k, v in tex.items() if deref(v)}, parents

TEX_PRIORITY = ["layer0", "all", "cross", "plant", "texture", "side", "wall", "front", "top", "end", "particle"]

def texture_image(rl):
    ns, path = split_id(rl, "minecraft")
    raw = asset(ns, f"textures/{path}.png")
    if not raw: return None
    img = Image.open(io.BytesIO(raw)).convert("RGBA")
    if img.height > img.width:                       # animated strip: first frame
        img = img.crop((0, 0, img.width, img.width))
    return img

def tint(img, rgb):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    px = img.load(); out = img.copy(); po = out.load()
    for y in range(img.height):
        for x in range(img.width):
            pr, pg, pb, pa = px[x, y]
            po[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out

def icon_for_item(item_id, egg=None):
    if egg:
        base, over = texture_image("minecraft:item/spawn_egg"), texture_image("minecraft:item/spawn_egg_overlay")
        if base and over:
            img = tint(base, egg[0]); img.alpha_composite(tint(over, egg[1])); return img
        return None
    ns, path = split_id(item_id)
    tex, _ = resolve_textures(f"{ns}:item/{path}")
    for key in TEX_PRIORITY:
        if key in tex:
            img = texture_image(tex[key])
            if key == "layer0" and "layer1" in tex:
                over = texture_image(tex["layer1"])
                if img and over and over.size == img.size: img.alpha_composite(over)
            if img: return img
    for v in tex.values():
        img = texture_image(v)
        if img: return img
    return None

def data_uri(img):
    if img is None: return None
    if img.width < 32: img = img.resize((img.width * 2, img.height * 2), Image.NEAREST)
    buf = io.BytesIO(); img.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

# ---------------------------------------------------------------- registry (Java source)
def read(p): return open(p, encoding="utf-8").read()

def parse_registry():
    items_java, plants_java = read(os.path.join(JAVA, "ModItems.java")), read(os.path.join(JAVA, "ModPlants.java"))
    materials = {}
    for name in re.findall(r'= item\("(\w+)"\)', items_java):
        materials[name] = {"rarity": "common", "burn": 0}
    for name, body in re.findall(r'tabItem\("(\w+)",\s*\(\)\s*->\s*new\s+(.*?)\);\s*$', plants_java, re.M):
        rar = re.search(r"Rarity\.(\w+)", body)
        burn = re.search(r"\),\s*(\d+),\s*(true|false)\)", body)
        materials[name] = {"rarity": RARITY.get(rar.group(1), "common") if rar else "common",
                           "burn": int(burn.group(1)) if burn else 0,
                           "cls": body.split("(")[0].strip()}
    eggs = {n: (int(a, 16), int(b, 16)) for n, a, b in
            re.findall(r'spawnEgg\("(\w+)",\s*[\w.]+,\s*0x([0-9A-Fa-f]+),\s*0x([0-9A-Fa-f]+)\)', items_java)}
    return materials, eggs

# ---------------------------------------------------------------- lang
def load_lang(code):
    p = os.path.join(RES, "assets", NS, "lang", f"{code}.json")
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}

ASSETS = os.path.expanduser("~/.gradle/caches/forge_gradle/assets")

def load_vanilla_lang(code):
    """Vanilla en_us from the client jar; other languages via the launcher asset index."""
    try:
        if code == "en_us": return json.loads(vanilla("assets/minecraft/lang/en_us.json") or b"{}")
        index = json.load(open(os.path.join(ASSETS, "indexes", "5.json")))
        h = index["objects"][f"minecraft/lang/{code}.json"]["hash"]
        return json.load(open(os.path.join(ASSETS, "objects", h[:2], h), encoding="utf-8"))
    except (OSError, KeyError, ValueError):
        return {}

def names(item_id, en, ja):
    ns, path = split_id(item_id)
    if item_id.startswith("#"):
        return f"タグ {item_id}", f"タグ {item_id}"
    for kind in ("item", "block"):
        k = f"{kind}.{ns}.{path}"
        if k in en or k in ja:
            return en.get(k, path), ja.get(k, en.get(k, path))
    pretty = path.replace("_", " ").title()
    return pretty, pretty

# ---------------------------------------------------------------- loot tables
def count_text(funcs):
    for f in funcs:
        if f.get("function") == "minecraft:set_count":
            c = f["count"]
            if isinstance(c, (int, float)): return str(int(c))
            if c.get("type") == "minecraft:uniform": return f"{int(c['min'])}–{int(c['max'])}"
            if c.get("type") == "minecraft:binomial": return f"0–{int(c['n'])}"
    return "1"

STATE_JA = {"top=true": "最上段のみ", "tip=true": "先端のみ", "ripe=true": "成熟時"}

def cond_tags(conds):
    tags = []
    for c in conds or []:
        kind = c.get("condition", "").split(":")[-1]
        pred = c.get("predicate", {})
        if kind == "match_tool":
            # 1.21: enchantments under predicates."minecraft:enchantments" ("enchantments": id), items a list or one id
            ench = pred.get("enchantments", []) + pred.get("predicates", {}).get("minecraft:enchantments", [])
            items = pred.get("items", [])
            items = [items] if isinstance(items, str) else items
            if any(str(e.get("enchantment", e.get("enchantments", ""))).endswith("silk_touch") for e in ench): tags.append("シルクタッチ")
            elif any(i.endswith("shears") for i in items): tags.append("ハサミ")
            else: tags.append("道具指定")
        elif kind == "inverted":
            inner = cond_tags([c.get("term", {})])
            tags.append(("シルクタッチ/ハサミ以外" if inner and inner[0] in ("シルクタッチ", "ハサミ") else "否定条件"))
        elif kind == "alternative" or kind == "any_of":
            tags.append(" / ".join(cond_tags(c.get("terms", []))) or "いずれか")
        elif kind == "random_chance": tags.append(f"{c['chance'] * 100:g}%")
        elif kind == "random_chance_with_looting": tags.append(f"{c['chance'] * 100:g}%（ドロップ増加）")
        elif kind == "random_chance_with_enchanted_bonus": tags.append(f"{c['unenchanted_chance'] * 100:g}%（ドロップ増加）")
        elif kind == "table_bonus":
            ch = c.get("chances", [])
            tags.append(f"{ch[0] * 100:g}%（幸運で上昇）" if ch else "幸運")
        elif kind == "block_state_property":
            for k, v in c.get("properties", {}).items():
                tags.append(STATE_JA.get(f"{k}={v}", f"{k}={v}"))
        elif kind in ("survives_explosion", "killed_by_player"):
            continue
        else: tags.append(kind)
    return tags

def fn_tags(funcs):
    tags = []
    for f in funcs or []:
        fn = f.get("function", "").split(":")[-1]
        if fn == "apply_bonus": tags.append("幸運")
        elif fn in ("looting_enchant", "enchanted_count_increase"): tags.append("ドロップ増加")
    return tags

def walk_entries(entries, inherited, out):
    for e in entries:
        t = e.get("type", "").split(":")[-1]
        conds = inherited + cond_tags(e.get("conditions"))
        if t == "item":
            out.append({"item": e["name"], "count": count_text(e.get("functions", [])),
                        "tags": conds + fn_tags(e.get("functions"))})
        elif t in ("alternatives", "group", "sequence"):
            walk_entries(e.get("children", []), conds, out)
        elif t == "tag":
            out.append({"item": "#" + e["name"], "count": "1", "tags": conds})

def parse_loot():
    tables = []
    base = os.path.join(RES, "data", NS, "loot_table")      # 1.21 folder name
    for f in sorted(glob.glob(os.path.join(base, "**", "*.json"), recursive=True)):
        rel = os.path.relpath(f, base).replace(os.sep, "/")[:-5]
        kind, name = rel.split("/", 1)
        data = json.load(open(f, encoding="utf-8"))
        drops = []
        for pool in data.get("pools", []):
            pc = cond_tags(pool.get("conditions"))
            walk_entries(pool.get("entries", []), pc, drops)
        tables.append({"table": f"{NS}:{rel}", "kind": kind, "source": f"{NS}:{name}", "drops": drops})
    return tables

# ---------------------------------------------------------------- recipes
def ingredient_ids(ing):
    if isinstance(ing, list): return [x for i in ing for x in ingredient_ids(i)]
    if "item" in ing: return [ing["item"]]
    if "tag" in ing: return ["#" + ing["tag"]]
    return []

def parse_recipes():
    recipes = []
    for f in sorted(glob.glob(os.path.join(RES, "data", NS, "recipe", "*.json"))):
        r = json.load(open(f, encoding="utf-8"))
        t = r["type"].split(":")[-1]
        res = r.get("result")
        out = res if isinstance(res, str) else (res or {}).get("id", (res or {}).get("item"))   # 1.21: {"id", "count"}
        cnt = r.get("count", 1) if isinstance(res, str) else (res or {}).get("count", 1)
        inputs = []
        if "key" in r:
            per = {}
            for row in r["pattern"]:
                for ch in row:
                    if ch != " ": per[ch] = per.get(ch, 0) + 1
            for ch, n in per.items():
                for i in ingredient_ids(r["key"][ch])[:1]: inputs.append((i, n))
        elif "ingredients" in r:
            per = {}
            for ing in r["ingredients"]:
                for i in ingredient_ids(ing)[:1]: per[i] = per.get(i, 0) + 1
            inputs = list(per.items())
        elif "ingredient" in r:
            inputs = [(i, 1) for i in ingredient_ids(r["ingredient"])[:1]]
        recipes.append({"id": os.path.basename(f)[:-5], "type": t, "output": out, "count": cnt,
                        "inputs": [{"item": i, "n": n} for i, n in inputs]})
    return recipes

# ---------------------------------------------------------------- assemble
def build():
    en = {**load_vanilla_lang("en_us"), **load_lang("en_us")}
    ja = {**load_vanilla_lang("ja_jp"), **load_lang("ja_jp")}
    materials, eggs = parse_registry()
    model_ids = sorted(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(RES, "assets", NS, "models", "item", "*.json")))
    loot, recipes = parse_loot(), parse_recipes()

    icons = {}
    def icon(item_id):
        if item_id not in icons:
            if item_id.startswith("#"): icons[item_id] = None
            else:
                path = split_id(item_id)[1]
                icons[item_id] = data_uri(icon_for_item(item_id, eggs.get(path) if split_id(item_id)[0] == NS else None))
        return icons[item_id]

    def entry(item_id):
        e_en, e_ja = names(item_id, en, ja)
        return {"id": item_id, "en": e_en, "ja": e_ja}

    drop_sources, made_by, used_in = {}, {}, {}
    for t in loot:
        for d in t["drops"]:
            if d["item"] != t["source"]:
                drop_sources.setdefault(d["item"], []).append({"source": t["source"], "kind": t["kind"],
                                                               "count": d["count"], "tags": d["tags"]})
    for r in recipes:
        if r["output"]: made_by.setdefault(r["output"], []).append(r)
        for i in r["inputs"]: used_in.setdefault(i["item"], []).append(r)

    items = []
    for name in model_ids:
        iid = f"{NS}:{name}"
        cat = "egg" if name in eggs else "material" if name in materials else "block"
        e = entry(iid)
        e.update({"category": cat, "icon": icon(iid)})
        if cat == "material":
            m = materials[name]
            e.update({"rarity": m["rarity"], "burn": m["burn"],
                      "source_hint": ja.get(f"item.{NS}.{name}.source") or en.get(f"item.{NS}.{name}.source"),
                      "dropped_by": drop_sources.get(iid, []),
                      "made_by": [{"type": r["type"], "count": r["count"], "inputs": r["inputs"]} for r in made_by.get(iid, [])],
                      "used_in": [{"type": r["type"], "output": r["output"], "count": r["count"]} for r in used_in.get(iid, [])]})
        if cat == "egg": e["colors"] = ["#%06X" % c for c in eggs[name]]
        items.append(e)

    known = {i["id"] for i in items}
    extra = sorted({x for t in loot for d in t["drops"] for x in [d["item"]] if x not in known} |
                   {x for r in recipes for x in [r["output"]] + [i["item"] for i in r["inputs"]]
                    if x and x not in known and not x.startswith(NS)})
    externals = [dict(entry(x), icon=icon(x)) for x in extra]

    drop_tables, empty_tables = [], []
    for t in loot:
        if not t["drops"]:
            empty_tables.append(t["table"]); continue
        other = [d for d in t["drops"] if d["item"] != t["source"]]
        if not other and t["drops"]: continue          # plain self-drop blocks: listed under block items
        drop_tables.append({"table": t["table"], "kind": t["kind"], "source": entry(t["source"]),
                            "source_icon": icon(t["source"]) if t["kind"] != "entities" else icon(t["source"] + "_spawn_egg"),
                            "drops": t["drops"]})
    self_drop = {t["source"] for t in loot if t["kind"] == "blocks" and t["drops"] and
                 all(d["item"] == t["source"] for d in t["drops"])}
    for e in items:
        if e["category"] == "block": e["drops_self"] = e["id"] in self_drop
    return {"items": items, "external_items": externals, "loot_tables": drop_tables, "empty_loot_tables": empty_tables,
            "icons": {k: v for k, v in icons.items() if v}}

# ---------------------------------------------------------------- HTML
def render_html(cat):
    tpl = read(os.path.join(os.path.dirname(os.path.abspath(__file__)), "item_catalog_template.html"))
    payload = json.dumps(cat, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    return tpl.replace("/*__DATA__*/null", payload)

def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "item_catalog"))
    ap.add_argument("--json-only", action="store_true")
    a = ap.parse_args()
    cat = build()
    os.makedirs(a.out, exist_ok=True)
    slim = copy.deepcopy({k: v for k, v in cat.items() if k != "icons"})
    for lst in (slim["items"], slim["external_items"]):
        for e in lst: e.pop("icon", None)
    for t in slim["loot_tables"]: t.pop("source_icon", None)
    jp = os.path.join(a.out, "item_catalog.json")
    json.dump(slim, open(jp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    written = [jp]
    if not a.json_only:
        hp = os.path.join(a.out, "item_catalog.html")
        open(hp, "w", encoding="utf-8").write(render_html(cat))
        written.append(hp)
    counts = {}
    for e in cat["items"]: counts[e["category"]] = counts.get(e["category"], 0) + 1
    missing = [e["id"] for e in cat["items"] + cat["external_items"] if not e.get("icon") and not e["id"].startswith("#")]
    print(json.dumps({"written": written, "items": counts, "external": len(cat["external_items"]),
                      "drop_tables": len(cat["loot_tables"]), "missing_icons": missing}, ensure_ascii=False))

if __name__ == "__main__":
    main()
