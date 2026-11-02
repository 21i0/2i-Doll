"""Original offline crimson alpine gorge; no user image/video pixels are read.

This is an authored composition, not a photograph: asymmetric near walls,
interlocking middle shoulders and a low distant valley. Height, cast shadow,
rock strata and distance colour are baked once into a 2048x768 RGBA panorama.
The surface grain adapts a Poly Haven CC0 weathered-rock texture; source hash,
credit and the separately original authored terrain are recorded in the manifest.
The runtime only samples this image; expensive ray work never runs in Minecraft.
"""
from pathlib import Path
import hashlib,json,time
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'src/main/resources/assets/two_i_combat_doll/textures/effect/crimson_landscape.png'
EVIDENCE=ROOT/'verification/axolotl-20261027'
SEED=27102792
RNG=np.random.default_rng(SEED)
ROCK=EVIDENCE/'rock_boulder_cracked_diff_1k.jpg'
rock_image=np.asarray(Image.open(ROCK).convert('RGB'),dtype=np.float32)/255
rock_gray=rock_image[:,:,0]*.2126+rock_image[:,:,1]*.7152+rock_image[:,:,2]*.0722
N=1536;SPACING=1.;HALF=N/2
axis=np.arange(N,dtype=np.float32)-HALF
x,z=np.meshgrid(axis,axis)
started=time.time()

def field(size):
    a=RNG.random((size,size),dtype=np.float32)
    return np.asarray(Image.fromarray(a).resize((N,N),Image.Resampling.BICUBIC)).copy()

continental=field(12);large=field(39);medium=field(153);fine=field(577)
# Coherent fractures are narrow and directional. A small domain warp prevents
# mathematically repeated vertical lines while preserving large rock planes.
warp=(large-.5)*13+(medium-.5)*3
height=np.full((N,N),-8,np.float32)

def massif(cx,cz,rx,rz,high,angle,seed):
    dx=x-cx+warp;dz=z-cz+(continental-.5)*16
    co=np.cos(angle);si=np.sin(angle)
    u=(dx*co-dz*si)/rx;v=(dx*si+dz*co)/rz
    q=(np.abs(u)**2.5+np.abs(v)**2.5)**.4
    # A broad buttressed peak has a shoulder and a jagged upper roof. The
    # exponent preserves cliff faces instead of gaussian, soft round hills.
    a=np.arctan2(v,u)
    q*=1+.08*np.sin(a*5+seed)+.045*np.sin(a*11-seed)
    broad=np.clip(1-q,0,1)**.54
    rib=.5+.5*np.sin(a*13+medium*2.8+seed)
    channels=np.maximum(0,.52-rib)**1.3*(1-broad)*14
    profile=broad*high*(.85+.20*large+.08*medium)-channels
    shoulder=np.exp(-((u-.37)**2/.54+(v+.2)**2/.95))*high*.52*np.clip((1.24-q)*3,0,1)
    return np.maximum(profile,shoulder)-9

# Facing -Z: near left and right walls dominate different heights and distances.
# The valley axis bends left in the distance, producing an authored focal path.
composed=[
    (-155,-183,91,105,163,-.20,.6),(182,-235,103,118,192,.22,1.4),
    (-241,-324,109,130,208,.18,2.3),(264,-409,122,146,237,-.28,3.6),
    (-332,-470,139,158,261,-.13,5.2),(349,-560,145,162,256,.29,6.6),
    (-142,-349,61,82,119,.32,7.8),(162,-412,72,95,143,-.32,8.4),
    (-123,-501,83,89,104,.20,9.8),(110,-551,101,113,128,-.24,10.2),
    (-60,-668,89,80,92,.04,11.7),(59,-685,92,91,112,-.08,12.8),
]
for row in composed:height=np.maximum(height,massif(*row))
# The remaining panorama remains a coherent range when players turn away. It
# does not repeat the exact front silhouette or put a giant peak in the valley.
for i in range(23):
    angle=RNG.uniform(-np.pi,np.pi)
    if abs(angle)<.92:continue
    radius=RNG.uniform(235,640)
    height=np.maximum(height,massif(np.sin(angle)*radius,-np.cos(angle)*radius,
        RNG.uniform(65,130),RNG.uniform(65,145),RNG.uniform(110,210),RNG.uniform(-.6,.6),14+i*.79))

