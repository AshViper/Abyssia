"""AB02 crust ores: ore veins inside the solid abyss crust (Y -376 .. -1862), by depth band, excavator tier and biome.

Generator module for tools/gen_worldgen.py (it does `try: import crust_ores`).  Nothing here writes files by itself.

CALL PROTOCOL (tools/gen_worldgen.py)
-------------------------------------
1. In main(), BEFORE the `for name, cf in CONFIGURED.items(): write(...)` / `for name, (cf, placement) in PLACED.items()` loops
   (e.g. right after abyss_features()), call once:

       crust_ores.register(CONFIGURED, PLACED)

   It adds every crust vein feature to the two dicts in gen_worldgen's own shapes (CONFIGURED[name] = configured feature,
   PLACED[name] = (configured name, placement list)); the normal write loops then write
   configured_feature/<name>.json and placed_feature/<name>.json.  (Equivalent alternative that goes through the
   `feature(name, configured, placement)` helper instead: `crust_ores.write_features(feature)`; use one of the two, not both.)

2. For each abyss biome id (ABYSS_BIOMES key, with or without the "abyssia:" prefix) put the list in decoration step 6
   (UNDERGROUND_ORES), in this order:

       features[6] = ordered(crust_ores.VEIN_FEATURE_ORDER, crust_ores.biome_features(name))

   `VEIN_FEATURE_ORDER` is one global order of all crust feature names (no namespace); `biome_features` returns names in that order
   already, so `[A(f) for f in crust_ores.biome_features(name)]` is equally valid.  The two lists never overlap the seabed
   VEIN_ORDER (all names start with "crust_"), so the feature-order cycle check passes.

3. The placed features have no abyssia:deep_floor / abyss_floor step: their placement is
   [count | rarity_filter, abyssia:config (mk0_ore | mk1_ore | mk2_ore), in_square, height_range uniform absolute [yLo, yHi],
   minecraft:biome]; gen_worldgen's "every deep feature has a floor or a height_range" assert is satisfied by the height_range.

Names: crust_<tier>_<metal>_<band>_<size>[_s]   tier mk0|mk1|mk2, band b (B' -650..-376) | c | d | e, size small|medium|large|huge;
a trailing _s marks the secondary (low-rate) biome group of the same vein (shares the configured feature of the primary one).
Census: `python tools/crust_ores.py` prints the expected veins per chunk per band / tier / biome.  All numbers below are tunable.
"""
import sys

A = lambda n: "abyssia:" + n

# ================================================================ bands (absolute Y of the placement point, inclusive)
# A vein reaches at most 13 blocks from the placed point (OreVeinFeature.MAX_REACH), so B' stops at -393 (the vein stays below
# -380) and E starts at -1855 (stays >= 4 above the world bottom -1872).
BANDS = {          # id: (y_lo, y_hi, label)
    "b": (-650, -393, "B'"),
    "c": (-1100, -650, "C"),
    "d": (-1550, -1100, "D"),
    "e": (-1855, -1550, "E"),
}
BAND_ORDER = ["b", "c", "d", "e"]

BIOMES = ["abyss_plain", "abyss_garden", "abyss_crystal", "abyss_toxic", "abyss_toxic_vents", "abyss_volcanic",
          "abyss_magma", "abyss_geothermal", "abyss_frozen", "abyss_cryo", "abyss_anomaly", "abyss_ruins"]
SIZES = ["small", "medium", "large", "huge"]       # Config ore: small 5-15, medium 15-40, large 40-100, huge 100-300 blocks of ore volume
TIER_OPTION = {"mk0": "mk0_ore", "mk1": "mk1_ore", "mk2": "mk2_ore"}   # ConfigPlacement options (Config crust_ore section)

