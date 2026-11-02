"""Blender 4.5 study of exact Minecraft renderer meshes, UVs and pose clips.

blender -b --python tools/blender/import_runtime_mesh.py -- --input DIR --output DIR
Studio frames are shape/material evidence, not a replacement for in-game shaders.
"""
from pathlib import Path
import argparse, hashlib, json, math, sys
import bpy
from mathutils import Vector

def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))

def axis(row):
    return (row[0], -row[2], row[1])

def material(root, group, cache):
    key=group['texture_file']
    if key in cache:return cache[key]
    mat=bpy.data.materials.new(group['texture']);mat.use_nodes=True
    nodes=mat.node_tree.nodes;nodes.clear();links=mat.node_tree.links
    output=nodes.new('ShaderNodeOutputMaterial');bsdf=nodes.new('ShaderNodeBsdfPrincipled')
    texture=nodes.new('ShaderNodeTexImage');texture.image=bpy.data.images.load(str(root/key),check_existing=True)
    texture.image.pack();texture.interpolation='Closest';texture.extension='REPEAT'
    colour=nodes.new('ShaderNodeVertexColor');colour.layer_name='MinecraftColor'
    multiply=nodes.new('ShaderNodeMixRGB');multiply.blend_type='MULTIPLY';multiply.inputs[0].default_value=1
    links.new(texture.outputs['Color'],multiply.inputs[1]);links.new(colour.outputs['Color'],multiply.inputs[2])
    links.new(multiply.outputs[0],bsdf.inputs['Base Color'])
    alpha=nodes.new('ShaderNodeMath');alpha.operation='MULTIPLY'
    links.new(texture.outputs['Alpha'],alpha.inputs[0]);links.new(colour.outputs['Alpha'],alpha.inputs[1])
    links.new(alpha.outputs[0],bsdf.inputs['Alpha'])
    bsdf.inputs['Roughness'].default_value=.65
    if 'astral' in key or 'scarlet' in key:
        links.new(multiply.outputs[0],bsdf.inputs['Emission Color']);bsdf.inputs['Emission Strength'].default_value=.20
    links.new(bsdf.outputs[0],output.inputs[0])
    mat.use_backface_culling=False
    cache[key]=mat;return mat

def mesh_object(root, group, name, collection, cache):
    rows=group['vertices'];mode=group['mode']
    if mode not in ('QUADS','TRIANGLES'):raise ValueError(f'Unsupported actual primitive {mode} in {name}')
    n=4 if mode=='QUADS' else 3
    faces=[tuple(range(i,i+n)) for i in range(0,len(rows),n)]
    mesh=bpy.data.meshes.new(name);mesh.from_pydata([axis(row) for row in rows],[],faces);mesh.update()
    obj=bpy.data.objects.new(name,mesh);collection.objects.link(obj)
    mesh.uv_layers.new(name='MinecraftUV')
    mesh.color_attributes.new(name='MinecraftColor',type='FLOAT_COLOR',domain='CORNER')
    # Adding custom data reallocates Blender's backing arrays. Reacquire both
    # RNA handles afterwards, otherwise UV writes can corrupt vertex colours.
    uv=mesh.uv_layers['MinecraftUV'];color=mesh.color_attributes['MinecraftColor']
    for loop in mesh.loops:
        row=rows[loop.vertex_index]
        uv.data[loop.index].uv=(row[3],1-row[4])
        color.data[loop.index].color=tuple(v/255 for v in row[5:9])
    for loop in mesh.loops:
        row=rows[loop.vertex_index]
        assert max(abs(uv.data[loop.index].uv[i]-(row[3],1-row[4])[i]) for i in range(2))<1e-5
        assert max(abs(color.data[loop.index].color[i]-row[5+i]/255) for i in range(4))<1e-5
    mesh.materials.append(material(root,group,cache))
    obj['runtime_material']=group['texture'];obj['runtime_group']=group['name']
    return obj

def set_active_collection(collection):
    for child in bpy.context.scene.collection.children:
        if child.get('runtime_action'):
            active=child==collection;child.hide_render=not active;child.hide_viewport=not active