# Directional rock erosion: broad diagonal strata, channels, then small broken
# ledges. These affect surface normals and cast shadows, not just flat colour.
erosion=(medium-.5)*13+(fine-.5)*4.7
terrace=np.sin(height*.17+x*.013+z*.009+large*2)
height=np.where(height>1,height+erosion+np.maximum(0,terrace)*2.3,height).astype(np.float32)
gz,gx=np.gradient(height,SPACING)

def sample(a,xx,zz):
    fx=np.clip(xx+HALF,0,N-2);fz=np.clip(zz+HALF,0,N-2)
    ix=fx.astype(np.int32);iz=fz.astype(np.int32);u=fx-ix;v=fz-iz
    return (a[iz,ix]*(1-u)+a[iz,ix+1]*u)*(1-v)+(a[iz+1,ix]*(1-u)+a[iz+1,ix+1]*u)*v

def rock_sample(u,v):
    size=rock_gray.shape[0];fx=np.mod(u,1)*size;fy=np.mod(v,1)*size
    ix=fx.astype(np.int32)%size;iy=fy.astype(np.int32)%size
    nx=(ix+1)%size;ny=(iy+1)%size;a=fx-np.floor(fx);b=fy-np.floor(fy)
    return (rock_gray[iy,ix]*(1-a)+rock_gray[iy,nx]*a)*(1-b)+(rock_gray[ny,ix]*(1-a)+rock_gray[ny,nx]*a)*b

W,H=2048,768
image=np.zeros((H,W,4),np.uint8)
az=np.linspace(-np.pi,np.pi,W,endpoint=False,dtype=np.float32)
sx=np.sin(az);sz=-np.cos(az)
for row in range(0,H,48):
    rows=np.arange(row,min(H,row+48),dtype=np.float32)
    # Matches the Cangsheng atlas convention: top slope1.15, bottom-.10.
    slopes=(1-rows[:,None]/(H-1))*1.25-.10
    hit=np.zeros((len(rows),W),bool);distance=np.zeros(hit.shape,np.float32)
    low=np.zeros_like(distance);previous=19.
    for d in np.geomspace(20,742,430):
        terrain=sample(height,sx[None,:]*d,sz[None,:]*d)
        now=(4+slopes*d<=terrain)&~hit
        distance[now]=d;low[now]=previous;hit|=now;previous=d
    for _ in range(7):
        d=(low+distance)*.5
        below=4+slopes*d<=sample(height,sx[None,:]*d,sz[None,:]*d)
        distance=np.where(below,d,distance);low=np.where(below,low,d)
    px=sx[None,:]*distance;pz=sz[None,:]*distance
    py=sample(height,px,pz)
    nx=-sample(gx,px,pz);nz=-sample(gz,px,pz)
    inv=1/np.sqrt(nx*nx+nz*nz+1)
    direct=np.clip((nx*-.62+.65+nz*.43)*inv,0,1)
    shadow=np.ones_like(py)
    for step in (3,8,18,37,78,135):
        occluded=sample(height,px-step*.62,pz+step*.43)>py+step*.65+1.2
        shadow*=np.where(occluded,.81,1)
    light=(.19+.81*direct)*shadow
    small=sample(fine,px,pz);patch=sample(medium,px,pz);macro=sample(large,px,pz)
    # Charcoal/burgundy shaded planes vs warm silver lit planes. This gives rock
    # its own value structure under the red covenant, rather than red neon snow.
    dark=np.array([.076,.041,.058]);warm=np.array([.58,.48,.46])
    rgb=dark[None,None,:]+(warm-dark)[None,None,:]*light[:,:,None]
    strata=.5+.5*np.sin(py*.46+px*.036+pz*.014+patch*1.7)
    seams=np.maximum(0,.32-np.abs(np.sin(px*.155+pz*.053+macro*1.8)))
    # Triplanar CC0 natural crack grain: albedo only, baked offline. Mixing the
    # orthogonal planes avoids stretching a vertical stripe across every cliff.
    blend=np.abs(nx)/(np.abs(nx)+np.abs(nz)+.001)
    vertical_x=rock_sample(pz*.012+patch*.013,py*.012)
    vertical_z=rock_sample(px*.012+patch*.013,py*.012+.23)
    texture=vertical_x*blend+vertical_z*(1-blend)
    broad_texture=rock_sample(px*.010+pz*.006,py*.010+.47)
    weather=np.clip((texture-.24)*2.25,0,1.1)
    rockdetail=.25+weather*.75+.13*broad_texture+.07*small
    rgb*=rockdetail[:,:,None]
    slope=np.sqrt(nx*nx+nz*nz)
    cap=np.clip((py-103+macro*37)/70,0,1)*np.clip(1.6-slope*.22,0,1)
    cap*=np.clip((direct-.19)*1.6,0,1)
    rgb=rgb*(1-cap[:,:,None]*.65)+np.array([.64,.56,.54])*cap[:,:,None]*.65
    # Sparse garnet mineral seams stay subordinate to the massive lit faces.
    mineral=np.clip((patch-.66)*8,0,1)*np.clip(1.5-slope*.16,0,1)*.055
    rgb+=mineral[:,:,None]*np.array([.35,.018,.035])
    # Low fog lifts only distant ranges; the front cliff stays dark and crisp.
    fog=np.clip((distance-105)/850,0,.62)
    fog+=np.exp(-((py-11)/17)**2)*np.clip((distance-160)/600,0,.22)
    rgb=rgb*(1-fog[:,:,None])+np.array([.19,.105,.155])*fog[:,:,None]
    image[row:row+len(rows),:,:3]=np.clip(rgb*255,0,255).astype(np.uint8)
    image[row:row+len(rows),:,3]=hit.astype(np.uint8)*255
    print('baked rows',row+len(rows),'/',H,flush=True)

