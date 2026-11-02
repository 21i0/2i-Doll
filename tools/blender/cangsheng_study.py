"""Editable Cangsheng study from the exact shared Minecraft rig and baked ridge.

blender -b --python tools/blender/cangsheng_study.py -- --output DIR --ridge DIR

--ridge is the real client's screenshots directory containing cangsheng-ridge-
high.json. The script refuses to invent an attractive replacement mountain.
The .blend keeps all cuboids, UVs, joints and a skeleton guide separately
editable. Studio lighting is intentionally separate from the in-game shader.
"""
from pathlib import Path
import argparse, hashlib, json, math, sys
import bpy
from mathutils import Euler, Matrix, Vector

REPO=Path(__file__).resolve().parents[2]
ASSETS=REPO/'src/main/resources/assets/two_i_combat_doll'
T=Matrix(((1,0,0),(0,0,1),(0,-1,0)))

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def vec(p):return T@Vector(p)

def material(name,path,tinted=False):
    mat=bpy.data.materials.new(name);mat.use_nodes=True
    nodes=mat.node_tree.nodes;nodes.clear();links=mat.node_tree.links
    out=nodes.new('ShaderNodeOutputMaterial');bsdf=nodes.new('ShaderNodeBsdfPrincipled')
    tex=nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(path),check_existing=True)
    tex.image.pack();tex.interpolation='Closest';tex.extension='REPEAT'
    colour=tex.outputs['Color']
    if tinted:
        tint=nodes.new('ShaderNodeVertexColor');tint.layer_name='MinecraftColor'
        mix=nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1
        links.new(colour,mix.inputs[1]);links.new(tint.outputs['Color'],mix.inputs[2]);colour=mix.outputs[0]
    links.new(colour,bsdf.inputs['Base Color']);bsdf.inputs['Roughness'].default_value=.9
    links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
    links.new(bsdf.outputs[0],out.inputs[0]);mat.use_backface_culling=False
    return mat

def cube_mesh(name,cube,parent,mat,collection):
    x,y,z,w,h,d=cube['box'];u,v=cube['uv']
    raw=[(x,y,z),(x+w,y,z),(x+w,y+h,z),(x,y+h,z),
         (x,y,z+d),(x+w,y,z+d),(x+w,y+h,z+d),(x,y+h,z+d)]
    # The same six vertex sequences and UV rectangles as ModelPart.Cube.
    faces=[(5,4,0,1),(2,3,7,6),(0,4,7,3),(1,0,3,2),(5,1,2,6),(4,5,6,7)]
    rects=[(u+d,v,u+d+w,v+d),(u+d+w,v+d,u+d+2*w,v),
           (u,v+d,u+d,v+d+h),(u+d,v+d,u+d+w,v+d+h),
           (u+d+w,v+d,u+2*d+w,v+d+h),(u+2*d+w,v+d,u+2*d+2*w,v+d+h)]
    mesh=bpy.data.meshes.new(name);mesh.from_pydata([vec(p) for p in raw],[],faces);mesh.update()
    uv=mesh.uv_layers.new(name='MinecraftUV')
    for poly,rect in zip(mesh.polygons,rects):
        a,b,c,e=rect
        coords=[(c/128,1-b/128),(a/128,1-b/128),(a/128,1-e/128),(c/128,1-e/128)]
        for loop,co in zip(poly.loop_indices,coords):uv.data[loop].uv=co
    mesh.materials.append(mat);obj=bpy.data.objects.new(name,mesh);collection.objects.link(obj)
    obj.parent=parent;obj['source_box']=json.dumps(cube);return obj

def ridge_mesh(rows,name,mat,collection,turntable,lift,source):
    verts=[]
    for p in rows:verts.append((p['x']*1.03,p['z']*.84,p['y']*.84+lift))
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],[tuple(range(i,i+4)) for i in range(0,len(verts),4)]);mesh.update()
    mesh.uv_layers.new(name='MinecraftUV');mesh.color_attributes.new(name='MinecraftColor',type='FLOAT_COLOR',domain='CORNER')
    # Adding a CustomData layer can invalidate earlier RNA handles in Blender
    # 4.5. Reacquire both after allocation before writing UV or RGBA corners.
    uv=mesh.uv_layers['MinecraftUV'];col=mesh.color_attributes['MinecraftColor']
    for loop in mesh.loops:
        p=rows[loop.vertex_index];uv.data[loop.index].uv=(p['u'],1-p['v'])
        c=p['color']&0xffffffff
        col.data[loop.index].color=((c>>16&255)/255,(c>>8&255)/255,(c&255)/255,(c>>24&255)/255)
    for loop in mesh.loops:
        p=rows[loop.vertex_index];c=p['color']&0xffffffff
        expected=((c>>16&255)/255,(c>>8&255)/255,(c&255)/255,(c>>24&255)/255)
        if max(abs(a-b) for a,b in zip(col.data[loop.index].color,expected))>2e-6:
            raise AssertionError('Blender CustomData RGBA corruption at loop '+str(loop.index))
        if max(abs(a-b) for a,b in zip(uv.data[loop.index].uv,(p['u'],1-p['v'])))>2e-6:
            raise AssertionError('Blender CustomData UV corruption at loop '+str(loop.index))
    mesh.materials.append(mat);obj=bpy.data.objects.new(name,mesh);collection.objects.link(obj);obj.parent=turntable
    obj['exact_java_mesh']=str(source);obj['java_vertices']=len(rows);return obj

