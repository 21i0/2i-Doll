"""Bake original tileable fractured rock colour and relief, without source images.

RGB contains weathered stone; alpha is height. Runtime bounded parallax samples
this atlas instead of evaluating cellular noise repeatedly for every step.
"""
from pathlib import Path
import hashlib, json
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/assets/two_i_combat_doll/textures/effect/crimson_ground_relief.png'
EVIDENCE = ROOT / 'verification/axolotl-20261027'
N = 1024
SEED = 27102814
rng = np.random.default_rng(SEED)
y, x = np.mgrid[0:N, 0:N].astype(np.float32) / N

def smooth(a, b, value):
    t = np.clip((value-a)/(b-a), 0, 1)
    return t*t*(3-2*t)

def field(cells):
    grid = rng.random((cells, cells), dtype=np.float32)
    u=x*cells; v=y*cells
    ix=u.astype(np.int32); iy=v.astype(np.int32)
    fu=smooth(0, 1, u-ix); fv=smooth(0, 1, v-iy)
    return ((grid[iy%cells, ix%cells]*(1-fu)+grid[iy%cells, (ix+1)%cells]*fu)*(1-fv)
        +(grid[(iy+1)%cells, ix%cells]*(1-fu)+grid[(iy+1)%cells, (ix+1)%cells]*fu)*fv)

coarse=field(8); mid=field(32); fine=field(128); grain=field(512)
u=x*8+(field(8)-.5)*.19; v=y*8+(field(8)-.5)*.19
cellx=np.floor(u).astype(np.int32); celly=np.floor(v).astype(np.int32)
first=np.full((N,N), 100., np.float32); second=first.copy()
seed=np.zeros_like(first)
jitter=rng.random((8,8,3),dtype=np.float32)
for oy in range(-2,3):
    for ox in range(-2,3):
        cx=cellx+ox; cy=celly+oy
        point=jitter[cy%8,cx%8]
        distance=np.sqrt((cx+.15+point[:,:,0]*.7-u)**2+(cy+.15+point[:,:,1]*.7-v)**2)
        closer=distance<first
        second=np.where(closer,first,np.minimum(second,distance))
        seed=np.where(closer,point[:,:,2],seed)
        first=np.minimum(first,distance)

edge=second-first
width=.025+.033*seed+.013*mid
bevel=smooth(width,width+.115,edge)
plate=.65+seed*.19+(coarse-.5)*.06+(mid-.5)*.032
height=.055+bevel*(plate-.055)
height+=np.exp(-((edge-width-.11)/.035)**2)*(.035+.035*seed)
height+=(fine-.5)*.010*bevel
# Smaller eroded fissures terminate within the large plates, not a second grid.
vein=np.abs(np.sin(u*9+v*3+(mid-.5)*3))
small=(1-smooth(.022,.085,vein))*smooth(.2,.55,edge)*smooth(.45,.7,mid)
height-=small*.075
height=np.clip(height,0,1)
weather=.55+.22*coarse+.16*mid+.07*fine
base=np.stack((.225+.065*seed, .115+.035*seed, .101+.029*seed),axis=-1)
rgb=base*weather[:,:,None]
rgb*= (.83+.17*grain)[:,:,None]
# Broken rim and occasional pale mineral inclusions have real surface variation.
rim=np.exp(-((edge-width-.085)/.055)**2)
rgb+=rim[:,:,None]*np.array([.065,.038,.029])
fleck=smooth(.86,.98,grain)*smooth(.65,.80,fine)*bevel
rgb+=fleck[:,:,None]*np.array([.20,.14,.10])
rgb*= (.30+.70*bevel)[:,:,None]
image=np.concatenate((np.clip(rgb,0,1),height[:,:,None]),axis=-1)
OUT.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(np.uint8(image*255),'RGBA').save(OUT)
manifest={
    'origin':'Original deterministic periodic cellular rock, authored bevels and weathering; no image input',
    'source_images_read':[], 'seed':SEED, 'size':[N,N],
    'generator':str(Path(__file__).relative_to(ROOT)).replace('\\','/'),
    'generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest().upper(),
    'asset':str(OUT.relative_to(ROOT)).replace('\\','/'),
    'asset_sha256':hashlib.sha256(OUT.read_bytes()).hexdigest().upper(),
    'channels':{'rgb':'weathered stone albedo','alpha':'linear surface height, 0 deep to 1 high'},
    'world_period_blocks':18, 'maximum_relief_blocks':.38,
}
(EVIDENCE/'crimson-ground-bake.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
Image.fromarray(np.uint8(height*255),'L').save(EVIDENCE/'crimson-ground-height-preview.png')
Image.fromarray(np.uint8(np.clip(rgb*2,0,1)*255),'RGB').save(EVIDENCE/'crimson-ground-colour-preview.png')
print(json.dumps(manifest,ensure_ascii=False,indent=2))
