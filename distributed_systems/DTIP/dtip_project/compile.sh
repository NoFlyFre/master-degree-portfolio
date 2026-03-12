#!/bin/bash
# Script di compilazione per il progetto DTIP

set -e  # Esci al primo errore

echo "=== Compilazione Progetto DTIP ==="
echo ""

# Verifica Java
if ! command -v javac &> /dev/null; then
    echo "✗ Errore: javac non trovato. Installa Java JDK 11+"
    exit 1
fi

JAVA_VERSION=$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)
echo "Java version: $JAVA_VERSION"

# Crea la directory out se non esiste
mkdir -p out

# Pulisci compilazioni precedenti
echo "Pulizia directory out/..."
rm -rf out/*

# Trova tutti i file sorgente (esclusi test)
echo ""
echo "Compilazione codice sorgente..."
find . -name "*.java" -not -path "./test/*" -not -path "./out/*" > sources.txt

if [ ! -s sources.txt ]; then
    echo "✗ Nessun file .java trovato!"
    rm -f sources.txt
    exit 1
fi

echo "  File da compilare: $(wc -l < sources.txt | tr -d ' ')"

# Compila
javac -d out -cp "lib/*" @sources.txt 2>&1

if [ $? -eq 0 ]; then
    echo "✓ Compilazione completata con successo!"
    rm -f sources.txt
else
    echo "✗ Errore durante la compilazione"
    rm -f sources.txt
    exit 1
fi

# Compila i test (opzionale)
echo ""
echo "Compilazione test..."

if [ -d "test" ]; then
    find test -name "*.java" > test_sources.txt

    if [ -s test_sources.txt ]; then
        javac -d out -cp "lib/*:out" @test_sources.txt 2>&1

        if [ $? -eq 0 ]; then
            echo "✓ Test compilati con successo!"
        else
            echo "⚠ Warning: alcuni test non compilano (non bloccante)"
        fi
        rm -f test_sources.txt
    else
        echo "  Nessun test trovato"
    fi
else
    echo "  Directory test/ non presente"
fi

# Conta classi generate
CLASS_COUNT=$(find out -name "*.class" | wc -l | tr -d ' ')
echo ""
echo "=== Compilazione terminata ==="
echo "Classi generate: $CLASS_COUNT"
echo "Output: out/"
echo ""
echo "Per avviare il sistema: ./run.sh"
