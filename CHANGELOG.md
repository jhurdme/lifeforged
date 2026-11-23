# Changelog

Details and reasons for every item are in [`docs/bridge.md`](docs/bridge.md) and
[`docs/still-life-ftf-edition.md`](docs/still-life-ftf-edition.md). New options are saved per world: worlds made with an
earlier version keep them off.

## Still Life x FreeTerraForged Bridge

### 5.6.0
**Mountain belts** (`mountain_cooling`, saved per world, format 15; off by default, older worlds off). In warm, dry
country Still Life's shrublands (cacti savanna, painted xeric shrubland, yucca scrubland) ran straight up to snowy painted
and snowy barren peaks: its table has no cold belt for warm, dry climates, and its peaks band turns snowy below
temperature 0.23. With `mountain_cooling` above 0 the climate the biome lookup sees gets gradually cooler from the
highlands up to the peaks band, by that much at the top, so mountains pass Still Life's montane and alpine biomes
(meadows, prairie highlands, alpine tundra, cold barren highlands) below their caps. The peaks band itself, polar caps,
cave biomes and all land below the highlands are unchanged. With 0.25 in the tester's world: shrubland next to a snow cap
on 18.6% / 7.7% of the caps' edge in its two warmest mountain regions -> 0%, the same snow cover as before, under 0.2% of
the land below Y 300 changed. Works with both `latitude` settings. Tested with FreeTerraForged 1.0.0032 and 1.0.0033 and
Still Life 0.3.6-ftf.

### 5.5.0
**No snow or sand on cave floors.** FreeTerraForged leaves many caves in the biome of the ground above them (every cave
outside the cells its `undergroundBiomeCoverage` gives cave biomes, and near the surface everywhere), and Still Life laid
that biome's ground on their floors: snow blocks in caves under snowy peaks, sand under deserts. New setting
`cave_floor_guard` (saved per world, format 14; older worlds `off`):
- `exact` (default): Still Life's ground blocks go only on ground open to the sky or under standing water, never on the
  floor of a cave, pocket or overhang. Uses a new surface rule condition, `stilllife_ftf_bridge:surface_layer`, which
  datapacks can use too.
- `vanilla`: Minecraft's own check (`above_preliminary_surface`), as Minecraft's biomes have; on FTF's terrain it still lets
  small pockets 8-25 blocks under the ground through.
- `off`: as before.
**Cave biome depth** (`column_depth`, saved per world; older worlds false): FreeTerraForged's depth number is the share of
the column below the surface; 5.0-5.4 read it as blocks, so under high ground cave biomes started up to 48 blocks deeper
than with FTF alone and Still Life's deepest cave biomes (Haunted Depths, deep Deep Dark) never appeared. Now each column
is mapped onto the Lithosphere column of the same Still Life height: cave biomes start no deeper than FTF's own, and the
deepest ones sit near bedrock under high ground.
**Config file**: every setting now shows its default and the values it accepts (`# Default: 40. Allowed: 0 to 160.`).
Works with Still Life 0.3.6-ftf as it is; no new Still Life edition needed. No measurable cost: on the test server, with the
tester's 1024-high preset, surface rules took 24.0 ms per chunk without the guard and 23.5 ms with `exact`.

### 5.4.0
**Both biome sets on one continent.** New setting `biome_sets` (saved per world, format 13):
- `continents` (default): one Still Life biome set per FreeTerraForged continent, as before.
- `regions`: every continent divided into irregular regions, each American or Afro-Eurasian, like Still Life's own worlds.
  `biome_set_region_size` (default 25) is the size of one region in percent of an average continent's land (measured
  once per world): 25 = about four regions per continent.
- `halves`: every continent American in the west, Afro-Eurasian in the east, along a wandering line.
- In `regions` and `halves`, `american_share` is the share of each continent's own land (0.5 = every continent half and
  half); in `continents` it stays the share of continents. Works with FTF's single-continent worlds (continent type
  SINGLE). Older worlds keep one set per continent.

### 5.3.0
**Fabric.** The bridge now comes for NeoForge and Fabric: `stilllife_ftf_bridge-5.3.0-neoforge.jar` and
`stilllife_ftf_bridge-5.3.0-fabric.jar`. Same features, same config and world settings (format 12, unchanged), the same
terrain and biomes on both loaders. Tested with FreeTerraForged 1.0.0032 (its `-neoforge` and `-fabric` jars), Fabric
Loader 0.20.1 and Fabric API 0.116.18.
- The Fabric jar needs Fabric API (biome modifications and server events). It adds the wetland pools and sulfur rock with
  Fabric API's biome modifications, as the biome modifier files do on NeoForge.
- **VanillaBackport is required**, 1.2.2 or newer, on both loaders. Still Life's FTF edition already needed it (any 1.2+
  on NeoForge, any version on Fabric): without it the game stops at start-up with a message saying so.
- **VanillaBackport's own biome switches are followed**: `pale_garden`, `has_dappled_forest` or `has_sulfur_caves` set to
  false in `config/vanillabackport-common.toml` leaves that biome out of FTF worlds, the same as setting its amount to 0 in
  the bridge's config. Before, the bridge placed them anyway. All other Still Life and bridge features are unaffected.
