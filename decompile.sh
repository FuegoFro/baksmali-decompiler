#!/bin/bash
echo "Copying new binaries..."
rm -rf '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/baksmali/'
cp -r '/home/danny/workspace/baksmali-smali/baksmali/target/classes/org/jf/baksmali/' '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/'

rm -rf '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/dexlib/'
cp -r '/home/danny/workspace/baksmali-smali/dexlib/target/classes/org/jf/dexlib/' '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/'

rm -rf '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/util/'
cp -r '/home/danny/workspace/baksmali-smali/util/target/classes/org/jf/util/' '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/'


echo "Decompiling dex file..."

frameworkDir='/home/danny/Desktop/java-framework/java'
if [ -d "$frameworkDir" ]; then
    rm -rf "$frameworkDir"
fi
mkdir "$frameworkDir"

cd '/home/danny/workspace/decompiled/by-hand/baksmali/classes'
if [ "$1" == "d" ]; then
    java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -cp .:~/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar org.jf.baksmali.main -a 10 -o "$frameworkDir" '/home/danny/workspace/decompiled/cm7/classes.dex'
else
    java -cp .:~/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar org.jf.baksmali.main -a 10 -o "$frameworkDir" '/home/danny/workspace/decompiled/cm7/classes.dex'
fi
