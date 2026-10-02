import numpy as np, sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
import os; os.makedirs(dst, exist_ok=True)
for i in range(6):
    a = np.asarray(Image.open(f'{src}/panorama_{i}.png').convert('RGB').resize((1024,1024), Image.LANCZOS)).astype(np.float32)/255
    lum = a @ np.array([0.299,0.587,0.114])
    # chroma of "plain water": bluish & mid-luminance -> push down hard; saturated warm/bright stays
    blue = np.clip((a[...,2]-np.maximum(a[...,0],a[...,1]))*3,0,1)
    k = 0.62 + 0.38*np.clip((lum-0.35)/0.45,0,1)**1.2        # brightness keep factor
    k = k*(1-0.25*blue) + 0.25*blue*0.45
    out = a*k[...,None]
    out = out**1.08
    out *= np.array([0.78,0.92,1.0])
    h = np.linspace(0,1,1024)[:,None]
    if i < 4:   # side faces: darker towards the top (no sunlight), horizon haze fades distance
        out *= (0.55 + 0.45*h)[...,None]
    elif i == 4: # up: near black
        out *= 0.42
    else:        # down: floor, slightly dim
        out *= 0.9
    Image.fromarray((np.clip(out,0,1)*255).astype(np.uint8)).save(f'{dst}/panorama_{i}.png')
strip = Image.new('RGB',(256*6,256))
for i in range(6): strip.paste(Image.open(f'{dst}/panorama_{i}.png').resize((256,256)),(i*256,0))
strip.save(f'{dst}/strip.png')
