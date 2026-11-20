# Notes on FreeTerraForged's terrain

What measuring FreeTerraForged (FTF) 1.0.0025 on generated worlds showed, while tuning a preset and the bridge, with what
changed in 1.0.0027, 1.0.0030 and 1.0.0032 at the end. Useful for anyone tuning an FTF preset. Heights are block Y in a 1024-block-tall world (sea level 63); FTF's internal height
scale is 256 blocks per 1.0 (`Levels.terrainScaleFactor = min(worldHeight, 256)`).

## Which preset values do what

**`terrain.<type>.horizontalScale` is ignored by most terrain types.** Only `plains`, `steppe` and `mountains` read it.
Hills, dales, torridonian, plateau and badlands use fixed noise sizes (`Populators.makeHills1`: 200/400,
`makeDales`: 300/350, `makeTorridonian`: 100/150, `makePlateau`: 500/150), so changing their horizontal scale does nothing.
The only way to widen them is `general.globalHorizontalScale`, which scales all terrain (not the continents).

**`verticalScale` is applied twice for plains, hills and plateau**: once inside the noise (multiplied by
`globalVerticalScale`) and once as the populator's height scale. Plains' amplitude is `0.15 x globalVerticalScale x
plains.verticalScale` of 256 blocks: at 3.9 (as the tested preset had) "plains" swing over 140 blocks. Steppe uses 0.08.

**`baseScale`** multiplies the terrain's base (the `ground` noise, 0.25 = Y 64) before the height is added.

**`plains` and `steppe` also shape every terrain region's border.** `Heightmap.make` blends neighbouring regions through
a border populator made from the plains and steppe settings, so smoothing plains smooths every region boundary too.

**`mountains.horizontalScale` moves the mountain ranges.** It sets the ridge noise (`610 x scale`) and also the mountain
chain layout (a Worley edge noise of `2250 x scale`, times `globalHorizontalScale`). Lowering it from 2.76 to 1.4 made the
mountains around the test spawn disappear entirely.

**`rivers.lakes.minBankHeight` / `maxBankHeight` are unused** (`LakeConfig.bankMin/bankMax` are read by nothing); lakes use
their river's bank heights.

**`rivers.*.bankWidth`** (1.0.0025) sets the flat valley floor beside a river (up to that many blocks) and the width of the
valley's fade-out to the land (`bankWidth x (4 + height above water / 100)`); wider = gentler valleys. In 1.0.0027 it sets
the flat lead-out beside eroded, flat or pinched stretches (up to 0.8 x `bankWidth`) and the *least* width of the fade-out
(`bankWidth`, varied by noise); the fade-out is otherwise as wide as a ~24-degree slope needs (2.2 blocks out per block of
height), up to 1100 blocks.

## How the land is built

`Heightmap.applyTerrain`: continent, then terrain regions, then `land = Blender(mountainShape, terrainBlend, mountains,
0.3, 0.8, 0.575)`, plus the water-table uplift. Below a chain-mask value of 0.3 the land is the region's own terrain
(plains, hills...), above 0.8 it is mountain chain, in between a blend. With the tested preset 98% of land had a mask above
0.3 and 36% above 0.8, so almost everything was at least partly mountain.

**Mountain chain heights** are `1.3 x globalVerticalScale x mountains.verticalScale` squared in effect (the vertical scale
is applied in the noise and again as height scale): at 1.7 peaks can reach 900+ blocks.

## The water table, lakes and plateaus (UPLIFT continents)

- Each point gets a `waterTable` value from the continental uplift (higher toward raised plate borders). The water surface
  and a height offset for the land both come from it through a **stepped** function (`ContinentalHydrology.fixedCurvedStep`:
  15 plateaus with 0.02-wide ramps). That is why FTF's land and rivers form terraces, why rivers have waterfalls, and why
  any smooth change in the water table shows as ~10-block steps on flat ground.
- **Lakes** are not objects: a river widens (up to `sizeMax` %) where it crosses a flat water-table step
  (`UpliftRiverCarver.getLakeMultiplier`). Their cells are plain `RIVER` cells.
