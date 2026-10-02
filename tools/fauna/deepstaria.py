"""ディープスタリアクラゲ / Deepstaria, Deepstaria enigmatica Russell, 1967.
Cnidaria > Scyphozoa > Semaeostomeae > Ulmaridae (a true medusa).

Japanese name: JAMSTEC BISMaL lists it as ディープスタリアクラゲ (9000299; genus ディープスタリアクラゲ属) - a katakana
name made from the genus name, not a traditional Japanese name.

Research ("In situ observations of the meso-bathypelagic scyphozoan Deepstaria enigmatica (Semaeostomeae:
Ulmaridae)", American Museum Novitates 3900, 2018 (B. Phillips et al.); Wikipedia "Deepstaria enigmatica";
JAMSTEC BISMaL 9000299):
* meso- to bathypelagic, ~600-1750 m (filmed at 974 m, a carcass at 899 m); Antarctic and sub-Antarctic waters,
  Gulf of Mexico, San Diego Trough, North Atlantic, eastern Pacific
* bell very wide and extremely thin, up to ~60-70 cm (the filmed animal 68 cm long x 56 cm across); no marginal
  tentacles, short oral arms; a dense, branched, anastomosing canal network over the whole bell "like wire netting"
* translucent (a collected specimen was described as deep purple-blue)
* catches prey by closing the whole bell around it like a bag in under 3 s ("bagging"); swims by slow peristaltic
  waves (~1.5 cm/s) running up from the margin
* NOT bioluminescent (the luminous animal in the 2018 footage was a Tomopteris worm swimming beside it)
* its carcass on the floor ("jelly fall") drew lithodid crabs and dense caridean shrimp; seen singly; sting risk
  to humans UNKNOWN

Sources: https://bioone.org/journals/american-museum-novitates/volume-2018/issue-3900/3900.1/
https://en.wikipedia.org/wiki/Deepstaria_enigmatica  https://www.godac.jamstec.go.jp/bismal/j/view/9000299

Game simplifications: harmless, slow, one per wide area; the bag-like closing is not simulated (the generic
"spread"/pulse clips stand in); the canal net is a texture pattern; ~1.1 blocks across; it does not glow and is
met in the beam of the player's own light.
"""

INFO = dict(
    names=("Deepstaria", "ディープスタリアクラゲ"),
    egg=(0xB8A8B4, 0x9A6A4E),
    role="passive",
    voice="jelly",
    sounds={
        "ambient": (2, "Deepstaria billows", "ディープスタリアクラゲがたなびく"),
        "pulse": (2, "", ""),
        "hurt": (2, "Deepstaria flinches", "ディープスタリアクラゲが縮む"),
        "death": (1, "Deepstaria dies", "ディープスタリアクラゲが死ぬ"),
    },
    # 600-1750 m, sightings around 900-1000 m
    spawn=dict(weight=1, group=(1, 1), depth_m=(600, 800, 1500, 1750), placement="open_water",
               cave_factor=0.0, open_factor=1.0, max_light=4, clearance=6, cap=(1, 160)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="medusa", size_m=0.7, health=12,
    # slow peristaltic swimmer: long gaps, weak pulses
    traits=dict(pulse_interval=120, pulse_strength=0.03),
    render=dict(fish=False, pitch=False, translucent=["bell"]))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="deepstaria_tentacle", count=(1, 2), looting=1, chance=0.6)]
