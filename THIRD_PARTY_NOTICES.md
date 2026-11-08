# Asset licenses

## Canmeng companion integration (2026-11-04)

The source-integrated `canmeng` companion retains its original
1.21.1-1.0.0 registry identity and author credit **2i学习**. Its generated
Java sources are MIT licensed; the original source LICENSE and byte manifest
are preserved under `verification/canmeng-release-20261104/source-import/`.
The owner-supplied skin and blink artwork retain the artwork terms below.
Existing shared template pat-response cues are retained, not advertised as
new character-specific recordings. No reference-video audio was imported.
Integration changes only the shared umbrella-query cache and an unambiguous
survival recipe (black wool and a feather).

## Magua, Shuangshi and Qiaotou companion integration (2026-11-04)

The source-integrated `magua`, `shuangshi` and `qiaotou` companions retain
their original 1.21.1-1.0.0 registry identities and the author credit **2i学习**.
Their generated Java sources declare MIT; original source LICENSE files and
byte manifests are preserved under
`verification/three-dolls-release-20261104/source-import/source-baseline/`.
Skins and blink images were supplied by the project owner and retain the
owner-supplied-artwork terms below. No reference-video audio was imported.
The existing generated pat-response sounds are retained; they are shared
template cues and are not advertised as new character-specific recordings.
Integration changes only shared umbrella-query caching, unambiguous survival
recipes and Qiaotou's requested Chinese display name, **俏头的玩偶**.

Original code, procedural magic artwork and original synthesized sound effects are MIT licensed.
The 2i skin and custom artwork were supplied by the project owner.
The code license does not relicense owner-supplied skins or the third-party
recordings identified below. A bundled character's source LICENSE is not a
grant of rights in a recording owned by someone else.

The adapted tail geometry at `assets/two_i_combat_doll/models/entity/two_i_tail.json`
uses Winefox geometry from Touhou Little Maid by tartaric_acid and contributors
(revision `8a3ac5e9eacf0c6d63adf8832949f3ce81484fe6`). It remains under
CC-BY-NC-SA-4.0. This asset is not covered by the code's MIT license.
Conversion, proportions, skinning, 2i markings, bow and secondary-motion changes
are described in `TWO_I_TAIL_CREDITS.txt`; the full asset license is included in
`TWO_I_TAIL_LICENSE.txt`.

## Astral sound credits

`assets/two_i_combat_doll/sounds/time_stop.ogg` is an adaptation of two zero-cost
Kenney CC0-1.0 source recordings with original sub/clock/reflection processing:

- Sci-Fi Sounds: `Audio/lowFrequency_explosion_000.ogg`
  — https://kenney.nl/assets/sci-fi-sounds
- Impact Sounds: `Audio/impactBell_heavy_002.ogg`
  — https://kenney.nl/assets/impact-sounds

Author/distributor: Kenney. License: Creative Commons Zero 1.0,
https://creativecommons.org/publicdomain/zero/1.0/ . Each downloaded package's
License.txt explicitly allows personal, educational and commercial projects.
Credit is optional under the source license; it is retained in the packaged
`assets/two_i_combat_doll/SOUND_CREDITS.txt`.

The selected B declaration combines filtered/gain-adjusted CC0 sources, a
lower-pitch bell, an original short sub hit, quiet reflection taps and sparse
clock ticks. It is not represented as an entirely original recording. The four
separate `astral_charge`, `astral_tick`, `astral_reverse` and `star_verdict`
effects remain original procedural synthesis under the project's MIT license.

Historical rejected candidates and source evidence are outside packaged game
resources. No audio extracted from the combat-effect reference video or JOJO
is used in these astral combat sound effects. The complete CC0 archives are not
bundled; only the resulting adapted declaration and credits are shipped.

## Source-integrated companions and third-party performance audio

The Shaniao 1.21.1-1.0.0, Chuanshuo 1.21.1-1.0.1 and Luomu
1.21.1-1.0.1 companion implementations are integrated as source and resources,
not nested independent mod JARs. Their supplied source LICENSE files declare
MIT. The complete original notices are preserved without byte changes at
`assets/shaniao/SOURCE_LICENSE.txt`, `assets/chuanshuo/SOURCE_LICENSE.txt` and
`assets/luomu/SOURCE_LICENSE.txt`. The supplied skins and custom artwork retain
their original ownership. Source project paths, binary/source JAR SHA256 values
and integration changes are listed in the repository document
`docs/bundled-source-provenance.md`.

Chuanshuo's `assets/chuanshuo/sounds/chuanshuo_laugh.ogg` contains the first
12.00 seconds of the user-provided `video_奶龙大笑纯享版一分钟_0.mp4`, converted
to mono 44.1 kHz Ogg Vorbis with 20 ms fades at each end. It is a third-party
recording, not original synthesis, not a JOJO sample and not identified as CC0.
The source package does not provide a separate third-party audio license; the
project's MIT code license grants no rights in that recording. Its attribution
and exact audio SHA256 are retained in `assets/chuanshuo/SOUND_CREDITS.txt`.

