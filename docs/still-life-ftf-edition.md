# The Still Life FTF edition

`still-life-patch/make_ftf_edition.py` turns the Still Life 0.3 jar (NeoForge, from Modrinth) into
`still_life-0.3.6-ftf`. Still Life is All Rights Reserved, so neither the original nor the result is in this repository;
the script edits your own download on your own computer. Its output was checked to be identical, file for file, to the
0.3.6-ftf jar used in testing.

The edition works with FreeTerraForged 1.0.0027-1.0.0032 and bridge 5.x: nothing FTF changed in those versions touches what
it patches (checked by generating salt lakes, wetlands, rivers and coasts with it, with no datapack or tag errors).
0.3.5-ftf still works with bridge 5.1; it only lacks the river beds.

The edition has two layers.

## 1. The base edition (0.3-ftf): terrain shaping removed

Still Life ships two things in one: a **biome layout** (biomes, their features and surfaces, the climate table that says
which biome goes where) and **terrain shaping** that plugs into Lithosphere (density functions and a noise router built on
Lithosphere's). FreeTerraForged generates the terrain itself and replaces Minecraft's noise router with its own, so the
terrain half has nothing to do except break: it references Lithosphere functions that are not installed.

| Change | Why |
|---|---|
| Removed `data/minecraft/worldgen/density_function/` | Still Life's overrides of Minecraft's terrain functions, built on Lithosphere's. FTF makes the terrain. |
| Removed `data/still_life/worldgen/density_function/` | Still Life's own terrain functions (same reason). |
| Removed `data/still_life/worldgen/configured_carver/` | Carvers tuned for Lithosphere's terrain and underground rivers. |
| Removed `noise_settings/overworld_old.json` and `META-INF/mods.toml` | An unused older copy, and old Forge metadata. |
| `noise_settings/overworld.json`: the noise-router entries that referenced Lithosphere or Still Life functions (`final_density`, `initial_density_without_jaggedness`, `depth`, `barrier`, `fluid_level_floodedness`, `fluid_level_spread`, `temperature`, `vegetation`) set back to Minecraft 1.21.1's own | So the file loads without Lithosphere. FTF supplies its own terrain and climate anyway. |
| Metadata: name "Still Life (FTF edition)", version `-ftf`, requires FreeTerraForged and VanillaBackport | The jar replaces the normal Still Life jar and needs both. VanillaBackport provides the 1.21.4+ content Still Life 0.3 uses (pale oak, bushes, leaf litter, cactus flowers, resin). |

Everything else (biomes, the climate table, surface rules, features, structures) is kept as Still Life ships it. Placing
the biomes correctly on FTF terrain is the bridge's job (`docs/bridge.md`).

## 2. The fixes (0.3.1-ftf to 0.3.6-ftf): `patch_sl.py`

Found while testing on FTF terrain. Each change is applied to Still Life's own files by the script, not copied in.

### Granite and diorite only where rock is exposed (0.3.3)

Still Life paints whole mountain surfaces with granite and diorite bands. On FTF's tall mountains this made pink and
white mountains (diorite reads as snow from far away). Now the top three blocks are stone and the bands only start below,
so they show where FTF's erosion strips slopes down to the rock (cliffs, gullies, bare high rock) as streaks. Diorite keeps
half of its band. Ores and vanilla's granite/diorite blobs are features and are not affected.

### VanillaBackport's surfaces for its biomes (0.3.x)

VanillaBackport adds its biomes' surfaces (for example the Dappled Forest's) through `NoiseBasedChunkGenerator.buildSurface`,
which FTF's generator does not use. They are added to Still Life's surface rule instead, written with 2D noise so FTF and
Worldgen Accelerator's GPU surface path accept them. The Sulfur Caves' rock is placed by the bridge instead (FTF skips
surface rules deep in rock).

### Big snow patches also on rock (0.3.x)

Still Life's large snow patches were limited to soil. On FTF's high mountains most of the top is rock, so peaks above the
snow line had almost no snow. The patches now also cover rock.

### Salt lakes and aspen swamps at any height (0.3.5)

Still Life builds these for sea level, because Lithosphere drops its wetland climate band there: the salt crust only exists
at Y 62-64 and the lake bed at Y 40-61; the aspen swamp's mud only at Y 62. On FTF terrain they sit at any height (the
bridge places them on flat dry land), where they looked like plain desert or forest.

- Salt lake: on dry ground, a white calcite crust dithered with clay (as Still Life's Y 62 band looks) at any height; clay
  under water.
- Aspen swamp: patches of mud on dry ground at any height (the bridge adds pools of standing water, and sheets of shallow
  water in salt lakes).
- The `disk_calcite_salt_lake` feature (rings of calcite in shallow water) no longer only at Y 60-62.

### River beds by water depth (0.3.6)

Still Life picks the bed of sea-level rivers (Y 52-62) from one noise, `still_life:river_sediments`, whose features are
about 500 blocks across: mud, packed mud, gravel or clay by threshold. On Lithosphere's narrow rivers that reads as
variety; FTF's rivers are wide and long, so a river ran one material for hundreds of blocks and then switched across its
whole width along a nearly straight line, which looked artificial. Rivers above Y 63 never used this rule.

Now the bed follows the water depth, read with the surface rule's water condition: in the shallows (1 block deep) mud and
packed mud, at 2 blocks a mix with gravel, in the channel (3 or more) mostly gravel with some clay. Minecraft's
`surface` noise (~64-block features) mixes them. Arid rivers (desert, Mediterranean and savanna) use their own materials
the same way: coarse dirt and packed mud in the shallows, packed mud and clay in the channel. Tropical rivers, which are
almost all mud, are unchanged. On the test river: shallows 47% mud / 37% packed mud, channel 72% gravel / 26% clay.

The jar's Fabric and Quilt metadata now carry the version too: some launchers (Prism) read it from there and showed 0.3.5-ftf.

![river beds](images/river-beds.png)
*A sea-level river on FTF (temperate river, 384 x 224 blocks, bed blocks under the water; dark = mud, tan = packed mud,
grey = gravel, blue-grey = clay). Left: 0.3.5-ftf; right: 0.3.6-ftf.*

### VanillaBackport biomes in the climate table (0.3.1, moved to the bridge in 0.3.2)

0.3.1 carved the Pale Garden, Dappled Forest and Sulfur Caves into Still Life's climate table. From 0.3.2 the bridge does
it at runtime with an adjustable amount per world, so the table is left as Still Life ships it (`patch_sl.py --no-carve`,
which `make_ftf_edition.py` uses).