# ================================================================ metals
# metal: (tier, ore block, host rock painted around the ore).  Blocks verified in tools/gen_deep_assets.py (MINERALS / RARE_METALS /
# VANILLA_MINERALS).  No coal exists in the mod.
METALS = {
    # MK0: vanilla-item ores (drops the vanilla item; excavator Mk1 mines them)
    "iron": ("mk0", "abyssal_iron_ore", "mineral_host_rock"),
    "copper": ("mk0", "deep_copper_ore", "mineral_host_rock"),
    "gold": ("mk0", "abyssal_gold_ore", "mineral_host_rock"),
    "redstone": ("mk0", "abyssal_redstone_ore", "mineral_host_rock"),
    "lapis": ("mk0", "abyssal_lapis_ore", "mineral_host_rock"),
    "diamond": ("mk0", "abyssal_diamond_ore", "mineral_host_rock"),
    "emerald": ("mk0", "abyssal_emerald_ore", "mineral_host_rock"),
    # MK1
    "cobalt": ("mk1", "cobalt_ore", "mineral_host_rock"),
    "nickel": ("mk1", "deep_nickel_ore", "mineral_host_rock"),
    "manganese": ("mk1", "manganese_ore", "mineral_host_rock"),
    "titanium": ("mk1", "titanium_ore", "abyssal_rock"),
    "lead": ("mk1", "lead_ore", "mineral_host_rock"),
    "molybdenum": ("mk1", "molybdenum_ore", "abyssal_rock"),
    "vanadium": ("mk1", "vanadium_ore", "abyssal_rock"),
    "zinc": ("mk1", "zinc_ore", "thermal_rock"),
    # MK2
    "tungsten": ("mk2", "tungsten_ore", "trench_rock"),
    "platinum": ("mk2", "platinum_ore", "trench_rock"),
    "tellurium": ("mk2", "tellurium_ore", "trench_rock"),
    "iridium": ("mk2", "iridium_ore", "trench_rock"),
    "uranium": ("mk2", "uranium_ore", "abyssal_rock"),
    "neodymium": ("mk2", "neodymium_ore", "abyssal_rock"),
    "yttrium": ("mk2", "yttrium_ore", "abyssal_rock"),
    "thorium": ("mk2", "thorium_ore", "trench_rock"),
}

# ================================================================ density model
# A density is "veins per chunk per 100 blocks of height" inside the biome the vein is listed for.  Attempts per chunk of a
# feature = density * band thickness / 100 (a placement outside the feature's biome is dropped by minecraft:biome, so the density
# is per volume of that biome, independent of how much of the chunk it covers).

# ---- MK0: the supply.  Present in every band at all 12 biomes (never zero); common in the plain/primary biomes.
MK0_BAND = {"b": 1.0, "c": 0.85, "d": 0.70, "e": 0.55}       # falls gently with depth
MK0_SECONDARY = 0.4                                           # every other biome: 40 % of the primary rate
MK0_RULES = {
    # metal: (primary biomes, {size: density in a primary biome in band B'})
    "iron": (["abyss_plain", "abyss_garden", "abyss_ruins", "abyss_crystal"], {"small": 0.50, "medium": 0.30}),
    "copper": (["abyss_plain", "abyss_garden", "abyss_toxic_vents", "abyss_geothermal"], {"small": 0.35, "medium": 0.25}),
    "gold": (["abyss_plain", "abyss_volcanic", "abyss_magma", "abyss_geothermal", "abyss_ruins"], {"small": 0.10, "medium": 0.12}),
    "redstone": (["abyss_plain", "abyss_volcanic", "abyss_magma", "abyss_anomaly"], {"small": 0.12, "medium": 0.14}),
    "lapis": (["abyss_plain", "abyss_crystal", "abyss_frozen", "abyss_cryo", "abyss_ruins"], {"small": 0.10, "medium": 0.12}),
    "diamond": (["abyss_plain", "abyss_crystal", "abyss_cryo", "abyss_magma"], {"small": 0.10}),
    "emerald": (["abyss_plain", "abyss_garden", "abyss_crystal", "abyss_ruins"], {"small": 0.08}),
}

