"""オニボウズギス / black swallower, Chiasmodon niger.

Japanese name: オニボウズギス (JAMSTEC BISMaL 9014246).

Research (FishBase summary "Chiasmodon niger"; JAMSTEC BISMaL 9014246; Wikipedia "Black swallower"; Melo 2009,
"Taxonomic and phylogenetic revision of the family Chiasmodontidae", Auburn University dissertation):
* 700-2745 m, fish over 4.5 cm mostly at 730-1900 m (mean 1390 m); juveniles shallower (0-1050 m); meso- to
  bathypelagic, tropical and temperate oceans
* 15-20 cm, up to 25 cm SL; uniformly dark brown-black; long head, blunt snout, lower jaw projecting, a single
  row of sharp depressible teeth with enlarged canines
* swallows whole fish over twice its length and ten times its mass: the stomach and belly skin stretch into a
  pale, translucent bag (a 19 cm fish held an 86 cm snake mackerel); prey too large to digest rots, the gas
  floats the swallower to the surface - how most specimens were found
* no photophores in Chiasmodon (some Kali and Pseudoscopelus of the same family have them); solitary

Sources: https://www.fishbase.se/summary/Chiasmodon-niger.html
https://www.godac.jamstec.go.jp/bismal/j/view/9014246  https://en.wikipedia.org/wiki/Black_swallower
https://etd.auburn.edu/bitstream/handle/10415/1885/MELO%202009%20-%20Dissertation.pdf

Game simplifications: always shown with a distended belly; cod and small mod fishes stand in for its prey;
harmless to players.
"""

INFO = dict(
    names=("Black Swallower", "オニボウズギス"),
    egg=(0x1E1A1C, 0xB8A8A0),
    role="predator",
    voice="anglerfish",
    prey=["minecraft:cod", "minecraft:salmon", "viperfish", "deep_sea_shrimp"],
    sounds={
        "ambient": (2, "Black swallower swims", "オニボウズギスが泳ぐ"),
        "hurt": (2, "Black swallower hurts", "オニボウズギスが傷つく"),
        "death": (1, "Black swallower dies", "オニボウズギスが死ぬ"),
        "flop": (2, "Black swallower flops", "オニボウズギスが跳ねる"),
        "snap": (2, "Black swallower gulps", "オニボウズギスが丸呑みする"),
    },
    # 700-2745 m, adults mostly 730-1900 m; midwater
    spawn=dict(weight=7, group=(1, 1), depth_m=(500, 750, 1900, 2745), placement="open_water",
               cave_factor=0.6, open_factor=1.0, max_light=6, cap=(24, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.25, health=5, attack=1,
    # hangs in midwater and lunges at fish larger than itself
    traits=dict(steering=(20, 5, 0.0022), zone=("open_water", 0.9, 220), migrates=0.2, flees=(2.5, 8), hunts=(1.4, 0.35, 0.3),
                hangs_still=True, home=24, ambient=450),
    render=dict(eyeshine=(0.5, ["right_eye", "left_eye"])))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="abyssal_fish_fillet", count=(2, 3), looting=1, chance=0.7)]
