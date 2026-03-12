package core;

import interfaces.DTIPNodeInterface;
import model.MutexState;
import util.ConsoleColors;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Ricart-Agrawala algorithm for Distributed Mutual Exclusion.
 * <p>
 * This class coordinates access to a critical section (e.g., the shared ledger file)
 * ensuring that only one node enters it at a time.
 * <p>
 * <b>Algorithm Overview:</b>
 * <ol>
 *   <li>Node requests entry by broadcasting a REQUEST(seq_num) to all peers.</li>
 *   <li>Node waits for REPLY from all N-1 peers.</li>
 *   <li>If a peer receives a REQUEST:
 *     <ul>
 *       <li>If not interested in CS, send REPLY.</li>
 *       <li>If already in CS, defer REPLY.</li>
 *       <li>If also requesting CS, compare sequence numbers. Lowest wins. If we lose, send REPLY; else defer.</li>
 *     </ul>
 *   </li>
 *   <li>When leaving CS, send all deferred REPLYs.</li>
 * </ol>
 *
 * <b>Fault Tolerance:</b> Implements a timeout mechanism with failed peer tracking
 * to prevent deadlocks if a peer crashes.
 */
public class RicartAgrawalaManager {

    private DTIPNode node;
    private int myNodeId;

    // State variables
    private boolean requesting;
    private int mySequenceNumber;
    private int highestSequenceNumberSeen;

    // Coordination variables
    private int outstandingReplies;
    private boolean inCriticalSection;
    private List<Integer> deferredReplies;
    private Set<Integer> failedPeers; // Track peers that failed during broadcast
    private Set<Integer> receivedRepliesFrom; // Track which peers actually replied
    private final Object lock = new Object();

    /**
     * Initializes the manager for a specific node.
     *
     * @param node Reference to the parent DTIPNode (for callbacks and logging).
     * @param myNodeId The unique ID of this node.
     */
    public RicartAgrawalaManager(DTIPNode node, int myNodeId) {
        this.node = node;
        this.myNodeId = myNodeId;
        this.requesting = false;
        this.mySequenceNumber = 0;
        this.highestSequenceNumberSeen = 0;
        this.outstandingReplies = 0;
        this.inCriticalSection = false;
        this.deferredReplies = new ArrayList<>();
        this.failedPeers = new HashSet<>();
        this.receivedRepliesFrom = new HashSet<>();
    }

    /**
     * Marks a peer as failed during mutex request broadcast.
     * Called when a peer is unreachable and stub refresh also failed.
     *
     * @param peerId The peer that failed.
     */
    public void markPeerFailed(int peerId) {
        synchronized (lock) {
            if (requesting && !receivedRepliesFrom.contains(peerId)) {
                failedPeers.add(peerId);
                // If this peer was one of the outstanding replies, count it now
                // (graceful degradation: treat confirmed-dead peers as implicit consent)
                if (outstandingReplies > 0) {
                    outstandingReplies--;
                    node.log("MUTEX", "Peer " + peerId + " marked failed. Remaining: " + outstandingReplies,
                            ConsoleColors.YELLOW);
                    if (outstandingReplies == 0) {
                        lock.notifyAll();
                    }
                }
            }
        }
    }

