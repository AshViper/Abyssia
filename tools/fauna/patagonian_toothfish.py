"""マジェランアイナメ / Patagonian toothfish, Dissostichus eleginoides.

Japanese name: マジェランアイナメ (sold in Japan as メロ / 銀ムツ), family ノトテニア科 (Nototheniidae).

Research (FishBase summary "Dissostichus eleginoides"; CCAMLR fishery reports; Wikipedia "Patagonian toothfish";
Collins et al. 2010, "The Patagonian toothfish: biology, ecology and fishery", Adv. Mar. Biol. 58:227-300):
* sub-Antarctic waters of the Southern Ocean: southern South America, Falklands, South Georgia, Kerguelen,
  Macquarie; 70-1600 m and deeper (to ~3850 m), adults mainly 500-1500 m on the slope; bathydemersal
* commonly 70-150 cm, up to 2.2 m and 100 kg; heavy, torpedo-shaped body, big broad head, large mouth with the
  lower jaw projecting and rows of small sharp teeth (hence the name); two dorsal fins (short spiny first, long
  soft second matching the anal fin), big fan pectorals, square tail
* dark grey-brown, mottled, paler belly; no light organs
* slow, mostly solitary, patrols the slope; eats fish (grenadiers, icefish), squid and prawns; lives 50+ years
* antifreeze glycoproteins in the blood; oily white flesh, sold as "Chilean sea bass"

Sources: https://www.fishbase.se/summary/Dissostichus-eleginoides.html
https://en.wikipedia.org/wiki/Patagonian_toothfish  https://doi.org/10.1016/B978-0-12-381015-1.00004-6

Game simplifications: a typical 1 m adult; harmless (it ignores the player and other mod fish); drops its own raw
fillet.
"""

INFO = dict(
    names=("Patagonian Toothfish", "マジェランアイナメ"),
    egg=(0x4E4A44, 0x8A8A86),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Patagonian toothfish swims", "マジェランアイナメが泳ぐ"),
        "hurt": (2, "Patagonian toothfish hurts", "マジェランアイナメが傷つく"),
        "death": (1, "Patagonian toothfish dies", "マジェランアイナメが死ぬ"),
        "flop": (2, "Patagonian toothfish flops", "マジェランアイナメが跳ねる"),
    },
    # adults mainly 500-1500 m on the sub-Antarctic slope
    spawn=dict(weight=6, group=(1, 2), depth_m=(200, 500, 1200, 1600), placement="near_floor",
               cave_factor=0.3, open_factor=1.0, max_light=8, clearance=2, cap=(12, 96)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~1.4 blocks long
INFO["java"] = dict(kind="swimmer", size_m=1.0, health=16, armor=1,
    # slow solitary patrol over the slope floor
    traits=dict(steering=(10, 3, 0.0016), zone=("near_floor", 0.9, 180), flees=(1.8, 6), home=48, ambient=550),
    render=dict(eyeshine=(0.6, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet, looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_patagonian_toothfish", count=(1, 2), looting=1)]
