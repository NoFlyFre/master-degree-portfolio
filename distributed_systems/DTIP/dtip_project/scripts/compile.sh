#!/bin/bash
echo "🧹 Cleaning..."
rm -rf out
mkdir -p out

echo "🔨 Compiling DTIP..."

# Classpath con Lanterna
CLASSPATH="lib/lanterna-3.1.1.jar"

javac -cp "$CLASSPATH" -d out \
    interfaces/*.java \
    model/*.java \
    util/*.java \
    core/*.java \
    client/*.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation Complete. Classes are in 'out/'"
else
    echo "❌ Compilation Failed"
    exit 1
fi
