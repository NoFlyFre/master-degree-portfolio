# DTIP - Distributed Threat Intelligence Platform

Sistema distribuito per la condivisione e l'analisi di Indicatori di Compromissione (IoC) in tempo reale.

## 📁 Struttura del Progetto

```
Progetto_Consegna/
├── core/               # Componenti principali del sistema
│   ├── DTIPNode.java           # Nodo P2P principale
│   ├── VectorClock.java        # Clock vettoriale (Fidge/Mattern)
│   ├── RicartAgrawalaManager.java  # Mutua esclusione distribuita
│   ├── SensorListener.java     # Listener TCP per IoC
│   └── *Analyzer.java          # Analizzatori minacce
├── model/              # Modelli dati
├── client/             # Client per testare il sistema (WebBridge, AutomatedSOC)
├── scripts/            # Script di automazione (Avvio rete, Dashboard)
├── tui-python/         # Dashboard grafica in Python
├── util/               # Utility di sistema
├── interfaces/         # Interfacce RMI
├── test/               # Test unitari e di integrazione
├── lib/                # Librerie esterne (.jar)
├── config/             # Policy di sicurezza Java
├── docs/               # Documentazione PDF e Javadoc
└── compile.sh          # Script di compilazione principale
```

## 🚀 Quick Start

### 1. Compilazione

```bash
chmod +x compile.sh scripts/*.sh
./compile.sh
```

### 2. Esecuzione (Network Automatica - macOS)

Il sistema include uno script che avvia automaticamente 5 nodi e il WebBridge. 
*Nota: Lo script utilizza `osascript` ed è progettato per **macOS**.*

```bash
./scripts/start_network.sh
```

### 2b. Esecuzione Manuale (Linux / Windows)

Se non utilizzi macOS, puoi avviare i nodi manualmente aprendo **terminali separati** per ogni componente (dalla root del progetto).

**Nota per Windows:** Sostituire i due punti (`:`) nel classpath (`-cp`) con il punto e virgola (`;`).

**Terminale 1 (Nodo 0 - Banca):**
```bash
java -Djava.security.policy=config/local_java.policy -cp "out:lib/lanterna-3.1.1.jar" core.DTIPNode 0 Banca 5
```

**Terminale 2 (Nodo 1 - Retail):**
```bash
java -Djava.security.policy=config/local_java.policy -cp "out:lib/lanterna-3.1.1.jar" core.DTIPNode 1 Retail 5
```

**Terminale 3 (Nodo 2 - Energia):**
```bash
java -Djava.security.policy=config/local_java.policy -cp "out:lib/lanterna-3.1.1.jar" core.DTIPNode 2 Energia 5
```

**Terminale 4 (Nodo 3 - Sanità):**
```bash
java -Djava.security.policy=config/local_java.policy -cp "out:lib/lanterna-3.1.1.jar" core.DTIPNode 3 Sanità 5
```

**Terminale 5 (Nodo 4 - Trasporti):**
```bash
java -Djava.security.policy=config/local_java.policy -cp "out:lib/lanterna-3.1.1.jar" core.DTIPNode 4 Trasporti 5
```

**Terminale 6 (WebBridge - Opzionale):**
```bash
java -cp "out:lib/lanterna-3.1.1.jar" client.WebBridge
```

### 3. Dashboard TUI

Per visualizzare lo stato della rete in tempo reale (richiede Python 3):

```bash
./scripts/run_dashboard.sh
```

### 4. Iniezione IoC di Test

Una volta avviato il sistema, puoi iniettare IoC via TCP per testare la propagazione:

```bash
# Formato: TYPE:VALUE:CONFIDENCE[:TAGS]
echo "IP:192.168.1.100:85:malware" | nc localhost 9000
echo "DOMAIN:evil.com:90:phishing,critical" | nc localhost 9001
```

## 📊 Algoritmi Implementati

| Algoritmo | Scopo | File |
|-----------|-------|------|
| **Ricart-Agrawala** | Mutua esclusione per scrittura ledger | `RicartAgrawalaManager.java` |
| **Vector Clock** | Ordinamento causale eventi | `VectorClock.java` |
| **Gossip Protocol** | Propagazione IoC nella rete | `DTIPNode.propagateIoC()` |

## 📖 Documentazione

- **PDF Completo**: `docs/Documentazione_DTIP.pdf`
- **Javadoc**: `docs/javadoc/index.html`

## 🔧 Requisiti

- Java 11 o superiore
- Python 3 (per la dashboard opzionale)
- Librerie incluse in `lib/`:
  - JUnit Platform Console Standalone
  - Lanterna 3.1.1 (TUI)

## 🎓 Autore

**Francesco Caligiuri**
Corso di Algoritmi Distribuiti - A.A. 2025/2026

