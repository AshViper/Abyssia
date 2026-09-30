"""トリノアシ / sea lily (stalked crinoid), Metacrinus rotundus Carpenter, 1884.
Echinodermata > Crinoidea > Isocrinida > Isocrinidae (JAMSTEC BISMaL 9000385).

Research (Wikipedia "Metacrinus rotundus" and "トリノアシ"; JAMSTEC BISMaL 9000385; "Photographic observations of the
stalked crinoid Metacrinus rotundus in Suruga Bay", J. Oceanogr.; "Particle selection by the sea lily Metacrinus
rotundus"):
* unusually shallow for a living stalked crinoid: 100-500 m, usually 100-200 m (Sagami Bay, Suruga Bay, Kii Channel);
  dense beds that shelter bivalves and brittle stars
* rocky and gravelly bottoms; anchored by the cirri of the stalk base
* stalk 30-50 cm, round-pentagonal, jointed, with whorls of five clawed cirri; a small calyx; five arms branching 3-4
  times into ~50 arms fringed with pinnules, arms about half the stalk length; pale reddish in life
* passive suspension feeder: holds the arms open as a parabolic fan into the current 10-50 cm above the bottom and
  catches plankton and resuspended detritus with tube feet and mucus
* can crawl a little with its arms but seldom does; readily sheds the crown under stress and regrows it in months
* no bioluminescence

Sources: https://en.wikipedia.org/wiki/Metacrinus_rotundus  https://ja.wikipedia.org/wiki/トリノアシ
https://www.godac.jamstec.go.jp/bismal/j/view/9000385  https://link.springer.com/article/10.1007/BF02109286

Game simplifications: an "environmental" animal - never moves, does not despawn; "retract" is the crown curling its
arms shut when touched, then opening again; 10 feathered arms stand for the ~50; ~0.9 block tall; small beds on
rock at the shelf edge.
"""

INFO = dict(
    names=("Sea Lily", "トリノアシ"),
    egg=(0xC8A292, 0x8A5E50),
    role="environmental",
    voice="tubeworm",
    sounds={
        "retract": (2, "Sea lily curls its arms", "トリノアシが腕を閉じる"),
        "hurt": (2, "Sea lily hurts", "トリノアシが傷つく"),
        "death": (1, "Sea lily dies", "トリノアシが死ぬ"),
    },
    # 100-500 m, mostly 100-200 m; rock and gravel
    spawn=dict(weight=10, group=(2, 4), depth_m=(90, 100, 250, 500), placement="seabed",
               cave_factor=0.3, open_factor=1.0, max_light=15, cap=(8, 16),
               substrate=[dict(blocks="#abyssia:fauna/rock", factor=3.0)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="sessile", size_m=0.5, health=6, render=dict(fish=False, pitch=False))
