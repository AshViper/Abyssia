"""ホタルイカ / firefly squid, Watasenia scintillans.

Research (Wikipedia "Firefly squid"; Animal Diversity Web; bioRxiv 2025, "Synchronous and asynchronous
counterillumination by three types of photophores in the firefly squid"; Sci. Rep. 2016, "Mass spectrometry
analysis ... glowing squid crystal proteins", PMC4899746):
* western North Pacific around Japan (Sea of Japan, off the Pacific coast); famous spring spawning aggregations in
  Toyama Bay (February-July)
* diel vertical migration: ~300-400 m by day (200-600 m band), 20-60 m at night
* small: mantle ~6-7 cm, ~7.5 cm long; reddish-brown translucent body with chromatophores, rhomboid terminal fins,
  eight arms and two tentacles with hooked clubs
* three kinds of photophores: three large organs at the tip of each fourth (ventral) arm, the brightest, intense blue
  and flashed when disturbed; five organs along the lower edge of each eye (blue); ~800-1000 minute ventral skin
  photophores glowing blue or green, used for counter-illumination
* eats planktonic crustaceans (copepods when young), small fishes and squid; forms large schools; lives ~1 year

Game simplifications: a model a little larger than life (readable size); the day and night depths are merged into one
band with migration handled by the swimmer AI; skin photophores are a sprinkle of dots on the flanks; schools of
5-12; passive prey for midwater predators.
"""

INFO = dict(
    names=("Firefly Squid", "ホタルイカ"),
    egg=(0x8A4A3E, 0x3F8CFF),
    role="passive",
    voice="squid",
    sounds={
        "ambient": (2, "Firefly squid jets", "ホタルイカが泳ぐ"),
        "hurt": (2, "Firefly squid hurts", "ホタルイカが傷つく"),
        "death": (1, "Firefly squid dies", "ホタルイカが死ぬ"),
        "flop": (2, "Firefly squid flops", "ホタルイカが跳ねる"),
    },
    # ~300-400 m by day (200-600 m), 20-60 m at night
    spawn=dict(weight=14, group=(5, 12), depth_m=(20, 200, 400, 600), placement="open_water",
               cave_factor=0.5, open_factor=1.0, max_light=9, cap=(60, 48)),
    java=dict(kind="swimmer", size_m=0.08, health=3,
              traits=dict(steering=(25, 8, 0.0025), zone=("open_water", 1.0, 120), migrates=0.45, schools=6,
                          flees=(2.5, 8), home=32, ambient=300),
              render=dict(glow=dict(bones=["arm_4_3", "arm_5_3", "right_eye", "left_eye", "body", "mantle", "mantle_tip"],
                                    halos=[("arm_4_3", 0.15, 0.25, 0.55, 1.0), ("arm_5_3", 0.15, 0.25, 0.55, 1.0)],
                                    base=0.8, flicker=0.25))),
)
