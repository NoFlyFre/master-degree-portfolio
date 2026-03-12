package core;

import interfaces.DTIPNodeInterface;
import model.IoC;
import model.NodeInfo;
import model.NodeReputation;
import model.NodeEvent;
import model.MutexState;
import util.ConsoleColors;
import util.MetricsCollector;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The core class representing a Peer-to-Peer Node in the DTIP network.
 * <p>
 * This class implements the {@link DTIPNodeInterface} and acts as both client
 * and server (Servent).
 * It orchestrates all distributed algorithms:
 * <ul>
 * <li><b>Vector Clocks</b> for causal ordering.</li>
 * <li><b>Gossip Protocol</b> for information propagation.</li>
 * <li><b>Consensus</b> with heterogeneous voting policies.</li>
 * <li><b>Ricart-Agrawala</b> for distributed mutual exclusion.</li>
 * </ul>
 */
public class DTIPNode extends UnicastRemoteObject implements DTIPNodeInterface {

    /** Unique integer ID for the node. */
    private int nodeId;

    /** Human-readable name (e.g., "Bank"). */
    private String nodeName;

    // State
    /** Local database of known IoCs. Map: ID -> IoC object. */
    private Map<String, IoC> iocDatabase;

    /** Reputation tracking for all other nodes. Map: NodeID -> Reputation. */
    private Map<Integer, NodeReputation> reputations;

    /** Logical clock for causal ordering. */
    private VectorClock vectorClock;

    /** Simulated offline state for Chaos Engineering tests. */
    private boolean isOffline = false;

    // Network
    /** Active RMI connections to peers. Map: NodeID -> Stub. */
    private Map<Integer, DTIPNodeInterface> peers;

    /** Manager for the Ricart-Agrawala mutual exclusion algorithm. */
    private RicartAgrawalaManager mutexManager;

    /** Tracks IoCs already written to ledger by this node (prevents duplicates). */
    private Set<String> writtenToLedger = ConcurrentHashMap.newKeySet();

    /**
     * Tracks peers known to be offline (to avoid log spam during health checks).
     */
    private Set<Integer> knownOfflinePeers = ConcurrentHashMap.newKeySet();

    // Educational features (for dashboard)
    /** Recent event log for visualization. */
    private List<NodeEvent> eventLog;

    /** Maximum number of events to keep in memory. */
    private static final int MAX_EVENT_LOG_SIZE = 50;

    /** Singleton metrics collector reference. */
    private MetricsCollector metricsCollector;

    // Config
    /** Total number of nodes in the network configuration. */
    private int totalNodes;

    // Threat Analysis
    /** Strategy for analyzing threat level (Composite: Heuristic + LLM). */
    private ThreatAnalyzer threatAnalyzer;

    /**
     * Constructs a new DTIPNode.
     *
     * @param nodeId     Unique integer ID for the node.
     * @param nodeName   Human-readable name (e.g., "Bank", "Hospital").
     * @param totalNodes Total expected number of nodes in the network (for
     *                   VectorClock initialization).
     * @throws RemoteException If RMI export fails.
     */
    public DTIPNode(int nodeId, String nodeName, int totalNodes) throws RemoteException {
        super();
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.totalNodes = totalNodes;

        this.iocDatabase = new ConcurrentHashMap<>();
        this.reputations = new ConcurrentHashMap<>();
        this.vectorClock = new VectorClock(nodeId, totalNodes);
        this.peers = new ConcurrentHashMap<>();
        this.mutexManager = new RicartAgrawalaManager(this, nodeId);

        // Educational features
        this.eventLog = Collections.synchronizedList(new ArrayList<>());
        this.metricsCollector = MetricsCollector.getInstance();

        // Threat analysis with LLM fallback (Strategy Pattern)
        this.threatAnalyzer = new CompositeAnalyzer();

        // Initialize reputations for all nodes (0 to totalNodes-1)
        for (int i = 0; i < totalNodes; i++) {
            reputations.put(i, new NodeReputation(i, "Node-" + i));
        }

        printStartupBanner();
    }

    // ==================================================================================
    // PUBLICATION & GOSSIP
    // ==================================================================================

