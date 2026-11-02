"""Read-only audit of saved Blender UV/color/image values against runtime JSON."""
from pathlib import Path
import argparse, json, math, sys
import bpy


def rgba(image, x, y_top):
    width, height = image.size
    offset=((height-1-y_top)*width+x)*4
    return list(image.pixels[offset:offset+4])


def main():
    args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    parser=argparse.ArgumentParser()
    parser.add_argument('--blend',type=Path,required=True)
    parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    options=parser.parse_args(args)
    bpy.ops.wm.open_mainfile(filepath=str(options.blend.resolve()))
    report={'blend':str(options.blend.resolve()),'blender':bpy.app.version_string,'objects':[],'images':[]}
    for obj in bpy.data.objects:
        if obj.type!='MESH' or 'source_frame' not in obj:continue
        frame=json.loads((options.runtime/obj['source_frame']).read_text(encoding='utf-8'))
        candidates=[g for g in frame['groups'] if g['name']==obj['runtime_group']
                    and g['texture']==obj['runtime_material']]
        same_size=[g for g in candidates if len(g['vertices'])==len(obj.data.vertices)]
        if not same_size:
            report['objects'].append({'name':obj.name,'error':'No matching source material/group/vertex count',
                                      'actual_vertices':len(obj.data.vertices),'candidate_vertices':[len(g['vertices']) for g in candidates]})
            continue
        group=same_size[0]
        rows=group['vertices'];mesh=obj.data
        uv=mesh.uv_layers.get('MinecraftUV');color=mesh.color_attributes.get('MinecraftColor')
        samples=[];max_uv=0.;max_color=0.;max_pos=0.
        for loop in mesh.loops:
            row=rows[loop.vertex_index]
            expected_uv=(row[3],1-row[4]);actual_uv=list(uv.data[loop.index].uv)
            expected_color=[v/255 for v in row[5:9]];actual_color=list(color.data[loop.index].color)
            expected_pos=(row[0],-row[2],row[1]);actual_pos=list(mesh.vertices[loop.vertex_index].co)
            max_uv=max(max_uv,max(abs(a-b) for a,b in zip(actual_uv,expected_uv)))
            max_color=max(max_color,max(abs(a-b) for a,b in zip(actual_color,expected_color)))
            max_pos=max(max_pos,max(abs(a-b) for a,b in zip(actual_pos,expected_pos)))
            if loop.index<8:
                samples.append({'loop':loop.index,'expected_uv':expected_uv,'actual_uv':actual_uv,
                                'expected_color':expected_color,'actual_color':actual_color})
        materials=[]
        for slot in obj.material_slots:
            mat=slot.material
            materials.append({'name':mat.name,'nodes':[{'name':node.name,'type':node.bl_idname,
                'attribute':getattr(node,'layer_name',None),'image':getattr(getattr(node,'image',None),'name',None)} for node in mat.node_tree.nodes],
                'links':[(link.from_node.name,link.from_socket.name,link.to_node.name,link.to_socket.name) for link in mat.node_tree.links]})
        report['objects'].append({'name':obj.name,'loops':len(mesh.loops),'source':obj['source_frame'],
            'max_uv_error':max_uv,'max_color_error':max_color,'max_position_error':max_pos,
            'uv_layers':[v.name for v in mesh.uv_layers],'color_attributes':[(c.name,c.domain,c.data_type) for c in mesh.color_attributes],
            'samples':samples,'materials':materials})
    for image in bpy.data.images:
        if image.source not in ('FILE','GENERATED'):continue
        width,height=image.size
        if not width or not height:continue
        pixels=[]
        for x,y in [(0,0),(min(16,width-1),min(16,height-1)),(width//2,height//2),(width-1,height-1)]:
            pixels.append({'x':x,'y_top':y,'rgba':rgba(image,x,y)})
        report['images'].append({'name':image.name,'filepath':image.filepath,'size':[width,height],
            'colorspace':image.colorspace_settings.name,'alpha_mode':image.alpha_mode,
            'packed':image.packed_file is not None,'pixels':pixels})
    options.output.parent.mkdir(parents=True,exist_ok=True)
    options.output.write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({'output':str(options.output),'objects':[{k:o[k] for k in ('name','error','max_uv_error','max_color_error','max_position_error') if k in o} for o in report['objects']],'images':report['images']},indent=2))


if __name__=='__main__':main()
