"""ギンダラ / sablefish (black cod), Anoplopoma fimbria.

Japanese name: ギンダラ (銀鱈), family ギンダラ科 (Anoplopomatidae); not a true cod.

Research (FishBase summary "Anoplopoma fimbria"; NOAA Fisheries "Sablefish"; Wikipedia "Sablefish";
Sigler et al. 2001, "Diel vertical migration of sablefish", Fisheries Bulletin 99):
* North Pacific from Japan and the Bering Sea to Baja California; adults on the continental slope and in deep
  troughs at 200-1000 m, recorded to ~2700-3000 m; juveniles shallower; bathydemersal
* usually 60-80 cm, up to 1.2 m; long, slender, cigar-shaped body with a pointed head; two well-separated dorsal
  fins of similar size, the anal fin under the second; slightly forked tail
* slate-black to dark greenish-grey above, paler grey sides and belly
* swims close to the bottom alone or in small groups, rising off the floor at night; opportunistic feeder on fish,
  squid, krill, jellies and carrion
* rich, oily white flesh ("black cod"); long-lived (90+ years)

Sources: https://www.fishbase.se/summary/Anoplopoma-fimbria.html
https://www.fisheries.noaa.gov/species/sablefish  https://en.wikipedia.org/wiki/Sablefish

Game simplifications: one spawn band for juveniles and adults; harmless prey near the floor; drops its own raw fillet.
"""

INFO = dict(
    names=("Sablefish", "ギンダラ"),
    egg=(0x2C3036, 0x7A8088),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Sablefish swims", "ギンダラが泳ぐ"),
        "hurt": (2, "Sablefish hurts", "ギンダラが傷つく"),
        "death": (1, "Sablefish dies", "ギンダラが死ぬ"),
        "flop": (2, "Sablefish flops", "ギンダラが跳ねる"),
    },
    # adults 200-1000 m on the slope, recorded to ~3000 m
    spawn=dict(weight=10, group=(1, 3), depth_m=(200, 500, 2400, 3000), placement="near_floor",
               cave_factor=0.5, open_factor=1.0, max_light=8, cap=(24, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~1.1 blocks long
INFO["java"] = dict(kind="swimmer", size_m=0.66, health=10,
    # cruises just above the floor alone or in twos and threes
    traits=dict(steering=(15, 4, 0.002), zone=("near_floor", 0.8, 160), flees=(2.2, 8), home=32, ambient=500),
    render=dict(eyeshine=(0.6, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet, looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_sablefish", count=(1, 2), looting=1)]
