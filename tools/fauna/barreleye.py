"""デメニギス / barreleye, Macropinna microstoma.

Research (Robison & Reisenbichler 2008, "Macropinna microstoma and the paradox of its tubular eyes", Copeia; MBARI
news release 2009; FishBase summary "Macropinna microstoma"):
* 600-800 m, recorded to about 1000 m; ~15 cm
* a transparent, fluid-filled shield covers the head; inside, tubular eyes with green lenses (yellow pigment filters
  out the downwelling light to reveal bioluminescence) look straight up; the "eyes" in front are its nostrils
* hangs nearly motionless, level, stabilised by large pectoral fins, watching for silhouettes above
* the eyes rotate forward as the body tilts upright to take prey - probably stolen from siphonophore tentacles
* small mouth; dark body with large scales

Game simplifications: no siphonophores - feeding is shown as the upright tilt and a pluck of the mouth; crystal
caves count as a habitat (the "some fish in crystal caves" link).
"""

INFO = dict(
    names=("Barreleye", "デメニギス"),
    egg=(0x2B2F36, 0x6EDC8C),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Barreleye hovers", "デメニギスが漂う"),
        "hurt": (2, "Barreleye hurts", "デメニギスが傷つく"),
        "death": (1, "Barreleye dies", "デメニギスが死ぬ"),
        "flop": (2, "Barreleye flops", "デメニギスが跳ねる"),
    },
    spawn=dict(weight=5, group=(1, 1), depth_m=(400, 600, 800, 1100), placement="open_water",
               cave_factor=1.0, open_factor=0.6, max_light=7, cap=(24, 64),
               habitat=[dict(blocks="#abyssia:fauna/crystal", radius=4, min=2, factor=3.0)]),
)