    @Override
    public String publishIoC(IoC ioc) throws RemoteException {
        checkOffline();
        // Update local vector clock (Tick)
        synchronized (vectorClock) {
            vectorClock.tick();
            ioc.setVectorClock(vectorClock.getClock());
        }
        ioc.getSeenBy().add(nodeId);

        // Save locally
        iocDatabase.put(ioc.getId(), ioc);

        log("PUBLISH", "IoC " + ioc.getId() + " - " + ioc.getType() + ":" + ioc.getValue(), ConsoleColors.GREEN_BOLD);
        log("CLOCK", "VectorClock: " + vectorClock, ConsoleColors.CYAN);

        logEvent("PUBLISH", ioc.getType() + ":" + ioc.getValue(), ioc.getId());

        // Gossip to peers
        propagateIoC(ioc);

        // Schedule automatic vote for self as well
        scheduleAutoVote(ioc);

        return ioc.getId();
    }

    @Override
    public void receiveIoC(IoC ioc, int fromNodeId) throws RemoteException {
        checkOffline();

        // Deduplication check
        if (iocDatabase.containsKey(ioc.getId())) {
            IoC existing = iocDatabase.get(ioc.getId());
            // Allow update if status is stuck (AWAITING_SOC) or manually forced
            boolean isUpdate = existing.getStatus() == IoC.IoCStatus.AWAITING_SOC;

            if (!isUpdate) {
                return; // Already seen, ignore
            } else {
                System.out.println(ConsoleColors.YELLOW + "[UPDATE]" + ConsoleColors.RESET
                        + " Processing update for IoC " + ioc.getId());
            }
        }

        // Merge vector clock
        synchronized (vectorClock) {
            vectorClock.update(ioc.getVectorClock());
        }
        ioc.getSeenBy().add(nodeId);

        // Save locally
        iocDatabase.put(ioc.getId(), ioc);

        log("GOSSIP", "Received IoC " + ioc.getId() + " from Node " + fromNodeId + " - " + ioc.getType() + ":"
                + ioc.getValue(), ConsoleColors.YELLOW);
        log("CLOCK", "VectorClock: " + vectorClock, ConsoleColors.CYAN);

        logEvent("RECEIVE", ioc.getType() + ":" + ioc.getValue() + " from Node " + fromNodeId, ioc.getId());

        // Propagate to other peers (Gossip)
        propagateIoC(ioc);

        // Start heuristic analysis
        scheduleAutoVote(ioc);
    }

