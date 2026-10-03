"""オレンジラフィー / orange roughy, Hoplostethus atlanticus.

Japanese name: オレンジラフィー is the trade name used in Japan (FS01 spec); the family is ヒウチダイ科 (Trachichthyidae).

Research (FishBase summary "Hoplostethus atlanticus"; NOAA / NIWA fishery profiles; Wikipedia "Orange roughy";
Andrews, Tracey & Dunn 2009, "Lead-radium dating of orange roughy", Can. J. Fish. Aquat. Sci. 66:1130-1140):
* 180-1809 m, mostly 400-1400 m (adults 700-1400 m), over seamounts, ridges and the continental slope of the
  Atlantic, south-west Pacific (New Zealand, Australia) and Indian Ocean; benthopelagic
* 35-50 cm usually, up to 75 cm; deep, laterally compressed, rounded body; big bony head with mucus-filled
  cavities, very large eyes, oblique mouth; one dorsal fin with 4-6 spines in front; forked tail
* brick-red to orange-red in life (looks black at depth where red light is absent); no light organs
* forms dense aggregations over seamounts and hills to spawn and feed; slow, sluggish swimmer
* eats prawns, mysids, small fish and squid; extremely slow-growing, lives 150+ years (one of the longest-lived fish),
  so seamount trawl fisheries overfished it quickly; sold as a white-fleshed food fish

Sources: https://www.fishbase.se/summary/Hoplostethus-atlanticus.html
https://en.wikipedia.org/wiki/Orange_roughy  https://www.fisheries.noaa.gov/species/orange-roughy

Game simplifications: small schools of 2-4 drift slowly in midwater instead of huge spawning aggregations; harmless
prey; drops its own raw fillet (FS01 edible fish).
"""

INFO = dict(
    names=("Orange Roughy", "オレンジラフィー"),
    egg=(0xD8582C, 0x7A2E22),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Orange roughy swims", "オレンジラフィーが泳ぐ"),
        "hurt": (2, "Orange roughy hurts", "オレンジラフィーが傷つく"),
        "death": (1, "Orange roughy dies", "オレンジラフィーが死ぬ"),
        "flop": (2, "Orange roughy flops", "オレンジラフィーが跳ねる"),
    },
    # 180-1809 m, mostly 400-1400 m over seamounts and the slope
    spawn=dict(weight=10, group=(2, 4), depth_m=(180, 400, 1400, 1800), placement="open_water",
               cave_factor=0.4, open_factor=1.0, max_light=8, cap=(32, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~1.0 block long
INFO["java"] = dict(kind="swimmer", size_m=0.56, health=8,
    # slow, loose schools in midwater over the slope
    traits=dict(steering=(14, 4, 0.0018), zone=("open_water", 0.7, 160), schools=3, flees=(2.0, 8), home=32, ambient=450),
    render=dict(eyeshine=(0.7, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet, looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_orange_roughy", count=(1, 2), looting=1)]
