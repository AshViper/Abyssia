"""チューブワーム (ハオリムシ) / giant tubeworm, Riftia pachyptila.

Research (Wikipedia "Riftia pachyptila"; Animal Diversity Web; Childress et al. 1984 on its blood; Lutz et al. 1994
on its growth rate, Nature):
* hydrothermal vents of the East Pacific Rise and Galapagos Rift, ~2000-2700 m (e.g. 2600 m at 21 N)
* tubes up to ~3 m of white chitin, in dense clumps on basalt around vents; the fastest-growing marine invertebrate
  known (to 1.5 m and maturity in under two years)
* no mouth, no gut: a trophosome packed with symbiotic sulfur-oxidising bacteria feeds it; the blood-red plume
  (haemoglobin binding oxygen and sulfide together) takes up what they need
* touched or threatened, it pulls the plume into the tube and seals it with the obturaculum

Game simplifications: real depths mapped onto the world's vent fields (fauna/__init__.py vent_depth); an
"environmental" animal - a colony per entity, never moving, not despawning, not counted in the per-player fauna
limit; weighted to its real depth, but present at any vent field as a minor member.
"""

from fauna import vent_depth

INFO = dict(
    names=("Giant Tubeworm", "チューブワーム"),
    egg=(0xEEEAE0, 0xC0282C),
    role="environmental",
    voice="tubeworm",
    sounds={
        "retract": (2, "Tubeworms withdraw", "チューブワームが引っ込む"),
        "hurt": (2, "Tubeworm hurts", "チューブワームが傷つく"),
        "death": (1, "Tubeworm dies", "チューブワームが死ぬ"),
    },
    spawn=dict(weight=20, group=(1, 2), depth_m=vent_depth(2000, 2700), placement="seabed",
               cave_factor=1.0, open_factor=1.0, max_light=15, cap=(4, 12),
               habitat=[dict(blocks="#abyssia:fauna/vent", radius=3, min=1, factor=1.0, required=True)]),
)