def camera_to(cam,point,angle,elevation=.22,radius=6):
    cam.location=point+Vector((math.sin(angle),-math.cos(angle),elevation))*radius
    cam.rotation_euler=(point-cam.location).to_track_quat('-Z','Y').to_euler()

def smooth(a,b,x):
    p=max(0,min(1,(x-a)/(b-a)));return p*p*(3-2*p)

def bell(x,start,peak,end):
    if x<=start or x>=end:return 0
    p=(x-start)/(peak-start) if x<peak else (end-x)/(end-peak)
    return p*p*(3-2*p)

def pet_pose(parts,rig,time,root,ridges):
    """Authoring preview of the production carried-idle equations, at 20 ticks/s.

    This does not replace actual Java mesh captures or claim shader equivalence.
    All boxes remain the shared rig; only the documented joint pose is sampled.
    """
    by_name={}
    for node in rig['nodes']:
        obj=parts[node['path']];p=node['pose'];obj.location=vec(p[:3])
        obj.rotation_quaternion=(T@Euler(p[3:],'ZYX').to_matrix()@T.inverted()).to_quaternion()
        scale=node.get('scale',(1,1,1));obj.scale=(scale[0],scale[2],scale[1]);by_name[node['name']]=obj
    def rotate(name,x=0,y=0,z=0):
        by_name[name].rotation_quaternion=(T@Euler((x,y,z),'ZYX').to_matrix()@T.inverted()).to_quaternion()
    breath=math.sin(time*.075);watch=(time/232)%1
    glance=smooth(.08,.12,watch)-smooth(.32,.36,watch)-smooth(.55,.59,watch)+smooth(.79,.83,watch)
    curious=bell(watch,.10,.22,.38)+bell(watch,.58,.68,.82)*.65
    sniff=bell((time/173)%1,.53,.55,.59)+bell((time/173)%1,.60,.62,.66)*.55
    smile=max(0,min(1,.48+curious*.58));cycle=(time/181)%1
    blink=max(bell(cycle,.77,.783,.805),bell(cycle,.825,.838,.857)*.82)
    rest=bell(watch,.38,.43,.50)*.26
    by_name['body'].scale.z=1+breath*.012
    rotate('hind',y=math.sin(time*.071-1.1)*.025,z=math.sin(time*.063+.8)*.010)
    rotate('head',breath*.016-curious*.065-sniff*.025-.018,
           glance*.155+math.sin(time*.025)*.018,glance*curious*.060+math.sin(time*.033)*.008)
    rotate('jaw',.012+max(0,breath)*.023+sniff*.04)
    for side,name in ((-1,'left'),(1,'right')):
        eye=by_name[name+'_eye'];eye.location+=vec((glance*.17,-curious*.045,0))
        eye.scale=(.122,1,.122*max(.055,1-blink*.95-rest));rotate(name+'_eye',z=side*curious*.055)
        corner=by_name[name+'_mouth_corner'];corner.location+=vec((0,-smile*.13,0))
        rotate(name+'_mouth_corner',z=-side*smile*.30)
    by_name['mouth'].scale=(.075,1,.075*(1+(max(0,breath)*.12+sniff*.20)*.35))
    for side in (-1,1):
        for rear in (0,1):
            name=('left' if side<0 else 'right')+('_front_leg' if rear==0 else '_hind_leg')
            rotate(name,y=(.04 if side<0 else -.04) if rear==0 else (.14 if side<0 else -.14),z=side*.12)
    for i in range(6):
        side=-1 if i<3 else 1;branch=i%3
        rotate('frond_'+str(i),math.sin(time*.062-branch*.61)*.05+sniff*(.06-branch*.018),
               side*(.13+branch*.09+math.sin(time*.067-branch*.48)*.055-curious*.045),
               side*(.11+math.sin(time*.085+branch*.73)*.055+curious*.075+sniff*.055))
    plumes=[node for node in rig['nodes'] if node['name'].startswith('plume_')]
    for i,node in enumerate(plumes):
        side=-1 if i<15 else 1;obj=parts[node['path']];p=node['pose']
        rotation=(math.sin(time*.061-i*.38)*.035+sniff*.025,0,p[5]+side*math.sin(time*.084-i*.47)*.025)
        obj.rotation_quaternion=(T@Euler(rotation,'ZYX').to_matrix()@T.inverted()).to_quaternion()
    for i in range(5):
        rotate('tail_'+str(i),math.sin(time*.051-i*.43)*.032,
               math.sin(time*.077-i*.52)*(.065+i*.032+.045),math.sin(time*.059-i*.5)*.014)
    root.location.z=rig['feet_lift']+breath*.006
    for ridge in ridges:ridge.location.z=breath*.006
    return {'time_ticks':time,'glance':glance,'curiosity':curious,'blink':blink,'smile':smile,'sniff':sniff}

