# 2i Doll 1.3.0 — release validation

Date: 2026-11-02. Minecraft 1.21.1, NeoForge 21.1.249, Java 21.

The production artifact is `two_i_combat_doll-1.21.1-1.3.0.jar` (21,627,665 bytes).

SHA256: `e4a400bcf7e1020e739724e8f970a8c79a4dc6d5a35f90d945e1ff606ca48e38`

The public name is **2i Doll / 2i玩偶**; the in-game author is **2i学习**.
The existing `two_i_combat_doll` registry namespace is retained for saved games.

## Executed release checks

| Environment | Result | Coverage |
| --- | --- | --- |
| Minimal dedicated server | Passed | No Stretcher, Useless Mod, DollChronicle or Curios; ownership, leaf ledger, full inventory, saved recovery, bundled features and native save/stop |
| Same world, new JVM | Passed | Separate Java process; saved ownership, memory, one-time rewards and legacy `chuanshou` chunk/item/block-entity aliases |
| Minimal client | Passed | UI entity/cursor rendering, six chapters, garden entry/show/return, reconnects, recipe manager, creative catalog, duel and new companions |
| Curios 9.5.1 | Passed | Actual equip/unequip, arbitrary and restricted slot fixtures, owner/UUID preservation and the five-minute companionship timer |
| DollChronicle 0.3.0 | Passed | Optional progression gate against the pinned production JAR; Curios/AE2/GuideME dependencies present |
| Production JAR dedicated server | Passed | Only the audited production JAR supplies the mod; no loose source output or test flags; fresh world reaches Done, saves and stops normally |
| Exported public source build | Passed | No local `libs`, verification directory or external source project; `build --no-build-cache --rerun-tasks` executes all production tasks and reproduces the identical JAR |

The first dedicated-server compilation attempt exposed a missing helper in the
private test fixture. That attempt and its diagnostics were retained; it did not
start Minecraft. The corrected fixture passed the subsequent server and restart
runs. A later integration-task change to a private duel test remains preserved
in the local working tree; the published test version is the version used by
the completed release regressions.

This round checks 15 internal mod IDs, 18 recipes, 16 mixin configurations and
858 production classes. The JAR contains no private test classes/data packs,
retired `lonya`/`lonya_plush` implementation, old `chuanshou` implementation, or
nested standalone companion JARs. Old `chuanshou` saves use official registry
aliases to the new `chuanshuo` registrations.

The 2i, Longhuan and Cangsheng combat/rendering implementations and their
specific resources are unchanged from the previously validated standalone
baseline. Their earlier dedicated combat/visual regressions and Stretcher/
Useless Mod integrations are inherited evidence, rather than new executions
of those entire suites. Automated fixtures use real Minecraft runtime APIs,
including test players; they do not establish remote multiplayer load capacity
or guarantee every graphics driver and optional-mod combination.

## Evidence and installation

Detailed immutable logs, screenshots, worlds, input hashes, runtime reports and
failed attempts are retained locally under `verification/release-20261102` and
`verification/standalone-20261102`. They are excluded from this source repository
because they include generated worlds and large runtime files.

Install the same production JAR on client and server. Internal companion IDs
are bundled content, not additional required downloads. Do not also install
standalone JARs for those same companions. Back up existing worlds before
upgrading, especially worlds containing the removed Lonya registrations; see
the current usage and migration instructions.

The source code license does not override the separate asset terms. The
adapted tail geometry, CC0 combat sounds, Longhuan dance track and Chuanshuo
laugh recording retain their individual notices and source attributions in
`THIRD_PARTY_NOTICES.md` and the packaged credits.