- **The coastal cliff**: within a band along every coast FTF replaces the uplift by a value from the coast distance
  (`UpliftContinentGenerator.apply`, `customPeak < 0.05`). Raised land meeting that band ends in a 40-100 block cliff
  parallel to the coast. `tectonicPlateUplift = 0` hides it by removing the uplift that makes the land high, but lowers about
  a third of the land by 50-95 blocks; the bridge's `uplift_smoothing` keeps the uplift and ramps it instead.

## Water columns round up

FTF fills a river or lake column with water from the block above its floor to the water surface block, but the floor block
it ends up with is the one the bed height reaches into, rounded **up** (`ceil`), while dry land rounds down. A bed less than
one whole block under the surface block is therefore flush with the water and dry. This is why FTF's rivers and lakes have
dry strips exactly at water level, and why the bridge's `lake_min_depth` counts whole blocks.

## The preset used in testing

FTF's defaults with rivers (4 main rivers, lakes chance 0.35, size 174-500%), `globalHorizontalScale` 7.0,
`continentScale` 5390, UPLIFT continents with `tectonicPlateUplift` 0.505, then smoothed:

| Value | Was | Now | Effect |
|---|---|---|---|
| `plains.verticalScale` / `horizontalScale` | 3.91 / 0.37 | 1.0 / 1.6 | gentle, broad plains (and region borders) |
| `steppe.verticalScale` / `horizontalScale` | 3.88 / 0.62 | 1.0 / 1.8 | same for steppe |
| `hills.verticalScale` | 3.22 | 1.4 | lower hills |
| `dales.verticalScale` | 3.10 | 1.4 | lower dales |
| `torridonian.verticalScale` | 2.38 | 1.4 | lower terraced hills |
| `plateau.verticalScale` | 2.53 | 2.0 | slightly lower mesas |
| `mainRivers.bankWidth` / `branchRivers.bankWidth` | 8 / 10 | 16 / 16 | wider flat valley floors, gentler valley sides |

Mountains unchanged.

## What changed in 1.0.0027

From FTF's source changes (git `40b2a1e9..a3b1142b`) and cell maps (FTF's raw terrain cells) of the test areas with the same preset:

- **River valleys** have natural V-shaped sides of about 24 degrees (`UpliftRiverCarver.carveValleySlopeFadeout`) instead of
  terraced steps, reaching as far out as the height needs. Rivers still cut straight through mountains (FTF's own
  `MountainRiverCutTest` expects it), now as wide valleys.
- **Lakes** still leave a dry shelf: the bed is still shaped for the river's plain width.
- **Wetlands** still flatten everything within their radius; on a mountainside that is a pit up to ~380 blocks deep. Only the
  bowl bottom is tagged wetland now; the flattened sides keep their terrain type.
- **The coastal uplift cliff** is unchanged (the `customPeak < 0.05` cap in `UpliftContinentGenerator.apply`): steps of up
  to 48 blocks between cells 4 blocks apart on the test coast.
- **Climate numbers**: a vanilla-banded `continentalness` on each cell, coastal land kept out of the valley weirdness band,
  and perlin instead of worley macro-biome noise. `Cell.continentEdge` is now `Cell.controlPoint`.
- **Biome roles** (`TerrainBiomeRoleSelection.resolve`): only submerged river, lake and wetland cells are re-biomed. River and
  lake water keeps a river biome, wetland water keeps a biome FTF recognises as a wetland (swamp/wetland tags or names), and
  dry banks lose river biomes.
- **Lakes as a terrain**: `TerrainType.LAKE` exists, but nothing assigns it yet; lakes are still river cells.

## 1.0.0030 and 1.0.0032

- **1.0.0030**: preset snapshots, biome locate changes, a stronghold cache fix and `findValueBruteForce` (an exhaustive
  search) in biome selection; terrain unchanged.
- **1.0.0032**: back to Minecraft's `findValue` in biome selection; the snow decorator can now cover small plants (a new
  preset setting) with a `snow_coverable` block tag. Terrain unchanged. Bridge 5.1's hooks apply on both.
