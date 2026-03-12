package util;

import model.SystemMetrics;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class for collecting and aggregating system-wide metrics.
 * <p>
 * This collector is used to track the performance of distributed algorithms
 * (Consensus time, Gossip hops, Mutex wait time) and node accuracy.
 * <p>
 * It is thread-safe to allow concurrent updates from multiple node threads.
 */
public class MetricsCollector {
    private static MetricsCollector instance;

    // Consensus metrics
    private final List<Long> consensusTimes = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> consensusStartTimes = new ConcurrentHashMap<>();

    // Gossip metrics
    private final List<Integer> gossipHops = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> gossipPropagationTimes = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> gossipStartTimes = new ConcurrentHashMap<>();

    // Node accuracy
    private final Map<Integer, Integer> nodeCorrectVotes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> nodeTotalVotes = new ConcurrentHashMap<>();

    // Mutex metrics
    private int mutexAcquisitions = 0;
    private final List<Long> mutexWaitTimes = Collections.synchronizedList(new ArrayList<>());

    // System
    private long systemStartTime = System.currentTimeMillis();

    private MetricsCollector() {}

    /**
     * Retrieves the singleton instance.
     * @return The global MetricsCollector.
     */
    public static synchronized MetricsCollector getInstance() {
        if (instance == null) {
            instance = new MetricsCollector();
        }
        return instance;
    }

    // ==================================================================================
    // CONSENSUS TRACKING
    // ==================================================================================

    /**
     * Marks the start of a consensus process for an IoC.
     * @param iocId The IoC ID.
     */
    public void recordConsensusStart(String iocId) {
        consensusStartTimes.put(iocId, System.currentTimeMillis());
    }

    /**
     * Marks the end of a consensus process and records the duration.
     * @param iocId The IoC ID.
     */
    public void recordConsensusEnd(String iocId) {
        Long startTime = consensusStartTimes.remove(iocId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            consensusTimes.add(duration);
        }
    }

    // ==================================================================================
    // GOSSIP TRACKING
    // ==================================================================================

    public void recordGossipStart(String iocId) {
        gossipStartTimes.put(iocId, System.currentTimeMillis());
    }

    public void recordGossipPropagation(String iocId, int hops) {
        Long startTime = gossipStartTimes.remove(iocId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            gossipPropagationTimes.add(duration);
            gossipHops.add(hops);
        }
    }

    // ==================================================================================
    // NODE ACCURACY
    // ==================================================================================

    public void recordNodeVote(int nodeId, boolean correct) {
        nodeCorrectVotes.merge(nodeId, correct ? 1 : 0, Integer::sum);
        nodeTotalVotes.merge(nodeId, 1, Integer::sum);
    }

    // ==================================================================================
    // MUTEX TRACKING
    // ==================================================================================

    public synchronized void recordMutexAcquisition(long waitTimeMs) {
        mutexAcquisitions++;
        mutexWaitTimes.add(waitTimeMs);
    }

    // ==================================================================================
    // AGGREGATION
    // ==================================================================================

    /**
     * Compiles all raw data into a structured SystemMetrics object.
     * Used by the WebBridge to serve the /api/metrics endpoint.
     *
     * @return A snapshot of system performance metrics.
     */
    public SystemMetrics getAggregatedMetrics() {
        SystemMetrics metrics = new SystemMetrics();

        // Total IoCs
        metrics.setTotalIoCs(consensusTimes.size());
        metrics.setConsensusCount(consensusTimes.size());

        // Consensus times
        if (!consensusTimes.isEmpty()) {
            double avg = consensusTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;
            double min = consensusTimes.stream().mapToLong(Long::longValue).min().orElse(0) / 1000.0;
            double max = consensusTimes.stream().mapToLong(Long::longValue).max().orElse(0) / 1000.0;

            metrics.setConsensusAvgTime(avg);
            metrics.setConsensusMinTime(min);
            metrics.setConsensusMaxTime(max);
        }

        // Gossip metrics
        metrics.setGossipCount(gossipHops.size());
        if (!gossipHops.isEmpty()) {
            double avgHops = gossipHops.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            metrics.setGossipAvgHops(avgHops);
        }
        if (!gossipPropagationTimes.isEmpty()) {
            double avgTime = gossipPropagationTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;
            metrics.setGossipAvgPropagationTime(avgTime);
        }

        // Node accuracy
        Map<Integer, Double> accuracy = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : nodeTotalVotes.entrySet()) {
            int nodeId = entry.getKey();
            int total = entry.getValue();
            int correct = nodeCorrectVotes.getOrDefault(nodeId, 0);
            accuracy.put(nodeId, (total > 0) ? (correct * 100.0 / total) : 0.0);
        }
        metrics.setNodeAccuracy(accuracy);

        // Mutex metrics
        metrics.setMutexAcquisitions(mutexAcquisitions);
        if (!mutexWaitTimes.isEmpty()) {
            double avgWait = mutexWaitTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;
            metrics.setMutexAvgWaitTime(avgWait);
        }

        metrics.setSystemUptime(System.currentTimeMillis() - systemStartTime);

        return metrics;
    }

    public synchronized void reset() {
        consensusTimes.clear();
        consensusStartTimes.clear();
        gossipHops.clear();
        gossipPropagationTimes.clear();
        gossipStartTimes.clear();
        nodeCorrectVotes.clear();
        nodeTotalVotes.clear();
        mutexAcquisitions = 0;
        mutexWaitTimes.clear();
        systemStartTime = System.currentTimeMillis();
    }
}