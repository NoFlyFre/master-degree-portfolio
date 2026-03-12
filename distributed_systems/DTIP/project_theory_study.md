# Studio Teorico Applicato al Progetto DTIP
## Algoritmi Distribuiti - A.A. 2025/2026
## Prof. Cabri / Prof. Montangero

---

# PARTE 1: SISTEMI DISTRIBUITI - Fondamenti

## 1.1 Definizione di Sistema Distribuito

### Teoria (Slide 01 - Cabri)

> *"Un vero sistema distribuito e' un insieme di elementi computazionali indipendenti che appaiono come un unico sistema agli utenti."* - Tanenbaum

**Architettura Distribuita**: insieme di elementi computazionali (processori) che:
- **NON condividono memoria** (loosely-coupled)
- **NON condividono clock**
- Comunicano attraverso un sistema di comunicazione

**Problemi dei sistemi centralizzati:**
1. **Bottleneck**: il punto centrale riceve tutte le richieste
2. **SPOF (Single Point of Failure)**: se il centro fallisce, tutto il sistema si blocca

### Applicazione in DTIP

Nel progetto DTIP, abbiamo esattamente questa architettura:

```
┌─────────┐    ┌─────────┐    ┌─────────┐
│  Banca  │────│ Retail  │────│ Energia │
│ (Node0) │    │ (Node1) │    │ (Node2) │
└────┬────┘    └────┬────┘    └────┬────┘
     │              │              │
     └──────────────┼──────────────┘
                    │
     ┌──────────────┼──────────────┐
     │              │              │
┌────┴────┐    ┌────┴────┐
│ Sanita  │────│Trasporti│
│ (Node3) │    │ (Node4) │
└─────────┘    └─────────┘
```

- **5 nodi indipendenti**: ognuno con propria memoria e clock
- **Rete mesh completa**: ogni nodo puo' comunicare con tutti gli altri
- **Nessun coordinatore centrale**: evita SPOF e bottleneck

---

## 1.2 Obiettivi dei Sistemi Distribuiti

### Teoria

| Obiettivo | Descrizione |
|-----------|-------------|
| **Resource Access** | Accesso a risorse locali e remote |
| **Transparency** | Nascondere dettagli all'utente |
| **Openness** | Conformita' a standard aperti |
| **Scalability** | Adattarsi a carichi crescenti |

**Tipi di Trasparenza:**
- **Access**: differenze nella rappresentazione dati
- **Location**: dove si trova una risorsa
- **Migration**: movimento di una risorsa
- **Replication**: numero di copie di una risorsa
- **Failure**: guasto e recupero di una risorsa

### Applicazione in DTIP

**Trasparenza implementata:**

| Tipo | Come in DTIP |
|------|--------------|
| Location | Il nodo non sa su quale macchina fisica risiede un peer |
| Replication | Ogni IoC e' replicato su tutti i nodi via Gossip |
| Failure | Il sistema continua a funzionare se un nodo crasha |

**Scalabilita':**
- **Comunicazione asincrona**: i nodi non si bloccano aspettando risposte
- **Gossip Protocol**: propagazione efficiente O(N) invece di broadcast centralizzato

---

# PARTE 2: COMUNICAZIONE

## 2.1 Message Passing

### Teoria (Slide 02 - Cabri)

In un sistema distribuito senza memoria condivisa (**Local Environment Model**), la comunicazione avviene tramite **message passing**.

**Primitive base:**
- `send(destinatario, messaggio)`
- `receive(mittente, messaggio)`

**Identificazione:**
- **Diretta**: mittente e destinatario si conoscono esplicitamente
- **Indiretta**: attraverso una mailbox intermedia

**Dimensione del canale:**
- **Size 0**: send sincrono (bloccante)
- **Size N**: send asincrono con buffer
- **Size infinito**: sempre asincrono

### Applicazione in DTIP

In DTIP usiamo **RMI** che astrae il message passing, ma internamente:

```java
// Nel SensorListener.java - Message Passing diretto via Socket
ServerSocket serverSocket = new ServerSocket(9000 + nodeId);
Socket clientSocket = serverSocket.accept();  // BLOCKING receive
BufferedReader in = new BufferedReader(
    new InputStreamReader(clientSocket.getInputStream())
);
String message = in.readLine();  // Riceve il messaggio
```

