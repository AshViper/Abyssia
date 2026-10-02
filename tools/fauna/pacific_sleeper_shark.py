"""オンデンザメ / Pacific sleeper shark, Somniosus pacificus.

Japanese name: オンデンザメ (JAMSTEC BISMaL 9000388).

Research (FishBase summary "Somniosus pacificus"; Wikipedia "Pacific sleeper shark", incl. the satellite-tag
depth records; JAMSTEC BISMaL 9000388):
* North Pacific and Arctic (Japan and Taiwan to the Bering, Chukchi and Beaufort Seas, south to Baja California);
  demersal / mesobenthopelagic on shelves and slopes, surface to ~2000 m (FishBase to 2205 m); in the far north it
  comes into shallow water, at lower latitudes it stays deep
* tagged sharks ranged 106-1323 m but spent over 99 % of the time at 200-600 m (mostly 300-500 m), with steady
  slow vertical oscillations
* mature adults ~3.7 m and 320-360 kg, max recorded ~4.4-4.65 m; heavy cylindrical body, short rounded snout,
  small eyes without a nictitating membrane, two small spineless dorsal fins, no anal fin, small fins and a short
  tail; uniformly dark grey to blackish-brown
* sluggish ("sleeper") stealth predator and scavenger: fishes (salmon, pollock, grenadiers, flatfish),
  cephalopods, crabs and snails, occasionally seals; suction-feeds and cuts with a rolling head motion
* no bioluminescence; solitary; flesh toxic (trimethylamine oxide)

Sources: https://www.fishbase.se/summary/Somniosus-pacificus.html
https://en.wikipedia.org/wiki/Pacific_sleeper_shark  https://www.godac.jamstec.go.jp/bismal/j/view/9000388

Game simplifications: ~3 blocks long; neutral like the other deep sharks (bites back only when hurt); shallow
high-latitude records ignored (starts at 100 m); grenadier, cod, salmon, squid and shrimp stand in for its prey.
"""

INFO = dict(
    names=("Pacific Sleeper Shark", "オンデンザメ"),
    egg=(0x3E3A37, 0x6A625C),
    role="predator",
    voice="shark",
    prey=["abyssal_grenadier", "deep_sea_shrimp", "minecraft:cod", "minecraft:salmon", "minecraft:squid"],
    sounds={
        "ambient": (2, "Sleeper shark swims", "オンデンザメが泳ぐ"),
        "hurt": (2, "Sleeper shark hurts", "オンデンザメが傷つく"),
        "death": (1, "Sleeper shark dies", "オンデンザメが死ぬ"),
        "flop": (2, "Sleeper shark flops", "オンデンザメが跳ねる"),
        "bite": (2, "Sleeper shark bites", "オンデンザメが噛みつく"),
    },
    # surface-2205 m (Arctic shallows ignored); tagged sharks mostly 200-600 m; slow, just above the slope floor
    spawn=dict(weight=3, group=(1, 1), depth_m=(100, 200, 600, 2205), placement="near_floor",
               cave_factor=0.2, open_factor=1.0, max_light=8, clearance=3, cap=(8, 128)),
    java=dict(kind="shark", size_m=3.7, health=50, attack=6, armor=3,
              traits=dict(steering=(8, 2, 0.0025), cruise_height=3, mouth_forward=1.2),
              render=dict(eyeshine=(0.5, ["right_eye", "left_eye"]))),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="shark_flesh", count=(3, 4), looting=1, chance=0.7)]
