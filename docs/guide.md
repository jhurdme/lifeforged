# Lifeforged guide

Lifeforged (formerly *Still Life x FreeTerraForged Bridge*) puts the biomes of
[Still Life](https://modrinth.com/datapack/still-life) on the terrain of
[FreeTerraForged](https://modrinth.com/mod/freeterraforged), and makes the ground, rock, snow and plants follow the
shape of the land. This guide is for players and server owners. The technical details are in [`bridge.md`](bridge.md).

1. [What you need](#1-what-you-need)
2. [Install](#2-install)
3. [What you will notice](#3-what-you-will-notice)
4. [Main settings](#4-main-settings)
5. [Existing worlds](#5-existing-worlds)
6. [Compatibility with other mods](#6-compatibility-with-other-mods)
7. [When something goes wrong](#7-when-something-goes-wrong)
8. [Reference](#8-reference) (all other settings, preset options, rock types, pack makers, credits)

## 1. What you need

| Mod | Needed? | Version |
|---|---|---|
| Minecraft | required | 1.21.1 |
| NeoForge | required | 21.1 (tested 21.1.265) |
| Java | required | 21 or newer |
| FreeTerraForged | required | 1.0.0027 or newer (tested 1.0.0034) |
| Still Life FTF edition | required | 0.3.7-ftf, made from your own Still Life 0.3 download |
| VanillaBackport | required | 1.2.2 or newer (tested 1.2.2) |
| Platform | required (by VanillaBackport) | tested 1.4.2 |
| Lifeforged | required | 6.0.0 (`stilllife_ftf_bridge-6.0.0-neoforge.jar`) |
| Nature's Spirit | optional | tested 2.3.2 |
| Still Life Naturalized | optional (needs Nature's Spirit) | FTF build 1.3.1-ftf (ready-made, no patching) |
| Create | optional | tested 6.0.10 |

Do **not** install Lithosphere, MoreDensityFunctions or the normal Still Life jar. The 6.0 Fabric jar has only been
tested on a dedicated server, not played on a client; it needs Fabric API, and Still Life Naturalized's FTF build is
NeoForge only.

## 2. Install

Still Life is All Rights Reserved, so this project cannot give you its files. You make the Still Life FTF edition from
your own download, with Python 3.8 or newer:

1. Download **Still Life 0.3 for Minecraft 1.21.1** (the mod jar) from Modrinth.
2. Run `python3 still-life-patch/make_ftf_edition.py still_life-0.3-1.21.1.jar`. It writes
   `still_life-0.3.7-ftf-1.21.1.jar`. Please don't upload it anywhere.
3. Still Life Naturalized (optional): use its FTF build 1.3.1-ftf as it is. Older FTF builds lack the treeline and
   cliff rules.
4. Put FreeTerraForged, VanillaBackport, Platform, the Still Life FTF edition and Lifeforged in `mods` (on a dedicated
   server: the server's `mods`).
5. Start the game once. Lifeforged writes `config/stilllife_ftf_bridge.properties`, with every setting explained.
6. Create a **new** world with the **FreeTerraForged** world type. Any preset and world height work. Keep the preset's
   *Erosion Decorator* and *Smooth Layer Decorator* on (they are on by default).

**Dedicated servers:** create the world in single player and copy it to the server. Otherwise the datapack order can
give the world Still Life's 576-block height, with every mountain cut off flat at Y 511
([details](#mountains-are-cut-off-flat-at-y-511)).

## 3. What you will notice

**Biomes and climate**
- Every biome is where it belongs: beaches on coasts, lowland biomes in lowland, mountain biomes on mountains.
- The world has one warm equator running east-west, and gets colder to the north and south.
- Still Life has two biome sets, American and Afro-Eurasian. Each landmass gets one, and where two meet their biomes mix
  in a ragged band.

**Mountains and snow**
- The snow line follows the climate: high near the equator, low towards the poles. Tropical mountains are green far up.
- Still Life's mountain biomes sit under the snow line: highlands, then alpine land, then snowy (or, where hot, bare)
  peaks.
- Snow lies where weather would put it: first in gullies, hollows and shade, last on sunny faces and windy ridges.
  Cliffs stay bare. Below the snow line it lies in patches; high above it the ground is mostly white.
- Still Life's arctic and frozen biomes keep their own snow and ice.

**Ground and rock**
- Scree (loose broken rock) lies below cliffs and on bare high ground. Steep slopes have thin rocky soil.
- Each mountain region has its own rock, and some show tilted and folded rock layers.
- Sand appears only where the ground is sand already. River banks are left as they are.
- Cave floors stay free of snow and sand.

**Plants**
- A ragged treeline, with small spruces and wind-shaped bushes above it, then alpine plants.
- No trees on cliffs or fresh scree.
- Forests have bushy edges, clearings, and tree mixes that change from region to region.

**Water and wetlands**
- Beaches are a low band along the water.
- Rocky shores below steep coasts, mud flats in warm shallow sea, reeds and lily pads on warm lakes.
- Swamps, bogs, fens and salt lakes have standing water at any height.
- On UPLIFT presets, the long cliff along coasts becomes gentle terraces.

## 4. Main settings

These are in `config/stilllife_ftf_bridge.properties`, and apply to **new** worlds. The file explains each one.

| Setting | Default | What it does |
|---|---|---|
| `latitude` | `still_life` | `still_life`: one warm equator. `ftf`: FreeTerraForged's warm and cold bands from your preset. |
| `pole_distance` | `50000` | Blocks from the equator to the coldest climate. Smaller = climates change faster. |
| `biome_sets` | `continents` | `continents`: one biome set per landmass. `regions`: several per continent (best for one-continent presets). Also `cells`, `halves`. |
| `american_share` | `0.5` | How much is in the American biome set, 0 to 1. |
| `snow_line_equator` | `auto` | Snow line at the equator (Y). `auto` = from this world's mountains (Y 650 on tall presets). |
| `snow_line_tundra` | `auto` | Snow line at the warm edge of the tundra (Y). `auto` = Y 250 on tall presets. |
| `mountain_snow` | `true` | Snow where weather would put it. `false` = Still Life's solid white peaks. |
| `ground_cover` | `true` | Scree and rocky soil. `false` = FreeTerraForged's one mix. |
| `rock_strata` | `bedded` | `off`, `regional` (own rock per region), `bedded` (plus rock layers), `deep` (rock layers through the whole land; about 5-17% slower generation, most in mountains). |
| `forest_variety` | `true` | Forest edges, clearings, regional tree mixes. |
| `shores` | `true` | Rocky shores, mud flats, lake reeds. |
| `humid_swamps` | `false` | Swamps also in very humid lowland, not only along rivers. |

The other settings are fine-tuning: see [all other settings](#all-other-settings).

## 5. Existing worlds

- When a world is first opened, Lifeforged copies the settings into `<world folder>/stilllife_ftf_bridge.json` and from
  then on uses only that file. Editing the config never changes an existing world.
- To change a world, edit or delete its `stilllife_ftf_bridge.json` and restart. Only chunks generated afterwards change,
  so a seam can show where old and new chunks meet.
- Chunks that already exist never change.
- Worlds made with an older version keep generating as that version did (new features off). A few things can still
  change in their new chunks: the VanillaBackport Pale Garden, Dappled Forest and Sulfur Caves are no longer placed, a
  few coast and ocean spots can change biome set, and the new Still Life FTF edition changes some trees and plants.
- An old config file keeps its values; new settings are added with their defaults.
- Back up your worlds before updating.

## 6. Compatibility with other mods

| Mod | Needed? | What changes | Notes |
|---|---|---|---|
| VanillaBackport, Platform | required | Nothing from Lifeforged: Still Life uses VanillaBackport's newer blocks (pale oak, bushes, leaf litter...). | Without them the game does not start. |
| Nature's Spirit | optional | With `ground_cover` on: kaolin clay rock in about half of the dry mountain regions (with `rock_strata` `regional`, `bedded` or `deep`), travertine in the scree of pale regions, sandy soil on dry sandy ground. Above the treeline: fir krummholz and alpine grasses and heathers. With `rock_strata=deep` also: travertine as the main rock of some pale regions and chert under some clay rock. | Without it the same places use vanilla stone (diorite, andesite, calcite, coarse dirt). Tested with 2.3.2. |
| Still Life Naturalized | optional | Lifeforged adds Naturalized's reed edges around lakes and leaves out its own riverside plants and lake reeds (willows, birches, sugar cane, grass and ferns). Its trees follow the treeline and cliff rules. | Needs its FTF build 1.3.1-ftf and Nature's Spirit. The build is ready-made: no patching. Its rock layer under the soil stays; with `rock_strata=deep` it is kept in the top 4 blocks and the regional rock is used below. Tested with 1.3.1-ftf. |
| Create | optional | Only with `rock_strata=deep`: Create's limestone (`create:limestone`) replaces diorite as the main rock of limestone regions. No other Create block is used. | With `off`, `regional` or `bedded` nothing changes. Create's own generated stone is untouched. Without Create those regions use diorite and calcite. Tested in one test world with Create 6.0.10. |
| Streams Reflowing | optional | Its default restyles other mods' water as its own banks, which removes Lifeforged's pools and leaves mud rings. | Set `allWaterBanks=0` in `config/streamsreflowing.properties`. Tested with 2.17.5. |
| C2ME, Lithium, ModernFix, FerriteCore | optional | Nothing. | No known problems. |
| Seasons mods | optional | They add their own snow during play. | That snow is not part of world generation. |

## 7. When something goes wrong

### Mountains are cut off flat at Y 511

The world got Still Life's 576-block height, because the FreeTerraForged preset pack was loaded before Still Life's data
(on dedicated servers that list it first in `initial-enabled-packs`). Such a world cannot be repaired; create it again in
single player and copy it to the server.

### I changed a setting and nothing happened

The config only affects new worlds. Edit or delete the world's `stilllife_ftf_bridge.json` (see
[existing worlds](#5-existing-worlds)).

### No scree, no snow model, or no rocky shores

Check that `ground_cover`, `mountain_snow` and `shores` are `true` in the world's `stilllife_ftf_bridge.json`, and that
the preset's *Erosion Decorator* is on.

### Reporting a problem

Look in `logs/latest.log` for lines starting with `[stilllife_ftf_bridge]`. Open an issue on the project's GitHub page
with coordinates, a screenshot, your versions, your preset, the world's `stilllife_ftf_bridge.json` and
`logs/latest.log`.

## 8. Reference

You can skip this part unless you need it.

### All other settings

| Setting | Default | What it does |
|---|---|---|
| `biome_set_region_size` | `25` | With `biome_sets=regions`: region size, percent of an average continent (1 to 100). |
| `mountain_cooling` | `0.25` | With `latitude=still_life`: cold alpine biomes instead of desert scrub under snow caps (0 to 1; 0 = off). |
| `peak_line_variation` | `40` | Blocks by which the snow line wanders, lower in gullies, higher on ridges (0 to 160). |
| `natural_snow_layers` | `true` | Thin snow follows the real ground; leaves keep snow only in snowfields. Needs the Smooth Layer Decorator. |
| `cave_floor_guard` | `exact` | `exact`: no snow or sand on cave floors. `vanilla`: Minecraft's check. `off`: Still Life's own rules. |
| `uplift_smoothing` | `1600` | UPLIFT presets: blocks over which the coastal cliff is spread as terraces (0 to 4000; 0 = off). |
| `beach_max_height` | `8` | Coast more than this above the sea is never beach (0 to 256; 0 = no limit). |
| `beach_shallows` | `4` | Sea up to this deep next to the shore is beach (0 to 32). |
| `wetland_pools` | `true` | Standing water in wetlands and salt lakes at any height. |
| `aspen_swamps` | `1` | How common aspen swamps are: 0, 1 = a quarter of suitable land, 2 = half (American biome set). |
| `salt_lakes` | `1` | How common salt lakes are, same scale (American biome set). |
| `salt_lake_water` | `0.35` | Share of a salt lake under water (0 to 0.8). |
| `wetland_max_height` | `48` | Wetlands leave ground more than this above their water alone (0 to 2048; 0 = off). |
| `enabled` | `true` | The whole mod on or off. Not saved per world. |
| `biome_set_blend` | `384` | Width of the band where two biome sets mix (0 to 2048; 0 = sharp). |
| `column_depth` | `true` | Cave biomes from the surface down to bedrock in every column. |
| `snow_caps` | `true` | FreeTerraForged's erosion keeps mountain snow; only cliffs lose it. |
| `snow_cliff_steepness` | `0` | Slope from which snow is cleared; 0 = the preset's rock steepness (0 to 10). |
| `snow_cap_fade` | `24` | With `mountain_snow=false`: patchy snow at the bottom of snowy peaks (0 to 64). |
| `swamp_humidity` | `0.7` | With `humid_swamps`: humidity from which land becomes swamp (-1 to 1). |
| `swamp_max_height` | `80` | Highest land for swamps, on Still Life's height scale where 63 = sea level (63 to 400). |
| `salt_lake_max_relief` | `2` | Salt lakes only where the ground varies at most this many blocks over 32 (1 to 16). |
| `salt_lake_slope_pools` | `true` | Small round pools on stepped salt lake ground. |
| `keep_wetland_biomes` | `true` | Still Life's wetland biomes keep their water instead of becoming bog, fen or marsh. |

Snow line values are `auto` or a Y from 64 to 4064; the tundra line may not be above the equator line. A value that is
not allowed is logged and the default is used. Old spellings still read: `temperature_mode` for `latitude`, and
`true` / `false` for `cave_floor_guard`.

With `latitude=ftf` the snow line uses FreeTerraForged's sea-level temperature, so mountains are not cooled twice, and
`mountain_cooling` does nothing.

### Preset options

- **Erosion Decorator** must be on for scree, rock and shores. With it off, the snow model still runs if
  `natural_snow_layers` and the **Smooth Layer Decorator** are on (without scree); the log says so.
- **Smooth Layer Decorator** must be on for `natural_snow_layers`.
- **Continent type UPLIFT** is the only one where `uplift_smoothing` does something.

### Rock types

Each mountain region (some 600-900 blocks across) has one rock type, softly mottled; neighbouring regions blend over
about 150 blocks.

| Rock type | Main rock | Also |
|---|---|---|
| Dark | tuff | stone, rare deepslate |
| Grey | stone | andesite, rare tuff |
| Granitic | stone | andesite, rare granite veins |
| Andesitic | andesite | stone |
| Dolomite | andesite | stone, tuff layers, rare diorite |
| Limestone | diorite | calcite, rare andesite |
| Clay rock (Nature's Spirit, dry regions) | kaolin | other kaolin colours |

With `deep`, regions get stacks of rock types that tilt and fold, so tall mountains and deep valleys change rock with
height; Nature's Spirit and Create add some rock types ([details](#6-compatibility-with-other-mods)). Ores stay where they are, and the rock from
about sea level down stays normal stone and deepslate.

### For pack makers

- `stilllife_ftf_bridge:terrain` works as a placement filter and as a surface rule condition: it asks about slope, the
  snow line, the treeline, sun, wind, shelter, snow, scree, rock regions and more. All fields:
  [`bridge.md`, section 15](bridge.md#15-terrain-data-for-datapacks).
- `stilllife_ftf_bridge:surface_layer` is true only in the solid blocks that reach up to open air or water.
- Biome tags: `keeps_own_surface` (biomes whose own snow and ice are kept), `snow_caps` and `cold_alpine` (for
  `mountain_cooling`), `belt_hot`, `belt_dry`, `belt_forest`, `belt_heath`, `belt_tundra` (which plants grow above the
  treeline). All are in the `stilllife_ftf_bridge` namespace.

### Credits and licence

- **Still Life** by Jayzet (All Rights Reserved; not included). **FreeTerraForged** by the FreeTerraForged team.
  **Lithosphere**, whose climate layout Still Life follows. **VanillaBackport**.
- Lifeforged: bridge 3.0.1 by Sondre; later versions, the patch scripts and these documents written by Claude (an AI
  made by Anthropic) for and with the repository owner, who directed and tested them in game.
- The code here is MIT licensed ([`LICENSE`](../LICENSE)). Still Life is not, which is why you make the FTF edition
  yourself.