La comunicazione tra nodi usa invece RMI (vedi sezione successiva).

---

## 2.2 Socket

### Teoria

I Socket BSD sono il meccanismo base per la comunicazione in rete.

**Primitive Java:**

| Classe | Metodo | Significato |
|--------|--------|-------------|
| ServerSocket | constructor | Crea socket lato server |
| ServerSocket | accept() | Attende connessioni (BLOCCANTE) |
| Socket | constructor | Crea socket lato client |
| Socket | getInputStream() | Stream per ricevere dati |
| Socket | getOutputStream() | Stream per inviare dati |

### Applicazione in DTIP

Il `SensorListener` usa Socket TCP per ricevere IoC esterni:

```java
// File: core/SensorListener.java

public void run() {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
        while (running) {
            Socket clientSocket = serverSocket.accept();  // BLOCCANTE

            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );

            String line = in.readLine();
            // Formato: TYPE:VALUE:CONFIDENCE:TAGS
            // Esempio: "IP:192.168.1.100:85:malware"

            parseAndPublish(line);
        }
    }
}
```

**Porte utilizzate:**
- Node 0 (Banca): porta 9000
- Node 1 (Retail): porta 9001
- Node 2 (Energia): porta 9002
- Node 3 (Sanita): porta 9003
- Node 4 (Trasporti): porta 9004

---

## 2.3 Limitazioni del Message Passing

### Teoria

> *"Solo stream o pacchetti di byte, basso livello, difficile da programmare"*

Problemi:
- Il programmatore deve definire il protocollo nei dettagli
- Lunghezza dei messaggi
- Significato del contenuto
- Separatori dei parametri

**Soluzione**: meccanismi ad alto livello come **RPC** (Remote Procedure Call) e **RMI** (Remote Method Invocation).

### Perche' in DTIP usiamo RMI

Se usassimo solo Socket, dovremmo:
1. Serializzare manualmente ogni oggetto IoC
2. Definire un protocollo binario per ogni tipo di messaggio
3. Gestire errori di parsing

Con RMI, invece, invochiamo metodi remoti come se fossero locali.

---

## 2.4 RMI - Remote Method Invocation

### Teoria (Slide 05 - Cabri)

**Modello locale vs remoto:**
- **Locale**: tutti gli oggetti risiedono nella stessa JVM
- **Remoto**: un oggetto puo' invocare metodi di oggetti in altre JVM

**Caratteristiche chiave:**

