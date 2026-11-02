"""Inspect the production artifact, including retained identities and exact skins."""
raise SystemExit('RETIRED 2026-11-02: historical audit requires removed Lonya; use verification/release-20261102/audit-release.py with a reviewed resource manifest.')
from pathlib import Path
import hashlib,json,tomllib,zipfile
ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'verification/bundled-dolls-20261031'
JAR=ROOT/'build/libs/two_i_combat_doll-1.21.1-1.3.0.jar'
inventory=json.loads((EVIDENCE/'inventory.json').read_text('utf-8'))
selected=[x for x in inventory['candidates'] if x['selected']]
with zipfile.ZipFile(JAR) as jar:
    paths=set(jar.namelist());meta=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
    assert len(paths)==len(jar.namelist()),'Duplicate ZIP entry'
    mods={x['modId']:x for x in meta['mods']}
    expected={'two_i_combat_doll','piston'}|{x['modId'] for x in selected}
    assert set(mods)==expected,(set(mods),expected)
    assert len(meta['mods'])==len(mods),'Duplicate metadata modId'
    assert 'lonya' not in mods and 'lonya_plush' in mods,'Superseded Lonya export was not removed'
    obsolete=[x for x in paths if x.startswith(('com/customdoll/lonya/','assets/lonya/','data/lonya/')) or x=='lonya.mixins.json']
    assert not obsolete,obsolete
    evidence=[]
    for row in selected:
        mod=row['modId'];assert mods[mod]['version']==row['version']
        src=Path(row['project'])/'src/main/resources'
        skin=f'assets/{mod}/textures/entity/skin.png'
        assert jar.read(skin)==(src/skin).read_bytes(),f'Overwritten skin: {mod}'
        java=(ROOT/'src/bundled/java/com/customdoll'/mod/'DollMod.java')
        assert f'com/customdoll/{mod}/DollMod.class' in paths
        for mix in row['metadata'].get('mixins',[]):assert mix['config'] in paths
        evidence.append(dict(modId=mod,version=mods[mod]['version'],
                             exactOriginalSkinSha256=hashlib.sha256(jar.read(skin)).hexdigest()))
    assert jar.read('assets/piston/textures/entity/skin.png')==(EVIDENCE/'piston-material/d428bcb465b87923.png').read_bytes()
    prohibited=[x for x in paths if x.endswith('.jar') or '/checks/' in x or '/test/' in x or 'Checks' in x or x.endswith('.java')]
    assert not prohibited,prohibited
    for cls in ('DollDuelArena','DuelRules','DollDuelWonEvent'):assert f'com/twoi/combatdoll/duel/{cls}.class' in paths
    assert 'com/twoi/combatdoll/duel/client/DuelScreen.class' in paths
    atlas=json.loads(jar.read('assets/minecraft/atlases/blocks.json'))
    assert not any(x.get('resource','').startswith('lonya:') for x in atlas['sources'])
    protocol={}
    for kind in ('item','entity_type','block'):
        key=f'data/doll_protocol/tags/{kind}/umbrella_participants.json'
        protocol[kind]=json.loads(jar.read(key))['values']
        assert not any(isinstance(x,str) and x.startswith('lonya:') for x in protocol[kind]),key
    result=dict(result='PASSED',artifact=str(JAR),bytes=JAR.stat().st_size,
                sha256=hashlib.sha256(JAR.read_bytes()).hexdigest(),metadataModCount=len(mods),
                modIds=sorted(mods),sourceIntegratedCompanions=evidence,
                pistonSkinExact=True,nestedJarCount=0,testClassCount=0,javaSourceInProductionJar=0,
                blockAtlasSources=len(atlas['sources']),umbrellaParticipants=protocol,
                runtimeClientTested=False,runtimeMultiplayerTested=False)
    (EVIDENCE/'production-jar-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('sourceIntegratedCompanions','umbrellaParticipants')},ensure_ascii=False))