OUT.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(image).save(OUT,optimize=True)
EVIDENCE.mkdir(parents=True,exist_ok=True)
record={'origin':'Original deterministic authored heightfield; CC0 offline grayscale surface grain; no user reference image/video pixels',
    'design':'Asymmetric huge side walls, interlocking mid shoulders, low deep valley and small distant ranges; charcoal/burgundy rock and warm silver lit upper planes',
    'size':[W,H],'mode':'RGBA','seed':SEED,'terrain_grid':[N,N],
    'vertical_mapping':'slope = (1-y/(H-1))*1.25 - 0.10; front azimuth=0 at u=0.5',
    'generator':'tools/create-crimson-landscape.py','generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    'asset':str(OUT.relative_to(ROOT)),'asset_sha256':hashlib.sha256(OUT.read_bytes()).hexdigest(),
    'elapsed_seconds':round(time.time()-started,2),'source_images_read':[{
        'path':str(ROCK.relative_to(ROOT)),'sha256':hashlib.sha256(ROCK.read_bytes()).hexdigest(),
        'asset':'rock_boulder_cracked','author':'Dario Barresi / Dimitrios Savva','provider':'Poly Haven',
        'url':'https://polyhaven.com/a/rock_boulder_cracked','license':'CC0-1.0',
        'use':'Offline grayscale triplanar surface grain; not distributed as a separate source texture',
        'terrain_geometry':'Original heightfield and authored valley composition; not extracted from source photograph'}]}
(EVIDENCE/'crimson-landscape-bake.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
# Rectilinear proof renders, not game screenshots. Both original files retained.
for name,fov in [('wide',2.10),('front',1.35)]:
    u=np.linspace(-fov,fov,1536);v=np.linspace(.92,-.12,768)
    uu,vv=np.meshgrid(u,v);aa=np.arctan(uu);ee=vv/np.sqrt(1+uu*uu)
    ix=((aa+np.pi)/(2*np.pi)*W).astype(int)%W
    iy=np.clip(((1-(ee+.10)/1.25)*(H-1)).astype(int),0,H-1)
    pic=image[iy,ix].copy();background=np.array([18,4,11])
    pic[:,:,:3]=np.where((pic[:,:,3]>0)[:,:,None],pic[:,:,:3],background);pic[:,:,3]=255
    Image.fromarray(pic).save(EVIDENCE/f'crimson-landscape-{name}-preview.png')
print(json.dumps(record,ensure_ascii=False,indent=2),flush=True)
