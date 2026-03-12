package model;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * Immutable snapshot of the Ricart-Agrawala Mutual Exclusion state.
 * <p>
 * Used primarily for the educational dashboard to visualize the internal
 * state of the mutex algorithm (queues, flags) without exposing the manager itself.
 */
public class MutexState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean requesting;
    private final int sequenceNumber;
    private final List<Integer> deferredReplies;
    private final int outstandingReplies;

    /**
     * Constructs a state snapshot.
     *
     * @param requesting True if the node is currently requesting CS.
     * @param sequenceNumber The logical timestamp of the current request.
     * @param deferredReplies List of node IDs waiting for a reply from this node.
     * @param outstandingReplies Number of replies this node is still waiting for.
     */
    public MutexState(boolean requesting, int sequenceNumber,
            List<Integer> deferredReplies, int outstandingReplies) {
        this.requesting = requesting;
        this.sequenceNumber = sequenceNumber;
        this.deferredReplies = new ArrayList<>(deferredReplies);
        this.outstandingReplies = outstandingReplies;
    }

    public boolean isRequesting() {
        return requesting;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public List<Integer> getDeferredReplies() {
        return new ArrayList<>(deferredReplies);
    }

    public int getOutstandingReplies() {
        return outstandingReplies;
    }

    /**
     * Checks if the node is currently executing in the Critical Section.
     * True only if requesting AND all replies have been received.
     */
    public boolean isInCriticalSection() {
        return requesting && outstandingReplies == 0;
    }

    /**
     * Checks if the node is waiting to enter the Critical Section.
     */
    public boolean isWaiting() {
        return requesting && outstandingReplies > 0;
    }

    @Override
    public String toString() {
        if (isInCriticalSection()) {
            return "HELD (in CS)";
        } else if (isWaiting()) {
            return "WANTED (waiting for " + outstandingReplies + " replies)";
        } else {
            return "RELEASED";
        }
    }
}