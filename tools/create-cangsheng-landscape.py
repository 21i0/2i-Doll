"""Original offline height-field landscape. No reference picture pixels are used.

The expensive terrain intersection and shadow work is baked here, not performed
per frame. The transparent panoramic atlas is shared by sky and reflected water.
"""
from pathlib import Path
import json, hashlib, time
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'src/main/resources/assets/two_i_combat_doll/textures/effect/cangsheng_landscape.png'
RNG=np.random.default_rng(270627)
ROCK_SOURCE=ROOT/'verification/axolotl-20261027/rock_boulder_cracked_diff_1k.jpg'
rock_photo=np.asarray(Image.open(ROCK_SOURCE).convert('L'),dtype=np.float32)/255
N=1536; SPACING=1.0; HALF=N*SPACING/2
axis=np.arange(N,dtype=np.float32)*SPACING-HALF
x,z=np.meshgrid(axis,axis)
def field(size):
    a=RNG.random((size,size),dtype=np.float32)
    return np.asarray(Image.fromarray(a).resize((N,N),Image.Resampling.BICUBIC)).copy()
large=field(24); medium=field(91); fine=field(337)
height=np.full((N,N),-5.,np.float32)
for i in range(95):
    angle=RNG.uniform(-np.pi,np.pi); radius=RNG.uniform(95,610)
    cx=np.sin(angle)*radius; cz=-np.cos(angle)*radius
    width=RNG.uniform(23,66); depth=RNG.uniform(22,63)
    peak=RNG.uniform(50,145)*(0.7+radius/1000)
    dx=(x-cx+(medium-.5)*10+(large-.5)*13)/width
    dz=(z-cz+(medium-.5)*7-(large-.5)*11)/depth
    q=np.sqrt(dx*dx+dz*dz)
    # Broad mass, flanking shoulders and eroded vertical ridges; no cone forest.
    angle=np.arctan2(dz,dx)
    q*=1+.10*np.sin(angle*5+cx)+.055*np.sin(angle*11+cz)
    rock=np.power(np.clip(1-q,0,1),.73)
    shoulder=np.exp(-((dx-.45)**2/.43+(dz+.08)**2/.72))*0.60
    profile=np.maximum(rock,shoulder)*peak
    profile*=.60+.33*large+.18*medium
    profile-=np.maximum(0,1-fine)*np.maximum(0,q-.10)*7
    height=np.maximum(height,profile-9)
height=np.where(height>0,height+(large-.5)*6+(medium-.5)*2,height)
# Central distant mass, with two lower shoulders rather than a triangular icon.
q=np.sqrt((x/75)**2+((z+210)/68)**2)
height=np.maximum(height,np.maximum(0,1-q)**.68*(100+medium*35)-5)
height=np.where(height>2,height+(medium-.5)*19+(fine-.5)*7,height)
height=height.astype(np.float32)
gz,gx=np.gradient(height,SPACING)
def sample(a,xx,zz):
    fx=np.clip((xx+HALF)/SPACING,0,N-2);fz=np.clip((zz+HALF)/SPACING,0,N-2)
    ix=fx.astype(np.int32);iz=fz.astype(np.int32);u=fx-ix;v=fz-iz
    return (a[iz,ix]*(1-u)+a[iz,ix+1]*u)*(1-v)+(a[iz+1,ix]*(1-u)+a[iz+1,ix+1]*u)*v