    /**
     * Propagates an IoC to all connected peers using the Gossip protocol.
     * <p>
     * Implements basic "Push" gossip with retry logic. Sends the IoC to all peers
     * that are NOT in the 'seenBy' list of the IoC to prevent loops.
     *
     * @param ioc The IoC to propagate.
     */
    private void propagateIoC(IoC ioc) {
        for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
            int peerId = entry.getKey();

            // Loop prevention check
            if (ioc.getSeenBy().contains(peerId)) {
                continue;
            }

            // Async send with retry logic
            new Thread(() -> {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        DTIPNodeInterface peer = peers.get(peerId);
                        if (peer == null)
                            return;
                        peer.receiveIoC(ioc, nodeId);
                        return; // Success
                    } catch (RemoteException e) {
                        if (attempt == 0 && refreshPeerStubSilent(peerId)) {
                            continue; // Retry silently
                        }
                        // Only log if peer is in Chaos mode (expected) or truly dead (unexpected)
                        // Silence routine failures
                    }
                }
            }).start();
        }
    }

    // ==================================================================================
    // VOTING & CONSENSUS
    // ==================================================================================

    @Override
    public void vote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException {
        checkOffline();
        IoC ioc = iocDatabase.get(iocId);
        if (ioc == null) {
            System.out.println("[Node " + nodeId + "] IoC " + iocId + " not found");
            return;
        }

        IoC.IoCStatus oldStatus = ioc.getStatus();
        ioc.addVote(voterId, vote);

        log("VOTE", "Casting " + vote + " on IoC " + iocId + " (My Vote)", ConsoleColors.PURPLE_BOLD);
        logEvent("VOTE", vote + " from Node " + voterId, iocId);

        // Check if status changed due to new quorum
        if (ioc.getStatus() != oldStatus && ioc.getStatus() != IoC.IoCStatus.PENDING) {
            handleStatusChange(ioc, oldStatus);
        }

        // Propagate vote to peers
        propagateVote(iocId, voterId, vote);
    }

    @Override
    public void receiveVote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException {
        checkOffline();
        IoC ioc = iocDatabase.get(iocId);
        if (ioc == null)
            return;

        IoC.IoCStatus oldStatus;

        // Synchronized block to prevent race condition on vote idempotency check
        synchronized (ioc) {
            // Idempotency check (thread-safe)
            if (ioc.getVotes().containsKey(voterId))
                return;

            oldStatus = ioc.getStatus();
            ioc.addVote(voterId, vote);
        }

        log("VOTE", "Received " + vote + " on IoC " + iocId + " from Node " + voterId, ConsoleColors.PURPLE);

        if (ioc.getStatus() != oldStatus && ioc.getStatus() != IoC.IoCStatus.PENDING) {
            handleStatusChange(ioc, oldStatus);
        }
    }

    /**
     * Broadcasts a vote to all peers with retry logic.
     *
     * @param iocId   Target IoC.
     * @param voterId ID of the voter.
     * @param vote    The vote type.
     */
    private void propagateVote(String iocId, int voterId, IoC.VoteType vote) {
        for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
            int peerId = entry.getKey();
            new Thread(() -> {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        DTIPNodeInterface peer = peers.get(peerId);
                        if (peer == null)
                            return;
                        peer.receiveVote(iocId, voterId, vote);
                        return; // Success
                    } catch (RemoteException e) {
                        if (attempt == 0 && refreshPeerStubSilent(peerId)) {
                            continue; // Retry silently
                        }
                        // Silent failure - votes are idempotent
                    }
                }
            }).start();
        }
    }

    // ==================================================================================
    // HEURISTIC & DECISION LOGIC
    // ==================================================================================

    private static final Random autoVoteRandom = new Random();

    /**
     * Schedules an automatic vote after a random delay.
     * <p>
     * Simulates the time taken for local threat analysis.
     *
     * @param ioc The IoC to analyze and vote on.
     */
    private void scheduleAutoVote(IoC ioc) {
        new Thread(() -> {
            try {
                // Simulate analysis delay (2-5 seconds)
                int delay = 2000 + autoVoteRandom.nextInt(3000);
                Thread.sleep(delay);

                if (ioc.getStatus() == IoC.IoCStatus.VERIFIED || ioc.getStatus() == IoC.IoCStatus.REJECTED) {
                    return;
                }

                // Compute vote based on policy and score
                IoC.VoteType localVote = computeLocalVote(ioc);

                log("AUTO-VOTE", "Local Analysis Complete: " + localVote + " for IoC " + ioc.getId(),
                        ConsoleColors.CYAN_BOLD);

                // Register and propagate
                vote(ioc.getId(), nodeId, localVote);

            } catch (Exception e) {
                // Interrupted
            }
        }).start();
    }

    /**
     * Computes the vote based on threat score and local policy.
     * Uses the Strategy Pattern via {@link ThreatAnalyzer}.
     * 
     * @param ioc The IoC to evaluate.
     * @return The calculated vote (CONFIRM/REJECT).
     */
    private IoC.VoteType computeLocalVote(IoC ioc) {
        int baseScore = computeThreatScore(ioc);

        int threshold;
        String policy;

        // Apply node-specific policy
        switch (nodeId) {
            case 0:
                threshold = 70;
                policy = "CONSERVATIVE";
                break;
            case 1:
                threshold = 30;
                policy = "AGGRESSIVE";
                break;
            case 2:
                threshold = 50;
                policy = "BALANCED";
                break;
            case 3:
                threshold = 10;
                policy = "PARANOID";
                break;
            case 4:
                threshold = 80;
                policy = "SKEPTICAL";
                break;
            default:
                threshold = 50;
                policy = "DEFAULT";
        }

        IoC.VoteType vote = baseScore >= threshold ? IoC.VoteType.CONFIRM : IoC.VoteType.REJECT;

        log("DEBUG", String.format("Vote Decision: Score %d vs Threshold %d (%s) -> %s",
                baseScore, threshold, policy, vote), ConsoleColors.CYAN);

        return vote;
    }

    /**
     * Computes numeric threat score using the active analyzer.
     * 
     * @param ioc The IoC.
     * @return Score (0-100).
     */
    private int computeThreatScore(IoC ioc) {
        return threatAnalyzer.analyze(ioc, reputations);
    }

    /**
     * Handles logic when an IoC changes status (e.g., reaches consensus).
     * <ul>
     * <li>Updates reputations.</li>
     * <li>If VERIFIED, triggers Ricart-Agrawala to write to the shared ledger.</li>
     * <li>Broadcasts the final status to sync peers.</li>
     * </ul>
     * 
     * @param ioc       The updated IoC.
     * @param oldStatus The previous status.
     */
    private void handleStatusChange(IoC ioc, IoC.IoCStatus oldStatus) {
        long confirms = ioc.getVotes().values().stream().filter(v -> v == IoC.VoteType.CONFIRM).count();
        long rejects = ioc.getVotes().values().stream().filter(v -> v == IoC.VoteType.REJECT).count();

        String statusColor = ioc.getStatus() == IoC.IoCStatus.VERIFIED ? ConsoleColors.GREEN_BOLD
                : ConsoleColors.RED_BOLD;
        log("CONSENSUS", "IoC " + ioc.getId() + " STATUS CHANGED: " + oldStatus + " -> " + ioc.getStatus() +
                " (Votes: " + confirms + " CONFIRM, " + rejects + " REJECT)", statusColor);

        logEvent("STATUS_CHANGE",
                "[Node " + nodeId + "] " + ioc.getId().substring(0, 8) + ": " + oldStatus + " -> " + ioc.getStatus(),
                ioc.getId());

        NodeReputation publisherRep = reputations.get(ioc.getPublisherId());

        if (ioc.getStatus() == IoC.IoCStatus.VERIFIED) {
            if (publisherRep != null)
                publisherRep.onIoCVerified();

            // Check if already written to ledger (prevent duplicates)
            if (writtenToLedger.contains(ioc.getId())) {
                log("LEDGER", "IoC " + ioc.getId() + " already written to ledger, skipping.", ConsoleColors.YELLOW);
                return;
            }

            // --- CRITICAL SECTION: Write to Shared Ledger ---
            new Thread(() -> {
                // Double-check after acquiring thread (race condition prevention)
                if (writtenToLedger.contains(ioc.getId())) {
                    return;
                }

                logEvent("MUTEX", "🔒 Acquiring Mutex for Ledger Commit...", ioc.getId());
                mutexManager.requestMutex();

                logEvent("MUTEX", "📝 Mutex acquired. Checking ledger...", ioc.getId());

                // Check if IoC is already in the ledger (network-level deduplication)
                String iocIdShort = ioc.getId().substring(0, 8);
                boolean alreadyInLedger = false;
                try (BufferedReader reader = new BufferedReader(new FileReader("shared_ledger.txt"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("ID: " + iocIdShort)) {
                            alreadyInLedger = true;
                            break;
                        }
                    }
                } catch (IOException e) {
                    // File might not exist yet, proceed with write
                }

                if (alreadyInLedger) {
                    log("LEDGER", "IoC " + iocIdShort + " already in ledger (written by another node), skipping.",
                            ConsoleColors.YELLOW);
                    writtenToLedger.add(ioc.getId());
                    mutexManager.releaseMutex();
                    logEvent("MUTEX", "⚪ Skipped duplicate. Mutex released.", ioc.getId());
                    return;
                }

                // Simulate write delay
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }

                try (PrintWriter out = new PrintWriter(new FileWriter("shared_ledger.txt", true))) {
                    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String iocType = ioc.getType() != null ? ioc.getType().toString() : "UNKNOWN";
                    String iocValue = ioc.getValue() != null ? ioc.getValue() : "N/A";
                    out.printf("[%s] Node %d | %s: %s | ID: %s | VERIFIED%n",
                            ts, nodeId, iocType, iocValue, iocIdShort);
                    // Mark as written to prevent future duplicates from this node
                    writtenToLedger.add(ioc.getId());
                    log("LEDGER", "✅ IoC " + iocIdShort + " written to ledger", ConsoleColors.GREEN);
                } catch (IOException e) {
                    logEvent("ERROR", "Failed to write to ledger: " + e.getMessage(), ioc.getId());
                }

                mutexManager.releaseMutex();
                logEvent("MUTEX", "⚪ Ledger updated. Mutex released.", ioc.getId());
            }).start();

        } else if (ioc.getStatus() == IoC.IoCStatus.REJECTED) {
            if (publisherRep != null)
                publisherRep.onIoCRejected();
        }

        // Update voter reputations
        for (Map.Entry<Integer, IoC.VoteType> voteEntry : ioc.getVotes().entrySet()) {
            NodeReputation voterRep = reputations.get(voteEntry.getKey());
            if (voterRep == null)
                continue;

            boolean votedCorrectly = (ioc.getStatus() == IoC.IoCStatus.VERIFIED
                    && voteEntry.getValue() == IoC.VoteType.CONFIRM) ||
                    (ioc.getStatus() == IoC.IoCStatus.REJECTED && voteEntry.getValue() == IoC.VoteType.REJECT);

            if (votedCorrectly)
                voterRep.onCorrectVote();
            else
                voterRep.onIncorrectVote();
        }

        // Force Sync Status to ensure eventual consistency
        IoC.IoCStatus finalStatus = ioc.getStatus();
        if (finalStatus == IoC.IoCStatus.VERIFIED || finalStatus == IoC.IoCStatus.REJECTED) {
            for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
                new Thread(() -> {
                    try {
                        entry.getValue().syncStatus(ioc.getId(), finalStatus);
                    } catch (RemoteException e) {
                    }
                }).start();
            }
        }
    }

    // ==================================================================================
    // MUTUAL EXCLUSION (RICART-AGRAWALA) DELEGATES
    // ==================================================================================

    @Override
    public void receiveMutexRequest(int requesterId, int sequenceNumber, int timestamp) throws RemoteException {
        checkOffline();
        mutexManager.handleRequest(requesterId, sequenceNumber, timestamp);
    }

    @Override
    public void receiveMutexReply(int replierId) throws RemoteException {
        checkOffline();
        mutexManager.handleReply(replierId);
    }

    @Override
    public void requestCriticalSection() throws RemoteException {
        checkOffline();
        log("MUTEX", "Manual trigger: Requesting Critical Section...", ConsoleColors.YELLOW_BOLD);
        new Thread(() -> mutexManager.requestMutex()).start();
    }

    /**
     * Broadcasts a mutex request to all connected peers.
     * <p>
     * If a peer is unreachable, we try to refresh its stub from the registry.
     * If still unreachable, we mark it as failed but do NOT count it as implicit
     * consent.
     * The timeout in RicartAgrawalaManager will handle failed peers gracefully.
     *
     * @param sequenceNumber Logical timestamp of the request.
     */
    public void broadcastMutexRequest(int sequenceNumber) {
        for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
            int peerId = entry.getKey();
            new Thread(() -> {
                // Retry with stub refresh on failure
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        DTIPNodeInterface peer = peers.get(peerId);
                        if (peer == null)
                            break;
                        peer.receiveMutexRequest(nodeId, sequenceNumber, 0);
                        return; // Success
                    } catch (RemoteException e) {
                        if (attempt == 0 && refreshPeerStubSilent(peerId)) {
                            continue; // Retry silently
                        }
                        // Final failure: mark as failed, timeout will handle
                        mutexManager.markPeerFailed(peerId);
                    }
                }
            }).start();
        }
    }

    /**
     * Attempts to refresh a peer's RMI stub from the registry.
     *
     * @param peerId The peer to refresh.
     * @return true if refresh succeeded, false otherwise.
     */
    private boolean refreshPeerStub(int peerId) {
        try {
            String url = "//localhost:" + (1099 + peerId) + "/DTIPNode" + peerId;
            DTIPNodeInterface newStub = (DTIPNodeInterface) Naming.lookup(url);
            peers.put(peerId, newStub);
            log("NET", "Refreshed stub for Peer " + peerId, ConsoleColors.GREEN);
            return true;
        } catch (Exception e) {
            log("NET", "Could not refresh stub for Peer " + peerId + ": " + e.getMessage(), ConsoleColors.RED);
            return false;
        }
    }

    /**
     * Silent version of refreshPeerStub - no logging (used in health checks to
     * avoid spam).
     */
    private boolean refreshPeerStubSilent(int peerId) {
        try {
            String url = "//localhost:" + (1099 + peerId) + "/DTIPNode" + peerId;
            DTIPNodeInterface newStub = (DTIPNodeInterface) Naming.lookup(url);
            peers.put(peerId, newStub);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performs health check on all connected peers.
     * <p>
     * Pings each peer and refreshes the stub if unreachable.
     * Only logs state CHANGES (online->offline or offline->online) to avoid spam.
     */
    public void performHealthCheck() {
        List<Integer> deadPeers = new ArrayList<>();

        for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
            int peerId = entry.getKey();
            try {
                entry.getValue().ping();
                // Peer is alive - if it was previously known offline, log recovery
                if (knownOfflinePeers.remove(peerId)) {
                    log("HEALTH", "Peer " + peerId + " is back ONLINE", ConsoleColors.GREEN);
                }
            } catch (RemoteException e) {
                // Only log if this is a NEW failure (not already known offline)
                if (knownOfflinePeers.add(peerId)) {
                    log("HEALTH", "Peer " + peerId + " went OFFLINE", ConsoleColors.YELLOW);
                }
                // Try to refresh stub silently (for when peer comes back)
                if (!refreshPeerStubSilent(peerId)) {
                    deadPeers.add(peerId);
                }
                // Note: We do NOT remove from knownOfflinePeers here.
                // Only a successful ping can do that.
            }
        }

        // Remove confirmed dead peers (registry lookup failed)
        for (Integer deadPeer : deadPeers) {
            peers.remove(deadPeer);
            knownOfflinePeers.remove(deadPeer);
            log("HEALTH", "Removed dead Peer " + deadPeer + " from peer list", ConsoleColors.RED);
        }
    }

    /**
     * Sends a reply to a specific node granting CS permission.
     * <p>
     * Implements retry with stub refresh to handle stale RMI connections.
     *
     * @param targetId Node to reply to.
     */
    public void sendMutexReply(int targetId) {
        new Thread(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                DTIPNodeInterface peer = peers.get(targetId);
                if (peer == null) {
                    return; // Peer not available
                }
                try {
                    peer.receiveMutexReply(nodeId);
                    return; // Success
                } catch (RemoteException e) {
                    if (attempt < 2) {
                        refreshPeerStubSilent(targetId);
                        try {
                            Thread.sleep(100 * (attempt + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    // Silent failure - mutex timeout handles unreachable peers
                }
            }
        }).start();
    }

    /**
     * @return Number of currently connected peers.
     */
    public int getPeerCount() {
        return peers.size();
    }

    // ==================================================================================
    // QUERY & UTIL
    // ==================================================================================

    @Override
    public List<IoC> getAllIoCs() throws RemoteException {
        checkOffline();
        return new ArrayList<>(iocDatabase.values());
    }

    @Override
    public List<IoC> getIoCsByType(IoC.IoCType type) throws RemoteException {
        List<IoC> result = new ArrayList<>();
        for (IoC ioc : iocDatabase.values()) {
            if (ioc.getType() == type)
                result.add(ioc);
        }
        return result;
    }

    @Override
    public IoC findIoC(String value) throws RemoteException {
        for (IoC ioc : iocDatabase.values()) {
            if (ioc.getValue().equals(value))
                return ioc;
        }
        return null;
    }

    @Override
    public IoC getIoC(String iocId) throws RemoteException {
        checkOffline();
        return iocDatabase.get(iocId);
    }

    @Override
    public List<NodeReputation> getAllReputations() throws RemoteException {
        return new ArrayList<>(reputations.values());
    }

    @Override
    public NodeReputation getReputation(int nodeId) throws RemoteException {
        return reputations.get(nodeId);
    }

    /**
     * Establishes a connection to a remote peer.
     * 
     * @param peerId The peer's ID.
     * @param peer   The RMI stub.
     */
    public void connectToPeer(int peerId, DTIPNodeInterface peer) {
        if (!peers.containsKey(peerId)) {
            peers.put(peerId, peer);
            log("NET", "Connected to Peer " + peerId, ConsoleColors.GREEN);

            // Feedback: Check if connected to all peers
            if (peers.size() == totalNodes - 1) {
                log("NET", "✅ FULL MESH ESTABLISHED: Connected to all " + (totalNodes - 1) + " peers!",
                        ConsoleColors.GREEN_BOLD);
            }

            try {
                peer.registerPeer(nodeId);
            } catch (RemoteException e) {
            }
        }
    }

    @Override
    public void registerPeer(int peerId) throws RemoteException {
        if (!peers.containsKey(peerId)) {
            try {
                DTIPNodeInterface peerRef = (DTIPNodeInterface) Naming.lookup("//localhost/DTIPNode" + peerId);
                peers.put(peerId, peerRef);
                log("NET", "Peer " + peerId + " registered (callback received)", ConsoleColors.BLUE);
            } catch (Exception e) {
                System.out.println("[Node " + nodeId + "] Could not lookup peer " + peerId);
            }
        }
    }

    @Override
    public boolean ping() throws RemoteException {
        checkOffline();
        return true;
    }

    @Override
    public NodeInfo getNodeInfo() throws RemoteException {
        return new NodeInfo(nodeId, nodeName, iocDatabase.size(), peers.size());
    }

    /**
     * @return The node's ID.
     */
    public int getId() {
        return nodeId;
    }

    @Override
    public List<IoC> sync(int[] theirVectorClock) throws RemoteException {
        checkOffline();
        // Delta Sync: Return only IoCs that are "ahead" of the requester's clock
        List<IoC> missing = new ArrayList<>();
        for (IoC ioc : iocDatabase.values()) {
            int[] iocClock = ioc.getVectorClock();
            if (iocClock != null) {
                for (int i = 0; i < Math.min(iocClock.length, theirVectorClock.length); i++) {
                    if (iocClock[i] > theirVectorClock[i]) {
                        missing.add(ioc);
                        break;
                    }
                }
            }
        }
        log("SYNC", "Sync requested by peer, returning " + missing.size() + " IoCs", ConsoleColors.BLUE);
        return missing;
    }

    @Override
    public int[] getVectorClock() throws RemoteException {
        checkOffline();
        return vectorClock.getClock();
    }

    @Override
    public String getStats() throws RemoteException {
        NodeReputation myRep = reputations.get(nodeId);
        return String.format("Node %d (%s)\nIoCs: %d\nPeers: %d\nRep Score: %.2f",
                nodeId, nodeName, iocDatabase.size(), peers.size(), myRep != null ? myRep.getScore() : 0.0);
    }

    // ==================================================================================
    // CHAOS ENGINEERING
    // ==================================================================================

    @Override
    public void setOffline(boolean offline) throws RemoteException {
        this.isOffline = offline;
        if (offline) {
            log("CHAOS", "💀 FAILURE SIMULATION STARTED - GOING DARK", ConsoleColors.RED_BOLD);
        } else {
            log("CHAOS", "♻️ NODE RESTARTED - PERFORMING SYNC", ConsoleColors.GREEN_BOLD);
            new Thread(this::performSync).start();
        }
    }

    @Override
    public boolean isOffline() throws RemoteException {
        return isOffline;
    }

    /**
     * Internal check to simulate node failure.
     * 
     * @throws RemoteException If node is in offline mode.
     */
    private void checkOffline() throws RemoteException {
        if (isOffline) {
            throw new RemoteException("Node offline (CHAOS SIMULATION)");
        }
    }

    @Override
    public void syncStatus(String iocId, IoC.IoCStatus newStatus) throws RemoteException {
        checkOffline();
        IoC ioc = iocDatabase.get(iocId);
        if (ioc != null) {
            if (ioc.getStatus() != newStatus) {
                ioc.setStatus(newStatus);
                log("SYNC", "IoC " + iocId + " status sync: " + newStatus, ConsoleColors.CYAN);
            }
        }
    }

    /**
     * Performs anti-entropy synchronization with all peers.
     * <p>
     * Called on node restart/recovery. Pulls missing IoCs from all peers
     * and catches up with the network state.
     */
    public void performSync() {
        for (Map.Entry<Integer, DTIPNodeInterface> entry : peers.entrySet()) {
            try {
                List<IoC> missing = entry.getValue().sync(vectorClock.getClock());
                for (IoC ioc : missing) {
                    if (!iocDatabase.containsKey(ioc.getId())) {
                        iocDatabase.put(ioc.getId(), ioc);
                        vectorClock.update(ioc.getVectorClock());
                    }
                }
                if (!missing.isEmpty()) {
                    log("SYNC", "Synced " + missing.size() + " IoCs from Peer " + entry.getKey(), ConsoleColors.BLUE);
                }
            } catch (RemoteException e) {
                // Peer offline
            }
        }

        // Vote Recovery: check for any PENDING IoCs we missed voting on
        for (IoC ioc : iocDatabase.values()) {
            if (ioc.getStatus() == IoC.IoCStatus.PENDING && !ioc.getVotes().containsKey(nodeId)) {
                log("RECOVERY", "Found unvoted IoC " + ioc.getId() + " - scheduling vote", ConsoleColors.YELLOW);
                scheduleAutoVote(ioc);
            }
        }
    }

    /**
     * Logs a message to the console with color coding.
     * 
     * @param category Log category tag.
     * @param message  Message content.
     * @param color    ANSI color code.
     */
    public void log(String category, String message, String color) {
        String prefix = "[" + "Node " + nodeId + "]";
        if (nodeName != null)
            prefix += " (" + nodeName + ")";
        System.out.println(
                color + String.format("%-15s", prefix) + " [" + category + "] " + ConsoleColors.RESET + message);
    }

    public void log(String category, String message) {
        log(category, message, ConsoleColors.WHITE);
    }

    // ==================================================================================
    // EDUCATIONAL FEATURES
    // ==================================================================================

    /**
     * Adds an event to the circular event log.
     * 
     * @param type   Event type.
     * @param detail Description.
     * @param iocId  Associated IoC ID (optional).
     */
    public void logEvent(String type, String detail, String iocId) {
        NodeEvent event = new NodeEvent(System.currentTimeMillis(), type, detail, iocId);
        synchronized (eventLog) {
            eventLog.add(event);
            while (eventLog.size() > MAX_EVENT_LOG_SIZE)
                eventLog.remove(0);
        }
    }

    @Override
    public List<NodeEvent> getRecentEvents() throws RemoteException {
        checkOffline();
        synchronized (eventLog) {
            return new ArrayList<>(eventLog);
        }
    }

    @Override
    public MutexState getMutexState() throws RemoteException {
        checkOffline();
        return mutexManager.getState();
    }

    @Override
    public int[] getVectorClockArray() throws RemoteException {
        checkOffline();
        synchronized (vectorClock) {
            return vectorClock.getClock();
        }
    }

    /** Prints startup info banner. */
    private void printStartupBanner() {
        System.out.println(
                ConsoleColors.BLUE_BOLD + "NODE " + nodeId + ": " + nodeName + " STARTED" + ConsoleColors.RESET);
    }

    // ==================================================================================
    // MAIN ENTRY POINT
    // ==================================================================================

    /**
     * Main entry point for the Node process.
     * 
     * @param args Command line arguments: nodeId nodeName totalNodes [peerURL...]
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java core.DTIPNode <nodeId> <nodeName> <totalNodes> [peer1URL] ...");
            System.exit(1);
        }

        try {
            int nodeId = Integer.parseInt(args[0]);
            String nodeName = args[1];
            int totalNodes = Integer.parseInt(args[2]);
            int rmiPort = 1099 + nodeId;

            DTIPNode node = new DTIPNode(nodeId, nodeName, totalNodes);

            java.rmi.registry.Registry registry;
            try {
                registry = java.rmi.registry.LocateRegistry.createRegistry(rmiPort);
            } catch (Exception e) {
                registry = java.rmi.registry.LocateRegistry.getRegistry(rmiPort);
            }

            registry.rebind("DTIPNode" + nodeId, node);
            System.out.println("DTIPNode bound on port " + rmiPort);

            // Auto-Discovery loop with periodic health checks
            if (args.length == 3) {
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        System.out.println("Auto-discovering peers (Periodic)...");
                        int healthCheckCounter = 0;
                        while (true) {
                            // Discovery: connect to new peers
                            for (int i = 0; i < totalNodes; i++) {
                                if (i == nodeId)
                                    continue;
                                try {
                                    String url = "//localhost:" + (1099 + i) + "/DTIPNode" + i;
                                    DTIPNodeInterface peer = (DTIPNodeInterface) Naming.lookup(url);
                                    node.connectToPeer(i, peer);
                                } catch (Exception e) {
                                    // Only print error if not already connected
                                    if (!node.peers.containsKey(i)) {
                                        System.err.println("Failed to connect to Node " + i + ": " + e.getMessage());
                                    }
                                }
                            }

                            // Health check every 3 cycles (15 seconds)
                            healthCheckCounter++;
                            if (healthCheckCounter >= 3) {
                                healthCheckCounter = 0;
                                node.performHealthCheck();
                            }

                            Thread.sleep(5000); // Retry every 5 seconds
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                // Manual peers
                for (int i = 3; i < args.length; i++) {
                    try {
                        DTIPNodeInterface peer = (DTIPNodeInterface) Naming.lookup(args[i]);
                        node.connectToPeer(peer.getNodeInfo().getNodeId(), peer);
                    } catch (Exception e) {
                        System.err.println("Failed to connect to " + args[i]);
                    }
                }
            }

            // Start Sensor Listener
            new Thread(new SensorListener(9000 + nodeId, node, nodeId, nodeName)).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
