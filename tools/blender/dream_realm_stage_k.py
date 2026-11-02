"""K stage-only correction: brighter lilac material and a broad low-relief summit.

blender -b --threads 6 --python tools/blender/dream_realm_study.py -- --output DIR
No reference pixels, photographs, external meshes or image textures are read.
The actual Cycles panorama is reprojected into the existing slope atlas contract.
"""
from pathlib import Path
import argparse, hashlib, json, math, random, shutil, struct, sys, time, zlib
import bpy
import numpy as np
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[2]
RNG=random.Random(27102829)
W,H=2048,768
SLOPE_MIN,SLOPE_MAX=-.35,1.15
PALETTE={
    'pearl':(.84,.80,.90),'light':(.96,.91,.97),'lavender':(.68,.60,.80),
    'shadow':(.58,.51,.71),'ledge':(.73,.79,.63),'grass':(.44,.61,.37),
    'leaf':(.82,.46,.63),'pink':(.94,.65,.76),'blossom_light':(.98,.79,.84),
    'trunk':(.34,.21,.29),'roof':(.27,.15,.42),'window':(.065,.09,.18),
    'gold':(.80,.68,.48),'water':(.62,.85,.98),'foam':(.93,.98,1.),
}
HAZE=(.83,.83,.94)
MATS={}
COUNTS={'islands':0,'floating_islands':0,'towers':0,'trees':0,'bridges':0,'waterfalls':0}


def material(kind,depth):
    key=(kind,depth)
    if key in MATS:return MATS[key]
    rgb=PALETTE[kind];fog=(.015,.12,.27)[depth]
    rgb=tuple(rgb[i]*(1-fog)+HAZE[i]*fog for i in range(3))
    # Palette values are authored sRGB; Blender shader inputs are scene-linear.
    # Treating these as linear was the source of the washed-out white/grey cliff pass.
    rgb=tuple(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in rgb)
    mat=bpy.data.materials.new(f'{kind} / depth {depth+1}');mat.diffuse_color=(*rgb,1);mat.use_nodes=True
    bsdf=mat.node_tree.nodes.get('Principled BSDF');bsdf.inputs['Base Color'].default_value=(*rgb,1)
    bsdf.inputs['Roughness'].default_value=.82
    # Actual editable, multiscale stone / blossom materials. No photograph input.
    nodes=mat.node_tree.nodes;links=mat.node_tree.links
    coord=nodes.new('ShaderNodeTexCoord')
    noise=nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=6.5
    noise.inputs['Detail'].default_value=3.2;noise.inputs['Roughness'].default_value=.74
    links.new(coord.outputs['Generated'],noise.inputs['Vector'])
    ramp=nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position=.22;ramp.color_ramp.elements[1].position=.78
    ramp.color_ramp.elements[0].color=(*(v*.74 for v in rgb),1)
    ramp.color_ramp.elements[1].color=(*(min(1,v*1.11+.04) for v in rgb),1)
    links.new(noise.outputs['Fac'],ramp.inputs[0]);links.new(ramp.outputs[0],bsdf.inputs['Base Color'])
    fine=nodes.new('ShaderNodeTexNoise');fine.inputs['Scale'].default_value=125;fine.inputs['Detail'].default_value=2.4
    links.new(coord.outputs['Generated'],fine.inputs['Vector'])
    bump=nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.23;bump.inputs['Distance'].default_value=.12
    links.new(fine.outputs['Fac'],bump.inputs['Height']);links.new(bump.outputs['Normal'],bsdf.inputs['Normal'])
    if kind in ('foam','water'):
        bsdf.inputs['Emission Color'].default_value=(*rgb,1);bsdf.inputs['Emission Strength'].default_value=.13
    MATS[key]=mat;return mat


