package core;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Implementation of Vector Clocks for Causal Ordering in Distributed Systems.
 * <p>
 * Based on the Fidge/Mattern algorithm. Each node maintains a vector of integers
 * representing its knowledge of logical time across the entire system.
 * <p>
 * This class provides mechanisms to:
 * <ul>
 *   <li>Increment local time (Tick).</li>
 *   <li>Merge with remote time (Update).</li>
 *   <li>Compare timestamps to determine Happened-Before relationships.</li>
 * </ul>
 */
public class VectorClock implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int[] clock;
    private int nodeId;
    private int numNodes;
    
    /**
     * Initializes a new Vector Clock.
     *
     * @param nodeId The unique ID of the owner node (index in the vector).
     * @param numNodes The total number of nodes in the system (vector size).
     */
    public VectorClock(int nodeId, int numNodes) {
        this.nodeId = nodeId;
        this.numNodes = numNodes;
        this.clock = new int[numNodes];
    }
    
    /**
     * Copy constructor. Creates a deep copy of another VectorClock.
     *
     * @param other The VectorClock to clone.
     */
    public VectorClock(VectorClock other) {
        this.nodeId = other.nodeId;
        this.numNodes = other.numNodes;
        this.clock = Arrays.copyOf(other.clock, other.clock.length);
    }
    
    /**
     * Increments the local component of the vector clock.
     * <p>
     * Must be called before performing a local event or sending a message.
     * Rule: VC[i] = VC[i] + 1
     */
    public void tick() {
        clock[nodeId]++;
    }
    
    /**
     * Updates the local vector clock upon receiving a message with a remote timestamp.
     * <p>
     * Implements the merge rule:
     * 1. VC[j] = max(VC[j], received[j]) for all j
     * 2. VC[i] = VC[i] + 1 (increment local time)
     *
     * @param receivedClock The vector clock attached to the received message.
     */
    public void update(int[] receivedClock) {
        if (receivedClock == null || receivedClock.length != numNodes) {
            return;
        }
        for (int i = 0; i < numNodes; i++) {
            clock[i] = Math.max(clock[i], receivedClock[i]);
        }
        clock[nodeId]++;
    }
    
    /**
     * Compares this vector clock with another to determine causal relationship.
     *
     * @param other The other vector clock to compare against.
     * @return 
     * <ul>
     *   <li><b>-1</b> if this -> other (Happened-Before)</li>
     *   <li><b>1</b> if other -> this (Happened-After)</li>
     *   <li><b>0</b> if this || other (Concurrent)</li>
     * </ul>
     */
    public int compareTo(VectorClock other) {
        boolean thisLess = false;
        boolean otherLess = false;
        
        for (int i = 0; i < numNodes; i++) {
            if (this.clock[i] < other.clock[i]) thisLess = true;
            if (this.clock[i] > other.clock[i]) otherLess = true;
        }
        
        if (thisLess && !otherLess) return -1;  // this happened-before other
        if (otherLess && !thisLess) return 1;   // other happened-before this
        return 0;  // Concurrent
    }
    
    /**
     * Convenience method to check if this clock causally precedes another.
     *
     * @param other The other vector clock.
     * @return true if this clock happened-before the other.
     */
    public boolean happenedBefore(VectorClock other) {
        return compareTo(other) == -1;
    }
    
    /**
     * Returns a copy of the raw integer array backing this vector clock.
     *
     * @return An int array representing the vector.
     */
    public int[] getClock() {
        return Arrays.copyOf(clock, clock.length);
    }
    
    @Override
    public String toString() {
        return Arrays.toString(clock);
    }
}
