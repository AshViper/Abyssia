"""キタクシノハクモヒトデ / notched brittle star, Ophiura sarsii Lütken, 1855.
Echinodermata > Ophiuroidea > Ophiurida > Ophiuridae (WoRMS: accepted).

Picked as the mod's brittle star because it forms the famous deep "brittle-star carpets" off northern Japan.
Japanese name from JAMSTEC BISMaL (9000252); クモヒトデ is only the name of the whole class.

Research (Wikipedia "Ophiura sarsii"; National Museum of Nature and Science, Tokyo, "深海底をおおうクモヒトデのじゅうたん"
(Fujita); JAMSTEC BISMaL 9000252; WoRMS 124934):
* cold-water, North Pacific / North Atlantic / Arctic, shelf and slope to ~3000 m; off northern Japan (Hokkaido to
  Hachinohe/Otsuchi) a band of dense beds at ~200-500 m, more than 100 animals per square metre, almost covering the
  bottom
* soft sediment (mud, muddy fine sand)
* flat round disc usually up to 25 mm (max ~40 mm), notched at the arm bases; five thin jointed arms up to ~90 mm,
  3-4x the disc diameter; red to brown
* carnivorous generalist (amphipods and other small crustaceans, molluscs); the Japanese carpets live largely on
  mesopelagic animals that sink or swim down - lanternfish, krill; long-lived (>10 up to ~27 years)
* crawls by rowing its arms (brittle-star locomotion; no species-specific study found)
* bioluminescence: known in some brittle stars (Amphiura filiformis, Ophiopsila), but NOT reported for Ophiura sarsii
  - so it does not glow

Sources: https://en.wikipedia.org/wiki/Ophiura_sarsii  https://www.kahaku.go.jp/news/nid00000006.html
https://www.godac.jamstec.go.jp/bismal/j/view/9000252  https://marinespecies.org/aphia.php?p=taxdetails&id=124934

Game simplifications: ~0.55 block across (a real one is ~20 cm with arms); groups of several stand in for the
carpets (no hundreds per block); arms row with the generic travelling-wave clip (tentacle_move); harmless, crawls
away from whatever hits it; the deepest records (to ~3000 m) are kept as a thin tail of the spawn range.
"""

INFO = dict(
    names=("Brittle Star", "キタクシノハクモヒトデ"),
    egg=(0x8A5A44, 0xC49A80),
    role="passive",
    voice="crab",
    sounds={
        "ambient": (2, "Brittle star stirs", "キタクシノハクモヒトデがうごめく"),
        "hurt": (2, "Brittle star hurts", "キタクシノハクモヒトデが傷つく"),
        "death": (1, "Brittle star dies", "キタクシノハクモヒトデが死ぬ"),
        "step": (3, None, None),
    },
    # dense beds at 200-500 m off northern Japan; the species reaches ~3000 m; soft bottoms
    spawn=dict(weight=14, group=(3, 6), depth_m=(100, 200, 600, 3000), placement="seabed",
               cave_factor=0.3, open_factor=1.0, max_light=12, cap=(24, 32),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=3.0)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="walker", size_m=0.2, health=4,
    # rows slowly over the mud; stays in its bed
    traits=dict(move_clip="tentacle_move", speed=0.45, wander=240, home=10, ambient=700),
    render=dict(fish=False, pitch=False))