class Mesh:
    def __init__(self,name,collection,depth):
        self.name=name;self.collection=collection;self.depth=depth;self.vertices=[];self.faces=[];self.indices=[];self.materials=[]
    def face(self,points,kind):
        if kind not in self.materials:self.materials.append(kind)
        start=len(self.vertices);self.vertices.extend(points);self.faces.append(tuple(range(start,start+len(points))));self.indices.append(self.materials.index(kind))
    def box(self,x,y,z,w,d,h,kind='pearl',angle=0):
        c,s=math.cos(angle),math.sin(angle)
        points=[(x+c*a-s*b,y+s*a+c*b,z+f) for a,b,f in
                [(0,0,0),(w,0,0),(w,d,0),(0,d,0),(0,0,h),(w,0,h),(w,d,h),(0,d,h)]]
        for face in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]:
            self.face([points[i] for i in face],kind)
    def finish(self):
        if not self.vertices:return None
        data=bpy.data.meshes.new(self.name);data.from_pydata(self.vertices,[],self.faces);data.update()
        for kind in self.materials:data.materials.append(material(kind,self.depth))
        for poly,index in zip(data.polygons,self.indices):poly.material_index=index
        obj=bpy.data.objects.new(self.name,data);self.collection.objects.link(obj)
        obj['original_procedural_geometry']=True;obj['depth_layer']=self.depth+1
        return obj


def tree(mesh,x,y,z,size=1):
    COUNTS['trees']+=1
    mesh.box(x-.32*size,y-.32*size,z,.64*size,.64*size,4.4*size,'trunk')
    for side in (-1,1):mesh.box(x+side*.9*size-.28*size,y-.25*size,z+2.3*size,.56*size,.5*size,2.3*size,'trunk')
    # Broad, layered, irregular block crowns; never a sphere or neon ring.
    lobes=[(-1.7,-.2,3.7,3.3,2.8,1.5),(.5,.2,4.2,3.1,3.0,1.6),(-.5,-1.2,5.0,3.7,2.7,1.6),
           (-.4,.6,5.4,2.5,2.5,1.5),(-1.1,-.4,6.0,2.6,2.3,.9)]
    for i,(ox,oy,oz,w,d,h) in enumerate(lobes):
        # Layered blossoms have broken edges and readable small light-catching cubes.
        mesh.box(x+(ox-w*.5)*size,y+(oy-d*.5)*size,z+oz*size,w*size,d*size,h*size,
                 ('leaf','pink','blossom_light','pink','blossom_light')[i])
        for j in range(8):
            sx=x+(ox+RNG.uniform(-w*.52,w*.52))*size;sy=y+(oy+RNG.uniform(-d*.52,d*.52))*size
            mesh.box(sx,sy,z+(oz+RNG.uniform(.2,h))*size,.65*size,.65*size,.6*size,
                     'blossom_light' if j%3 else 'pink')
    for _ in range(3):
        ox=RNG.uniform(-2,2)*size;oy=RNG.uniform(-1.8,1.8)*size
        mesh.box(x+ox,y+oy,z+RNG.uniform(3.2,5.5)*size,.7*size,.7*size,.6*size,'pink')


def tower(mesh,x,y,z,width,height,grand=False):
    COUNTS['towers']+=1
    height*=.40
    for rise,spread,h in [(0,.9,1.2),(1.2,.4,1.1),(2.3,0,height)]:
        mesh.box(x-width*.5-spread,y-width*.5-spread,z+rise,width+spread*2,width+spread*2,h,'light' if rise<2 else 'pearl')
    top=z+height+2.3
    for side in (-1,1):
        mesh.box(x-width*.5-.2,y+side*width*.5-.18,z+2.3,width+.4,.36,height,'light')
        mesh.box(x+side*width*.5-.18,y-width*.5-.2,z+2.3,.36,width+.4,height,'light')
    levels=max(2,int(height/7))
    for level in range(levels):
        window_z=z+4+level*(height-3)/levels
        for side in (-1,1):
            mesh.box(x-width*.14,y+side*(width*.5+.011)-.035,window_z,width*.28,.07,3.0,'window')
            mesh.box(x+side*(width*.5+.011)-.035,y-width*.14,window_z,.07,width*.28,3.0,'window')
            mesh.box(x-width*.18,y+side*(width*.5+.06)-.08,window_z-.35,width*.36,.16,.38,'light')
            mesh.box(x+side*(width*.5+.06)-.08,y-width*.18,window_z-.35,.16,width*.36,.38,'light')
    mesh.box(x-width*.5-.65,y-width*.5-.65,top,width+1.3,width+1.3,1.2,'light')
    for i in range(5):
        for side in (-1,1):
            mesh.box(x-width*.5-.4+i*width/4,y+side*(width*.5+.35)-.4,top+1.2,.8,.8,1.1,'light')
            mesh.box(x+side*(width*.5+.35)-.4,y-width*.5-.4+i*width/4,top+1.2,.8,.8,1.1,'light')
    roof_base=top+1.3;steps=9 if grand else 7
    for i in range(steps):
        r=width*.44*(1-i/steps)+.28
        mesh.box(x-r-.35,y-r-.35,roof_base+i*.85,(r+.35)*2,(r+.35)*2,.88,'roof')
    mesh.box(x-.20,y-.20,roof_base+steps*.85,.4,.4,2.8,'gold')


