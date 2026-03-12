package model;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an Indicator of Compromise (IoC) in the DTIP network.
 * <p>
 * This class encapsulates all data related to a threat indicator, including:
 * <ul>
 * <li>Core data (type, value, confidence).</li>
 * <li>Propagation metadata (Vector Clock, seenBy set).</li>
 * <li>Consensus state (votes, current status).</li>
 * </ul>
 * <p>
 * Implements {@link Serializable} for RMI transmission.
 */
public class IoC implements Serializable {
    private static final long serialVersionUID = 1L;

    // Identity
    private String id;
    private IoCType type;
    private String value;

    // Metadata
    private int confidence;
    private List<String> tags;
    private String description;

    // Origin
    private int publisherId;
    private String publisherName;
    private long publishedAt;

    // Propagation (Vector Clock)
    private int[] vectorClock;
    private Set<Integer> seenBy;

    // Voting
    private Map<Integer, VoteType> votes;
    private IoCStatus status;
    private int quorumSize;
    private int totalNodes;
    private int activeNodesAtCreation; // Fixed at publish time for stalemate detection

    /**
     * Enumeration of supported IoC types.
     */
    public enum IoCType {
        IP, DOMAIN, HASH, URL, EMAIL, CVE
    }

    /**
     * Enumeration of possible votes.
     */
    public enum VoteType {
        CONFIRM, REJECT
    }

    /**
     * Enumeration of IoC lifecycle states.
     */
    public enum IoCStatus {
        /** Initial state when published. */
        PENDING,
        /** Consensus reached a tie, waiting for manual SOC intervention. */
        AWAITING_SOC,
        /** Confirmed as a valid threat by the network. */
        VERIFIED,
        /** Rejected as a false positive by the network. */
        REJECTED,
        /** Time-to-live expired (not currently implemented). */
        EXPIRED
    }

    /**
     * Full constructor for creating a new IoC.
     *
     * @param type          The type of indicator (e.g., IP, URL).
     * @param value         The indicator string (e.g., "192.168.1.1").
     * @param confidence    Initial confidence score (0-100).
     * @param tags          List of semantic tags (e.g., "ransomware").
     * @param publisherId   Node ID of the creator.
     * @param publisherName Human-readable name of the creator.
     * @param totalNodes    Total network size (for reference).
     * @param activeNodes   Number of active nodes at creation (for stalemate
     *                      detection).
     */
    public IoC(IoCType type, String value, int confidence,
            List<String> tags, int publisherId, String publisherName, int totalNodes, int activeNodes) {
        // Deterministic ID based on type+value (same IoC = same ID)
        this.id = generateId(type, value);
        this.type = type;
        this.value = value;
        this.confidence = confidence;
        this.tags = tags != null ? tags : new ArrayList<>();
        this.publisherId = publisherId;
        this.publisherName = publisherName;
        this.publishedAt = System.currentTimeMillis();
        this.votes = new ConcurrentHashMap<>();
        this.seenBy = new HashSet<>();
        this.status = IoCStatus.PENDING;

        this.totalNodes = totalNodes;
        // Quorum based on ACTIVE nodes at creation time
        // Example: 4 active nodes -> Quorum = 3
        this.activeNodesAtCreation = activeNodes;
        this.quorumSize = (activeNodes / 2) + 1;
    }

    /**
     * Constructor for totalNodes = activeNodes (all nodes online).
     */
    public IoC(IoCType type, String value, int confidence,
            List<String> tags, int publisherId, String publisherName, int totalNodes) {
        this(type, value, confidence, tags, publisherId, publisherName, totalNodes, totalNodes);
    }

    /**
     * Legacy/Convenience constructor assuming a default 5-node network.
     */
    public IoC(IoCType type, String value, int confidence,
            List<String> tags, int publisherId, String publisherName) {
        this(type, value, confidence, tags, publisherId, publisherName, 5);
    }

    /**
     * Generates a stable, deterministic ID based on type and value.
     * Ensures that multiple nodes reporting the same threat generate the same ID.
     */
    private static String generateId(IoCType type, String value) {
        String input = type.name() + ":" + value;
        return Integer.toHexString(input.hashCode()).substring(0,
                Math.min(8, Integer.toHexString(input.hashCode()).length()));
    }

    /**
     * Registers a vote from a peer and triggers status update logic.
     * Uses activeNodesAtCreation (fixed at publish time) for consistent decisions.
     *
     * @param nodeId The ID of the voting node (-1 for SOC/Manual override).
     * @param vote   The vote cast (CONFIRM/REJECT).
     */
    public void addVote(int nodeId, VoteType vote) {
        votes.put(nodeId, vote);

        // SOC (Node -1) has absolute authority
        if (nodeId == -1) {
            if (vote == VoteType.CONFIRM) {
                status = IoCStatus.VERIFIED;
            } else {
                status = IoCStatus.REJECTED;
            }
            return;
        }

        updateStatus();
    }

    /**
     * Recalculates the IoC status based on current votes.
     * - Uses FIXED quorumSize (based on activeNodesAtCreation)
     * - Uses activeNodesAtCreation for stalemate detection
     * All values are fixed at IoC creation for consistency across nodes.
     */
    private void updateStatus() {
        // Count valid node votes (excluding SOC/Manual -1)
        long nodeConfirms = votes.entrySet().stream()
                .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.CONFIRM)
                .count();
        long nodeRejects = votes.entrySet().stream()
                .filter(e -> e.getKey() >= 0 && e.getValue() == VoteType.REJECT)
                .count();

        // 1. Majority Rule with FIXED Quorum (based on activeNodesAtCreation)
        if (nodeConfirms >= quorumSize) {
            status = IoCStatus.VERIFIED;
            return;
        }

        if (nodeRejects >= quorumSize) {
            status = IoCStatus.REJECTED;
            return;
        }

        // 2. Stalemate Detection
        // If all nodes that were active at creation have voted but no quorum, escalate.
        // Example: 4 active at creation (quorum=3). Votes: 2v2 → AWAITING_SOC
        long totalVotes = nodeConfirms + nodeRejects;

        if (totalVotes >= activeNodesAtCreation && status == IoCStatus.PENDING) {
            status = IoCStatus.AWAITING_SOC;
        }
    }

    // ==================================================================================
    // GETTERS & SETTERS
    // ==================================================================================

    public String getId() {
        return id;
    }

    public IoCType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getConfidence() {
        return confidence;
    }

    public List<String> getTags() {
        return tags;
    }

    public int getPublisherId() {
        return publisherId;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public int[] getVectorClock() {
        return vectorClock;
    }

    public void setVectorClock(int[] vectorClock) {
        this.vectorClock = vectorClock;
    }

    public Set<Integer> getSeenBy() {
        return seenBy;
    }

    public Map<Integer, VoteType> getVotes() {
        return votes;
    }

    public IoCStatus getStatus() {
        return status;
    }

    public void setStatus(IoCStatus status) {
        this.status = status;
    }

    public int getQuorumSize() {
        return quorumSize;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public int getActiveNodesAtCreation() {
        return activeNodesAtCreation;
    }

    /**
     * Returns a concise string representation for logging.
     */
    public String toShortString() {
        return String.format("[%s] %s %s (%s) - %s",
                id, type, value, confidence + "%", status);
    }
}