# ---- MK1: cobalt nickel manganese titanium lead molybdenum vanadium zinc.  Main region C (giant caverns), B' a bit less,
# thinning in D, rare in E.
MK1_BAND = {"b": 0.7, "c": 1.0, "d": 0.35, "e": 0.10}
MK1_SIZES = {"small": 0.12, "medium": 0.18, "large": 0.04, "huge": 0.008}
MK1_SECONDARY = 0.2                                           # other biomes, small + medium veins only
MK1_SECONDARY_SIZES = ("small", "medium")
MK1_RULES = {      # metal: primary biomes (the rest of the 12 are secondary)
    "cobalt": ["abyss_plain", "abyss_crystal", "abyss_garden"],
    "nickel": ["abyss_plain", "abyss_volcanic", "abyss_geothermal", "abyss_magma"],
    "manganese": ["abyss_plain", "abyss_garden", "abyss_toxic", "abyss_ruins"],
    "titanium": ["abyss_plain", "abyss_volcanic", "abyss_frozen"],
    "lead": ["abyss_ruins", "abyss_toxic", "abyss_toxic_vents", "abyss_plain"],
    "molybdenum": ["abyss_plain", "abyss_geothermal", "abyss_volcanic"],
    "vanadium": ["abyss_plain", "abyss_toxic_vents", "abyss_crystal"],
    "zinc": ["abyss_toxic_vents", "abyss_geothermal", "abyss_volcanic", "abyss_toxic"],
}
MK1_BIAS = {       # per-metal band multipliers on top of MK1_BAND (shallow vs deep leaning metals)
    "lead": {"b": 1.4, "d": 0.8}, "zinc": {"b": 1.3}, "manganese": {"b": 1.3},
    "vanadium": {"d": 1.6, "e": 1.5}, "molybdenum": {"d": 1.5, "e": 1.5}, "titanium": {"d": 1.2},
}

# ---- MK2: tungsten platinum tellurium iridium uranium neodymium yttrium thorium.  Mainly below Y -650; each metal has a few
# preferred biomes only, so no single cavern or biome supplies everything.
MK2_SIZES = {"small": 0.10, "medium": 0.07, "large": 0.02, "huge": 0.005}
MK2_BAND = {"c": 0.4, "d": 1.0, "e": 1.8}
MK2_BAND_SIZES = {"c": ("small", "medium"), "d": ("small", "medium", "large"), "e": SIZES}   # huge veins only in E
MK2_METAL = {"iridium": 0.6, "tellurium": 0.9}                # rarest metals
MK2_E_WEIGHT = {"uranium": 0.55, "thorium": 0.55, "neodymium": 0.55, "yttrium": 0.55}   # E richness is for pt/te/ir/w
MK2_SECONDARY = 0.08                                          # D/E only: other biomes get a trickle of small + medium veins
MK2_SECONDARY_SIZES = ("small", "medium")
MK2_RULES = {      # band: {metal: primary biomes}
    "c": {                                                    # early ones, only in the dangerous special biomes
        "neodymium": ["abyss_frozen", "abyss_cryo"], "yttrium": ["abyss_frozen", "abyss_cryo"],
        "uranium": ["abyss_volcanic", "abyss_magma", "abyss_geothermal"],
        "thorium": ["abyss_volcanic", "abyss_magma", "abyss_geothermal"],
    },
    "d": {                                                    # all eight, each in 1-3 biomes
        "tungsten": ["abyss_magma", "abyss_volcanic"], "platinum": ["abyss_ruins", "abyss_crystal"],
        "tellurium": ["abyss_toxic", "abyss_toxic_vents"], "iridium": ["abyss_anomaly"],
        "uranium": ["abyss_geothermal", "abyss_toxic_vents"], "neodymium": ["abyss_frozen", "abyss_cryo", "abyss_crystal"],
        "yttrium": ["abyss_cryo", "abyss_frozen"], "thorium": ["abyss_volcanic", "abyss_geothermal", "abyss_magma"],
    },
    "e": {                                                    # richest: platinum tellurium iridium tungsten in anomaly/ruins/magma
        "platinum": ["abyss_anomaly", "abyss_ruins"], "tellurium": ["abyss_anomaly", "abyss_magma"],
        "iridium": ["abyss_anomaly", "abyss_ruins", "abyss_magma"], "tungsten": ["abyss_magma", "abyss_anomaly", "abyss_ruins"],
        "uranium": ["abyss_geothermal", "abyss_anomaly"], "thorium": ["abyss_magma", "abyss_anomaly"],
        "neodymium": ["abyss_frozen", "abyss_cryo"], "yttrium": ["abyss_cryo", "abyss_anomaly"],
    },
}


