#!/bin/bash

# Compile first
./scripts/compile.sh

# Check for tmux
if ! command -v tmux &> /dev/null; then
    echo "❌ Error: 'tmux' is not installed."
    echo "💡 Please install it via Homebrew: brew install tmux"
    exit 1
fi

# Cleanup previous runs
echo "🛑 Killing existing processes..."
pkill -f "java.*core.DTIPNode"
pkill -f "java.*client.WebBridge"
sleep 2

# Close old Terminal windows via AppleScript (Cleanup)
echo "🪟 Closing old Terminal windows..."
osascript -e 'tell application "Terminal" to close (every window whose history contains "DTIPNode")' 2>/dev/null || true
osascript -e 'tell application "Terminal" to close (every window whose history contains "WebBridge")' 2>/dev/null || true
sleep 1

# Compile
./scripts/compile.sh

SESSION="dtip"
tmux kill-session -t $SESSION 2>/dev/null

# Create new session
tmux new-session -d -s $SESSION

# Enable Pane Titles
tmux set -g pane-border-status top
tmux set -g pane-border-format " #{pane_title} "

# Rename window
tmux rename-window -t $SESSION:0 'DTIP Cluster'

# Layout: 3x2 Grid (Strict Construction)
# 1. Start with Pane 0. Split vertically to make 3 rows.
tmux split-window -v -p 66 -t $SESSION:0.0   # Top 33% (0) and Bot 66% (1)
tmux split-window -v -p 50 -t $SESSION:0.1   # Split Bot 66% into Mid (1) and Bot (2)

# Now we have 3 full-width rows (Panes 0, 1, 2).
# Split each horizontally to make pairs.
tmux split-window -h -t $SESSION:0.0  # Split Top Row -> Panes 0, 1
tmux split-window -h -t $SESSION:0.2  # Split Mid Row (was 1, now 2 after previous split) -> Panes 2, 3
tmux split-window -h -t $SESSION:0.4  # Split Bot Row (was 2, now 4) -> Panes 4, 5

# Result mapping:
# Row 1: 0 (Banca), 1 (Retail)
# Row 2: 2 (Energia), 3 (Sanità)
# Row 3: 4 (Trasporti), 5 (WebBridge)

# Create logs directory
mkdir -p logs
rm -f logs/*.log
touch logs/combined.log

# Send commands to panes
# Node 0 (Banca)
tmux select-pane -t $SESSION:0.0 -T "🏦 Node 0: BANCA"
tmux send-keys -t $SESSION:0.0 "java -Djava.security.policy=config/local_java.policy -Dgemini.api.key=$GEMINI_API_KEY -cp \"out:lib/*\" core.DTIPNode 0 Banca 5 2>&1 | tee /dev/tty | perl -pe '\$|=1;s/\x1b\[[0-9;]*m//g' | tee logs/node0.log >> logs/combined.log" C-m

# Node 1 (Retail)
tmux select-pane -t $SESSION:0.1 -T "🛒 Node 1: RETAIL"
tmux send-keys -t $SESSION:0.1 "java -Djava.security.policy=config/local_java.policy -Dgemini.api.key=$GEMINI_API_KEY -cp \"out:lib/*\" core.DTIPNode 1 Retail 5 2>&1 | tee /dev/tty | perl -pe '\$|=1;s/\x1b\[[0-9;]*m//g' | tee logs/node1.log >> logs/combined.log" C-m

# Node 2 (Energia)
tmux select-pane -t $SESSION:0.2 -T "⚡ Node 2: ENERGIA"
tmux send-keys -t $SESSION:0.2 "java -Djava.security.policy=config/local_java.policy -Dgemini.api.key=$GEMINI_API_KEY -cp \"out:lib/*\" core.DTIPNode 2 Energia 5 2>&1 | tee /dev/tty | perl -pe '\$|=1;s/\x1b\[[0-9;]*m//g' | tee logs/node2.log >> logs/combined.log" C-m

# Node 3 (Sanità)
tmux select-pane -t $SESSION:0.3 -T "🏥 Node 3: SANITA"
tmux send-keys -t $SESSION:0.3 "java -Djava.security.policy=config/local_java.policy -Dgemini.api.key=$GEMINI_API_KEY -cp \"out:lib/*\" core.DTIPNode 3 Sanità 5 2>&1 | tee /dev/tty | perl -pe '\$|=1;s/\x1b\[[0-9;]*m//g' | tee logs/node3.log >> logs/combined.log" C-m

# Node 4 (Trasporti)
tmux select-pane -t $SESSION:0.4 -T "🚌 Node 4: TRASPORTI"
tmux send-keys -t $SESSION:0.4 "java -Djava.security.policy=config/local_java.policy -Dgemini.api.key=$GEMINI_API_KEY -cp \"out:lib/*\" core.DTIPNode 4 Trasporti 5 2>&1 | tee /dev/tty | perl -pe '\$|=1;s/\x1b\[[0-9;]*m//g' | tee logs/node4.log >> logs/combined.log" C-m

# WebBridge (Pane 5)
tmux select-pane -t $SESSION:0.5 -T "🌐 WEB BRIDGE (Client API)"
tmux send-keys -t $SESSION:0.5 "echo '🚀 Starting WebBridge in 5s...'; sleep 5; java -Djava.security.policy=config/local_java.policy -Dgemini.api.key=$GEMINI_API_KEY -cp \"out:lib/*\" client.WebBridge 2>&1 | tee /dev/tty | perl -pe '\$|=1;s/\x1b\[[0-9;]*m//g' | tee logs/webbridge.log >> logs/combined.log; echo '❌ WebBridge Exited with code $?'; read" C-m

# Attach
tmux attach -t $SESSION
