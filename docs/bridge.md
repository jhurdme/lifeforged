# The bridge, feature by feature

Each section says what was wrong, why, what the bridge changes (with the FreeTerraForged code it hooks into) and how it was
checked. FreeTerraForged (FTF) class names are from FTF 1.0.0025; the bridge's own classes are in
`bridge/src/main/java/stilllife/ftfbridge`. Version numbers say when a feature came in (see `CHANGELOG.md`).

Every option that changes terrain or biomes is **saved per world** (`WorldLock`, `<world>/stilllife_ftf_bridge.json`),
because a world whose settings change halfway would generate chunks that don't match. Worlds made before an option existed
keep it off. Every hook into FTF that is not essential is optional (`require = 0`): if a future FTF changes that code, the
feature switches itself off instead of stopping the game; `FtfCompat` checks the essential ones at start-up and fails with
a readable message.

- [1. Climate translation](#1-climate-translation)
- [2. Latitude, continents and biome sets](#2-latitude-continents-and-biome-sets)
- [3. Swamps in humid lowland](#3-swamps-in-humid-lowland)
- [4. Dry wetlands: aspen swamps and salt flats](#4-dry-wetlands-aspen-swamps-and-salt-flats)
- [5. FTF lakes as salt lakes](#5-ftf-lakes-as-salt-lakes)
- [6. Lakes filled to their banks](#6-lakes-filled-to-their-banks)
- [7. Standing water in wetlands](#7-standing-water-in-wetlands)
- [8. Snow on peaks](#8-snow-on-peaks)
- [9. Rivers start below mountains](#9-rivers-start-below-mountains)
- [10. Even valley sides](#10-even-valley-sides)
- [11. Wetlands stay off hillsides](#11-wetlands-stay-off-hillsides)
- [12. The coastal uplift cliff](#12-the-coastal-uplift-cliff)
- [13. VanillaBackport biomes](#13-vanillabackport-biomes)
- [14. Sulfur Caves rock](#14-sulfur-caves-rock)
- [15. /ftf locate biome](#15-ftf-locate-biome)

## 1. Climate translation

*3.0, refined in 4.x. `StillLifeClimateBridge`, `mixin/SamplerDecorationMixin`.*

**Problem.** Still Life is a multi-noise biome layout: thousands of entries, each a box in six climate parameters
(temperature, humidity, continentalness, erosion, depth, weirdness) and a biome. It was written for Lithosphere, whose
terrain produces those parameters in a particular way: continentalness rises with distance from the coast, erosion splits
the world into lowland and highland bands (and into Still Life's two biome sets), weirdness encodes peaks and valleys,
depth is the distance below the surface. FTF also gives Minecraft six numbers, but computes them from its own terrain, so
Still Life's table read them wrongly: biomes ended up by the wrong rules (inland beaches, highland biomes in valleys).

**Change.** The bridge hooks the point where FTF hands a climate point to the biome lookup
(`WorldgenPlans.SamplerDecoration.sample`, a `@ModifyReturnValue`) and rewrites it from FTF's terrain cell at that
position (`CellSampler.getCachedOrLookup`):

- **Continentalness** from FTF's `continentEdge` (its distance to the coast), mapped through a table so that the coast,
  50, 110, 170, 320, 690, 1180 and 1380 blocks inland land on the continentalness values Lithosphere has at those
  distances. Oceans keep FTF's value.
- **Height scale.** Still Life's height-dependent choices assume Lithosphere's heights. The bridge samples FTF's terrain
  of the world once (a 32 x 32 km grid), takes the height percentiles of land, and maps them onto Lithosphere's percentiles
  (`Y_LITHO`), so "the highest 5% of land" means the same in both. The result is saved in the world's settings file
  (`heights`, `coast_edge`) and only re-measured if the preset's terrain changes.
- **Erosion** = the biome-set half (-0.5 American / +0.5 Afro-Eurasian) plus Still Life's band offsets for lowland,
  highland (FTF's highland terrain category) and wetland.
- **Weirdness** = a ridge magnitude from the mapped height (peaks, slopes, valleys as Still Life expects), with a
  per-region sign so both of Still Life's variants appear.
- **Depth** rescaled from FTF's to Lithosphere's (`LITHO_DEPTH`), so cave biomes sit at the depths Still Life meant.

**Checked** on generated worlds: biome maps per height band and per distance from the coast, compared with the same seed
on Lithosphere + Still Life.

## 2. Latitude, continents and biome sets

*3.x. `latitude`, `pole_distance`, `american_share`.*

- `latitude=still_life` gives Still Life's own climate: one equator and colder bands toward the poles, `pole_distance`
  blocks away. `ftf` keeps FTF's repeating temperature bands (with altitude cooling).
- Still Life has two complete biome sets (American and Afro-Eurasian). Lithosphere chooses them by region; the bridge
  rolls once per FTF continent (`continentId`), so each continent has one consistent set, `american_share` of them American.

## 3. Swamps in humid lowland

*4.0. `humid_swamps`, `swamp_humidity`, `swamp_max_height`.*

**Problem.** Still Life's swamps, marshes and fens live in its "wetland" erosion band, which Lithosphere gives to low,
wet land. FTF only marks its own river wetlands (a few spots along rivers), so Still Life's large swamp biomes almost
never appeared.

**Change.** Land that is very humid (FTF humidity at or above `swamp_humidity`), low (at or below `swamp_max_height` on
Lithosphere's scale), not highland and at least ~110 blocks inland (coasts keep their beaches) gets the wetland band.

## 4. Dry wetlands: aspen swamps and salt flats

*4.4, surfaces in Still Life 0.3.5-ftf. `aspen_swamps`, `salt_lakes`.*

**Problem.** Two of Still Life's wetland biomes need the wetland band *together with* fairly dry air: `aspen_swamp`
(cool) and `salt_lake` (hot). The humid-swamp rule above never gives dry air, so they never generated. `/ftf locate biome`
found none.

**Change.** On American-set continents (the Afro-Eurasian set has neither biome), inland **flat** ground (at most 4 blocks
between the column and the ground 16 blocks away in four directions) whose climate falls inside one of the two biomes'
climate boxes (taken from Still Life's table, `ASPEN_SWAMP` / `SALT_LAKE`) gets the wetland band in noise-shaped patches a
few hundred blocks across. `1` gives about a quarter of the qualifying land (thresholds measured over several seeds,
`MASK_COVER`/`MASK_THRESHOLD`), `2` about half. Salt flats may sit 25 blocks higher than `swamp_max_height`, because dry
inland basins are often raised.

**Why the Still Life patch too.** Still Life builds both biomes for sea level (Lithosphere drops the wetland band to the
sea): its salt crust is only at Y 62-64 and its aspen-swamp mud only at Y 62. On FTF terrain they sit at any height and
looked like plain desert or forest. The 0.3.5-ftf surface rules work at any height: white calcite crust (dithered with
clay) on dry salt-lake ground, clay under water, mud in aspen swamps (see `still-life-ftf-edition.md`).

## 5. FTF lakes as salt lakes

*4.6. `salt_lakes_on_lakes`. `LakeCell`, `mixin/CellMixin`, `mixin/UpliftRiverCarverMixin`, `mixin/TerrainBiomeRoleSelectionMixin`.*

**Problem.** A salt lake should be a lake. FTF has no lake objects: its "lakes" are stretches where a river widens on a
flat step of the water table (`UpliftRiverCarver.getLakeMultiplier`, which multiplies the river bed radius). Their cells
are marked `TerrainType.RIVER` like any river, `Terrain.isLake()` is never true (no terrain type uses
`TerrainCategory.LAKE`), and FTF swaps the biome of every river cell for the nearest river biome
(`TerrainBiomeRoleSelection.resolve`). A first attempt in 4.5 keyed on `isLake()` and so never fired.

**Change.**
- A boolean is added to FTF's `Cell` (`LakeCell`, copied in `copyFrom`, cleared in `reset`). Right after FTF carves a
  river bed (`carveZone1Riverbed`), the bridge marks the cell as lake when the river is widened at least 1.5x there.
- In the salt-lake climate, lake cells and ground within 16 blocks of them get the wetland band (on either continent set;
  Afro-Eurasian ones use the American wetland band, where the only possible biome there is the salt lake).
- FTF's river-biome swap is skipped when the chosen biome is in the tag `stilllife_ftf_bridge:lake_biomes`
  (`still_life:salt_lake`), so the lake keeps its biome.

**Checked** on the harness: 46k salt-lake columns around a test lake; render and cross-sections.

## 6. Lakes filled to their banks

*4.7.0, `lake_min_depth` in 4.7.1. `lake_fill`, `lake_min_depth`. `mixin/UpliftRiverCarverMixin`.*

**Problem.** Most FTF lakes were ringed by a dry shelf 20-40 blocks wide, exactly at water level, between the water and
the bank. A cross-section of a test lake (water at Y 88) showed ground at Y 88 for 25-35 blocks on each side. Two causes,
both in FTF:

1. FTF clears the lake to its width-noise-varied radius (`zone1Radius`, which includes `biasedScale`) but shapes the bed
   for the river's plain width (`carveZone1Riverbed` is passed `scaleFactor` 1, not `biasedScale`). Wherever the noise
   widens the lake, the outer ring of the cleared area has no bed depth: it stays flat at water level.
2. A water column's floor block is the block the bed height reaches into, rounded *up* (measured by dumping FTF's cells
   along a transect and comparing them with the generated blocks). A bed less than one whole block under the surface
   block therefore ends flush with the water surface and stays dry.

**Change.** In lake stretches (blended in as the river widens from 1x to 1.5x, so ordinary river beds are unchanged):
the bed is shaped for the full cleared width (`@ModifyArg` on the `carveZone1Riverbed` call), and the bed stays at least
`lake_min_depth` whole blocks under the water surface block (`@ModifyReturnValue` on `carveZone1Riverbed`).

**Checked**: the test lake's water area grew 46% (24.9k to 36.4k columns) and now reaches within ~2 blocks of the bank; the
banks and the ridge around the lake are unchanged. With depth 1 a quarter of the lake was 1-block shallows that looked
white over the salt crust; 2 (the default since 4.7.1) reads as water.

## 7. Standing water in wetlands

*4.5. `wetland_pools`. `WetlandPoolsFeature`.*

**Problem.** Still Life floods its swamps, marshes, bogs and fens only at sea level (its surface rules put water and mud
at Y 62-63), because Lithosphere drops them there. They are meant to be filled in by Lithosphere's terrain; FTF has no such
hook, so above sea level they were dry forests.

**Change.** A feature (lakes step, added to the 23 Still Life wetland biomes in tag `stilllife_ftf_bridge:has_wetland_pools`
by a NeoForge biome modifier) places noise-shaped pools from a few to twenty blocks across in the top ground block, 1-2
blocks deep, with a mud bed and a muddy rim. A column only becomes water where all four neighbours reach at least as high,
so a pool is always held by ground and never spills down a slope; the decision depends only on the terrain, so pools match
across chunk borders.

## 8. Snow on peaks

*4.0. `snow_caps`, `snow_cliff_steepness`. `SnowCaps`, `mixin/ScreeFeatureMixin`, `mixin/DecorateSnowFeatureMixin`.*

**Problem.** Still Life puts snow blocks on its snowy peaks with surface rules. FTF's erosion decorator erodes snow, powder
snow and gravel (its `erodible` tag): above its rock line it turned that snow into stone, on slopes into tuff, in its dirt
band into moss or coarse dirt. Its snow decorator also cleared snow layers off any slope steeper than 0.45 above Y 95 (a
height made for 256-block worlds), so in tall worlds almost no snow survived on mountains.

**Change.** The erosion decorator skips columns topped with snow unless they are cliffs (steeper than
`snow_cliff_steepness`, or FTF's rock steepness when 0), and the snow decorator only clears cliffs.

## 9. Rivers start below mountains

*4.0. `river_headwaters`, `river_mountain_height`, `river_fade_in`. `RiverHeadwaters`, `mixin/UpliftRiverCarverMixin`, `mixin/NetworkMixin`.*

**Problem.** FTF's rivers are warped straight lines from the continent's interior to the sea that ignore the terrain, so
they slice deep canyons through mountain ranges.

**Change.** Once per river, its course is sampled every 48 blocks on FTF's terrain before any river carving; the last
point (going downstream) where the ground is more than `river_mountain_height` above the river's water is found, and the
river is only carved below it. Over `river_fade_in` blocks it fades in from a dry valley (which keeps the land's own
terrain type and biome) to a full river. A branch that would join its parent upstream of the parent's new start has
nowhere to flow and is dropped too (`NetworkMixin` tells each branch which river it joins).

## 10. Even valley sides

*4.0, slopes in 4.1. `river_flank_max_cut`, `river_flank_slope`.*

**Problem.** FTF fades a river valley out to the surrounding land over a fixed width, so in tall terrain it shears 100+
blocks off mountain sides, leaving vertical walls.

**Change.** Outside the river banks, carving may lower the land by at most `river_flank_max_cut` blocks, except close to the
river, where the limit relaxes along a slope rising `river_flank_slope` blocks per block from the bank. Valley sides become
even slopes that meet the banks, never walls (`@ModifyVariable` on `finalHeight` in `UpliftRiverCarver.carve`).

## 11. Wetlands stay off hillsides

*4.0. `mixin/WetlandMixin`.*

FTF's wetlands flatten everything within their radius to water level, so where one touches a hill it digs a pit into it.
Now ground up to half of `river_flank_max_cut` above the wetland's water is shaped normally, ground above the full value is
left alone, and in between the wetland fades out.

## 12. The coastal uplift cliff

*4.8.0. `uplift_smoothing`. `UpliftSmoothing`, `mixin/UpliftContinentGeneratorMixin`.*

**Problem.** Long, flat-topped "plateau ridges" ran for kilometres parallel to coasts: a 40-100 block cliff with lowland on
the sea side and high land behind. It was first taken for FTF's plateau terrain, then for its tectonic plate uplift
(`tectonicPlateUplift`: setting it to 0 removed the ridge, but also lowered a third of the land by 50-95 blocks). Dumping
FTF's raw cells across the cliff showed the real cause: the water table (which also lifts the land) drops from ~Y 150-166
to sea level between two neighbouring cells. In `UpliftContinentGenerator.apply`, FTF replaces the uplift by a "custom
peak" from the coast distance wherever that value is below 0.05:

```java
float customPeak = shiftAndRemap(cell.continentEdge, levels.water);
if (customPeak < 0.05F && customPeak < upliftGradient) upliftGradient = customPeak;
```

So within a band along every coast the uplift is forced to nearly zero, and where raised land meets that band it ends in
a cliff.

**Change.** Beyond the band, the uplift is capped by a value that climbs from the band's edge back to full uplift over
`uplift_smoothing` blocks (`@WrapOperation` on the write to `cell.waterTable`). Coastlines, the land seaward of the old
cliff and everything farther inland are unchanged. FTF's water table is stepped (15 plateaus, `ContinentalHydrology`),
so the ramp shows as a staircase of ~10-block terraces; 1600 blocks spreads them 150-250 blocks apart, like river benches.

**Checked** on raw terrain maps of 3 x 3 km at the cliff: cell-to-cell height jumps over 20 blocks went from 704 to 0;
4.7% of the area changed (lowered by up to ~80 blocks near the old cliff top).

## 13. VanillaBackport biomes

*4.2. `pale_garden`, `dappled_forest`, `sulfur_caves`. `VanillaBackportBiomes`, `mixin/MinecraftBiomeSourceGraphsMixin`.*

VanillaBackport adds the Pale Garden, Dappled Forest and Sulfur Caves to vanilla's layout, which Still Life replaces. The
bridge adds them to the layout FTF places biomes from (`MinecraftBiomeSourceGraphs.multiNoiseRoot`) by taking a humidity
band from chosen Still Life biomes: the Pale Garden the most humid part of temperate old-growth and mixed-wood forests,
the Dappled Forest the drier cool part of temperate forest, wooded prairie, aspen woodlands and inland mixed forest, the
Sulfur Caves thin bands of Still Life's barren caves. Each setting scales its band (1 = standard, 0 = none, 2 = about
double). Before 4.2 the Still Life patch wrote these into Still Life's table; now the amounts can be changed per world.

## 14. Sulfur Caves rock

*4.3. `SulfurRockFeature`.*

VanillaBackport paints the Sulfur Caves with a surface rule driven by a 3D noise. FTF doesn't run VanillaBackport's surface
rule and skips surface rules for rock far from open space, so the caves were plain stone. The bridge places the same rock
as the first feature step, before VanillaBackport's sulfur pools and spikes: base stone becomes cinnabar where the
`sulfur_cave_gradient` noise is in [-0.4, -0.1] or above 0.4, and sulfur in [0, 0.4] (VanillaBackport's own thresholds).

## 15. /ftf locate biome

*4.4. `mixin/FTFBiomeLocateQueryMixin`.*

`/ftf locate biome` failed with "An unexpected error occurred" (tooltip `spawnSearch`, a NullPointerException):
`FTFBiomeLocateQuery.isolateSampler` copies the world's spawn-search setting onto a private climate sampler with
`Objects.requireNonNull`, and that setting is empty unless the world's spawn was chosen in this session. The spawn search
plays no part in locating biomes, so an empty one is no longer copied.
