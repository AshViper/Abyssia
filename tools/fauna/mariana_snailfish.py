"""マリアナスネイルフィッシュ / Mariana snailfish, Pseudoliparis swirei.

Japanese name: no standard Japanese name; JAMSTEC's 2017 press release (8178 m film) calls it
"マリアナスネイルフィッシュ" (a シンカイクサウオ-group snailfish).

Research (FishBase summary "Pseudoliparis swirei"; Gerringer et al. 2017, Zootaxa 4358(1): 161-177;
Linley et al. 2016, Deep-Sea Research I 114: 99-110; Wang et al. 2019, Nature Ecology & Evolution 3: 823-833;
JAMSTEC press release 2017-08-24):
* endemic to the Mariana Trench, bathydemersal on the hadal floor, 6198-8076 m captured (abundant at 7000-8000 m);
  filmed at 8178 m, the deepest fish recorded by video with a measured depth at the time
* ~23-29 cm standard length, 160 g; tadpole-like: short broad head (17-22 % of SL), tiny eyes, wide toothless-
  looking mouth, a sucking disc under the chest, big pectoral fins with a distinct lower lobe, long tapering body
  wrapped in continuous dorsal (51-58 rays) and anal fins
* pale, unpigmented, translucent gelatinous skin (the pink organs show through); reduced muscle, incompletely
  ossified skull, poor vision - adaptations to crushing pressure
* the top predator of the trench floor: suction-feeds on amphipods; gathers in numbers at bait
* no bioluminescence; not schooling but aggregates

Sources: https://www.fishbase.se/summary/Pseudoliparis-swirei.html  https://doi.org/10.11646/zootaxa.4358.1.7
https://www.jamstec.go.jp/j/about/press_release/20170824/  https://en.wikipedia.org/wiki/Pseudoliparis_swirei

Game simplifications: built on the eel template for the long finned tail; the disc is left out; the skin is
opaque pale pink (no translucency); it does not hunt (no amphipods in the game) - it just cruises slowly above
the deepest trench floors and swims off when threatened; harmless.
"""

INFO = dict(
    names=("Mariana Snailfish", "マリアナスネイルフィッシュ"),
    egg=(0xE2C4C2, 0xC58A90),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Snailfish swims", "マリアナスネイルフィッシュが泳ぐ"),
        "hurt": (2, "Snailfish hurts", "マリアナスネイルフィッシュが傷つく"),
        "death": (1, "Snailfish dies", "マリアナスネイルフィッシュが死ぬ"),
        "flop": (2, "Snailfish flops", "マリアナスネイルフィッシュが跳ねる"),
    },
    # 6198-8178 m, abundant 7000-8000 m; just above the hadal floor
    spawn=dict(weight=10, group=(2, 5), depth_m=(6198, 7000, 8000, 8178), placement="near_floor",
               cave_factor=0.5, open_factor=1.0, max_light=4, cap=(20, 64)),
    java=dict(kind="swimmer", size_m=0.25, health=5, attack=0,
              traits=dict(steering=(15, 4, 0.0015), zone=("near_floor", 0.5, 240), flees=(1.4, 6), home=20,
                          ambient=400)),
)
