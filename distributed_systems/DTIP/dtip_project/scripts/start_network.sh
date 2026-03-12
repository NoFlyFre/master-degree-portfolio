#!/bin/bash

# === DTIP Network Startup Script (5 Nodes with Separate RMI Ports) ===
# Ogni nodo crea il proprio RMI Registry sulla porta 1099 + nodeId

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "📂 Working directory: $PROJECT_DIR"

# === CLEANUP ===
echo "🧹 Cleaning up old processes..."
# Kill WebBridge/API running on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null

# Kill Java Nodes
pkill -f "java.*DTIPNode" 2>/dev/null
pkill -f "WebBridge" 2>/dev/null
sleep 1

# Close old Terminal windows via AppleScript
echo "🪟 Closing old Terminal windows..."
osascript -e 'tell application "Terminal" to close (every window whose history contains "DTIPNode")' 2>/dev/null
sleep 1

# === SETUP LOGS ===
LOGS_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOGS_DIR"
echo "📝 Resetting log files in $LOGS_DIR..."
> "$LOGS_DIR/node_0.log"
> "$LOGS_DIR/node_1.log"
> "$LOGS_DIR/node_2.log"
> "$LOGS_DIR/node_3.log"
> "$LOGS_DIR/node_4.log"
> "$LOGS_DIR/webbridge.log"
> "$LOGS_DIR/combined.log"
echo "$(date '+%Y-%m-%d %H:%M:%S') - Network Started" >> "$LOGS_DIR/combined.log"

# === COMPILE ===
if [ -f "./scripts/compile.sh" ]; then
    ./scripts/compile.sh
fi

if [ ! -d "out" ]; then
    echo "❌ Error: 'out/' directory not found."
    exit 1
fi

echo ""
echo "🚀 Starting 5-Node Network (Porte RMI: 1099-1103)..."
echo ""

# === LOAD ENVIRONMENT ===
if [ -f ".env" ]; then
    echo "📋 Loading .env configuration..."
    export $(grep -v '^#' .env | xargs)
    if [ -n "$GEMINI_API_KEY" ]; then
        echo "🤖 Gemini API key loaded (LLM fallback enabled)"
    fi
fi

# CLASSPATH with Lanterna
CLASSPATH="out:lib/lanterna-3.1.1.jar"

# Java options
JAVA_OPTS="-Djava.security.policy=config/local_java.policy"
if [ -n "$GEMINI_API_KEY" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dgemini.api.key=$GEMINI_API_KEY"
fi

# Helper to open new terminal tab with logging (Mac)
open_tab_with_log() {
    local cmd="$1"
    local logfile="$2"
    osascript -e "tell application \"Terminal\" to do script \"cd '$PROJECT_DIR'; export GEMINI_API_KEY='$GEMINI_API_KEY'; $cmd 2>&1 | tee -a '$logfile' '$LOGS_DIR/combined.log'; exit\""
}

# Node 0 (Banca) - Conservative threshold 70 - Port 1099
echo "🏦 Starting Node 0 (Banca) on port 1099..."
open_tab_with_log "java $JAVA_OPTS -cp $CLASSPATH core.DTIPNode 0 Banca 5" "$LOGS_DIR/node_0.log"
sleep 2

# Node 1 (Retail) - Aggressive threshold 30 - Port 1100
echo "🛒 Starting Node 1 (Retail) on port 1100..."
open_tab_with_log "java $JAVA_OPTS -cp $CLASSPATH core.DTIPNode 1 Retail 5" "$LOGS_DIR/node_1.log"
sleep 2

# Node 2 (Energia) - Balanced threshold 50 - Port 1101
echo "⚡ Starting Node 2 (Energia) on port 1101..."
open_tab_with_log "java $JAVA_OPTS -cp $CLASSPATH core.DTIPNode 2 Energia 5" "$LOGS_DIR/node_2.log"
sleep 2

# Node 3 (Sanità) - Paranoid threshold 10 - Port 1102
echo "🏥 Starting Node 3 (Sanità) on port 1102..."
open_tab_with_log "java $JAVA_OPTS -cp $CLASSPATH core.DTIPNode 3 Sanità 5" "$LOGS_DIR/node_3.log"
sleep 2

# Node 4 (Trasporti) - Skeptical threshold 80 - Port 1103
echo "🚌 Starting Node 4 (Trasporti) on port 1103..."
open_tab_with_log "java $JAVA_OPTS -cp $CLASSPATH core.DTIPNode 4 Trasporti 5" "$LOGS_DIR/node_4.log"
sleep 2

echo ""
echo "✅ 5-Node Network Launched!"
echo ""
echo "📊 Porte RMI:"
echo "   Node 0 (Banca):     1099"
echo "   Node 1 (Retail):    1100"
echo "   Node 2 (Energia):   1101"
echo "   Node 3 (Sanità):    1102"
echo "   Node 4 (Trasporti): 1103"
echo ""

# === START WEBBRIDGE ===
echo "🌐 Starting WebBridge API on port 8080..."
sleep 2
open_tab_with_log "java -cp $CLASSPATH client.WebBridge" "$LOGS_DIR/webbridge.log"
sleep 2

echo ""
echo "✅ WebBridge API started on http://localhost:8080"
echo ""
echo "📝 Logs disponibili in: $LOGS_DIR/"
echo "   - node_0.log ... node_4.log (singoli nodi)"
echo "   - webbridge.log"
echo "   - combined.log (tutti insieme)"
echo ""
echo "📌 Per lanciare la TUI Dashboard:"
echo "   ./scripts/run_dashboard.sh"
echo ""
echo "👀 Per seguire i log in tempo reale:"
echo "   tail -f $LOGS_DIR/combined.log"
echo ""
