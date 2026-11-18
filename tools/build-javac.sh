#!/bin/bash
# Builds both bridge jars with plain javac + jar, without Gradle (this is how the released jars are made):
#   build/javac/stilllife_ftf_bridge-<v>-neoforge.jar   common + neoforge, Mojang's names (what NeoForge runs)
#   build/javac/stilllife_ftf_bridge-<v>-fabric.jar     common + fabric, remapped to Fabric's intermediary names
# Needs JDK 21+ and the files listed in libs/README.md in libs/.
# usage: tools/build-javac.sh [neoforge|fabric]   (default: both)
set -e
cd "$(dirname "$0")/.."
prop() { grep "^$1=" gradle.properties | cut -d= -f2; }
one() { # first file in libs/ matching a pattern
   local f; f=$(ls libs/$1 2>/dev/null | head -1)
   [ -n "$f" ] || { echo "missing libs/$1 (see libs/README.md)" >&2; exit 1; }
   echo "$f"
}
VERSION=$(prop mod_version)
OUT=build/javac
JAVAC="javac --release 21 -nowarn -proc:none -encoding UTF-8"

# vanilla Minecraft (Mojang's names) and the libraries every module compiles against; no loader jars here, so the common
# code can only use what both loaders have
MC=$(one 'client-1.21.1-*-srg.jar')
FTF=$(one "$(prop ftf_neoforge_jar)")
BASE="$MC:$FTF"
for p in 'brigadier-*.jar' 'datafixerupper-*.jar' 'fastutil-*.jar' 'gson-*.jar' 'guava-*.jar' 'jsr305-*.jar' 'log4j-api-*.jar' \
   'slf4j-api-*.jar' 'logging-*.jar' 'commons-lang3-*.jar' 'sponge-mixin-*.jar' 'mixinextras-*.jar'; do
   BASE="$BASE:$(one "$p")"
done

stage() { # <stage dir> <module>... : classes and resources of the modules, with ${version} filled in
   local dir=$1; shift
   rm -rf "$dir" && mkdir -p "$dir"
   for m in "$@"; do
      cp -r $OUT/$m/classes/. "$dir/"
      cp -r $m/src/main/resources/. "$dir/"
   done
   for f in "$dir/META-INF/neoforge.mods.toml" "$dir/fabric.mod.json"; do
      [ -f "$f" ] && sed -i "s/\${version}/$VERSION/g" "$f"
   done
   printf "Manifest-Version: 1.0\nImplementation-Title: stilllife_ftf_bridge\nImplementation-Version: %s\n" "$VERSION" > "$dir.MF"
}

rm -rf $OUT/common && mkdir -p $OUT/common/classes
$JAVAC -cp "$BASE" -d $OUT/common/classes $(find common/src/main/java -name "*.java")

if [ "${1:-neoforge}" = neoforge ] || [ -z "$1" ]; then
   NEO="$BASE:$OUT/common/classes:$(one 'neoforge-*-universal.jar'):$(one 'loader-*.jar'):$(one 'bus-*.jar'):$(one 'maven-artifact-*.jar')"
   rm -rf $OUT/neoforge && mkdir -p $OUT/neoforge/classes
   $JAVAC -cp "$NEO" -d $OUT/neoforge/classes $(find neoforge/src/main/java -name "*.java")
   stage $OUT/neoforge/stage common neoforge
   (cd $OUT/neoforge/stage && jar cfm ../../stilllife_ftf_bridge-$VERSION-neoforge.jar ../stage.MF .)
   echo "built $OUT/stilllife_ftf_bridge-$VERSION-neoforge.jar"
fi

if [ "${1:-fabric}" = fabric ] || [ -z "$1" ]; then
   LOADER=$(one 'fabric-loader-*.jar')
   MAPPINGS=$(one '*-1.21.1-mappings.txt')
   INTERMEDIARY=$(one 'intermediary-1.21.1*.jar')
   REMAP="java -cp $LOADER:$(one 'asm-[0-9]*.jar'):$(one 'asm-commons-*.jar'):$(one 'asm-tree-*.jar'):$(one 'asm-analysis-*.jar'):$(one 'asm-util-*.jar') tools/RemapJar.java"
   # Fabric API ships in Fabric's names: remap the three modules the bridge uses to Mojang's names to compile against
   API=$OUT/fabric/api && rm -rf $OUT/fabric && mkdir -p $API/jars $OUT/fabric/classes
   unzip -q -o "$(one 'fabric-api-*.jar')" 'META-INF/jars/*' -d $API/jars
   FAB="$BASE:$OUT/common/classes:$LOADER"
   for m in fabric-api-base fabric-biome-api-v1 fabric-lifecycle-events-v1; do
      $REMAP named "$(ls $API/jars/META-INF/jars/$m-*.jar | head -1)" $API/$m-named.jar "$MAPPINGS" "$INTERMEDIARY" 2>/dev/null
      FAB="$FAB:$API/$m-named.jar"
   done
   $JAVAC -cp "$FAB" -d $OUT/fabric/classes $(find fabric/src/main/java -name "*.java")
   stage $OUT/fabric/stage common fabric
   (cd $OUT/fabric/stage && jar cfm ../named.jar ../stage.MF .)
   # Mojang's names -> intermediary (with Minecraft and FreeTerraForged on the class path, for inherited members)
   $REMAP intermediary $OUT/fabric/named.jar $OUT/stilllife_ftf_bridge-$VERSION-fabric.jar "$MAPPINGS" "$INTERMEDIARY" $(echo "$BASE" | tr ':' ' ')
   echo "built $OUT/stilllife_ftf_bridge-$VERSION-fabric.jar"
fi
