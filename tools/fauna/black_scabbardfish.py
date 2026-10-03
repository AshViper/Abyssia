"""クロタチカマス / black scabbardfish, Aphanopus carbo.

Japanese name: クロタチカマス (FS01 spec), family クロタチカマス科 (Trichiuridae: scabbardfishes and cutlassfishes).

Research (FishBase summary "Aphanopus carbo"; Wikipedia "Black scabbardfish"; Farias et al. 2013, "Reproductive
and feeding spatial dynamics of the black scabbardfish in NE Atlantic", J. Fish Biol.):
* North Atlantic: Iceland and the Faroes to Madeira and the Canaries, also off Newfoundland; 200-1700 m, mostly
  ~500-1300 m; benthopelagic over the slope and seamounts, rising into midwater at night
* up to 1.1-1.5 m; very long, laterally compressed, blade-like body, much lower than an oarfish; large head, long
  jaw with the lower jaw protruding and big fang-like teeth, very large eyes; one long dorsal fin (spiny front
  part) from head to tail, a short anal fin near the tail, small forked tail on a thin stalk
* coppery-black with an iridescent metallic sheen, black mouth and gill cavity; no light organs
* hunts fish, crustaceans and cephalopods in midwater; Madeira longline fishery ("espada preta") for food

Sources: https://www.fishbase.se/summary/Aphanopus-carbo.html  https://en.wikipedia.org/wiki/Black_scabbardfish

Game simplifications: built on the eel template (long ribbon body); small schools of 2-4 cruise slowly in midwater;
harmless to the player despite the teeth; drops its own raw fillet.
"""

INFO = dict(
    names=("Black Scabbardfish", "クロタチカマス"),
    egg=(0x1E1C1E, 0xB8A060),
    role="passive",
    voice="gulper_eel",
    sounds={
        "ambient": (2, "Black scabbardfish swims", "クロタチカマスが泳ぐ"),
        "hurt": (2, "Black scabbardfish hurts", "クロタチカマスが傷つく"),
        "death": (1, "Black scabbardfish dies", "クロタチカマスが死ぬ"),
        "flop": (2, "Black scabbardfish flops", "クロタチカマスが跳ねる"),
    },
    # 200-1700 m, mostly 500-1300 m over the slope, in midwater at night
    spawn=dict(weight=8, group=(2, 4), depth_m=(350, 500, 1400, 1700), placement="open_water",
               cave_factor=0.3, open_factor=1.0, max_light=8, clearance=2, cap=(24, 80)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~1.2 blocks long
INFO["java"] = dict(kind="swimmer", size_m=0.77, health=10,
    # slow midwater cruising in small groups
    traits=dict(steering=(12, 4, 0.0016), zone=("open_water", 0.7, 200), schools=3, flees=(2.0, 8), home=40, ambient=450),
    render=dict(eyeshine=(0.8, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet, looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_black_scabbardfish", count=(1, 2), looting=1)]
