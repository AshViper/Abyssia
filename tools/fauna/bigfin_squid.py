"""ミズヒキイカ / bigfin squid, Magnapinna spp. (e.g. Magnapinna pacifica).

Names: JAMSTEC BISMaL lists Magnapinna pacifica as ミズヒキイカ (family ミズヒキイカ科, Magnapinnidae); the genus has
~5 species (M. pacifica, M. atlantica, M. talismani, sp. B, sp. C), mostly known from ROV video.

Research (MBARI "Bigfin squid"; Vecchione et al. 2001, "Worldwide observations of remarkable deep-sea squids",
Science 294: 2505; PLOS One 2020, "Multiple observations of Bigfin Squid (Magnapinna sp.) in the Great Australian
Bight", PLOS One / PMC7657483; BISMaL "Magnapinna pacifica ミズヒキイカ"; Wikipedia "Bigfin squid"):
* bathypelagic to hadal, usually near the seafloor: MBARI 1600-6200 m (deepest 6212 m, Philippine Trench); most
  sightings ~2000-4000 m (BISMaL records of M. pacifica 2339-3890 m; Great Australian Bight 2178-3060 m)
* estimated to 6-8 m total length, almost all of it arm filaments; mantle small; fins very large, up to ~90% of the
  mantle length
* all ten arms and tentacles look alike: the thick proximal part is held out at nearly a right angle to the body
  ("elbow"), then continues as a very long thin white sticky filament hanging parallel to the body axis, up to ~20x
  the body length
* pale reddish-pink / orange, semi-translucent
* hovers arms-down with the fins undulating, sometimes dragging the filaments over the bottom; drifts with the
  filaments spread, probably catching small animals that touch them (diet unknown); solitary, very rarely seen
* no light organs known

Game simplifications: built upright (arms-down) with the parts template since the squid template cannot bend the
arms; filaments ~3x the mantle+head length instead of up to 20x; a 4 m animal; stays near the seafloor, hangs still
and barely moves; never grabs anything.
"""

INFO = dict(
    names=("Bigfin Squid", "ミズヒキイカ"),
    egg=(0xB86F68, 0xE6DCD6),
    role="passive",
    voice="squid",
    sounds={
        "ambient": (2, "Bigfin squid drifts", "ミズヒキイカが漂う"),
        "hurt": (2, "Bigfin squid hurts", "ミズヒキイカが傷つく"),
        "death": (1, "Bigfin squid dies", "ミズヒキイカが死ぬ"),
        "flop": (2, "Bigfin squid flops", "ミズヒキイカが跳ねる"),
    },
    # 1600-6212 m, most sightings ~2000-4000 m, near the seafloor
    spawn=dict(weight=2, group=(1, 1), depth_m=(1600, 2000, 4000, 6212), placement="near_floor",
               cave_factor=0.3, open_factor=1.0, max_light=4, clearance=5, cap=(3, 128)),
    java=dict(kind="swimmer", size_m=4.0, health=12,
              traits=dict(steering=(4, 2, 0.001), zone=("near_floor", 0.3, 400), hangs_still=True,
                          flees=(1.2, 5), home=24, ambient=500),
              render=dict(fish=False, pitch=False)),
)

# the hanging filaments are not body: keep the hitbox to the mantle and fins
INFO["java"]["hitbox"] = (1.4, 1.4)
