"""Editable Blender studies of the exact Java STAFF and BOOK meshes.

Run with the approved portable Blender, not system Python:
  blender --background --python tools/blender/arcana_weapons_study.py -- \
      --runtime-dir build/<verified-client>/screenshots/model-exports \
      --output-dir verification/axolotl-20261027/blender/arcana-weapons

The shared importer owns coordinate/UV conversion, packed materials, editable
mesh objects, captured poses and turntable cameras. This wrapper intentionally
does not re-create a similar-looking staff or book from a separate recipe.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import sys

MODELS = ('astral_edict', 'scarlet_codex')
ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path,
                        default=ROOT/'verification/axolotl-20261027/blender/arcana-weapons')
    parser.add_argument('--no-render', action='store_true')
    args = parser.parse_args(argv)
    runtime = args.runtime_dir.resolve()
    output = args.output_dir.resolve()
    manifest_path = runtime/'manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    frames = manifest['frames']
    selected = [frame for frame in frames if frame['model'] in MODELS]
    absent = [model for model in MODELS if not any(frame['model'] == model for frame in selected)]
    if absent:
        raise ValueError(f'Actual runtime exporter is missing required model(s): {absent}')
    for frame in selected:
        if not (runtime/frame['file']).is_file():
            raise FileNotFoundError(runtime/frame['file'])
    importer_path = Path(__file__).with_name('import_runtime_mesh.py')
    spec = importlib.util.spec_from_file_location('two_i_runtime_mesh_importer', importer_path)
    importer = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = importer
    spec.loader.exec_module(importer)
    importer.build_study(runtime, output, models=list(MODELS), render=not args.no_render)

    output.mkdir(parents=True, exist_ok=True)
    production = [
        'src/main/java/com/twoi/combatdoll/client/AstralEdictModel.java',
        'src/main/java/com/twoi/combatdoll/client/ArcanaWeaponRenderer.java',
        'src/main/java/com/twoi/combatdoll/client/TwoIArcanaRenderer.java',
        'src/main/resources/assets/two_i_combat_doll/textures/item/astral_edict.png',
        'src/main/resources/assets/two_i_combat_doll/textures/effect/scarlet_codex.png',
    ]
    audit = {
        'purpose': 'Editable review of exact captured runtime meshes; not a separate replacement design.',
        'manifest': str(manifest_path),
        'manifest_sha256': digest(manifest_path),
        'models': {model: [frame for frame in selected if frame['model'] == model] for model in MODELS},
        'source_sha256': {name: digest(ROOT/name) for name in production},
        'render_requested': not args.no_render,
        'review': {
            'astral_edict': 'Inspect ivory/gold material separation from both sides, layered square/diamond frame, visible nebula jewel, thin shaft, sparse ornament motion and full silhouette.',
            'scarlet_codex': 'Inspect closed red leather binding and open hinge, page block/spine, raised hex-eye/cornea depth, and front/side/back material coverage.',
            'animation': 'STAFF orbit captures use monotonic physical time. BOOK captures use the actual opening parameter. These mesh studies do not reproduce the stellar shader finale or prove first-person stability.',
        },
        'art_review_passed': False,
    }
    (output/'arcana-weapons-study.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(f'ARCANA WEAPONS STUDY WRITTEN: {output}; inspect Blender and rendered PNGs before recording visual acceptance')


if __name__ == '__main__':
    main()
