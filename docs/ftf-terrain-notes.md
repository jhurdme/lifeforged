# Notes on FreeTerraForged's terrain

What measuring FreeTerraForged (FTF) 1.0.0025 on generated worlds showed, while tuning a preset and the bridge. Useful
for anyone tuning an FTF preset. Heights are block Y in a 1024-block-tall world (sea level 63); FTF's internal height
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

**`rivers.*.bankWidth`** sets the flat valley floor beside a river (up to that many blocks) and the width of the valley's
fade-out to the land (`bankWidth x (4 + height above water / 100)`); wider = gentler valleys.

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