def camera_to(camera,center,radius,azimuth,elevation=.18):
    camera.location=center+Vector((math.sin(azimuth),-math.cos(azimuth),elevation))*radius
    camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler()

def study_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=12
    scene.cycles.use_denoising=True;scene.render.resolution_x=768;scene.render.resolution_y=768
    scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG'
    scene.render.film_transparent=False
    scene.world=bpy.data.worlds.new('Neutral studio');scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.075,.09,.13,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=.5
    scene.view_settings.view_transform='Standard';scene.view_settings.look='Medium High Contrast'
    camera_data=bpy.data.cameras.new('Study camera');camera=bpy.data.objects.new('Study camera',camera_data)
    scene.collection.objects.link(camera);scene.camera=camera;camera_data.type='ORTHO';camera_data.lens=48
    return scene,camera

def add_lights(scene,center,size):
    for name,offset,power,colour in [
        ('Key',(-3,-4,5),650,(1,.91,.85)),('Fill',(4,-1,2),420,(.81,.88,1)),('Rim',(1,4,4),800,(.9,.82,1))]:
        data=bpy.data.lights.new(name,'AREA');data.energy=power*size*size;data.shape='DISK';data.size=size*3
        data.color=colour;obj=bpy.data.objects.new(name,data);scene.collection.objects.link(obj)
        obj.location=center+Vector(offset)*size
        obj.rotation_euler=(center-obj.location).to_track_quat('-Z','Y').to_euler()