1. **Granularita'**: l'unita' minima indirizzabile e' l'oggetto
2. **Trasparenza**: stessa sintassi per chiamate locali e remote
3. **Omogenea**: solo Java (pro: piu' efficiente, contro: meno flessibile)

**Passaggio parametri:**

| Tipo | Passaggio |
|------|-----------|
| Tipi primitivi | Per valore (copia) |
| Oggetti locali | Per valore (serializzazione, deep copy) |
| Oggetti remoti | Per riferimento (copia dello stub) |

### Architettura RMI

```
         2. lookup
    ┌──────────────────┐
    │    RMI Registry  │
    │   (porta 1099)   │
    └────────┬─────────┘
             │ 3. Remote reference
    ┌────────┴─────────┐
    │                  │
    ▼                  │
┌───────┐         ┌────┴────┐
│ Client│◄────────│  Server │
│       │ 4. call │         │
└───────┘         └─────────┘
             │
         1. bind
```

### Applicazione in DTIP

**Interfaccia remota:**

```java
// File: interfaces/DTIPNodeInterface.java

public interface DTIPNodeInterface extends Remote {

    // Pubblicazione IoC (chiamato dal SensorListener)
    String publishIoC(IoC ioc) throws RemoteException;

    // Ricezione IoC via Gossip
    void receiveIoC(IoC ioc, int fromNodeId) throws RemoteException;

    // Votazione
    void vote(String iocId, IoC.VoteType vote, int voterId)
        throws RemoteException;

    // Mutua esclusione
    void requestMutex(int requesterId, int sequenceNumber, int timestamp)
        throws RemoteException;
    void replyMutex(int replierId) throws RemoteException;

    // Sincronizzazione
    List<IoC> sync(int[] remoteVectorClock) throws RemoteException;
}
```

**Registrazione sul Registry:**

```java
// File: core/DTIPNode.java (costruttore)

// Crea il registry sulla porta 1099 + nodeId
LocateRegistry.createRegistry(1099 + nodeId);

// Esporta l'oggetto come remoto
DTIPNodeInterface stub = (DTIPNodeInterface)
    UnicastRemoteObject.exportObject(this, 0);

// Registra con nome "DTIPNode"
Registry registry = LocateRegistry.getRegistry(1099 + nodeId);
registry.rebind("DTIPNode", stub);
```

**Connessione ai peer:**

```java
// File: core/DTIPNode.java (initPeers)

for (int i = 0; i < totalNodes; i++) {
    if (i != nodeId) {
        Registry peerRegistry = LocateRegistry.getRegistry(
            "localhost", 1099 + i);
        DTIPNodeInterface peer = (DTIPNodeInterface)
            peerRegistry.lookup("DTIPNode");
        peers.put(i, peer);
    }
}
```

**Porte RMI:**
- Node 0: 1099
- Node 1: 1100
- Node 2: 1101
- Node 3: 1102
- Node 4: 1103

---

## 2.5 RemoteException

### Teoria

> *"Ogni metodo remoto deve dichiarare che puo' lanciare RemoteException"*

Questo riduce la trasparenza ma aumenta la sicurezza:
- Il client sa quali metodi sono remoti
- Il client deve gestire fallimenti di rete

### Applicazione in DTIP

```java
// Ogni chiamata remota e' in try-catch
try {
    peer.receiveIoC(ioc, nodeId);
} catch (RemoteException e) {
    // Il peer potrebbe essere offline
    log("GOSSIP", "Peer " + peerId + " unreachable", RED);

    // Provo a riconnettermi
    refreshPeerStub(peerId);
}
```

---

# PARTE 3: SINCRONIZZAZIONE

## 3.1 Il Problema del Clock

### Teoria (Slide 02 - Cabri)

> *"In un sistema centralizzato c'e' un solo clock, quindi il tempo e' lo stesso per tutti i processi."*

In un sistema distribuito:
- Ogni nodo ha il proprio clock
- I clock possono avere **drift** (deriva)
- Non esiste un "tempo globale" condiviso

**Esempio del problema (Make):**
- Se il clock del server di compilazione e' indietro rispetto al client
- Un file sorgente potrebbe apparire piu' vecchio dell'eseguibile
- Make non ricompila quando dovrebbe!

### Applicazione in DTIP

In DTIP non sincronizziamo i clock fisici, ma usiamo **clock logici** per ordinare gli eventi.

---

## 3.2 Algoritmo di Lamport

### Teoria

> *"La sincronizzazione fisica dei clock non e' strettamente necessaria. Se due processi non interagiscono, i loro clock non hanno bisogno di sincronizzazione."*

**Relazione Happens-Before (→):**
- Se `a` e `b` appartengono allo stesso processo, e `a` viene prima di `b`, allora `a → b`
- Se `a` e' l'invio di un messaggio e `b` e' la ricezione dello stesso, allora `a → b`
- Transitivita': se `a → b` e `b → c`, allora `a → c`

**Algoritmo:**
1. Ogni processo mantiene un contatore `C`
2. Prima di ogni evento locale: `C = C + 1`
3. Ogni messaggio porta il timestamp `T` del mittente
4. Alla ricezione: `C = max(C, T) + 1`

**Limitazione di Lamport:**
- Se `C(a) < C(b)`, NON possiamo concludere che `a → b`
- Potrebbe essere che `a || b` (concorrenti)

---

## 3.3 Vector Clock

### Teoria

> *"Un algoritmo piu' complesso e' il Vector Timestamp. Si basa su vettori con tanti elementi quanti sono i processi."*

**Vantaggi rispetto a Lamport:**
- Possiamo determinare la relazione causale esatta
- `VC1 < VC2` implica `evento1 → evento2`
- Se ne' `VC1 < VC2` ne' `VC2 < VC1`, gli eventi sono **concorrenti**

**Regole:**
1. **Inizializzazione**: `VC = [0, 0, 0, 0, 0]`
2. **Evento locale (tick)**: `VC[i] = VC[i] + 1`
3. **Invio messaggio**: prima tick, poi allega `VC` al messaggio
4. **Ricezione messaggio**: `VC[j] = max(VC[j], received[j])` per ogni j, poi tick

### Applicazione in DTIP

```java
// File: core/VectorClock.java

public class VectorClock implements Serializable {
    private int[] clock;    // [0, 0, 0, 0, 0]
    private int nodeId;     // Indice di questo nodo
    private int numNodes;   // 5

    // Evento locale
    public void tick() {
        clock[nodeId]++;
    }

    // Ricezione messaggio
    public void update(int[] receivedClock) {
        for (int i = 0; i < numNodes; i++) {
            clock[i] = Math.max(clock[i], receivedClock[i]);
        }
        clock[nodeId]++;  // Tick dopo merge
    }

    // Confronto per happens-before
    public int compareTo(VectorClock other) {
        boolean thisLess = false;
        boolean otherLess = false;

        for (int i = 0; i < numNodes; i++) {
            if (this.clock[i] < other.clock[i]) thisLess = true;
            if (this.clock[i] > other.clock[i]) otherLess = true;
        }

        if (thisLess && !otherLess) return -1;  // this → other
        if (otherLess && !thisLess) return 1;   // other → this
        return 0;  // Concorrenti
    }
}
```

**Esempio di esecuzione:**

```
Node 0 pubblica IoC:
  Prima: [0, 0, 0, 0, 0]
  tick() → [1, 0, 0, 0, 0]
  Allega VC all'IoC

Node 2 riceve IoC:
  Prima: [0, 0, 0, 0, 0]
  update([1,0,0,0,0]) → max + tick
  Dopo: [1, 0, 1, 0, 0]
```

---

# PARTE 4: MUTUA ESCLUSIONE DISTRIBUITA

## 4.1 Il Problema

### Teoria (Slide 02 - Cabri)

> *"L'accesso alle regioni critiche da parte di processi distribuiti deve essere regolato."*

In ambienti centralizzati: monitor, semafori
In ambienti distribuiti: **algoritmi distribuiti**

**Algoritmo centralizzato:**
- Un coordinatore gestisce l'accesso
- Problema: SPOF!

**Algoritmo distribuito (Ricart-Agrawala):**
1. Chi vuole entrare in CS manda REQUEST a tutti con timestamp
2. Chi riceve REQUEST:
   - Se non interessato → risponde OK
   - Se in CS → non risponde (defer)
   - Se anche lui vuole → confronta timestamp, il minore vince
3. Chi riceve tutti gli OK entra in CS
4. All'uscita, manda OK a tutti quelli in coda

### Complessita' messaggi

- **Centralizzato**: 3 messaggi (request, grant, release)
- **Ricart-Agrawala**: 2(N-1) messaggi
  - N-1 REQUEST
  - N-1 REPLY

---

## 4.2 Implementazione in DTIP

### Quando serve il Mutex

In DTIP, la mutua esclusione serve per scrivere sul **ledger condiviso**:

```
IoC raggiunge VERIFIED → Node rileva cambio stato
                       → Richiede mutex
                       → Entra in Critical Section
                       → Scrive su shared_ledger.txt
                       → Rilascia mutex
```

### Codice

```java
// File: core/RicartAgrawalaManager.java

public void requestMutex() {
    synchronized (lock) {
        requesting = true;
        mySequenceNumber = highestSequenceNumberSeen + 1;
        outstandingReplies = node.getPeerCount();  // 4
    }

    // Broadcast REQUEST
    node.broadcastMutexRequest(mySequenceNumber);

    // Attendi tutte le REPLY
    synchronized (lock) {
        while (outstandingReplies > 0) {
            lock.wait();  // BLOCCANTE
        }
        inCriticalSection = true;
    }
}
```

**Gestione conflitti (priorita'):**

```java
public void handleRequest(int requesterId, int seqNum, int timestamp) {
    synchronized (lock) {
        highestSequenceNumberSeen = Math.max(highestSequenceNumberSeen, seqNum);

        boolean defer = false;

        if (requesting) {
            // Chi ha priorita'?
            boolean weHavePriority =
                (mySequenceNumber < seqNum) ||
                (mySequenceNumber == seqNum && myNodeId < requesterId);

            if (weHavePriority) {
                defer = true;  // Rimando la risposta
            }
        }

        if (defer) {
            deferredReplies.add(requesterId);
        } else {
            node.sendMutexReply(requesterId);
        }
    }
}
```

**Rilascio:**

```java
public void releaseMutex() {
    synchronized (lock) {
        inCriticalSection = false;
        requesting = false;

        // Invia tutte le reply posticipate
        for (Integer targetId : deferredReplies) {
            node.sendMutexReply(targetId);
        }
        deferredReplies.clear();
    }
}
```

---

# PARTE 5: TOLLERANZA AI GUASTI

## 5.1 Classificazione dei Guasti

### Teoria (Slide 06 - Montangero)

> *"L'affidabilita' totale e' praticamente INESISTENTE nei sistemi reali!"*

**Per causa:**
- **Execution faults**: errori durante l'esecuzione
- **Transmission faults**: malfunzionamento trasmissione
- **Component failure**: disattivazione componente

**Per durata:**
- **Transient**: appaiono e scompaiono da soli
- **Permanent**: esistono finche' non riparati

**Per estensione:**
- **Localized**: solo alcuni componenti
- **Ubiquitous**: tutti i componenti eventualmente

---

## 5.2 Modelli di Guasto

### Teoria

**Entity Failure (ordinati per pericolosita'):**

| Tipo | Descrizione | Pericolosita' |
|------|-------------|---------------|
| **Crash (fail-stop)** | L'entita' funziona poi si ferma | Bassa |
| **Omission** | Perde occasionalmente messaggi | Media |
| **Byzantine** | L'entita' puo' fare QUALSIASI cosa | Alta |

**Link Failure:**
- **Omission**: messaggio inviato mai consegnato
- **Addition**: messaggio consegnato mai inviato
- **Corruption**: messaggio consegnato diverso dall'inviato

### Applicazione in DTIP

In DTIP assumiamo solo guasti di tipo **Crash**:
- Un nodo funziona correttamente, poi si spegne
- Non invia messaggi corrotti o malevoli

```java
// File: core/DTIPNode.java

private volatile boolean offline = false;  // Simula crash

private void checkOffline() throws RemoteException {
    if (offline) {
        throw new RemoteException("Node is offline");
    }
}

// Chiamato dalla dashboard per simulare guasto
public void setOffline(boolean offline) {
    this.offline = offline;
}
```

---

## 5.3 Connettivita' e Resilienza

### Teoria

> *"Se k link arbitrari possono crashare, e' impossibile fare broadcast a meno che la rete non sia (k+1)-edge-connected"*

| Rete | Node Connectivity | Edge Connectivity |
|------|-------------------|-------------------|
| Albero | 1 | 1 |
| Anello | 2 | 2 |
| Grafo completo | n-1 | n-1 |

### Applicazione in DTIP

DTIP usa una **rete mesh completa** (grafo completo):
- 5 nodi, ognuno connesso a tutti gli altri
- Node connectivity = 4
- Puo' tollerare fino a 3 guasti simultanei

---

## 5.4 Consenso

### Teoria

> *"Problema del Consenso: ogni entita' deve decidere su un valore, e tutte devono decidere lo stesso valore."*

**Vincoli:**
1. **Agreement**: tutte le entita' decidono lo stesso valore
2. **Non-triviality**: se tutti i valori iniziali sono uguali, la decisione deve essere quel valore
3. **Termination**: tutte le entita' eventualmente decidono

**Teorema dei Due Generali:**
> *"Il problema dei due generali non ha soluzione, anche se il sistema e' completamente sincrono."*

### Applicazione in DTIP

In DTIP implementiamo il consenso tramite **votazione a maggioranza**:

```java
// File: model/IoC.java

private void updateStatus() {
    long confirms = votes.values().stream()
        .filter(v -> v == VoteType.CONFIRM).count();
    long rejects = votes.values().stream()
        .filter(v -> v == VoteType.REJECT).count();

    // Quorum = maggioranza semplice
    if (confirms >= quorumSize) {
        status = IoCStatus.VERIFIED;
    } else if (rejects >= quorumSize) {
        status = IoCStatus.REJECTED;
    } else if (confirms + rejects >= activeNodesAtCreation) {
        status = IoCStatus.AWAITING_SOC;  // Stallo!
    }
}
```

**Quorum dinamico:**
- 5 nodi attivi → quorum = 3
- 4 nodi attivi → quorum = 3
- 3 nodi attivi → quorum = 2

---

## 5.5 Gestione Timeout in DTIP

### Problema

In Ricart-Agrawala, se un nodo crasha mentre aspettiamo la sua REPLY, restiamo bloccati per sempre (deadlock).

### Soluzione

```java
// File: core/RicartAgrawalaManager.java

public void requestMutex() {
    // ...

    synchronized (lock) {
        // Timeout dinamico: 3s per peer + 2s buffer
        long timeout = Math.max(10000, 3000L * node.getPeerCount() + 2000);
        long deadline = System.currentTimeMillis() + timeout;

        while (outstandingReplies > 0) {
            long remaining = deadline - System.currentTimeMillis();

            if (remaining <= 0) {
                // TIMEOUT! Abort
                log("MUTEX", "TIMEOUT! Aborting request", RED);
                requesting = false;
                return;  // Non entriamo in CS
            }

            lock.wait(remaining);
        }
    }
}

// Se un peer e' irraggiungibile, lo contiamo come "consenso implicito"
public void markPeerFailed(int peerId) {
    synchronized (lock) {
        if (requesting) {
            failedPeers.add(peerId);
            outstandingReplies--;

            if (outstandingReplies == 0) {
                lock.notifyAll();  // Procediamo!
            }
        }
    }
}
```

---

# PARTE 6: GOSSIP PROTOCOL

## 6.1 Propagazione Epidemica

### Teoria

Il Gossip Protocol e' un meccanismo di propagazione **epidemica**:
- Ogni nodo che riceve un'informazione la propaga ai vicini
- L'informazione si diffonde come un "virus"
- Garantisce **eventual consistency**

**Vantaggi:**
- Decentralizzato
- Resiliente ai guasti
- Scalabile

**Svantaggi:**
- Possibili duplicati
- Latenza non deterministica

### Applicazione in DTIP

```java
// File: core/DTIPNode.java

private void propagateIoC(IoC ioc) {
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        int peerId = entry.getKey();

        // LOOP PREVENTION: non reinvio a chi l'ha gia' visto
        if (ioc.getSeenBy().contains(peerId)) {
            continue;
        }

        // Propagazione asincrona
        new Thread(() -> {
            try {
                entry.getValue().receiveIoC(ioc, nodeId);
            } catch (RemoteException e) {
                // Peer offline, ignoro
            }
        }).start();
    }
}
```

**Deduplicazione con seenBy:**

```java
@Override
public void receiveIoC(IoC ioc, int fromNodeId) throws RemoteException {
    // Gia' visto? Ignora
    if (iocDatabase.containsKey(ioc.getId())) {
        return;
    }

    // Merge Vector Clock
    vectorClock.update(ioc.getVectorClock());

    // Mi segno come "ho visto"
    ioc.getSeenBy().add(nodeId);

    // Salvo e propago
    iocDatabase.put(ioc.getId(), ioc);
    propagateIoC(ioc);  // Continua l'epidemia!
}
```

---

# PARTE 7: RIEPILOGO ALGORITMI

| Algoritmo | Scopo in DTIP | Complessita' |
|-----------|---------------|--------------|
| **Vector Clock** | Ordinamento causale eventi | O(N) per messaggio |
| **Gossip** | Propagazione IoC | O(N) per IoC |
| **Consenso (Voting)** | Decisione su validita' IoC | N messaggi |
| **Ricart-Agrawala** | Accesso esclusivo al ledger | 2(N-1) messaggi |

---

# DOMANDE TIPICHE D'ESAME

**D: Perche' Vector Clock invece di Lamport?**
R: Lamport da' un ordine totale ma non preserva la causalita'. Con Vector Clock posso distinguere eventi concorrenti da eventi causalmente correlati.

**D: Cosa succede se un nodo crasha durante il mutex?**
R: Implemento un timeout. Dopo il timeout, considero il nodo crashato come "consenso implicito" e procedo.

**D: Perche' RMI invece di Socket puri?**
R: RMI offre trasparenza di accesso (stessa sintassi locale/remota) e gestisce automaticamente serializzazione/deserializzazione.

**D: Come gestisci la partizione di rete?**
R: Le due partizioni continuano indipendentemente. Al ripristino, il meccanismo di sync (anti-entropy) riconcilia gli stati.

**D: Qual e' il quorum minimo per decidere?**
R: Maggioranza semplice: (N/2) + 1. Con 5 nodi, serve 3. Con 4 attivi, serve ancora 3.

---

*Documento di studio per l'esame di Algoritmi Distribuiti - A.A. 2025/2026*
*Francesco Caligiuri*
