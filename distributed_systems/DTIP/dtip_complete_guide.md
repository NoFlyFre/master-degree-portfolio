# DTIP - Guida Completa al Sistema
## Distributed Threat Intelligence Platform

**Versione:** 2.1 - Gennaio 2026
**Autore:** Francesco Caligiuri (con Claude Opus 4.5)

> **Changelog v2.1:** Aggiornato per riflettere il Quorum Dinamico basato sui nodi attivi al momento della pubblicazione dell'IoC.

---

## Indice

1. [Introduzione e Contesto](#1-introduzione-e-contesto)
2. [Architettura del Sistema](#2-architettura-del-sistema)
3. [I Quattro Algoritmi Distribuiti](#3-i-quattro-algoritmi-distribuiti)
4. [Struttura del Codice](#4-struttura-del-codice)
5. [DTIPNode.java - Il Cuore del Sistema](#5-dtipnodejava---il-cuore-del-sistema)
6. [VectorClock.java - Ordinamento Causale](#6-vectorclockjava---ordinamento-causale)
7. [RicartAgrawalaManager.java - Mutua Esclusione](#7-ricartagrawalamanagerjava---mutua-esclusione)
8. [IoC.java - Il Modello Dati](#8-iocjava---il-modello-dati)
9. [Sistema di Analisi Minacce](#9-sistema-di-analisi-minacce)
10. [Flusso Completo di una Minaccia](#10-flusso-completo-di-una-minaccia)
11. [Gestione Guasti e Recovery](#11-gestione-guasti-e-recovery)
12. [Comandi e Utilizzo](#12-comandi-e-utilizzo)
13. [Domande Frequenti per l'Esame](#13-domande-frequenti-per-lesame)

---

## 1. Introduzione e Contesto

### 1.1 Cos'è DTIP?

DTIP (Distributed Threat Intelligence Platform) è un sistema distribuito peer-to-peer per la condivisione e validazione collaborativa di **Indicatori di Compromissione (IoC)** tra organizzazioni.

**Scenario reale:** Immagina 5 organizzazioni (una Banca, un Retailer, un'Azienda Energetica, un Ospedale e un'Azienda di Trasporti) che vogliono proteggersi dagli attacchi informatici. Se la Banca rileva un indirizzo IP malevolo, vuole condividerlo con gli altri. Ma come si fa senza un server centrale di cui tutti si fidano?

**La soluzione:** Un sistema peer-to-peer dove ogni organizzazione è un nodo equivalente. Non c'è un "capo" - tutti collaborano democraticamente per decidere se una minaccia è reale.

### 1.2 Cosa sono gli IoC?

Un **Indicator of Compromise (IoC)** è un artefatto osservato in rete o su un sistema che indica con alta probabilità un'intrusione. Esempi:

| Tipo | Esempio | Significato |
|------|---------|-------------|
| IP | `185.220.101.45` | Indirizzo IP di un server malevolo |
| DOMAIN | `evil-phishing.xyz` | Dominio usato per phishing |
| HASH | `d41d8cd98f00b204e9800998ecf8427e` | Hash MD5 di un file malware |
| URL | `http://malware.com/payload.exe` | URL che distribuisce malware |
| EMAIL | `attacker@evil.com` | Email usata in campagne di phishing |

### 1.3 Il Problema Distribuito

In un sistema centralizzato, un singolo server decide cosa è una minaccia. Ma questo crea problemi:
- **Single Point of Failure**: Se il server muore, tutto si ferma
- **Fiducia**: Chi gestisce il server potrebbe essere compromesso
- **Scalabilità**: Un solo server può diventare un collo di bottiglia

DTIP risolve questi problemi usando 4 algoritmi distribuiti fondamentali:

1. **Vector Clocks** - Per ordinare gli eventi nel tempo
2. **Gossip Protocol** - Per propagare le informazioni
3. **Consensus** - Per votare democraticamente
4. **Ricart-Agrawala** - Per scrivere in modo sicuro sul file condiviso

---

## 2. Architettura del Sistema

### 2.1 Topologia di Rete

```
        ┌─────────────────────────────────────────────────────────────┐
        │                    DTIP P2P NETWORK                          │
        │                                                              │
        │   Node 0 (Banca)  ←──────────→  Node 1 (Retail)              │
        │   RMI: 1099                     RMI: 1100                    │
        │   Sensor: 9000                  Sensor: 9001                 │
        │        ↕                              ↕                      │
        │   Node 2 (Energia) ←─────────→ Node 3 (Sanità)              │
        │   RMI: 1101                     RMI: 1102                    │
        │   Sensor: 9002                  Sensor: 9003                 │
        │        ↕                              ↕                      │
        │             Node 4 (Trasporti)                               │
        │             RMI: 1103                                        │
        │             Sensor: 9004                                     │
        └─────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP :8080
                                    ↓
                            ┌──────────────┐
                            │  WebBridge   │
                            └──────────────┘
                                    │
                                    ↓
                            ┌──────────────┐
                            │ TUI Dashboard│
                            │   (Python)   │
                            └──────────────┘
```

### 2.2 Componenti Principali

| Componente | File | Ruolo |
|------------|------|-------|
| **DTIPNode** | `core/DTIPNode.java` | Nodo peer principale - gestisce tutto |
| **VectorClock** | `core/VectorClock.java` | Orologi logici vettoriali |
| **RicartAgrawalaManager** | `core/RicartAgrawalaManager.java` | Mutua esclusione distribuita |
| **IoC** | `model/IoC.java` | Modello dati per le minacce |
| **CompositeAnalyzer** | `core/CompositeAnalyzer.java` | Analisi minacce (euristiche + LLM) |
| **WebBridge** | `client/WebBridge.java` | API REST per la dashboard |
| **Dashboard** | `tui-python/dashboard.py` | Interfaccia grafica terminale |

### 2.3 Policy dei Nodi

Ogni nodo ha una "personalità" diversa che influenza come vota:

| Node ID | Nome | Policy | Threshold | Comportamento |
|---------|------|--------|-----------|---------------|
| 0 | Banca | CONSERVATIVE | 70 | Molto prudente, vota CONFIRM solo se sicuro |
| 1 | Retail | AGGRESSIVE | 30 | Vota CONFIRM facilmente |
| 2 | Energia | BALANCED | 50 | Equilibrato |
| 3 | Sanità | PARANOID | 10 | Iperprudente, quasi tutto è minaccia |
| 4 | Trasporti | SKEPTICAL | 80 | Molto scettico, serve score alto |

**Esempio:** Se un IoC ha score 60:
- Node 0 (threshold 70): REJECT (60 < 70)
- Node 1 (threshold 30): CONFIRM (60 > 30)
- Node 3 (threshold 10): CONFIRM (60 > 10)

---

## 3. I Quattro Algoritmi Distribuiti

### 3.1 Vector Clocks (Orologi Vettoriali)

**Problema:** In un sistema distribuito, non esiste un "tempo globale". Come sappiamo se l'evento A è successo prima di B?

**Soluzione:** Ogni nodo mantiene un vettore di N contatori (dove N = numero di nodi).

**Esempio con 5 nodi:**
```
Stato iniziale di tutti:  [0, 0, 0, 0, 0]

Node 0 pubblica un IoC:
  - Node 0: [1, 0, 0, 0, 0]  ← incrementa la sua posizione

Node 1 riceve l'IoC:
  - Fa merge: max([0,0,0,0,0], [1,0,0,0,0]) = [1,0,0,0,0]
  - Poi incrementa la sua posizione: [1, 1, 0, 0, 0]

Node 2 riceve da Node 1:
  - Merge: [1, 1, 0, 0, 0]
  - Incrementa: [1, 1, 1, 0, 0]
```

**Relazione Happened-Before:**
- `[1,0,0,0,0]` → `[1,1,0,0,0]` (il primo è avvenuto prima)
- `[1,0,0,0,0]` || `[0,1,0,0,0]` (concorrenti - non c'è ordine)

### 3.2 Gossip Protocol (Propagazione Epidemica)

**Problema:** Come faccio a dire a tutti i nodi che ho visto una minaccia?

**Soluzione:** Propagazione "a pettegolezzo" - ogni nodo che riceve l'informazione la passa agli altri.

**Algoritmo:**
```
Quando pubblico un IoC:
    1. Lo salvo nel mio database locale
    2. Per ogni peer che NON l'ha già visto:
       - Glielo invio in modo asincrono (in un thread separato)

Quando ricevo un IoC:
    1. Se l'ho già visto → ignoro (deduplicazione)
    2. Altrimenti:
       - Aggiorno il mio Vector Clock
       - Lo salvo nel mio database
       - Lo propago agli altri (come sopra)
       - Schedulo il mio voto automatico
```

**Caratteristiche:**
- **Complessità:** O(N log N) messaggi totali
- **Affidabilità:** Anche se un nodo è offline, l'IoC arriva tramite altri percorsi
- **Deduplicazione:** Il set `seenBy` evita loop infiniti

### 3.3 Consenso a Quorum Dinamico (Votazione Democratica)

**Problema:** Chi decide se un IoC è una vera minaccia? E cosa succede se alcuni nodi sono offline?

**Soluzione:** Tutti votano e vince la maggioranza, con **quorum calcolato dinamicamente** in base ai nodi attivi.

**Quorum Dinamico:**
```
Al momento della PUBBLICAZIONE dell'IoC:
  - Si conta quanti nodi sono attivi (activeNodes)
  - Quorum = (activeNodes / 2) + 1
  - Questo valore viene FISSATO nell'IoC (activeNodesAtCreation)

Esempio:
  - 5 nodi attivi → Quorum = 3
  - 4 nodi attivi → Quorum = 3
  - 3 nodi attivi → Quorum = 2
  - 2 nodi attivi → Quorum = 2
```

**Perché è importante:** Se il quorum fosse sempre fisso a 3, con 2 nodi offline e un voto 2-1, non si raggiungerebbe mai il consenso. Il quorum dinamico garantisce che il sistema rimanga operativo anche con nodi offline.

**Algoritmo:**
```
Quando un nodo riceve/pubblica un IoC:
    1. Analizza l'IoC (euristiche + LLM)
    2. Calcola uno score (0-100)
    3. Confronta con la sua threshold (basata sulla policy)
    4. Vota CONFIRM se score >= threshold, altrimenti REJECT
    5. Propaga il voto a tutti

Quando un nodo riceve un voto:
    1. Lo registra nella mappa votes dell'IoC
    2. Conta i voti: CONFIRM vs REJECT
    3. Verifica se si è raggiunto il quorum (fissato alla creazione):
       - Se CONFIRM >= quorum → status = VERIFIED
       - Se REJECT >= quorum → status = REJECTED
    4. STALEMATE DETECTION: Se tutti i nodi attivi (al momento della creazione)
       hanno votato ma nessun quorum è raggiunto → status = AWAITING_SOC
```

**Esempio:**
```
IoC: 185.220.101.45 (IP in range Tor)

Voti:
  Node 0 (Banca):     REJECT  (score 55 < threshold 70)
  Node 1 (Retail):    CONFIRM (score 55 > threshold 30)
  Node 2 (Energia):   CONFIRM (score 55 > threshold 50)
  Node 3 (Sanità):    CONFIRM (score 55 > threshold 10)
  Node 4 (Trasporti): REJECT  (score 55 < threshold 80)

Risultato: 3 CONFIRM vs 2 REJECT → VERIFIED
```

### 3.4 Ricart-Agrawala (Mutua Esclusione Distribuita)

**Problema:** C'è un file condiviso (`shared_ledger.txt`) dove scriviamo le minacce verificate. Se due nodi scrivono contemporaneamente, il file si corrompe.

**Soluzione:** Prima di scrivere, chiedo il permesso a tutti gli altri nodi.

**Algoritmo:**
```
Quando voglio entrare in Sezione Critica (scrivere sul file):
    1. Genero un sequence number = highest_seen + 1
    2. Mando REQUEST(seq) a tutti i peer
    3. Aspetto che tutti rispondano con REPLY
    4. Quando ho ricevuto N-1 REPLY → entro in CS
    5. Scrivo sul file
    6. Mando REPLY a tutti quelli che ho messo in attesa

Quando ricevo un REQUEST:
    1. Se non sto richiedendo la CS → rispondo subito
    2. Se sto richiedendo:
       - Se il mio seq < loro seq → ho priorità, non rispondo (DEFER)
       - Se il mio seq > loro seq → rispondono prima loro
       - Se pareggio → vince il nodeId più basso
```

**Caratteristiche:**
- **Message Complexity:** 2(N-1) messaggi per ogni accesso (N-1 REQUEST + N-1 REPLY)
- **Fairness:** Chi chiede prima (seq number più basso) ha priorità
- **Deadlock Prevention:** Timeout dinamico + gestione nodi falliti

---

## 4. Struttura del Codice

```
DTIP/
├── core/                          # Logica principale
│   ├── DTIPNode.java              # Nodo peer (1069 righe)
│   ├── VectorClock.java           # Orologi vettoriali (127 righe)
│   ├── RicartAgrawalaManager.java # Mutua esclusione (261 righe)
│   ├── CompositeAnalyzer.java     # Analisi a 2 livelli (168 righe)
│   ├── HeuristicAnalyzer.java     # Regole euristiche (230 righe)
│   ├── OllamaAnalyzer.java        # LLM locale
│   ├── GeminiAnalyzer.java        # LLM cloud (fallback)
│   └── SensorListener.java        # Socket per input IoC
│
├── model/                         # Modelli dati
│   ├── IoC.java                   # Indicatore di Compromissione
│   ├── NodeReputation.java        # Reputazione dei nodi
│   ├── MutexState.java            # Stato mutex per monitoring
│   └── NodeEvent.java             # Eventi per logging
│
├── interfaces/                    # Interfacce RMI
│   └── DTIPNodeInterface.java     # Contratto remoto
│
├── client/                        # Client e bridge
│   └── WebBridge.java             # API REST (HTTP 8080)
│
├── tui-python/                    # Dashboard Python
│   └── dashboard.py               # TUI con Textual
│
├── docs/                          # Documentazione
├── scripts/                       # Script di avvio
├── logs/                          # Log dei nodi
└── shared_ledger.txt              # Registro minacce verificate
```

---

## 5. DTIPNode.java - Il Cuore del Sistema

Questa è la classe più importante. Ogni istanza rappresenta un nodo nella rete.

### 5.1 Campi Principali

```java
public class DTIPNode extends UnicastRemoteObject implements DTIPNodeInterface {

    // Identità
    private int nodeId;                    // ID univoco (0-4)
    private String nodeName;               // Nome leggibile ("Banca")

    // Stato
    private Map<String, IoC> iocDatabase;  // Database locale IoC: ID → IoC
    private Map<Integer, NodeReputation> reputations;  // Reputazione nodi
    private VectorClock vectorClock;       // Orologio logico
    private boolean isOffline = false;     // Simulazione guasto

    // Rete
    private Map<Integer, DTIPNodeInterface> peers;  // Stub RMI dei peer
    private RicartAgrawalaManager mutexManager;     // Gestore mutex

    // Anti-duplicati
    private Set<String> writtenToLedger;   // IoC già scritti nel ledger

    // Analisi
    private ThreatAnalyzer threatAnalyzer; // Analizzatore (Composite)
}
```

### 5.2 Metodi Principali - PUBBLICAZIONE

#### `publishIoC(IoC ioc)` - Pubblica un nuovo IoC

```java
/**
 * Pubblica un nuovo IoC nella rete.
 *
 * COSA FA:
 * 1. Incrementa il Vector Clock locale (tick)
 * 2. Attacca il clock all'IoC
 * 3. Marca sé stesso come "ha visto questo IoC"
 * 4. Salva nel database locale
 * 5. Propaga ai peer (Gossip)
 * 6. Schedula il proprio voto automatico
 *
 * @param ioc L'IoC da pubblicare
 * @return L'ID dell'IoC pubblicato
 */
public String publishIoC(IoC ioc) throws RemoteException {
    checkOffline();  // Lancia eccezione se sono "morto"

    // PASSO 1: Aggiorna Vector Clock
    synchronized (vectorClock) {
        vectorClock.tick();                    // VC[myId]++
        ioc.setVectorClock(vectorClock.getClock());  // Attacca al messaggio
    }

    // PASSO 2: Marca come visto da me
    ioc.getSeenBy().add(nodeId);

    // PASSO 3: Salva localmente
    iocDatabase.put(ioc.getId(), ioc);

    // PASSO 4: Propaga (Gossip)
    propagateIoC(ioc);

    // PASSO 5: Schedula auto-voto
    scheduleAutoVote(ioc);

    return ioc.getId();
}
```

#### `receiveIoC(IoC ioc, int fromNodeId)` - Riceve un IoC da un peer

```java
/**
 * Riceve un IoC propagato da un altro nodo.
 *
 * COSA FA:
 * 1. Controlla se l'ho già visto (deduplicazione)
 * 2. Aggiorna Vector Clock (merge + tick)
 * 3. Salva nel database locale
 * 4. Propaga ulteriormente (Gossip)
 * 5. Schedula il proprio voto
 *
 * IMPORTANTE: La deduplicazione evita loop infiniti nel gossip.
 */
public void receiveIoC(IoC ioc, int fromNodeId) throws RemoteException {
    checkOffline();

    // DEDUPLICAZIONE
    if (iocDatabase.containsKey(ioc.getId())) {
        return;  // Già visto, ignoro
    }

    // MERGE VECTOR CLOCK
    synchronized (vectorClock) {
        vectorClock.update(ioc.getVectorClock());  // max + tick
    }

    // Marca come visto
    ioc.getSeenBy().add(nodeId);

    // Salva
    iocDatabase.put(ioc.getId(), ioc);

    // Continua gossip
    propagateIoC(ioc);

    // Vota
    scheduleAutoVote(ioc);
}
```

#### `propagateIoC(IoC ioc)` - Propaga via Gossip

```java
/**
 * Propaga un IoC a tutti i peer che non l'hanno ancora visto.
 *
 * CARATTERISTICHE:
 * - Invio asincrono (ogni peer in un thread separato)
 * - Loop prevention (non mando a chi l'ha già visto)
 * - Retry con refresh stub (se il peer sembra morto, provo a riconnettere)
 */
private void propagateIoC(IoC ioc) {
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        int peerId = entry.getKey();

        // LOOP PREVENTION
        if (ioc.getSeenBy().contains(peerId)) {
            continue;  // Questo peer l'ha già visto
        }

        // INVIO ASINCRONO
        new Thread(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    DTIPNodeInterface peer = peers.get(peerId);
                    if (peer == null) return;

                    peer.receiveIoC(ioc, nodeId);  // Chiamata RMI
                    return;  // Successo!

                } catch (RemoteException e) {
                    if (attempt == 0) {
                        // Primo fallimento: provo a rinfrescare lo stub
                        if (refreshPeerStub(peerId)) {
                            continue;  // Riprova
                        }
                    }
                    // Fallimento finale: log e ignora
                }
            }
        }).start();
    }
}
```

### 5.3 Metodi Principali - VOTAZIONE

#### `vote(String iocId, int voterId, IoC.VoteType vote)` - Registra un voto

```java
/**
 * Registra e propaga un voto su un IoC.
 *
 * FLUSSO:
 * 1. Trova l'IoC nel database
 * 2. Aggiunge il voto
 * 3. Controlla se lo status è cambiato (raggiunto quorum)
 * 4. Propaga il voto a tutti i peer
 */
public void vote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException {
    checkOffline();

    IoC ioc = iocDatabase.get(iocId);
    if (ioc == null) return;

    IoC.IoCStatus oldStatus = ioc.getStatus();

    // REGISTRA VOTO
    ioc.addVote(voterId, vote);

    // CONTROLLA CAMBIO STATUS
    if (ioc.getStatus() != oldStatus && ioc.getStatus() != IoC.IoCStatus.PENDING) {
        handleStatusChange(ioc, oldStatus);  // Potrebbe triggerare scrittura ledger
    }

    // PROPAGA
    propagateVote(iocId, voterId, vote);
}
```

#### `receiveVote(String iocId, int voterId, IoC.VoteType vote)` - Riceve un voto

```java
/**
 * Riceve un voto propagato da un peer.
 *
 * IMPORTANTE: Usa synchronized per evitare race condition!
 * Senza sync, due thread potrebbero aggiungere lo stesso voto due volte.
 */
public void receiveVote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException {
    checkOffline();

    IoC ioc = iocDatabase.get(iocId);
    if (ioc == null) return;

    IoC.IoCStatus oldStatus;

    // SYNCHRONIZED per thread-safety
    synchronized (ioc) {
        // Idempotency check: ho già questo voto?
        if (ioc.getVotes().containsKey(voterId)) {
            return;  // Già ricevuto, ignoro
        }

        oldStatus = ioc.getStatus();
        ioc.addVote(voterId, vote);
    }

    // Controlla cambio status (fuori dal sync per evitare deadlock)
    if (ioc.getStatus() != oldStatus && ioc.getStatus() != IoC.IoCStatus.PENDING) {
        handleStatusChange(ioc, oldStatus);
    }
}
```

#### `scheduleAutoVote(IoC ioc)` - Schedula voto automatico

```java
/**
 * Schedula un voto automatico dopo un delay casuale (2-5 secondi).
 * Il delay simula il tempo di analisi della minaccia.
 */
private void scheduleAutoVote(IoC ioc) {
    new Thread(() -> {
        try {
            // Delay casuale per evitare che tutti votino insieme
            int delay = 2000 + random.nextInt(3000);
            Thread.sleep(delay);

            // Se già deciso, non votare
            if (ioc.getStatus() == IoC.IoCStatus.VERIFIED ||
                ioc.getStatus() == IoC.IoCStatus.REJECTED) {
                return;
            }

            // CALCOLA IL VOTO
            IoC.VoteType localVote = computeLocalVote(ioc);

            // REGISTRA E PROPAGA
            vote(ioc.getId(), nodeId, localVote);

        } catch (Exception e) {
            // Interrotto
        }
    }).start();
}
```

#### `computeLocalVote(IoC ioc)` - Calcola il voto basato sulla policy

```java
/**
 * Calcola il voto locale basandosi su:
 * 1. Threat score (0-100) calcolato dall'analizzatore
 * 2. Threshold del nodo (basata sulla policy)
 */
private IoC.VoteType computeLocalVote(IoC ioc) {
    // STEP 1: Calcola score
    int score = threatAnalyzer.analyze(ioc, reputations);

    // STEP 2: Determina threshold basata sulla policy
    int threshold;
    String policy;

    switch (nodeId) {
        case 0: threshold = 70; policy = "CONSERVATIVE"; break;
        case 1: threshold = 30; policy = "AGGRESSIVE";   break;
        case 2: threshold = 50; policy = "BALANCED";     break;
        case 3: threshold = 10; policy = "PARANOID";     break;
        case 4: threshold = 80; policy = "SKEPTICAL";    break;
        default: threshold = 50; policy = "DEFAULT";
    }

    // STEP 3: Confronta e decidi
    return score >= threshold ? VoteType.CONFIRM : VoteType.REJECT;
}
```

### 5.4 Metodi Principali - GESTIONE CONSENSO

#### `handleStatusChange(IoC ioc, IoC.IoCStatus oldStatus)` - Gestisce cambio stato

```java
/**
 * Chiamato quando un IoC raggiunge il consenso (VERIFIED o REJECTED).
 *
 * SE VERIFIED:
 * 1. Controlla se già scritto nel ledger (deduplicazione locale)
 * 2. Richiede mutex (Ricart-Agrawala)
 * 3. Controlla il file per deduplicazione a livello rete
 * 4. Scrive nel ledger
 * 5. Rilascia mutex
 * 6. Sincronizza status con i peer
 */
private void handleStatusChange(IoC ioc, IoC.IoCStatus oldStatus) {

    if (ioc.getStatus() == IoC.IoCStatus.VERIFIED) {

        // DEDUPLICAZIONE LOCALE
        if (writtenToLedger.contains(ioc.getId())) {
            return;  // Già scritto da me
        }

        // THREAD SEPARATO per non bloccare
        new Thread(() -> {
            // Double-check
            if (writtenToLedger.contains(ioc.getId())) return;

            // RICHIEDI MUTEX
            mutexManager.requestMutex();  // Blocca finché non ho il permesso

            // DEDUPLICAZIONE A LIVELLO RETE
            // Leggo il file e controllo se c'è già questo IoC
            String iocIdShort = ioc.getId().substring(0, 8);
            boolean alreadyInLedger = false;

            try (BufferedReader reader = new BufferedReader(
                    new FileReader("shared_ledger.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("ID: " + iocIdShort)) {
                        alreadyInLedger = true;
                        break;
                    }
                }
            } catch (IOException e) {
                // File non esiste ancora
            }

            if (alreadyInLedger) {
                writtenToLedger.add(ioc.getId());
                mutexManager.releaseMutex();
                return;  // Qualcun altro l'ha già scritto
            }

            // SCRIVI NEL LEDGER
            try (PrintWriter out = new PrintWriter(
                    new FileWriter("shared_ledger.txt", true))) {
                String ts = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                out.printf("[%s] Node %d | %s: %s | ID: %s | VERIFIED%n",
                    ts, nodeId, ioc.getType(), ioc.getValue(), iocIdShort);
                writtenToLedger.add(ioc.getId());
            }

            // RILASCIA MUTEX
            mutexManager.releaseMutex();

        }).start();
    }

    // SINCRONIZZA STATUS con tutti i peer
    for (DTIPNodeInterface peer : peers.values()) {
        new Thread(() -> {
            try {
                peer.syncStatus(ioc.getId(), ioc.getStatus());
            } catch (RemoteException e) {}
        }).start();
    }
}
```

### 5.5 Metodi Principali - MUTUA ESCLUSIONE

#### `broadcastMutexRequest(int sequenceNumber)` - Invia richiesta mutex

```java
/**
 * Invia REQUEST a tutti i peer per entrare in sezione critica.
 *
 * GESTIONE FALLIMENTI:
 * - Se un peer non risponde, prova a rinfrescare lo stub
 * - Se ancora non risponde, lo marca come "fallito"
 * - Il timeout in RicartAgrawalaManager gestirà i peer morti
 */
public void broadcastMutexRequest(int sequenceNumber) {
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        int peerId = entry.getKey();

        new Thread(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    DTIPNodeInterface peer = peers.get(peerId);
                    if (peer == null) break;

                    peer.receiveMutexRequest(nodeId, sequenceNumber, 0);
                    return;  // Successo

                } catch (RemoteException e) {
                    if (attempt == 0) {
                        // Prova refresh
                        if (refreshPeerStub(peerId)) {
                            continue;
                        }
                    }
                    // Fallimento: marca come failed
                    mutexManager.markPeerFailed(peerId);
                }
            }
        }).start();
    }
}
```

#### `sendMutexReply(int targetId)` - Invia REPLY

```java
/**
 * Invia REPLY a un nodo che ha richiesto la CS.
 * Implementa retry con backoff esponenziale.
 */
public void sendMutexReply(int targetId) {
    new Thread(() -> {
        for (int attempt = 0; attempt < 3; attempt++) {
            DTIPNodeInterface peer = peers.get(targetId);
            if (peer == null) return;

            try {
                peer.receiveMutexReply(nodeId);
                return;  // Successo
            } catch (RemoteException e) {
                if (attempt < 2) {
                    refreshPeerStub(targetId);
                    Thread.sleep(100 * (attempt + 1));  // Backoff
                }
            }
        }
    }).start();
}
```

### 5.6 Metodi di Supporto

#### `refreshPeerStub(int peerId)` - Rinfresca connessione RMI

```java
/**
 * Tenta di ottenere un nuovo stub RMI per un peer.
 * Utile quando uno stub diventa "stale" (il peer è stato riavviato).
 */
private boolean refreshPeerStub(int peerId) {
    try {
        String url = "//localhost:" + (1099 + peerId) + "/DTIPNode" + peerId;
        DTIPNodeInterface newStub = (DTIPNodeInterface) Naming.lookup(url);
        peers.put(peerId, newStub);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

#### `performHealthCheck()` - Controllo salute peer

```java
/**
 * Controlla la connettività di tutti i peer.
 * Chiamato periodicamente (ogni 15 secondi).
 */
public void performHealthCheck() {
    List<Integer> deadPeers = new ArrayList<>();

    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        try {
            entry.getValue().ping();  // Ping RMI
        } catch (RemoteException e) {
            // Peer non risponde, prova refresh
            if (!refreshPeerStub(entry.getKey())) {
                deadPeers.add(entry.getKey());  // Confermato morto
            }
        }
    }

    // Rimuovi peer morti
    for (Integer deadPeer : deadPeers) {
        peers.remove(deadPeer);
    }
}
```

#### `performSync()` - Sincronizzazione dopo recovery

```java
/**
 * Sincronizza lo stato dopo un riavvio/recovery.
 * Chiamato quando setOffline(false).
 */
public void performSync() {
    // PULL: Chiedi IoC mancanti a tutti i peer
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        try {
            List<IoC> missing = entry.getValue().sync(vectorClock.getClock());
            for (IoC ioc : missing) {
                if (!iocDatabase.containsKey(ioc.getId())) {
                    iocDatabase.put(ioc.getId(), ioc);
                    vectorClock.update(ioc.getVectorClock());
                }
            }
        } catch (RemoteException e) {}
    }

    // VOTE RECOVERY: Vota su IoC che ho perso
    for (IoC ioc : iocDatabase.values()) {
        if (ioc.getStatus() == IoC.IoCStatus.PENDING &&
            !ioc.getVotes().containsKey(nodeId)) {
            scheduleAutoVote(ioc);
        }
    }
}
```

---

## 6. VectorClock.java - Ordinamento Causale

### 6.1 Struttura

```java
public class VectorClock implements Serializable {
    private int[] clock;     // Array di contatori [0, 0, 0, 0, 0]
    private int nodeId;      // Chi sono io (0-4)
    private int numNodes;    // Quanti nodi ci sono (5)
}
```

### 6.2 Metodi

#### `tick()` - Evento locale

```java
/**
 * Incrementa il contatore locale.
 * QUANDO: Prima di inviare un messaggio o fare un evento locale.
 *
 * Esempio:
 *   Prima:  [3, 1, 2, 0, 1] (io sono Node 0)
 *   Dopo:   [4, 1, 2, 0, 1]
 */
public void tick() {
    clock[nodeId]++;
}
```

#### `update(int[] receivedClock)` - Ricezione messaggio

```java
/**
 * Merge con il clock ricevuto + tick locale.
 *
 * ALGORITMO:
 * 1. Per ogni posizione: prendi il MAX tra locale e ricevuto
 * 2. Incrementa la tua posizione (ricezione è un evento locale)
 *
 * Esempio (io sono Node 1):
 *   Mio clock:      [3, 2, 1, 0, 0]
 *   Ricevuto:       [5, 1, 3, 2, 0]
 *   Dopo merge:     [5, 2, 3, 2, 0]
 *   Dopo tick:      [5, 3, 3, 2, 0]
 */
public void update(int[] receivedClock) {
    if (receivedClock == null || receivedClock.length != numNodes) {
        return;
    }

    // MERGE: max componente per componente
    for (int i = 0; i < numNodes; i++) {
        clock[i] = Math.max(clock[i], receivedClock[i]);
    }

    // TICK: l'evento "ricezione" è locale
    clock[nodeId]++;
}
```

#### `compareTo(VectorClock other)` - Confronto causale

```java
/**
 * Determina la relazione causale tra due clock.
 *
 * REGOLE:
 * - VC1 < VC2 (happened-before) se:
 *     ∀i: VC1[i] ≤ VC2[i]  E  ∃j: VC1[j] < VC2[j]
 * - VC1 > VC2 (happened-after): viceversa
 * - VC1 || VC2 (concurrent): altrimenti
 *
 * @return -1 se this→other, 1 se other→this, 0 se concorrenti
 */
public int compareTo(VectorClock other) {
    boolean thisLess = false;   // Esiste i dove this[i] < other[i]?
    boolean otherLess = false;  // Esiste i dove this[i] > other[i]?

    for (int i = 0; i < numNodes; i++) {
        if (this.clock[i] < other.clock[i]) thisLess = true;
        if (this.clock[i] > other.clock[i]) otherLess = true;
    }

    if (thisLess && !otherLess) return -1;  // this happened-before other
    if (otherLess && !thisLess) return 1;   // other happened-before this
    return 0;  // Concurrent (eventi paralleli)
}
```

---

## 7. RicartAgrawalaManager.java - Mutua Esclusione

### 7.1 Struttura

```java
public class RicartAgrawalaManager {
    private DTIPNode node;           // Riferimento al nodo padre
    private int myNodeId;            // Il mio ID

    // Stato
    private boolean requesting;      // Sto richiedendo la CS?
    private int mySequenceNumber;    // Il mio numero di sequenza
    private int highestSequenceNumberSeen;  // Il più alto visto finora

    // Coordinazione
    private int outstandingReplies;  // Quante REPLY mi mancano
    private boolean inCriticalSection;
    private List<Integer> deferredReplies;  // Chi devo rispondere dopo

    // Fault tolerance
    private Set<Integer> failedPeers;       // Peer che sono morti
    private Set<Integer> receivedRepliesFrom;  // Chi ha già risposto
}
```

### 7.2 Metodi

#### `requestMutex()` - Richiedi accesso alla CS

```java
/**
 * Richiede l'accesso alla sezione critica.
 * BLOCCA finché non ottengo il permesso da tutti (o timeout).
 *
 * ALGORITMO:
 * 1. Genera sequence number = highest_seen + 1
 * 2. Invia REQUEST a tutti i peer
 * 3. Attendi REPLY da tutti (con timeout dinamico)
 * 4. Se timeout → abort (evita deadlock)
 * 5. Se successo → entra in CS
 */
public void requestMutex() {
    synchronized (lock) {
        requesting = true;
        mySequenceNumber = highestSequenceNumberSeen + 1;
        outstandingReplies = node.getPeerCount();
        failedPeers.clear();
        receivedRepliesFrom.clear();
    }

    // BROADCAST REQUEST
    node.broadcastMutexRequest(mySequenceNumber);

    // ATTENDI REPLY
    synchronized (lock) {
        // Timeout dinamico: 3s per peer + 2s buffer
        long timeout = Math.max(10000, 3000L * node.getPeerCount() + 2000);
        long deadline = System.currentTimeMillis() + timeout;

        while (outstandingReplies > 0) {
            long remaining = deadline - System.currentTimeMillis();

            if (remaining <= 0) {
                // TIMEOUT!
                requesting = false;
                outstandingReplies = 0;
                return;  // Esci senza entrare in CS
            }

            try {
                lock.wait(remaining);  // Aspetta notifica o timeout
            } catch (InterruptedException e) {
                return;
            }
        }

        // SUCCESSO: tutte le reply ricevute
        inCriticalSection = true;
    }
}
```

#### `handleRequest(int requesterId, int sequenceNumber, int timestamp)` - Gestisci REQUEST

```java
/**
 * Gestisce una REQUEST ricevuta da un peer.
 *
 * LOGICA DI PRIORITÀ:
 * - Se non sto richiedendo → rispondo subito
 * - Se sto richiedendo:
 *   - Se il mio seq < loro seq → ho priorità, DEFER
 *   - Se il mio seq > loro seq → loro hanno priorità, REPLY
 *   - Se pareggio → vince nodeId più basso
 */
public void handleRequest(int requesterId, int sequenceNumber, int timestamp) {
    synchronized (lock) {
        // Aggiorna highest seen
        highestSequenceNumberSeen = Math.max(highestSequenceNumberSeen, sequenceNumber);

        boolean defer = false;

        if (requesting) {
            // CHI HA PRIORITÀ?
            boolean weHavePriority =
                (mySequenceNumber < sequenceNumber) ||
                (mySequenceNumber == sequenceNumber && myNodeId < requesterId);

            if (weHavePriority) {
                defer = true;  // Li faccio aspettare
            }
        }

        if (defer) {
            deferredReplies.add(requesterId);  // Rispondo dopo
        } else {
            node.sendMutexReply(requesterId);  // Rispondo subito
        }
    }
}
```

#### `handleReply(int replierId)` - Gestisci REPLY

```java
/**
 * Gestisce una REPLY ricevuta.
 * Quando outstandingReplies arriva a 0, sblocca il thread in attesa.
 */
public void handleReply(int replierId) {
    synchronized (lock) {
        // Evita doppio conteggio
        if (receivedRepliesFrom.contains(replierId)) {
            return;
        }
        receivedRepliesFrom.add(replierId);

        // Se era marcato come fallito ma ha risposto, ignora
        if (failedPeers.contains(replierId)) {
            return;
        }

        outstandingReplies--;

        if (outstandingReplies == 0) {
            lock.notifyAll();  // Sblocca requestMutex()
        }
    }
}
```

#### `releaseMutex()` - Rilascia la CS

```java
/**
 * Esce dalla sezione critica.
 * Invia REPLY a tutti i nodi che erano in attesa (deferred).
 */
public void releaseMutex() {
    synchronized (lock) {
        inCriticalSection = false;
        requesting = false;

        // Invia reply a chi aspettava
        for (Integer targetId : deferredReplies) {
            node.sendMutexReply(targetId);
        }
        deferredReplies.clear();
    }
}
```

#### `markPeerFailed(int peerId)` - Marca peer come fallito

```java
/**
 * Chiamato quando un peer non risponde e il refresh fallisce.
 * Decrementa outstandingReplies per evitare deadlock.
 */
public void markPeerFailed(int peerId) {
    synchronized (lock) {
        if (requesting && !receivedRepliesFrom.contains(peerId)) {
            failedPeers.add(peerId);

            if (outstandingReplies > 0) {
                outstandingReplies--;

                if (outstandingReplies == 0) {
                    lock.notifyAll();
                }
            }
        }
    }
}
```

---

## 8. IoC.java - Il Modello Dati

### 8.1 Struttura

```java
public class IoC implements Serializable {
    // IDENTITÀ
    private String id;          // ID univoco (hash di type+value)
    private IoCType type;       // IP, DOMAIN, HASH, URL, EMAIL, CVE
    private String value;       // "185.220.101.45"

    // METADATA
    private int confidence;     // 0-100
    private List<String> tags;  // ["ransomware", "c2"]

    // ORIGINE
    private int publisherId;    // Chi l'ha pubblicato
    private String publisherName;
    private long publishedAt;   // Timestamp

    // PROPAGAZIONE
    private int[] vectorClock;  // Clock al momento della pubblicazione
    private Set<Integer> seenBy; // Nodi che l'hanno già visto

    // CONSENSO
    private Map<Integer, VoteType> votes;  // NodeID → CONFIRM/REJECT
    private IoCStatus status;   // PENDING, VERIFIED, REJECTED, AWAITING_SOC
    private int quorumSize;     // Calcolato dinamicamente: (activeNodes/2)+1
    private int totalNodes;     // Numero totale di nodi nella rete
    private int activeNodesAtCreation; // Nodi attivi al momento della pubblicazione (FISSO)
}
```

**Nota sul Quorum Dinamico:** Il campo `activeNodesAtCreation` viene fissato al momento della pubblicazione dell'IoC e NON cambia mai. Questo garantisce che tutti i nodi usino lo stesso valore per calcolare il consenso, evitando inconsistenze.

### 8.2 Metodi

#### `addVote(int nodeId, VoteType vote)` - Registra voto

```java
/**
 * Registra un voto e aggiorna lo status.
 *
 * CASO SPECIALE: nodeId = -1 è il SOC (operatore umano)
 * Il voto SOC ha priorità assoluta e decide immediatamente.
 */
public void addVote(int nodeId, VoteType vote) {
    votes.put(nodeId, vote);

    // SOC OVERRIDE
    if (nodeId == -1) {
        if (vote == VoteType.CONFIRM) {
            status = IoCStatus.VERIFIED;
        } else {
            status = IoCStatus.REJECTED;
        }
        return;  // Non serve contare i voti
    }

    updateStatus();
}
```

#### `updateStatus()` - Calcola status da voti

```java
/**
 * Calcola lo status basandosi sui voti ricevuti.
 *
 * LOGICA (con Quorum Dinamico):
 * 1. MAJORITY RULE: Se CONFIRM >= quorum → VERIFIED
 * 2. MAJORITY RULE: Se REJECT >= quorum → REJECTED
 * 3. STALEMATE: Se tutti i nodi attivi (activeNodesAtCreation) hanno votato
 *    ma nessun quorum raggiunto → AWAITING_SOC
 *
 * NOTA: quorumSize e activeNodesAtCreation sono FISSI al momento della
 * pubblicazione per garantire consistenza tra tutti i nodi.
 */
private void updateStatus() {
    // Conta solo i voti dei nodi (non SOC, che ha nodeId = -1)
    long nodeConfirms = votes.entrySet().stream()
        .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.CONFIRM)
        .count();
    long nodeRejects = votes.entrySet().stream()
        .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.REJECT)
        .count();

    // 1. Majority Rule con Quorum FISSO
    if (nodeConfirms >= quorumSize) {
        status = IoCStatus.VERIFIED;
        return;
    }
    if (nodeRejects >= quorumSize) {
        status = IoCStatus.REJECTED;
        return;
    }

    // 2. Stalemate Detection
    // Se TUTTI i nodi attivi al momento della creazione hanno votato
    // ma nessuno ha raggiunto il quorum → escalation a SOC
    long totalVotes = nodeConfirms + nodeRejects;
    if (totalVotes >= activeNodesAtCreation && status == IoCStatus.PENDING) {
        status = IoCStatus.AWAITING_SOC;
    }
}
```

**Esempio di Stalemate:**
```
Scenario: 4 nodi attivi alla creazione (quorum = 3)
Voti: 2 CONFIRM, 2 REJECT
Risultato: Nessuno ha 3 voti, ma tutti hanno votato → AWAITING_SOC
```

---

## 9. Sistema di Analisi Minacce

### 9.1 Architettura a Due Livelli

```
                    ┌─────────────────────┐
                    │  CompositeAnalyzer  │
                    └──────────┬──────────┘
                               │
           ┌───────────────────┴───────────────────┐
           ↓                                       ↓
    ┌──────────────┐                      ┌──────────────┐
    │  Heuristic   │  Score 40-60?        │     LLM      │
    │  Analyzer    │ ─────────────────────→  (Ollama/    │
    │  (Fast)      │   "Uncertain zone"   │   Gemini)    │
    └──────────────┘                      └──────────────┘
           │                                       │
           ↓                                       ↓
      Score 0-100                            Score 0-100
           │                                       │
           └───────────────────┬───────────────────┘
                               ↓
                    Final Score = 0.4*Heuristic + 0.6*LLM
```

### 9.2 HeuristicAnalyzer - Regole Veloci

L'analizzatore euristico calcola uno score basandosi su:

| Fattore | Contributo |
|---------|------------|
| **Confidence** | 90+: +45, 70+: +30, 50+: +15 |
| **Tipo IoC** | HASH: +25, DOMAIN: +15, IP: +10 |
| **Keyword Matching** | "malware", "phishing": +50, "login", "bank": +25 |
| **TLD Sospetti** | .xyz, .tk, .ml: +20 |
| **Range IP Malevoli** | 185.220.x.x (Tor): +35 |
| **Whitelist** | google.com, 8.8.8.8: -50/-60 |
| **Reputazione Publisher** | >80% accuracy: +20, <30%: -15 |

### 9.3 LLM Analyzer - Analisi Semantica

Quando lo score euristico è tra 40-60 ("zona incerta"), viene consultato un LLM:

1. **Ollama (preferito)**: LLM locale, gratuito, privacy totale
2. **Gemini (fallback)**: Cloud, richiede API key

L'LLM riceve un prompt del tipo:
```
Analyze this Indicator of Compromise:
Type: DOMAIN
Value: secure-login-portal.org
Confidence: 75

Rate the threat level from 0 (safe) to 100 (malicious).
```

---

## 10. Flusso Completo di una Minaccia

### Esempio: IP 185.220.101.50 (Tor Exit Node)

```
FASE 1: PUBBLICAZIONE
━━━━━━━━━━━━━━━━━━━━━━
[Node 3 - Sanità] Pubblica IoC via sensor socket (porta 9003)
  → Genera ID: "985dc388" (hash di "IP:185.220.101.50")
  → Vector Clock: [0,0,0,1,0] (incrementa posizione 3)
  → Status: PENDING
  → Propaga via Gossip a tutti i peer

FASE 2: GOSSIP
━━━━━━━━━━━━━━━
Node 3 → Node 0, Node 1, Node 2, Node 4 (parallelo)
  ↓
Ogni nodo:
  → Riceve IoC
  → Merge Vector Clock: max(mio, ricevuto) + tick
  → Salva in database locale
  → (Non ri-propaga perché tutti l'hanno già visto)

FASE 3: VOTAZIONE
━━━━━━━━━━━━━━━━━━
Ogni nodo analizza l'IoC:
  HeuristicAnalyzer:
    - Confidence 95: +45
    - Tipo IP: +10
    - Range 185.220.x.x (Tor): +35
    = Score: 90

Voti basati sulle policy:
  Node 0 (threshold 70): 90 > 70 → CONFIRM
  Node 1 (threshold 30): 90 > 30 → CONFIRM
  Node 2 (threshold 50): 90 > 50 → CONFIRM
  Node 3 (threshold 10): 90 > 10 → CONFIRM
  Node 4 (threshold 80): 90 > 80 → CONFIRM

FASE 4: CONSENSO
━━━━━━━━━━━━━━━━
Node 4 (il primo a raggiungere quorum):
  → Conta: 3 CONFIRM, 0 REJECT
  → 3 >= quorum (3) → Status: VERIFIED!
  → Triggera handleStatusChange()

FASE 5: SCRITTURA LEDGER (Ricart-Agrawala)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Node 4:
  → requestMutex()
    → mySeq = 1
    → Invia REQUEST(seq=1) a tutti
    → Attendi REPLY...

Node 0,1,2,3:
  → Ricevono REQUEST
  → Non stanno richiedendo → REPLY immediato

Node 4:
  → Riceve 4 REPLY
  → outstandingReplies = 0
  → Entra in Critical Section
  → Legge shared_ledger.txt (deduplicazione)
  → IoC non presente → SCRIVE
  → releaseMutex()

FASE 6: SINCRONIZZAZIONE
━━━━━━━━━━━━━━━━━━━━━━━━━
Node 4 invia syncStatus("985dc388", VERIFIED) a tutti
Altri nodi aggiornano il loro database locale

RISULTATO FINALE:
  shared_ledger.txt:
  [2026-01-28 03:31:48] Node 4 | IP: 185.220.101.50 | ID: 985dc388 | VERIFIED
```

---

## 11. Gestione Guasti e Recovery

### 11.1 Tipi di Guasto Gestiti

| Guasto | Rilevamento | Gestione |
|--------|-------------|----------|
| **Nodo Offline** | RemoteException su chiamata RMI | Retry con stub refresh, poi mark as failed |
| **Stub Stale** | RemoteException dopo riavvio nodo | refreshPeerStub() |
| **Timeout Mutex** | Deadline scaduta | Abort richiesta, non entra in CS |
| **Peer Morto durante REQUEST** | markPeerFailed() | Conta come REPLY implicita |

### 11.2 Health Check Periodico

Ogni 15 secondi, ogni nodo:
1. Pinga tutti i peer
2. Se un peer non risponde → prova refresh stub
3. Se ancora non risponde → rimuove dalla lista peer

### 11.3 Recovery dopo Crash

Quando un nodo torna online (`setOffline(false)`):
1. Esegue `performSync()`
2. Chiede a tutti i peer gli IoC che ha perso
3. Confronta Vector Clock per capire cosa è "nuovo"
4. Vota sugli IoC PENDING che non aveva votato

---

## 12. Comandi e Utilizzo

### 12.1 Avvio Sistema

```bash
# Compila
javac -d out -cp "lib/*" interfaces/*.java util/*.java model/*.java core/*.java client/*.java

# Avvia nodi (in terminali separati)
java -Djava.security.policy=policy/policy.all -cp "out:lib/*" core.DTIPNode 0 Banca 5
java -Djava.security.policy=policy/policy.all -cp "out:lib/*" core.DTIPNode 1 Retail 5
java -Djava.security.policy=policy/policy.all -cp "out:lib/*" core.DTIPNode 2 Energia 5
java -Djava.security.policy=policy/policy.all -cp "out:lib/*" core.DTIPNode 3 Sanità 5
java -Djava.security.policy=policy/policy.all -cp "out:lib/*" core.DTIPNode 4 Trasporti 5

# Avvia WebBridge
java -cp "out:lib/*" client.WebBridge

# Avvia Dashboard
cd tui-python && python dashboard.py
```

### 12.2 Iniettare IoC via Sensor

```bash
# Formato: TYPE:VALUE:CONFIDENCE[:TAGS]
echo "IP:185.220.101.50:90" | nc localhost 9000
echo "DOMAIN:evil-phishing.xyz:85:phishing,suspicious" | nc localhost 9001
echo "HASH:d41d8cd98f00b204e9800998ecf8427e:95:ransomware" | nc localhost 9002
```

### 12.3 Comandi Dashboard TUI

| Tasto | Azione |
|-------|--------|
| `I` | Inietta nuovo IoC |
| `K` | Killa un nodo (simula crash) |
| `R` | Resuscita un nodo |
| `S` | Salva log |
| `Q` | Esci |

---

## 13. Domande Frequenti per l'Esame

### Q1: "Come gestite la concorrenza sul file condiviso?"

**R:** Usiamo l'algoritmo di Ricart-Agrawala per garantire mutua esclusione distribuita. Prima di scrivere su `shared_ledger.txt`, un nodo:
1. Genera un sequence number
2. Invia REQUEST a tutti i peer
3. Attende REPLY da tutti
4. Solo quando ha tutte le risposte entra nella Critical Section
5. Dopo aver scritto, rilascia e invia REPLY ai nodi in attesa

La priorità è data dal sequence number (più basso = più priorità), con tie-breaking sul nodeId.

### Q2: "Come evitate i duplicati nel ledger?"

**R:** Implementiamo deduplicazione a tre livelli:
1. **In-memory locale**: Set `writtenToLedger` traccia gli IoC già scritti da questo nodo
2. **Pre-write check**: Prima di scrivere, leggiamo il file per verificare che l'IoC non sia già presente
3. **ID deterministico**: L'ID è un hash di tipo+valore, quindi lo stesso IoC ha sempre lo stesso ID

### Q3: "Cosa succede se un nodo muore durante il protocollo?"

**R:** Abbiamo diversi meccanismi di fault tolerance:
1. **Retry con stub refresh**: Se una chiamata RMI fallisce, proviamo a ottenere un nuovo stub dal registry
2. **Timeout dinamico**: Il mutex ha un timeout che scala con il numero di peer
3. **markPeerFailed**: I peer irraggiungibili vengono contati come REPLY implicite per evitare deadlock
4. **Health check periodico**: Ogni 15 secondi verifichiamo la connettività

### Q4: "Come scalerebbe a 1000 nodi?"

**R:**
- **Gossip**: Scala bene (O(N log N))
- **Vector Clock**: Diventa costoso (O(N) spazio per messaggio)
- **Ricart-Agrawala**: Non scala (O(N) messaggi per CS)

Per reti grandi servirebbe:
- Sostituire Vector Clock con Hybrid Logical Clocks
- Sostituire Ricart-Agrawala con Maekawa (quorum √N) o Raft

### Q5: "Come funziona l'analisi delle minacce?"

**R:** Usiamo un sistema a due livelli (Strategy Pattern):
1. **Tier 1 - Euristico**: Analisi veloce basata su regole (keyword matching, IP ranges, TLD sospetti)
2. **Tier 2 - LLM**: Se lo score euristico è incerto (40-60), consultiamo un LLM locale (Ollama) o cloud (Gemini)

Il risultato finale è una media pesata: 40% euristico + 60% LLM.

---

### Q6: "Come gestite il consenso quando alcuni nodi sono offline?"

**R:** Implementiamo un **Quorum Dinamico** basato sui nodi attivi al momento della pubblicazione:

1. **Al momento della pubblicazione**, contiamo quanti nodi sono attivi (`activeNodesAtCreation`)
2. Il quorum viene calcolato come `(activeNodes / 2) + 1`
3. Questo valore viene **fissato nell'IoC** e non cambia mai

Questo garantisce:
- **Consistenza**: Tutti i nodi usano lo stesso quorum per lo stesso IoC
- **Operatività**: Il sistema funziona anche con nodi offline
- **Stalemate Detection**: Se tutti i nodi attivi hanno votato senza raggiungere il quorum, l'IoC passa in `AWAITING_SOC`

**Esempio:** Con 4 nodi attivi su 5, il quorum è 3. Se i voti sono 2-2, viene richiesto l'intervento del SOC.

---

*Documento generato il 28 Gennaio 2026*
*Versione 2.1 - Include Quorum Dinamico, fix per race condition, deduplicazione ledger, e fault tolerance migliorata*
