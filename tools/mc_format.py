"""Minecraft 1.21.1 / NeoForge 21.1 <-> Forge 1.20.1 data and JSON formats (NeoForge branch only).

The NeoForge 1.21.1 branch is the base; features are ported to Forge 1.20.1 (main).  The data kept here is 1.21:

    python tools/mc_format.py --to-forge F:/Java/Abyssia [--dry-run] [--check]
        1.21 -> 1.20: write this tree's data (and model JSON) into the Forge checkout in the 1.20 folders / shapes.
        Only recipes, loot tables, tags and models are written, and only when their content changes; 1.21-only data
        (enchantments, ...) is skipped; other data (worldgen, dimension types, ...) that differs is listed as
        ``unconverted_diffs`` for a hand port, never written; files in the Forge tree that this tree does not
        produce are listed as orphans (never deleted).
        --check exits 1 when the Forge tree differs (a parity check); --dry-run writes nothing.

``downgrade`` (path) and ``downgrade_json`` (content) are the 1.21 -> 1.20 functions, both idempotent (1.20 input
passes through unchanged).  A 1.21 feature with no 1.20 equivalent (item components in a recipe result, an enchantment
predicate on a list or tag, ...) raises ValueError: port it by hand.

``upgrade`` (path) and ``upgrade_json`` (content) go the other way, 1.20 -> 1.21.  The generators of this branch
(gen_deep_assets, gen_fauna, tools/bt01, ...) still build recipes, loot tables and tags in the 1.20 shapes and pass
every data file through them, so they keep working; 1.21-shaped input passes through unchanged.  New generator code
may write 1.21 directly.

Folders (1.20 -> 1.21): loot_tables -> loot_table, recipes -> recipe, advancements -> advancement, structures -> structure,
tags/blocks -> tags/block, items -> item, entity_types -> entity_type, fluids -> fluid, game_events -> game_event.
Recipes: result {"id", "count"} (1.20: crafting / smithing {"item", "count"}; stonecutting a string result with a
top-level count; a cooking recipe a string result, whose top-level count Forge 1.20.1 ignored).
Loot: match_tool enchantments -> predicates."minecraft:enchantments", tag -> items "#tag", nbt -> custom_data;
looting_enchant -> enchanted_count_increase, random_chance_with_looting -> random_chance_with_enchanted_bonus,
set_nbt -> set_custom_data.  Tags anywhere: forge:x -> c:x (1.21 common tag convention, FORGE_TAGS for renames).
Models: an element's forge_data -> neoforge_data.
"""
import copy
import json
import os
import re
import subprocess
import sys

FOLDERS = {"loot_tables": "loot_table", "recipes": "recipe", "advancements": "advancement", "structures": "structure"}
TAG_FOLDERS = {"blocks": "block", "items": "item", "entity_types": "entity_type", "fluids": "fluid",
               "game_events": "game_event"}
# forge:<x> tags whose c: name is not c:<x> (NeoForge 21.1 data/c/tags); everything else maps to c:<x>.
FORGE_TAGS = {"glass": "glass_blocks", "stone": "stones", "cobblestone": "cobblestones", "sand": "sands",
              "gravel": "gravels", "obsidian": "obsidians", "netherrack": "netherracks", "slimeballs": "slime_balls",
              "string": "strings", "leather": "leathers", "gunpowder": "gunpowders", "shears": "tools/shear",
              "fruits": "foods/fruit", "vegetables": "foods/vegetable", "berries": "foods/berry"}
COOKING = {"minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking"}
INGREDIENT_KEYS = ("ingredient", "ingredients", "base", "addition", "template")


# ================================================================ paths

def upgrade(path):
    """The 1.21 location of a data file given by its 1.20 (or already 1.21) path; separators are kept."""
    path = _FOLDER_RE.sub(lambda m: m.group(1) + FOLDERS[m.group(2)], path)
    return _TAG_RE.sub(lambda m: m.group(1) + TAG_FOLDERS[m.group(2)], path)


