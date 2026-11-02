"""Audit and vendor current 1.21.1 companions into the standalone combat JAR.

No binary companion JAR is used as a compile/runtime dependency. Existing public
mod IDs, item IDs, NBT and special animation code are retained as a multi-mod JAR.
Run without --apply for inventory only; old source trees are never modified.
"""
from pathlib import Path
import argparse, hashlib, json, re, shutil, tomllib, zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'DollSkinBlinkTool/output/mods'
EVIDENCE = ROOT / 'verification/bundled-dolls-20261031'
DEST = ROOT / 'src/bundled'
# The custom story edition supersedes the original export of the same character,
# despite having changed its mod ID. Do not expose two Lonya dolls in a new pack.
SUPERSEDED_EXPORTS = {'lonya': 'lonya_plush'}

def digest(blob): return hashlib.sha256(blob).hexdigest()
def properties(path):
    return dict(line.split('=', 1) for line in path.read_text('utf-8-sig').splitlines()
                if '=' in line and not line.startswith('#'))
def order(version): return tuple(int(n) for n in re.findall(r'\d+', version))
def metadata(path):
    with zipfile.ZipFile(path) as jar:
        name = 'META-INF/neoforge.mods.toml'
        return tomllib.loads(jar.read(name).decode('utf-8-sig'))

def inventory():
    rows=[]
    for folder in sorted(SOURCE.iterdir()):
        project=folder/'project'; prop=project/'gradle.properties'
        if not prop.is_file(): continue
        p=properties(prop); v=p['mod_version']; mod=p['mod_id']
        meta=project/'src/main/resources/META-INF/neoforge.mods.toml'
        if not v.startswith('1.21.1-'):
            rows.append(dict(modId=mod, version=v, project=str(project), selected=False,
                             reason='different Minecraft/loader target'))
            continue
        jars=[]
        for base in (folder/'jars', project/'build/libs'):
            for jar in sorted(base.glob('*.jar')):
                if jar.name.endswith('-sources.jar'): continue
                try:
                    m=metadata(jar)
                    if any(x['modId']==mod and x['version']==v for x in m['mods']):
                        jars.append((jar,m))
                except (KeyError, ValueError, zipfile.BadZipFile): continue
        if not jars: raise RuntimeError(f'No actual metadata JAR for {mod} {v}')
        jar,m=jars[0]
        mc=next(d for d in m['dependencies'][mod] if d['modId']=='minecraft')['versionRange']
        if mc!='[1.21.1]': raise RuntimeError(f'{mod}: game range {mc}')
        rows.append(dict(modId=mod,version=v,game=mc,displayName=p['mod_name'],
                         project=str(project),jar=str(jar),jarSha256=digest(jar.read_bytes()),
                         sourceMetadataSha256=digest(meta.read_bytes()),metadata=m,selected=False))
    newest={}
    for r in rows:
        if 'metadata' not in r: continue
        old=newest.get(r['modId'])
        if old is None or order(r['version'])>order(old['version']):newest[r['modId']]=r
    for r in rows:
        if r is newest.get(r['modId']):r['selected']=True;r['reason']='highest actual mod version for 1.21.1'
        elif 'metadata' in r:r['reason']='older duplicate modId/game target'
    for old_mod, replacement_mod in SUPERSEDED_EXPORTS.items():
        old = newest.get(old_mod); replacement = newest.get(replacement_mod)
        if old is None or replacement is None: continue
        def original_skin(row):
            return (Path(row['project']) / 'src/main/resources/assets' /
                    row['modId'] / 'textures/entity/skin.png').read_bytes()
        if original_skin(old) != original_skin(replacement):
            raise RuntimeError(f'Superseded export skin identity changed: {old_mod}/{replacement_mod}')
        if order(replacement['version']) <= order(old['version']):
            raise RuntimeError(f'Superseding export is not newer: {old_mod}/{replacement_mod}')
        old['selected'] = False
        old['reason'] = f'superseded same-character export by newer {replacement_mod}; exact original skin matches'
        old['supersededBy'] = replacement_mod
    # Verify the newly installed Longhuan and XiaoXiao really are the chosen source.
    installed=Path('D:/AI/packs/MyGenesis-0.2.0/overrides/mods')
    for mod in ('longhuan','xiaoxiao'):
        r=newest[mod]; official=installed/f"{mod}-{r['version']}.jar"
        if official.exists():
            with zipfile.ZipFile(official) as jar:
                skin=f'assets/{mod}/textures/entity/skin.png'
                src=Path(r['project'])/'src/main/resources'/skin
                if jar.read(skin)!=src.read_bytes():raise RuntimeError(f'Installed {mod} skin differs from selected source')
                r['installedJar']=str(official);r['installedSha256']=digest(official.read_bytes())
                r['installedSkinExactlyMatchesSource']=True
    return rows

