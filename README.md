# Lifeforged

Still Life's biomes on FreeTerraForged's terrain, for Minecraft 1.21.1 on NeoForge 21.1 and Fabric.

[Still Life](https://modrinth.com/datapack/still-life) is a realistic overworld built for
[Lithosphere](https://modrinth.com/datapack/lithosphere)'s terrain. [FreeTerraForged](https://modrinth.com/mod/freeterraforged)
(FTF) makes its own, very different terrain: continents, erosion, river networks, tall mountains. Put the two together and
Still Life places its biomes by numbers that no longer mean what it expects: beaches inland, deserts on peaks, swamps on
mountain sides. This repository holds what makes them work together, worked out over many test worlds:

| Part | What it is |
|---|---|
| [`common/`](common), [`neoforge/`](neoforge), [`fabric/`](fabric) | **Still Life x FreeTerraForged Bridge** (`stilllife_ftf_bridge`), a mod for NeoForge and Fabric: all its code is in `common/`, the two loader folders only hold the start-up code and the mod's metadata. Translates FTF's terrain into the climate Still Life expects, and fixes a long list of places where the two disagree (beaches, snow, wetlands, salt lakes, the coastal uplift cliff). Server side (also needed in single player). |
| [`still-life-patch/`](still-life-patch) | A script that turns **your own copy** of Still Life 0.3 into the **Still Life FTF edition 0.3.6-ftf**. Still Life is All Rights Reserved, so it is not included here. |
| [`docs/`](docs) | What every change does and **why** it was made, and what was learned about FTF's terrain on the way. |

> [!WARNING]
> **This project is AI-generated.** The code, the scripts and these documents were written by Claude (an AI made by
> Anthropic), directed and tested in game by one person, starting from version 3.0.1 of the bridge by Sondre.
> It changes world generation: **back up your worlds**, try it on a copy first, and report problems with your log
> (`logs/latest.log`).

> [!IMPORTANT]
> **Still Life is not in this repository.** Its licence is All Rights Reserved. Download Still Life 0.3 (the mod jar works on
> both NeoForge and Fabric) from Modrinth yourself and run [`still-life-patch/make_ftf_edition.py`](still-life-patch) on it. Please don't upload the
> resulting jar anywhere.

## Contents

- [What you get](#what-you-get)
- [Supported versions and mods](#supported-versions-and-mods)
- [Installation](#installation)
- [Settings are saved per world](#settings-are-saved-per-world)
- [Config](#config)
- [How the bridge works](#how-the-bridge-works)
- [How the changes were tested](#how-the-changes-were-tested)
- [Building from source](#building-from-source)
- [Version history](#version-history)
- [Credits and licence](#credits-and-licence)

## What you get

Each item links to its full explanation in [`docs/bridge.md`](docs/bridge.md) (bridge) or
[`docs/still-life-ftf-edition.md`](docs/still-life-ftf-edition.md) (Still Life patch).

**Biomes in the right places**
- Still Life's biomes follow FTF's real terrain: coasts get beaches, lowland gets lowland biomes, peaks get peaks, inland
  stays inland. The bridge measures each world's terrain once and maps FTF's heights and coast distances onto the scale
  Still Life was tuned for ([climate translation](docs/bridge.md#1-climate-translation)).
- Choice of Still Life's own latitude climate (one equator, colder towards the poles) or FTF's temperature bands.
- Still Life's two biome sets (American / Afro-Eurasian) per FTF continent, at a chosen share, blended into an irregular
  edge where two continents' land meets. Or both sets on every continent (`biome_sets`): in regions of a chosen share of
  the continent's size, or as a west and an east half, which also works with FTF's single-continent worlds.
- Beaches like Still Life's own: a low band along the shore and the shallow water in front of it, instead of "beach" grassland
  30-50 blocks up the coastal slopes.
- Swamps, marshes and fens in very humid lowland, not only in FTF's river wetlands.
- Still Life's dry wetlands: aspen swamps on cool, fairly dry flat land; salt lakes on hot, dry, flat land, with wide
  sheets of shallow water over white calcite like Still Life's own.
- VanillaBackport's Pale Garden, Dappled Forest and Sulfur Caves inside Still Life's layout, each at an adjustable amount,
  with the Sulfur Caves' sulfur and cinnabar rock.
- Cave biomes in Still Life's order from the surface down to bedrock in every column, its deepest ones (Haunted Depths)
  included, however tall the world (`column_depth`).
- Still Life's own wetland biomes keep their water: FTF re-picks the biome of wetland water, which would turn muskegs,
  floodplains, flooded prairies and salt lakes into patches of bog or fen.

**Terrain**
- **No more coastal "plateau ridges"** (`uplift_smoothing`): FTF ends its continental uplift in a 40-100 block cliff that
  runs for kilometres parallel to the coast; the bridge turns it into gentle terraces.
- Wetlands no longer dig pits into hill and mountain sides.
- FTF's own rivers and lakes are left as FTF makes them (bridge 5.1 removed its river and lake changes).

**Surface**
- Snow stays on peaks at every altitude (FTF's erosion turned Still Life's snow into stone, tuff and moss).
- **A natural snow line** (`peak_line_variation`, `snow_cap_fade`): snow caps no longer start at one height everywhere like a
  contour line. The line wanders up and down across a range, reaches further down gullies than ridges, and the lowest blocks
  of a cap hold snow only in patches, so it fades in.
- **Mountain belts** (`mountain_cooling`, off by default): in warm, dry country Still Life's shrublands ran straight up to
  the snow caps. With it on, mountains pass montane and alpine biomes (meadows, prairie highlands, alpine tundra, cold barren
  highlands) below their caps; the caps themselves stay as they are ([mountain belts](docs/bridge.md#mountain-belts-56)).
- Standing water in Still Life's swamps, marshes, bogs and salt lakes at any height (Still Life floods them only at sea
  level), only on level ground, so it never lines the steps of a slope.
- Granite and diorite only where erosion cuts into rock, not whole pink or white mountains.
- Salt lakes with a white calcite crust and clay beds at any height; muddy aspen swamps.
- River beds that follow the water depth (mud in the shallows, gravel in the channel) instead of one material for hundreds
  of blocks.
- **No snow or sand on cave floors** (`cave_floor_guard`): FTF leaves many caves in the biome of the ground above them,
  and Still Life laid that biome's ground (snow blocks under snowy peaks, sand under deserts) on their floors. Now its
  ground goes only on ground open to the sky or under standing water ([cave floors](docs/bridge.md#12-cave-floors)).

## Supported versions and mods

| | Version used | Notes |
|---|---|---|
| Minecraft | 1.21.1 | |
| NeoForge | 21.1.265 | any 21.1.x should work; jar `stilllife_ftf_bridge-<version>-neoforge.jar` |
| Fabric Loader / Fabric API | 0.20.1 / 0.116.18+1.21.1 | jar `stilllife_ftf_bridge-<version>-fabric.jar`; needs Fabric API (VanillaBackport needs Loader 0.16.12+) |
| Java | 21 or newer | the jars are compiled for Java 21 |
| FreeTerraForged | 1.0.0027-1.0.0033 (unstable) | 1.0.0027 or newer for bridge 5.x (bridge 4.x: 1.0.0025); 5.1 tested on 1.0.0027, 1.0.0030 and 1.0.0032, 5.2-5.5 on 1.0.0032 (5.3 on both loaders: FTF's `-neoforge` and `-fabric` jars), 5.6 on 1.0.0032 and 1.0.0033; continent type UPLIFT for `uplift_smoothing` |
| Still Life FTF edition | 0.3.6-ftf | made from Still Life 0.3 with `still-life-patch/` (0.3.5-ftf also works, without the river beds) |
| VanillaBackport | 1.2.2 | **required** (1.2.2 or newer, both loaders): Still Life uses its 1.21.4+ content (pale oak, bushes, leaf litter...), and the bridge places its biomes. Without it the game doesn't start and says so. |
| Platform | 1.4.2 | VanillaBackport's library |
| Worldgen Accelerator (0.63.4; 0.70.3 on NeoForge), C2ME, Lithium, ModernFix, FerriteCore | tested alongside | all fine, on both loaders |
| Streams Reflowing (2.17.5) | tested alongside | set `allWaterBanks=0` in `config/streamsreflowing.properties`: with its default 1 it restyles other mods' water as its banks, which removes the bridge's salt lake and wetland pools and leaves mud rings |

**Not together with** Lithosphere, MoreDensityFunctions or the normal Still Life jar: the FTF edition replaces Still Life,
and FTF replaces Lithosphere's terrain.

## Installation

1. Install FreeTerraForged, VanillaBackport and Platform for your loader (on Fabric also Fabric API).
2. Make the Still Life FTF edition from your own Still Life download:
   ```
   python3 still-life-patch/make_ftf_edition.py still_life-0.3-1.21.1.jar
   ```
   and put the resulting `still_life-0.3.6-ftf-1.21.1.jar` in `mods/` (remove the normal Still Life jar).
3. Put `stilllife_ftf_bridge-5.6.0-neoforge.jar` or `stilllife_ftf_bridge-5.6.0-fabric.jar` in `mods/` (client and
   server; on a server only the server needs it, but single player runs a server too).
4. Start the game once: `config/stilllife_ftf_bridge.properties` is written with every option, its explanation, its
   default and the values it accepts.
5. Create a **new world** with the FreeTerraForged world type.

Only one bridge jar and one Still Life jar may be in `mods/` at a time.

## Settings are saved per world

Most options change the terrain or the biomes, so a world must keep the settings it was made with, or new chunks would not
match old ones. The first time a world is opened, the bridge copies the current config into
`<world>/stilllife_ftf_bridge.json` and from then on uses that file, whatever the config says. So:

- The config file is for **new worlds**.
- Worlds made with an older bridge keep generating the way that bridge did: options added later are **off** in them
  (each bridge version records a `format` number; keys a world's file doesn't have load as "off").
- To change an existing world's settings, edit its `stilllife_ftf_bridge.json` (or delete it to take the current config).
  Already generated chunks don't change, so the border between old and new chunks may show.
- Bridge 5.x needs FreeTerraForged 1.0.0027, which carves rivers and wetlands differently from 1.0.0025: in a world started
  with bridge 4.x and FTF 1.0.0025, new chunks will not match old ones along rivers whatever the settings say.
- Bridge 5.1 no longer changes FTF's lakes and rivers: in worlds made with an older bridge, their saved lake and river
  settings are ignored, so new chunks will not match old ones along lakes and rivers.

## Config

`config/stilllife_ftf_bridge.properties` (defaults shown). Every key is also explained in the file itself, with its default
and the values it accepts (a value outside them is logged and the default used).

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Turn the whole bridge off (Still Life's biomes then land in the wrong places). |
| `latitude` | `still_life` | `still_life`: Still Life's latitude climate (one equator, colder toward the poles). `ftf`: FTF's temperature bands. |
| `pole_distance` | `40000` | Blocks from the equator to the coldest band (`still_life` latitude only). |
| `biome_sets` | `continents` | How Still Life's two biome sets are laid out. `continents`: one set per FTF continent. `regions`: irregular regions on every continent, each American or Afro-Eurasian (like Still Life's own worlds). `halves`: every continent American in the west, Afro-Eurasian in the east. |
| `biome_set_region_size` | `25` | `regions` only: size of one region in percent of the land of an average continent (measured per world): 25 = about four regions per continent, 50 = about two. |
| `american_share` | `0.5` | Share that is American: of the continents (`continents`), or of each continent's own land (`regions`, `halves`). |
| `biome_set_blend` | `384` | Where the two sets meet, the switch is spread over about this many blocks as an irregular, dithered edge; 0 = a straight switch. |
| `beach_max_height` | `8` | Coastal land more than this many blocks above the sea is never a beach biome; 0 = no limit. |
| `beach_shallows` | `4` | Sea up to this many blocks deep gets beach biomes (sand or gravel floor), as in Still Life's own worlds; 0 = ocean biomes to the shore. |
| `humid_swamps` | `true` | Very humid, low, flat land gets Still Life's swamp/marsh/fen climate, not only FTF's river wetlands. |
| `swamp_humidity` | `0.7` | FTF humidity (-1..1) from which lowland counts as swamp land. |
| `swamp_max_height` | `80` | Highest land that can become swamp, on Lithosphere's height scale (63 = sea, 84 = median land). |
| `aspen_swamps` | `1` | Still Life's aspen swamps on cool, fairly dry, flat lowland (American continents): 0 = none, 1 = about a quarter of that land, 2 = about half. |
| `salt_lakes` | `1` | Salt lakes on hot, dry, flat land far inland (American continents), same scale. |
| `salt_lake_max_relief` | `2` | Salt lakes only where the ground varies by at most this many blocks over 32; 4 = more salt lakes, also on gently stepped ground. |
| `wetland_pools` | `true` | Shallow pools of standing water in Still Life's wetlands and salt lakes at any height, only on level ground; the sloped parts of a pool patch become mud. |
| `salt_lake_water` | `0.35` | Share of a salt lake under one block of water over calcite (wide sheets with calcite islands); 0 = dry salt flats. Needs `wetland_pools`. |
| `salt_lake_slope_pools` | `true` | On stepped salt lake ground, small round pools instead of wide sheets (sheets could only lie in bands along each step); false = stepped ground stays dry. |
| `wetland_max_height` | `48` | FTF wetlands leave ground more than this many blocks above their water alone (and fade out above half of it), so they don't dig pits into hillsides; 0 = FTF's own wetlands. |
| `keep_wetland_biomes` | `true` | Water in FTF's wetlands keeps the Still Life wetland biome chosen there (FTF 1.0.0027 replaces ones it doesn't recognise). |
| `snow_caps` | `true` | Keep Still Life's snow on peaks at every altitude; only cliffs are bare. |
| `snow_cliff_steepness` | `0` | Slope above which snow is cleared; 0 = FTF's rock steepness from the preset. |
| `peak_line_variation` | `40` | Blocks by which the height where the mountain biome bands start (alpine, then snowy peaks) wanders: up and down across a range, lower in gullies, higher on ridges; 0 = a level line. |
| `snow_cap_fade` | `24` | The lowest this many blocks of a snowy peaks cap keep snow only in patches that thin out downwards, with bare rock between; 0 = solid snow down to the cap's edge. Needs `snow_caps`. |
| `mountain_cooling` | `0` | How much cooler the climate gets (on Still Life's temperature scale, -1 to 1; allowed 0 to 1) from the highlands up to the snowy peaks band, for choosing biomes only, so mountains get montane and alpine belts below their caps. The peaks band, polar caps and cave biomes are unchanged. Tested: 0.25. 0 = Still Life's own bands. |
| `cave_floor_guard` | `exact` | Where Still Life's ground blocks (snow blocks, sand, mud ...) may go, for the caves FTF leaves in a surface biome. `exact` = only on ground open to the sky or under standing water; `vanilla` = Minecraft's own `above_preliminary_surface` check (small pockets near the surface keep them); `off` = Still Life's own rules (cave floors get them too). |
| `column_depth` | `true` | Cave biome depth as the share of the column below the surface (FTF's own measure), mapped onto Still Life's Lithosphere scale. `false` = bridge 5.0-5.4: cave biomes start deeper under high ground and Still Life's deepest cave biomes never appear. |
| `uplift_smoothing` | `1600` | Blocks over which FTF's continental uplift climbs back from its coastal band (instead of a cliff); 0 = FTF's cliff. |
| `pale_garden` | `0.18` | VanillaBackport Pale Garden amount (1 = standard, 0 = none, up to 2). |
| `dappled_forest` | `0.5` | VanillaBackport Dappled Forest amount. |
| `sulfur_caves` | `0.5` | VanillaBackport Sulfur Caves amount. Switching a biome off in VanillaBackport's own config (`pale_garden`, `has_dappled_forest`, `has_sulfur_caves` in `config/vanillabackport-common.toml`) does the same as 0 here. |
| `sulfur_rock` | `true` | Sulfur and cinnabar rock in the Sulfur Caves (FTF skips the surface rule that paints it). |

## How the bridge works

The short version; [`docs/bridge.md`](docs/bridge.md) has every feature with the problem it solves, how it was found,
what exactly it changes in FreeTerraForged and how it was checked.

Still Life is a *biome layout*: a table that says which biome goes with which climate (temperature, humidity,
continentalness, erosion, depth, weirdness), written for Lithosphere, whose terrain makes those numbers in its own way:
continentalness rises with distance from the coast, erosion and weirdness encode lowland, highland and peaks. FreeTerraForged
also hands Minecraft these six numbers, but makes them differently, so Still Life's table reads FTF's numbers wrongly.

The bridge sits where FTF hands a climate point to the biome lookup (`WorldgenPlans.SamplerDecoration.sample`) and
rewrites it from FTF's own terrain cell at that spot:

- **continentalness** from FTF's distance to the coast, matched to the distances Lithosphere uses;
- **erosion and weirdness** from the terrain height, after mapping this world's height distribution onto Lithosphere's
  (the bridge samples FTF's terrain over 32 x 32 km once per world and stores the result in the world's settings file);
- **depth** rescaled to Lithosphere's;
- **temperature** from latitude, or FTF's;
- the **continent's biome set** from a per-continent roll, blended where two continents meet.

Everything else is a targeted fix, each in its own mixin, each documented in the code and in `docs/bridge.md`.

## How the changes were tested

Every terrain change was checked on real generated terrain, not by eye alone: a headless NeoForge server (and since 5.3 a
Fabric one) with the full mod set generates an area with Chunky, and Python scripts read the region files directly to measure heights, flatness, water
and biomes and to render shaded relief maps and cross-sections. A debug build of the bridge can also dump FreeTerraForged's
raw terrain cells for 32 x 32 km in seconds, which is how the coastal-cliff fix was found and tuned.
[`docs/ftf-terrain-notes.md`](docs/ftf-terrain-notes.md) collects what these measurements showed about FTF's terrain and
which preset values actually do what.

## Building from source

Needs a JDK 21 or newer. Copy FreeTerraForged's jars into `libs/` as described in [`libs/README.md`](libs/README.md) (they
are not on a public Maven), then:

```
./gradlew build
```

The jars land in `neoforge/build/libs/` and `fabric/build/libs/`. `neoforge/` uses NeoForge's ModDevGradle, `fabric/` Fabric
Loom; both compile the shared code in `common/` into their jar. The first run downloads NeoForge, Minecraft and Fabric.

The released jars are made by [`tools/build-javac.sh`](tools/build-javac.sh) instead, with plain `javac` and `jar` and the
jars listed in `libs/README.md`: it compiles `common/` against plain Minecraft first (so the shared code can't use anything
only one loader has), then each loader's code, and remaps the Fabric jar from Mojang's names to Fabric's with
[`tools/RemapJar.java`](tools/RemapJar.java), which uses the remapper inside the Fabric loader. The Gradle build has not been
run by the author (the development machine had no access to Maven); if it needs fixing, the javac script is the reference.

The Still Life FTF edition is not built here; see [installation](#installation) step 2.

## Version history

[`CHANGELOG.md`](CHANGELOG.md): every version of the bridge (3.0.1 to 5.6.0) and the Still Life FTF edition (0.3-ftf to
0.3.6-ftf), with what changed and why.

## Credits and licence

- **Still Life** by Jayzet (All Rights Reserved; not included).
- **FreeTerraForged** by the FreeTerraForged team; **Lithosphere**, whose climate layout Still Life's table follows.
- **VanillaBackport**, and **Worldgen Accelerator** (whose README this one follows).
- Bridge 3.0.1 by Sondre; 4.x, the Still Life patch and these documents written by Claude (Anthropic) for and with the
  repository owner, who directed and tested them in game.

The code in this repository is MIT licensed (see [`LICENSE`](LICENSE)).
