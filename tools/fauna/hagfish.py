"""ヌタウナギ / inshore hagfish, Eptatretus burgeri.

Japanese name: ヌタウナギ (JAMSTEC BISMaL 9000548).

Research (FishBase summary "Eptatretus burgeri"; Wikipedia "Inshore hagfish" and ja "ヌタウナギ"; JAMSTEC BISMaL
9000548):
* NW Pacific (central Honshu to Kyushu, Sea of Japan, S Korea, E China, Taiwan); sublittoral, 5-10 to 270 m on
  sand-mud bottoms, usually buried in the mud; nocturnal; the only hagfish with a seasonal breeding cycle,
  moving into deeper water to spawn
* to ~60 cm; jawless and eel-like; no paired fins, only a fin fold around the tail; 3-4 pairs of short barbels
  around the single nostril and the mouth; eyes are pale spots under the skin (vision weak or absent); six pairs
  of external gill apertures in a row; pinkish grey-brown with a prominent white mid-dorsal line
* scavenger: gathers at carcasses of whales and large fishes and burrows into them, also takes live prey;
  horny rasping tooth plates on an eversible "tongue"
* releases copious slime ("nuta") from 81-92 slime pores when disturbed, turning the water around it to jelly
* no bioluminescence

Sources: https://www.fishbase.se/summary/Eptatretus-burgeri.html  https://en.wikipedia.org/wiki/Inshore_hagfish
https://ja.wikipedia.org/wiki/%E3%83%8C%E3%82%BF%E3%82%A6%E3%83%8A%E3%82%AE
https://www.godac.jamstec.go.jp/bismal/j/view/9000548

Game simplifications: a shallow-water hagfish kept on muddy shelf floors near its real depths (core 20-200 m is a
game choice); it rests on the seabed instead of burying; the slime defence is only a quick escape; the barbels
are a few short prongs at the snout; no scavenging behaviour; harmless.
"""

INFO = dict(
    names=("Hagfish", "ヌタウナギ"),
    egg=(0x8A6E6A, 0xE8DCD8),
    role="passive",
    voice="gulper_eel",
    sounds={
        "ambient": (2, "Hagfish wriggles", "ヌタウナギがくねる"),
        "hurt": (2, "Hagfish hurts", "ヌタウナギが傷つく"),
        "death": (1, "Hagfish dies", "ヌタウナギが死ぬ"),
        "flop": (2, "Hagfish flops", "ヌタウナギが跳ねる"),
    },
    # 5-270 m on sand-mud bottoms
    spawn=dict(weight=6, group=(1, 3), depth_m=(5, 20, 200, 270), placement="near_floor",
               cave_factor=0.5, open_factor=1.0, max_light=7, cap=(16, 64),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=2.0)]),
    java=dict(kind="swimmer", size_m=0.6, health=8, attack=0,
              traits=dict(steering=(15, 6, 0.0015), zone=("bottom", 0.45, 300), flees=(1.3, 6), home=16,
                          ambient=500)),
)
