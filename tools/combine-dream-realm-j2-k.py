"""Freeze reviewed J2 panorama + K stage without misattributing either source.

This integration operation copies already-rendered authored assets; it does not
claim to rerender either Blender scene. install-dream-realm.py performs the
production install and preserves the preceding production resources/manifest.
"""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "verification/model3d-20261027"
DEST = BASE / "dream-realm-j2-k"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def frozen_copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if sha(target) != sha(source):
            raise RuntimeError(f"Refusing to overwrite frozen integration file {target}")
    else:
        shutil.copy2(source, target)


def source_record(label, folder, files):
    manifest_path = folder / "dream-realm-bake.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf8"))
    generator = ROOT / manifest["generator"]
    if sha(generator) != manifest["generator_sha256"]:
        raise RuntimeError(f"Authoring script changed since {label} actual bake")
    return {
        "role": label,
        "bake_directory": str(folder),
        "bake_manifest": str(manifest_path),
        "bake_manifest_sha256": sha(manifest_path),
        "generator": manifest["generator"],
        "generator_sha256": sha(generator),
        "blend": str(folder / "dream_realm.blend"),
        "blend_sha256": sha(folder / "dream_realm.blend"),
        "outputs": {name: {"source": str(folder / name), "sha256": sha(folder / name),
                            "bytes": (folder / name).stat().st_size} for name in files},
        "source_images_read": [],
        "external_textures_or_meshes": [],
    }, manifest


def main():
    stage_names = ["cangsheng_dream_materials.png", "cangsheng_dream_stage.json"]
    background_names = ["cangsheng_landscape.png"]
    stage, stage_manifest = source_record("runtime_stage_and_atlas", BASE / "dream-realm-k", stage_names)
    background, background_manifest = source_record("daytime_background_panorama", BASE / "dream-realm-j2", background_names)
    for record in [stage, background]:
        for name, output in record["outputs"].items():
            frozen_copy(Path(output["source"]), DEST / name)
    frozen_copy(Path(stage["blend"]), DEST / "dream_realm.blend")
    frozen_copy(ROOT / stage["generator"], DEST / "stage-authoring-script.py")
    frozen_copy(ROOT / background["generator"], DEST / "background-authoring-script.py")
    manifest = dict(stage_manifest)
    manifest.update({
        "origin": "Reviewed integration of original J2 Cycles daytime panorama and original K editable runtime stage/procedural atlas; independently authored sources retained",
        "integration_source": "tools/combine-dream-realm-j2-k.py",
        "integration_source_sha256": sha(Path(__file__)),
        "integration_kind": "Already-rendered J2 background + K stage/atlas; no rerender and no third-party assets",
        "blend": str(DEST / "dream_realm.blend"),
        "primary_blend_role": "Exact copy of K runtime stage authoring scene; J2 background uses its separately recorded source scene",
        "source_bakes": [stage, background],
        "background_origin": background,
        "background_counts": background_manifest["counts"],
        "coverage": background_manifest["coverage"],
        "sha256": sha(DEST / "cangsheng_landscape.png"),
        "render_contract": {
            "recommended_runtime_anchor_y": 16,
            "authoring_vertical_compression": .42,
            "compression_already_baked_into_json_and_blend": True,
            "runtime_additional_y_compression": False,
            "material_pipeline": "Unlit atlas with one runtime directional shade (.70 + .30 * dot)",
        },
        "review_passed": False,
        "installed": False,
        "review_scope": "Developer integration review; final user aesthetic approval and perfect reference reproduction are not claimed.",
    })
    target = DEST / "dream-realm-bake.json"
    content = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    if target.exists() and target.read_text(encoding="utf8") != content:
        raise RuntimeError("Refusing to overwrite changed integration manifest")
    target.write_text(content, encoding="utf8")
    print(json.dumps({"integration_directory": str(DEST), "source_bakes": [stage, background]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
