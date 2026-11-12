#!/bin/bash
# Builds the bridge with plain javac + jar, without Gradle (this is how the released jars were made).
# Needs JDK 21+ and the jars listed in libs/README.md (mod jars and the game's own jars) in libs/.
# usage: tools/build-javac.sh            -> build/javac/stilllife_ftf_bridge-<v>.jar
set -e
cd "$(dirname "$0")/.."
CP=$(ls libs/*.jar | tr '\n' ':')
prop() { grep "^$1=" gradle.properties | cut -d= -f2; }
build() { # <project dir> <jar base name> <version>
   local dir=$1 name=$2 ver=$3 out=build/javac/$2
   rm -rf $out && mkdir -p $out/classes $out/stage
   javac --release 21 -nowarn -proc:none -encoding UTF-8 -cp "$CP" -d $out/classes $(find $dir/src/main/java -name "*.java")
   cp -r $dir/src/main/resources/* $out/stage/ && cp -r $out/classes/* $out/stage/
   printf "Manifest-Version: 1.0\nImplementation-Title: %s\nImplementation-Version: %s\n" "$name" "$ver" > $out/MANIFEST.MF
   (cd $out/stage && jar cfm ../../$name-$ver.jar ../MANIFEST.MF .)
   echo "built build/javac/$name-$ver.jar"
}
build bridge stilllife_ftf_bridge "$(prop bridge_version)"