    /**
     * Requests entry to the Critical Section.
     * <p>
     * This method blocks the calling thread until permission is granted by all peers
     * or a timeout occurs.
     * <p>
     * <b>Timeout Strategy:</b> Dynamic timeout based on peer count (2s per peer + buffer).
     * If not all replies are received, the request is aborted to preserve liveness.
     * Failed peers (detected during broadcast) are counted as implicit consent.
     */
    public void requestMutex() {
        synchronized (lock) {
            requesting = true;
            mySequenceNumber = highestSequenceNumberSeen + 1;
            outstandingReplies = node.getPeerCount();
            failedPeers.clear();
            receivedRepliesFrom.clear();
        }

        node.log("MUTEX", "Requesting CS with seq=" + mySequenceNumber, ConsoleColors.CYAN);

        // Broadcast REQUEST to all peers (asynchronous/non-blocking calls)
        node.broadcastMutexRequest(mySequenceNumber);

        // Wait for replies with dynamic timeout
        synchronized (lock) {
            // Dynamic timeout: 3s per peer + 2s buffer (more lenient for slow networks)
            long timeout = Math.max(10000, 3000L * node.getPeerCount() + 2000);
            long deadline = System.currentTimeMillis() + timeout;

            while (outstandingReplies > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    // Timeout! Log which peers didn't reply
                    node.log("MUTEX", "TIMEOUT after " + (timeout/1000) + "s. Missing " + outstandingReplies +
                            " replies. Failed peers: " + failedPeers, ConsoleColors.RED_BOLD);
                    requesting = false;
                    outstandingReplies = 0; // Reset
                    failedPeers.clear();
                    receivedRepliesFrom.clear();
                    return; // Exit without entering CS
                }

                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    node.log("MUTEX", "Interrupted during wait", ConsoleColors.RED);
                    requesting = false;
                    failedPeers.clear();
                    receivedRepliesFrom.clear();
                    return;
                }
            }
            inCriticalSection = true;
            failedPeers.clear();
            receivedRepliesFrom.clear();
        }

        node.log("MUTEX", "Entered Critical Section", ConsoleColors.GREEN_BOLD);
    }

    /**
     * Exits the Critical Section.
     * <p>
     * Resets the 'inCriticalSection' flag and sends REPLY messages to all
     * nodes that were deferred while this node was holding the lock.
     */
    public void releaseMutex() {
        node.log("MUTEX", "Releasing CS", ConsoleColors.CYAN);

        synchronized (lock) {
            inCriticalSection = false;
            requesting = false;

            // Send deferred replies
            for (Integer targetId : deferredReplies) {
                node.sendMutexReply(targetId);
            }
            deferredReplies.clear();
        }
    }

    /**
     * Handles an incoming REQUEST message from a peer.
     * <p>
     * Decides whether to grant permission immediately (send REPLY) or defer it
     * based on sequence number priority.
     *
     * @param requesterId The ID of the requesting node.
     * @param sequenceNumber The sequence number of the request.
     * @param timestamp Optional timestamp (unused in this logic).
     */
    public void handleRequest(int requesterId, int sequenceNumber, int timestamp) {
        synchronized (lock) {
            highestSequenceNumberSeen = Math.max(highestSequenceNumberSeen, sequenceNumber);

            boolean defer = false;

            if (requesting) {
                // Priority Check: Lower sequence number wins.
                // Tie-breaking: Lower Node ID wins.
                boolean weHavePriority = (mySequenceNumber < sequenceNumber) ||
                        (mySequenceNumber == sequenceNumber && myNodeId < requesterId);

                if (weHavePriority) {
                    defer = true;
                }
            }

            if (defer) {
                node.log("MUTEX", "Deferring reply to Node " + requesterId + " (my seq=" + mySequenceNumber
                        + ", their seq=" + sequenceNumber + ")", ConsoleColors.YELLOW);
                deferredReplies.add(requesterId);
            } else {
                node.log("MUTEX", "Granting immediate reply to Node " + requesterId, ConsoleColors.GREEN);
                // Reply immediately
                node.sendMutexReply(requesterId);
            }
        }
    }

    /**
     * Handles an incoming REPLY message.
     * <p>
     * Decrements the count of outstanding replies. If the count reaches zero,
     * it notifies the waiting thread to enter the Critical Section.
     *
     * @param replierId The ID of the node that sent the reply.
     */
    public void handleReply(int replierId) {
        synchronized (lock) {
            // Avoid double-counting if peer was already marked as failed
            if (receivedRepliesFrom.contains(replierId)) {
                return; // Already processed this reply
            }
            receivedRepliesFrom.add(replierId);

            // If this peer was previously marked as failed, it recovered - but we already counted it
            if (failedPeers.contains(replierId)) {
                node.log("MUTEX", "Peer " + replierId + " recovered (was marked failed). Already counted.",
                        ConsoleColors.GREEN);
                return;
            }

            outstandingReplies--;
            node.log("MUTEX", "Got reply from Node " + replierId + ". Remaining: " + outstandingReplies,
                    ConsoleColors.CYAN);
            if (outstandingReplies == 0) {
                lock.notifyAll();
            }
        }
    }

    /**
     * Retrieves the current state of the mutex manager for monitoring.
     *
     * @return A MutexState snapshot object.
     */
    public MutexState getState() {
        synchronized (lock) {
            return new MutexState(requesting, mySequenceNumber,
                    new ArrayList<>(deferredReplies), outstandingReplies);
        }
    }
}