- Repository: the code moved from `bridge/` to `common/` (shared, about 95%), `neoforge/` and `fabric/` (entry points and
  metadata). Gradle builds both jars (ModDevGradle and Fabric Loom); `tools/build-javac.sh` builds both without Gradle and
  remaps the Fabric jar with `tools/RemapJar.java`.

### 5.2.0
A natural snow line. Tested with FreeTerraForged 1.0.0032 and Still Life 0.3.6-ftf.
- **The snow line wanders** (`peak_line_variation`, default 40 blocks): Still Life's mountain biomes (alpine bands, then
  snowy peaks) start at a height that wanders up and down across a range by about 40 blocks, reaches further down gullies
  and stays higher on ridges, with a dithered edge. Before, every snow cap started at one height (about Y 380 in the
  tested world), so the snow line ran level around every mountain like a contour. Lowland biomes don't move.
- **Snow caps fade in** (`snow_cap_fade`, default 24 blocks; needs `snow_caps`): the lowest 24 blocks of a snowy peaks
  cap keep their snow only in patches that thin out downwards (steep spots first), with bare rock or gravel between, in
  the pattern Still Life's arctic desert uses for its snow. Before, the cap began with a solid white edge: snow went from
  2% to 79% of the ground within 8 blocks of height; now over about 60.
- World settings format 12. Older worlds keep their level snow line.

### 5.1.0
Fewer changes to FreeTerraForged itself, so the bridge keeps working with future FTF versions and leaves FTF's lakes and
rivers as its developer intends. Tested with FreeTerraForged 1.0.0027, 1.0.0030 and 1.0.0032 (all hooks apply on each).
- **Removed: FTF lakes as salt lakes (`salt_lakes_on_lakes`) and lakes filled to their banks (`lake_fill`,
  `lake_min_depth`).** FTF's lakes are its own again (FTF plans real lakes). Salt lakes now come only from the dry-wetland
  climate.
- **Removed: rivers start below mountains (`river_headwaters`, `river_mountain_height`, `river_fade_in`).** FTF's improved
  river placement is used as it is. Rivers again cross mountain ranges where FTF routes them.
