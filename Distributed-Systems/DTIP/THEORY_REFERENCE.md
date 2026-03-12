# Distributed Algorithms - Theory Reference Sheet

**Quick reference for DTIP implementation and exam preparation**

---

## 1. Vector Clocks (Lamport 1978, Fidge/Mattern 1988)

### 1.1 Problem & Motivation

In distributed systems:
- Physical clocks drift and cannot be perfectly synchronized
- We need to determine **causal relationships** between events
- "Did event A happen before event B?" or "Are A and B concurrent?"

### 1.2 Formal Definition

**Vector Clock (VC):** An array of N logical clocks (one per process)

```
VC_i = [c₀, c₁, c₂, ..., c_{N-1}]

where:
  VC_i[i] = number of events process i has executed
  VC_i[j] = process i's knowledge of events at process j (for j ≠ i)
```

### 1.3 Happened-Before Relation (→)

**Definition:**
Event `e₁ → e₂` (e₁ happened-before e₂) if:
1. e₁ and e₂ are on the same process and e₁ occurs before e₂, OR
2. e₁ is a send event and e₂ is the corresponding receive event, OR
3. Transitivity: e₁ → e₃ ∧ e₃ → e₂ ⟹ e₁ → e₂

**Vector Clock Comparison:**
```
VC₁ ≤ VC₂  ⟺  ∀i: VC₁[i] ≤ VC₂[i]
VC₁ < VC₂  ⟺  VC₁ ≤ VC₂ ∧ ∃i: VC₁[i] < VC₂[i]
```

**Concurrent Events (||):**
```
e₁ || e₂  ⟺  ¬(VC₁ < VC₂) ∧ ¬(VC₂ < VC₁)
```

### 1.4 Algorithm Pseudocode

```python
# Initialization (at process i with N total processes)
VC[i] = [0, 0, ..., 0]  # N zeros

# On local event at process i
def on_local_event():
    VC[i][i] += 1

# On sending message m from process i
def send_message(m, dest):
    VC[i][i] += 1
    m.timestamp = VC[i].copy()
    send(m, dest)

# On receiving message m at process j
def receive_message(m):
    for k in range(N):
        VC[j][k] = max(VC[j][k], m.timestamp[k])
    VC[j][j] += 1
    process(m)
```

### 1.5 Properties

| Property | Value |
|----------|-------|
| **Space complexity** | O(N) per process |
| **Message overhead** | O(N) per message |
| **Clock updates** | O(N) on receive |
| **Correctness** | If e₁ → e₂ then VC₁ < VC₂ |

### 1.6 Example

```
Process 0: VC=[0,0,0] → event → VC=[1,0,0] → send(m) → VC=[2,0,0]
Process 1: VC=[0,0,0] → receive(m, VC=[2,0,0]) → VC=[2,1,0]
Process 2: VC=[0,0,0] → event → VC=[0,0,1]

Check: VC₀=[2,0,0] and VC₂=[0,0,1]
  - Not VC₀ < VC₂ (since 2 > 0)
  - Not VC₂ < VC₀ (since 0 < 2)
  - Therefore: events are CONCURRENT
```

---

## 2. Ricart-Agrawala Mutual Exclusion (1981)

### 2.1 Problem Statement

**Mutual Exclusion:** Ensure only one process can enter the Critical Section (CS) at any time in a distributed system, without a central coordinator.

**Requirements:**
1. **Safety:** At most one process in CS at any time
2. **Liveness:** Every request eventually succeeds
3. **Fairness:** Requests served in timestamp order (no starvation)

### 2.2 Algorithm Overview

Uses **timestamped requests** and **N-1 replies** before entering CS.

**Messages:**
- `REQUEST(nodeId, sequenceNumber)` - Request permission to enter CS
- `REPLY(nodeId)` - Grant permission

### 2.3 Algorithm Pseudocode

