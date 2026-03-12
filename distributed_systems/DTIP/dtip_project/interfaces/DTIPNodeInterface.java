package interfaces;

import model.IoC;
import model.NodeInfo;
import model.NodeReputation;
import model.NodeEvent;
import model.MutexState;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote Interface defining the operations available on a DTIP P2P Node.
 * <p>
 * This interface exposes methods for:
 * <ul>
 * <li><b>IoC Lifecycle:</b> Publishing and receiving Indicators of
 * Compromise.</li>
 * <li><b>Consensus:</b> Voting and status synchronization.</li>
 * <li><b>Mutual Exclusion:</b> Ricart-Agrawala algorithm message handling.</li>
 * <li><b>Network Maintenance:</b> Peer discovery and health checks.</li>
 * <li><b>Monitoring:</b> Metrics and state retrieval for the dashboard.</li>
 * </ul>
 * 
 * @see core.DTIPNode
 */
public interface DTIPNodeInterface extends Remote {

    // ==================================================================================
    // IOC LIFECYCLE & GOSSIP
    // ==================================================================================

    /**
     * Publishes a new Indicator of Compromise (IoC) to the network.
     * <p>
     * This operation triggers the Gossip protocol to propagate the IoC to all
     * peers.
     * It also increments the local Vector Clock.
     *
     * @param ioc The IoC object to publish.
     * @return The unique ID of the published IoC.
     * @throws RemoteException If RMI communication fails.
     */
    String publishIoC(IoC ioc) throws RemoteException;

    /**
     * Receives an IoC propagated from another peer (Gossip protocol).
     * <p>
     * The node will:
     * 1. Check if the IoC is already known (deduplication).
     * 2. Update its local Vector Clock using the message's clock.
     * 3. Forward (gossip) the IoC to other peers if it's new.
     *
     * @param ioc        The IoC received.
     * @param fromNodeId The ID of the node that sent this message.
     * @throws RemoteException If RMI communication fails.
     */
    void receiveIoC(IoC ioc, int fromNodeId) throws RemoteException;

    // ==================================================================================
    // CONSENSUS & VOTING
    // ==================================================================================

    /**
     * Casts a vote on a specific IoC.
     * <p>
     * This method is called to propagate a local decision (CONFIRM/REJECT) to a
     * peer.
     *
     * @param iocId   The unique ID of the IoC being voted on.
     * @param voterId The ID of the node casting the vote.
     * @param vote    The vote type (CONFIRM or REJECT).
     * @throws RemoteException If RMI communication fails.
     */
    void vote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException;

    /**
     * Handles an incoming vote from a peer.
     * <p>
     * Updates the local vote count for the specified IoC. If the Quorum is reached,
     * the status of the IoC may change (e.g., to VERIFIED).
     *
     * @param iocId   The unique ID of the IoC.
     * @param voterId The ID of the peer that voted.
     * @param vote    The vote decision.
     * @throws RemoteException If RMI communication fails.
     */
    void receiveVote(String iocId, int voterId, IoC.VoteType vote) throws RemoteException;

    // ==================================================================================
    // MUTUAL EXCLUSION (RICART-AGRAWALA)
    // ==================================================================================

    /**
     * Receives a request to enter the Critical Section (CS).
     * <p>
     * Part of the Ricart-Agrawala algorithm. The receiver must decide whether to
     * reply immediately or defer the reply based on priority (sequence numbers).
     *
     * @param requesterId    The ID of the node requesting the CS.
     * @param sequenceNumber The requester's sequence number (timestamp).
     * @param timestamp      Optional secondary timestamp (often unused in basic
     *                       impl).
     * @throws RemoteException If RMI communication fails.
     */
    void receiveMutexRequest(int requesterId, int sequenceNumber, int timestamp) throws RemoteException;

    /**
     * Receives a permission reply for entering the Critical Section.
     *
     * @param replierId The ID of the node granting permission.
     * @throws RemoteException If RMI communication fails.
     */
    void receiveMutexReply(int replierId) throws RemoteException;

    /**
     * Triggers a request to enter the Critical Section.
     * <p>
     * Used primarily for demonstration/testing purposes to force a node
     * to attempt to acquire the distributed mutex.
     *
     * @throws RemoteException If RMI communication fails.
     */
    void requestCriticalSection() throws RemoteException;

    // ==================================================================================
    // QUERIES & STATE RETRIEVAL
    // ==================================================================================

    /**
     * Retrieves all IoCs currently stored in the local database.
     *
     * @return A list of all known IoC objects.
     * @throws RemoteException If RMI communication fails.
     */
    List<IoC> getAllIoCs() throws RemoteException;

    /**
     * Retrieves IoCs filtered by their type.
     *
     * @param type The type of IoC to filter by (e.g., IP, URL).
     * @return A list of matching IoCs.
     * @throws RemoteException If RMI communication fails.
     */
    List<IoC> getIoCsByType(IoC.IoCType type) throws RemoteException;

