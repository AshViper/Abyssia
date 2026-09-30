"""ゴエモンコシオリエビ / Shinkaia crosnieri.

Research (Watsuji et al. 2010 and 2015 on its epibiotic bacteria, Applied and Environmental Microbiology / ISME J;
Motoki et al. 2020, mSystems, metatranscriptomics of its episymbionts; Frontiers in Microbiology 2023 on Munidopsidae
epibionts):
* hydrothermal vents of the Okinawa Trough (e.g. Iheya North, ~980 m; range roughly 700-1600 m), in dense
  aggregations on and around the chimneys
* pale; long slender claws, three visible pairs of walking legs, abdomen folded under the body
* a bacteria farmer: dense plumose setae on its underside carry a thick film of chemosynthetic (sulfur- and
  methane-oxidising) bacteria, fed by the vent fluid it sits in; it combs them off with its mouthparts and they
  are its main food
* like other squat lobsters it escapes backwards with a flip of the abdomen

Game simplifications: real depths mapped onto the world's vent fields (fauna/__init__.py vent_depth); the farming is
shown only by where it lives (crowded on warm vent rock); passive, flips away from players who crowd it; bound to
its vent field.
"""

from fauna import vent_depth

INFO = dict(
    names=("Goemon Squat Lobster", "ゴエモンコシオリエビ"),
    egg=(0xE8E0D0, 0xB0A080),
    role="passive",
    voice="crab",
    sounds={
        "ambient": (2, "Squat lobster clicks", "ゴエモンコシオリエビがカチカチ鳴る"),
        "step": (3, None, None),
        "hurt": (2, "Squat lobster hurts", "ゴエモンコシオリエビが傷つく"),
        "death": (1, "Squat lobster dies", "ゴエモンコシオリエビが死ぬ"),
        "snap": (2, "Squat lobster raises its claws", "ゴエモンコシオリエビがはさみを振り上げる"),
        "flick": (2, "Squat lobster flips away", "ゴエモンコシオリエビが跳ねて逃げる"),
    },
    spawn=dict(weight=14, group=(3, 6), depth_m=vent_depth(700, 1600), placement="seabed",
               cave_factor=1.0, open_factor=1.0, max_light=15, cap=(10, 12),
               habitat=[dict(blocks="#abyssia:fauna/vent", radius=4, min=1, factor=1.0, required=True)]),
)
