"""リュウグウノツカイ / giant oarfish, Regalecus glesne.

Research (FishBase summary "Regalecus glesne"; Australian Museum "Oarfish"; Wikipedia "Giant oarfish" (ROV records in
the Gulf of Mexico); MarineBio "Oarfishes"):
* circumglobal, epi- to mesopelagic open ocean: FishBase 20-1000 m (usually given as 20-200 m, from surface and
  stranded fish); ROV sightings and the mesopelagic literature put the adults mostly at ~250-1000 m (Gulf of Mexico
  ROV records 463-492 m); no diel vertical migration documented
* the longest bony fish: record ~8 m (unconfirmed to 11 m), commonly 3-5 m; laterally compressed, scaleless silver
  ribbon with small dark spots and streaks, bluish on the head
* one dorsal fin runs the whole back; its first 10-13 rays form a tall red crest on the head, with red spots and skin
  flaps at the tips; the pelvic fins are single long red rays with paddle-like tips ("oars"); no anal fin; the tail
  tapers to a point and is often lost (self-amputation)
* swims by rippling the dorsal fin with the body held straight (amiiform), and is often filmed hanging vertically,
  head up, crest raised; slow, late flight response to ROVs; solitary
* small protrusible toothless mouth: suction-feeds on euphausiids (krill), other small crustaceans, small fishes and
  squid, strained by the gill rakers
* no light organs
* Japanese folklore: "messenger from the sea-god's palace", strandings linked (without evidence) to earthquakes

Game simplifications: a typical 4 m adult (not the record); the head crest is merged into the red dorsal ribbon (the
eel template has no separate head crest); spawn band uses the mesopelagic adult range, not the shallow strandings;
harmless, hangs still and turns slowly, flees late.
"""

INFO = dict(
    names=("Oarfish", "リュウグウノツカイ"),
    egg=(0xB9C3CC, 0xC8303A),
    role="passive",
    voice="gulper_eel",
    sounds={
        "ambient": (2, "Oarfish ripples its fin", "リュウグウノツカイがひれを波打たせる"),
        "hurt": (2, "Oarfish hurts", "リュウグウノツカイが傷つく"),
        "death": (1, "Oarfish dies", "リュウグウノツカイが死ぬ"),
        "flop": (2, "Oarfish flops", "リュウグウノツカイが跳ねる"),
    },
    # FishBase 20-1000 m; adults mostly ~250-1000 m (ROV records 463-492 m)
    spawn=dict(weight=3, group=(1, 1), depth_m=(20, 250, 1000, 1000), placement="open_water",
               cave_factor=0.3, open_factor=1.0, max_light=8, clearance=4, cap=(4, 128)),
    java=dict(kind="swimmer", size_m=4.0, health=16,
              traits=dict(steering=(6, 3, 0.0015), zone=("open_water", 0.6, 240), hangs_still=True,
                          flees=(1.8, 6), home=48, ambient=400)),
)
