# Lifeforged

Still Life's biomes on FreeTerraForged's terrain, for Minecraft 1.21.1 (NeoForge 21.1).

[Still Life](https://modrinth.com/datapack/still-life) is a realistic overworld built for
[Lithosphere](https://modrinth.com/datapack/lithosphere)'s terrain. [FreeTerraForged](https://modrinth.com/mod/freeterraforged)
(FTF) makes its own, very different terrain: continents, erosion, river networks, tall mountains. Put the two together and
Still Life places its biomes by numbers that no longer mean what it expects: beaches inland, deserts on peaks, swamps on
mountain sides. This repository holds what makes them work together, worked out over many test worlds:

| Part | What it is |
|---|---|
| [`bridge/`](bridge) | **Still Life x FreeTerraForged Bridge** (`stilllife_ftf_bridge`), a NeoForge mod. Translates FTF's terrain into the climate Still Life expects, and fixes a long list of places where the two disagree (snow, rivers, lakes, wetlands, the coastal uplift cliff). Server side (also needed in single player). |
| [`still-life-patch/`](still-life-patch) | A script that turns **your own copy** of Still Life 0.3 into the **Still Life FTF edition 0.3.5-ftf**. Still Life is All Rights Reserved, so it is not included here. |
| [`docs/`](docs) | What every change does and **why** it was made, and what was learned about FTF's terrain on the way. |

> [!WARNING]
> **This project is AI-generated.** The code, the scripts and these documents were written by Claude (an AI made by
> Anthropic), directed and tested in game by one person, starting from version 3.0.1 of the bridge by Sondre.
> It changes world generation: **back up your worlds**, try it on a copy first, and report problems with your log
> (`logs/latest.log`).

> [!IMPORTANT]
> **Still Life is not in this repository.** Its licence is All Rights Reserved. Download Still Life 0.3 (NeoForge) from
> Modrinth yourself and run [`still-life-patch/make_ftf_edition.py`](still-life-patch) on it. Please don't upload the
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
- Still Life's two biome sets (American / Afro-Eurasian) per FTF continent, at a chosen share.
- Swamps, marshes and fens in very humid lowland, not only in FTF's river wetlands.
- Still Life's dry wetlands: aspen swamps on cool, fairly dry flat land; salt flats on hot, dry flat land; and every FTF
  lake in a salt-lake climate becomes a white-shored salt lake, on both continent sets.
- VanillaBackport's Pale Garden, Dappled Forest and Sulfur Caves inside Still Life's layout, each at an adjustable amount,
  with the Sulfur Caves' sulfur and cinnabar rock.

**Terrain**
- **No more coastal "plateau ridges"** (`uplift_smoothing`): FTF ends its continental uplift in a 40-100 block cliff that
  runs for kilometres parallel to the coast; the bridge turns it into gentle terraces.
- Rivers start below mountains instead of slicing through them, and their valley sides are even slopes instead of walls.
- FTF's lakes filled to their banks (FTF leaves a dry shelf 20-40 blocks wide at water level around most of them).
- Wetlands no longer dig pits into hill and mountain sides.

**Surface**
- Snow stays on peaks at every altitude (FTF's erosion turned Still Life's snow into stone, tuff and moss).
- Standing water in Still Life's swamps, marshes and bogs at any height (Still Life floods them only at sea level).
- Granite and diorite only where erosion cuts into rock, not whole pink or white mountains.
- Salt lakes with a white calcite crust and clay beds at any height; muddy aspen swamps.

**Fixes**
- `/ftf locate biome` works again (it failed with "An unexpected error occurred", tooltip "spawnSearch").

## Supported versions and mods

| | Version used | Notes |
|---|---|---|
| Minecraft | 1.21.1 | |
| NeoForge | 21.1.265 | any 21.1.x should work |
| Java | 21 or newer | the jars are compiled for Java 21 |
| FreeTerraForged | 1.0.0025 (preclimateFixes2) | 1.0.0014 or newer; continent type UPLIFT for `uplift_smoothing` |
| Still Life FTF edition | 0.3.5-ftf | made from Still Life 0.3 with `still-life-patch/` |
| VanillaBackport | 1.2.2 | required by Still Life's 1.21.4+ content (pale oak, bushes, leaf litter...) |
| Platform | 1.4.2 | VanillaBackport's library |
| Worldgen Accelerator, C2ME, Lithium, ModernFix, FerriteCore | tested alongside | all fine |

**Not together with** Lithosphere, MoreDensityFunctions or the normal Still Life jar: the FTF edition replaces Still Life,
and FTF replaces Lithosphere's terrain.

## Installation

1. Install FreeTerraForged, VanillaBackport and Platform.
2. Make the Still Life FTF edition from your own Still Life download:
   ```
   python3 still-life-patch/make_ftf_edition.py still_life-0.3-1.21.1.jar
   ```
   and put the resulting `still_life-0.3.5-ftf-1.21.1.jar` in `mods/` (remove the normal Still Life jar).
3. Put `stilllife_ftf_bridge-4.8.1.jar` in `mods/` (client and server; on a server only the server needs it, but single
   player runs a server too).
4. Start the game once: `config/stilllife_ftf_bridge.properties` is written with every option and its explanation.
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

## Config

`config/stilllife_ftf_bridge.properties` (defaults shown). Every key is also explained in the file itself.

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Turn the whole bridge off (Still Life's biomes then land in the wrong places). |
| `latitude` | `still_life` | `still_life`: Still Life's latitude climate (one equator, colder toward the poles). `ftf`: FTF's temperature bands. |
| `pole_distance` | `40000` | Blocks from the equator to the coldest band (`still_life` latitude only). |
| `american_share` | `0.5` | Share of FTF continents that get Still Life's American biome set (the rest: Afro-Eurasian). |
| `humid_swamps` | `true` | Very humid, low, flat land gets Still Life's swamp/marsh/fen climate, not only FTF's river wetlands. |
| `swamp_humidity` | `0.7` | FTF humidity (-1..1) from which lowland counts as swamp land. |
| `swamp_max_height` | `80` | Highest land that can become swamp, on Lithosphere's height scale (63 = sea, 84 = median land). |
| `aspen_swamps` | `1` | Still Life's aspen swamps on cool, fairly dry, flat lowland (American continents): 0 = none, 1 = about a quarter of that land, 2 = about half. |
| `salt_lakes` | `1` | Salt flats on hot, dry, flat land far inland (American continents), same scale. |
| `salt_lakes_on_lakes` | `true` | FTF lakes (and shores) in the salt-lake climate become salt lakes, on both continent sets. |
| `wetland_pools` | `true` | Shallow pools of standing water in Still Life's wetlands at any height. |
| `snow_caps` | `true` | Keep Still Life's snow on peaks at every altitude; only cliffs are bare. |
| `snow_cliff_steepness` | `0` | Slope above which snow is cleared; 0 = FTF's rock steepness from the preset. |
| `river_headwaters` | `true` | Rivers start below mountains instead of cutting through them. |
| `river_mountain_height` | `160` | Ground this many blocks above a river's water counts as a mountain on its course. |
| `river_fade_in` | `192` | Blocks over which a shortened river fades in. |
| `river_flank_max_cut` | `48` | Most blocks a river valley may lower the land away from the river; 0 = unlimited (FTF). |
| `river_flank_slope` | `1` | Valley sides rise along this slope from the bank (1 = 45 degrees, 0.5 = gentler). |
| `lake_fill` | `true` | FTF lakes filled to their banks instead of leaving a dry shelf at water level. |
| `lake_min_depth` | `2` | Least water depth over a filled lake's bed, right up to the bank. |
| `uplift_smoothing` | `1600` | Blocks over which FTF's continental uplift climbs back from its coastal band (instead of a cliff); 0 = FTF's cliff. |
| `pale_garden` | `0.18` | VanillaBackport Pale Garden amount (1 = standard, 0 = none, up to 2). |
| `dappled_forest` | `0.5` | VanillaBackport Dappled Forest amount. |
| `sulfur_caves` | `0.5` | VanillaBackport Sulfur Caves amount. |

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
- the **continent's biome set** from a per-continent roll.

Everything else is a targeted fix, each in its own mixin, each documented in the code and in `docs/bridge.md`.

## How the changes were tested

Every terrain change was checked on real generated terrain, not by eye alone: a headless NeoForge server with the full mod
set generates an area with Chunky, and Python scripts read the region files directly to measure heights, flatness, water
and biomes and to render shaded relief maps and cross-sections. A debug build of the bridge can also dump FreeTerraForged's
raw terrain cells for 32 x 32 km in seconds, which is how the coastal-cliff fix was found and tuned.
[`docs/ftf-terrain-notes.md`](docs/ftf-terrain-notes.md) collects what these measurements showed about FTF's terrain and
which preset values actually do what.

## Building from source

Needs a JDK 21 or newer. Copy the FreeTerraForged jar into `libs/` as described in [`libs/README.md`](libs/README.md)
(it is not on a public Maven), then:

```
./gradlew build
```

The jar lands in `bridge/build/libs/`. Gradle uses NeoForge's ModDevGradle and downloads NeoForge 21.1.265 on the first
run.

No Gradle? [`tools/build-javac.sh`](tools/build-javac.sh) builds the jar with plain `javac` and `jar` (this is how the
released jars were made); it also needs the game's own jars in `libs/` (listed in `libs/README.md`).

The Still Life FTF edition is not built here; see [installation](#installation) step 2.

## Version history

[`CHANGELOG.md`](CHANGELOG.md): every version of the bridge (3.0.1 to 4.8.1) and the Still Life FTF edition (0.3-ftf to
0.3.5-ftf), with what changed and why.

## Credits and licence

- **Still Life** by Jayzet (All Rights Reserved; not included).
- **FreeTerraForged** by the FreeTerraForged team; **Lithosphere**, whose climate layout Still Life's table follows.
- **VanillaBackport**, and **Worldgen Accelerator** (whose README this one follows).
- Bridge 3.0.1 by Sondre; 4.x, the Still Life patch and these documents written by Claude (Anthropic) for and with the
  repository owner, who directed and tested them in game.

The code in this repository is MIT licensed (see [`LICENSE`](LICENSE)).
