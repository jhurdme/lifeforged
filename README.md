# Lifeforged

Lifeforged (formerly *Still Life x FreeTerraForged Bridge*) puts the biomes of
[Still Life](https://modrinth.com/datapack/still-life) on the terrain of
[FreeTerraForged](https://modrinth.com/mod/freeterraforged) (FTF), for Minecraft 1.21.1 on NeoForge. Still Life was made
for another terrain mod, Lithosphere; on FreeTerraForged alone it puts beaches inland and deserts on peaks. Lifeforged
places every biome where it belongs and makes the ground, rock, snow and plants follow the shape of the land.

**Read the [guide](docs/guide.md)**: install, settings, existing worlds and common problems.

> [!WARNING]
> **This project is AI-generated.** The code, scripts and documents were written by Claude (an AI made by Anthropic),
> directed and tested in game by one person, starting from version 3.0.1 of the bridge by Sondre. It changes world
> generation: **back up your worlds** and report problems with your `logs/latest.log`.

> [!IMPORTANT]
> **Still Life is not in this repository** (All Rights Reserved). Download Still Life 0.3 from Modrinth and make the
> Still Life FTF edition yourself with [`still-life-patch/make_ftf_edition.py`](still-life-patch). Please don't upload
> the result.

## What you get

- **Biomes and climate**: every Still Life biome where it belongs, a climate with one warm equator, one biome set per
  landmass.
- **Mountains and snow**: a snow line that follows the climate, Still Life's mountain biomes under it, and snow where
  weather would put it.
- **Ground and rock**: scree below cliffs, each mountain region with its own rock, tilted and folded rock layers.
- **Plants**: a ragged treeline with small trees and alpine plants above it, forest edges and clearings.
- **Water and wetlands**: rocky shores, low beaches, swamps and salt lakes with water at any height, no coastal cliff on
  UPLIFT presets.
- **Settings saved per world**, so new chunks always match old ones.
- **Terrain data for datapacks**: the filter and surface condition `stilllife_ftf_bridge:terrain`.

## Requirements

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
| Still Life Naturalized | optional (needs Nature's Spirit) | FTF build 1.2.3-ftf, patched with `patch_terrain.py` |
| Create | optional | tested 6.0.10 |

The 6.0 Fabric jar has not been built or tested (Lifeforged declares Fabric Loader 0.15+, VanillaBackport needs 0.16.12+).

Not together with Lithosphere, MoreDensityFunctions or the normal Still Life jar. Exactly what changes with Nature's Spirit, Still Life
Naturalized, Create and other mods: [guide, section 6](docs/guide.md#6-compatibility-with-other-mods).

## Install

1. `python3 still-life-patch/make_ftf_edition.py still_life-0.3-1.21.1.jar` writes `still_life-0.3.7-ftf-1.21.1.jar`.
   With Still Life Naturalized, also run `python3 still-life-patch/patch_terrain.py <its jar> <output jar>`.
2. Put FreeTerraForged, VanillaBackport, Platform, the Still Life FTF edition and
   `stilllife_ftf_bridge-6.0.0-neoforge.jar` in `mods/` (on a server, the server's `mods/`).
3. Start the game once: `config/stilllife_ftf_bridge.properties` is written, with every setting explained.
4. Create a **new world** with the FreeTerraForged world type. For a dedicated server, create it in single player and
   copy it over ([why](docs/guide.md#mountains-are-cut-off-flat-at-y-511)).

## Documents

- [`docs/guide.md`](docs/guide.md): the guide for players and server owners.
- [`docs/bridge.md`](docs/bridge.md): what every feature changes, why, and how it was checked.
- [`docs/still-life-ftf-edition.md`](docs/still-life-ftf-edition.md): what the Still Life patch changes.
- [`docs/ftf-terrain-notes.md`](docs/ftf-terrain-notes.md): what testing showed about FreeTerraForged's terrain.
- [`CHANGELOG.md`](CHANGELOG.md): every version.

## Building from source

Needs a JDK 21 or newer. Copy FreeTerraForged's jars into `libs/` as described in [`libs/README.md`](libs/README.md),
then run `./gradlew build` (or, offline once the dependencies are cached,
`./gradlew --offline -Pftf_fabric_jar=freeterraforged.jar :neoforge:build`). Jars land in `neoforge/build/libs/` and
`fabric/build/libs/`; all code is in `common/`. Earlier releases were built with
[`tools/build-javac.sh`](tools/build-javac.sh) (plain `javac`, see `libs/README.md`).

## Credits and licence

- **Still Life** by Jayzet (All Rights Reserved; not included). **FreeTerraForged** by the FreeTerraForged team.
  **Lithosphere**, whose climate layout Still Life follows. **VanillaBackport**.
- Bridge 3.0.1 by Sondre; later versions, the patch scripts and these documents written by Claude (Anthropic) for and
  with the repository owner, who directed and tested them in game.
- The code in this repository is MIT licensed (see [`LICENSE`](LICENSE)).
