# libs/

The mod jar the code compiles against but that are not on a public Maven repository. They are **not** committed (see
`.gitignore`); copy them here from your Minecraft instance's `mods` folder (or download them) and rename them:

| File | What | Where to get it |
|---|---|---|
| `freeterraforged.jar` | FreeTerraForged 1.0.0025 (any 1.0.0014+ should compile) | the FreeTerraForged download you play with |

A different name: `./gradlew build -Pftf_jar=<name>`.

`tools/build-javac.sh` (the plain-`javac` build, no Gradle) additionally needs the game's own jars here: the NeoForge
universal jar, Minecraft's client jar (`client-1.21.1-*-srg.jar` from the NeoForge install), Mixin, MixinExtras,
Brigadier, DataFixerUpper, fastutil, gson, guava, log4j-api, slf4j-api, logging and the FML loader jar. Prism Launcher keeps
them under its `libraries/` folder.