def build(output,ridge_dir=None,render=True,turn_frames=24,runtime_dir=None,stills_only=False,body_only=False):
    output=Path(output).resolve();output.mkdir(parents=True,exist_ok=True)
    rig_path=ASSETS/'models/entity/cangsheng_rig.json';rig=read(rig_path)
    if body_only:
        high_rows=[];low_rows=None;high_path=None;low_path=None
    elif ridge_dir:
        ridge_dir=Path(ridge_dir).resolve();high_path=ridge_dir/'cangsheng-ridge-high.json';low_path=ridge_dir/'cangsheng-ridge-low.json'
        if not high_path.is_file() or not low_path.is_file():
            raise FileNotFoundError('Run the actual AxolotlClientChecks first; both exact Java ridge exports are required: '+str(ridge_dir))
        high_rows=read(high_path);low_rows=read(low_path)
    elif runtime_dir:
        # The earlier CPU capture is also exact Java geometry. Undo only the
        # documented outer ridge transform before rebuilding the same scene.
        runtime=Path(runtime_dir).resolve();m=read(runtime/'manifest.json')
        frame=next(f for f in m['frames'] if f['model']=='cangsheng_axolotl' and f['action']=='idle')
        high_path=runtime/frame['file'];data=read(high_path)
        group=next(g for g in data['groups'] if 'cangsheng_guardian_atlas' in g['texture'])
        high_rows=[]
        for row in group['vertices']:
            r,g,b,a=(int(c) for c in row[5:9])
            high_rows.append({'x':row[0]/1.03,'y':(row[1]-rig['feet_lift'])/.84,'z':row[2]/.84,
                              'u':row[3],'v':row[4],'color':(a<<24)|(r<<16)|(g<<8)|b})
        low_path=None;low_rows=None
    else:raise ValueError('Provide --ridge exact client exports or --runtime exact CPU-capture manifest')
    java=(REPO/'src/main/java/com/twoi/combatdoll/client/CangshengAxolotlModel.java').read_text(encoding='utf-8')
    if 'models/entity/cangsheng_rig.json' not in java:raise ValueError('Minecraft is not loading this authoring rig')
    if rig['model_scale']!=1.5 or rig['feet_lift']!=.37:raise ValueError('Java root transform changed; update the shared authoring transform')
    bpy.ops.wm.read_factory_settings(use_empty=True);scene=bpy.context.scene
    scene.render.engine='CYCLES';scene.cycles.samples=16;scene.cycles.use_denoising=True
    scene.render.resolution_x=800;scene.render.resolution_y=800;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard';scene.view_settings.exposure=-1.0
    scene.world=bpy.data.worlds.new('Neutral pastel studio');scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.17,.18,.22,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=.55
    rig_collection=bpy.data.collections.new('Editable voxel rig');scene.collection.children.link(rig_collection)
    mountain_collection=bpy.data.collections.new('Exact Java mountain LODs');scene.collection.children.link(mountain_collection)
    turn=bpy.data.objects.new('Turntable / preview only',None);scene.collection.objects.link(turn)
    root=bpy.data.objects.new('Cangsheng / pixel-to-world',None);rig_collection.objects.link(root)
    root.parent=turn;root.scale=(rig['model_scale']/16,)*3;root.location.z=rig['feet_lift']
    skin_path=ASSETS/'textures/entity/cangsheng_axolotl_skin.png';atlas_path=ASSETS/'textures/entity/cangsheng_guardian_atlas.png'
    skin=material('Original nearest-pixel milk / rose skin',skin_path)
    atlas=material('Original nearest-pixel living rock / petals',atlas_path,True)
    parts={'root':root};all_cubes=[];node_map={}
    for index,node in enumerate(rig['nodes']):
        obj=bpy.data.objects.new(node['name'],None);rig_collection.objects.link(obj);obj.parent=parts[node['parent']]
        p=node['pose'];obj.location=vec(p[:3]);obj.rotation_mode='QUATERNION'
        obj.rotation_quaternion=(T@Euler(p[3:],'ZYX').to_matrix()@T.inverted()).to_quaternion()
        if 'scale' in node:obj.scale=(node['scale'][0],node['scale'][2],node['scale'][1])
        obj['source_rig_path']=node['path'];obj['source_node']=json.dumps(node)
        parts[node['path']]=obj;node_map[node['path']]=node
        for ci,cube in enumerate(node['boxes']):
            mesh=cube_mesh(f'{index:02d}.{node["name"]}.cube-{ci:02d}',cube,obj,skin,rig_collection)
            all_cubes.append((mesh,node['path']))
    high=ridge_mesh(high_rows,'Living mountain / high LOD',atlas,mountain_collection,turn,rig['feet_lift'],high_path)
    if low_rows:
        low=ridge_mesh(low_rows,'Living mountain / low LOD',atlas,mountain_collection,turn,rig['feet_lift'],low_path)
        low.hide_render=True;low.hide_viewport=True
    # A real bone guide supplements the editable transform joints. Each box
    # is assigned wholly to its joint, retaining the original voxel hard edges.
    bpy.context.view_layer.update()
    arm_data=bpy.data.armatures.new('Cangsheng original joint skeleton')
    arm=bpy.data.objects.new('Cangsheng skeleton / editable',arm_data);rig_collection.objects.link(arm)
    arm.parent=turn;bpy.context.view_layer.objects.active=arm;arm.select_set(True);bpy.ops.object.mode_set(mode='EDIT')
    bones={}
    for index,node in enumerate(rig['nodes']):
        joint=parts[node['path']];name=f'{index:02d}.{node["name"]}'
        bone=arm_data.edit_bones.new(name);bone.head=joint.matrix_world.translation
        bone.tail=bone.head+joint.matrix_world.to_3x3()@Vector((0,0,.65))
        if node['parent'] in bones:bone.parent=arm_data.edit_bones[bones[node['parent']]]
        bones[node['path']]=name
    bpy.ops.object.mode_set(mode='OBJECT');arm.show_in_front=True;arm.hide_render=True;arm.display_type='WIRE';arm.select_set(False)
    for obj,path in all_cubes:
        group=obj.vertex_groups.new(name=bones[path]);group.add(list(range(len(obj.data.vertices))),1,'REPLACE')
        modifier=obj.modifiers.new('Rigid voxel joint binding','ARMATURE');modifier.object=arm
    # Neutral studio fixtures expose gaps and intersections. They are not part
    # of the game model and remain clearly separated in the editable project.
    bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.015));floor=bpy.context.object;floor.name='Studio ground / not game geometry'
    floor_mat=bpy.data.materials.new('Studio warm grey');floor_mat.diffuse_color=(.19,.18,.21,1);floor.data.materials.append(floor_mat)
    center=Vector((0,.45,.50))
    for name,pos,power,color in [('Key',(-3,-4,5),560,(1,.92,.90)),('Fill',(4,-1,3),390,(.88,.93,1)),('Rim',(1,4,4),650,(1,.86,.95))]:
        data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=4;data.color=color
        obj=bpy.data.objects.new(name,data);scene.collection.objects.link(obj);obj.location=Vector(pos)
        obj.rotation_euler=(center-obj.location).to_track_quat('-Z','Y').to_euler()
    camera_data=bpy.data.cameras.new('Study camera');cam=bpy.data.objects.new('Study camera',camera_data)
    scene.collection.objects.link(cam);scene.camera=cam;camera_data.type='ORTHO';camera_data.ortho_scale=4.2
    scene.render.fps=20;scene.frame_start=1;scene.frame_end=240
    for f,angle in ((1,0),(240,math.tau)):
        turn.rotation_euler.z=angle;turn.keyframe_insert('rotation_euler',frame=f)
    for fc in turn.animation_data.action.fcurves:
        for key in fc.keyframe_points:key.interpolation='LINEAR'
    ridges=[high]+([low] if low_rows else [])
    for frame in range(1,241):
        pet_pose(parts,rig,frame-1,root,ridges)
        for obj in list(parts.values())+ridges:
            obj.keyframe_insert('location',frame=frame);obj.keyframe_insert('rotation_quaternion',frame=frame);obj.keyframe_insert('scale',frame=frame)
    for obj in list(parts.values())+ridges:
        if obj.animation_data:
            for fc in obj.animation_data.action.fcurves:
                for key in fc.keyframe_points:key.interpolation='LINEAR'
    scene.frame_set(1);camera_to(cam,center,-.48,.24)
    scene['authoritative_geometry']=str(rig_path);scene['ridge_is_exact_runtime_export']=not body_only
    scene['body_only_preview']=body_only
    scene['preview_scope']='Neutral shape/UV study. In-game scene lighting, shaders and performance require actual-client checks.'
    scene['editing']='Mesh cuboids and nearest-pixel materials are individually editable. Joint empties retain source axes; armature binds each rigid cuboid to its source joint.'
    blend=output/'cangsheng-shared-rig.blend';bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    images=[];expressions=[]
    if render:
        for label,angle in [('front',0),('three-quarter',-.58),('side',math.pi/2),('back',math.pi)]:
            scene.frame_set(1);camera_to(cam,center,angle,.18)
            scene.render.filepath=str(output/f'cangsheng-{label}.png');bpy.ops.render.render(write_still=True);images.append(scene.render.filepath)
        # Expression frames keep a fixed camera so the motion, not the camera
        # orbit, explains the carried pet's glance, tiny smile and paired blink.
        expression_dir=output/'expressions';expression_dir.mkdir(exist_ok=True)
        turn.rotation_euler.z=0
        for fc in turn.animation_data.action.fcurves:fc.mute=True
        camera_to(cam,Vector((0,-.66,.47)),0,.10);camera_data.ortho_scale=2.15
        expression_samples=[] if stills_only else [('rest',0),('curious-left',52),('sniff',95),('blink',142),('second-blink',152),('curious-right',158)]
        for label,tick in expression_samples:
            scene.frame_set(tick+1);turn.rotation_euler.z=0
            values=pet_pose(parts,rig,tick,root,ridges)
            scene.render.filepath=str(expression_dir/f'{label}.png');bpy.ops.render.render(write_still=True)
            expressions.append({'label':label,'image':scene.render.filepath,**values})
        for fc in turn.animation_data.action.fcurves:fc.mute=False
        camera_data.ortho_scale=4.2
        # The project includes a smooth 240-frame turntable. A compact PNG
        # sequence is also exported without depending on external video codecs.
        frames_dir=output/'turntable';frames_dir.mkdir(exist_ok=True);camera_to(cam,center,-.48,.24)
        for i in range(0 if stills_only else turn_frames):
            scene.frame_set(1+round(i*239/max(1,turn_frames-1)))
            scene.render.filepath=str(frames_dir/f'cangsheng-{i:03d}.png');bpy.ops.render.render(write_still=True)
    manifest={'blender_version':bpy.app.version_string,'blend':str(blend),'rig':str(rig_path),'rig_sha256':sha(rig_path),
              'java_source_sha256':sha(REPO/'src/main/java/com/twoi/combatdoll/client/CangshengAxolotlModel.java'),
              'skin_sha256':sha(skin_path),'atlas_sha256':sha(atlas_path),'high_mesh_sha256':sha(high_path) if high_path else None,'low_mesh_sha256':sha(low_path) if low_path else None,
              'parts':len(rig['nodes']),'cuboids':len(all_cubes),'runtime_body_vertices':len(all_cubes)*24,
              'exact_high_ridge_vertices':len(high_rows),'exact_low_ridge_vertices':len(low_rows) if low_rows else None,
              'images':images,'expression_samples':expressions,'authoring_animation':'Production carried-idle equations sampled at 20 ticks/s; actual-client captures remain authoritative.',
              'turntable_png_frames':turn_frames if render and not stills_only else 0,'body_only_preview':body_only,
              'scope':'Exact shared body rig and exact baked Java ridge, editable joints/materials/cuboids. Studio lighting is not an in-game performance claim.'}
    (output/'cangsheng-study-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    return manifest

if __name__=='__main__':
    args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--ridge');p.add_argument('--runtime')
    p.add_argument('--no-render',action='store_true');p.add_argument('--turntable-frames',type=int,default=24)
    p.add_argument('--stills-only',action='store_true');p.add_argument('--body-only',action='store_true')
    a=p.parse_args(args);build(a.output,a.ridge,not a.no_render,a.turntable_frames,a.runtime,a.stills_only,a.body_only)
