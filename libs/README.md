# libs/

Files the bridge compiles against that are not on a public Maven repository, or that the plain-`javac` build needs. They
are **not** committed (see `.gitignore`); copy them here from your Minecraft instances (Prism Launcher keeps the libraries
under its `libraries/` folder) or download them.

**Gradle** (`./gradlew build`) only needs FreeTerraForged:

| File | What | Where to get it |
|---|---|---|
| `freeterraforged.jar` | FreeTerraForged for NeoForge, 1.0.0027 or later | the FTF download you play with (`ftf-unstable-*-neoforge.jar`), renamed |
| `freeterraforged-fabric.jar` | FreeTerraForged for Fabric, same version | `ftf-unstable-*-fabric.jar` from FTF's GitHub releases, renamed |

Other names: `./gradlew build -Pftf_neoforge_jar=<name> -Pftf_fabric_jar=<name>`.

**`tools/build-javac.sh`** (no Gradle; how the released jars are made) additionally needs:

| Files | For | Where |
|---|---|---|
| `client-1.21.1-*-srg.jar` | Minecraft with Mojang's names | a NeoForge install (`libraries/net/minecraft/client/...`) |
| `brigadier-*.jar`, `datafixerupper-*.jar`, `fastutil-*.jar`, `gson-*.jar`, `guava-*.jar`, `jsr305-*.jar`, `log4j-api-*.jar`, `slf4j-api-*.jar`, `logging-*.jar`, `commons-lang3-*.jar` | Minecraft's own libraries | any 1.21.1 install |
| `sponge-mixin-*.jar`, `mixinextras-*.jar` | Mixin annotations | any NeoForge or Fabric install |
| `neoforge-21.1.*-universal.jar`, `loader-*.jar` (FML), `bus-*.jar`, `maven-artifact-*.jar` | the NeoForge jar | a NeoForge install |
| `fabric-loader-*.jar`, `asm-*.jar`, `asm-commons-*.jar`, `asm-tree-*.jar`, `asm-analysis-*.jar`, `asm-util-*.jar` | the Fabric jar, and the remapper inside the Fabric loader | a Fabric install (`libraries/net/fabricmc`, `libraries/org/ow2/asm`) |
| `intermediary-1.21.1.jar` | Fabric's names for Minecraft | a Fabric install (`libraries/net/fabricmc/intermediary`) |
| `server-1.21.1-mappings.txt` (or `client-1.21.1-mappings.txt`) | Mojang's names for Minecraft | a NeoForge server install (`libraries/net/minecraft/server/1.21.1-*/`), or Mojang's version manifest |
| `fabric-api-*.jar` | the three Fabric API modules the Fabric entry point uses | your Fabric instance's `mods` folder |

Mojang's mappings may be used but not redistributed, so `libs/*.txt` is ignored by git too.
