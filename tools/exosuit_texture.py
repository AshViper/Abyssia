# One-off painter for textures/entity/exosuit.png: 64x64, 4x4 cells of 16px, one material per cell
# (index = row*4+col): 0 hull,1 metal,2 joint,3 piston,4 glass,5 lamp,6 vent,7 drill,8 hazard,9 tread,10 grille.
import random, sys
from PIL import Image
random.seed(7)
O=(198,110,30);O2=(181,100,27);OH=(225,133,50);OD=(69,41,15)
G=(60,69,75);G2=(47,51,54);GH=(97,108,116);GD=(24,26,27);GM=(85,92,98)
im=Image.new("RGBA",(64,64),(0,0,0,0)); px=im.load()
def cell(i,f):
    cx,cy=(i%4)*16,(i//4)*16
    for y in range(16):
        for x in range(16):
            c=f(x,y)
            if len(c)==3: c=c+(255,)
            px[cx+x,cy+y]=c
def jit(c,a=6):
    d=random.randint(-a,a); return tuple(max(0,min(255,v+d)) for v in c)
def hull(x,y):
    if x in(0,15) or y==15: return O2
    if y==0: return OH
    if (x,y) in((2,2),(13,2),(2,13),(13,13)): return OD
    if y==8 and 1<x<14: return O2
    return jit(O,5)
def metal(x,y):
    if x in(0,15) or y in(0,15): return G2
    if y==1: return GH
    if x==7 and 2<y<13: return G2
    if (x,y) in((3,3),(12,3),(3,12),(12,12)): return GM
    return jit(G,4)
def joint(x,y): return GD if y%3==0 else (G2 if y%3==1 else jit((40,44,47),3))
def piston(x,y):
    s=[93,110,140,175,200,175,150,130,115,100,93,85,78,72,66,60][x]; return (s,s+8,s+12)
def glass(x,y):
    if x in(0,15) or y in(0,15): return (150,200,215,170)
    if x+y in(5,6,7) or x+y==20: return (220,245,250,140)
    return (110,175,200,80)
def lamp(x,y):
    r=max(abs(x-7.5),abs(y-7.5))
    return (255,250,220) if r<4 else ((255,232,160) if r<6.5 else (210,180,110))
def vent(x,y):
    r=max(abs(x-7.5),abs(y-7.5))
    if r>6.5: return G2
    if r>5: return GH
    if r>3.5: return GD
    return (120,70,30) if r>1.5 else (60,40,25)
def drill(x,y): k=(x+y*2)%6; return [(150,158,164),(150,158,164),GH,(63,69,73),(63,69,73),GM][k]
def hazard(x,y): return OH if (x+y)%8<4 else GD
def tread(x,y): return GD if y%4<2 else (G2 if x%4 else GD)
def grille(x,y):
    if x in(0,15) or y in(0,15): return G2
    return GD if y%3==1 else (GH if y%3==2 else G)
for i,f in enumerate([hull,metal,joint,piston,glass,lamp,vent,drill,hazard,tread,grille]): cell(i,f)
im.save(sys.argv[1])
