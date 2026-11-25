# Changelog

Details and reasons for every item are in [`docs/bridge.md`](docs/bridge.md) and
[`docs/still-life-ftf-edition.md`](docs/still-life-ftf-edition.md). New options are saved per world: worlds made with an
earlier version keep them off.

## Still Life x FreeTerraForged Bridge

### 6.0.0
The mod is now called **Lifeforged**: the ground, rock, snow and plants follow the shape of the land. New worlds are saved
with settings format 19. Worlds made with 5.x keep generating as before (the new options are off, `rock_strata` is
`off`), except for the consistency fixes and removals below. New plain-language guide: [`docs/guide.md`](docs/guide.md). Tested on NeoForge with FreeTerraForged 1.0.0034
and Still Life FTF edition 0.3.7-ftf. The Fabric jar has not been built or tested.

**Mountains and snow**
- **Snow line by climate** (`snow_line_equator`, `snow_line_tundra`, default `auto`): highest at the equator, falling
  towards the poles and reaching the sea at the cold edge of the tundra. `auto` is worked out from the world's measured
  mountain heights and saved with the world: Y 650 / 250 where the high mountains stand about 330 blocks above the sea,
  about Y 375 / 165 on FreeTerraForged's default preset, about Y 200 / 105 on a 320-high preset. The other heights of the
  snow zone and the bands scale the same way. A typed number sets the line. Works with both `latitude` settings
  (`latitude=ftf` uses FreeTerraForged's temperature at sea level).
- **Mountain bands under the snow line**: Still Life's own highland, alpine and peak biomes, with the alpine band just
  under the line and snowy peaks above it (bare rocky peaks start lower in hot country). The mountain biomes never come
  below a floor, so lowland, taiga and tundra keep their biomes. Tropical mountains are green far up.
- **`mountain_cooling` only where needed** (default now 0.25, `latitude=still_life`): only the alpine band under a snow
  cap is cooled, and only into a cold alpine biome (biome tags `stilllife_ftf_bridge:snow_caps` and `cold_alpine`). No
  more desert scrub under snow caps, and no other biome changes (test builds made conifer strips under the alpine tundra
  and could change tropical highlands).
- **One snow model** (`mountain_snow`, `natural_snow_layers`): snow lies where shelter, sun, wind and climate put it. It
  comes in gradually: patches and tongues below the line, mostly white high ground with bare cliffs and wind-scoured
  crests. Dry climates get less, wet ones more. The same model decides snow blocks, snow layers, snow on leaves and
  mountain ice. Stepped slopes are snowy or rocky as a whole (no white and grey stripes). Snow layers follow the real
  ground instead of FreeTerraForged's grid-like stripes.
- **Cold country keeps Still Life's own ground**: gentle taiga and tundra keep their ground; light, soft snow patches in
  the coldest strip of the tundra; Still Life's arctic and frozen biomes keep their own snow, ice, gravel and mud at every
  height except on real faces and cliffs (biome tag `stilllife_ftf_bridge:keeps_own_surface`, extendable by datapacks).
  Sea ice, icebergs and ice shelves are never touched.
- **Presets with FreeTerraForged's erosion decorator off**: `mountain_snow` runs in the snow layer pass instead (needs
  `natural_snow_layers` and the Smooth Layer Decorator), without scree. The log says so once per world.

**Ground and rock**
- **Scree where it belongs** (`ground_cover`): takes over FreeTerraForged's erosion decorator. Talus below cliffs in
  tongues and cones, sorted from gravel to cobblestone, overgrown where mild and wet; frost scree fields and talus on bare
  high ground; thin rocky soil on steep slopes; a soft fringe around snow patches. Rock regions 600-900 blocks across
  each have their own scree. Only on real mountains. Sand only where the ground is sand; no terracotta. River banks are
  left alone.
- **Rock regions and strata** (`rock_strata`: `off`, `regional`, `bedded`; default `bedded`): bare rock, the rock in
  scree and the risers of steep grassy slopes are made of their region's rock (dark tuff, grey stone, granitic,
  andesitic, dolomite, limestone; kaolin clay rock in about half of the dry regions with Nature's Spirit). `bedded` adds
  tilted and folded beds and rare seams. Regions blend over about 150 blocks.
- **Experimental: `rock_strata=deep`**, not tested in game: the regional rock through the whole body of the land in
  stacks of rock types that tilt and fold by region, with Nature's Spirit travertine and chert and Create's limestone
  when installed. Costs about 5-17% generation time. Its pass runs at the start of the light step, so it is skipped when
  Worldgen Accelerator's `gpuLight` is on.

**Plants, shores and forests**
- **Shores** (`shores`): gravel and stone below steep coasts and lake sides, mud flats in warm shallow sea, lake shores
  with reeds and lily pads. River banks and lakes that are part of a river are left alone.
