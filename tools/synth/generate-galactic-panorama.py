"""Bake an original seamless stellar panorama. NumPy/Pillow; no source imagery.

Expensive domain-warped cloud detail is baked once, not recomputed per frame.
The runtime samples this world-aligned panorama for sky AND water reflections.
"""
from pathlib import Path
import hashlib
import json
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/two_i_combat_doll/textures/effect/galactic_panorama.png'
W, H = 2048, 1024
rng = np.random.default_rng(210027)
x, y = np.meshgrid(np.arange(W, dtype=np.float32) / W, np.arange(H, dtype=np.float32) / H)
lon, lat = (x - .5) * (2*np.pi), (.5-y)*np.pi


def noise(u, v, nx, ny, seed):
    grid = np.random.default_rng(seed).random((ny, nx), dtype=np.float32)
    xx, yy = np.mod(u, 1)*nx, np.mod(v, 1)*ny
    xi, yi = xx.astype(np.int32), yy.astype(np.int32)
    fx, fy = xx-xi, yy-yi
    fx, fy = fx*fx*(3-2*fx), fy*fy*(3-2*fy)
    a = grid[yi % ny, xi % nx]*(1-fx)+grid[yi % ny, (xi+1) % nx]*fx
    b = grid[(yi+1) % ny, xi % nx]*(1-fx)+grid[(yi+1) % ny, (xi+1) % nx]*fx
    return a*(1-fy)+b*fy


def fbm(u, v, seed, octaves=7, start=5):
    field = np.zeros_like(u)
    total, amp = 0., 1.
    for octave in range(octaves):
        field += noise(u, v, start*2**octave, max(3,start//2)*2**octave, seed+octave)*amp
        total += amp
        amp *= .55
    return field/total


# Longitude periodicity is maintained by every field; the poles fade to stars.
warp = fbm(x, y, 90, 5)
warp2 = fbm(x+.19, y+.37, 150, 5)
u = x + (warp2-.5)*.050
v = y + (warp-.5)*.055
cloud = fbm(u, v, 230)
fine = fbm(u+(cloud-.5)*.019, v, 350, 6, 23)
wisps = fbm(u, v+(cloud-.5)*.014, 420, 6, 11)
core_longitude = np.arctan2(np.sin(lon-.65), np.cos(lon-.65))
meander = .040*np.sin(lon*2+.4)+.025*np.sin(lon*5-1.5)+(warp-.5)*.18
distance = lat-meander
width = .12+.065*(np.sin(lon-1.1)*.5+.5)+.11*(cloud-.35)
belt = np.exp(-np.abs(distance/width)**1.40)
outskirts = np.exp(-np.abs(distance/(width*2.8))**1.25)
bulge = np.exp(-core_longitude**2/1.10-(distance/.34)**2)
# Different cloud ridges have finite knots and gaps, rather than uniform bands.
structure = np.maximum(.0, (cloud-.30)*2.7)**1.7
granules = np.maximum(0, (fine-.36)*2.0)**1.5
light = belt*(.14+structure*1.9+granules*.75)
light += outskirts*np.maximum(0,wisps-.46)*.65
light += bulge*(.22+structure*.6)
cool = np.array([.26,.34,.56],np.float32)
warm = np.array([.72,.55,.36],np.float32)
warmth = np.clip(bulge*.83+(wisps-.5)*.28,0,1)[...,None]
rgb = np.zeros((H,W,3),np.float32)+np.array([.003,.005,.013])
rgb += outskirts[...,None]*np.array([.010,.012,.033])
rgb += light[...,None]*(cool*(1-warmth)+warm*warmth)

# Great-Rift-like branching dusty gaps. Warp locally; never a straight black slit.
lane_center = meander+.042*np.sin(lon*3.0+.7)+(warp2-.5)*.18
lane_width = .026+.048*noise(x,y,17,7,570)
lane1 = np.exp(-((lat-lane_center)/lane_width)**2)
branch_center = lane_center+.10+.055*np.sin(lon*4.0-1.2)
lane2 = np.exp(-((lat-branch_center)/(.018+.022*cloud))**2)
broken = np.clip((fbm(u,v,630,5,14)-.28)*2.5,0,1)
dust = np.clip((lane1*.84+lane2*.62)*broken,0,.93)
dark_knots = np.maximum(0,(.48-wisps)*4.5)*belt*.26
rgb *= (1-np.clip(dust+dark_knots,0,.94))[...,None]

# Sparse emission regions and warm unresolved stellar dust.
emission = np.maximum(0,fbm(u+.31,v+.07,710,5,18)-.58)**2*18*belt
rgb += emission[...,None]*np.array([.48,.065,.19])
rgb += (np.maximum(0,fine-.58)**3*14*belt*(1-dust))[...,None]*np.array([.45,.40,.31])

# Individual stars follow spherical area and the cloud's stellar population.
# Gaussian splats avoid single-pixel aliasing; only rare stars get restrained rays.
population = 68000
sx = rng.integers(0,W,population)
sy = rng.integers(4,H-4,population)
probability = np.cos(lat[sy,sx])*(.10+.86*belt[sy,sx]*(1-dust[sy,sx]*.9))
keep = rng.random(population)<probability
sx,sy=sx[keep],sy[keep]
brightness = np.clip(rng.pareto(2.25,len(sx))*.08+.028,.018,2.8)
temperature = rng.random(len(sx))
colors = np.stack([np.ones(len(sx)),.77+.21*temperature,.53+.65*temperature],axis=1)
colors[temperature>.50] = np.stack([.68+.25*(1-temperature[temperature>.50]),.80+.18*(1-temperature[temperature>.50]),np.ones(np.sum(temperature>.50))],axis=1)
for dx in range(-3,4):
    for dy in range(-3,4):
        sigma = .48+np.minimum(brightness,.8)*.34
        weights=np.exp(-(dx*dx+dy*dy)/(2*sigma*sigma))
        values=brightness[:,None]*colors*weights[:,None]
        for c in range(3):
            np.add.at(rgb[...,c],((sy+dy)%H,(sx+dx)%W),values[:,c])
# A few distant, small diffraction crosses, never a field of giant sparkles.
bright=np.flatnonzero(brightness>.95)
for offset in range(-6,7):
    fall=np.exp(-abs(offset)*.64)*.09
    for c in range(3):
        np.add.at(rgb[...,c],(sy[bright],(sx[bright]+offset)%W),fall*colors[bright,c])
        np.add.at(rgb[...,c],((sy[bright]+offset)%H,sx[bright]),fall*colors[bright,c])

# Filmic shoulder keeps small hot cores and cloud detail instead of white clipping.
rgb = 1-np.exp(-np.maximum(0,rgb)*1.35)
pixels=np.uint8(np.clip(rgb,0,1)*255+.5)
OUT.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(pixels,'RGB').save(OUT,optimize=True)
OUT.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":false}}\n',encoding='utf-8')
report=dict(seed=210027,width=W,height=H,stars=len(sx),source='Entirely generated from deterministic noise and stellar distributions. No source imagery.',
            path=str(OUT),sha256=hashlib.sha256(OUT.read_bytes()).hexdigest(),runtime='One bilinear panorama lookup plus sparse foreground stars; same sampler for sky and water.')
evidence=ROOT/'verification/astral-20261027/galaxy-panorama.json'
evidence.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
Image.fromarray(pixels,'RGB').resize((1536,768),Image.Resampling.LANCZOS).save(evidence.with_suffix('.png'))
print(json.dumps(report,ensure_ascii=False,indent=2))
