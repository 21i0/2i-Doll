"""Archive the superseded Lonya export and update the active source receipts.

This only touches the merged project and the Chronicle test catalogue. Original
user exports under DollSkinBlinkTool/output/mods are never modified. No build or
game process is launched. Re-running after completion performs validation only.
"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib, importlib.util, json, re, shutil, tomllib

ROOT = Path(__file__).resolve().parents[1]
CHRONICLE = ROOT.parent / 'DollChronicle'
EVIDENCE = ROOT / 'verification/bundled-dolls-20261031'
CATALOG = CHRONICLE / 'src/testmod/resources/com/twoi/chronicle/test/progression/bundled-doll-catalog.json'
ARCHIVE = ROOT / 'verification' / ('lonya-dedup-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
META = ROOT / 'src/main/resources/META-INF/neoforge.mods.toml'
RETIRED = [ROOT / 'src/bundled/java/com/customdoll/lonya',
           ROOT / 'src/bundled/resources/assets/lonya',
           ROOT / 'src/bundled/resources/data/lonya',
           ROOT / 'src/bundled/resources/lonya.mixins.json']

def checked(path, root):
    resolved = path.resolve()
    if resolved == root.resolve() or not resolved.is_relative_to(root.resolve()):
        raise RuntimeError('Archive target escapes explicit project root: ' + str(path))
    return resolved

def backup(path, root):
    checked(path, root)
    destination = ARCHIVE / ('TwoICombatDoll' if root == ROOT else 'DollChronicle') / path.relative_to(root)
    destination.parent.mkdir(parents=True, exist_ok=True)
    if path.is_file(): shutil.copy2(path, destination)

def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def run():
    initial = tomllib.loads(META.read_text('utf-8-sig'))
    if any(row['modId'] == 'lonya' for row in initial['mods']):
        ARCHIVE.mkdir(parents=True, exist_ok=False)
        # Keep every changed metadata/receipt/shared resource before migration.
        backup(META, ROOT); backup(CATALOG, CHRONICLE)
        for name in ('inventory.json', 'source-import-files.json', 'production-jar-audit.json'):
            backup(EVIDENCE / name, ROOT)
        for base in (ROOT / 'src/main/resources', ROOT / 'src/bundled/resources'):
            for path in base.rglob('*.json'):
                if any(path == item or path.is_relative_to(item) for item in RETIRED): continue
                if 'lonya:' in path.read_text('utf-8-sig'): backup(path, ROOT)
        for source in RETIRED:
            checked(source, ROOT)
            target = ARCHIVE / 'TwoICombatDoll' / source.relative_to(ROOT)
            checked(target, ARCHIVE)
            if source.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(source), str(target))
        text = META.read_text('utf-8-sig')
        text, removed = re.subn(r'\n\[\[mods\]\]\s*\nmodId="lonya".*?(?=\n\[\[mods\]\])', '', text, flags=re.S)
        if removed != 1: raise RuntimeError('Expected one isolated Lonya metadata block')
        META.write_text(text, encoding='utf-8')
        for base in (ROOT / 'src/main/resources', ROOT / 'src/bundled/resources'):
            for path in base.rglob('*.json'):
                text = path.read_text('utf-8-sig')
                if 'lonya:' not in text: continue
                value = json.loads(text)
                if 'sources' in value:
                    value['sources'] = [x for x in value['sources'] if not x.get('resource', '').startswith('lonya:')]
                elif 'values' in value:
                    value['values'] = [x for x in value['values'] if not (isinstance(x, str) and x.startswith('lonya:'))]
                else: raise RuntimeError('Unexpected Lonya reference in active JSON: ' + str(path))
                write_json(path, value)
        catalog = json.loads(CATALOG.read_text('utf-8-sig'))
        catalog['mods'] = [row for row in catalog['mods'] if row['mod_id'] != 'lonya']
        catalog['excluded_superseded_exports'] = [{'mod_id': 'lonya', 'replaced_by': 'lonya_plush',
                                                  'reason': 'Same character and exact skin; keep the newer story edition only'}]
        write_json(CATALOG, catalog)
        inventory = json.loads((EVIDENCE / 'inventory.json').read_text('utf-8'))
        for row in inventory['candidates']:
            if row['modId'] == 'lonya':
                row['selected'] = False; row['supersededBy'] = 'lonya_plush'
                row['reason'] = 'superseded same-character export by newer lonya_plush; exact original skin matches'
        inventory['selectedCount'] = sum(row['selected'] for row in inventory['candidates'])
        inventory['import']['companionModIds'] = [mod for mod in inventory['import']['companionModIds'] if mod != 'lonya']
        inventory['import']['javaFiles'] = len(list((ROOT / 'src/bundled/java').rglob('*.java')))
        files = json.loads((EVIDENCE / 'source-import-files.json').read_text('utf-8'))
        files = [row for row in files if row['modId'] != 'lonya']
        resource_paths = {row['path'] for row in files if row['kind'] == 'resources'}
        resource_paths.update(path.relative_to(ROOT / 'src/bundled/resources').as_posix()
                              for path in (ROOT / 'src/bundled/resources').rglob('*') if path.is_file())
        inventory['import']['resources'] = len(resource_paths)
        inventory['deduplication'] = {'retiredModId': 'lonya', 'retainedModId': 'lonya_plush',
                                      'restoreArchive': str(ARCHIVE), 'originalExportsUnchanged': True}
        write_json(EVIDENCE / 'inventory.json', inventory)
        write_json(EVIDENCE / 'source-import-files.json', files)

    spec = importlib.util.spec_from_file_location('companion_inventory', ROOT / 'tools/bundle-companion-sources.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    expected = module.inventory()
    selected = {row['modId'] for row in expected if row['selected']}
    recorded = json.loads((EVIDENCE / 'inventory.json').read_text('utf-8'))
    assert selected == {row['modId'] for row in recorded['candidates'] if row['selected']}
    assert len(selected) == 12 and 'lonya' not in selected and 'lonya_plush' in selected
    mods = tomllib.loads(META.read_text('utf-8-sig'))['mods']
    assert len(mods) == 14 and {row['modId'] for row in mods} == selected | {'piston', 'two_i_combat_doll'}
    assert not any(path.exists() for path in RETIRED)
    catalog = json.loads(CATALOG.read_text('utf-8-sig'))
    assert len(catalog['mods']) == 14 and {row['mod_id'] for row in catalog['mods']} == {row['modId'] for row in mods}
    for base in (ROOT / 'src/main/resources', ROOT / 'src/bundled/resources'):
        for path in base.rglob('*.json'):
            assert 'lonya:' not in path.read_text('utf-8-sig'), path
    print(json.dumps({'result': 'STATIC LONYA DEDUP PASSED', 'selectedExports': len(selected),
                      'companionModIds': len(mods)-1, 'totalModIds': len(mods),
                      'runtimeTested': False, 'archive': recorded.get('deduplication', {}).get('restoreArchive')}, ensure_ascii=False))

if __name__ == '__main__':
    raise SystemExit('RETIRED 2026-11-02: this historical migration retains Lonya and edits another project. Both Lonya identities are now removed; do not run.')