def castle(mesh,x,y,z,scale=1):
    # An irregular connected keep with four distinct turrets, no repeated cones.
    mesh.box(x-9*scale,y-6*scale,z,18*scale,12*scale,10*scale,'pearl')
    for sx,sy,w,h in [(-9,-5,5,22),(9,-3,5.5,29),(-6,6,4,36),(5,5,6.5,44)]:
        tower(mesh,x+sx*scale,y+sy*scale,z,w*scale,h*scale,grand=h>35)
    for i in range(5):
        mesh.box(x+(-7.5+i*3.6)*scale,y-6.03*scale,z+3.2*scale,1.8*scale,.10*scale,4.4*scale,'window')
        mesh.box(x+(-7.8+i*3.6)*scale,y-6.14*scale,z+2.6*scale,2.4*scale,.30*scale,.7*scale,'light')


def waterfall(mesh,x,y,top,bottom,width,angle=0):
    COUNTS['waterfalls']+=1
    c,s=math.cos(angle),math.sin(angle)
    def p(a,b,h):return (x+a*c-b*s,y+a*s+b*c,h)
    for strip in range(7):
        center=(strip/6-.5)*width
        for step in range(10):
            z0=top+(bottom-top)*step/10;z1=top+(bottom-top)*(step+1)/10
            phase=math.sin(step*1.4+strip*.8)*.26
            kind='foam' if strip in (0,6) or (step+strip)%7==0 else 'water'
            mesh.face([p(center-width/15,.02+phase,z0),p(center+width/15,.02+phase,z0),
                       p(center+width/15,.20-phase,z1),p(center-width/15,.20-phase,z1)],kind)
    mesh.box(x-width*.6,y-.7,top-.3,width*1.2,1.4,.35,'foam',angle)


