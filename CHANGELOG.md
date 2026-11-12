# Changelog

Details and reasons for every item are in [`docs/bridge.md`](docs/bridge.md) and
[`docs/still-life-ftf-edition.md`](docs/still-life-ftf-edition.md). New options are saved per world: worlds made with an
earlier version keep them off.

## Still Life x FreeTerraForged Bridge

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

- **0.3.5-ftf**: salt lakes (white calcite crust, clay beds) and aspen swamps (mud) at any height, not only at sea level;
  salt-crust rings in lakes at any height.
- **0.3.3-ftf**: granite and diorite only where erosion exposes rock (no pink or white mountains).
- **0.3.2-ftf**: VanillaBackport biomes left to the bridge (which places them at an adjustable amount per world).
- **0.3.1-ftf to 0.3.4-ftf** (also): VanillaBackport's biome surfaces (such as the Dappled Forest's) in Still Life's surface
  rule; big snow patches also on rock; in 0.3.1, VanillaBackport biomes carved into Still Life's climate table.
- **0.3-ftf**: Still Life without its Lithosphere terrain shaping, for FreeTerraForged.