W,H=2048,768
image=np.zeros((H,W,4),np.uint8)
az=np.linspace(-np.pi,np.pi,W,endpoint=False,dtype=np.float32)
sx=np.sin(az); sz=-np.cos(az)
for row in range(0,H,64):
    rows=np.arange(row,min(H,row+64),dtype=np.float32)
    slopes=(1-rows[:,None]/(H-1))*1.25-.10
    hit=np.zeros((len(rows),W),bool); distance=np.zeros_like(hit,dtype=np.float32)
    # All search work is offline; logarithmic steps preserve close silhouettes.
    previous=32.; low=np.zeros_like(distance)
    for d in np.geomspace(35,730,390):
        terrain=sample(height,sx[None,:]*d,sz[None,:]*d)
        now=(4+slopes*d<=terrain)&~hit
        distance[now]=d;low[now]=previous;hit|=now;previous=d
    for _ in range(7):
        d=(low+distance)*.5
        below=4+slopes*d<=sample(height,sx[None,:]*d,sz[None,:]*d)
        distance=np.where(below,d,distance);low=np.where(below,low,d)
    px=sx[None,:]*distance; pz=sz[None,:]*distance
    py=sample(height,px,pz)
    nx=-sample(gx,px,pz); nz=-sample(gz,px,pz)
    inv=1/np.sqrt(nx*nx+nz*nz+1)
    light=np.clip((nx*-.45+.73+nz*.52)*inv,0,1)
    shadow=np.ones_like(py)
    for step in (4,10,22,48,90):
        occlusion=sample(height,px-step*.52,pz+step*.60)>py+step*.73
        shadow*=np.where(occlusion,.84,1)
    light=(.39+.61*light)*shadow
    noise=sample(fine,px,pz); broad=sample(medium,px,pz)
    # Violet-grey rock with cream sunlit planes and vertical weathering.
    dark=np.array([.39,.37,.52]); bright=np.array([.88,.82,.92])
    rgb=dark[None,None,:]+(bright-dark)[None,None,:]*light[:,:,None]
    streak=np.sin(px*.91+np.sin(pz*.3)*2+noise*2)*.5+.5
    strata=np.sin(py*.92+noise*2+np.sin(px*.13+pz*.07)*2)*.5+.5
    rgb*= (.77+.13*streak+.10*noise+.13*strata)[:,:,None]
    # Redistributable CC0 stone microstructure, projected in world coordinates.
    # The tint and all terrain silhouettes remain authored for this stage.
    ru=((px*.026+pz*.017)*rock_photo.shape[1]).astype(np.int32)%rock_photo.shape[1]
    rv=(py*.039*rock_photo.shape[0]).astype(np.int32)%rock_photo.shape[0]
    mineral=rock_photo[rv,ru]
    rgb*=(.62+mineral*.72)[:,:,None]
    slope=np.sqrt(nx*nx+nz*nz)
    snow=np.clip((py-61+noise*32)/43,0,1)*np.clip(1.3-slope*.25,0,1)
    rgb=rgb*(1-snow[:,:,None]*.8)+np.array([.97,.92,.98])*snow[:,:,None]*.8
    # Vegetated cliff ledges: clusters, never horizontal neon stripes.
    grove=np.clip((broad-.43)*7,0,1)*np.clip(2.6-slope,0,1)*np.clip((py-8)/20,0,1)
    blossom=np.array([.94,.61,.77])*(.65+.35*light[:,:,None])
    rgb=rgb*(1-grove[:,:,None]*.80)+blossom*grove[:,:,None]*.80
    creek=(np.sin(px*.118+np.sin(pz*.038)*1.8)>.9990)&(slope>1.3)&(py>17)&(broad>.45)
    rgb=np.where(creek[:,:,None],np.array([.84,.93,.99]),rgb)
    # Distance haze separates the ranges; valleys retain soft pearl cloud banks.
    fog=np.clip((distance-90)/900,0,.64)+np.exp(-((py-12)/11)**2)*.22
    rgb=rgb*(1-fog[:,:,None])+np.array([.86,.81,.92])*fog[:,:,None]
    image[row:row+len(rows),:,:3]=np.clip(rgb*255,0,255).astype(np.uint8)
    image[row:row+len(rows),:,3]=hit.astype(np.uint8)*255
    print('baked rows',row+len(rows),'/',H,flush=True)
OUT.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(image).save(OUT,optimize=True)
record={'origin':'Original deterministic height-field terrain, no reference image/video pixels',
        'size':[W,H],'seed':270627,'terrain_grid':[N,N],
        'sha256':hashlib.sha256(OUT.read_bytes()).hexdigest(),
        'stone_microstructure':{'asset':'Rock Boulder Cracked','source':'https://polyhaven.com/a/rock_boulder_cracked',
        'authors':['Dario Barresi','Dimitrios Savva'],'license':'CC0-1.0',
        'local_source':str(ROCK_SOURCE.relative_to(ROOT)),
        'sha256':hashlib.sha256(ROCK_SOURCE.read_bytes()).hexdigest()}}
(ROOT/'verification/axolotl-20261027/landscape-bake.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
# Perspective preview looking toward the central valley.
u=np.linspace(-.82,.82,1280); v=np.linspace(.75,-.12,640)
uu,vv=np.meshgrid(u,v); aa=np.arctan(uu); ee=vv/np.sqrt(1+uu*uu)
ix=((aa+np.pi)/(2*np.pi)*W).astype(int)%W
iy=np.clip(((1-(ee+.10)/1.25)*(H-1)).astype(int),0,H-1)
pic=image[iy,ix].copy(); bg=np.array([219,226,246])
pic[:,:,:3]=np.where((pic[:,:,3]>0)[:,:,None],pic[:,:,:3],bg);pic[:,:,3]=255
Image.fromarray(pic).save(ROOT/'verification/axolotl-20261027/landscape-bake-preview.png')
print(OUT,record,flush=True)
