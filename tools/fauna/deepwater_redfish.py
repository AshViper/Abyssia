"""アラスカメヌケ / deepwater redfish (beaked redfish), Sebastes mentella.

Japanese name: アラスカメヌケ is the trade name given in the FS01 spec (strictly the Pacific S. alutus); メヌケ is
the Japanese group name for the red deep rockfishes (Sebastes, メバル科 Sebastidae), S. mentella is the North Atlantic
species.

Research (FishBase summary "Sebastes mentella"; ICES advice "Beaked redfish"; Wikipedia "Sebastes mentella";
Planque et al. 2013, "Who eats whom in the Barents Sea", on its pelagic feeding):
* North Atlantic: Norway and the Barents Sea, Iceland, Greenland, Newfoundland; 300-1000 m and deeper, mostly
  ~300-500 m on the slope and in the oceanic Irminger Sea (pelagic stock); live-bearing
* 30-45 cm, up to 55 cm; perch-like: deep oval body, large spiny head with a bony "beak" knob on the lower jaw,
  very large eyes; long dorsal fin with a tall spiny front and soft rear part, broad rounded pectorals, square tail
* red to orange-red, slightly darker back, no light organs
* schools near the bottom by day and in midwater at night, eating krill, copepods, shrimp and small fish;
  lives 70+ years
* the main "redfish" (red fillets) of the North Atlantic fisheries

Sources: https://www.fishbase.se/summary/Sebastes-mentella.html  https://en.wikipedia.org/wiki/Sebastes_mentella

Game simplifications: the spec's depth band and group size were filled in by Claude (the ChatGPT table was cut off);
schools of 2-5 in midwater show as red fish shadows; harmless; drops its own raw fillet.
"""

INFO = dict(
    names=("Deepwater Redfish", "アラスカメヌケ"),
    egg=(0xC8402E, 0xE88A6A),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Deepwater redfish swims", "アラスカメヌケが泳ぐ"),
        "hurt": (2, "Deepwater redfish hurts", "アラスカメヌケが傷つく"),
        "death": (1, "Deepwater redfish dies", "アラスカメヌケが死ぬ"),
        "flop": (2, "Deepwater redfish flops", "アラスカメヌケが跳ねる"),
    },
    # 300-1000 m, mostly 300-500 m (pelagic at night)
    spawn=dict(weight=12, group=(2, 5), depth_m=(300, 500, 800, 1000), placement="open_water",
               cave_factor=0.5, open_factor=1.0, max_light=8, cap=(48, 48)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~0.8 blocks long
INFO["java"] = dict(kind="swimmer", size_m=0.38, health=6,
    # schools in midwater, rising a little at night
    traits=dict(steering=(18, 5, 0.002), zone=("open_water", 0.8, 140), migrates=0.2, schools=4, flees=(2.6, 9), home=32, ambient=400),
    render=dict(eyeshine=(0.7, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet (a small fish: 1), looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_deepwater_redfish", count=(1, 1), looting=1)]
