#!/bin/bash
echo "Copying new binaries..."
rm -rf '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/baksmali'
cp -r '/home/danny/workspace/baksmali-smali/baksmali/target/classes/org/jf/baksmali' '/home/danny/workspace/decompiled/by-hand/baksmali/classes/org/jf/baksmali'

echo "Decompiling dex file..."

frameworkDir=/home/danny/Desktop/java-framework/java
if [ -d "$frameworkDir" ]; then
    rm -rf "$frameworkDir"
fi
mkdir "$frameworkDir"

cd '/home/danny/workspace/decompiled/by-hand/baksmali/classes'
java -cp .:~/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar org.jf.baksmali.main -a 10 -o "$frameworkDir" '/home/danny/workspace/decompiled/gummy/classes.dex'

echo "Postprocessing source files..."
cd "$frameworkDir"
for file in `find . -name *.java`; do
    unprocessed="${file%.java}.unprocessed"
    cat "$unprocessed" |
    perl -i -p -e 's/^[ ]*;\n//g;
                   s/new StringBuilder\(\)\.append\((.*)\)\.toString\(\)/\1/g;
                   s/\)\.append\(/ \+ /g' >> "$file"
    rm "$unprocessed"
done