def island(collection,depth,label,angle,distance,rx,ry,top,floating=False,keep=False,
           cell_override=None,bottom_override=None,tree_density=None,tree_scale=1,tier_height=2.5,strata_height=8,
           terrace_profile='island',flower_count=0):
    COUNTS['islands']+=1
    if floating:COUNTS['floating_islands']+=1
    x=math.sin(angle)*distance;y=math.cos(angle)*distance
    mesh=Mesh(label,collection,depth)
    bottom=bottom_override if bottom_override is not None else (top-(26+rx*.50))
    # An exposed voxel column grid gives genuinely broken terraced silhouettes.
    # Interior faces are removed, so the same mesh is suitable for runtime export.
    cell=cell_override if cell_override is not None else (5.0 if depth<2 else 6.4)
    grid={}
    nx,ny=math.ceil(rx/cell),math.ceil(ry/cell)
    for gx in range(-nx,nx+1):
        for gy in range(-ny,ny+1):
            radius=math.sqrt((gx*cell/rx)**2+(gy*cell/ry)**2)
            edge=1+.10*math.sin(gx*.7)+.13*math.sin(gy*.9+gx*.4)
            if radius>edge:continue
            height=top+4-math.floor(radius*3.8)*tier_height+math.floor(math.sin(gx*1.7+gy*.9)*1.4)
            if terrace_profile=='main':
                px,py=gx*cell,gy*cell
                if abs(px)<95 and -65<py<85:height=0
                elif py<-60:height=-65-math.floor(max(0,-py-60)/65)*45
                elif px<-110:height=-48-math.floor((-px-110)/75)*38
                elif px>125:height=-95-math.floor((px-125)/70)*30
                else:height=-45
                if px<-150 and py>40:height-=55
                height+=0 if abs(px)<95 and -65<py<85 else math.floor(math.sin(gx*.67+gy*.91)*2.1)*4
            elif terrace_profile=='garden':
                height=top+4-(0 if gx<1 and gy>0 else 25)-(25 if gx>1 else 0)-(18 if gy<-1 else 0)
            grid[gx,gy]=height
    for (gx,gy),height in grid.items():
        px=x+gx*cell;py=y+gy*cell;h=cell/2
        mesh.face([(px-h,py-h,height),(px+h,py-h,height),(px+h,py+h,height),(px-h,py+h,height)],'ledge')
        for dx,dy in [(1,0),(-1,0),(0,1),(0,-1)]:
            neighbor=grid.get((gx+dx,gy+dy),bottom)
            if neighbor>=height:continue
            if dx:points=[(px+dx*h,py-h,height),(px+dx*h,py+h,height),(px+dx*h,py+h,neighbor),(px+dx*h,py-h,neighbor)]
            else:points=[(px-h,py+dy*h,height),(px+h,py+dy*h,height),(px+h,py+dy*h,neighbor),(px-h,py+dy*h,neighbor)]
            if dx==1 or dy==-1:points.reverse()
            # Layered, subtly varied cliff stones; no broad unbroken low-poly wall.
            bands=max(1,int((height-neighbor)/strata_height))
            for band in range(bands):
                a=band/bands;b=(band+1)/bands
                z0=height+(neighbor-height)*a;z1=height+(neighbor-height)*b
                mesh.face([(points[0][0],points[0][1],z0),(points[1][0],points[1][1],z0),
                           (points[2][0],points[2][1],z1),(points[3][0],points[3][1],z1)],
                          ('shadow','shadow','lavender','shadow','shadow')[(math.floor(gx*.37)+math.floor(gy*.31)+band)%5])
    # The topography is deterministic, allowing trees/architecture to sit correctly.
    def surface(ox,oy):
        return grid.get((round(ox/cell),round(oy/cell)),top-4)
    # Thin grassy seams on upper terraces, with occasional hanging vines.
    for i in range(7):
        sx=x+RNG.uniform(-.72,.65)*rx;sy=y+RNG.uniform(-.65,.65)*ry
        mesh.box(sx,sy,surface(sx-x,sy-y)+.025,RNG.uniform(3,8),RNG.uniform(3,7),.24,'grass')
    for i in range(12 if depth<2 else 7):
        az=i*math.pi*2/(12 if depth<2 else 7)
        sx=x+math.cos(az)*rx*.82;sy=y+math.sin(az)*ry*.82
        h=(top-bottom)*RNG.uniform(.22,.58)
        roof=surface(sx-x,sy-y)
        mesh.box(sx,sy,roof-h,RNG.uniform(1,2.3),RNG.uniform(1,2.3),h,'lavender' if i%3 else 'shadow')
        if i%3==0:
            vine_h=RNG.uniform(4,9);mesh.box(sx-.7,sy-.7,roof-vine_h,1.4,1.4,vine_h,'grass')
    trees=tree_density if tree_density is not None else (6 if floating else (11 if depth<2 else 7))
    for i in range(trees):
        a=i*2.399963;radius=math.sqrt((i+.6)/trees)
        ox=math.cos(a)*rx*radius*.73;oy=math.sin(a)*ry*radius*.73
        tree(mesh,x+ox,y+oy,surface(ox,oy)+.1,RNG.uniform(2.8,4.8)*tree_scale)
    for i in range(flower_count):
        a=i*2.399963+.6;radius=.55+.3*(i%3)/2
        ox=math.cos(a)*rx*radius;oy=math.sin(a)*ry*radius;roof=surface(ox,oy)
        sx=x+ox;sy=y+oy;stem=4.0
        mesh.box(sx-.7,sy-.7,roof,1.4,1.4,stem,'grass')
        for dx,dy in [(-2.7,0),(2.7,0),(0,-2.7),(0,2.7)]:
            mesh.box(sx+dx-1.6,sy+dy-1.6,roof+stem,3.2,3.2,1.8,'pink')
        mesh.box(sx-1.7,sy-1.7,roof+stem+1.1,3.4,3.4,1.1,'blossom_light')
    if keep:castle(mesh,x-rx*.10,y+ry*.12,top+4,2.35 if depth==1 else (2.15 if depth==2 else 3.5))
    elif floating:tower(mesh,x,y,top+.3,5.0,14,False)
    if not floating or angle>0:
        waterfall(mesh,x-rx*.26,y-ry*.95,surface(-rx*.26,-ry*.95)+.1,bottom+3,min(65,rx*.55))
    mesh.finish()
    return x,y,top