```python
# State variables
requesting = False
sequenceNumber = 0
deferredReplies = []  # Queue of pending replies
outstandingReplies = 0

# Request Critical Section
def requestCS():
    requesting = True
    sequenceNumber += 1
    mySeq = sequenceNumber

    # Broadcast REQUEST to all N-1 other processes
    for peer in peers:
        send(peer, REQUEST(myId, mySeq))

    outstandingReplies = N - 1

    # Wait for N-1 REPLY messages
    while outstandingReplies > 0:
        wait()  # Block until reply received

    # Enter critical section
    enterCS()

# On receiving REQUEST from process j
def onReceiveRequest(j, timestamp_j):
    if not requesting:
        # Not requesting - send REPLY immediately
        send(j, REPLY(myId))
    elif timestamp_j < mySeq or (timestamp_j == mySeq and j < myId):
        # Incoming request has higher priority - send REPLY
        send(j, REPLY(myId))
    else:
        # I have higher priority - defer reply
        deferredReplies.append(j)

# On receiving REPLY from process j
def onReceiveReply(j):
    outstandingReplies -= 1
    notify()  # Wake up waiting thread

# After exiting CS
def releaseCS():
    requesting = False

    # Send deferred replies
    for j in deferredReplies:
        send(j, REPLY(myId))

    deferredReplies.clear()
```

### 2.4 Priority Rule

**Timestamp comparison:**
```
Request(i, ts_i) has higher priority than Request(j, ts_j) if:
  ts_i < ts_j  OR  (ts_i == ts_j AND i < j)
```

### 2.5 Complexity Analysis

| Metric | Value |
|--------|-------|
| **Messages per CS entry** | 2(N-1) |
| **Synchronization delay** | 1 message round-trip |
| **Client delay** | 0 (no waiting after receiving all replies) |

**Breakdown:**
- (N-1) REQUEST messages broadcast
- (N-1) REPLY messages received
- **Total: 2(N-1) messages**

### 2.6 Example (3 Processes)

```
Time  Process 0           Process 1           Process 2
----  -----------------   -----------------   -----------------
T0    requestCS()
      seq=1
      broadcast REQ(0,1)
                          receive REQ(0,1)
                          send REPLY(1)
                                              receive REQ(0,1)
                                              send REPLY(2)
T1    receive REPLY(1)
      receive REPLY(2)
      outstandingReplies=0
      ENTER CS            requestCS()
                          seq=1
                          broadcast REQ(1,1)
      receive REQ(1,1)
      defer (in CS)
                                              receive REQ(1,1)
                                              send REPLY(2)
T2    EXIT CS
      send REPLY(0) to 1
                          receive REPLY(0)
                          receive REPLY(2)
                          ENTER CS
```

### 2.7 Deadlock Prevention

**Issue:** If a process crashes while holding deferred replies, requesters wait forever.

**Solution (DTIP):** Timeout mechanism
```python
timeout = 5000  # 5 seconds
deadline = current_time() + timeout

while outstandingReplies > 0:
    remaining = deadline - current_time()
    if remaining <= 0:
        requesting = False
        return ERROR  # Abort request
    wait(remaining)
```

---

## 3. Gossip Protocol (Epidemic Broadcast)

### 3.1 Problem & Motivation

**Reliable Multicast:** Ensure all nodes in a network receive a message, even with node failures and network partitions.

**Traditional approaches:**
- Flooding: O(N²) messages (wasteful)
- Tree-based: Single point of failure

**Gossip approach:**
- Probabilistic, fault-tolerant
- O(N log N) messages
- Natural redundancy

### 3.2 Algorithm Pseudocode

```python
# State
seen = Set()  # IDs of messages already seen
fanout = 3    # Number of random peers to forward to

# On receiving message m
def onReceiveMessage(m):
    if m.id in seen:
        return  # Already seen, ignore

    seen.add(m.id)
    process(m)  # Local handling

    # Gossip round
    for i in range(fanout):
        peer = selectRandomPeer()
        send(peer, m)

    # Schedule additional rounds (optional)
    schedule(lambda: gossipRound(m), delay=1s)

def gossipRound(m):
    for i in range(fanout):
        peer = selectRandomPeer()
        send(peer, m)
```

### 3.3 Analysis

**Propagation Time:** O(log N) rounds with high probability