- **A straight cliff along river valleys**: where a river crosses a coastal range, a straight step 15-20 blocks high can run
  from the river mouth up the slope for several hundred blocks (seen at X 3365490, Z -48790 to X 3365990, Z -48370 with the
  tester's preset, seed 8982759974645396284). Cell maps show no continent, terrain-region or control-point change across
  it; it lies in the river carver's `ValleyFadeout` zone, with high ground on one side, a sudden drop, then a gentle valley
  floor, as if the valley carve depth jumps where the nearest river segment changes. It appears with FTF and Still Life
  alone, with or without Worldgen Accelerator. Still Life's steep-slope rule paints the face bare stone, so it shows as a
  grey strip. (Up to bridge 5.0, its "rivers start below mountains" setting kept rivers out of such ranges, which hid it.)

## Cave biomes and surface biomes underground

FTF lays out cave biomes itself (`UndergroundBiomeBanding`, since its August 2026 redesign of underground biome selection),
with four preset settings on the Underground page: `undergroundBiomeCoverage` (default 0.25), `undergroundBiomeSize`
(default 225 blocks), `undergroundBiomeVerticalSize` and `undergroundBiomeClimateInfluence`.
- The world is cut into square cells of `undergroundBiomeSize`. Each cell rolls once against the coverage: cells that
  miss get no cave biome anywhere; their caves keep the biome of the ground above them from the surface to bedrock. The
  cells show as squares in cave-biome maps.
- Near the surface no cell gets cave biomes, so they never show through the ground: the chance ramps from 0 to the full
  coverage over a shell under the surface. Two limits apply, and the smaller wins: none within ~29 blocks of the lowest
  of four surface samples around the spot, full from ~53; and FTF's depth value (`ClimateDepthFunction`: 1.5 x
  (surface - y) / (surface + world depth), a share of the column rather than blocks) must reach (4 + 24) / 128. The
  second limit is (surface + world depth) x 0.146 blocks: ~45 blocks under a surface at Y 250, ~90 under Y 550. So the
  shell is thicker under high ground.
- Each cell's single roll is compared with coverage x the ramp, so within the shell the depth where cave biomes begin
  differs from cell to cell. Measured at coverage 1.0 (tester's preset, 9 cells around a snowy peak, ground Y 407-611):
  FTF alone 32-80 blocks below the ground (80 in a cell at Y 574).
- Which cells get cave biomes doesn't depend on the bridge: the same regions of two worlds with the same seed and preset,
  one with FTF alone and one with the bridge and Still Life, have exactly the same cave-biome and surface-biome columns.
  But bridge 5.0-5.4 read FTF's depth as 1/128 per block when it rescaled it for Still Life, which made depths look
  smaller, so with those the same cells started their cave biomes 0-48 blocks deeper (32-128 blocks). 5.5 reads it as the
  share of the column it is (`column_depth`): 32-64 blocks ([details](bridge.md#cave-biome-depth-55)).
- At the tester's coverage 0.35, 90% of cave floors at least 8 blocks down were in a surface biome around a snowy peak
  and a desert; at coverage 1.0, 3%.

Minecraft's own surface rules check `above_preliminary_surface`, so such caves stay plain rock with Minecraft's biomes.
Still Life's didn't (its own worlds never put a surface biome deep underground), so it laid snow blocks, sand and other
ground blocks on those cave floors. Bridge 5.5 keeps them off (`cave_floor_guard`,
[details](bridge.md#12-cave-floors)); Minecraft's check alone would still let small pockets near the surface through, because
on FTF's terrain the estimated surface it uses can sit 20-25 blocks below the real ground.

## Other mods

- **Streams Reflowing** (2.17.5) adds its own streams and lakes and, with its default `allWaterBanks=1`, restyles other
  water's banks with its materials. That replaces shallow water placed by other mods (the bridge's salt lake and wetland
  pools) with bank blocks and leaves mud rings where it was. `allWaterBanks=0` keeps its banks on its own water only.