    /**
     * Finds a specific IoC by its value (e.g., "192.168.1.1").
     *
     * @param value The value to search for.
     * @return The IoC object if found, null otherwise.
     * @throws RemoteException If RMI communication fails.
     */
    IoC findIoC(String value) throws RemoteException;

    /**
     * Retrieves a specific IoC by its unique ID.
     *
     * @param iocId The unique string ID of the IoC.
     * @return The IoC object if found, null otherwise.
     * @throws RemoteException If RMI communication fails.
     */
    IoC getIoC(String iocId) throws RemoteException;

    // ==================================================================================
    // REPUTATION SYSTEM
    // ==================================================================================

    /**
     * Retrieves the reputation metrics for all known nodes.
     *
     * @return A list of NodeReputation objects.
     * @throws RemoteException If RMI communication fails.
     */
    List<NodeReputation> getAllReputations() throws RemoteException;

    /**
     * Retrieves the reputation metrics for a specific node.
     *
     * @param nodeId The target node ID.
     * @return The NodeReputation object.
     * @throws RemoteException If RMI communication fails.
     */
    NodeReputation getReputation(int nodeId) throws RemoteException;

    // ==================================================================================
    // NETWORK & DISCOVERY
    // ==================================================================================

    /**
     * Simple health check method.
     *
     * @return true if the node is reachable and active.
     * @throws RemoteException If the node is unreachable (offline).
     */
    boolean ping() throws RemoteException;

    /**
     * Retrieves basic metadata about this node.
     *
     * @return A NodeInfo object containing ID, name, and stats summary.
     * @throws RemoteException If RMI communication fails.
     */
    NodeInfo getNodeInfo() throws RemoteException;

    /**
     * Performs an anti-entropy synchronization exchange.
     * <p>
     * The caller sends its Vector Clock. The receiver compares it with its own
     * and returns a list of IoCs that the caller is missing.
     *
     * @param myVectorClock The vector clock of the requesting node.
     * @return A list of missing IoCs (delta sync).
     * @throws RemoteException If RMI communication fails.
     */
    List<IoC> sync(int[] myVectorClock) throws RemoteException;

    /**
     * Registers a new peer connection.
     * <p>
     * Used for bidirectional connection establishment during discovery.
     *
     * @param peerId The ID of the peer announcing itself.
     * @throws RemoteException If RMI communication fails.
     */
    void registerPeer(int peerId) throws RemoteException;

    // ==================================================================================
    // METRICS & DEBUGGING
    // ==================================================================================

    /**
     * Retrieves the current Vector Clock of this node.
     *
     * @return An integer array representing the vector clock state.
     * @throws RemoteException If RMI communication fails.
     */
    int[] getVectorClock() throws RemoteException;

    /**
     * Retrieves a human-readable summary of the node's statistics.
     *
     * @return A formatted string with stats.
     * @throws RemoteException If RMI communication fails.
     */
    String getStats() throws RemoteException;

    // ==================================================================================
    // CHAOS ENGINEERING (FAILURE SIMULATION)
    // ==================================================================================

    /**
     * Simulates a node failure or recovery.
     * <p>
     * When offline, the node throws RemoteException on all method calls,
     * simulating a network partition or crash.
     *
     * @param offline true to simulate failure, false to recover.
     * @throws RemoteException If RMI communication fails.
     */
    void setOffline(boolean offline) throws RemoteException;

    /**
     * Checks if the node is currently in simulated offline mode.
     *
     * @return true if simulating failure.
     * @throws RemoteException If RMI communication fails.
     */
    boolean isOffline() throws RemoteException;

    // ==================================================================================
    // CONSENSUS FINALIZATION
    // ==================================================================================

    /**
     * Forces a status update for a specific IoC across the network.
     * <p>
     * Typically used when consensus is reached to ensure all nodes converge
     * to the final state (VERIFIED or REJECTED).
     *
     * @param iocId     The ID of the IoC.
     * @param newStatus The final status to apply.
     * @throws RemoteException If RMI communication fails.
     */
    void syncStatus(String iocId, IoC.IoCStatus newStatus) throws RemoteException;

    // ==================================================================================
    // DASHBOARD & VISUALIZATION
    // ==================================================================================

    /**
     * Retrieves the log of recent events for visualization.
     *
     * @return A list of the latest NodeEvent objects.
     * @throws RemoteException If RMI communication fails.
     */
    List<NodeEvent> getRecentEvents() throws RemoteException;

    /**
     * Retrieves the internal state of the Ricart-Agrawala manager.
     *
     * @return A MutexState object capturing queue sizes and flags.
     * @throws RemoteException If RMI communication fails.
     */
    MutexState getMutexState() throws RemoteException;

    /**
     * Duplicate of getVectorClock(), explicitly for dashboard use.
     *
     * @return The vector clock array.
     * @throws RemoteException If RMI communication fails.
     */
    int[] getVectorClockArray() throws RemoteException;
}
