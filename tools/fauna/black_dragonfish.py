"""ミツマタヤリウオ / black dragonfish, genus Idiacanthus (modelled on the female of I. atlanticus).

Names: ミツマタヤリウオ strictly belongs to the Pacific blackdragon Idiacanthus antrostomus (JAMSTEC BISMaL
"Idiacanthus antrostomus ミツマタヤリウオ"; Japanese Wikipedia), the congener found off Japan; the black dragonfish
I. atlanticus is a southern-hemisphere species (25-60 S) with no Japanese name of its own. The two are nearly
identical in form, so the model is the genus (as the anglerfish entry does with チョウチンアンコウ).

Research (FishBase summaries "Idiacanthus atlanticus" and "Idiacanthus antrostomus"; Fishes of Australia
"Idiacanthus atlanticus"; Japanese Wikipedia "ミツマタヤリウオ"):
* extreme sexual dimorphism: females to ~40-53 cm, black, scaleless, with fang-like canine teeth, pelvic fins and
  a chin barbel about twice the head length ending in an unpigmented, laterally flattened luminous tip; males are
  ~5 cm, brown, toothless, without barbel or pelvic fins, and do not feed as adults
* body extremely elongate (depth 2-6 % of length), small head; no pectoral fins; long dorsal and anal fins set
  toward the tail
* photophores: a postorbital organ behind the eye (small in females, larger than the eye in males), two rows of
  small photophores along each lower side, many tiny ones over head and body
* females live below 500 m by day and rise toward the surface at night to feed, mostly on fishes; males stay at
  1000-2000 m; I. antrostomus is recorded 0-1103 m, mainly 300-1000 m
* larvae carry their eyes on stalks up to half the body length

Game simplifications: only females appear; the spawn band merges the day depth and the night rise; the barbel hangs
from the front of the snout (the fish template has no chin attachment); the colour of the barbel light is not well
documented - painted the blue-white usual for stomiid barbels; harmless to players.
"""

INFO = dict(
    names=("Black dragonfish", "ミツマタヤリウオ"),
    egg=(0x0E1014, 0x8FD0FF),
    role="predator",
    voice="anglerfish",
    prey=["lanternfish", "hatchetfish", "minecraft:cod"],
    sounds={
        "ambient": (2, "Dragonfish swims", "ミツマタヤリウオが泳ぐ"),
        "hurt": (2, "Dragonfish hurts", "ミツマタヤリウオが傷つく"),
        "death": (1, "Dragonfish dies", "ミツマタヤリウオが死ぬ"),
        "flop": (2, "Dragonfish flops", "ミツマタヤリウオが跳ねる"),
        "snap": (2, "Dragonfish strikes", "ミツマタヤリウオが食らいつく"),
    },
    # females: below 500 m by day, shallower at night; mainly 300-1000 m (I. antrostomus), to ~2000 m
    spawn=dict(weight=5, group=(1, 1), depth_m=(200, 500, 1000, 2000), placement="open_water",
               cave_factor=0.5, open_factor=1.0, max_light=6, cap=(24, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.4, health=6, attack=1,
    # rises toward the surface at night; the glowing chin barbel lures small fish
    traits=dict(steering=(18, 5, 0.0022), zone=("open_water", 1.0, 200), migrates=0.4, flees=(2.5, 8), hunts=(1.6, 0.35, 0.4),
                hangs_still=True, home=32, ambient=450),
    render=dict(eyeshine=(0.5, ["right_eye", "left_eye"]),
                glow=dict(bones=["esca", "body", "head"], halos=[("esca", 0.2, 0.6, 0.8, 1.0)], base=0.8, flicker=0.2)))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="viper_flesh", count=(1, 2), looting=1, chance=0.7)]
