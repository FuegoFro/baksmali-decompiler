#!/bin/bash
set -ex

dex_file='/home/danny/workspace/decompiled/cm7/classes.dex'
dex_file='/home/danny/Desktop/swipepad/unzipped/classes.dex'
dex_file='/home/danny/Desktop/ScrambleDecompile/classes.dex'
dex_file='/home/danny/Desktop/apks/classes.dex'
dex_file='/home/danny/Desktop/graffiti/classes.dex'
dex_file='/home/danny/Projects/APK Decompiling/simcity/com.ea.game.simcitymobile_row.apk'
dex_file='/Users/danny/tmp_decompiled/FlagApp2-unzip/classes.dex'

WORKSPACE_DIR='/home/danny/Dropbox/workspace'
WORKSPACE_DIR='/Users/danny/Dropbox (Personal)/workspace'

echo "Copying new binaries..."
rm -rf "$WORKSPACE_DIR"'/decompiled/by-hand/baksmali/classes/org/jf/baksmali/'
cp -r "$WORKSPACE_DIR"'/baksmali-smali/baksmali/target/classes/org/jf/baksmali/' "$WORKSPACE_DIR"'/decompiled/by-hand/baksmali/classes/org/jf/'

rm -rf "$WORKSPACE_DIR"'/decompiled/by-hand/baksmali/classes/org/jf/dexlib/'
cp -r "$WORKSPACE_DIR"'/baksmali-smali/dexlib/target/classes/org/jf/dexlib/' "$WORKSPACE_DIR"'/decompiled/by-hand/baksmali/classes/org/jf/'

rm -rf "$WORKSPACE_DIR"'/decompiled/by-hand/baksmali/classes/org/jf/util/'
cp -r "$WORKSPACE_DIR"'/baksmali-smali/util/target/classes/org/jf/util/' "$WORKSPACE_DIR"'/decompiled/by-hand/baksmali/classes/org/jf/'


echo "Decompiling dex file..."

frameworkDir='/home/danny/Desktop/java-framework/java/'
frameworkDir='/Users/danny/tmp_java_framework'
if [ -d "$frameworkDir" ]; then
    rm -rf "$frameworkDir"
fi
mkdir "$frameworkDir"

cd "$WORKSPACE_DIR"'/decompiled/by-hand/baksmali/classes'
if [ "$1" == "d" ]; then
    java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -cp .:"$WORKSPACE_DIR"/baksmali-smali/java-libs/commons-cli-1.2/commons-cli-1.2.jar org.jf.baksmali.main -a 10 -o "$frameworkDir" "$dex_file"
else
    java -cp .:"$WORKSPACE_DIR"/baksmali-smali/java-libs/commons-cli-1.2/commons-cli-1.2.jar org.jf.baksmali.main -a 10 -o "$frameworkDir" "$dex_file"
fi
