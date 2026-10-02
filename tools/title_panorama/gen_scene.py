# Generates the T02 title-panorama scene as AutoShot steps (scratch only).
import random, math, sys
R = random.Random(7)
FY = -201          # floor top y
cmds = []
def c(s, w=1): cmds.append((s, w))
def sb(x, y, z, b): c(f"setblock {x} {y} {z} {b}")
def fill(x1,y1,z1,x2,y2,z2,b,extra=""): c(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {b}{extra}")
# water box (chunked) and floor
for y in range(FY+1, -156, 4):
    fill(-40, y, -40, 40, min(y+3, -156), 40, "minecraft:water")
for y in (FY-5, FY-2):
    fill(-40, y, -40, 40, y+2, 40, "abyssia:deep_sea_rock")
# floor relief: mounds, sediment patches
H = {}
for x in range(-40, 41):
    for z in range(-40, 41):
        h = 0
        h += 1.6*math.sin(x/7.0)+1.3*math.cos(z/6.0)+0.9*math.sin((x+z)/4.3)
        H[(x,z)] = max(0, int(round(h)))
for (x,z),h in H.items():
    if h>0: fill(x,FY+1,z,x,FY+h,z,"abyssia:deep_sea_rock")
top = lambda x,z: FY+H[(x,z)]
for _ in range(70):
    x,z = R.randint(-38,38), R.randint(-38,38)
    b = R.choice(["abyssia:deep_sediment","abyssia:black_vent_rock","abyssia:mineral_sediment","abyssia:seafloor_pebbles" ])
    if b.endswith("pebbles"): sb(x, top(x,z)+1, z, b)
    else: fill(x-1, top(x,z), z-1, x+1, top(x,z), z+1, b, " replace abyssia:deep_sea_rock")
# vent chimneys
def chimney(cx, cz, h, r0):
    y0 = top(cx,cz)
    for i in range(h):
        r = max(0, round(r0*(1-i/h)))
        b = "abyssia:black_vent_rock" if i%3 else "abyssia:sulfur_vent_rock"
        fill(cx-r, y0+1+i, cz-r, cx+r, y0+1+i, cz+r, b)
    sb(cx, y0+h+1, cz, "abyssia:thermal_vent")
    for _ in range(10):
        x,z = cx+R.randint(-4,4), cz+R.randint(-4,4)
        sb(x, top(x,z)+1, z, R.choice(["abyssia:thermal_tube","abyssia:sulfur_cluster","abyssia:vent_grass","abyssia:thermal_crystal_cluster"]))
chimney(7, 9, 11, 3)
chimney(-12, 14, 7, 2)
chimney(16, 2, 5, 1)
# glow fields
glow = ["abyssia:glow_anemone","abyssia:glow_coral","abyssia:abyssal_bloom","abyssia:lumen_quill","abyssia:hadal_bloom","abyssia:floating_bloom","abyssia:glowtip_grass"]
for _ in range(260):
    a = R.random()*math.tau; d = 4+R.random()*30
    x,z = int(math.cos(a)*d), int(math.sin(a)*d)
    if abs(x)>39 or abs(z)>39: continue
    sb(x, top(x,z)+1, z, R.choice(glow))
for _ in range(40):
    x,z = R.randint(-36,36), R.randint(-36,36)
    sb(x, top(x,z), z, R.choice(["abyssia:lumen_rock","abyssia:luminous_moss"]) if R.random()<0.5 else "abyssia:lumen_rock")
for _ in range(30):
    x,z = R.randint(-38,38), R.randint(-38,38)
    for i in range(R.randint(3,9)): sb(x, top(x,z)+1+i, z, "abyssia:deep_kelp")
# ruins (west-north)
fill(-20,top(-17,-12)+1,-15,-12,top(-17,-12)+1,-9,"abyssia:deep_sea_rock_bricks")
for x,z,h in [(-20,-15,6),(-12,-15,4),(-20,-9,3),(-16,-15,5)]:
    fill(x,top(x,z)+1,z,x,top(x,z)+h,z,"abyssia:chiseled_deep_sea_rock")
fill(-19,top(-16,-15)+1,-15,-13,top(-16,-15)+3,-15,"abyssia:cracked_deep_sea_rock_bricks")
fill(-19,top(-16,-15)+2,-15,-17,top(-16,-15)+3,-15,"minecraft:water")
# industrial outpost (east-south)
bx,bz=14,-14; by=top(bx,bz)+5
for x,z in [(bx-3,bz-3),(bx+3,bz-3),(bx-3,bz+3),(bx+3,bz+3)]:
    fill(x,top(x,z)+1,z,x,by-1,z,"abyssia:industrial_beam[axis=y]")
fill(bx-3,by,bz-3,bx+3,by,bz+3,"abyssia:metal_grating")
sb(bx,by+1,bz,"abyssia:hydrothermal_generator[facing=west]")
sb(bx-3,by+1,bz-3,"abyssia:work_light[facing=up]"); sb(bx+3,by+1,bz+3,"abyssia:warning_light[facing=up]")
sb(bx+3,by+1,bz-3,"abyssia:work_light[facing=up]")
fill(bx-4,by+1,bz,bx-12,by+1,bz,"abyssia:industrial_pipe[east=true,west=true]")
# far silhouettes
tx,tz=-30,26
for i in range(34):
    r = 3 if i<20 else 2
    fill(tx-r,top(tx,tz)+1+i,tz-r,tx+r,top(tx,tz)+1+i,tz+r,"abyssia:deep_sea_rock_bricks" if i%5 else "abyssia:chiseled_deep_sea_rock")
fill(tx-2,top(tx,tz)+2,tz-3,tx+2,top(tx,tz)+30,tz+3,"minecraft:water"," replace abyssia:deep_sea_rock_bricks") if False else None
ax,az=30,30
fill(ax-6,top(ax,az)+1,az,ax-5,top(ax,az)+14,az,"abyssia:deep_sea_rock_bricks")
fill(ax+5,top(ax,az)+1,az,ax+6,top(ax,az)+14,az,"abyssia:deep_sea_rock_bricks")
fill(ax-6,top(ax,az)+15,az,ax+6,top(ax,az)+16,az,"abyssia:deep_sea_rock_bricks")
fill(-35,top(-35,-32)+1,-32,-33,top(-35,-32)+22,-30,"abyssia:black_vent_rock")
# creatures (NoAI, persistent)
mobs = [("atolla_jelly",3,-4,6),("silky_medusa",-5,-3,9),("helmet_jelly",-2,2,5),("giant_phantom_jelly",-10,6,-10),
        ("atolla_jelly",9,1,-6),("silky_medusa",12,4,12),("anglerfish",5,-7,-3),("viperfish",-7,-5,-6),
        ("barreleye",-3,-2,-9),("gulper_eel",10,-2,4),("vent_eelpout",7,-9,11),("helmet_jelly",-14,0,-3),
        ("frilled_shark",-18,3,18),("giant_squid",22,6,-22),("deep_sea_shrimp",2,-9,7)]
CY=FY+13
for m,x,dy,z in mobs:
    yaw=R.randint(0,359)
    c(f"summon abyssia:{m} {x}.5 {CY+dy} {z}.5 {{NoAI:1b,PersistenceRequired:1b,Rotation:[{yaw}f,0f]}}")
c("kill @e[type=item]", 2)
out = []
for s,w in cmds:
    s = s.replace('"','\\"')
    out.append(f'        cmd("{s}", {w});')
open(sys.argv[1],'w',encoding='utf-8').write("\n".join(out)+"\n")
print(len(cmds), "commands, camera y", CY)