# ================================================================ the vein table
class Vein:
    """One (metal, band, size, biome group) vein feature."""
    def __init__(self, metal, band, size, biomes, dens, secondary=False):
        self.metal, self.band, self.size, self.biomes, self.dens, self.secondary = metal, band, size, tuple(biomes), dens, secondary
        self.tier, self.ore, self.host = METALS[metal]
        lo, hi, _ = BANDS[band]
        self.y_lo, self.y_hi = lo, hi
        self.attempts = dens * (hi - lo) / 100.0           # placement attempts per chunk (before the config multiplier)
        self.configured = f"crust_{self.tier}_{metal}_{band}_{size}"
        self.name = self.configured + ("_s" if secondary else "")


def _build():
    veins = []

    def add(metal, band, size, biomes, dens, secondary=False):
        biomes = [b for b in BIOMES if b in biomes]
        if biomes and dens > 0:
            veins.append(Vein(metal, band, size, biomes, round(dens, 5), secondary))

    for metal, (primary, sizes) in MK0_RULES.items():
        rest = [b for b in BIOMES if b not in primary]
        for band in BAND_ORDER:
            for size, d in sizes.items():
                add(metal, band, size, primary, d * MK0_BAND[band])
                add(metal, band, size, rest, d * MK0_BAND[band] * MK0_SECONDARY, True)
    for metal, primary in MK1_RULES.items():
        rest = [b for b in BIOMES if b not in primary]
        for band in BAND_ORDER:
            f = MK1_BAND[band] * MK1_BIAS.get(metal, {}).get(band, 1.0)
            for size, d in MK1_SIZES.items():
                add(metal, band, size, primary, d * f)
                if size in MK1_SECONDARY_SIZES:
                    add(metal, band, size, rest, d * f * MK1_SECONDARY, True)
    for band, rules in MK2_RULES.items():
        for metal, primary in rules.items():
            f = MK2_BAND[band] * MK2_METAL.get(metal, 1.0) * (MK2_E_WEIGHT.get(metal, 1.0) if band == "e" else 1.0)
            for size in MK2_BAND_SIZES[band]:
                add(metal, band, size, primary, MK2_SIZES[size] * f)
                if band in ("d", "e") and size in MK2_SECONDARY_SIZES:
                    add(metal, band, size, [b for b in BIOMES if b not in primary], MK2_SIZES[size] * f * MK2_SECONDARY, True)
    return veins


VEINS = _build()
BY_NAME = {v.name: v for v in VEINS}
assert len(BY_NAME) == len(VEINS), "duplicate crust feature names"

# One global order for decoration step 6: by tier, band, metal (table order), size, primary before secondary.  Every biome gets a
# subsequence of it, so the FeatureSorter never sees a cycle.
_METAL_INDEX = {m: i for i, m in enumerate(METALS)}
_TIER_INDEX = {"mk0": 0, "mk1": 1, "mk2": 2}
VEIN_FEATURE_ORDER = [v.name for v in sorted(VEINS, key=lambda v: (BAND_ORDER.index(v.band), _TIER_INDEX[v.tier], _METAL_INDEX[v.metal],
                                                                   SIZES.index(v.size), v.secondary))]


def biome_features(biome_id):
    """Feature names (no namespace) for decoration step 6 of an abyss biome id ("abyss_magma" or "abyssia:abyss_magma")."""
    biome_id = biome_id.split(":")[-1]
    wanted = {v.name for v in VEINS if biome_id in v.biomes}
    return [n for n in VEIN_FEATURE_ORDER if n in wanted]


# ================================================================ JSON
def _state(name):
    return {"Name": A(name)}


