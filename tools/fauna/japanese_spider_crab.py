"""タカアシガニ / Japanese spider crab, Macrocheira kaempferi (Temminck, 1836).

Research (Wikipedia "Japanese spider crab"; Animal Diversity Web "Macrocheira kaempferi"; SeaLifeBase
"Macrocheira kaempferi"; JAMSTEC BISMaL 9000230 "タカアシガニ"):
* Pacific side of Japan (Honshu to Kyushu, ~30-40 deg N), best known from Suruga, Sagami and Tosa bays and off the
  Kii Peninsula; adults 50-600 m, most often 150-300 m (regularly ~300 m in Suruga Bay); moves up to ~50 m to spawn
* sandy and rocky bottoms of the outer shelf and upper slope; the knobbly, spiny carapace camouflages it on rock
* the largest leg span of any arthropod: up to ~3.7 m claw tip to claw tip, but the pear-shaped carapace is only
  ~37-40 cm wide and the animal weighs up to ~19 kg; males have longer chelipeds than females
* orange-red with white spots along the legs; stalked eyes; two short horns at the front of the carapace
* omnivorous scavenger: carrion and dead fish, molluscs and other invertebrates, some algae; slow, deliberate,
  mostly nocturnal, of "gentle disposition" despite its looks; usually met singly
* no bioluminescence

Game simplifications: body plus legs about 2 m instead of the full 3.7 m span, so it still fits between rocks;
passive (it never attacks the player); spawns singly or in pairs on rocky shelf/slope floors, not only in Japanese
waters; no spawning migration.
"""

INFO = dict(
    names=("Japanese Spider Crab", "タカアシガニ"),
    egg=(0xB8573A, 0xE8DCCB),
    role="passive",
    voice="crab",
    sounds={
        "ambient": (2, "Spider crab clicks", "タカアシガニがカチカチ鳴る"),
        "hurt": (2, "Spider crab hurts", "タカアシガニが傷つく"),
        "death": (1, "Spider crab dies", "タカアシガニが死ぬ"),
        "step": (3, None, None),
    },
    # adults 50-600 m, most often 150-300 m (Suruga Bay ~300 m); sandy and rocky shelf/slope bottoms
    spawn=dict(weight=8, group=(1, 2), depth_m=(50, 150, 350, 600), placement="seabed",
               cave_factor=0.8, open_factor=1.0, max_light=15, cap=(8, 24),
               habitat=[dict(blocks="#abyssia:fauna/rock", radius=3, min=3, factor=1.5)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py). Real span up to 3.7 m: sized as ~2 m.
INFO["java"] = dict(kind="walker", size_m=2.0, health=30, armor=6,
    # slow, deliberate stilt-walker that wanders a small home range
    traits=dict(move_clip="walk", speed=0.4, wander=140, home=16, ambient=500),
    render=dict(fish=False, pitch=False, eyeshine=(0.3, ["right_eye", "left_eye"])))
