"""ダイオウグソクムシ / giant isopod, Bathynomus giganteus.

Research (Wikipedia "Giant isopod" and "Bathynomus giganteus"; Aquarium of the Pacific; Lowry & Dempsey 2006,
"The giant deep-sea scavenger genus Bathynomus"):
* 170-2140 m on the West Atlantic continental slope; over 80 % found between 365 and 730 m
* typically 19-36 cm long, largest confirmed ~50 cm
* bottom dweller of mud and clay seafloors, solitary; may shelter in pits dug in the sediment
* scavenger and facultative predator: fish, cephalopods, decapods, whale carcasses, sponges, sea cucumbers;
  finds food by chemoreception; gorges when food arrives and can fast for years (5 years in captivity)
* rigid calcareous exoskeleton of overlapping segments; 7 pairs of walking legs; two pairs of antennae; compound
  eyes of ~4000 facets set far apart with a reflective tapetum (eye shine, not light); curls up in defence
* pale lilac to brown; walks slowly, can swim

Game simplifications: walks only (no swimming); carrion sinks to the seabed so it can be found.
"""

INFO = dict(
    names=("Giant Isopod", "ダイオウグソクムシ"),
    egg=(0xA9A3B5, 0x5D566B),
    role="passive",
    sounds={
        "ambient": (3, "Giant isopod clicks", "ダイオウグソクムシがカチカチと鳴る"),
        "step": (4, None, None),
        "hurt": (2, "Giant isopod hurts", "ダイオウグソクムシが傷つく"),
        "death": (1, "Giant isopod dies", "ダイオウグソクムシが死ぬ"),
        "curl": (2, "Giant isopod curls up", "ダイオウグソクムシが丸くなる"),
        "eat": (3, "Giant isopod feeds", "ダイオウグソクムシが食べる"),
    },
    # 170-2140 m, over 80 % between 365 and 730 m; solitary, on mud and clay, sheltering by rock
    spawn=dict(weight=12, group=(1, 2), depth_m=(170, 365, 730, 2140), placement="seabed",
               cave_factor=1.3, open_factor=1.0, max_light=15, cap=(24, 48),
               habitat=[dict(blocks="#abyssia:fauna/rock", radius=3, min=5, factor=2.0)],
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=1.4)]),
)
