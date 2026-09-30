"""ニジクラゲ / silky medusa, Colobonema sericeum Vanhöffen, 1902.
Cnidaria > Hydrozoa > Trachymedusae > Rhopalonematidae (WoRMS: accepted). A hydromedusa, not a scyphozoan jellyfish.

Research (MBARI "Silky jelly"; WoRMS AphiaID 117854; JAMSTEC BISMaL "ニジクラゲ"; Wikipedia "Colobonema sericeum"):
* mesopelagic, 200-700 m (MBARI)
* bell up to 4.5 cm; 32 tentacles, curled and spread out while it rests in the water; semi-transparent, silky bell
* one of the fastest jellies: an escape jet carries it more than five body lengths per stroke
* eats small crustaceans, jellies and fish
* bioluminescent: brilliant blue flashes from the BELL; the tentacles do not make light
* defence: sheds sticky tentacles as decoys while it flees, then regrows them (MBARI; no peer-reviewed paper found)
* sting risk to humans: UNKNOWN

Game simplifications: the shed tentacles are shown as a burst of pale drifting strands (they do not glow) and grow back
after about a minute; the faint resting glow is a readability aid (real flashes happen only when disturbed).
"""

INFO = dict(
    names=("Silky Medusa", "ニジクラゲ"),
    egg=(0xC9D3DE, 0x6FB2FF),
    role="passive",
    voice="jelly",
    sounds={
        "ambient": (2, "Silky medusa pulses", "ニジクラゲが拍動する"),
        "pulse": (2, "", ""),
        "hurt": (2, "Silky medusa flinches", "ニジクラゲが縮む"),
        "death": (1, "Silky medusa dies", "ニジクラゲが死ぬ"),
    },
    spawn=dict(weight=10, group=(2, 4), depth_m=(150, 250, 650, 900), placement="open_water",
               cave_factor=0.6, open_factor=1.0, max_light=7, cap=(10, 48)),
)