**Proof sketch:**
- Round 0: 1 node knows the message
- Round 1: ~fanout nodes know
- Round 2: ~fanout² nodes know
- Round k: ~fanout^k nodes know
- Solve: fanout^k ≥ N ⟹ k ≥ log_fanout(N) = O(log N)

**Message Complexity:** O(N log N)
- Each node sends fanout messages per round
- Total rounds: O(log N)
- Total messages: N × fanout × log N = O(N log N)

### 3.4 Properties

| Property | Description |
|----------|-------------|
| **Eventual Consistency** | All reachable nodes eventually receive the message |
| **Fault Tolerance** | Works with node failures (alternative paths exist) |
| **Scalability** | Decentralized, no coordinator |
| **Redundancy** | Messages may arrive multiple times (idempotency required) |

### 3.5 Optimizations

**Push-Pull Gossip:**
```python
# Push: Send full message
# Pull: Send only message ID, receiver requests if missing

def pushPull():
    peer = selectRandomPeer()

    # Push: Send my messages
    for m in myMessages:
        send(peer, m.id)

    # Pull: Request missing messages
    missingIds = peer.messageIds - myMessageIds
    for id in missingIds:
        request(peer, id)
```

---

## 4. Consensus in Distributed Systems

### 4.1 General Problem

**Byzantine Generals Problem (Lamport 1982):**
Processes must agree on a value despite:
- Process failures
- Message loss
- Malicious actors

**FLP Impossibility (1985):**
Deterministic consensus impossible in asynchronous systems with even one crash failure.

### 4.2 DTIP Consensus (Multi-tier Voting)

**Assumptions:**
- Synchronous rounds (timeout-based)
- No Byzantine faults (nodes are honest)
- Heterogeneous policies (innovation)

**Protocol:**
```python
# Phase 1: Gossip
gossip(ioc)  # Ensure all nodes have the IoC

# Phase 2: Local Evaluation
score = queryThreatIntelAPI(ioc.value)

# Phase 3: Voting
vote = CONFIRM if score >= myPolicy.threshold else REJECT
broadcast(VOTE(myId, ioc.id, vote))

# Phase 4: Counting
wait_for_all_votes()
confirmCount = count(votes, CONFIRM)
rejectCount = count(votes, REJECT)

# Phase 5: Decision
if confirmCount >= quorum:
    status = VERIFIED
elif rejectCount >= quorum:
    status = REJECTED
elif confirmCount == rejectCount:
    status = AWAITING_SOC  # Human tie-breaking
else:
    status = PENDING
```

**Quorum:** (N/2) + 1 = 4 voti (tutti i nodi votano, decisione a maggioranza semplice)

**Policy Examples:**
```
Node 0 (Banca):      threshold = 70  (CONSERVATIVE)
Node 1 (Retail):     threshold = 30  (AGGRESSIVE)
Node 2 (Energia):    threshold = 50  (BALANCED)
Node 3 (Sanità):     threshold = 10  (PARANOID)
Node 4 (Trasporti):  threshold = 80  (SKEPTICAL)
Node 5 (PA):         threshold = 50  (RANDOM)
```

### 4.3 Comparison: DTIP vs Traditional Consensus

| Property | Raft/Paxos | DTIP |
|----------|------------|------|
| **Node behavior** | Identical replicas | Heterogeneous policies |
| **Agreement** | On log entries | On threat validation |
| **Leader** | Yes (election) | No (peer-to-peer) |
| **Failure model** | Crash faults | Crash faults + timeout |
| **Use case** | State machine replication | Collaborative decision-making |

---

## 5. DTIP-Specific Integration

### 5.1 How Algorithms Interact

```
1. IoC Publication
   ├─ Node 0: VC[0,0,0,0,0,0] → tick → VC[1,0,0,0,0,0]
   └─ Attach VC to IoC

2. Gossip Propagation
   ├─ Node 0 → {Node 1, Node 3} (random fanout)
   ├─ Node 1: receive VC[1,0,0,0,0,0], update → VC[1,1,0,0,0,0]
   └─ Continue for O(log N) rounds

3. Consensus Coordination (Ricart-Agrawala)
   ├─ Multiple nodes want to initiate voting
   ├─ Use Ricart-Agrawala to serialize access
   └─ Winner enters CS and starts voting round

4. Voting
   ├─ Each node: score = API(ioc.value)
   ├─ vote = score >= threshold ? CONFIRM : REJECT
   └─ Broadcast votes

5. Decision
   └─ Count votes, apply quorum rule
```

