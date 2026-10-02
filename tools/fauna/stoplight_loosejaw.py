"""オオクチホシエソ / stoplight loosejaw, Malacosteus niger.

Names: the brief suggested ホウキボシエソ, but that is the old family name (ホウキボシエソ科, Malacosteidae, now part
of Stomiidae); the standard Japanese name of the species is オオクチホシエソ (Kochi University fish-lab species
sheet "Malacosteus niger"; iNaturalist ja; Japanese Wikipedia "オオクチホシエソ").

Research (FishBase summary "Malacosteus niger"; Wikipedia "Malacosteus niger"; Sutton 2005, "Trophic ecology of
the deep-sea fish Malacosteus niger", J. Fish Biol.; Douglas et al. 1998 / Widder et al. 1984 on its red light):
* bathypelagic, 500-3886 m, most often caught at 700-1500 m; unlike most stomiids it does not make a diel
  vertical migration; worldwide, 66 N - 30 S
* up to 25.6 cm; slender black body, the dorsal and anal fins set far back near the tail
* one of the largest relative gapes of any fish: the lower jaw is about a quarter of the body length and has no
  floor (no skin between the jaw bones), so the long toothed jaw snaps shut through the water
* a large suborbital photophore below the eye emits far-red light (peak ~705-710 nm) that most deep-sea animals
  cannot see - a private searchlight; a smaller postorbital photophore behind the eye gives the usual blue light;
  small photophores in rows on the body
* despite the jaws, 69-83 % of the diet is calanoid copepods (whose chlorophyll derivative it uses as a far-red
  visual pigment), plus some fishes and shrimps; solitary

Game simplifications: copepods do not exist, so deep_sea_shrimp and lanternfish stand in as prey; the far-red light
is painted a visible deep red (real 710 nm light would look almost black to us too); the small body photophores are
painted, only the two head organs glow; harmless to players.
"""

INFO = dict(
    names=("Stoplight loosejaw", "オオクチホシエソ"),
    egg=(0x15171D, 0xC0302A),
    role="predator",
    voice="anglerfish",
    prey=["deep_sea_shrimp", "lanternfish"],
    sounds={
        "ambient": (2, "Loosejaw swims", "オオクチホシエソが泳ぐ"),
        "hurt": (2, "Loosejaw hurts", "オオクチホシエソが傷つく"),
        "death": (1, "Loosejaw dies", "オオクチホシエソが死ぬ"),
        "flop": (2, "Loosejaw flops", "オオクチホシエソが跳ねる"),
        "snap": (2, "Loosejaw snaps", "オオクチホシエソが食らいつく"),
    },
    # 500-3886 m, mostly 700-1500 m; no diel migration; solitary in open water
    spawn=dict(weight=6, group=(1, 1), depth_m=(500, 700, 1500, 3886), placement="open_water",
               cave_factor=0.6, open_factor=1.0, max_light=5, cap=(24, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.25, health=5, attack=1,
    # waits in the dark with its red searchlight and snaps with the floorless jaw
    traits=dict(steering=(20, 5, 0.0022), zone=("open_water", 0.9, 220), flees=(2.5, 8), hunts=(1.5, 0.35, 0.35), hangs_still=True,
                home=24, ambient=450),
    render=dict(eyeshine=(0.5, ["right_eye", "left_eye"]), glow=dict(bones=["head"], base=0.85, flicker=0.1)))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="abyssal_fish_fillet", count=(1, 2), looting=1, chance=0.7)]