def bridge(collection,depth,label,a,b,z,arches=4):
    COUNTS['bridges']+=1;mesh=Mesh(label,collection,depth)
    dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy);angle=math.atan2(dy,dx);c,s=math.cos(angle),math.sin(angle)
    def box(u,v,h,w,d,t,kind='pearl'):
        mesh.box(a[0]+u*c-v*s,a[1]+u*s+v*c,h,w,d,t,kind,angle)
    box(0,-3,z,length,6,1.8,'light')
    for side in (-1,1):
        box(0,side*3-.4,z+1.8,length,.8,1.1,'light')
        for i in range(int(length/4)):
            box(i*4,side*3-.65,z+2.9,1.6,1.3,1.3,'light')
    span=length/arches
    for arch in range(arches):
        start=arch*span
        box(start-.9,-2.8,z-18,1.8,5.6,18,'pearl')
        for step in range(12):
            u=(step+.5)/12;under=z-18+13*math.sqrt(max(0,1-(u*2-1)**2))
            box(start+step*span/12,-2.7,under,span/12+.05,5.4,z-under,'light' if step%4==0 else 'pearl')
    box(length-.9,-2.8,z-18,1.8,5.6,18,'pearl');mesh.finish()


def png(path,pixels):
    def chunk(kind,data):return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
    height,width,_=pixels.shape
    raw=b''.join(b'\x00'+row.tobytes() for row in pixels)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',width,height,8,6,0,0,0))+
                     chunk(b'sRGB',b'\x00')+chunk(b'IDAT',zlib.compress(raw,7))+chunk(b'IEND',b''))


def bake_slope(raw_path,target,preview_path):
    image=bpy.data.images.load(str(raw_path),check_existing=False)
    rw,rh=image.size;source=np.array(image.pixels[:],dtype=np.float32).reshape(rh,rw,4)[::-1].copy()
    top=math.atan(SLOPE_MAX);bottom=math.atan(SLOPE_MIN)
    slope=np.linspace(SLOPE_MAX,SLOPE_MIN,H,dtype=np.float32)
    row=(top-np.arctan(slope))/(top-bottom)*(rh-1)
    lo=np.floor(row).astype(int);hi=np.minimum(lo+1,rh-1);f=(row-lo)[:,None,None]
    # Resample straight PNG through premultiplied colour to avoid dark alpha edges.
    source[:,:,:3]*=source[:,:,3:4]
    out=source[lo]*(1-f)+source[hi]*f
    out[:,:,:3]=np.divide(out[:,:,:3],out[:,:,3:4],out=np.zeros_like(out[:,:,:3]),where=out[:,:,3:4]>.0001)
    out[:,:,:3]=np.where(out[:,:,:3]<=.0031308,out[:,:,:3]*12.92,1.055*np.maximum(0,out[:,:,:3])**(1/2.4)-.055)
    pixels=np.clip(out*255+.5,0,255).astype(np.uint8);png(target,pixels)
    # A conventional front frustum extracted from the same real 3-D bake.
    xx,yy=np.meshgrid(np.linspace(-.95,.95,1280),np.linspace(.82,-.32,720))
    az=np.arctan(xx);s=yy/np.sqrt(1+xx*xx)
    ix=((az/(2*np.pi)+.5)*W).astype(int)%W
    iy=np.clip((1-(s-SLOPE_MIN)/(SLOPE_MAX-SLOPE_MIN))*(H-1),0,H-1).astype(int)
    front=pixels[iy,ix].copy();alpha=front[:,:,3:4].astype(float)/255
    sky=np.array([219,226,246]);front[:,:,:3]=(front[:,:,:3]*alpha+sky*(1-alpha)).astype(np.uint8);front[:,:,3]=255
    png(preview_path,front)
    return {'nonzero_alpha_fraction':float(np.mean(pixels[:,:,3]>0)),
            'opaque_alpha_fraction':float(np.mean(pixels[:,:,3]>250))}