def _count(attempts):
    """Placement modifier for an expected number of attempts per chunk: a constant count when whole, a rarity filter when rare,
    else a weighted count that keeps the exact mean."""
    whole = round(attempts)
    if whole >= 1 and abs(attempts - whole) <= 0.03 * whole:
        return {"type": "minecraft:count", "count": whole}
    if attempts <= 0.34:
        return {"type": "minecraft:rarity_filter", "chance": max(1, round(1 / attempts))}
    lo = int(attempts)
    w_hi = round((attempts - lo) * 100)
    dist = [{"data": lo, "weight": 100 - w_hi}, {"data": lo + 1, "weight": w_hi}]
    return {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": dist}}


def configured_json(v):
    return {"type": A("ore_vein"), "config": {"ore": _state(v.ore), "host": _state(v.host), "size": v.size, "crust": True}}


def placement_json(v):
    return [_count(v.attempts), {"type": A("config"), "option": TIER_OPTION[v.tier]}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": v.y_lo},
                                                          "max_inclusive": {"absolute": v.y_hi}}},
            {"type": "minecraft:biome"}]


def register(configured, placed):
    """Adds every crust vein to gen_worldgen's CONFIGURED / PLACED dicts (see the call protocol above)."""
    for v in VEINS:
        configured.setdefault(v.configured, configured_json(v))
        placed[v.name] = (v.configured, placement_json(v))


def write_features(feature):
    """Alternative to register(): feature(name, configured, placement) is gen_worldgen's helper (same-named configured feature
    per placed feature, so the secondary groups duplicate their configured JSON)."""
    for v in VEINS:
        feature(v.name, configured_json(v), placement_json(v))


# ================================================================ census
def density_by_biome():
    """{(biome, band): {tier: expected veins per chunk-equivalent volume of that biome in the band}} (config multipliers 1.0)."""
    out = {}
    for v in VEINS:
        for b in v.biomes:
            d = out.setdefault((b, v.band), {"mk0": 0.0, "mk1": 0.0, "mk2": 0.0})
            d[v.tier] += v.attempts
    return out


def metal_band_table():
    """{(metal, band): [veins per chunk in the primary biomes, number of primary biomes, of secondary biomes' rate]}"""
    out = {}
    for v in VEINS:
        row = out.setdefault((v.metal, v.band), {"primary": 0.0, "secondary": 0.0, "pbiomes": set()})
        if v.secondary:
            row["secondary"] += v.attempts
        else:
            row["primary"] += v.attempts
            row["pbiomes"].update(v.biomes)
    return out


def main():
    print(f"{len(VEINS)} crust vein features ({sum(1 for v in VEINS if not v.secondary)} primary, {sum(1 for v in VEINS if v.secondary)} secondary)")
    for tier in ("mk0", "mk1", "mk2"):
        print(f"\n== {tier.upper()}: veins per chunk-equivalent volume of a primary biome (all sizes) / secondary, by band")
        print(f"{'metal':<11}" + "".join(f"{BANDS[b][2]:>16}" for b in BAND_ORDER) + "   primary biomes (first band listing)")
        table = metal_band_table()
        for metal, (t, _, _) in METALS.items():
            if t != tier:
                continue
            cells, pb = [], ""
            for b in BAND_ORDER:
                row = table.get((metal, b))
                cells.append("-" if not row else f"{row['primary']:.2f}/{row['secondary']:.2f}")
                if row and not pb:
                    pb = ",".join(x.replace("abyss_", "") for x in BIOMES if x in row["pbiomes"])
            print(f"{metal:<11}" + "".join(f"{c:>16}" for c in cells) + "   " + pb)
    print("\n== Expected veins per chunk-equivalent volume of each biome, by band (mk0 / mk1 / mk2)")
    dens = density_by_biome()
    print(f"{'biome':<18}" + "".join(f"{BANDS[b][2]:>22}" for b in BAND_ORDER))
    for biome in BIOMES:
        cells = []
        for b in BAND_ORDER:
            d = dens.get((biome, b))
            cells.append("-" if not d else f"{d['mk0']:.1f}/{d['mk1']:.1f}/{d['mk2']:.2f}")
        print(f"{biome:<18}" + "".join(f"{c:>22}" for c in cells))
    print("\n== Features per biome (step 6): " + ", ".join(f"{b.replace('abyss_', '')}={len(biome_features(b))}" for b in BIOMES))
    tot = {t: sum(v.attempts for v in VEINS if v.tier == t and not v.secondary) for t in TIER_OPTION}
    print("Primary placement attempts per chunk over all bands: " + ", ".join(f"{t}={x:.1f}" for t, x in tot.items()))


if __name__ == "__main__":
    sys.exit(main())
