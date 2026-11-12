# Still Life FTF edition patch

Turns **your own** Still Life 0.3 jar into the Still Life FTF edition 0.3.5-ftf, which the bridge expects.

Still Life is All Rights Reserved: this folder contains no Still Life files, only scripts that edit the jar you downloaded,
on your computer. Please don't share the resulting jar.

1. Download **Still Life 0.3 for NeoForge 1.21.1** from [Modrinth](https://modrinth.com/datapack/still-life).
2. With Python 3.8 or newer:
   ```
   python3 make_ftf_edition.py path/to/still_life-0.3-1.21.1.jar
   ```
   It writes `still_life-0.3.5-ftf-1.21.1.jar` next to the input (or pass an output path as the second argument).
3. Put that jar in `mods/` instead of the normal Still Life jar, together with FreeTerraForged, VanillaBackport, Platform
   and the bridge.

| File | What it does |
|---|---|
| `make_ftf_edition.py` | The whole conversion: removes Still Life's Lithosphere terrain shaping, points its noise router back at Minecraft's own functions, updates the metadata, then runs `patch_sl.py`. |
| `patch_sl.py` | The 0.3.1-0.3.5 fixes on an extracted FTF edition (surface rules, snow, salt lakes and aspen swamps at any height, salt-crust rings). |

What each change does and why: [`docs/still-life-ftf-edition.md`](../docs/still-life-ftf-edition.md).
The output was checked to be identical, file for file, to the 0.3.5-ftf jar used in testing.
