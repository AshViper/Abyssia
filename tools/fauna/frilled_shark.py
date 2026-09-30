"""ラブカ / frilled shark, Chlamydoselachus anguineus.

Research (FishBase summary "Chlamydoselachus anguineus"; Wikipedia "Frilled shark"; Animal Diversity Web;
Kubota et al. 1991 on its diet in Suruga Bay):
* 0-1570 m, usually 120-1280 m (500-1000 m often cited), on outer shelves and upper slopes; well known from
  Suruga and Sagami Bays; may move shallower at night
* up to ~2 m; eel-like dark brown body, flattened snake-like head, six pairs of frilled gill slits (the first pair
  meets across the throat), dorsal, pelvic and anal fins set far back
* ~300 small three-pronged teeth in ~25 rows, backward-pointing, for hooking soft prey
* about 60 % of the diet is squid (Chiroteuthis, Histioteuthis, Onychoteuthis, Todarodes...), plus bony fishes and
  other sharks; thought to strike like a snake from a bent body

Game simplifications: neutral (bites back only when hurt); prey includes vanilla squid and cod and the mod's
viperfish and shrimp.
"""

INFO = dict(
    names=("Frilled Shark", "ラブカ"),
    egg=(0x3E3A36, 0x8A7A6A),
    role="predator",
    voice="shark",
    prey=["minecraft:squid", "minecraft:glow_squid", "minecraft:cod", "viperfish", "deep_sea_shrimp"],
    sounds={
        "ambient": (2, "Frilled shark swims", "ラブカが泳ぐ"),
        "hurt": (2, "Frilled shark hurts", "ラブカが傷つく"),
        "death": (1, "Frilled shark dies", "ラブカが死ぬ"),
        "flop": (2, "Frilled shark flops", "ラブカが跳ねる"),
        "bite": (2, "Frilled shark lunges", "ラブカが食らいつく"),
    },
    spawn=dict(weight=4, group=(1, 1), depth_m=(50, 500, 1000, 1570), placement="near_floor",
               cave_factor=0.4, open_factor=1.0, max_light=8, clearance=2, cap=(12, 96)),
)