def runtime_material_atlas(path):
    """Original stone / blossom atlas; generated noise, no external bitmap inputs."""
    kinds=list(PALETTE);size=512;tile=128
    pixels=np.zeros((size,size,4),dtype=np.uint8);pixels[:,:,3]=255
    rng=np.random.default_rng(27102830)
    yy,xx=np.mgrid[0:tile,0:tile]
    for index,kind in enumerate(kinds):
        rgb=np.array(PALETTE[kind])*255
        coarse=(np.sin(xx*.109+np.sin(yy*.043)*2.7)+np.sin(yy*.173+xx*.036))*4.5
        fine=rng.normal(0,1.8,(tile,tile));field=coarse+fine
        if kind in ('pearl','light','lavender','shadow','ledge'):
            seams=((yy+np.floor(xx/32)*3)%32<1)|((xx+np.floor(yy/32)*11)%43<1);field-=seams*11
        elif kind=='grass':field+=np.sin(xx*.4+yy*.31)*4
        elif kind in ('pink','leaf','blossom_light'):field+=np.sin(xx*.36)*np.cos(yy*.27)*6
        elif kind in ('water','foam'):field+=np.sin(xx*.32+np.sin(yy*.1))*6
        patch=np.clip(rgb[None,None,:]+field[:,:,None],0,255).astype(np.uint8)
        oy,ox=(index//4)*tile,(index%4)*tile;pixels[oy:oy+tile,ox:ox+tile,:3]=patch
    png(path,pixels);return kinds


def export_runtime_stage(scene,output):
    """Same editable cliff/tree/castle/arcade geometry, in final game block coordinates."""
    atlas_path=output/'cangsheng_dream_materials.png';kinds=runtime_material_atlas(atlas_path)
    record={'format':'cangsheng_dream_stage_v1','scale':1,'anchor':[0,0,0],
            'coordinate_system':'final game blocks, x right / y up / z away from viewer; center summit y=0',
            'rgba_range':'0..1','uv_origin':'top-left','texture':'textures/effect/cangsheng_dream_materials.png',
            'texture_sha256':hashlib.sha256(atlas_path.read_bytes()).hexdigest(),'texture_size':[512,512],
            'source':'Original editable Blender mesh + original procedural material atlas; no third-party assets',
            'recommended_runtime_anchor_y':16,'authoring_vertical_compression':.42,
            'lods':{}}
    for lod in ('HIGH','LOW'):
        col=bpy.data.collections.new(f'RuntimeStage {lod} / exact exported game mesh');scene.collection.children.link(col)
        island(col,0,'Runtime central broad pearl summit '+lod,0,0,270,205,-4,
               cell_override=30 if lod=='HIGH' else 82,bottom_override=-350,
               tree_density=2 if lod=='HIGH' else 0,tree_scale=3.0,tier_height=48,strata_height=55,terrace_profile='main',
               flower_count=10 if lod=='HIGH' else 0)
        settings=[(-.90,490,145,120,95,lod=='HIGH'),(.90,530,155,125,135,lod=='HIGH'),
                  (-1.8,600,125,115,35,False),(1.9,620,130,110,65,False)]
        if lod=='LOW':settings=settings[:2]
        points=[]
        for i,(az,r,rx,ry,top,keep) in enumerate(settings):
            points.append(island(col,0,f'Runtime side garden {i} '+lod,az,r,rx,ry,top,keep=keep,
                cell_override=26 if lod=='HIGH' else 70,bottom_override=top-150,
                tree_density=2 if lod=='HIGH' else 1,tree_scale=2.6,tier_height=17,strata_height=50,terrace_profile='garden',
                flower_count=2 if lod=='HIGH' else 0))
        for i,(az,r,top) in enumerate([(-.62,740,270),(.55,820,315)]):
            island(col,1,f'Runtime suspended sanctuary {i} '+lod,az,r,70,62,top,True,
                   cell_override=20 if lod=='HIGH' else 70,tree_density=1,tree_scale=1.5,tier_height=8,strata_height=40)
        bridge(col,0,'Runtime left flowering arcade '+lod,(-200,70),points[0],36,4 if lod=='HIGH' else 3)
        bridge(col,0,'Runtime right flowering arcade '+lod,(205,65),points[1],45,4 if lod=='HIGH' else 3)
        quads=[]
        for obj in col.objects:
            obj['runtime_lod']=lod
            for vertex in obj.data.vertices:vertex.co.z*=.42
            obj.data.update()
            for face in obj.data.polygons:
                if len(face.vertices)!=4:raise RuntimeError(f'Non-quad stage face {obj.name}')
                points=[Vector(obj.data.vertices[index].co) for index in face.vertices]
                positions=[[round(p.x*.1,6),round(p.z*.1,6),round(p.y*.1,6)] for p in reversed(points)]
                a,b,c=[Vector(p) for p in positions[:3]];normal=(b-a).cross(c-a).normalized()
                kind=obj.data.materials[face.material_index].name.split(' / depth ')[0]
                index=kinds.index(kind);u0=(index%4)*.25+1.5/512;v0=(index//4)*.25+1.5/512
                u1=u0+.25-3/512;v1=v0+.25-3/512
                quads.append({'p':positions,'n':[round(n,6) for n in normal],
                              'uv':[[u0,v0],[u1,v0],[u1,v1],[u0,v1]],'rgba':[1,1,1,1]})
        count=len(quads)*4;limit=60000 if lod=='HIGH' else 25000
        if count>=limit:raise RuntimeError(f'{lod} exported {count} vertices exceeds {limit}')
        record['lods'][lod]={'vertex_count':count,'quad_count':len(quads),'quads':quads}
        col.hide_render=True;col.hide_viewport=True
    (output/'cangsheng_dream_stage.json').write_text(json.dumps(record,separators=(',',':'))+'\n',encoding='utf-8')
    return {lod:{k:v for k,v in entry.items() if k!='quads'} for lod,entry in record['lods'].items()}


def main():
    args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--install',action='store_true');parser.add_argument('--skip-render',action='store_true')
    options=parser.parse_args(args);output=options.output.resolve();output.mkdir(parents=True,exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True);scene=bpy.context.scene
    scene.render.engine='CYCLES';scene.cycles.samples=16;scene.cycles.use_denoising=True
    scene.render.threads_mode='FIXED';scene.render.threads=6;scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA'
    scene.render.film_transparent=True;scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100
    scene.view_settings.view_transform='Standard';scene.view_settings.look='Medium High Contrast';scene.view_settings.exposure=-.75
    world=bpy.data.worlds.new('Pearl daylight / procedural, no external environment');world.use_nodes=True
    world.node_tree.nodes['Background'].inputs[0].default_value=(.75,.79,.91,1);world.node_tree.nodes['Background'].inputs[1].default_value=.50;scene.world=world
    sun_data=bpy.data.lights.new('Soft morning sun','SUN');sun_data.energy=1.2;sun_data.angle=.18;sun_data.color=(1.,.94,.89)
    sun=bpy.data.objects.new('Soft morning sun',sun_data);scene.collection.objects.link(sun);sun.rotation_euler=(.42,-.38,-.7)
    layers=[]
    for i,name in enumerate(('01 near garden escarpments','02 middle castle archipelagos','03 distant mist islands')):
        col=bpy.data.collections.new(name);scene.collection.children.link(col);col['depth_layer']=i+1;layers.append(col)
    near=[(-1.05,170,36,31,13),(1.12,180,42,34,18),(-1.72,180,40,35,17),(1.83,185,40,33,20),
          (-2.52,180,40,33,17),(2.61,195,42,35,20),(-3.08,200,42,32,18)]
    middle=[(-.60,255,58,45,55),(.66,275,63,47,60),(-1.26,280,61,44,68),(1.37,295,57,44,63),
            (-2.0,310,63,47,74),(2.15,305,62,51,66),(-2.75,320,66,45,68),(2.92,330,58,48,71)]
    far=[(-.30,405,70,50,155),(.34,425,73,52,161),(-.89,470,72,53,177),(.97,490,77,55,190),
         (-1.60,510,81,59,199),(1.67,530,77,57,187),(-2.35,550,82,60,208),(2.44,560,82,59,209),(3.0,590,86,63,220)]
    for i,values in enumerate(near):island(layers[0],0,f'Near garden cliff {i+1:02d}',*values)
    points=[]
    for i,values in enumerate(middle):points.append(island(layers[1],1,f'Castle island {i+1:02d}',*values,keep=True))
    for i,values in enumerate(far):island(layers[2],2,f'Far pearl terraces {i+1:02d}',*values,keep=True)
    for i,(az,r,rx,ry,z) in enumerate([(.30,340,17,15,205),(-.80,370,21,16,219),(1.62,420,20,17,228),(-2.13,450,21,18,230),(2.7,470,22,18,250)]):
        island(layers[2],2,f'Floating blossom sanctuary {i+1:02d}',az,r,rx,ry,z,True)
    bridge(layers[2],2,'Distant valley arcade / keeps center clear',(-138,360),(138,372),123,7)
    bridge(layers[1],1,'Left castle cliff arcade',points[0],points[2],76,5)
    bridge(layers[1],1,'Right terrace arcade',points[1],points[3],69,5)
    bridge(layers[1],1,'Rear island arcade',points[5],points[7],77,5)
    camera_data=bpy.data.cameras.new('Dream panorama / exact slope reprojection');camera=bpy.data.objects.new('Dream panorama',camera_data);scene.collection.objects.link(camera)
    camera.location=(0,0,45);camera.rotation_euler=(math.pi/2,0,0);camera_data.type='PANO'
    pano=camera_data if hasattr(camera_data,'panorama_type') else camera_data.cycles
    pano.panorama_type='EQUIRECTANGULAR';pano.latitude_min=math.atan(SLOPE_MIN);pano.latitude_max=math.atan(SLOPE_MAX)
    scene.camera=camera
    preview_data=bpy.data.cameras.new('Composition preview');preview=bpy.data.objects.new('Composition preview',preview_data);scene.collection.objects.link(preview)
    preview.location=(0,0,45);target=Vector((0,260,73));preview.rotation_euler=(target-preview.location).to_track_quat('-Z','Y').to_euler();preview_data.lens=17
    scene['original_geometry_and_materials']=True;scene['source_images_read']='None: reference composition only'
    scene['background_contract']='RGBA2048x768; u=az/(2pi)+.5; top slope1.15, bottom-.35'
    scene['layer_notes']='Central near field remains clear for the actual animated monster, main terrace and bridges rendered in game.'
    blend=output/'dream_realm.blend';bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    raw=output/'dream-realm-equirectangular.png';target_png=output/'cangsheng_landscape.png';preview_png=output/'dream-realm-front-preview.png'
    started=time.time()
    if not options.skip_render:
        scene.render.filepath=str(raw);print('DREAM REALM actual 3D panorama render starting',flush=True);bpy.ops.render.render(write_still=True)
        coverage=bake_slope(raw,target_png,preview_png)
    else:
        coverage={}
        frozen=ROOT/'verification/model3d-20261027/dream-realm-i'
        shutil.copy2(frozen/'cangsheng_landscape.png',target_png)
        shutil.copy2(frozen/'dream-realm-front-preview.png',preview_png)
    runtime=export_runtime_stage(scene,output)
    scene.camera=preview;scene.render.film_transparent=False;scene.render.resolution_x=1280;scene.render.resolution_y=768
    bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    record={'origin':'Original editable K runtime stage and procedural atlas; actual Cycles background preserved byte-for-byte from reviewed I bake',
            'generator':'tools/blender/dream_realm_stage_k.py','seed':27102829,'size':[W,H],
            'samples':16,'cpu_threads':6,'blender':bpy.app.version_string,'counts':COUNTS,
            'depth_layers':3,'source_images_read':[],'external_textures_or_meshes':[],
            'slope_contract':{'top':SLOPE_MAX,'bottom':SLOPE_MIN,'mapping':'row=(1-(slope+.35)/1.50)*(height-1)'},
            'blend':str(blend),'elapsed_render_seconds':time.time()-started,'coverage':coverage,'runtime_geometry':runtime,
            'review_passed':False,'installed':False,
            'background_origin':{'generator':'tools/blender/dream_realm_study.py',
                                 'generator_sha256':hashlib.sha256((ROOT/'tools/blender/dream_realm_study.py').read_bytes()).hexdigest(),
                                 'blend':str(ROOT/'verification/model3d-20261027/dream-realm-i/dream_realm.blend'),
                                 'bake_manifest':'verification/model3d-20261027/dream-realm-i/dream-realm-bake.json'}}
    if target_png.exists():record['sha256']=hashlib.sha256(target_png.read_bytes()).hexdigest()
    record['generator_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    if options.install:
        destination=ROOT/'src/main/resources/assets/two_i_combat_doll/textures/effect/cangsheng_landscape.png'
        previous=output/'previous-landscape';previous.mkdir(exist_ok=True)
        if destination.exists():
            oldhash=hashlib.sha256(destination.read_bytes()).hexdigest();shutil.copy2(destination,previous/f'cangsheng_landscape-{oldhash[:12]}.png');record['previous_sha256']=oldhash
        oldmanifest=ROOT/'verification/axolotl-20261027/landscape-bake.json'
        if oldmanifest.exists():shutil.copy2(oldmanifest,previous/'landscape-bake.json')
        shutil.copy2(target_png,destination);record['installed']=True
    (output/'dream-realm-bake.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(record,indent=2),flush=True)


if __name__=='__main__':main()
