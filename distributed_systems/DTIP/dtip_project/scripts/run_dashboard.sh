#!/bin/bash

# === DTIP Python TUI Dashboard ===

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TUI_DIR="$PROJECT_DIR/tui-python"

cd "$TUI_DIR"

# Check if venv exists
if [ ! -d "venv" ]; then
    echo "🔧 Creating Python virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    echo "📦 Installing dependencies..."
    pip install -r requirements.txt
else
    source venv/bin/activate
fi

echo "🖥️  Launching DTIP Dashboard..."
echo ""
python dashboard.py
