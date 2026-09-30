"""コウモリダコ / vampire squid, Vampyroteuthis infernalis.

Research (MBARI "Vampire squid"; Hoving & Robison 2012, "Vampire squid: detritivores in the oxygen minimum zone",
Proc. R. Soc. B; Robison et al. 2003, "Light production by the arm tips of the deep-sea cephalopod Vampyroteuthis
infernalis", Biol. Bull. 205: 102-109; Animal Diversity Web):
* worldwide in tropical and temperate seas, 300-3000 m; over the Monterey Canyon found throughout 600-900 m in the
  oxygen minimum zone (~0.4 ml/l O2), average ~690 m; ammonium-rich gelatinous tissue keeps it neutrally buoyant
* only ~30 cm total length (mantle to ~15 cm); the only living member of its order (neither squid nor octopus)
* dark reddish-brown to black; eight arms joined by a black web (cloak) lined with fleshy cirri; two long retractile
  sticky filaments; adults have one pair of ear-like fins on the rear mantle; proportionally the largest eyes of any
  animal (blue or red depending on the light)
* light organs: a large lidded photophore behind the base of each fin, smaller photophores over the skin, and light
  organs at the tips of all eight arms that glow and pulse blue; can eject a sticky cloud of blue luminous particles
  from the arm tips (lasting up to ~10 min); no ink sac; can turn the cloak inside out ("pineapple" posture)
* not a hunter: drifts and collects marine snow (detritus, faecal pellets, small crustaceans) on the filaments,
  wrapping it in mucus; low metabolism, solitary

Game simplifications: the web is approximated by wide dark arms (the squid template has no web); arm tips and fin
bases glow (skin photophores and the luminous cloud are left out); a harmless drifter that flees slowly.
"""

INFO = dict(
    names=("Vampire Squid", "コウモリダコ"),
    egg=(0x4A181D, 0x5FB4FF),
    role="passive",
    voice="squid",
    sounds={
        "ambient": (2, "Vampire squid drifts", "コウモリダコが漂う"),
        "hurt": (2, "Vampire squid hurts", "コウモリダコが傷つく"),
        "death": (1, "Vampire squid dies", "コウモリダコが死ぬ"),
        "flop": (2, "Vampire squid flops", "コウモリダコが跳ねる"),
    },
    # 300-3000 m; Monterey: throughout 600-900 m (oxygen minimum zone), ADW "mostly deeper"
    spawn=dict(weight=5, group=(1, 1), depth_m=(300, 600, 1200, 3000), placement="open_water",
               cave_factor=0.8, open_factor=1.0, max_light=5, cap=(10, 64)),
    java=dict(kind="swimmer", size_m=0.3, health=6,
              traits=dict(steering=(8, 4, 0.0012), zone=("open_water", 0.4, 300), hangs_still=True,
                          flees=(1.8, 6), home=32, ambient=400),
              render=dict(eyeshine=(0.3, ["right_eye", "left_eye"]),
                          glow=dict(bones=["arm_1_3", "arm_2_3", "arm_3_3", "arm_4_3", "arm_5_3", "arm_6_3", "arm_7_3",
                                           "arm_8_3", "right_fin", "left_fin"],
                                    halos=[("right_fin", 0.12, 0.37, 0.7, 1.0), ("left_fin", 0.12, 0.37, 0.7, 1.0)],
                                    base=0.6, flicker=0.35))),
)
