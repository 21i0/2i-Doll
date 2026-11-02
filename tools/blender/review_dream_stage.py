"""Read-only render of the exact exported editable stage geometry."""
import bpy,sys,math
from pathlib import Path
from mathutils import Vector
args=sys.argv[sys.argv.index('--')+1:];folder=Path(args[0]).resolve()
bpy.ops.wm.open_mainfile(filepath=str(folder/'dream_realm.blend'))
scene=bpy.context.scene
for col in scene.collection.children:
    col.hide_render=not col.name.startswith('RuntimeStage HIGH')
    col.hide_viewport=False
    print('STAGE REVIEW COLLECTION',col.name,col.hide_render,len(col.objects),flush=True)
    if col.name.startswith('RuntimeStage HIGH'):
        for obj in col.objects:obj.hide_render=False;obj.hide_set(False)
for layer in scene.view_layers:
    for child in layer.layer_collection.children:
        child.exclude=False;child.hide_viewport=False
bpy.context.view_layer.update()
data=bpy.data.cameras.new('RuntimeStage independent visual review')
data.clip_end=10000
camera=bpy.data.objects.new('RuntimeStage independent visual review',data);scene.collection.objects.link(camera);scene.camera=camera
scene.render.resolution_x=1400;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
scene.render.film_transparent=False;scene.cycles.samples=16;scene.render.threads=6
for name,location,target in [('front',(0,-1550,240),(0,100,-20)),('threequarter',(1000,-1300,600),(0,140,-20))]:
    camera.location=location;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();data.lens=38
    scene.render.filepath=str(folder/f'dream-stage-{name}.png');bpy.ops.render.render(write_still=True)
