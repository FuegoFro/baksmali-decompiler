#!/bin/bash
set -euxo pipefail

dex_file='/home/danny/workspace/decompiled/cm7/classes.dex'
dex_file='/home/danny/Desktop/swipepad/unzipped/classes.dex'
dex_file='/home/danny/Desktop/ScrambleDecompile/classes.dex'
dex_file='/home/danny/Desktop/apks/classes.dex'
dex_file='/home/danny/Desktop/graffiti/classes.dex'
dex_file='/home/danny/Projects/APK Decompiling/simcity/com.ea.game.simcitymobile_row.apk'
dex_file='/Users/danny/tmp_decompiled/FlagApp2-unzip/classes.dex'

frameworkDir='/home/danny/Desktop/java-framework/java/'
frameworkDir='/Users/danny/tmp_java_framework'
if [ -d "$frameworkDir" ]; then
    rm -rf "$frameworkDir"
fi
mkdir "$frameworkDir"

echo "Decompiling dex file..."
./gradlew baksmali:run --args="-o $frameworkDir $dex_file"