_FOLDER_RE = re.compile(r"((?:^|[\\/])data[\\/][^\\/]+[\\/])(" + "|".join(FOLDERS) + r")(?=[\\/]|$)")
_TAG_RE = re.compile(r"((?:^|[\\/])data[\\/][^\\/]+[\\/]tags[\\/])(" + "|".join(TAG_FOLDERS) + r")(?=[\\/]|$)")


def kind(path):
    """'recipe', 'loot_table', 'tags', 'model' or None for a (1.21) data or asset path."""
    parts = re.split(r"[\\/]", path)
    for i in range(len(parts) - 2):
        if parts[i] == "data" and parts[i + 2] in ("recipe", "loot_table", "tags"):
            return parts[i + 2]
        if parts[i] == "assets" and parts[i + 2] == "models":
            return "model"
    return None


# ================================================================ content

def tag_id(t):
    """forge:x -> c:x (with the common-tag renames); other tags unchanged.  Keeps a leading '#'."""
    hash_ = t.startswith("#")
    ns, _, name = t.lstrip("#").partition(":")
    if ns != "forge":
        return t
    return ("#" if hash_ else "") + "c:" + FORGE_TAGS.get(name, name)


def _ingredient(x):
    if isinstance(x, list):
        return [_ingredient(v) for v in x]
    if isinstance(x, dict) and "tag" in x:
        return {k: (tag_id(v) if k == "tag" else v) for k, v in x.items()}
    return x


def _result(r, count=None):
    if isinstance(r, str):
        r = {"id": r}
    else:
        unknown = set(r) - {"item", "id", "count"}
        if unknown:
            raise ValueError(f"recipe result with {unknown}: port by hand (1.21 uses components)")
        r = {("id" if k == "item" else k): v for k, v in r.items()}
    if count is not None and "count" not in r:
        r["count"] = count
    return r


def recipe(obj):
    obj = copy.deepcopy(obj)
    for k in INGREDIENT_KEYS:
        if k in obj:
            obj[k] = _ingredient(obj[k])
    if isinstance(obj.get("key"), dict):
        obj["key"] = {c: _ingredient(v) for c, v in obj["key"].items()}
    if "result" in obj:
        count = obj.pop("count", None)
        if obj.get("type") in COOKING:
            count = None                     # Forge 1.20.1 ignored a cooking recipe's top-level count
        obj["result"] = _result(obj["result"], count)
    return obj


def _match_tool(pred):
    pred = dict(pred)
    if "tag" in pred:
        pred["items"] = "#" + tag_id(pred.pop("tag"))
    sub = dict(pred.get("predicates", {}))
    if "enchantments" in pred:
        sub["minecraft:enchantments"] = [{("enchantments" if k == "enchantment" else k): v for k, v in e.items()}
                                         for e in pred.pop("enchantments")]
    if "nbt" in pred:
        sub["minecraft:custom_data"] = pred.pop("nbt")
    if sub:
        pred["predicates"] = sub
    return pred


def _loot_node(o):
    if o.get("condition") == "minecraft:match_tool" and "predicate" in o:
        o["predicate"] = _match_tool(o["predicate"])
    if o.get("condition") == "minecraft:random_chance_with_looting":
        chance, per = o["chance"], o["looting_multiplier"]
        o.clear()
        o.update({"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                  "unenchanted_chance": chance,
                  "enchanted_chance": {"type": "minecraft:linear", "base": round(chance + per, 6), "per_level_above_first": per}})
    if o.get("function") == "minecraft:looting_enchant":
        o["function"] = "minecraft:enchanted_count_increase"
        o.setdefault("enchantment", "minecraft:looting")
    if o.get("function") == "minecraft:set_nbt":
        o["function"] = "minecraft:set_custom_data"
    if o.get("type") == "minecraft:tag" and isinstance(o.get("name"), str):
        o["name"] = tag_id(o["name"])


def _walk(o, f):
    if isinstance(o, dict):
        f(o)
        for v in o.values():
            _walk(v, f)
    elif isinstance(o, list):
        for v in o:
            _walk(v, f)


def loot_table(obj):
    obj = copy.deepcopy(obj)
    _walk(obj, _loot_node)
    return obj


