# DTIP - Distributed Threat Intelligence Platform

![Java](https://img.shields.io/badge/Java-11%2B-ED8B00?logo=java&logoColor=white)
![Python](https://img.shields.io/badge/Python-3-3776AB?logo=python&logoColor=white)
![RMI](https://img.shields.io/badge/Distributed-RMI-blue)

A distributed system designed for real-time sharing and analysis of **Indicators of Compromise (IoC)** across a peer-to-peer network.

## 📁 Project Structure

```
dtip_project/
├── core/               # Main system components
│   ├── DTIPNode.java           # Principal P2P Node
│   ├── VectorClock.java        # Fidge/Mattern Vector Clock implementation
│   ├── RicartAgrawalaManager.java  # Distributed Mutual Exclusion
│   ├── SensorListener.java     # TCP listener for IoC injection
│   └── *Analyzer.java          # Threat analysis strategies (Heuristic, Gemini, Ollama)
├── model/              # Data models and structures
├── client/             # Testing clients (WebBridge, AutomatedSOC)
├── scripts/            # Automation scripts for network startup and TUI
├── tui-python/         # Graphical TUI Dashboard implemented in Python
├── util/               # System utilities and metrics collectors
├── interfaces/         # RMI Interface definitions
├── test/               # Unit and integration tests
├── lib/                # External libraries (Lanterna, JUnit)
├── config/             # Java security policies
└── compile.sh          # Main compilation script
```

## 🚀 Quick Start

### 1. Compilation
Ensure executable permissions are set:
```bash
chmod +x compile.sh scripts/*.sh
./compile.sh
```

### 2. Automatic Network Startup (macOS)
The system includes an AppleScript-based automation that launches 5 nodes and a WebBridge in separate terminals.
```bash
./scripts/start_network.sh
```

### 2b. Manual Startup (Linux / Windows)
Open separate terminals for each node. Use the `-cp` (classpath) flag appropriately.
*Note: On Windows, use a semicolon (`;`) as the classpath separator instead of a colon (`:`).*

Example for Node 0:
```bash
java -Djava.security.policy=config/local_java.policy -cp "out:lib/lanterna-3.1.1.jar" core.DTIPNode 0 Bank 5
```

### 3. Real-time Dashboard
To visualize the network status, ensure Python 3 is installed and run:
```bash
./scripts/run_dashboard.sh
```

### 4. Testing IoC Injection
Inject indicators via TCP to test propagation and analysis:
```bash
# Format: TYPE:VALUE:CONFIDENCE[:TAGS]
echo "IP:192.168.1.100:85:malware" | nc localhost 9000
echo "DOMAIN:evil.com:90:phishing,critical" | nc localhost 9001
```

## 📊 Implemented Algorithms

- **Ricart-Agrawala**: Distributed mutual exclusion for write-access to the shared ledger.
- **Vector Clock**: Causal ordering of events across the network.
- **Gossip Protocol**: Probabilistic propagation of IoCs for high availability and fault tolerance.

## 🎓 Academic Context
This project was developed for the **Distributed Systems** course (A.A. 2025/2026) as part of the Master's Degree in Computer Science at **UNIMORE**.
