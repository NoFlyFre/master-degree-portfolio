package model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Metriche aggregate del sistema DTIP.
 * Usato per dashboard MetricsPanel per dimostrare correttezza algoritmi.
 */
public class SystemMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    // Consensus metrics
    private int totalIoCs;
    private double consensusAvgTime;  // secondi
    private double consensusMinTime;
    private double consensusMaxTime;
    private int consensusCount;

    // Gossip metrics
    private double gossipAvgHops;
    private double gossipAvgPropagationTime; // secondi
    private int gossipCount;

    // Node accuracy (nodeId → accuracy percentage)
    private Map<Integer, Double> nodeAccuracy;

    // System health
    private long systemUptime; // millisecondi
    private int mutexAcquisitions;
    private double mutexAvgWaitTime; // secondi

    // Constructor
    public SystemMetrics() {
        this.totalIoCs = 0;
        this.consensusAvgTime = 0.0;
        this.consensusMinTime = Double.MAX_VALUE;
        this.consensusMaxTime = 0.0;
        this.consensusCount = 0;
        this.gossipAvgHops = 0.0;
        this.gossipAvgPropagationTime = 0.0;
        this.gossipCount = 0;
        this.nodeAccuracy = new HashMap<>();
        this.systemUptime = 0;
        this.mutexAcquisitions = 0;
        this.mutexAvgWaitTime = 0.0;
    }

    // Getters
    public int getTotalIoCs() {
        return totalIoCs;
    }

    public double getConsensusAvgTime() {
        return consensusAvgTime;
    }

    public double getConsensusMinTime() {
        return consensusMinTime == Double.MAX_VALUE ? 0.0 : consensusMinTime;
    }

    public double getConsensusMaxTime() {
        return consensusMaxTime;
    }

    public int getConsensusCount() {
        return consensusCount;
    }

    public double getGossipAvgHops() {
        return gossipAvgHops;
    }

    public double getGossipAvgPropagationTime() {
        return gossipAvgPropagationTime;
    }

    public int getGossipCount() {
        return gossipCount;
    }

    public Map<Integer, Double> getNodeAccuracy() {
        return new HashMap<>(nodeAccuracy);
    }

    public long getSystemUptime() {
        return systemUptime;
    }

    public int getMutexAcquisitions() {
        return mutexAcquisitions;
    }

    public double getMutexAvgWaitTime() {
        return mutexAvgWaitTime;
    }

    // Setters
    public void setTotalIoCs(int totalIoCs) {
        this.totalIoCs = totalIoCs;
    }

    public void setConsensusAvgTime(double consensusAvgTime) {
        this.consensusAvgTime = consensusAvgTime;
    }

    public void setConsensusMinTime(double consensusMinTime) {
        this.consensusMinTime = consensusMinTime;
    }

    public void setConsensusMaxTime(double consensusMaxTime) {
        this.consensusMaxTime = consensusMaxTime;
    }

    public void setConsensusCount(int consensusCount) {
        this.consensusCount = consensusCount;
    }

    public void setGossipAvgHops(double gossipAvgHops) {
        this.gossipAvgHops = gossipAvgHops;
    }

    public void setGossipAvgPropagationTime(double gossipAvgPropagationTime) {
        this.gossipAvgPropagationTime = gossipAvgPropagationTime;
    }

    public void setGossipCount(int gossipCount) {
        this.gossipCount = gossipCount;
    }

    public void setNodeAccuracy(Map<Integer, Double> nodeAccuracy) {
        this.nodeAccuracy = new HashMap<>(nodeAccuracy);
    }

    public void setSystemUptime(long systemUptime) {
        this.systemUptime = systemUptime;
    }

    public void setMutexAcquisitions(int mutexAcquisitions) {
        this.mutexAcquisitions = mutexAcquisitions;
    }

    public void setMutexAvgWaitTime(double mutexAvgWaitTime) {
        this.mutexAvgWaitTime = mutexAvgWaitTime;
    }

    @Override
    public String toString() {
        return String.format("SystemMetrics{IoCs=%d, consensusAvg=%.2fs, gossipAvg=%.2f hops}",
                totalIoCs, consensusAvgTime, gossipAvgHops);
    }
}