def tag(obj):
    obj = copy.deepcopy(obj)
    vals = obj.get("values", [])
    for i, v in enumerate(vals):
        if isinstance(v, str):
            vals[i] = tag_id(v) if v.startswith("#") else v
        elif isinstance(v, dict) and isinstance(v.get("id"), str) and v["id"].startswith("#"):
            v["id"] = tag_id(v["id"])
    return obj


def upgrade_json(path, obj):
    """The 1.21 content of a data file (``path`` may be the 1.20 or the 1.21 location)."""
    k = kind(upgrade(path))
    if k == "recipe":
        return recipe(obj)
    if k == "loot_table":
        return loot_table(obj)
    if k == "tags":
        return tag(obj)
    if k == "model":
        return model(obj)
    return obj


def model(obj):
    """Block/item model: an element's forge_data (per-element light, e.g. emissive glow layers) is neoforge_data in
    NeoForge 21.1; a model still using forge_data fails to load (missing-texture cube)."""
    if not any("forge_data" in e for e in obj.get("elements", ())):
        return obj
    obj = copy.deepcopy(obj)
    for e in obj["elements"]:
        if "forge_data" in e:
            e["neoforge_data"] = e.pop("forge_data")
    return obj


# ================================================================ in-place migration of the committed data

def _dump(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def migrate(data_root, dry_run=False):
    """git mv the 1.20 folders to their 1.21 names, then rewrite the JSON whose content changes."""
    report = {"moved_dirs": [], "rewritten": 0, "files": 0}
    for ns in sorted(os.listdir(data_root)):
        nsdir = os.path.join(data_root, ns)
        moves = [(os.path.join(nsdir, a), os.path.join(nsdir, b)) for a, b in FOLDERS.items()]
        moves += [(os.path.join(nsdir, "tags", a), os.path.join(nsdir, "tags", b)) for a, b in TAG_FOLDERS.items()]
        for src, dst in moves:
            if not os.path.isdir(src):
                continue
            report["moved_dirs"].append(os.path.relpath(src, data_root).replace("\\", "/") + " -> "
                                        + os.path.relpath(dst, data_root).replace("\\", "/"))
            if dry_run:
                continue
            if os.path.exists(dst):
                # merge: move file by file (e.g. another agent already created tags/block)
                for root, _, files in os.walk(src):
                    for f in files:
                        s = os.path.join(root, f)
                        d = os.path.join(dst, os.path.relpath(s, src))
                        if os.path.exists(d):
                            raise SystemExit(f"both exist: {s} and {d}")
                        os.makedirs(os.path.dirname(d), exist_ok=True)
                        subprocess.run(["git", "mv", s, d], check=True, cwd=data_root)
            else:
                subprocess.run(["git", "mv", src, dst], check=True, cwd=data_root)
    for root, _, files in os.walk(data_root):
        for f in files:
            if not f.endswith(".json"):
                continue
            p = os.path.join(root, f)
            report["files"] += 1
            with open(p, encoding="utf-8") as fh:
                text = fh.read()
            obj = json.loads(text)
            new = upgrade_json(p, obj)
            if new != obj:
                report["rewritten"] += 1
                if not dry_run:
                    with open(p, "w", encoding="utf-8") as fh:      # platform newlines, like the generators
                        fh.write(_dump(new))
    return report


def migrate_assets(assets_root, dry_run=False):
    """Rewrite the model JSON whose content changes (no folder renames in assets)."""
    report = {"rewritten": 0, "files": 0}
    for root, _, files in os.walk(assets_root):
        for f in files:
            p = os.path.join(root, f)
            if not f.endswith(".json") or kind(p) != "model":
                continue
            report["files"] += 1
            with open(p, encoding="utf-8") as fh:
                obj = json.load(fh)
            new = upgrade_json(p, obj)
            if new != obj:
                report["rewritten"] += 1
                if not dry_run:
                    with open(p, "w", encoding="utf-8") as fh:
                        fh.write(_dump(new))
    return report


# ================================================================ 1.21 -> 1.20 (port to the Forge branch)

REV_FOLDERS = {v: k for k, v in FOLDERS.items()}
REV_TAG_FOLDERS = {v: k for k, v in TAG_FOLDERS.items()}
REV_FORGE_TAGS = {v: k for k, v in FORGE_TAGS.items()}
_REV_FOLDER_RE = re.compile(r"((?:^|[\\/])data[\\/][^\\/]+[\\/])(" + "|".join(REV_FOLDERS) + r")(?=[\\/]|$)")
_REV_TAG_RE = re.compile(r"((?:^|[\\/])data[\\/][^\\/]+[\\/]tags[\\/])(" + "|".join(REV_TAG_FOLDERS) + r")(?=[\\/]|$)")
# data folders of a namespace that only exist in 1.21: nothing to port, the Forge branch does it differently (or not at all)
ONLY_1_21 = {"enchantment", "enchantment_provider", "jukebox_song", "trim_material", "trim_pattern", "banner_pattern",
             "painting_variant", "wolf_variant", "instrument", "neoforge", "data_maps", "loot_modifiers"}


def _only_1_21(parts):
    """parts = the path below data/, split: <ns>/<folder>/...  True for data that has no 1.20 form."""
    if len(parts) > 1 and parts[1] in ONLY_1_21:
        return True
    if len(parts) > 3 and parts[1] == "tags":
        return parts[2] == "enchantment" or (parts[2] == "item" and parts[3] == "enchantable")
    return False


def downgrade(path):
    """The 1.20 location of a data file given by its 1.21 (or already 1.20) path; separators are kept."""
    path = _REV_FOLDER_RE.sub(lambda m: m.group(1) + REV_FOLDERS[m.group(2)], path)
    path = _REV_TAG_RE.sub(lambda m: m.group(1) + REV_TAG_FOLDERS[m.group(2)], path)
    return _C_TAG_FILE_RE.sub(lambda m: _c_tag_file(m), path)


_C_TAG_FILE_RE = re.compile(r"((?:^|[\\/])data[\\/])c([\\/]tags[\\/][^\\/]+[\\/])(.+)(\.json)$")


def _c_tag_file(m):
    name = m.group(3).replace("\\", "/")
    if name not in REV_FORGE_TAGS:
        return m.group(0)
    return m.group(1) + "forge" + m.group(2) + REV_FORGE_TAGS[name] + m.group(4)


def tag_id_down(t):
    """c:x -> forge:x (undoing the FORGE_TAGS renames); other tags unchanged.  Keeps a leading '#'."""
    hash_ = t.startswith("#")
    ns, _, name = t.lstrip("#").partition(":")
    if ns != "c":
        return t
    return ("#" if hash_ else "") + "forge:" + REV_FORGE_TAGS.get(name, name)


def _ingredient_down(x):
    if isinstance(x, list):
        return [_ingredient_down(v) for v in x]
    if isinstance(x, dict) and "tag" in x:
        return {k: (tag_id_down(v) if k == "tag" else v) for k, v in x.items()}
    return x


def recipe_down(obj):
    obj = copy.deepcopy(obj)
    for k in INGREDIENT_KEYS:
        if k in obj:
            obj[k] = _ingredient_down(obj[k])
    if isinstance(obj.get("key"), dict):
        obj["key"] = {c: _ingredient_down(v) for c, v in obj["key"].items()}
    result = obj.get("result")
    if not isinstance(result, dict) or "id" not in result:
        return obj                                  # a string, or {"item": ..}: already the 1.20 shape
    unknown = set(result) - {"id", "count"}
    if unknown:
        raise ValueError(f"recipe result with {unknown}: item components have no 1.20 form, port by hand")
    rtype = obj.get("type")
    if rtype in COOKING:
        # 1.20.1 takes a plain item id, or an {"item", "count"} object when it yields more than one
        obj["result"] = {"item": result["id"], "count": result["count"]} if result.get("count", 1) != 1 else result["id"]
    elif rtype == "minecraft:stonecutting":
        obj["result"] = result["id"]
        obj["count"] = result.get("count", 1)       # required by the 1.20 serializer
    else:
        obj["result"] = {"item": result["id"], **({"count": result["count"]} if "count" in result else {})}
    return obj


def _match_tool_down(pred):
    pred = dict(pred)
    items = pred.get("items")
    if isinstance(items, str) and items.startswith("#"):
        pred["tag"] = tag_id_down(items[1:])
        del pred["items"]
    sub = dict(pred.get("predicates", {}))
    if "minecraft:enchantments" in sub:
        entries = []
        for e in sub.pop("minecraft:enchantments"):
            e = dict(e)
            if "enchantments" in e:
                ench = e.pop("enchantments")
                if isinstance(ench, list) and len(ench) == 1:
                    ench = ench[0]
                if not isinstance(ench, str) or ench.startswith("#"):
                    raise ValueError(f"enchantment predicate on {ench}: 1.20 matches one enchantment, port by hand")
                e = {"enchantment": ench, **e}
            entries.append(e)
        pred["enchantments"] = entries
    if "minecraft:custom_data" in sub:
        pred["nbt"] = sub.pop("minecraft:custom_data")
    if sub:
        raise ValueError(f"item predicates {sorted(sub)}: 1.21 components have no 1.20 form, port by hand")
    pred.pop("predicates", None)
    return pred


def _loot_node_down(o):
    if o.get("condition") == "minecraft:match_tool" and "predicate" in o:
        o["predicate"] = _match_tool_down(o["predicate"])
    if o.get("condition") == "minecraft:random_chance_with_enchanted_bonus":
        chance, bonus = o["unenchanted_chance"], o["enchanted_chance"]
        if o.get("enchantment") != "minecraft:looting" or not isinstance(bonus, dict) \
                or bonus.get("type") != "minecraft:linear":
            raise ValueError("random_chance_with_enchanted_bonus on something but a linear looting chance: port by hand")
        per = bonus["per_level_above_first"]
        if abs(bonus["base"] - (chance + per)) > 1e-6:
            raise ValueError("enchanted_chance base != unenchanted_chance + per level: no looting_multiplier form, port by hand")
        o.clear()
        o.update({"condition": "minecraft:random_chance_with_looting", "chance": chance, "looting_multiplier": per})
    if o.get("function") == "minecraft:enchanted_count_increase":
        if o.get("enchantment", "minecraft:looting") != "minecraft:looting":
            raise ValueError(f"enchanted_count_increase on {o['enchantment']}: 1.20 only has looting_enchant, port by hand")
        o["function"] = "minecraft:looting_enchant"
        o.pop("enchantment", None)
    if o.get("function") == "minecraft:set_custom_data":
        o["function"] = "minecraft:set_nbt"
    if o.get("type") == "minecraft:tag" and isinstance(o.get("name"), str):
        o["name"] = tag_id_down(o["name"])


def loot_table_down(obj):
    obj = copy.deepcopy(obj)
    _walk(obj, _loot_node_down)
    return obj


def tag_down(obj):
    obj = copy.deepcopy(obj)
    vals = obj.get("values", [])
    for i, v in enumerate(vals):
        if isinstance(v, str):
            vals[i] = tag_id_down(v) if v.startswith("#") else v
        elif isinstance(v, dict) and isinstance(v.get("id"), str) and v["id"].startswith("#"):
            v["id"] = tag_id_down(v["id"])
    return obj


def model_down(obj):
    """An element's neoforge_data (per-element light, e.g. emissive glow layers) is forge_data in Forge 1.20.1."""
    if not any("neoforge_data" in e for e in obj.get("elements", ())):
        return obj
    obj = copy.deepcopy(obj)
    for e in obj["elements"]:
        if "neoforge_data" in e:
            e["forge_data"] = e.pop("neoforge_data")
    return obj


def downgrade_json(path, obj):
    """The 1.20 content of a data file (``path`` may be the 1.21 or the 1.20 location)."""
    k = kind(upgrade(path))
    if k == "recipe":
        return recipe_down(obj)
    if k == "loot_table":
        return loot_table_down(obj)
    if k == "tags":
        return tag_down(obj)
    if k == "model":
        return model_down(obj)
    return obj


# ================================================================ writing the converted tree into the Forge checkout

def _rel(path, root):
    return os.path.relpath(path, root).replace("\\", "/")


def to_forge(src_res, dst_res, dry_run=False):
    """Convert this tree's src/main/resources (data, and model JSON of assets) into the Forge tree's.

    Returns a report: ``written`` (new or changed files), ``unchanged``, ``skipped_1_21_only``, ``orphans`` (in the
    Forge tree, not produced from here; never deleted) and ``errors`` (files that need a hand port; not written).
    """
    report = {"written": [], "unchanged": 0, "skipped_1_21_only": [], "unconverted": [], "orphans": [], "errors": []}
    produced = set()
    for part in ("data", "assets"):
        src_root = os.path.join(src_res, part)
        for root, _, files in os.walk(src_root):
            for f in files:
                p = os.path.join(root, f)
                rel = _rel(p, src_root)
                parts = rel.split("/")
                if part == "data" and _only_1_21(parts):
                    report["skipped_1_21_only"].append(f"data/{rel}")
                    continue
                is_json = f.endswith(".json")
                if part == "assets" and not (is_json and kind(p) == "model"):
                    continue
                out_rel = downgrade(f"{part}/{rel}")[len(part) + 1:]
                dst = os.path.join(dst_res, part, *out_rel.split("/"))
                produced.add(os.path.normcase(dst))
                if is_json:
                    with open(p, encoding="utf-8") as fh:
                        obj = json.load(fh)
                    try:
                        new = downgrade_json(p, obj)
                    except ValueError as e:
                        report["errors"].append(f"{part}/{rel}: {e}")
                        continue
                    same = False
                    if os.path.exists(dst):
                        with open(dst, encoding="utf-8") as fh:
                            same = json.load(fh) == new
                else:
                    with open(p, "rb") as fh:
                        raw = fh.read()
                    new = None
                    same = os.path.exists(dst) and open(dst, "rb").read() == raw
                if same:
                    report["unchanged"] += 1
                    continue
                if not is_json or kind(upgrade(p)) is None:
                    # worldgen, dimension types, ...: the 1.21 and 1.20 shapes differ in ways this module does not
                    # convert (e.g. a nested uniform int provider), so these are ported by hand and only reported
                    report["unconverted"].append(f"{part}/{out_rel}")
                    continue
                report["written"].append(f"{part}/{out_rel}")
                if not dry_run:
                    os.makedirs(os.path.dirname(dst), exist_ok=True)
                    if is_json:
                        with open(dst, "w", encoding="utf-8") as fh:        # platform newlines, like the generators
                            fh.write(_dump(new))
                    else:
                        with open(dst, "wb") as fh:
                            fh.write(raw)
    for root, _, files in os.walk(os.path.join(dst_res, "data")):
        for f in files:
            p = os.path.join(root, f)
            if os.path.normcase(p) not in produced:
                report["orphans"].append(_rel(p, dst_res))
    return report


if __name__ == "__main__":
    args = sys.argv[1:]
    dry = "--dry-run" in args
    res = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources"))
    if "--to-forge" not in args:
        sys.exit(__doc__)
    dst_res = os.path.join(args[args.index("--to-forge") + 1], "src", "main", "resources")
    rep = to_forge(res, dst_res, dry)
    summary = {"written": len(rep["written"]), "unchanged": rep["unchanged"], "skipped_1_21_only": len(rep["skipped_1_21_only"]),
               "unconverted_diffs": len(rep["unconverted"]), "orphans": len(rep["orphans"]), "errors": len(rep["errors"])}
    print(json.dumps({"summary": summary, "written": rep["written"][:50], "unconverted_diffs": rep["unconverted"][:50], "orphans": rep["orphans"][:50],
                      "errors": rep["errors"][:50], "dry_run": dry}, indent=2, ensure_ascii=False))
    if "--check" in args and (rep["written"] or rep["errors"] or rep["unconverted"]):
        sys.exit(1)
