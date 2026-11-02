"""Install an already-rendered, visually reviewed dream-realm export without rerendering.

Old production files and their hashes remain recoverable next to the bake.
Blender sources, authoring scripts and all review PNGs stay outside game resources.
"""
from pathlib import Path
import argparse,hashlib,json,shutil,struct

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('bake',type=Path);parser.add_argument('--install',action='store_true')
    args=parser.parse_args();folder=args.bake.resolve()
    manifest=json.loads((folder/'dream-realm-bake.json').read_text(encoding='utf8'))
    if manifest['generator_sha256']!=sha(ROOT/manifest['generator']):raise RuntimeError('Generator changed after actual bake')
    stage=json.loads((folder/'cangsheng_dream_stage.json').read_text(encoding='utf8'))
    assert stage['format']=='cangsheng_dream_stage_v1' and stage['scale']==1
    assert stage['rgba_range']=='0..1' and stage['uv_origin']=='top-left'
    for lod,limit in [('HIGH',60000),('LOW',25000)]:
        data=stage['lods'][lod];assert data['vertex_count']==len(data['quads'])*4<=limit
    files={'textures/effect/cangsheng_landscape.png':'cangsheng_landscape.png',
           'textures/effect/cangsheng_dream_materials.png':'cangsheng_dream_materials.png',
           'models/effect/cangsheng_dream_stage.json':'cangsheng_dream_stage.json'}
    resources=ROOT/'src/main/resources/assets/two_i_combat_doll';assets={};previous={}
    final_target=ROOT/'verification/axolotl-20261027/dream-realm-bake-final.json'
    if args.install and final_target.exists():
        archive=folder/'previous-manifests'/('dream-realm-bake-final-'+sha(final_target)[:16]+'.json')
        archive.parent.mkdir(parents=True,exist_ok=True)
        if not archive.exists():shutil.copy2(final_target,archive)
        manifest['previous_final_manifest_archive']=str(archive)
    for key,name in files.items():
        source=folder/name;target=resources/key
        if name.endswith('.png'):
            raw=source.read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n'
            width,height=struct.unpack('!2I',raw[16:24]);assert raw[25]==6
            assert [width,height]==([2048,768] if name=='cangsheng_landscape.png' else [512,512])
        if target.exists():
            oldhash=sha(target);previous[key]={'sha256':oldhash,'bytes':target.stat().st_size}
            if args.install:
                backup=folder/'previous-production'/key
                if backup.exists() and sha(backup)!=oldhash:backup=backup.with_name(backup.stem+'-'+oldhash[:16]+backup.suffix)
                backup.parent.mkdir(parents=True,exist_ok=True)
                if not backup.exists():shutil.copy2(target,backup)
                previous[key]['backup']=str(backup)
        if args.install:target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        assets['assets/two_i_combat_doll/'+key]={'sha256':sha(source),'bytes':source.stat().st_size}
    assert sha(folder/'cangsheng_dream_materials.png')==stage['texture_sha256']
    manifest.update({'installed':args.install,'review_passed':args.install,
                     'review':'Actual Blender front panorama and exact stage front / three-quarter PNGs reviewed before installation.',
                     'review_scope':'Developer integration review; final user aesthetic approval is not claimed.',
                     'assets':assets,'previous_production':previous,'blend_sha256':sha(folder/'dream_realm.blend'),
                     'finalizer':'tools/install-dream-realm.py','finalizer_sha256':sha(Path(__file__)),
                     'blend':str(folder/'dream_realm.blend'),'bake_directory':str(folder),
                     'runtime_geometry':{lod:{k:v for k,v in data.items() if k!='quads'} for lod,data in stage['lods'].items()},
                     'not_packaged':['dream_realm.blend','authoring scripts','source/review PNGs','historical rejected bakes'],
                     'source_images_read':[],'external_textures_or_meshes':[]})
    target=final_target if args.install else folder/'installation-preview.json'
    target.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    if args.install:
        history=json.dumps(previous,indent=2)+'\n';history_hash=hashlib.sha256(history.encode('utf8')).hexdigest()[:16]
        history_target=folder/f'previous-production/previous-hashes-{history_hash}.json'
        history_target.parent.mkdir(parents=True,exist_ok=True)
        if not history_target.exists():history_target.write_text(history,encoding='utf8')
    print(json.dumps(manifest,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
