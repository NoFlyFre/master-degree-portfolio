# Discorso Esame - DTIP
## Simulazione della Discussione con il Professore
### Algoritmi Distribuiti - A.A. 2025/2026

---

## PARTE 1: Introduzione e Presentazione del Progetto

**[Apro la dashboard TUI e mostro i 5 nodi attivi]**

**IO:** "Buongiorno professore. Il mio progetto si chiama DTIP - Distributed Threat Intelligence Platform. E' una piattaforma peer-to-peer per la validazione collaborativa di minacce informatiche, chiamate IoC - Indicators of Compromise."

**PROF:** "Interessante. Perche' ha scelto questo dominio applicativo?"

**IO:** "Perche' volevo applicare gli algoritmi distribuiti del corso a un caso d'uso reale, non il classico esempio dell'elezione del leader su un anello. Il threat intelligence e' un ambito in cui la decentralizzazione ha senso: piu' organizzazioni che collaborano senza fidarsi di un'autorita' centrale."

**[Mostro la struttura nella dashboard]**

**IO:** "Ho implementato 5 nodi che rappresentano settori diversi:
- Banca (conservativa, threshold 70)
- Retail (aggressiva, threshold 30)
- Energia (bilanciata, threshold 50)
- Sanita' (paranoica, threshold 10)
- Trasporti (scettica, threshold 80)

Ogni nodo ha una policy di voto diversa, simulando organizzazioni con appetito al rischio differente."

---

## PARTE 2: Vector Clock

**PROF:** "Mi parli dei Vector Clock. Come li ha implementati?"

**IO:** "Ho seguito l'algoritmo di Fidge/Mattern. Ogni nodo mantiene un vettore di 5 interi che traccia gli eventi di tutto il sistema."

