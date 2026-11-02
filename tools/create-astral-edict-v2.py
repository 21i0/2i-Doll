"""Original 512px Astral Edict materials and stellar-core atlas.

References inform structure and colour; no reference pixels are included.
"""
from pathlib import Path
import hashlib
import json
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/assets/two_i_combat_doll/textures/item/astral_edict.png'
PREVIEW = ROOT / 'verification/astral-edict-20261028/materials'
PREVIEW.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(28106303)
im = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
palette = [(231, 228, 216), (220, 166, 73), (29, 34, 80), (117, 75, 31),
           (242, 229, 255), (107, 211, 250), (155, 91, 226), (14, 17, 45)]
yy, xx = np.mgrid[:64, :64]
for i, colour in enumerate(palette):
    bevel = np.where((xx < 3) | (yy < 3), 14, np.where((xx > 59) | (yy > 59), -19, 0))
    grain = rng.integers(-3, 4, (64, 64))
    bands = np.where(((xx + yy // 7) % 23 == 0), -5, 0)
    p = np.zeros((64, 64, 4), np.uint8)
    p[:, :, :3] = np.clip(np.array(colour) + (bevel + grain + bands)[:, :, None], 0, 255)
    p[:, :, 3] = 255
    if i == 2:
        for x in (8, 55):
            p[7:57, x:x+1, :3] = (120, 101, 87)
        p[8:10, 10:54, :3] = (50, 59, 117)
    im.paste(Image.fromarray(p), (i * 64, 0))

n = 384
y, x = np.mgrid[:n, :n].astype(np.float32)
u = (x - (n - 1) / 2) / (n / 2)
v = (y - (n - 1) / 2) / (n / 2)
r = np.sqrt(u*u + v*v)
noise = np.zeros((n, n), np.float32)
for scale, weight in ((6, .52), (13, .26), (27, .13), (65, .06)):
    coarse = Image.fromarray((rng.random((scale, scale))*255).astype(np.uint8))
    noise += np.asarray(coarse.resize((n, n), Image.Resampling.BICUBIC), np.float32) / 255 * weight
warp = np.sin(u*4.7 + noise*5.5)*.15 + np.sin(u*9.3-v*2)*.055
cyan = np.exp(-((v + u*.57 + warp) / (.09 + noise*.17))**2)
pink = np.exp(-((v - u*.72 + .26*np.sin(u*3.4+noise*3)) / (.08 + noise*.16))**2)
cloud = np.clip(noise-.24, 0, 1)
dust = (.5+.5*np.sin(np.arctan2(v, u)*2.5+r*7+noise*4))*np.exp(-r*1.7)
rgb = np.zeros((n, n, 3), np.float32)
rgb[:, :, 0] = 12 + cloud*54 + cyan*12 + pink*(75+cloud*120) + dust*17
rgb[:, :, 1] = 16 + cloud*57 + cyan*(72+cloud*155) + pink*20 + dust*21
rgb[:, :, 2] = 44 + cloud*105 + cyan*119 + pink*125 + dust*36
# Narrow rays leave the surrounding navy cloud visible.
radial = np.exp(-r*19)
ray_a = np.exp(-np.abs(v+u*.25)*135) * np.exp(-r*4.3)
ray_b = np.exp(-np.abs(u-v*.25)*145) * np.exp(-r*4.0)
rgb += radial[:, :, None]*np.array([155, 125, 160])
rgb += (ray_a + ray_b)[:, :, None]*np.array([93, 100, 122])
pixels = np.zeros((n, n, 4), np.uint8)
pixels[:, :, :3] = np.clip(rgb, 0, 255)
pixels[:, :, 3] = 255
im.paste(Image.fromarray(pixels), (0, 128))
d = ImageDraw.Draw(im)
for i in range(390):
    sx = int(rng.integers(4, 380)); sy = int(rng.integers(132, 508))
    c = (182, 220, 255, 255) if i % 3 else (228, 175, 255, 255)
    d.point((sx, sy), fill=c)
    if i % 41 == 0:
        d.line((sx-2, sy, sx+2, sy), fill=c)
        d.line((sx, sy-3, sx, sy+3), fill=c)
for cx, cy in ((88, 239), (286, 390), (154, 425)):
    for i in range(42):
        sx = int(np.clip(cx+rng.normal(0, 22), 4, 379))
        sy = int(np.clip(cy+rng.normal(0, 24), 132, 507))
        d.rectangle((sx, sy, sx+1, sy+1), fill=(169, 191, 248, 255))
d.rectangle((190, 318, 193, 321), fill=(255, 244, 255, 255))
OUT.parent.mkdir(parents=True, exist_ok=True)
im.save(OUT)
im.resize((1024, 1024), Image.Resampling.NEAREST).save(PREVIEW / 'atlas-preview.png')
im.crop((0, 128, 384, 512)).resize((768, 768), Image.Resampling.NEAREST).save(PREVIEW / 'core-preview.png')
manifest = {
    'asset': 'textures/item/astral_edict.png', 'dimensions': [512, 512],
    'generator': 'tools/create-astral-edict-v2.py', 'seed': 28106303,
    'provenance': 'Original deterministic pixel materials and layered nebula currents; no external pixels.',
    'sha256': hashlib.sha256(OUT.read_bytes()).hexdigest().upper(),
    'generator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest().upper(),
}
(PREVIEW / 'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
print(json.dumps(manifest, indent=2))
