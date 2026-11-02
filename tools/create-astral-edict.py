"""Original pixel atlas for the ivory/gold Astral Edict staff.

The user reference informs the palette and layered silhouette only. No reference
image pixels, downloaded textures or image-generation outputs are consumed.
"""
from pathlib import Path
import hashlib, json
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/assets/two_i_combat_doll/textures/item/astral_edict.png'
PREVIEW = ROOT / 'verification/axolotl-20261027/astral-edict'
PREVIEW.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(27062703)
im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
palette = [(252,247,234), (230,176,77), (71,45,112), (158,100,40),
           (255,250,236), (135,222,255), (158,117,235), (27,29,73)]
for i, color in enumerate(palette):
    pixels = np.zeros((32,32,4), np.uint8)
    for y in range(32):
        for x in range(32):
            bevel = 12 if y<3 or x<2 else -17 if y>28 or x>29 else 0
            fleck = int(rng.integers(-4,5))
            # Faint, hand-cut material bands remain readable at item size.
            line = -5 if i==0 and (x+y//5)%15==0 else 0
            pixels[y,x,:3] = np.clip(np.array(color)+bevel+fleck+line,0,255)
            pixels[y,x,3] = 255
    im.paste(Image.fromarray(pixels), (i*32,0))

n=192
y,x=np.mgrid[:n,:n].astype(np.float32)
xx=(x-(n-1)/2)/(n/2);yy=(y-(n-1)/2)/(n/2)
r=np.sqrt(xx*xx+yy*yy);a=np.arctan2(yy,xx)
cloud=np.zeros((n,n),np.float32)
for _ in range(14):
    cx,cy=rng.uniform(-1.2,1.2,2); width=rng.uniform(.10,.40)
    cloud += rng.uniform(.08,.26)*np.exp(-((xx-cx)**2+(yy-cy)**2)/(width*width))
arm=np.exp(-((yy-.36*np.sin(xx*3.2)+xx*.30)/.19)**2)
grain=rng.random((n,n))
nebula=np.zeros((n,n,4),np.uint8)
nebula[:,:,0]=np.clip(29+cloud*118+arm*68+grain*5,0,255)
nebula[:,:,1]=np.clip(33+cloud*72+arm*92+grain*7,0,255)
nebula[:,:,2]=np.clip(90+cloud*122+arm*152+grain*8,0,255)
nebula[:,:,3]=255
im.paste(Image.fromarray(nebula),(0,64))
d=ImageDraw.Draw(im)
for _ in range(130):
    sx=int(rng.integers(4,188));sy=int(rng.integers(68,252))
    color=(211,230,255,255) if rng.random()>.35 else (196,162,255,255)
    d.point((sx,sy),fill=color)
    if rng.random()<.09:
        d.line((sx-2,sy,sx+2,sy),fill=color)
        d.line((sx,sy-2,sx,sy+2),fill=color)
# A pixel-cut stellar nucleus, not a lens-flare image.
for extent,color in [(28,(124,125,250,255)),(16,(177,211,255,255)),(8,(255,247,255,255))]:
    cx,cy=96,160
    d.polygon([(cx,cy-extent),(cx+3,cy-3),(cx+extent,cy),(cx+3,cy+3),
               (cx,cy+extent),(cx-3,cy+3),(cx-extent,cy),(cx-3,cy-3)],fill=color)
d.rectangle((94,158,98,162),fill=(255,255,255,255))
OUT.parent.mkdir(parents=True,exist_ok=True)
im.save(OUT)
im.resize((768,768),Image.Resampling.NEAREST).save(PREVIEW/'atlas-preview.png')
manifest={
    'asset':'textures/item/astral_edict.png','dimensions':[256,256],
    'generator':'tools/create-astral-edict.py','seed':27062703,
    'provenance':'Original deterministic procedural pixel material/nebula atlas. No external pixels.',
    'sha256':hashlib.sha256(OUT.read_bytes()).hexdigest(),
    'generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
}
(PREVIEW/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps(manifest,indent=2))