- These removals take out 4 of the 11 hooks into FTF (the river carver, the river network, the heightmap and FTF's `Cell`),
  which also lets Worldgen Accelerator's fast path for FTF rivers work again. Saved values in older worlds are ignored, so
  new chunks of those worlds won't match old ones along lakes and rivers.
- **Standing water in salt lakes** (`salt_lake_water`, default 0.35): wide, irregular sheets one block deep over white
  calcite, with round calcite islands, as on Still Life's own salt lakes. On stepped ground, small round pools instead
  (`salt_lake_slope_pools`, default true).
- **Salt lakes only on flat ground** (`salt_lake_max_relief`, default 2; it was 4): FTF's "flat" land is often a gentle
  slope of 1-block terraces, where water could only lie in narrow bands along each step. There are fewer salt lakes, and
  they are flatter.
- **Wetland pools without stripes**: water now only sits where the ground is level for 2 blocks around. The sloped parts of
  a pool patch become mud, level with the ground. Before, on a sloped marsh, water filled every 1-block step and left
  grass steps between them, which looked like broken ground.
- **Blended biome-set borders** (`biome_set_blend`, default 384 blocks): where two FTF continents with different Still Life
  biome sets share land, the biomes no longer switch along a straight line on the continent border. The border is now
  irregular and dithered, like FTF's other biome edges.
- World settings format 11. Worlds made with 5.0 (format 10) keep their sharp biome-set borders and salt lake flatness,
  and get salt lake water at 0.2 in new chunks. The new pool shapes apply to new chunks in every world. Works with Still
  Life 0.3.5-ftf; 0.3.6-ftf adds the river beds.

### 5.0.0
- **Needs FreeTerraForged 1.0.0027** or later (4.x needs 1.0.0025). Built and tested against 1.0.0027 with Worldgen
  Accelerator 0.63.4.
- **Removed `river_flank_max_cut` / `river_flank_slope`.** FTF now gives river valleys natural ~24-degree sides itself; the
  bridge's limit on top of that only left a terrace edge around valleys and steeper valley floors. Saved values in existing
  worlds are ignored.
- **Removed the `/ftf locate biome` fix**: FTF 1.0.0027 fixed the crash itself.
- **Wetland biomes keep their water**: FTF 1.0.0027 re-picks the biome of wetland water and only keeps biomes it recognises
  as wetlands, so Still Life's muskegs, floodplain, flooded prairie and salt lake water would have turned into bog or fen
  patches. They now keep their biome.
- Salt lakes on FTF lakes adapted to FTF's rewritten river-biome rule.
- New settings for functions that only `enabled=false` could turn off before: **`wetland_max_height`** (default 48; the
  wetland hillside limit, which 4.x took from `river_flank_max_cut`), **`keep_wetland_biomes`** (default true) and
  **`sulfur_rock`** (default true). Older worlds keep what they had: their saved `river_flank_max_cut` becomes their
  `wetland_max_height`.
- **Beaches** (`beach_max_height`, default 8; `beach_shallows`, default 4): beach biomes only on coastal land within 8 blocks
  of the sea, plus the shallow sea in front of it, as in Still Life's own worlds. Before, the beach biome covered coastal
  slopes up to 30-50 blocks above the sea (grassland, since Still Life only lays sand below Y 65) and the sea was ocean right
  up to the shore. Older worlds keep their beaches.
- World settings format 10.

### 4.8.1
- `mountain_width` removed (mountains are again exactly as the preset makes them). Worlds made with 4.8.0 generate
  full-width mountains in new chunks.

### 4.8.0
- **`uplift_smoothing`** (default 1600 blocks): FTF's continental uplift no longer ends in a 40-100 block cliff running
  parallel to the coast ("plateau ridges"); it climbs back from FTF's coastal band in gentle terraces.
- **`mountain_width`** (default 0.35): mountains and mountain chains narrower and steeper in the same places (the preset's
  `mountains.horizontalScale` would move every range). At 0.35 chains cover ~19% of land instead of ~60%.
- World settings format 9.

### 4.7.1
- **`lake_min_depth`** (default 2): least water depth over a filled lake's bed. 4.7.0 used 1, which left pale, nearly white
  shallows over salt crust. World settings format 8 (4.7.0 worlds keep 1).

### 4.7.0
- **`lake_fill`**: FTF lakes filled to their banks. FTF shaped lake beds for the river's plain width and left a dry shelf
  20-40 blocks wide at water level; water area of a test lake +46%. World settings format 7.

### 4.6.0
- **`salt_lakes_on_lakes`**: FTF lakes (and shores within 16 blocks) in the salt-lake climate become Still Life salt lakes,
  on both continent sets. Lake cells are marked when FTF carves them (FTF itself marks lakes as plain river), and FTF's
  river-biome swap leaves them alone. World settings format 6.

### 4.5.0
- **`wetland_pools`**: standing water in Still Life's swamps, marshes, bogs and fens at any height.
- Salt lakes on FTF lakes (first attempt; it never fired, because FTF never reports a lake: fixed in 4.6.0).
- World settings format 5. Needs Still Life 0.3.5-ftf for the salt-lake and aspen-swamp surfaces.

### 4.4.0
- **Dry wetlands**: Still Life's aspen swamps (`aspen_swamps`) and salt flats (`salt_lakes`) on flat dry land, which never
  generated before.
- **Fix** `/ftf locate biome` ("An unexpected error occurred", tooltip `spawnSearch`).
- VanillaBackport defaults halved again: Pale Garden 0.18, Dappled Forest 0.5, Sulfur Caves 0.5. World settings format 4.

### 4.3.0
- Sulfur and cinnabar rock in VanillaBackport's Sulfur Caves (FTF skips the surface rule that paints it).

### 4.2.0
- VanillaBackport's Pale Garden, Dappled Forest and Sulfur Caves placed by the bridge, each with an adjustable amount.

### 4.1.0
- River valley sides as even slopes (`river_flank_slope`) instead of a hard cut limit.

### 4.0.0
- Snow on peaks kept at every altitude (`snow_caps`).
- Rivers start below mountains instead of cutting through them (`river_headwaters`).
- River valleys may only lower land by `river_flank_max_cut`; wetlands stay off hillsides.
- Swamps in very humid lowland (`humid_swamps`).
- Settings saved per world.

### 3.0.1 (Sondre)
- The climate translation: Still Life's biomes placed by FTF's real terrain (coast distance, height scale, biome sets,
  depth, latitude).

## Still Life FTF edition

- **0.3.6-ftf**: river beds at sea level follow the water depth: mud and packed mud in the shallows, gravel and some clay
  in the deep channel (arid rivers: coarse dirt, packed mud, clay). Before, a whole river ran one material for hundreds of
  blocks and then switched across its full width. The jar's Fabric/Quilt metadata now also carries the version (launchers
  such as Prism showed 0.3.5-ftf).
- 0.3.5-ftf works unchanged with FreeTerraForged 1.0.0027 (bridge 5.0).
- **0.3.5-ftf**: salt lakes (white calcite crust, clay beds) and aspen swamps (mud) at any height, not only at sea level;
  salt-crust rings in lakes at any height.
- **0.3.3-ftf**: granite and diorite only where erosion exposes rock (no pink or white mountains).
- **0.3.2-ftf**: VanillaBackport biomes left to the bridge (which places them at an adjustable amount per world).
- **0.3.1-ftf to 0.3.4-ftf** (also): VanillaBackport's biome surfaces (such as the Dappled Forest's) in Still Life's surface
  rule; big snow patches also on rock; in 0.3.1, VanillaBackport biomes carved into Still Life's climate table.
- **0.3-ftf**: Still Life without its Lithosphere terrain shaping, for FreeTerraForged.
