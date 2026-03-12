# DTIP - Code Flow: Stack Trace su Carta

Questo documento traccia il percorso esatto del codice con **snippet Java reali** e **stato delle variabili**.
Usalo come un debugger su carta per seguire ogni esecuzione.

---

## Indice

1. [Avvio del Sistema](#1-avvio-del-sistema)
2. [Connessione P2P (Full Mesh)](#2-connessione-p2p-full-mesh)
3. [Pubblicazione di un IoC](#3-pubblicazione-di-un-ioc)
4. [Propagazione Gossip](#4-propagazione-gossip)
5. [Votazione e Consenso](#5-votazione-e-consenso)
6. [Scrittura sul Ledger (Ricart-Agrawala)](#6-scrittura-sul-ledger-ricart-agrawala)
7. [Scenari Speciali](#7-scenari-speciali)

---

## 1. Avvio del Sistema

### 1.1 Entry Point: `DTIPNode.main()`

**File:** `core/DTIPNode.java:1002-1081`

```java
// STEP 1: Parsing argomenti
// Comando: java core.DTIPNode 3 Sanita 5
public static void main(String[] args) {
    int nodeId = Integer.parseInt(args[0]);      // nodeId = 3
    String nodeName = args[1];                    // nodeName = "Sanita"
    int totalNodes = Integer.parseInt(args[2]);   // totalNodes = 5
    int rmiPort = 1099 + nodeId;                  // rmiPort = 1102
```

**Stato dopo parsing:**
```
┌─────────────────────────────────┐
│ nodeId     = 3                  │
│ nodeName   = "Sanita"           │
│ totalNodes = 5                  │
│ rmiPort    = 1102               │
└─────────────────────────────────┘
```

### 1.2 Costruttore: `DTIPNode()`

**File:** `core/DTIPNode.java:101-126`

```java
public DTIPNode(int nodeId, String nodeName, int totalNodes) throws RemoteException {
    super();  // UnicastRemoteObject export
    this.nodeId = nodeId;
    this.nodeName = nodeName;
    this.totalNodes = totalNodes;

    // Strutture dati principali
    this.iocDatabase = new ConcurrentHashMap<>();    // Map<String, IoC>
    this.reputations = new ConcurrentHashMap<>();    // Map<Integer, NodeReputation>
    this.vectorClock = new VectorClock(nodeId, totalNodes);  // int[5] = [0,0,0,0,0]
    this.peers = new ConcurrentHashMap<>();          // Map<Integer, DTIPNodeInterface>
    this.mutexManager = new RicartAgrawalaManager(this, nodeId);

    // Inizializza reputazioni per tutti i nodi
    for (int i = 0; i < totalNodes; i++) {
        reputations.put(i, new NodeReputation(i, "Node-" + i));
    }

    // Strategy Pattern per analisi minacce
    this.threatAnalyzer = new CompositeAnalyzer();
}
```

**Stato dopo costruttore:**
```
┌───────────────────────────────────────────────────────────┐
│ DTIPNode (nodeId=3, "Sanita")                             │
├───────────────────────────────────────────────────────────┤
│ iocDatabase  = {}                  (vuoto)                │
│ peers        = {}                  (vuoto)                │
│ vectorClock  = [0, 0, 0, 0, 0]     (5 elementi, tutti 0)  │
│ reputations  = {0: Rep, 1: Rep, 2: Rep, 3: Rep, 4: Rep}   │
│ isOffline    = false                                      │
│ writtenToLedger = Set<String>()    (vuoto)                │
└───────────────────────────────────────────────────────────┘
```

### 1.3 VectorClock Initialization

**File:** `core/VectorClock.java:32-36`

```java
public VectorClock(int nodeId, int numNodes) {
    this.nodeId = nodeId;       // nodeId = 3
    this.numNodes = numNodes;   // numNodes = 5
    this.clock = new int[numNodes];  // clock = [0, 0, 0, 0, 0]
}
```

### 1.4 RMI Binding & Thread Avvio

**File:** `core/DTIPNode.java:1016-1076`

```java
// STEP 2: Crea/Ottieni RMI Registry
Registry registry = LocateRegistry.createRegistry(rmiPort);  // porta 1102
registry.rebind("DTIPNode" + nodeId, node);  // nome: "DTIPNode3"
// Log: "DTIPNode bound on port 1102"

// STEP 3: Avvia thread discovery e health check
new Thread(() -> {
    Thread.sleep(2000);  // Attende 2s per permettere ad altri nodi di avviarsi
    while (true) {
        // Discovery loop
        for (int i = 0; i < totalNodes; i++) {
            if (i == nodeId) continue;  // Salta se stesso
            // Cerca peer...
        }
        Thread.sleep(5000);  // Ogni 5 secondi
    }
}).start();

// STEP 4: Avvia SensorListener
new Thread(new SensorListener(9000 + nodeId, node, nodeId, nodeName)).start();
// SensorListener attivo su porta 9003
```

---

## 2. Connessione P2P (Full Mesh)

### 2.1 Discovery Loop

**File:** `core/DTIPNode.java:1034-1048`

```java
// Eseguito ogni 5 secondi nel thread di discovery
for (int i = 0; i < totalNodes; i++) {
    if (i == nodeId) continue;  // i=3 (me stesso) -> skip

    try {
        // Costruisce URL RMI per il peer
        String url = "//localhost:" + (1099 + i) + "/DTIPNode" + i;
        // Esempio per i=0: "//localhost:1099/DTIPNode0"

        // Lookup RMI
        DTIPNodeInterface peer = (DTIPNodeInterface) Naming.lookup(url);

        // Connetti al peer
        node.connectToPeer(i, peer);
    } catch (Exception e) {
        // Peer non ancora disponibile
    }
}
```

### 2.2 Connessione: `connectToPeer()`

**File:** `core/DTIPNode.java:768-784`

```java
public void connectToPeer(int peerId, DTIPNodeInterface peer) {
    if (!peers.containsKey(peerId)) {
        peers.put(peerId, peer);  // Aggiunge stub RMI
        log("NET", "Connected to Peer " + peerId, ConsoleColors.GREEN);

        // Verifica full mesh
        if (peers.size() == totalNodes - 1) {  // 4 peer = 5-1
            log("NET", "✅ FULL MESH ESTABLISHED: Connected to all 4 peers!");
        }

        // Registra callback sul peer remoto
        try {
            peer.registerPeer(nodeId);  // Notifica: "Sono Node 3"
        } catch (RemoteException e) {}
    }
}
```

**Stato dopo full mesh (Node 3):**
```
┌────────────────────────────────────────────────────────┐
│ peers = {                                              │
│   0: DTIPNodeInterface (stub RMI -> Node0 Banca)       │
│   1: DTIPNodeInterface (stub RMI -> Node1 Retail)      │
│   2: DTIPNodeInterface (stub RMI -> Node2 Energia)     │
│   4: DTIPNodeInterface (stub RMI -> Node4 Trasporti)   │
│ }                                                      │
│ peers.size() = 4  ✓ Full Mesh                          │
└────────────────────────────────────────────────────────┘
```

---

## 3. Pubblicazione di un IoC

### 3.1 Input dal Sensor (TCP)

**File:** `core/SensorListener.java:91-115`

```java
// Input ricevuto su porta TCP 9003:
// "DOMAIN:malware.evil.com:85:ransomware,critical"

private void parseAndPublish(String raw) {
    String[] parts = raw.split(":");
    // parts = ["DOMAIN", "malware.evil.com", "85", "ransomware,critical"]

    IoC.IoCType type = IoC.IoCType.valueOf(parts[0].toUpperCase());
    // type = IoCType.DOMAIN

    String value = parts[1];
    // value = "malware.evil.com"

    int confidence = Integer.parseInt(parts[2]);
    // confidence = 85

    List<String> tags = new ArrayList<>();
    if (parts.length > 3) {
        String[] tagParts = parts[3].split(",");
        for (String t : tagParts) tags.add(t.trim());
    }
    // tags = ["ransomware", "critical"]

    // Crea IoC con costruttore legacy (5 nodi default)
    IoC ioc = new IoC(type, value, confidence, tags, myNodeId, myNodeName + "-Sensor");

    node.publishIoC(ioc);
}
```

### 3.2 Costruttore IoC con Quorum Dinamico

**File:** `model/IoC.java:91-111`

```java
public IoC(IoCType type, String value, int confidence,
        List<String> tags, int publisherId, String publisherName,
        int totalNodes, int activeNodes) {

    // ID deterministico (stesso IoC = stesso ID su tutti i nodi)
    this.id = generateId(type, value);
    // id = Integer.toHexString(("DOMAIN:malware.evil.com").hashCode())
    // id = "a1b2c3d4" (esempio)

    this.type = type;
    this.value = value;
    this.confidence = confidence;
    this.tags = tags;
    this.publisherId = publisherId;          // 3
    this.publisherName = publisherName;      // "Sanita-Sensor"
    this.publishedAt = System.currentTimeMillis();

    this.votes = new ConcurrentHashMap<>();  // Map<Integer, VoteType>
    this.seenBy = new HashSet<>();           // Set<Integer>
    this.status = IoCStatus.PENDING;

    // QUORUM DINAMICO - Fissato al momento della pubblicazione
    this.totalNodes = totalNodes;            // 5
    this.activeNodesAtCreation = activeNodes; // es. 4 (se un nodo offline)
    this.quorumSize = (activeNodes / 2) + 1; // (4/2)+1 = 3
}
```

**Stato IoC appena creato:**
```
┌───────────────────────────────────────────────────────────┐
│ IoC Object                                                │
├───────────────────────────────────────────────────────────┤
│ id                   = "a1b2c3d4"                         │
│ type                 = DOMAIN                             │
│ value                = "malware.evil.com"                 │
│ confidence           = 85                                 │
│ tags                 = ["ransomware", "critical"]         │
│ publisherId          = 3                                  │
│ publisherName        = "Sanita-Sensor"                    │
│ status               = PENDING                            │
│ votes                = {}  (vuoto)                        │
│ seenBy               = {}  (vuoto)                        │
│ vectorClock          = null (non ancora assegnato)        │
│ activeNodesAtCreation = 4                                 │
│ quorumSize           = 3                                  │
└───────────────────────────────────────────────────────────┘
```

### 3.3 Pubblicazione: `publishIoC()`

**File:** `core/DTIPNode.java:133-157`

```java
@Override
public String publishIoC(IoC ioc) throws RemoteException {
    checkOffline();  // Lancia RemoteException se isOffline==true

    // STEP 1: Incrementa Vector Clock (Tick)
    synchronized (vectorClock) {
        vectorClock.tick();  // clock[3]++ → [0,0,0,1,0]
        ioc.setVectorClock(vectorClock.getClock());
    }
    // Log: "[CLOCK] VectorClock: [0, 0, 0, 1, 0]"

    // STEP 2: Marca come visto da me
    ioc.getSeenBy().add(nodeId);  // seenBy = {3}

    // STEP 3: Salva nel database locale
    iocDatabase.put(ioc.getId(), ioc);
    // iocDatabase = {"a1b2c3d4": IoC}

    // Log: "[PUBLISH] IoC a1b2c3d4 - DOMAIN:malware.evil.com"

    // STEP 4: Propaga via Gossip
    propagateIoC(ioc);

    // STEP 5: Schedula auto-voto
    scheduleAutoVote(ioc);

    return ioc.getId();  // "a1b2c3d4"
}
```

**Stato dopo publishIoC:**
```
┌───────────────────────────────────────────────────────────┐
│ Node 3 - Stato Interno                                    │
├───────────────────────────────────────────────────────────┤
│ vectorClock    = [0, 0, 0, 1, 0]  ← incrementato          │
│ iocDatabase    = {"a1b2c3d4": IoC}                        │
│ IoC.seenBy     = {3}                                      │
│ IoC.vectorClock = [0, 0, 0, 1, 0]                         │
└───────────────────────────────────────────────────────────┘
```

### 3.4 VectorClock.tick()

**File:** `core/VectorClock.java:55-57`

```java
public void tick() {
    clock[nodeId]++;  // clock[3] = 0 + 1 = 1
}

// Prima: clock = [0, 0, 0, 0, 0]
// Dopo:  clock = [0, 0, 0, 1, 0]
//                         ↑
//                      nodeId=3
```

---

## 4. Propagazione Gossip

### 4.1 Invio ai Peer: `propagateIoC()`

**File:** `core/DTIPNode.java:207-235`

```java
private void propagateIoC(IoC ioc) {
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        int peerId = entry.getKey();      // es. peerId = 0

        // LOOP PREVENTION: Controlla se peer ha già visto l'IoC
        if (ioc.getSeenBy().contains(peerId)) {
            continue;  // Skip - già visto
        }
        // seenBy = {3}, peerId = 0 → 0 non in {3} → procedi

        // Thread separato per non bloccare
        new Thread(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    DTIPNodeInterface peer = peers.get(peerId);
                    if (peer == null) return;

                    peer.receiveIoC(ioc, nodeId);  // RMI call!
                    return;  // Successo

                } catch (RemoteException e) {
                    if (attempt == 0 && refreshPeerStubSilent(peerId)) {
                        continue;  // Riprova con stub fresco
                    }
                    // Fallimento silenzioso
                }
            }
        }).start();
    }
}
```

**Flusso Gossip (Node 3 pubblica):**
```
       Node 3 (Publisher)
            │
   ┌────────┼────────┬────────┬────────┐
   ▼        ▼        ▼        ▼        ▼
Thread    Thread   Thread   Thread   (me stesso)
   │        │        │        │
   ▼        ▼        ▼        ▼
Node 0   Node 1   Node 2   Node 4
 (RMI)    (RMI)    (RMI)    (RMI)
```

### 4.2 Ricezione: `receiveIoC()` (su Node 0)

**File:** `core/DTIPNode.java:160-197`

```java
@Override
public void receiveIoC(IoC ioc, int fromNodeId) throws RemoteException {
    checkOffline();

    // STEP 1: Deduplicazione
    if (iocDatabase.containsKey(ioc.getId())) {
        return;  // Già visto, ignora
    }

    // STEP 2: Merge Vector Clock
    synchronized (vectorClock) {
        vectorClock.update(ioc.getVectorClock());
    }
    // Vedi dettaglio sotto

    // STEP 3: Marca come visto
    ioc.getSeenBy().add(nodeId);  // seenBy = {3, 0}

    // STEP 4: Salva localmente
    iocDatabase.put(ioc.getId(), ioc);

    // Log: "[GOSSIP] Received IoC a1b2c3d4 from Node 3 - DOMAIN:malware.evil.com"

    // STEP 5: Continua propagazione (Gossip)
    propagateIoC(ioc);  // Invia a Node 1, 2, 4 (non a 3, già in seenBy)

    // STEP 6: Schedula auto-voto
    scheduleAutoVote(ioc);
}
```

### 4.3 VectorClock.update() - Merge

**File:** `core/VectorClock.java:68-76`

```java
public void update(int[] receivedClock) {
    // receivedClock = [0, 0, 0, 1, 0]  (da Node 3)
    // clock locale  = [0, 0, 0, 0, 0]  (Node 0 prima del merge)

    // Merge: prendi il massimo per ogni posizione
    for (int i = 0; i < numNodes; i++) {
        clock[i] = Math.max(clock[i], receivedClock[i]);
    }
    // clock = [max(0,0), max(0,0), max(0,0), max(0,1), max(0,0)]
    // clock = [0, 0, 0, 1, 0]

    // Tick locale
    clock[nodeId]++;  // clock[0]++ → [1, 0, 0, 1, 0]
}
```

**Stato Node 0 dopo ricezione:**
```
┌───────────────────────────────────────────────────────────┐
│ Node 0 - Stato Dopo Ricezione                             │
├───────────────────────────────────────────────────────────┤
│ vectorClock    = [1, 0, 0, 1, 0]                          │
│                   ↑        ↑                              │
│                   │        └── Evento di Node 3           │
│                   └── Evento locale (merge+tick)          │
│                                                           │
│ iocDatabase    = {"a1b2c3d4": IoC}                        │
│ IoC.seenBy     = {3, 0}                                   │
└───────────────────────────────────────────────────────────┘
```

---

## 5. Votazione e Consenso

### 5.1 Analisi e Voto Automatico: `scheduleAutoVote()`

**File:** `core/DTIPNode.java:333-357`

```java
private void scheduleAutoVote(IoC ioc) {
    new Thread(() -> {
        try {
            // Delay casuale 2-5 secondi (simula tempo analisi)
            int delay = 2000 + autoVoteRandom.nextInt(3000);  // es. 3500ms
            Thread.sleep(delay);

            // Controlla stato: se già deciso, esci
            if (ioc.getStatus() == IoCStatus.VERIFIED ||
                ioc.getStatus() == IoCStatus.REJECTED) {
                return;
            }

            // Calcola voto basato su policy
            IoC.VoteType localVote = computeLocalVote(ioc);

            // Log: "[AUTO-VOTE] Local Analysis Complete: CONFIRM for IoC a1b2c3d4"

            // Registra e propaga voto
            vote(ioc.getId(), nodeId, localVote);

        } catch (Exception e) {}
    }).start();
}
```

### 5.2 Calcolo del Voto: `computeLocalVote()`

**File:** `core/DTIPNode.java:366-405`

```java
private IoC.VoteType computeLocalVote(IoC ioc) {
    // STEP 1: Calcola threat score (0-100) via CompositeAnalyzer
    int baseScore = computeThreatScore(ioc);  // es. 75

    int threshold;
    String policy;

    // STEP 2: Policy specifica per nodo
    switch (nodeId) {
        case 0: threshold = 70; policy = "CONSERVATIVE"; break;  // Banca
        case 1: threshold = 30; policy = "AGGRESSIVE";   break;  // Retail
        case 2: threshold = 50; policy = "BALANCED";     break;  // Energia
        case 3: threshold = 10; policy = "PARANOID";     break;  // Sanità
        case 4: threshold = 80; policy = "SKEPTICAL";    break;  // Trasporti
        default: threshold = 50; policy = "DEFAULT";
    }

    // STEP 3: Decisione
    IoC.VoteType vote = baseScore >= threshold
                        ? IoC.VoteType.CONFIRM
                        : IoC.VoteType.REJECT;

    // Log: "[DEBUG] Vote Decision: Score 75 vs Threshold 50 (BALANCED) -> CONFIRM"

    return vote;
}
```

**Esempio decisioni voto per score=75:**
```
┌──────────────────────────────────────────────────────────────┐
│ Score = 75                                                   │
├──────────┬────────────┬───────────┬──────────────────────────┤
│ Node     │ Threshold  │ Policy    │ Vote                     │
├──────────┼────────────┼───────────┼──────────────────────────┤
│ 0 Banca  │ 70         │CONSERVATIVE│ 75 >= 70 → CONFIRM ✓    │
│ 1 Retail │ 30         │AGGRESSIVE │ 75 >= 30 → CONFIRM ✓     │
│ 2 Energia│ 50         │BALANCED   │ 75 >= 50 → CONFIRM ✓     │
│ 3 Sanità │ 10         │PARANOID   │ 75 >= 10 → CONFIRM ✓     │
│ 4 Trasp. │ 80         │SKEPTICAL  │ 75 < 80  → REJECT ✗      │
└──────────┴────────────┴───────────┴──────────────────────────┘
Risultato: 4 CONFIRM, 1 REJECT
```

### 5.3 Registrazione Voto: `vote()`

**File:** `core/DTIPNode.java:241-263`

```java
@Override
public void vote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException {
    checkOffline();

    IoC ioc = iocDatabase.get(iocId);
    if (ioc == null) return;

    IoC.IoCStatus oldStatus = ioc.getStatus();  // PENDING

    // Aggiungi voto
    ioc.addVote(voterId, vote);  // → Aggiorna anche status!

    // Log: "[VOTE] Casting CONFIRM on IoC a1b2c3d4 (My Vote)"

    // Verifica cambio stato
    if (ioc.getStatus() != oldStatus && ioc.getStatus() != IoCStatus.PENDING) {
        handleStatusChange(ioc, oldStatus);  // → Scrittura ledger
    }

    // Propaga voto ai peer
    propagateVote(iocId, voterId, vote);
}
```

### 5.4 Logica Consenso: `IoC.addVote()` e `updateStatus()`

**File:** `model/IoC.java:146-196`

```java
public void addVote(int nodeId, VoteType vote) {
    votes.put(nodeId, vote);
    // Esempio: votes = {3: CONFIRM, 0: CONFIRM}

    // SOC OVERRIDE: nodeId == -1 ha autorità assoluta
    if (nodeId == -1) {
        if (vote == VoteType.CONFIRM) {
            status = IoCStatus.VERIFIED;
        } else {
            status = IoCStatus.REJECTED;
        }
        return;  // Decisione immediata
    }

    updateStatus();  // Ricalcola stato
}

private void updateStatus() {
    // Conta solo voti da nodi reali (nodeId >= 0)
    long nodeConfirms = votes.entrySet().stream()
            .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.CONFIRM)
            .count();

    long nodeRejects = votes.entrySet().stream()
            .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.REJECT)
            .count();

    // REGOLA 1: Maggioranza con quorum FISSO
    if (nodeConfirms >= quorumSize) {  // quorumSize = 3
        status = IoCStatus.VERIFIED;
        return;
    }

    if (nodeRejects >= quorumSize) {
        status = IoCStatus.REJECTED;
        return;
    }

    // REGOLA 2: Stalemate Detection
    long totalVotes = nodeConfirms + nodeRejects;

    if (totalVotes >= activeNodesAtCreation && status == IoCStatus.PENDING) {
        status = IoCStatus.AWAITING_SOC;
        // Tutti hanno votato ma nessun quorum
    }
}
```

**Esempio progressione voti (quorum=3, activeNodes=4):**
```
┌─────────────────────────────────────────────────────────────────┐
│ Progressione Voti                                               │
├─────────┬─────────────────┬────────────┬────────────┬───────────┤
│ Evento  │ votes           │ Confirms   │ Rejects    │ Status    │
├─────────┼─────────────────┼────────────┼────────────┼───────────┤
│ Iniziale│ {}              │ 0          │ 0          │ PENDING   │
│ Node 3  │ {3: CONFIRM}    │ 1          │ 0          │ PENDING   │
│ Node 0  │ {3:C, 0:C}      │ 2          │ 0          │ PENDING   │
│ Node 1  │ {3:C, 0:C, 1:C} │ 3 >= 3 ✓   │ 0          │ VERIFIED  │
└─────────┴─────────────────┴────────────┴────────────┴───────────┘
```

### 5.5 Ricezione Voto Remoto: `receiveVote()`

**File:** `core/DTIPNode.java:266-289`

```java
@Override
public void receiveVote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException {
    checkOffline();

    IoC ioc = iocDatabase.get(iocId);
    if (ioc == null) return;

    IoC.IoCStatus oldStatus;

    // Thread-safe con synchronized
    synchronized (ioc) {
        // IDEMPOTENCY: Se voto già presente, ignora
        if (ioc.getVotes().containsKey(voterId)) {
            return;  // Già processato
        }

        oldStatus = ioc.getStatus();
        ioc.addVote(voterId, vote);  // Registra + updateStatus()
    }

    // Log: "[VOTE] Received CONFIRM on IoC a1b2c3d4 from Node 0"

    // Gestisci cambio stato (fuori dal sync per evitare deadlock)
    if (ioc.getStatus() != oldStatus && ioc.getStatus() != IoCStatus.PENDING) {
        handleStatusChange(ioc, oldStatus);
    }
}
```

---

## 6. Scrittura sul Ledger (Ricart-Agrawala)

### 6.1 Trigger: `handleStatusChange()` → VERIFIED

**File:** `core/DTIPNode.java:428-545`

```java
private void handleStatusChange(IoC ioc, IoC.IoCStatus oldStatus) {
    // Log cambio stato
    // "[CONSENSUS] IoC a1b2c3d4 STATUS CHANGED: PENDING -> VERIFIED"

    if (ioc.getStatus() == IoCStatus.VERIFIED) {
        // Deduplicazione locale
        if (writtenToLedger.contains(ioc.getId())) {
            log("LEDGER", "IoC already written, skipping.");
            return;
        }

        // Thread separato per mutual exclusion
        new Thread(() -> {
            // Double-check
            if (writtenToLedger.contains(ioc.getId())) return;

            // ═══════════════════════════════════════════════════
            // RICHIEDI MUTEX (BLOCKING)
            // ═══════════════════════════════════════════════════
            mutexManager.requestMutex();
            // Log: "[MUTEX] Entered Critical Section"

            // Deduplicazione a livello rete
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
            } catch (IOException e) {}

            if (alreadyInLedger) {
                writtenToLedger.add(ioc.getId());
                mutexManager.releaseMutex();
                return;  // Skip duplicato
            }

            // ═══════════════════════════════════════════════════
            // SCRITTURA CRITICAL SECTION
            // ═══════════════════════════════════════════════════
            Thread.sleep(500);  // Simula latenza I/O

            try (PrintWriter out = new PrintWriter(
                    new FileWriter("shared_ledger.txt", true))) {
                String ts = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                out.printf("[%s] Node %d | %s: %s | ID: %s | VERIFIED%n",
                        ts, nodeId, ioc.getType(), ioc.getValue(), iocIdShort);
                // "[2026-01-28 14:30:00] Node 3 | DOMAIN: malware.evil.com | ID: a1b2c3d4 | VERIFIED"

                writtenToLedger.add(ioc.getId());
                // Log: "[LEDGER] ✅ IoC a1b2c3d4 written to ledger"
            }

            // ═══════════════════════════════════════════════════
            // RILASCIA MUTEX
            // ═══════════════════════════════════════════════════
            mutexManager.releaseMutex();
            // Log: "[MUTEX] Releasing CS"

        }).start();
    }
}
```

### 6.2 Ricart-Agrawala: `requestMutex()`

**File:** `core/RicartAgrawalaManager.java:105-155`

```java
public void requestMutex() {
    // STEP 1: Inizializzazione
    synchronized (lock) {
        requesting = true;
        mySequenceNumber = highestSequenceNumberSeen + 1;  // es. seq = 1
        outstandingReplies = node.getPeerCount();          // 4 peer
        failedPeers.clear();
        receivedRepliesFrom.clear();
    }
    // Log: "[MUTEX] Requesting CS with seq=1"

    // STEP 2: Broadcast REQUEST a tutti i peer
    node.broadcastMutexRequest(mySequenceNumber);

    // STEP 3: Attendi tutte le REPLY (con timeout)
    synchronized (lock) {
        long timeout = Math.max(10000, 3000L * node.getPeerCount() + 2000);
        // timeout = max(10000, 3000*4 + 2000) = 14000ms
        long deadline = System.currentTimeMillis() + timeout;

        while (outstandingReplies > 0) {
            long remaining = deadline - System.currentTimeMillis();

            if (remaining <= 0) {
                // TIMEOUT!
                log("MUTEX", "TIMEOUT! Missing " + outstandingReplies + " replies");
                requesting = false;
                return;  // Esci senza entrare in CS
            }

            lock.wait(remaining);  // Bloccante
        }

        // Tutte le reply ricevute!
        inCriticalSection = true;
    }
    // Log: "[MUTEX] Entered Critical Section"
}
```

**Stato durante requestMutex():**
```
┌───────────────────────────────────────────────────────────────┐
│ RicartAgrawalaManager - Node 3                                │
├───────────────────────────────────────────────────────────────┤
│ requesting         = true                                     │
│ mySequenceNumber   = 1                                        │
│ outstandingReplies = 4  → 3 → 2 → 1 → 0                       │
│ deferredReplies    = []                                       │
│ inCriticalSection  = false → true (quando outstandingReplies=0)│
└───────────────────────────────────────────────────────────────┘
```

### 6.3 Ricart-Agrawala: `handleRequest()` (su peer remoto)

**File:** `core/RicartAgrawalaManager.java:188-215`

```java
public void handleRequest(int requesterId, int sequenceNumber, int timestamp) {
    synchronized (lock) {
        // Aggiorna highest seen
        highestSequenceNumberSeen = Math.max(highestSequenceNumberSeen, sequenceNumber);

        boolean defer = false;

        if (requesting) {
            // Entrambi vogliono la CS → Priority Check

            // Vince: sequence number più basso
            // Tie-breaker: node ID più basso
            boolean weHavePriority =
                (mySequenceNumber < sequenceNumber) ||
                (mySequenceNumber == sequenceNumber && myNodeId < requesterId);

            if (weHavePriority) {
                defer = true;  // Noi abbiamo priorità → defer reply
            }
        }

        if (defer) {
            // Log: "[MUTEX] Deferring reply to Node 3"
            deferredReplies.add(requesterId);
        } else {
            // Rispondi subito
            // Log: "[MUTEX] Granting immediate reply to Node 3"
            node.sendMutexReply(requesterId);
        }
    }
}
```

**Esempio conflitto (Node 3 e Node 4 richiedono insieme):**
```
Node 3: mySeq=2, myId=3        Node 4: mySeq=2, myId=4
     │                              │
     └──────── REQUEST ────────────►│
     ◄──────── REQUEST ─────────────┘

Node 4 riceve REQUEST da Node 3:
  - weHavePriority = (2 < 2) || (2==2 && 4 < 3) = false || false = false
  - !defer → Rispondi subito a Node 3

Node 3 riceve REQUEST da Node 4:
  - weHavePriority = (2 < 2) || (2==2 && 3 < 4) = false || true = true
  - defer → Posticipa reply a Node 4

Risultato: Node 3 entra per primo (ID più basso vince tie-break)
```

### 6.4 Ricart-Agrawala: `handleReply()`

**File:** `core/RicartAgrawalaManager.java:225-247`

```java
public void handleReply(int replierId) {
    synchronized (lock) {
        // Evita doppio conteggio
        if (receivedRepliesFrom.contains(replierId)) {
            return;
        }
        receivedRepliesFrom.add(replierId);

        // Se peer era marcato failed ma ha risposto → già contato
        if (failedPeers.contains(replierId)) {
            return;
        }

        outstandingReplies--;
        // Log: "[MUTEX] Got reply from Node 0. Remaining: 3"

        if (outstandingReplies == 0) {
            lock.notifyAll();  // Sblocca requestMutex()
        }
    }
}
```

### 6.5 Ricart-Agrawala: `releaseMutex()`

**File:** `core/RicartAgrawalaManager.java:163-176`

```java
public void releaseMutex() {
    // Log: "[MUTEX] Releasing CS"

    synchronized (lock) {
        inCriticalSection = false;
        requesting = false;

        // Invia reply posticipate
        for (Integer targetId : deferredReplies) {
            node.sendMutexReply(targetId);
        }
        deferredReplies.clear();
    }
}
```

**Timeline completa Ricart-Agrawala:**
```
Tempo │ Node 3                     │ Node 0, 1, 2, 4
──────┼────────────────────────────┼─────────────────────────────
t0    │ requestMutex()             │
      │ outstandingReplies = 4     │
      │                            │
t1    │ broadcastMutexRequest(1)   │
      │ ─────── REQUEST(seq=1) ───►│
      │                            │
t2    │                            │ handleRequest(3, 1, 0)
      │                            │ requesting = false
      │                            │ → sendMutexReply(3)
      │                            │
t3    │ ◄─────── REPLY ────────────│
      │ handleReply(0)             │
      │ outstandingReplies = 3     │
      │                            │
t4-t6 │ ... (altre reply) ...      │
      │ outstandingReplies = 0     │
      │                            │
t7    │ inCriticalSection = true   │
      │ [MUTEX] Entered CS         │
      │                            │
t8    │ *** SCRITTURA LEDGER ***   │
      │                            │
t9    │ releaseMutex()             │
      │ [MUTEX] Releasing CS       │
```

---

## 7. Scenari Speciali

### 7.1 Stalemate → AWAITING_SOC

**Scenario:** 4 nodi attivi, voti 2-2

```java
// Stato IoC:
activeNodesAtCreation = 4
quorumSize = (4/2) + 1 = 3
votes = {0: CONFIRM, 1: CONFIRM, 2: REJECT, 4: REJECT}

// updateStatus():
nodeConfirms = 2  // < quorumSize (3)
nodeRejects = 2   // < quorumSize (3)
totalVotes = 4    // >= activeNodesAtCreation (4)
status = PENDING  // → AWAITING_SOC
```

**Risoluzione via SOC (voto manuale):**
```java
// Operatore SOC vota
ioc.addVote(-1, VoteType.CONFIRM);

// In addVote():
if (nodeId == -1) {  // -1 = SOC
    status = IoCStatus.VERIFIED;  // Override immediato
    return;
}

// Log: "[CONSENSUS] IoC a1b2c3d4 STATUS CHANGED: AWAITING_SOC -> VERIFIED"
```

### 7.2 Nodo Offline (Chaos Engineering)

**File:** `core/DTIPNode.java:855-879`

```java
@Override
public void setOffline(boolean offline) throws RemoteException {
    this.isOffline = offline;

    if (offline) {
        // Log: "[CHAOS] 💀 FAILURE SIMULATION STARTED - GOING DARK"
    } else {
        // Log: "[CHAOS] ♻️ NODE RESTARTED - PERFORMING SYNC"
        new Thread(this::performSync).start();
    }
}

private void checkOffline() throws RemoteException {
    if (isOffline) {
        throw new RemoteException("Node offline (CHAOS SIMULATION)");
    }
}
```

### 7.3 Recovery Sync: `performSync()`

**File:** `core/DTIPNode.java:899-924`

```java
public void performSync() {
    // STEP 1: Chiedi IoC mancanti a tutti i peer
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        try {
            // Delta Sync basato su Vector Clock
            List<IoC> missing = entry.getValue().sync(vectorClock.getClock());

            for (IoC ioc : missing) {
                if (!iocDatabase.containsKey(ioc.getId())) {
                    iocDatabase.put(ioc.getId(), ioc);
                    vectorClock.update(ioc.getVectorClock());
                }
            }

            if (!missing.isEmpty()) {
                // Log: "[SYNC] Synced 3 IoCs from Peer 0"
            }
        } catch (RemoteException e) {}
    }

    // STEP 2: Vote Recovery - vota su IoC PENDING non ancora votati
    for (IoC ioc : iocDatabase.values()) {
        if (ioc.getStatus() == IoCStatus.PENDING &&
            !ioc.getVotes().containsKey(nodeId)) {
            // Log: "[RECOVERY] Found unvoted IoC - scheduling vote"
            scheduleAutoVote(ioc);
        }
    }
}
```

### 7.4 Peer Failure durante Mutex

**File:** `core/RicartAgrawalaManager.java:77-93`

```java
public void markPeerFailed(int peerId) {
    synchronized (lock) {
        if (requesting && !receivedRepliesFrom.contains(peerId)) {
            failedPeers.add(peerId);

            // Conta come reply implicita (graceful degradation)
            if (outstandingReplies > 0) {
                outstandingReplies--;
                // Log: "[MUTEX] Peer 2 marked failed. Remaining: 2"

                if (outstandingReplies == 0) {
                    lock.notifyAll();  // Procedi comunque
                }
            }
        }
    }
}
```

---

## 8. Riferimento Rapido: Tag dei Log

| Tag | Dove | Significato |
|-----|------|-------------|
| `[NET]` | DTIPNode | Connessioni P2P |
| `[CLOCK]` | DTIPNode | Aggiornamento Vector Clock |
| `[PUBLISH]` | DTIPNode | Nuovo IoC pubblicato |
| `[GOSSIP]` | DTIPNode | IoC ricevuto via gossip |
| `[VOTE]` | DTIPNode | Voto inviato/ricevuto |
| `[DEBUG]` | DTIPNode | Decisione voto (score vs threshold) |
| `[AUTO-VOTE]` | DTIPNode | Completamento analisi automatica |
| `[CONSENSUS]` | DTIPNode | Cambio stato IoC |
| `[MUTEX]` | DTIPNode/RA | Ricart-Agrawala (request/reply/CS) |
| `[LEDGER]` | DTIPNode | Scrittura su shared_ledger.txt |
| `[HEALTH]` | DTIPNode | Stato peer (ONLINE/OFFLINE) |
| `[SYNC]` | DTIPNode | Sincronizzazione post-recovery |
| `[CHAOS]` | DTIPNode | Simulazione guasti |
| `[RECOVERY]` | DTIPNode | Recupero voti mancanti |

---

*Documento aggiornato il 28 Gennaio 2026*
*Per uso interno - Debug e comprensione del sistema*