def merge_json(old, new, path):
    a=json.loads(old); b=json.loads(new)
    key='sources' if path=='assets/minecraft/atlases/blocks.json' else 'values'
    if key not in a or key not in b:raise RuntimeError('Unmergeable resource collision: '+path)
    values=[]
    for x in a[key]+b[key]:
        if x not in values:values.append(x)
    a[key]=values
    if key=='values':a['replace']=bool(a.get('replace',False) or b.get('replace',False))
    return (json.dumps(a,ensure_ascii=False,indent=2)+'\n').encode()

def vendor(rows):
    if DEST.exists():raise RuntimeError(f'{DEST} exists; inspect instead of overwriting prior import')
    resources={}; origins={}; files=[]
    for r in rows:
        if not r['selected']:continue
        project=Path(r['project'])
        for kind in ('java','resources'):
            for f in sorted((project/f'src/main/{kind}').rglob('*')):
                if not f.is_file():continue
                path=f.relative_to(project/f'src/main/{kind}').as_posix()
                if kind=='resources' and path in ('META-INF/neoforge.mods.toml','pack.mcmeta'):continue
                blob=f.read_bytes()
                if kind=='java':
                    dest=DEST/kind/path
                    if dest.exists():raise RuntimeError(f'Duplicate Java path {path}')
                    dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(blob)
                else:
                    if path in resources and resources[path]!=blob:resources[path]=merge_json(resources[path],blob,path)
                    else:resources[path]=blob
                    origins.setdefault(path,[]).append(r['modId'])
                files.append(dict(modId=r['modId'],kind=kind,path=path,sha256=digest(blob)))
    # Piston has no existing exported mod. Build its complete B companion from the
    # existing MIT companion implementation, using the owner's actual Piston skin.
    original=Path(next(r['project'] for r in rows if r['selected'] and r['modId']=='nadir'))
    for f in sorted((original/'src/main/java').rglob('*.java')):
        path=f.relative_to(original/'src/main/java').as_posix().replace('nadir','piston')
        content=f.read_text('utf-8-sig').replace('nadir','piston').replace('Nadir','Piston')
        dest=DEST/'java'/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(content,'utf-8')
    for f in sorted((original/'src/main/resources').rglob('*')):
        if not f.is_file():continue
        old=f.relative_to(original/'src/main/resources').as_posix()
        if old in ('META-INF/neoforge.mods.toml','pack.mcmeta'):continue
        path=old.replace('nadir','piston');blob=f.read_bytes()
        if f.suffix in ('.json',):
            blob=blob.decode('utf-8-sig').replace('nadir','piston').replace('Nadir的玩偶','活塞的玩偶').replace('Nadir','Piston').encode()
        if path in resources and resources[path]!=blob:resources[path]=merge_json(resources[path],blob,path)
        else:resources[path]=blob
    skin=EVIDENCE/'piston-material/d428bcb465b87923.png'
    if not skin.exists():raise RuntimeError('Piston skin must be extracted from owner-provided 7z first')
    from PIL import Image
    im=Image.open(skin).convert('RGBA')
    if im.size!=(64,64):raise RuntimeError(f'Unexpected Piston skin size {im.size}')
    resources['assets/piston/textures/entity/skin.png']=skin.read_bytes()
    # Eye regions are the original skin's front eyes at x9/13, y12. Each frame
    # fills the actual eye row, preserving the surrounding hair and cheeks.
    import io
    for state in ('half','closed'):
        out=im.copy()
        for x0 in (9,13):
            for x in range(x0,x0+2):
                for y in (12,13):
                    if state=='closed' or y==12:out.putpixel((x,y),(225,190,169,255))
        buf=io.BytesIO();out.save(buf,format='PNG')
        resources[f'assets/piston/textures/entity/skin_blink_{state}.png']=buf.getvalue()
    config=json.loads(resources['assets/piston/textures/entity/skin.json'])
    config['source']=str(skin);config['regions']={f'{side}_eye':dict(x=x,y=12,width=2,height=2) for side,x in (('left',9),('right',13))}
    resources['assets/piston/textures/entity/skin.json']=(json.dumps(config,ensure_ascii=False,indent=2)+'\n').encode()
    for path,blob in resources.items():
        # Merge shared global tags/atlas with the existing independent 2i.
        current=ROOT/'src/main/resources'/path
        if current.exists():
            if current.read_bytes()!=blob:blob=merge_json(current.read_bytes(),blob,path)
            current.write_bytes(blob)
        else:
            dest=DEST/'resources'/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(blob)
    # One source-built JAR owns the original companion identities. Keeping metadata
    # avoids breaking existing items, entities, blocks, NBT, recipes and resources.
    toml=ROOT/'src/main/resources/META-INF/neoforge.mods.toml'
    text=toml.read_text('utf-8-sig')
    text+='\n# Source-integrated companions; do not install their old standalone JARs.\n'
    selected=[r for r in rows if r['selected']]
    selected.append(dict(modId='piston',version='1.21.1-1.0.0',displayName='活塞的玩偶',
                         metadata={'mixins':[{'config':'piston.mixins.json'}],'dependencies':{'piston':[]}}))
    for r in selected:
        mod=r['modId']
        text+='\n[[mods]]\nmodId='+json.dumps(mod)+'\nversion='+json.dumps(r['version'])+'\ndisplayName='+json.dumps(r['displayName'],ensure_ascii=False)+'\nauthors="2i学习"\ndescription="Source-integrated companion; preserves original registry identities."\n'
        for mix in r['metadata'].get('mixins',[]):text+='\n[[mixins]]\nconfig='+json.dumps(mix['config'])+'\n'
        for dep in r['metadata'].get('dependencies',{}).get(mod,[]):
            text+=f'\n[[dependencies.{mod}]]\n'
            for k,v in dep.items():text+=k+'='+json.dumps(v,ensure_ascii=False)+'\n'
        if mod=='piston':
            for dep,vr in (('minecraft','[1.21.1]'),('neoforge','[21.1.249,)'),('curios','[9.5.1,)')):
                text+=f'\n[[dependencies.piston]]\nmodId="{dep}"\ntype="{"optional" if dep=="curios" else "required"}"\nversionRange="{vr}"\nordering="NONE"\nside="BOTH"\n'
    toml.write_text(text,'utf-8')
    (EVIDENCE/'source-import-files.json').write_text(json.dumps(files,ensure_ascii=False,indent=2),'utf-8')
    return dict(javaFiles=len(list((DEST/'java').rglob('*.java'))),resources=len(resources),
                companionModIds=[r['modId'] for r in selected],
                pistonSkinSha256=digest(skin.read_bytes()),testClassesImported=0)

if __name__=='__main__':
    raise SystemExit('RETIRED 2026-11-02: historical importer selects obsolete exports. Do not run; src/bundled is the reviewed release source of truth.')
    ap=argparse.ArgumentParser();ap.add_argument('--apply',action='store_true');args=ap.parse_args()
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    rows=inventory();report=dict(candidates=rows,selectedCount=sum(r['selected'] for r in rows))
    if args.apply:report['import']=vendor(rows)
    (EVIDENCE/'inventory.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='candidates'},ensure_ascii=False))
