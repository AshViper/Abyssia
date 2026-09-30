"""トガリムネエソ / lovely hatchetfish (Atlantic silver hatchetfish), Argyropelecus aculeatus.

Names: the Japanese name is トガリムネエソ (genus テンガンムネエソ属 in JAMSTEC BISMaL; family ムネエソ科); "ムネエソ"
alone names Sternoptyx, and "ホシエソ" names stomiid dragonfishes - neither fits.

Research (FishBase summary "Argyropelecus aculeatus"; Wikipedia "Argyropelecus aculeatus" citing Hopkins & Baird
1985; Denton 1970 and Warrant & Locket 2004 on hatchetfish eyes and counter-illumination):
* mesopelagic in all tropical and subtropical oceans (western Pacific 35 N - 35 S); 100-2056 m; by day 300-600 m,
  at night 100-300 m (diel vertical migration)
* up to 8.3 cm SL; very deep, paper-thin body shaped like a hatchet blade, mirror-silver sides, darker back; big
  upward-pointing tubular eyes (metallic blue in life) and a steeply upturned mouth, watching for silhouettes above
* rows of large tubular photophores along the belly and lower sides point straight down; their blue light
  (~470-480 nm) matches the downwelling light and erases the fish's silhouette (counter-illumination); adult
  photophores develop at ~15 mm
* a selective dusk feeder on ostracods and copepods, larger fish also on krill and fish larvae; caught in numbers
  in midwater trawls and eaten by many larger fishes

Game simplifications: shown in loose schools of 4-10 (they occur in numbers, but true schooling is not
documented); only the ventral photophore rows glow.
"""

INFO = dict(
    names=("Hatchetfish", "トガリムネエソ"),
    egg=(0x9AA3AD, 0x5A9CF0),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Hatchetfish swims", "トガリムネエソが泳ぐ"),
        "hurt": (2, "Hatchetfish hurts", "トガリムネエソが傷つく"),
        "death": (1, "Hatchetfish dies", "トガリムネエソが死ぬ"),
        "flop": (2, "Hatchetfish flops", "トガリムネエソが跳ねる"),
    },
    # day 300-600 m, night 100-300 m, recorded to 2056 m
    spawn=dict(weight=14, group=(4, 10), depth_m=(100, 300, 600, 2056), placement="open_water",
               cave_factor=0.6, open_factor=1.0, max_light=8, cap=(120, 32)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.08, health=3,
    # schools; the belly light-organs cancel its silhouette against the light from above
    traits=dict(steering=(25, 8, 0.0025), zone=("open_water", 1.0, 120), migrates=0.4, schools=6, flees=(3.0, 10), home=32, ambient=350),
    render=dict(eyeshine=(0.6, ["right_eye", "left_eye"]), glow=dict(bones=["body"], base=0.7, flicker=0.1)))
