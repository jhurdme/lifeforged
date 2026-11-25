# Lifeforged guide

Lifeforged (also called the *Still Life x FreeTerraForged bridge*) lets the Still Life biomes work on FreeTerraForged
terrain. This guide is for players and server owners. No programming knowledge is needed. The technical details are in
[`bridge.md`](bridge.md).

**Contents**

1. [What it is](#1-what-it-is)
2. [What you need](#2-what-you-need)
3. [Install and create a world](#3-install-and-create-a-world)
4. [What you will notice](#4-what-you-will-notice)
5. [Settings](#5-settings)
6. [Other mods](#6-other-mods)
7. [Upgrading existing worlds](#7-upgrading-existing-worlds)
8. [For pack makers](#8-for-pack-makers)
9. [Troubleshooting](#9-troubleshooting)
10. [Credits and licence](#10-credits-and-licence)

## 1. What it is

- **[Still Life](https://modrinth.com/datapack/still-life)** replaces Minecraft's biomes with hundreds of realistic
  ones. It was made for the terrain of another mod, Lithosphere.
- **[FreeTerraForged](https://modrinth.com/mod/freeterraforged)** (FTF) replaces the terrain: continents, rivers that
  run to the sea, mountains many hundreds of blocks tall.

Together, without help, Still Life misreads FreeTerraForged's land: beaches inland, deserts on peaks, swamps on
hillsides. Lifeforged tells Still Life what the land really looks like, so each biome lands where it belongs. It also
makes the ground, rock, snow and plants follow the shape of the land: scree below cliffs, rock that changes by region,
snow in gullies and shade, and a ragged treeline.

## 2. What you need

| What | Version | Needed? |
|---|---|---|
| Minecraft | 1.21.1 | yes |
| Java | 21 or newer | yes |
| NeoForge | 21.1 (tested 21.1.265) | yes (see the Fabric note below) |
| FreeTerraForged | 1.0.0027 or newer (6.0 tested with 1.0.0034) | yes |
| Still Life FTF edition | 0.3.7-ftf, made from your own Still Life 0.3 download | yes, instead of the normal Still Life |
| Lifeforged | `stilllife_ftf_bridge-6.0.0-neoforge.jar` | yes |
| VanillaBackport | 1.2.2 or newer | yes: Still Life uses its blocks |
| Platform | tested 1.4.2 | yes: VanillaBackport needs it |
| Nature's Spirit | tested 2.3.2 | optional: kaolin clay rock in dry mountains, travertine, some alpine plants |
| Still Life Naturalized | its FTF build (tested 1.2.3-ftf), patched with `patch_terrain.py` | optional |

**Fabric:** the code is shared with NeoForge, but the 6.0 Fabric jar has not been built or tested. Earlier versions
(up to 5.6) ran on Fabric (Loader 0.16.12 or newer with Fabric API).

**Do not install** Lithosphere, MoreDensityFunctions or the normal Still Life jar. Only one Lifeforged jar and one Still
Life jar may be in `mods`.

Lifeforged runs on the server side. In single player your game runs a small server, so put it in your own `mods`
folder. On a dedicated server, the server needs it.

## 3. Install and create a world

### Make the Still Life FTF edition

Still Life is All Rights Reserved, so it is not in this project and nobody may share a modified copy. You make the FTF
edition yourself from your own download, with the scripts in [`still-life-patch`](../still-life-patch). You need
Python 3.8 or newer.

1. Download **Still Life 0.3 for Minecraft 1.21.1** (the mod jar) from Modrinth.
2. Run:
   ```
   python3 still-life-patch/make_ftf_edition.py still_life-0.3-1.21.1.jar
   ```
   It writes `still_life-0.3.7-ftf-1.21.1.jar` next to the input.
3. Put that jar in `mods` and remove the normal Still Life jar. Please don't upload it anywhere.
4. If you use Still Life Naturalized, patch its FTF jar too, so its trees follow the same rules:
   ```
   python3 still-life-patch/patch_terrain.py naturalized.jar naturalized-patched.jar
   ```
   Use the output jar instead of the original. The script can safely run again on a jar it already patched.

### Install

1. Install NeoForge for Minecraft 1.21.1.
2. Put FreeTerraForged, VanillaBackport, Platform, the Still Life FTF edition and the Lifeforged jar in `mods` (plus
   Nature's Spirit and Still Life Naturalized if you want them).
3. Start the game once. Lifeforged writes `config/stilllife_ftf_bridge.properties`, with every option explained.
4. Create a **new** world with the **FreeTerraForged** world type.

### Preset options that matter

FreeTerraForged's world screen lets you pick and edit a **preset** (all its terrain settings). Lifeforged works with any
preset and any world height: it measures each world's terrain once and scales Still Life to it. These preset options
matter:

- **Erosion Decorator** (*Miscellaneous*) must be on for scree, rock and shores (`ground_cover`, `rock_strata`,
  `shores`). With it off, mountain snow still works if **Smooth Layer Decorator** and `natural_snow_layers` are on, but
  without scree. The log says so once per world.
- **Smooth Layer Decorator** (*Miscellaneous*) must be on for `natural_snow_layers`.
- **Continent type UPLIFT** is the only type where `uplift_smoothing` does something.

### Servers: the world height trap

Still Life ships its own overworld that is 576 blocks tall. The FreeTerraForged preset is applied as a datapack, and the
last datapack wins. If the preset pack comes **before** Still Life's data, the world gets 512-high terrain: every mountain
is cut off flat at **Y 511**.

Creating a world in single player puts the packs in the right order. The trap catches dedicated servers whose
`server.properties` (`initial-enabled-packs`) lists the preset pack first. The safe way: create the world in single
player and copy the world folder to the server.

**How to check:** with Worldgen Accelerator, `logs/latest.log` has a line like
`WGA overworld: TerraForgedChunkGenerator, build height y -64 to 1024; noise from y -64, 1088 tall, ...`. The "tall"
number should be your preset's height plus 64. **576 tall** means the world got Still Life's height. Without Worldgen
Accelerator, look for flat mountain tops at Y 511. Such a world cannot be repaired; create it again.

### Settings are saved per world

The config file only holds the settings for **new** worlds. When a world is opened for the first time, Lifeforged copies
the settings into `<world folder>/stilllife_ftf_bridge.json` and from then on uses only that file. This keeps new chunks
matching old ones.

- Editing `config/stilllife_ftf_bridge.properties` never changes an existing world.
- To change an existing world, edit its `stilllife_ftf_bridge.json` (same option names), or delete it so the world takes
  the current config at the next start. Only chunks generated afterwards change, so a seam shows where old and new
  chunks meet.
- The world file also holds measurements of the world's terrain (`heights`, `coast_edge`, `snow_scale` and others).
  Leave those alone.
- `enabled` is the one option that is not saved per world.

### The preview

FreeTerraForged's world screen can preview a world. The preview uses the current config file and a quick, rough height
lookup, so its biomes can differ a little from the real world on mountains. Seed viewer mods get the same quick lookup.

## 4. What you will notice

Option names are written like `this`; [section 5](#5-settings) lists them all.

### Biomes in the right places

Coasts have beaches, lowlands have lowland biomes, mountains have mountain biomes, and inland stays inland. Lifeforged
recalculates Still Life's climate numbers from FreeTerraForged's real terrain: distance from the coast, height (measured
once per world, so any preset and world height work), and humidity from FreeTerraForged.

### Climate and latitude

- `latitude=still_life` (default): one warm **equator** running east-west, colder and colder to the north and the south
  until polar ice. The equator usually lies 10,000 to 32,000 blocks south of the world centre; the log says where.
  `pole_distance` (default 50,000) is the distance from the equator to the coldest climate.
- `latitude=ftf`: FreeTerraForged's own warm and cold bands, set with the climate sliders of your preset. (In
  `still_life` mode FreeTerraForged's temperature sliders do nothing; its humidity sliders still work.)

### Two biome sets

Still Life has an **American** and an **Afro-Eurasian** set of biomes for the same climates. Some biomes, such as aspen
swamps and salt lakes, exist only in the American set. `biome_sets` decides the layout:

- `continents` (default): one set per landmass. Continents joined by FreeTerraForged's land bridges count as one, so the
  set never changes in the middle of the land.
- `cells`: one set per FreeTerraForged continent cell (the layout of worlds made before 6.0).
- `regions`: irregular regions on every continent, like Still Life's own worlds. Use this if your preset makes one
  single continent.
- `halves`: every continent is American in the west, Afro-Eurasian in the east.

Where two sets meet, their biomes mix in a ragged band instead of a straight line.

### Mountains and the snow line

The **snow line** is the height above which snow lies all year. It follows the climate, as on Earth: highest at the
equator, lower towards the poles, down to the sea at the cold edge of the tundra. Tropical mountains are green far up;
mountains in cold country are white much lower.

- `snow_line_equator` and `snow_line_tundra` default to `auto`. Lifeforged works the line out from how high the world's
  mountains are when the world is created, and saves it with the world. Where the high mountains stand about 330 blocks
  above the sea, that is Y 650 at the equator and Y 250 at the warm edge of the tundra. On FreeTerraForged's default
  preset it is about Y 375 and Y 165; on a 320-high preset about Y 200 and Y 105. A typed number sets the line at that Y.
- Still Life's **mountain biomes** are placed under the line: highlands, then an alpine band just under the line, then
  snowy peaks (or bare rocky peaks in hot country) above it. Each band keeps Still Life's own biome for the climate.
  Lowland, taiga and tundra keep their biomes.
- The line is not level: `peak_line_variation` lets it wander (lower in gullies, higher on ridges, different from one
  range to the next).
- `mountain_cooling` (default 0.25, only with `latitude=still_life`): where Still Life would put desert scrub right under
  a snow cap, the alpine band becomes a cold alpine biome (alpine tundra, cold barren highlands...) instead. Nothing
  else changes.

### Snow

With `mountain_snow` (default on), snow lies where weather would put it:

- Snow comes in gradually, not at a sharp line. First in gullies, hollows, shade and out of the wind; sunny faces,
  windy ridges and cliffs stay bare longest. Tongues of snow run down gullies below the snow line.
- Below the line there are patches. High above it the ground is mostly white, except cliffs and wind-scoured crests.
- Dry climates get less snow and a higher line; wet climates more.
- Stepped slopes are snowy or rocky as a whole, not in white and grey stripes.
- Gentle lowland never gets mountain snow. Still Life's own **arctic and frozen biomes** (arctic desert, glaciers, frozen
  tundra and taiga...) keep their own snow and ice at every height. Sea ice and icebergs are never touched.
- With `natural_snow_layers` (needs the Smooth Layer Decorator), thin snow takes its depth from the real ground, and
  trees keep snow on their leaves only inside snowfields.

With `mountain_snow=false` you get Still Life's own snow: snowy peak biomes solid white.

### Scree and ground

With `ground_cover` (default on):

- **Scree** (loose broken rock) lies below cliffs in tongues and cones: gravel near the cliff, cobblestone further down,
  overgrown with moss and plants where it is mild and wet.
- Bare high ground near and above the snow line gets **frost scree fields** of gravel and cobblestone, only on real
  mountains.
- Steep slopes without a cliff get thin rocky soil.
- Each mountain region (some 600-900 blocks across) has its own amount and kind of scree.
- Sand and sandstone appear only where the ground is sand already. No terracotta is added.
- **River banks** are left exactly as Still Life and FreeTerraForged make them.

With `ground_cover=false` you get FreeTerraForged's one mix of tuff, andesite, moss and coarse dirt everywhere.

### Rock

`rock_strata` (default `bedded`, needs `ground_cover`) decides what bare rock is made of. Each mountain region has its own
rock type; neighbouring regions usually look alike, and their rocks blend over about 150 blocks. Steep grassy slopes
show small rock steps.

| Rock type | Main rock | Also |
|---|---|---|
| Dark | tuff | stone; rare deepslate seams |
| Grey | stone | andesite; rare tuff seams |
| Granitic | stone | andesite; rare thin granite veins |
| Andesitic | andesite | stone |
| Dolomite | andesite | stone, tuff layers; rare diorite seams |
| Limestone | diorite | calcite; rare andesite seams |
| Buff clay rock | light grey kaolin | grey kaolin layers, white kaolin seams |
| Ochre clay rock | kaolin | orange kaolin, brown kaolin seams |

Clay rock appears only with **Nature's Spirit**, in about half of the dry regions. Layers and seams show only with
`bedded`.

- `off`: the world's own stone.
- `regional`: one rock per region, softly mottled, no layers.
- `bedded` (default): regional, plus tilted and folded layers in some regions and rare thin seams.
- `deep` (**experimental**, not tested in game): bedded, plus the regional rock through the whole body of the land, not
  only its surface. Regions get stacks of rock types that tilt and fold, so tall mountains and deep valleys change rock
  with height. With Nature's Spirit it can use travertine and chert, with Create its limestone. Ores stay where they
  are, and the rock from about sea level down stays normal stone and deepslate. It costs about 5-17% more generation
  time. If Worldgen Accelerator's `gpuLight` is on, the part below the surface is skipped.

### Shores, lakes and rivers

With `shores` (default on): gravel, stone and cobblestone below steep coasts and lake sides; mud flats in warm, shallow
sea; gravel, mud and clay on lake shores, with reeds and lily pads on warm lakes. Most FreeTerraForged lakes are part of
a river and get nothing. River banks are not decorated. Without Still Life Naturalized, Lifeforged adds a few riverside
plants (willows, birches, sugar cane, grass, ferns); with Naturalized, its own riverside plants are used instead.

### Forests and the treeline

- With `forest_variety` (default on): a belt of bushes and young trees where forest meets open land, natural clearings,
  and tree mixes that change from region to region.
- Without an option (needs the Still Life FTF edition): no trees on cliffs, steep faces or fresh scree; a ragged
  treeline that climbs into sheltered gullies and up sunny faces; above it small spruces and krummholz (low,
  wind-shaped bushes) in cool country and bush groups in hot country; a subtle belt of alpine plants; turf and small
  plants on cliff ledges.
- Palms always keep their crowns.
- The content patch removes Still Life's hidden obsidian monolith easter egg.

### Beaches, wetlands, land shape and caves

- **Beaches** are a low band along the water (`beach_max_height`), and the shallow sea in front has a sandy or gravelly
  floor (`beach_shallows`).
- **Swamps** in very humid lowland (`humid_swamps`, off by default), not only FreeTerraForged's river wetlands.
- **Aspen swamps and salt lakes** (American set) appear on flat, dry land (`aspen_swamps`, `salt_lakes`). Salt lakes are
  white salt flats with thin sheets of water.
- **Standing water** in wetlands at any height, only on level ground (`wetland_pools`).
- **No pits in hillsides**: FreeTerraForged's wetlands no longer dig deep into hills (`wetland_max_height`).
- **No coastal cliff**: on UPLIFT presets, the long 40-100 block cliff along coasts becomes gentle terraces
  (`uplift_smoothing`).
- **Clean cave floors**: no snow blocks or sand on cave floors under snowy peaks and deserts (`cave_floor_guard`).
- **Cave biomes** reach from the surface down to bedrock in every column, including Haunted Depths (`column_depth`).

## 5. Settings

All options are in `config/stilllife_ftf_bridge.properties`. They are the settings for **new** worlds; each world keeps
its own copy. A value that is not accepted is logged as an error and the default is used. Two old spellings are still
read: `temperature_mode` for `latitude`, and `true` / `false` for `cave_floor_guard` (meaning `exact` / `off`). Removed
options from older versions are ignored and disappear from the file.

**Basics and climate**

| Option | Default | Effect |
|---|---|---|
| `enabled` | `true` | The whole mod on or off. Not saved per world. |
| `latitude` | `still_life` | `still_life`: one equator, colder towards the poles. `ftf`: FreeTerraForged's temperature bands. |
| `pole_distance` | `50000` | Blocks from the equator to the coldest climate (`still_life` only). 1000 to 10000000. |

**Biome sets**

| Option | Default | Effect |
|---|---|---|
| `biome_sets` | `continents` | `continents`, `cells`, `regions` or `halves`: how the two sets are laid out. |
| `biome_set_region_size` | `25` | `regions` only: region size in percent of an average continent's land. 1 to 100. |
| `american_share` | `0.5` | Share that is American. 0 to 1. |

**Mountains and snow**

| Option | Default | Effect |
|---|---|---|
| `snow_line_equator` | `auto` | Snow line (Y) at the equator. `auto` or 64 to 4064. |
| `snow_line_tundra` | `auto` | Snow line (Y) at the warm edge of the tundra. `auto` or 64 to 4064, not higher than `snow_line_equator`. |
| `mountain_cooling` | `0.25` | Alpine band under snow caps becomes cold alpine instead of desert scrub (`still_life` only). 0 to 1; 0 = off. |
| `peak_line_variation` | `40` | Blocks by which the snow line wanders. 0 to 160. |
| `mountain_snow` | `true` | The snow model. Needs the Erosion Decorator, or else `natural_snow_layers` and the Smooth Layer Decorator. |
| `natural_snow_layers` | `true` | Thin snow follows the real ground. Needs the Smooth Layer Decorator. |

**Ground, rock, forests, coasts**

| Option | Default | Effect |
|---|---|---|
| `ground_cover` | `true` | Scree, frost scree fields, thin soil, regional ground. Needs the Erosion Decorator. |
| `rock_strata` | `bedded` | `off`, `regional`, `bedded` or `deep` (experimental): what bare rock is made of. Needs `ground_cover`. |
| `forest_variety` | `true` | Forest edges, clearings, regional tree mixes. |
| `beach_max_height` | `8` | Coast higher than this above the sea is never beach. 0 = no limit. 0 to 256. |
| `beach_shallows` | `4` | Sea up to this deep gets the beach biome. 0 = ocean to the shore. 0 to 32. |
| `shores` | `true` | Rocky shores, mud flats, lake shores with reeds and lily pads. |

**Wetlands, land shape, caves**

| Option | Default | Effect |
|---|---|---|
| `humid_swamps` | `false` | Swamps, marshes and fens in very humid lowland. |
| `wetland_pools` | `true` | Standing water in wetlands and salt lakes at any height. |
| `aspen_swamps` | `1` | How common aspen swamps are: 0 = only in FTF wetlands, 1 = a quarter of suitable land, 2 = half. 0 to 2. |
| `salt_lakes` | `1` | How common salt lakes are, same scale. 0 to 2. |
| `salt_lake_water` | `0.35` | Share of a salt lake under water (needs `wetland_pools`). 0 to 0.8. |
| `uplift_smoothing` | `1600` | Blocks over which the UPLIFT coastal cliff is spread as terraces. 0 = off. 0 to 4000. |
| `wetland_max_height` | `48` | Wetlands leave ground more than this far above their water alone. 0 = off. 0 to 2048. |
| `cave_floor_guard` | `exact` | `exact`, `vanilla` or `off`: where Still Life's ground may go in caves. |

**Advanced** (fine-tuning most people never need)

| Option | Default | Effect |
|---|---|---|
| `biome_set_blend` | `384` | Width in blocks of the band where the two sets mix. 0 = sharp. 0 to 2048. |
| `snow_caps` | `true` | FreeTerraForged's erosion keeps mountain snow; only cliffs are bared. |
| `snow_cliff_steepness` | `0` | Slope from which snow is cleared (`snow_caps` only). 0 = the preset's rock steepness. 0 to 10. |
| `snow_cap_fade` | `24` | Patchy lowest blocks of snowy peaks (only with `mountain_snow=false`). 0 to 64. |
| `swamp_humidity` | `0.7` | Humidity from which land becomes swamp (`humid_swamps` only). -1 to 1. |
| `swamp_max_height` | `80` | Highest land for swamps and aspen swamps (salt lakes 25 higher), on Still Life's height scale (63 = sea). 63 to 400. |
| `salt_lake_max_relief` | `2` | Salt lakes only where the ground varies at most this many blocks over 32. 1 to 16. |
| `salt_lake_slope_pools` | `true` | Small round pools on stepped salt lake ground. |
| `keep_wetland_biomes` | `true` | Still Life's wetland biomes keep their water instead of becoming bog, fen or marsh. |
| `column_depth` | `true` | Cave biomes from the surface down to bedrock in every column. |

## 6. Other mods

- **Worldgen Accelerator** (tested with 0.70.8 on the GPU path): the world comes out the same, within normal
  run-to-run variation, and generation is about 2.7 to 5.7 times faster. Known issue: its GPU surface step can place
  wrong blocks (seen as granite where deepslate or stone should be, in temperate deep ocean on one seed), and it only
  spot-checks 1 chunk in 64, so wrong blocks can stay. For block-exact worlds, set `gpuSurface=OFF` (or `CHECK`) in its
  config. Keep `gpuLight=OFF` (its default; `rock_strata=deep` needs it off). `warnUnsupportedVersions=false` silences
  its "unsupported bridge version" notice.
- **C2ME, Lithium, ModernFix, FerriteCore**: used alongside earlier versions on both loaders without problems.
- **Streams Reflowing**: set `allWaterBanks=0` in `config/streamsreflowing.properties`. Its default restyles other mods'
  water, which removes Lifeforged's salt lake and wetland pools and leaves mud rings.
- **Nature's Spirit** (optional): clay rock in dry regions, travertine, some alpine plants and firs.
- **Still Life Naturalized** (optional): patch its FTF jar with `patch_terrain.py` (section 3). Lifeforged then uses its
  reeds and riverside plants.
- **Create** (optional): only used by the experimental `rock_strata=deep`, for its limestone.
- **Seasons mods** place their own snow during play. That snow is not part of world generation.
- **Distant Horizons, Voxy** and other far-view renderers only display the terrain; nothing depends on them.

## 7. Upgrading existing worlds

- Chunks that already exist never change.
- A world keeps the settings saved in its `stilllife_ftf_bridge.json`. Worlds made with 5.x keep generating as 5.x did:
  options added later are off in them, and `rock_strata` is `off`. Worlds made with 6.0 test builds keep the snow line
  and mountain bands they were made with.
- 6.0 no longer places VanillaBackport's Pale Garden, Dappled Forest and Sulfur Caves. In worlds that had them, new
  chunks will not get them.
- A fix to the biome sets can change the set of a few coast and ocean spots in new chunks of existing worlds.
- The new Still Life FTF edition (0.3.7-ftf) can change trees and plants in new chunks of any world.
- Your old config file keeps the values written in it. New options are added with their defaults.
- Back up worlds before updating.

## 8. For pack makers

**Terrain filter.** Datapacks can ask about the terrain at a spot with `stilllife_ftf_bridge:terrain`:

- as a **placement filter** in a placed feature's `placement` list (after the `heightmap` step);
- as a **surface rule condition** inside `minecraft:condition`.

Every field is optional. You can ask about slope, river zones, the snow line and treeline, sun, wind and shelter, snow,
climate, scree, rock regions and rock types, cliff ledges and forest edges. A field that needs an option that is off in
the world is ignored, and outside FreeTerraForged worlds everything matches. Example: a shrub above the treeline, in
shelter, off scree and snow:

```json
{ "type": "stilllife_ftf_bridge:terrain",
  "min_snow_line": -60, "max_snow_line": -10,
  "min_shelter": 0.1,
  "avoid_talus": ["fresh", "rock"],
  "snow": false }
```

All fields, ranges and more examples: [`bridge.md`, section 15](bridge.md#15-terrain-data-for-datapacks). A second
condition, `stilllife_ftf_bridge:surface_layer`, is true only in the solid blocks that reach up to open air or water.

**Biome tags** (a datapack can add to them, or replace them with `"replace": true`):

- `stilllife_ftf_bridge:keeps_own_surface`: biomes whose own snow, ice and ground the snow model and scree leave alone
  (Still Life's arctic and frozen biomes).
- `stilllife_ftf_bridge:snow_caps`: peak biomes that count as a snow cap for `mountain_cooling`.
- `stilllife_ftf_bridge:cold_alpine`: biomes the cooled alpine band may become.
- `stilllife_ftf_bridge:belt_hot`, `belt_dry`, `belt_forest`, `belt_heath`, `belt_tundra`: which biomes get which plants
  in the belt above the treeline.

## 9. Troubleshooting

**Mountains are cut off flat at Y 511.** The preset datapack was applied before Still Life's data. See
[the world height trap](#servers-the-world-height-trap). The world has to be created again.

**I changed a setting and nothing happened.** The config only affects new worlds. Edit or delete
`<world folder>/stilllife_ftf_bridge.json` (see [settings are saved per world](#settings-are-saved-per-world)).

**New terrain does not match the old.** Expected after a settings change: only new chunks use new settings.

**No scree, no snow model, no rocky shores.** Check that `ground_cover`, `mountain_snow` and `shores` are on in the
world's own settings file, and that the preset's Erosion Decorator is on.

**Snow on leaves in green country.** Leaves lose snow outside snowfields only with `mountain_snow`, `natural_snow_layers`
and the preset's Smooth Layer Decorator. Snow that falls during play is not from world generation.

**The preview is empty.** Look in `logs/latest.log` for errors mentioning `stilllife_ftf_bridge` or FreeTerraForged, and
check that exactly one Lifeforged jar and one Still Life jar are installed.

**Useful log lines** (`logs/latest.log`, starting with `[stilllife_ftf_bridge]`):

- `defaults for new worlds: ...`: what the config file says.
- `using this world's saved settings: ...` or `saved settings for this world: ...`: what the world really uses.
- `Still Life's equator lies at z = ...; snow line from Y ... (equator) to Y ...`: where the equator and snow line are.
- `config: <option>=<value> is invalid ...`: a typo in the config file.
- `... failed (FreeTerraForged changed?)`: a FreeTerraForged update changed something; that part falls back to
  FreeTerraForged's own behaviour.

**Reporting a problem.** Open an issue on the project's GitHub page with what you expected and what you see (coordinates
and a screenshot help), the versions of Minecraft, NeoForge, FreeTerraForged, Lifeforged and the Still Life FTF edition,
your preset, the world's `stilllife_ftf_bridge.json`, and `logs/latest.log`.

## 10. Credits and licence

- **Still Life** by Jayzet (All Rights Reserved; not included in this project).
- **FreeTerraForged** by the FreeTerraForged team; **Lithosphere**, whose climate layout Still Life's table follows.
- **VanillaBackport**; **Worldgen Accelerator**.
- Lifeforged: bridge 3.0.1 by Sondre; later versions, the Still Life patch scripts and these documents written by Claude
  (an AI made by Anthropic) for and with the repository owner, who directed and tested them in game.

The code in this repository is MIT licensed (see [`LICENSE`](../LICENSE)). Still Life is not, which is why you make the
FTF edition from your own download.