### 5.2 Vector Clock Usage

**Causal Consistency:**
- IoC A published at VC_A
- Vote V on IoC A sent at VC_V
- Guarantee: VC_A < VC_V (vote causally depends on IoC)

**Conflict Detection:**
If two nodes publish IoC with concurrent VCs, order is arbitrary (both valid).

---

## 6. Key Theorems & Proofs

### 6.1 Vector Clock Correctness

**Theorem:** If e₁ → e₂, then VC(e₁) < VC(e₂)

**Proof (sketch):**
- Case 1: Same process - VC increments on each event
- Case 2: Message send/receive - max rule ensures VC increases
- Case 3: Transitivity - follows from cases 1&2

**Converse (NOT always true):** VC(e₁) < VC(e₂) does NOT imply e₁ → e₂
- Example: e₁ at P0, e₂ at P1, both independent
- VC₀=[1,0], VC₁=[1,1] → VC₀ < VC₁, but not e₀ → e₁

### 6.2 Ricart-Agrawala Safety

**Theorem:** At most one process in CS at any time.

**Proof (by contradiction):**
Assume P_i and P_j both in CS at time T.
- P_i received N-1 replies, including one from P_j
- P_j received N-1 replies, including one from P_i

Case 1: ts_i < ts_j
  - P_j should have deferred reply to P_i (higher priority)
  - Contradiction

Case 2: ts_j < ts_i
  - P_i should have deferred reply to P_j
  - Contradiction

Case 3: ts_i = ts_j
  - Impossible (sequence numbers unique)

---

## 7. Common Exam Questions

### Q1: Why use Vector Clocks instead of Lamport Clocks?

**Answer:**
- **Lamport Clocks:** e₁ → e₂ ⟹ LC(e₁) < LC(e₂), but NOT vice versa
- **Vector Clocks:** e₁ → e₂ ⟺ VC(e₁) < VC(e₂) (if and only if)
- VCs can detect **concurrency**, Lamport clocks cannot

### Q2: Why is Ricart-Agrawala better than Centralized Mutex?

**Answer:**
- **Pros:** No single point of failure, 2(N-1) messages
- **Cons:** Centralized is 3 messages (REQ, GRANT, RELEASE)
- Trade-off: Fault tolerance vs efficiency

### Q3: Why doesn't Gossip guarantee delivery?

**Answer:**
- Gossip is **probabilistic** - works with high probability
- Network partition can split graph into disconnected components
- Solution: Assume eventual connectivity or use hybrid approach

### Q4: Can DTIP consensus tolerate Byzantine faults?

**Answer:**
- **No** - DTIP assumes honest nodes
- For Byzantine tolerance, need 3f+1 nodes to tolerate f faults (PBFT)
- DTIP uses 6 nodes with quorum 4 - tolerates 2 crash faults only

---

## 8. Quick Reference Tables

### Message Complexity Comparison

| Algorithm | Messages per Operation |
|-----------|------------------------|
| Centralized Mutex | 3 |
| Ricart-Agrawala | 2(N-1) |
| Token Ring | N (avg) |
| Gossip (epidemic) | O(N log N) total |
| Raft (leader election) | O(N) |

### Time Complexity

| Algorithm | Time Complexity |
|-----------|-----------------|
| Vector Clock update | O(N) |
| Lamport Clock update | O(1) |
| Gossip propagation | O(log N) rounds |
| Ricart-Agrawala CS entry | O(1) rounds, O(N) messages |

---

**Use this reference during the exam for quick lookup of definitions, pseudocode, and complexity bounds!**

---

*Last updated: December 2024*
*Course: Distributed Algorithms - UniMORE*
*Author: Francesco Caligiuri*
