# Still Life FTF edition patch

Turns **your own** Still Life 0.3 jar into the Still Life FTF edition 0.3.8-ftf, which the bridge expects.

Still Life is All Rights Reserved: this folder contains no Still Life files, only scripts that edit the jar you downloaded,
on your computer. Please don't share the resulting jar.

1. Download **Still Life 0.3 for 1.21.1** (the mod jar) from [Modrinth](https://modrinth.com/datapack/still-life). The
   same jar, and the FTF edition made from it, works on NeoForge and Fabric.
2. With Python 3.8 or newer:
   ```
   python3 make_ftf_edition.py path/to/still_life-0.3-1.21.1.jar
   ```
   It writes `still_life-0.3.8-ftf-1.21.1.jar` next to the input (or pass an output path as the second argument).
3. Put that jar in `mods/` instead of the normal Still Life jar, together with FreeTerraForged, VanillaBackport, Platform
   and the bridge.

| File | What it does |
|---|---|
| `make_ftf_edition.py` | The whole conversion: removes Still Life's Lithosphere terrain shaping, points its noise router back at Minecraft's own functions, keeps the Deep Dark underground in the climate table, updates the metadata, then runs `patch_sl.py` and `patch_terrain.py`. |
| `patch_sl.py` | The 0.3.1-0.3.8 fixes on an extracted FTF edition (surface rules, the Dappled Forest ground, snow, salt lakes and aspen swamps at any height, salt-crust rings, river beds by water depth and at any height). |
| `patch_terrain.py` | Puts the bridge's terrain filter (bridge 6.0+) on Still Life's placed features: trees off cliffs, faces and fresh talus, a ragged treeline, snowy spruces only in snowfields (plain twins elsewhere), meadows thinner on exposed ground, palm crowns left unfiltered (so palms keep them), stone_cliff without its packed mud fallback, and removes Still Life's hidden obsidian monolith easter egg (a giant pillar at 20000, 20000 and eight small ones around it: the references in every biome, the features and their block tags). Runs on a folder or a jar (`python3 patch_terrain.py in.jar out.jar`), also on add-ons; idempotent. Still Life Naturalized 1.3.1-ftf and 1.3.2-ftf already have it applied. The bridge adds the belts above the trees itself (krummholz, subalpine shrubs and ground, ledge plants: docs/bridge.md section 15). |

What each change does and why: [`docs/still-life-ftf-edition.md`](../docs/still-life-ftf-edition.md).
The output was checked against the 0.3.7-ftf jar used in testing: the same content file for file (JSON compared by value), except the Dappled Forest rule in the overworld noise settings and the version in the metadata. A plain-language guide to the whole mod: [`docs/guide.md`](../docs/guide.md).
