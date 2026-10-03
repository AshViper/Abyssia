"""キンメダイ / alfonsino (red bream), Beryx decadactylus.

Japanese name: キンメダイ (金目鯛) is Beryx splendens; B. decadactylus is ナンヨウキンメ. The FS01 spec uses the
familiar name キンメダイ for the in-game species; both are キンメダイ科 (Berycidae) and look alike.

Research (FishBase summaries "Beryx decadactylus" and "Beryx splendens"; JAMSTEC BISMaL; Wikipedia "Beryx
decadactylus"; NOAA "Alfonsino" fishery profile):
* circumglobal in tropical and temperate seas (not the east Pacific); 110-1000 m, mostly 200-700 m over seamounts,
  ridges and the upper slope; benthopelagic, often near rocky bottoms
* 30-50 cm (B. splendens smaller, ~35 cm); deep, laterally compressed body, huge eyes with golden rims (the "kinme",
  golden eye), short blunt snout and oblique mouth, single short dorsal fin, large deeply forked tail
* bright scarlet red back and fins, silvery-pink flanks; no light organs
* forms schools over seamounts, rising toward the surface at night; eats fish, crustaceans and cephalopods
* prized food fish in Japan (simmered kinmedai)

Sources: https://www.fishbase.se/summary/Beryx-decadactylus.html  https://www.fishbase.se/summary/Beryx-splendens.html
https://en.wikipedia.org/wiki/Beryx_decadactylus

Game simplifications: smaller and rounder than the orange roughy so the two red fish read apart; small schools of
2-5 that scatter when approached; harmless; drops its own raw fillet.
"""

INFO = dict(
    names=("Alfonsino", "キンメダイ"),
    egg=(0xE02A2E, 0xF0C040),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Alfonsino swims", "キンメダイが泳ぐ"),
        "hurt": (2, "Alfonsino hurts", "キンメダイが傷つく"),
        "death": (1, "Alfonsino dies", "キンメダイが死ぬ"),
        "flop": (2, "Alfonsino flops", "キンメダイが跳ねる"),
    },
    # 110-1000 m, mostly 200-700 m over seamounts and the upper slope
    spawn=dict(weight=12, group=(2, 5), depth_m=(200, 300, 700, 1000), placement="open_water",
               cave_factor=0.5, open_factor=1.0, max_light=8, cap=(48, 48)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~0.7 blocks long
INFO["java"] = dict(kind="swimmer", size_m=0.3, health=5,
    # small schools that scatter quickly when something approaches
    traits=dict(steering=(20, 6, 0.0022), zone=("open_water", 0.9, 140), schools=4, flees=(3.0, 10), home=32, ambient=400),
    render=dict(eyeshine=(0.9, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet (a small fish: 1), looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_alfonsino", count=(1, 1), looting=1)]
