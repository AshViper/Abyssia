"""ユノハナガニ / Gandalfus yunohana (Takeda, Hashimoto & Ohta 2000, described as Austinograea yunohana).

Research (Yahagi et al. 2020, "Population connectivity of the crab Gandalfus yunohana", Journal of Crustacean
Biology; Hamasaki et al. 2010 on its larval stages; Ministry of the Environment of Japan, EBSA "Sagami Trough and
southern seamounts of the Izu-Ogasawara Arc"):
* 420-1400 m at hydrothermal vents of the Izu-Ogasawara (Izu-Bonin-Mariana) arc: Myojin Knoll, Suiyo Seamount,
  Kaikata Seamount, Nikko Seamount; named after the white "yunohana" hot-spring mineral deposits
* pale, almost white; squarish hairy carapace, reduced eyes, strong claws with darker tips, long walking legs
* a bythograeid: this family are the scavengers and opportunistic predators of vent communities (diet of this
  species itself little documented)

Game simplifications: real depths mapped onto the world's vent fields (fauna/__init__.py vent_depth); picks at the
bacterial film on vent rock and scavenges carrion items; warns with its claws and pinches whoever crowds or attacks
it (neutral); bound to its vent field.
"""

from fauna import vent_depth

INFO = dict(
    names=("Yunohana Crab", "ユノハナガニ"),
    egg=(0xECE8E0, 0x9A8F84),
    role="neutral",
    voice="crab",
    sounds={
        "ambient": (2, "Vent crab clicks", "ユノハナガニがカチカチ鳴る"),
        "step": (3, None, None),
        "hurt": (2, "Vent crab hurts", "ユノハナガニが傷つく"),
        "death": (1, "Vent crab dies", "ユノハナガニが死ぬ"),
        "snap": (2, "Vent crab snaps its claws", "ユノハナガニがはさみを鳴らす"),
    },
    spawn=dict(weight=12, group=(1, 2), depth_m=vent_depth(420, 1400), placement="seabed",
               cave_factor=1.0, open_factor=1.0, max_light=15, cap=(4, 16),
               habitat=[dict(blocks="#abyssia:fauna/vent", radius=4, min=1, factor=1.0, required=True)]),
)
