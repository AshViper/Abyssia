"""Minecraft 1.21.1 / NeoForge 21.1 data layout and JSON formats (NeoForge branch only).

The generators still build recipes, loot tables and tags in the Forge 1.20.1 shapes of the main branch, so a change
made there ports by copying it.  Their JSON writers pass every data file through ``upgrade`` (path) and
``upgrade_json`` (content), which move it to the 1.21 folder and rewrite the 1.20 shapes.  Both are idempotent:
1.21 input passes through unchanged.

    python tools/mc_format.py [--dry-run]   # migrate src/main/resources/data in place (git mv + rewrite), JSON report

Folders: loot_tables -> loot_table, recipes -> recipe, advancements -> advancement, structures -> structure,
tags/blocks -> tags/block, items -> item, entity_types -> entity_type, fluids -> fluid, game_events -> game_event.
Recipes: result {"id", "count"} (a string result becomes {"id"}; stonecutting's top-level count moves into it; a
cooking recipe's top-level count is dropped: Forge 1.20.1 ignored it, so the recipe yields 1 as before).
Loot: match_tool enchantments -> predicates."minecraft:enchantments", tag -> items "#tag", nbt -> custom_data;
looting_enchant -> enchanted_count_increase, random_chance_with_looting -> random_chance_with_enchanted_bonus,
set_nbt -> set_custom_data.  Tags anywhere: forge:x -> c:x (1.21 common tag convention, FORGE_TAGS for renames).
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
              "string": "strings", "leather": "leathers", "gunpowder": "gunpowders", "shears": "tools/shear"}
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


if __name__ == "__main__":
    res = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
    dry = "--dry-run" in sys.argv
    print(json.dumps({"data": migrate(os.path.normpath(os.path.join(res, "data")), dry),
                      "assets": migrate_assets(os.path.normpath(os.path.join(res, "assets")), dry)}, indent=2))
