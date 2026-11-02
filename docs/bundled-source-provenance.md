# Integrated companion provenance

These companions are compiled from the source projects below into the single
`two_i_combat_doll` JAR. Their independent binary/source JARs are provenance
references; no independent doll JAR is nested, loaded or redistributed inside
the combined JAR. Existing registry identities are preserved except for the
explicitly replaced old `chuanshou` A export; the final `chuanshuo` B export is
the sole active implementation of that character.

The local absolute paths identify the reviewed source snapshots on the author's
build machine. They are provenance records, not runtime requirements.

| Role | Reviewed source project | Version |
| --- | --- | --- |
| Shaniao | `D:\AI\sources\DollSkinBlinkTool\output\mods\shaniao-1.21.1-1.0.0-neoforge-B-20261102-152637\project` | `1.21.1-1.0.0` |
| Chuanshuo | `D:\AI\sources\DollSkinBlinkTool\output\mods\chuanshuo-1.21.1-1.0.1-B-laugh-20261102\project` | `1.21.1-1.0.1` |
| Luomu | `D:\AI\sources\DollSkinBlinkTool\output\mods\luomu-1.21.1-1.0.1-neoforge-B-20261102\project` | `1.21.1-1.0.1` |

Each reviewed binary and source JAR is under its source project's `build\libs`.
The final Chuanshuo binary also matches `D:\AI\chuanshuo-1.21.1-1.0.1.jar`.

| File | Bytes | SHA256 |
| --- | ---: | --- |
| `shaniao-1.21.1-1.0.0.jar` | 123976 | `27e9c3bb34d7916dc5362518045226d358739c95c7289c2a41cbd0185362c2a1` |
| `shaniao-1.21.1-1.0.0-sources.jar` | 73421 | `dc45a33e2ef06ff6324f05e2fe14477a0645edbffb56bc4e8b291b9f5317bc3c` |
| `chuanshuo-1.21.1-1.0.1.jar` | 235679 | `790325aecc601ce7142b730bf6389dce9e6db012eb54404b60cb4b6cbac89c78` |
| `chuanshuo-1.21.1-1.0.1-sources.jar` | 174061 | `5c9ae7b3f955d7bf59798e60ced4408acc0fefa1dd9c729f78c9bc63870e20c0` |
| `luomu-1.21.1-1.0.1.jar` | 122879 | `1174cd389a0aca26a85cb802d7fd9769c3ef176fa96ba7c5011578fd4b94e77d` |
| `luomu-1.21.1-1.0.1-sources.jar` | 69299 | `576c87b19521fc89a96b1151e6cb81358581ef69c14a41c48d4eabf1246d342d` |

The three source LICENSE files declare MIT and have the same exact SHA256:
`83239577d211d69c55a22cea845c391aa874e10ff078713e71f3c0af7e876783`.
Their complete original bytes, including the supplied copyright-line encoding,
are preserved in `assets/<role>/SOURCE_LICENSE.txt`. This documents the source
code license without inventing a license for someone else's artwork or audio.
The skins/custom artwork were supplied with the character projects and retain
their original ownership; the code license does not transfer that ownership.

Integration preserves each final source's skin, blink frames, umbrella palette,
client model/render logic and B-companion behavior. The shared umbrella lookup
delegates to the combined mod's cache. The native survival recipes use black,
brown and lime wool for Shaniao, Chuanshuo and Luomu respectively to avoid recipe
collisions. Shared metadata, atlas and tags include only the active identities.
The old `chuanshou` item, block and BE names resolve through official NeoForge
registry aliases to the new B objects, without restoring old code or appearance.
Both retired Lonya namespaces remain absent from active registrations/resources.

Chuanshuo's final source adds a 12-second owner-triggered laugh performance.
`assets/chuanshuo/sounds/chuanshuo_laugh.ogg` comes from the first 12.00 seconds
of the user-provided `video_奶龙大笑纯享版一分钟_0.mp4`, converted to mono
44.1 kHz Vorbis with 20 ms end fades. Exact audio SHA256:
`25259e8f6241250464e7f37ce92ee94eac7dfc73e5488966cc0c470c29994ae0`.
This is third-party recorded audio, not original synthesis and not declared CC0.
The provided source package includes no separate license for that recording;
MIT applies to code and does not grant third-party audio rights. The packaged
`assets/chuanshuo/SOUND_CREDITS.txt` retains the source attribution and this
boundary. The entire source video is not included.

Longhuan's pre-existing root `CREDITS.txt` is retained unchanged. Its dance music
is credited to https://www.bilibili.com/video/BV1TLay6XEiS/ , uploader 你个人机cc,
with the video's music credit @活跃黑江乐. It is also separate third-party audio,
outside the MIT code license. The astral combat declaration remains an adaptation
of Kenney CC0 samples, and the original procedural combat cues remain distinct.
No JOJO or combat-effect reference-video audio is used in those combat cues.

The existing tail CC-BY-NC-SA-4.0, Kenney CC0 and Poly Haven CC0 notices remain
in `THIRD_PARTY_NOTICES.md` and the corresponding packaged credit/license files.
This document does not replace or broaden any of those licenses.