def build_study(runtime_dir,output_dir,models=None,render=True):
    root=Path(runtime_dir).resolve();output=Path(output_dir).resolve();output.mkdir(parents=True,exist_ok=True)
    manifest_path=root/('manifest-with-optional.json' if (root/'manifest-with-optional.json').exists() else 'manifest.json')
    manifest=read(manifest_path)
    available=list(dict.fromkeys(frame['model'] for frame in manifest['frames']))
    selected=available if models is None else models
    result=[]
    for model in selected:
        entries=[frame for frame in manifest['frames'] if frame['model']==model]
        if not entries:raise ValueError(f'Missing actual model {model}')
        scene,camera=study_scene();cache={};collections={};first_objects={};source_records=[]
        actions=list(dict.fromkeys(frame['action'] for frame in entries))
        for action in actions:
            subset=sorted([e for e in entries if e['action']==action],key=lambda e:e['frame'])
            collection=bpy.data.collections.new(model+' / '+action);scene.collection.children.link(collection)
            collection['runtime_action']=action;collections[action]=collection
            frames=[read(root/e['file']) for e in subset];base=frames[0]
            objects=[]
            for index,group in enumerate(base['groups']):
                obj=mesh_object(root,group,f'{model}.{action}.{index}',collection,cache)
                obj['source_frame']=subset[0]['file'];obj['source_rig_snapshot']=json.dumps(base.get('rig',{}))
                obj.shape_key_add(name='Basis');objects.append(obj)
            first_objects[action]=objects
            # These keys are exact posed meshes. They deliberately do not claim
            # to be a reconstructed artist rig; Cangsheng has its own shared rig.
            for index,(entry,data) in enumerate(zip(subset,frames)):
                source_records.append({'file':entry['file'],'sha256':hashlib.sha256((root/entry['file']).read_bytes()).hexdigest()})
                for group_index,obj in enumerate(objects):
                    if group_index>=len(data['groups']):raise ValueError(f'Changing material group topology: {entry}')
                    rows=data['groups'][group_index]['vertices']
                    if len(rows)!=len(obj.data.vertices):raise ValueError(f'Changing vertex topology: {entry}')
                    key=obj.shape_key_add(name=f'pose-{index:02d}')
                    for vertex,row in zip(key.data,rows):vertex.co=axis(row)
                    key.value=0;key.keyframe_insert('value',frame=max(0,index*8-8))
                    key.value=1;key.keyframe_insert('value',frame=index*8)
                    key.value=0;key.keyframe_insert('value',frame=index*8+8)
            collection.hide_render=True;collection.hide_viewport=True
        hero=next((a for a in ('standing','idle','orbit','opening','actual_entity_idle') if a in collections),actions[0])
        set_active_collection(collections[hero]);scene.frame_set(0)
        points=[vertex.co for obj in first_objects[hero] for vertex in obj.data.vertices]
        low=Vector(tuple(min(v[i] for v in points) for i in range(3)));high=Vector(tuple(max(v[i] for v in points) for i in range(3)))
        center=(low+high)*.5;extent=high-low;size=max(extent);camera.data.ortho_scale=size*1.42
        add_lights(scene,center,max(.2,size))
        # Neutral floor and shadow make gaps, detached components and proportions
        # easier to see than a beauty render with a busy magical background.
        bpy.ops.mesh.primitive_plane_add(size=size*200,location=(0,0,low.z-.015*size))
        plane=bpy.context.object;plane.name='Neutral ground / preview only'
        mat=bpy.data.materials.new('Studio floor');mat.diffuse_color=(.14,.17,.22,1);plane.data.materials.append(mat)
        scene.frame_end=max(56,max(len([e for e in entries if e['action']==a]) for a in actions)*8-8)
        front=math.pi if model in ('cangsheng_axolotl','longhuan','xiaoxiao') else 0
        previous=None
        for frame,angle in ((1,0),(20,math.pi*.5),(40,math.pi),(60,math.pi*1.5),(80,math.pi*2)):
            camera_to(camera,center,max(1,size)*3,front+angle,.19)
            if previous is not None:camera.rotation_euler.make_compatible(previous)
            previous=camera.rotation_euler.copy()
            camera.keyframe_insert('location',frame=frame);camera.keyframe_insert('rotation_euler',frame=frame)
        if camera.animation_data and camera.animation_data.action:
            for curve in camera.animation_data.action.fcurves:
                for point in curve.keyframe_points:point.interpolation='LINEAR'
        scene['runtime_manifest']=str(manifest_path);scene['source_meshes_are_exact']=True
        scene['studio_is_not_game_postprocessing']=True
        scene['action_instructions']='Enable one collection per action. Shape-key clips use eight Blender frames per captured Minecraft pose.'
        scene.frame_set(0);camera_to(camera,center,max(1,size)*3,front-.42,.23)
        blend=output/(model+'.blend');bpy.ops.wm.save_as_mainfile(filepath=str(blend))
        shots=[]
        if render:
            # Keyframes belong to the editable turntable, but must not reset an
            # explicitly positioned still camera during dependency evaluation.
            camera_action=camera.animation_data.action;camera.animation_data.action=None
            for view,angle in (('front',0),('three-quarter',-.6),('side',math.pi*.5),('back',math.pi)):
                scene.frame_set(0);camera_to(camera,center,max(1,size)*3,front+angle,.19)
                scene.render.filepath=str(output/(model+'-'+view+'.png'));bpy.ops.render.render(write_still=True);shots.append(scene.render.filepath)
            for action in actions:
                if action not in ('attack','roar','carried','opening','rain'):continue
                set_active_collection(collections[action]);scene.frame_set(max(8,(len([e for e in entries if e['action']==action])-1)*4))
                camera_to(camera,center,max(1,size)*3,front-.6,.19)
                scene.render.filepath=str(output/(model+'-'+action+'.png'));bpy.ops.render.render(write_still=True);shots.append(scene.render.filepath)
            camera.animation_data.action=camera_action
        result.append({'model':model,'blend':str(blend),'actions':actions,'actual_frames':len(entries),'bounds':[list(low),list(high)],'images':shots,'source_records':source_records})
        (output/'blender-study-manifest.json').write_text(json.dumps({'blender_version':bpy.app.version_string,'runtime_manifest':str(manifest_path),'models':result,'render_scope':'Neutral studio previews of exact meshes; in-game postprocessing and performance must be verified separately'},indent=2),encoding='utf-8')
    return result

if __name__=='__main__':
    args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    parser=argparse.ArgumentParser();parser.add_argument('--input',required=True);parser.add_argument('--output',required=True)
    parser.add_argument('--models',default='');parser.add_argument('--no-render',action='store_true')
    options=parser.parse_args(args)
    build_study(options.input,options.output,options.models.split(',') if options.models else None,not options.no_render)