Longhuan's existing dance recording also remains third-party audio. The original
root `CREDITS.txt` is preserved, including the source video
https://www.bilibili.com/video/BV1TLay6XEiS/ , uploader 你个人机cc and the
video's credited music author @活跃黑江乐. This recording is not relicensed by
the project's MIT code license. These two companion performance recordings are
separate from the original and Kenney CC0 combat sound effects described here.

## Cangsheng mountain guardian and landscape

The guardian uses an original editable voxel rig in
`assets/two_i_combat_doll/models/entity/cangsheng_rig.json`, rendered by
`CangshengAxolotlModel`, and an original 128px
`textures/entity/cangsheng_axolotl_skin.png`. The milk-white stepped head,
large violet pixel eyes and highlights, pink external-gill branches, jointed
short feet, jaw and articulated five-part fin tail were authored for this
project. The current renderer does not call the vanilla `AxolotlModel` or
reference the Minecraft Lucy skin. Historical experiments and reference files
outside production resources are not the source of this final rig or skin.

The guardian's 512px `textures/entity/cangsheng_guardian_atlas.png` and shallow
moss-and-flower back garden are original procedural artwork. The final body
has no tall rock peak, crystal spire, tree or waterfall attached to its back;
the front riding seat stays clear. Thirty separately animated plume tips
form the pink branching gill fans. This same rig and these materials appear
as the pocket pet, XiaoXiao mount, deployed titan and scene guardian. The body
rig, skin, back atlas, meshes and animation code use the project's MIT license.

The user's images and videos guide body proportions, composition, colour and
movement only; their pixels, models, textures and audio are not packaged as
game assets. No Terraria mod asset was imported. The current daytime dream
realm is original editable Blender geometry and procedural material work:
terraced pearl cliffs, pink flowering trees, blue waterfalls, violet-roofed
castles, arcades and floating islands. Its three-depth panorama, runtime stage
JSON and 512px stage atlas use no photograph, external mesh or external texture.
The final J2 panorama is authored by `tools/blender/dream_realm_panorama_j.py`;
the K runtime stage and atlas are authored by
`tools/blender/dream_realm_stage_k.py`. The reviewed integration script,
`tools/combine-dream-realm-j2-k.py`, copies those completed outputs without
claiming that one authoring scene generated all three assets. Separate source
`.blend` files, script/scene/output hashes and byte-identical integration
outputs are recorded in `source_bakes` within
`verification/axolotl-20261027/dream-realm-bake-final.json`. Actual Blender
sources, review renders and previous rejected bakes remain under
`verification`; they are not packaged in the game. This new daytime work does
not use the CC0 photograph credited below; that source remains applicable to
the separate crimson scene.

## Cangsheng original sound credits

`cangsheng_awaken.ogg`, `cangsheng_roar.ogg`, `cangsheng_impact.ogg` and
`cangsheng_flute.ogg` are original deterministic synthesis under the project's
MIT license. No source recording, external sample, video audio or paid asset is
used. The roar is an original low guardian vocal pulse with chest resonance and
finite throat excitation, not a recording of real axolotl behaviour.

The current awaken and roar use
`tools/synth/original-cangsheng-audio-v2.py`; their synthesis recipes, fixed
seeds, listening WAVs and encoded-asset audit are retained in
`verification/axolotl-20261027/audio-v2/audio-audit-v2.json`. Awaken combines a
finite low body hit, stable low stone modes and a quiet pearlescent decay.
The unchanged impact and flute retain `tools/synth/original-cangsheng-audio.py`
and `verification/axolotl-20261027/audio/audio-audit.json`. The earlier four-cue
OGGs, generator and original audit remain in that audio directory's
`original-v1-history/` folder. Packaged credits are
`assets/two_i_combat_doll/CANGSHENG_SOUND_CREDITS.txt`; the main
`assets/two_i_combat_doll/SOUND_CREDITS.txt` points to the same provenance.
This new original sound set does not alter or replace the separate Kenney CC0
declaration credit above.

## Landscape stone microstructure (CC0)

The separate crimson mountain bake uses **Rock Boulder Cracked**
from **Poly Haven**: photography by **Dimitrios Savva**, processing by
**Dario Barresi**, licensed **CC0 1.0 Universal**.
Source: https://polyhaven.com/a/rock_boulder_cracked
License statement: https://polyhaven.com/license
Legal text: https://creativecommons.org/publicdomain/zero/1.0/

The source photograph supplies only stone microstructure. Terrain geometry,
composition, palette, lighting and animation are authored for this project.
The photograph is not included in the JAR; the derived crimson atlas is.
Official metadata, the source file and hashes are retained in verification.
See the packaged `assets/two_i_combat_doll/LANDSCAPE_CREDITS.txt`.
The guardian's own 512px material atlas, pocket/mount model details, current
daytime Blender panorama and runtime dream-stage material atlas remain original
procedural artwork, without this photograph as input. Historical Cangsheng
photo-assisted bakes are retained outside game resources as rejected evidence.