**[Apro il file VectorClock.java nell'editor]**

```java
// File: core/VectorClock.java (linee 32-36)

public VectorClock(int nodeId, int numNodes) {
    this.nodeId = nodeId;       // es. nodeId = 3
    this.numNodes = numNodes;   // numNodes = 5
    this.clock = new int[numNodes];  // clock = [0, 0, 0, 0, 0]
}
```

**IO:** "Alla creazione, il clock e' tutto a zero. Quando un nodo genera un evento locale, esegue un tick:"

```java
// File: core/VectorClock.java (linee 55-57)

public void tick() {
    clock[nodeId]++;  // Incrementa solo la propria posizione
}

// Esempio: Node 3 pubblica un IoC
// Prima: [0, 0, 0, 0, 0]
// Dopo:  [0, 0, 0, 1, 0]
//                  ^-- posizione di Node 3
```

**PROF:** "E alla ricezione di un messaggio?"

**IO:** "Qui applico la regola del merge: per ogni componente prendo il massimo tra il mio valore e quello ricevuto, poi incremento la mia posizione."

```java
// File: core/VectorClock.java (linee 68-76)

public void update(int[] receivedClock) {
    // Merge: max component-wise
    for (int i = 0; i < numNodes; i++) {
        clock[i] = Math.max(clock[i], receivedClock[i]);
    }
    // Tick locale
    clock[nodeId]++;
}

// Esempio: Node 0 riceve messaggio con clock [0, 0, 0, 1, 0]
// Node 0 clock prima: [0, 0, 0, 0, 0]
// Dopo merge:         [0, 0, 0, 1, 0]  (max component-wise)
// Dopo tick:          [1, 0, 0, 1, 0]  (incremento posizione 0)
```

**PROF:** "Come determina la relazione di happened-before?"

**IO:** "Con il metodo compareTo. Se tutte le componenti di VC1 sono minori o uguali a quelle di VC2, e almeno una e' strettamente minore, allora VC1 happened-before VC2."

```java
// File: core/VectorClock.java (linee 89-101)

public int compareTo(VectorClock other) {
    boolean thisLess = false;
    boolean otherLess = false;

    for (int i = 0; i < numNodes; i++) {
        if (this.clock[i] < other.clock[i]) thisLess = true;
        if (this.clock[i] > other.clock[i]) otherLess = true;
    }

    if (thisLess && !otherLess) return -1;  // this -> other
    if (otherLess && !thisLess) return 1;   // other -> this
    return 0;  // Concurrent (eventi paralleli)
}
```

**[Nella dashboard mostro la matrice Vector Clock]**

**IO:** "Nella dashboard si vede la matrice in tempo reale. La diagonale evidenziata mostra quanti eventi ha generato ciascun nodo."

---

## PARTE 3: Gossip Protocol

**PROF:** "Come propaga gli IoC nella rete?"

**IO:** "Uso il Gossip Protocol, una propagazione epidemica. Quando un nodo riceve un nuovo IoC, lo inoltra a tutti i peer che non lo hanno ancora visto."

**[Apro DTIPNode.java]**

```java
// File: core/DTIPNode.java (linee 133-157)

@Override
public String publishIoC(IoC ioc) throws RemoteException {
    checkOffline();  // Lancia eccezione se simuliamo un guasto

    // STEP 1: Tick del Vector Clock
    synchronized (vectorClock) {
        vectorClock.tick();
        ioc.setVectorClock(vectorClock.getClock());
    }

    // STEP 2: Mi marco come "ho visto questo IoC"
    ioc.getSeenBy().add(nodeId);

    // STEP 3: Salvo nel database locale
    iocDatabase.put(ioc.getId(), ioc);

    // STEP 4: Propago via Gossip
    propagateIoC(ioc);

    // STEP 5: Schedulo il mio voto automatico
    scheduleAutoVote(ioc);

    return ioc.getId();
}
```

**IO:** "La propagazione evita i loop grazie al set seenBy che traccia quali nodi hanno gia' visto l'IoC:"

```java
// File: core/DTIPNode.java (propagateIoC - logica semplificata)

private void propagateIoC(IoC ioc) {
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        int peerId = entry.getKey();

        // LOOP PREVENTION: se il peer ha gia' visto l'IoC, skip
        if (ioc.getSeenBy().contains(peerId)) {
            continue;  // Non reinvio a chi l'ha gia' ricevuto
        }

        // Invio asincrono su thread separato
        new Thread(() -> {
            try {
                DTIPNodeInterface peer = peers.get(peerId);
                peer.receiveIoC(ioc, nodeId);  // Chiamata RMI
            } catch (RemoteException e) {
                // Riprovo con stub fresco, poi fallimento silenzioso
            }
        }).start();
    }
}
```

**PROF:** "Quindi ogni nodo che riceve l'IoC lo propaga ulteriormente?"

**IO:** "Esatto. Alla ricezione, il nodo fa merge del Vector Clock, si aggiunge a seenBy, salva l'IoC e lo propaga ai peer che non l'hanno ancora visto:"

```java
// File: core/DTIPNode.java (linee 160-197)

@Override
public void receiveIoC(IoC ioc, int fromNodeId) throws RemoteException {
    checkOffline();

    // Deduplicazione: se gia' ho questo IoC, ignoro
    if (iocDatabase.containsKey(ioc.getId())) {
        return;
    }

    // Merge Vector Clock
    synchronized (vectorClock) {
        vectorClock.update(ioc.getVectorClock());
    }

    // Mi aggiungo a seenBy
    ioc.getSeenBy().add(nodeId);

    // Salvo localmente
    iocDatabase.put(ioc.getId(), ioc);

    // Continuo la propagazione (Gossip)
    propagateIoC(ioc);

    // Schedulo il mio voto
    scheduleAutoVote(ioc);
}
```

**[Inietto un IoC dalla dashboard premendo 'i']**

**IO:** "Le mostro la propagazione. Inietto un dominio malevolo..."

**[I log mostrano: PUBLISH su Node 0, poi GOSSIP Received su Node 1, 2, 3, 4]**

**IO:** "Vede? Node 0 pubblica, poi tutti gli altri ricevono quasi simultaneamente. Il gossip garantisce l'eventual consistency."

---

## PARTE 4: Consenso e Voting

**PROF:** "Come funziona il meccanismo di consenso?"

**IO:** "Ogni nodo, dopo aver ricevuto un IoC, esegue un'analisi locale e vota CONFIRM (e' una minaccia) o REJECT (innocuo). La decisione dipende dalla policy del nodo."

```java
// File: core/DTIPNode.java (computeLocalVote - logica)

private IoC.VoteType computeLocalVote(IoC ioc) {
    // Calcolo threat score (0-100) basato su euristiche + LLM
    int baseScore = computeThreatScore(ioc);  // es. 75

    int threshold;
    switch (nodeId) {
        case 0: threshold = 70; break;  // Banca: Conservative
        case 1: threshold = 30; break;  // Retail: Aggressive
        case 2: threshold = 50; break;  // Energia: Balanced
        case 3: threshold = 10; break;  // Sanita: Paranoid
        case 4: threshold = 80; break;  // Trasporti: Skeptical
        default: threshold = 50;
    }

    // Decisione: se score >= threshold -> CONFIRM
    return baseScore >= threshold
           ? IoC.VoteType.CONFIRM
           : IoC.VoteType.REJECT;
}
```

**PROF:** "E il quorum come viene calcolato?"

**IO:** "Il quorum e' dinamico, fissato al momento della pubblicazione basandosi sui nodi attivi. Se ho 5 nodi attivi, quorum = 3. Se un nodo e' offline e ne ho 4 attivi, quorum = 3 ugualmente."

```java
// File: model/IoC.java (linee 91-111)

public IoC(IoCType type, String value, int confidence,
        List<String> tags, int publisherId, String publisherName,
        int totalNodes, int activeNodes) {

    // ID deterministico: stesso IoC = stesso ID su tutti i nodi
    this.id = generateId(type, value);

    // ... altri campi ...

    // QUORUM DINAMICO - Fissato al momento della creazione
    this.totalNodes = totalNodes;             // 5
    this.activeNodesAtCreation = activeNodes; // es. 4 se 1 offline
    this.quorumSize = (activeNodes / 2) + 1;  // (4/2)+1 = 3
}
```

**IO:** "La logica di decisione e' in updateStatus():"

```java
// File: model/IoC.java (linee 168-196)

private void updateStatus() {
    // Conta voti (escludo SOC che ha nodeId = -1)
    long nodeConfirms = votes.entrySet().stream()
            .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.CONFIRM)
            .count();
    long nodeRejects = votes.entrySet().stream()
            .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.REJECT)
            .count();

    // Regola 1: Quorum raggiunto
    if (nodeConfirms >= quorumSize) {
        status = IoCStatus.VERIFIED;  // MINACCIA CONFERMATA
        return;
    }
    if (nodeRejects >= quorumSize) {
        status = IoCStatus.REJECTED;  // INNOCUO
        return;
    }

    // Regola 2: Stalemate Detection (tutti votano, nessun quorum)
    long totalVotes = nodeConfirms + nodeRejects;
    if (totalVotes >= activeNodesAtCreation && status == IoCStatus.PENDING) {
        status = IoCStatus.AWAITING_SOC;  // Serve intervento umano
    }
}
```

**PROF:** "Cosa succede in caso di parita'?"

**IO:** "Se tutti votano ma nessuno raggiunge il quorum - ad esempio 2-2 con 4 nodi attivi - lo stato diventa AWAITING_SOC. A quel punto il SOC (Security Operations Center) puo' intervenire manualmente con autorita' assoluta:"

```java
// File: model/IoC.java (linee 146-160)

public void addVote(int nodeId, VoteType vote) {
    votes.put(nodeId, vote);

    // SOC (Node -1) ha autorita' assoluta
    if (nodeId == -1) {
        if (vote == VoteType.CONFIRM) {
            status = IoCStatus.VERIFIED;
        } else {
            status = IoCStatus.REJECTED;
        }
        return;  // Decisione immediata, ignora quorum
    }

    updateStatus();
}
```

**[Nella dashboard mostro il pannello voti con 3 CONFIRM e 1 REJECT]**

**IO:** "Qui vede: 3 nodi hanno votato MINACCIA, 1 ha votato INNOCUO. Quorum = 3, quindi lo stato e' VERIFIED."

---

## PARTE 5: Ricart-Agrawala (Mutual Exclusion)

**PROF:** "Vedo che ha implementato Ricart-Agrawala. A cosa serve nel suo sistema?"

**IO:** "Quando un IoC raggiunge lo stato VERIFIED, il nodo che rileva il cambio di stato deve scrivere sul ledger condiviso. Per evitare race condition e scritture duplicate, uso la mutua esclusione distribuita."

**[Apro RicartAgrawalaManager.java]**

```java
// File: core/RicartAgrawalaManager.java (linee 105-155)

public void requestMutex() {
    synchronized (lock) {
        requesting = true;
        mySequenceNumber = highestSequenceNumberSeen + 1;
        outstandingReplies = node.getPeerCount();  // es. 4 peer
        failedPeers.clear();
        receivedRepliesFrom.clear();
    }

    // Broadcast REQUEST a tutti i peer
    node.broadcastMutexRequest(mySequenceNumber);

    // Attendo REPLY da tutti (con timeout)
    synchronized (lock) {
        long timeout = Math.max(10000, 3000L * node.getPeerCount() + 2000);
        long deadline = System.currentTimeMillis() + timeout;

        while (outstandingReplies > 0) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                // TIMEOUT! Esco senza entrare in CS
                requesting = false;
                return;
            }
            lock.wait(remaining);  // Bloccante
        }

        inCriticalSection = true;  // Ho tutte le reply!
    }
}
```

**PROF:** "Come gestisce il conflitto se due nodi richiedono contemporaneamente?"

**IO:** "Uso la priorita' basata su sequence number. Chi ha il numero piu' basso vince. In caso di parita', vince il nodo con ID piu' basso."

```java
// File: core/RicartAgrawalaManager.java (linee 188-215)

public void handleRequest(int requesterId, int sequenceNumber, int timestamp) {
    synchronized (lock) {
        highestSequenceNumberSeen = Math.max(highestSequenceNumberSeen, sequenceNumber);

        boolean defer = false;

        if (requesting) {
            // Sto richiedendo anch'io: chi ha priorita'?
            boolean weHavePriority =
                (mySequenceNumber < sequenceNumber) ||
                (mySequenceNumber == sequenceNumber && myNodeId < requesterId);

            if (weHavePriority) {
                defer = true;  // Io ho priorita', rimando la reply
            }
        }

        if (defer) {
            deferredReplies.add(requesterId);  // Rispondero' dopo
        } else {
            node.sendMutexReply(requesterId);  // Rispondo subito
        }
    }
}
```

**IO:** "Quando rilascio la critical section, invio tutte le reply posticipate:"

```java
// File: core/RicartAgrawalaManager.java (linee 163-176)

public void releaseMutex() {
    synchronized (lock) {
        inCriticalSection = false;
        requesting = false;

        // Invio reply posticipate
        for (Integer targetId : deferredReplies) {
            node.sendMutexReply(targetId);
        }
        deferredReplies.clear();
    }
}
```

**PROF:** "E se un nodo crasha durante l'attesa delle reply?"

**IO:** "Ho implementato un meccanismo di timeout e graceful degradation. Se un peer non risponde e non riesco a ricontattarlo, lo marco come failed e conto la sua reply come implicita:"

```java
// File: core/RicartAgrawalaManager.java (linee 77-93)

public void markPeerFailed(int peerId) {
    synchronized (lock) {
        if (requesting && !receivedRepliesFrom.contains(peerId)) {
            failedPeers.add(peerId);

            // Graceful degradation: peer morto = consenso implicito
            if (outstandingReplies > 0) {
                outstandingReplies--;
                if (outstandingReplies == 0) {
                    lock.notifyAll();  // Procedo comunque
                }
            }
        }
    }
}
```

---

## PARTE 6: Demo Pratica - Fault Tolerance

**PROF:** "Mi faccia vedere come si comporta il sistema in caso di guasto."

**[Nella dashboard premo 'k' per killare Node 2]**

**IO:** "Simulo un guasto su Node 2 (Energia). Vede che nella dashboard diventa OFFLINE."

**[Inietto un nuovo IoC]**

**IO:** "Ora inietto un nuovo IoC. Il gossip lo propaga solo ai nodi online. Il quorum viene calcolato su 4 nodi attivi, quindi serve 3 voti."

**[Mostro i log: solo 4 nodi votano, quorum raggiunto]**

**PROF:** "E quando il nodo torna online?"

**[Premo 'r' per revive Node 2]**

**IO:** "Esegue un sync automatico - quello che in letteratura si chiama anti-entropy:"

```java
// File: core/DTIPNode.java (performSync - logica semplificata)

public void performSync() {
    // Chiedo a ogni peer gli IoC che mi mancano
    for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
        try {
            // Delta sync basato su Vector Clock
            List<IoC> missing = entry.getValue().sync(vectorClock.getClock());

            for (IoC ioc : missing) {
                if (!iocDatabase.containsKey(ioc.getId())) {
                    iocDatabase.put(ioc.getId(), ioc);
                    vectorClock.update(ioc.getVectorClock());
                }
            }
        } catch (RemoteException e) {}
    }

    // Vote Recovery: voto sugli IoC PENDING che non ho ancora votato
    for (IoC ioc : iocDatabase.values()) {
        if (ioc.getStatus() == IoCStatus.PENDING &&
            !ioc.getVotes().containsKey(nodeId)) {
            scheduleAutoVote(ioc);
        }
    }
}
```

**IO:** "Il nodo recupera gli IoC persi durante l'offline e vota su quelli ancora pendenti. Questo garantisce l'eventual consistency."

---

## PARTE 7: Domande Teoriche Finali

**PROF:** "Qual e' la complessita' in messaggi del suo gossip?"

**IO:** "Nel caso peggiore O(N^2) per IoC, perche' ogni nodo potrebbe inoltrare a tutti gli altri. Ma grazie al set seenBy, in pratica e' molto inferiore perche' evitiamo duplicati."

**PROF:** "E per Ricart-Agrawala?"

**IO:** "2(N-1) messaggi per accesso alla critical section: N-1 REQUEST e N-1 REPLY. E' ottimale per algoritmi permission-based."

**PROF:** "Quali sono i limiti del suo sistema?"

**IO:** "Il principale e' che il quorum richiede la maggioranza dei nodi attivi. Se piu' della meta' dei nodi fallisce, il sistema si blocca. Inoltre, il gossip e' push-only: se un nodo e' offline durante la propagazione e tutti gli altri hanno gia' processato l'IoC, deve aspettare il sync al riavvio."

**PROF:** "Come migliorerebbe il sistema?"

**IO:** "Aggiungerei:
1. Un meccanismo di pull periodico per garantire convergenza anche senza sync esplicito
2. Persistenza su disco per sopravvivere ai restart
3. Crittografia end-to-end per proteggere gli IoC in transito
4. Un sistema di reputazione piu' sofisticato che penalizza i nodi che votano sempre in modo anomalo"

---

## CONCLUSIONE

**IO:** "In sintesi, DTIP dimostra come i quattro algoritmi del corso - Vector Clock, Gossip, Consenso Distribuito e Ricart-Agrawala - possono essere integrati in un sistema reale per il threat intelligence collaborativo."

**PROF:** "Bene, grazie. Il progetto e' completo e ben strutturato."

---

## APPENDICE: Comandi Rapidi per la Demo

```bash
# Compila
./scripts/compile.sh

# Avvia rete (tmux)
./scripts/start_tmux.sh

# Oppure con Terminal tabs
./scripts/start_network.sh

# Dashboard TUI
./scripts/run_dashboard.sh

# Inject IoC via netcat (alternativa)
echo "DOMAIN:malware.evil.com:85:ransomware" | nc localhost 9000
```

**Keybindings Dashboard:**
- `i` - Inject nuovo IoC
- `k` - Kill un nodo (simula guasto)
- `r` - Revive un nodo (riattiva)
- `m` - Demo mutex
- `h` - Help
- `q` - Quit

---

## POSSIBILI DOMANDE AGGIUNTIVE E RISPOSTE

**D: "Come garantisce che tutti vedano gli stessi file se usa RMI?"**
**R:** "RMI garantisce la comunicazione, ma per la persistenza uso un concetto simile a un File System distribuito semplificato (il Ledger). La coerenza viene forzata tramite il Mutex distribuito di Ricart-Agrawala."

**D: "Se il nodo che detiene il mutex crasha?"**
**R:** "In Ricart-Agrawala classico, il sistema andrebbe in deadlock. La mia implementazione rileva il timeout, rimuove il nodo caduto dalla lista dei peer attivi e procede, privilegiando la continuita' di servizio (Availability) rispetto alla coerenza stretta in quel frangente."

**D: "Perche' RMI e non Socket o gRPC?"**
**R:** "RMI offre Trasparenza di Accesso (invocazione locale e remota identica) e gestisce automaticamente marshalling/unmarshalling, permettendomi di concentrarmi sulla logica distribuita invece che sul parsing dei pacchetti."

**D: "Perche' Vector Clock invece di Lamport timestamp?"**
**R:** "Il timestamp di Lamport mi avrebbe dato un ordine totale ma arbitrario. Io avevo bisogno di rilevare la causalita' (relazione happens-before) per gestire correttamente la sincronizzazione post-guasto (Delta Sync) e per identificare eventi concorrenti."

**D: "Come gestisce la partizione di rete?"**
**R:** "In caso di network partition, le due partizioni continuano a operare indipendentemente. Al ripristino della connettivita', il meccanismo di sync (anti-entropy) riconcilia gli stati. Tuttavia, potrebbero verificarsi decisioni conflittuali se entrambe le partizioni raggiungono il quorum su IoC con lo stesso ID - questo e' un limite del sistema attuale."

---

*Documento preparato per l'esame di Algoritmi Distribuiti - A.A. 2025/2026*
*Francesco Caligiuri*