- **Forests** (`forest_variety`): bush belts at forest edges, natural clearings, regional tree mixes.
- **The treeline and above** (no option; needs Still Life FTF edition 0.3.7-ftf): no trees on cliffs, steep faces,
  fresh scree or in avalanche gullies; a ragged treeline; small spruce clumps and krummholz above it, Still Life's bush
  groups instead in hot country (biome tag `stilllife_ftf_bridge:belt_hot`); a subtle subalpine belt; plants on cliff
  ledges; pioneer shrubs on old scree. No azalea plants or moss carpets.
- **Palms keep their crowns** (content patch): Still Life's palm crown features carry no terrain filter, so a trunk
  never stands without its crown.

**Biome sets**
- **One set per landmass** (`biome_sets=continents`, format 17): continents joined by FreeTerraForged's land bridges
  share one set, so the set no longer changes along a straight line in the middle of the land. A landmass of more than
  64 continents is split into parts of up to 4 x 4. The old layout is `biome_sets=cells`; worlds saved before format 17
  keep it. If a future FreeTerraForged changes what this reads, the log says so and the sets go per cell.

**Datapacks**
- The placement filter `stilllife_ftf_bridge:terrain` is now also a surface rule condition, and can ask about the snow
  model, sun, wind and shelter, scree, rock regions and rock types, climate (`min_climate` / `max_climate`), cliff ledges
  and forest edges ([bridge.md section 15](docs/bridge.md#15-terrain-data-for-datapacks)).

**Consistency fixes** (these can change newly generated chunks of existing worlds)
- The climate the bridge reads is worked out per position, so rock regions, snow and dry ground no longer switch along
  chunk borders, and the terrain filter's random choices are per position.
- Snow on high ground no longer depends on the order chunks are generated in (seams along chunk edges). With the
  content patch, Still Life's stone-cliff and snow disks no longer erase or add snow across chunk edges in format 18+
  worlds.
- With `biome_set_blend`, coast and ocean spots near an empty continent cell could take their biome set from an
  unrelated lookup; they now read the ocean's own set. In one test world this changed 41 of 40,000 samples, all outside
  the continents.

**Compatibility**
- Worldgen Accelerator 0.70.8 on the GPU path: the same world within normal run-to-run variation, about 2.7-5.7x faster.
  Its GPU surface step can place wrong blocks (granite where deepslate or stone should be, seen in temperate deep ocean
  on one seed) and only spot-checks 1 chunk in 64: set `gpuSurface=OFF` (or `CHECK`) for block-exact worlds. Keep
  `gpuLight=OFF` (default).
- FreeTerraForged's world preview and seed viewers get a quick height lookup (the full terrain model was far too slow),
  so their maps can differ slightly from the world on mountains.

**Removed**
- The VanillaBackport Pale Garden, Dappled Forest and Sulfur Caves placement and the Sulfur Caves rock (options
  `pale_garden`, `dappled_forest`, `sulfur_caves`, `sulfur_rock`; ignored in old files, and new chunks of worlds that
  used them no longer get these biomes). VanillaBackport is still required for Still Life's blocks.

**Config**
- New defaults for new worlds: `pole_distance` 50000 (was 40000), `mountain_cooling` 0.25 (was 0), `humid_swamps`
  false (was true). A config file written by an older version keeps its values.
- The config file is grouped by topic, with a short explanation per option and "Advanced:" / "Experimental:" marks.
  Same keys and values.

**Worlds made with 6.0 test builds** keep what they were made with: format 18 worlds keep their snow-line numbers
without scaling; formats 16-17 keep `snow_line_climate` and the ramped `mountain_cooling`; `latitude=ftf` worlds before
format 19 keep FreeTerraForged's bands; worlds saved before `rock_strata` existed load it as `off`.

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

- **0.3.7-ftf** (needs bridge 6.0): `patch_terrain.py` puts the bridge's terrain filter on Still Life's trees and plants:
  trees off cliffs, faces and fresh scree and out of steep gullies, fewer on ridges and wind-swept ground, a ragged
  treeline, natural clearings and regional tree mixes, snowy spruces only in snowfields, meadows thinner on exposed
  ground, steep-slope and stone-cliff patches only on steep ground (stone-cliff no longer leaves packed mud boxes), Still
  Life's snow layer patches and calcite disks left to the bridge's snow model. `make_ftf_edition.py` runs it; it can also
  be run on add-ons such as Still Life Naturalized's FTF jar. It also removes Still Life's hidden obsidian monoliths (a
  giant pillar at 20000, 20000 and eight small ones around it, listed in nearly every biome). Palm crowns carry no
  filter (so palms keep their crowns), and in bridge 6.0 worlds Still Life's stone-cliff and snow disks leave the
  bridge's snow alone.
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
