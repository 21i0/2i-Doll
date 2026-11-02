# Authoring tools

The production sources and assets are checked in under `src/main` and
`src/bundled`. A normal Java 21 / NeoForge build does not run these tools and
does not require Blender, Python, another local repository or third-party mod
JARs. Run `./gradlew build` (`gradlew.bat build` on Windows).

Blender and sound scripts preserve the project's editable procedural authoring
work. Their historical input/output paths refer to the author's workstation;
inspect and adapt paths before intentionally generating new artwork. They are
not installation steps.

Three October integration scripts are retained as historical source and their
entry points are disabled: `bundle-companion-sources.py`,
`deduplicate-lonya.py` and `audit-bundled-companions.py`. Their old inventory and
cross-project assumptions no longer describe the release. In particular,
neither `lonya` nor `lonya_plush` may be restored. Reviewed bundled source,
explicit import receipts and the current release audit take precedence.
