"""メンダコ / Japanese flapjack octopus, Exsuperoteuthis depressa (Ijima & Ikeda, 1895), formerly Opisthoteuthis
depressa (moved to Exsuperoteuthis Verhoeff, 2024; WoRMS).

Research (JAMSTEC BISMaL 9000323 "メンダコ", records 24.6-36.6 deg N off Japan; SeaLifeBase "Opisthoteuthis
depressa", benthic, 130-1100 m, male mantle to 3.8 cm; Toba Aquarium picture book "メンダコ", muddy floors ~100-400 m
from Sagami Bay to the East China Sea; Wikipedia "Exsuperoteuthis depressa"):
* endemic to the NW Pacific off Japan; lives on and just above soft muddy bottoms of the upper slope
* small: to ~20 cm arm tip to arm tip; the arms are joined almost to their tips by a web, so at rest it spreads into
  a flat orange-red disc ("pancake"); two small ear-like fins on the top of the body, cirri along the arms
* cirrate octopods swallow small, slow prey whole: mainly amphipods and polychaetes, other small crustaceans
* hovers or sits on the mud; when disturbed it swims off slowly by flapping the fins, pulsing the web and jetting
* no ink sac (cirrates lack it) and no bioluminescence

Game simplifications: hovers in the near-floor zone and rests still for long spells; passive, drifts away when
approached; eyes glint faintly in torchlight so players can spot the little disc.
"""

INFO = dict(
    names=("Flapjack Octopus", "メンダコ"),
    egg=(0xC0553A, 0xE8A080),
    role="passive",
    voice="squid",
    sounds={
        "ambient": (2, "Flapjack octopus flaps its fins", "メンダコがひれを動かす"),
        "hurt": (2, "Flapjack octopus hurts", "メンダコが傷つく"),
        "death": (1, "Flapjack octopus dies", "メンダコが死ぬ"),
        "flop": (2, "Flapjack octopus flops", "メンダコが跳ねる"),
    },
    # 130-1100 m, mostly ~150-600 m (aquarium specimens from ~100-400 m); soft mud of the upper slope
    spawn=dict(weight=8, group=(1, 2), depth_m=(130, 200, 600, 1100), placement="near_floor",
               cave_factor=0.3, open_factor=1.0, max_light=8, cap=(12, 32),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=2.0)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.2, health=5,
    # hangs just above the mud, flaps off slowly when disturbed; stays level (a disc, not a fish)
    traits=dict(steering=(10, 4, 0.0012), zone=("near_floor", 0.4, 300), flees=(1.2, 5), hangs_still=True, home=16,
                ambient=600),
    render=dict(fish=False, pitch=False, eyeshine=(0.3, ["eye_right", "eye_left"])))
