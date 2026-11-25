# Lifeforged

Still Life's biomes on FreeTerraForged's terrain, for Minecraft 1.21.1 on NeoForge 21.1 (the 6.0 Fabric jar has not been
built or tested).

[Still Life](https://modrinth.com/datapack/still-life) is a realistic overworld built for
[Lithosphere](https://modrinth.com/datapack/lithosphere)'s terrain. [FreeTerraForged](https://modrinth.com/mod/freeterraforged)
(FTF) makes its own, very different terrain: continents, erosion, river networks, tall mountains. Put the two together and
Still Life places its biomes by numbers that no longer mean what it expects: beaches inland, deserts on peaks, swamps on
mountain sides. This repository holds what makes them work together, worked out over many test worlds.

> **New here? Read [`docs/guide.md`](docs/guide.md)**: what the mod does, how to install it and create a world, every
> setting, and answers to common problems.


| Part | What it is |
|---|---|
| [`common/`](common), [`neoforge/`](neoforge), [`fabric/`](fabric) | **Still Life x FreeTerraForged Bridge** (`stilllife_ftf_bridge`), a mod for NeoForge and Fabric: all its code is in `common/`, the two loader folders only hold the start-up code and the mod's metadata. Translates FTF's terrain into the climate Still Life expects, and fixes a long list of places where the two disagree (beaches, snow, wetlands, salt lakes, the coastal uplift cliff). Server side (also needed in single player). |
| [`still-life-patch/`](still-life-patch) | Scripts that turn **your own copy** of Still Life 0.3 into the **Still Life FTF edition 0.3.7-ftf**, and put the bridge's terrain filter on the trees and plants of Still Life and its add-ons. Still Life is All Rights Reserved, so it is not included here. |
| [`docs/`](docs) | [`guide.md`](docs/guide.md), the guide for players; [`bridge.md`](docs/bridge.md), what every change does and **why** it was made; and what was learned about FTF's terrain on the way. |

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

Each item is explained in plain language in [`docs/guide.md`](docs/guide.md), and in technical detail in
[`docs/bridge.md`](docs/bridge.md) (bridge) and [`docs/still-life-ftf-edition.md`](docs/still-life-ftf-edition.md)
(Still Life patch).

**Biomes in the right places**
- Still Life's biomes follow FTF's real terrain: coasts get beaches, lowland gets lowland biomes, peaks get peaks, inland
  stays inland. The bridge measures each world's terrain once and maps FTF's heights and coast distances onto the scale
  Still Life was tuned for ([climate translation](docs/bridge.md#1-climate-translation)).
- Still Life's own latitude climate (one equator, colder towards the poles) or FTF's temperature bands (`latitude`).
- Still Life's two biome sets (American / Afro-Eurasian) per landmass, per region or per half continent (`biome_sets`),
  blended into a ragged edge where they meet.
- Beaches as a low band along the shore; swamps in humid lowland (`humid_swamps`, off by default); aspen swamps and salt
  lakes on dry flat land; standing water in wetlands at any height; wetland biomes keep their water.
- Cave biomes from the surface down to bedrock in every column (`column_depth`); no snow or sand on cave floors
  (`cave_floor_guard`).

**Mountains, snow and rock (6.0)**
- **A snow line by climate** (`snow_line_equator`, `snow_line_tundra`, `auto` by default): highest at the equator, down to
  the sea at the cold edge of the tundra, scaled to the world's own mountain heights and saved with the world. Still
  Life's highland, alpine and peak biomes are placed under it, so tropical mountains are green far up
  ([snow-line curve](docs/bridge.md#snow-line-curve-and-mountain-bands-60-format-18)).
- **One snow model** (`mountain_snow`, `natural_snow_layers`): snow lies where shelter, sun, wind and climate put it:
  patches below the line, mostly white high ground, cliffs bare. Still Life's arctic and frozen biomes keep their own
  snow and ice.
- **Mountain belts** (`mountain_cooling`): no desert scrub right under a snow cap.
- **Scree where it belongs** (`ground_cover`): talus below cliffs, frost scree on bare high ground, thin rocky soil on
  steep slopes. River banks are left alone.
- **Rock regions and strata** (`rock_strata`): bare rock belongs to its region, with tilted and folded beds. The value
  `deep` (regional rock through the whole land) is experimental.

**Plants and shores (6.0)**
- **Shores** (`shores`), **forest edges, clearings and regional tree mixes** (`forest_variety`).
- **A natural treeline** (Still Life FTF edition): trees off cliffs, a ragged treeline, krummholz and alpine plants above.

**Still Life FTF edition fixes**: granite and diorite only where erosion exposes rock; salt lakes and aspen swamps at
any height; river beds that follow the water depth; no obsidian monolith easter egg.

**Terrain**
- The coastal uplift cliff of FTF's UPLIFT continents becomes gentle terraces (`uplift_smoothing`).
- Wetlands no longer dig pits into hillsides (`wetland_max_height`).
- FTF's rivers and lakes are left as FTF makes them.

**For datapacks**: the placement filter and surface condition `stilllife_ftf_bridge:terrain`
([bridge.md section 15](docs/bridge.md#15-terrain-data-for-datapacks)).

## Supported versions and mods

| | Version used | Notes |
|---|---|---|
| Minecraft | 1.21.1 | |
| NeoForge | 21.1.265 | any 21.1.x should work; jar `stilllife_ftf_bridge-<version>-neoforge.jar` |
| Fabric Loader / Fabric API | 0.20.1 / 0.116.18+1.21.1 | up to 5.6 only: the 6.0 Fabric jar has not been built or tested (jar `stilllife_ftf_bridge-<version>-fabric.jar`; needs Fabric API; VanillaBackport needs Loader 0.16.12+) |
| Java | 21 or newer | the jars are compiled for Java 21 |
| FreeTerraForged | 1.0.0027 or newer (unstable) | 6.0 tested on 1.0.0034 (NeoForge); bridge 5.x needs 1.0.0027+, bridge 4.x 1.0.0025. Continent type UPLIFT for `uplift_smoothing` |
| Still Life FTF edition | 0.3.7-ftf | made from Still Life 0.3 with `still-life-patch/make_ftf_edition.py` (which also runs `patch_terrain.py`); older editions work, without the treeline and forest zoning |
| Still Life Naturalized, Nature's Spirit | 1.2.3-ftf, 2.3.2 | optional. Run `patch_terrain.py` on Naturalized's FTF jar too. Nature's Spirit adds kaolin clay rock in dry regions, travertine and some alpine plants |
| VanillaBackport | 1.2.2 | **required** (1.2.2 or newer, both loaders): Still Life uses its 1.21.4+ content (pale oak, bushes, leaf litter...). Without it the game doesn't start and says so. |
| Platform | 1.4.2 | VanillaBackport's library |
| Worldgen Accelerator (0.70.8 for 6.0; 0.63.4 and 0.70.3-0.70.7 earlier), C2ME, Lithium, ModernFix, FerriteCore | tested alongside | Worldgen Accelerator 0.70.8 on the GPU path: the same world within normal run-to-run variation, about 2.7-5.7x faster. Its GPU surface step can place wrong blocks (granite where deepslate or stone should be, seen in temperate deep ocean on one seed) and only spot-checks 1 chunk in 64: for block-exact worlds set `gpuSurface=OFF` (or `CHECK`) in its config. Keep `gpuLight=OFF` (its default). `warnUnsupportedVersions=false` silences its "unsupported bridge version" notice. The other mods: fine up to 5.6 on both loaders |
| Streams Reflowing (2.17.5) | tested alongside | set `allWaterBanks=0` in `config/streamsreflowing.properties`: with its default 1 it restyles other mods' water as its banks, which removes the bridge's salt lake and wetland pools and leaves mud rings |

**Not together with** Lithosphere, MoreDensityFunctions or the normal Still Life jar: the FTF edition replaces Still Life,
and FTF replaces Lithosphere's terrain.

## Installation

1. Install FreeTerraForged, VanillaBackport and Platform for your loader (on Fabric also Fabric API).
2. Make the Still Life FTF edition from your own Still Life download:
   ```
   python3 still-life-patch/make_ftf_edition.py still_life-0.3-1.21.1.jar
   ```
   and put the resulting `still_life-0.3.7-ftf-1.21.1.jar` in `mods/` (remove the normal Still Life jar). With Still Life
   Naturalized, also run `python3 still-life-patch/patch_terrain.py <its jar> <output jar>` and use the output.
3. Put `stilllife_ftf_bridge-6.0.0-neoforge.jar` or `stilllife_ftf_bridge-6.0.0-fabric.jar` in `mods/` (client and
   server; on a server only the server needs it, but single player runs a server too). The 6.0 Fabric jar has not been
   built or tested.
4. Start the game once: `config/stilllife_ftf_bridge.properties` is written with every option, its explanation, its
   default and the values it accepts.
5. Create a **new world** with the FreeTerraForged world type. On a dedicated server, mind the datapack order: the
   preset pack must come after Still Life's data, or the world gets Still Life's 576-high overworld and terrain cut off
   at Y 511 ([guide, section 3](docs/guide.md#servers-the-world-height-trap)). Creating the world in
   single player and copying it to the server avoids this.

Only one bridge jar and one Still Life jar may be in `mods/` at a time.

## Settings are saved per world

Most options change the terrain or the biomes, so a world must keep the settings it was made with, or new chunks would not
match old ones. The first time a world is opened, the bridge copies the current config into
`<world>/stilllife_ftf_bridge.json` and from then on uses that file, whatever the config says. So:

- The config file is for **new worlds**.
- Worlds made with an older bridge keep generating the way that bridge did: options added later are **off** in them
  (each bridge version records a `format` number; keys a world's file doesn't have load as "off"; `rock_strata` loads as
  `off` in worlds saved without it).
- To change an existing world's settings, edit its `stilllife_ftf_bridge.json` (or delete it to take the current config).
  Already generated chunks don't change, so the border between old and new chunks may show.
- Bridge 5.x needs FreeTerraForged 1.0.0027, which carves rivers and wetlands differently from 1.0.0025: in a world started
  with bridge 4.x and FTF 1.0.0025, new chunks will not match old ones along rivers whatever the settings say.
- Bridge 5.1 no longer changes FTF's lakes and rivers: in worlds made with an older bridge, their saved lake and river
  settings are ignored, so new chunks will not match old ones along lakes and rivers.

## Config

`config/stilllife_ftf_bridge.properties` is written at the first start, with every option, a short explanation, its
default and the values it accepts. "Advanced:" options are fine-tuning; "Experimental:" values (`rock_strata=deep`) are
not tested in game. A value that is not accepted is logged and the default used. Old spellings still read:
`temperature_mode` for `latitude`, `true`/`false` for `cave_floor_guard`. Removed options (`pale_garden`,
`dappled_forest`, `sulfur_caves`, `sulfur_rock`, and the river and lake options of 4.x) are ignored and disappear from
the file. Every option with its default and range: [guide, section 5](docs/guide.md#5-settings).

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

The 6.0 NeoForge jar is built with this Gradle build (offline, once the dependencies are cached:
`./gradlew --offline -Pftf_fabric_jar=freeterraforged.jar :neoforge:build`); the 6.0 Fabric jar has not been built.
Earlier releases were made by [`tools/build-javac.sh`](tools/build-javac.sh), with plain `javac` and `jar` and the jars
listed in `libs/README.md`: it compiles `common/` against plain Minecraft first, then each loader's code, and remaps the
Fabric jar from Mojang's names to Fabric's with [`tools/RemapJar.java`](tools/RemapJar.java).

The Still Life FTF edition is not built here; see [installation](#installation) step 2.

## Version history

[`CHANGELOG.md`](CHANGELOG.md): every version of the bridge (3.0.1 to 6.0.0) and the Still Life FTF edition (0.3-ftf to
0.3.7-ftf), with what changed and why.

## Credits and licence

- **Still Life** by Jayzet (All Rights Reserved; not included).
- **FreeTerraForged** by the FreeTerraForged team; **Lithosphere**, whose climate layout Still Life's table follows.
- **VanillaBackport**, and **Worldgen Accelerator** (whose README this one follows).
- Bridge 3.0.1 by Sondre; later versions, the Still Life patch and these documents written by Claude (Anthropic) for and
  with the repository owner, who directed and tested them in game.

The code in this repository is MIT licensed (see [`LICENSE`](LICENSE)).
