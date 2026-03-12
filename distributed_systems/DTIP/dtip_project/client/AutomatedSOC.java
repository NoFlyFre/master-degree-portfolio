package client;

import interfaces.DTIPNodeInterface;
import model.IoC;

import core.CompositeAnalyzer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutomatedSOC acts as an "External" Security Operations Center.
 * <p>
 * It monitors the network for IoCs in the {@link model.IoC.IoCStatus#AWAITING_SOC} state (ties)
 * and resolves them by triggering an LLM analysis and injecting a deciding vote.
 * <p>
 * This service runs independently of the nodes (in the WebBridge process),
 * ensuring that SOC intervention works even if specific nodes (like Node 0) are
 * offline.
 */
public class AutomatedSOC implements Runnable {

    /** Map of all nodes in the network (for polling). */
    private final Map<Integer, DTIPNodeInterface> nodes;
    
    /** Set of already processed IoCs to avoid redundant votes. */
    private final Set<String> processedIoCS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    /** The analyzer engine (Composite: Heuristic + LLM) used for decision making. */
    private final CompositeAnalyzer analyzer;
    
    /** Control flag for the service loop. */
    private boolean running = true;

    /**
     * Initializes the Automated SOC service.
     * 
     * @param nodes A map of connected nodes to monitor.
     */
    public AutomatedSOC(Map<Integer, DTIPNodeInterface> nodes) {
        this.nodes = nodes;
        this.analyzer = new CompositeAnalyzer(); // Uses system properties/env for keys
    }

    /**
     * Stops the SOC service loop.
     */
    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        System.out.println("🤖 Automated SOC Service Started (External)");

        while (running) {
            try {
                checkForConflicts();
                Thread.sleep(2000); // Check every 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("⚠️ Automated SOC Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Scans all nodes for IoCs that are stuck in AWAITING_SOC state.
     */
    private void checkForConflicts() {
        // Collect all IoCs from all reachable nodes
        Map<String, IoC> candidates = new HashMap<>();

        for (DTIPNodeInterface node : nodes.values()) {
            try {
                List<IoC> iocs = node.getAllIoCs();
                for (IoC ioc : iocs) {
                    if (ioc.getStatus() == IoC.IoCStatus.AWAITING_SOC && !processedIoCS.contains(ioc.getId())) {
                        candidates.put(ioc.getId(), ioc);
                    }
                }
            } catch (Exception e) {
                // Node might be offline, ignore
            }
        }

        // Process found candidates
        for (IoC ioc : candidates.values()) {
            resolveConflict(ioc);
        }
    }

    /**
     * Resolves a tie by running an independent analysis and casting a decisive vote.
     * 
     * @param ioc The IoC to arbitrate.
     */
    private void resolveConflict(IoC ioc) {
        System.out.println("🤖 SOC: Resolving conflict for IoC " + ioc.getId() + " (" + ioc.getValue() + ")...");
        processedIoCS.add(ioc.getId());

        // Perform Analysis (LLM)
        // We pass empty reputations map as we want pure LLM/Heuristic analysis on the
        // artifact itself
        // independent of internal node reputations.
        int score = analyzer.forceAnalyzeWithLLM(ioc, new HashMap<>());

        IoC.VoteType decision = score >= 50 ? IoC.VoteType.CONFIRM : IoC.VoteType.REJECT;
        System.out.println("🤖 SOC: Analysis Score = " + score + " -> Verdict: " + decision);

        // Broadcast the SOC vote to ALL accessible nodes to ensure propagation
        // casting vote as Node -1 (SOC)
        for (Map.Entry<Integer, DTIPNodeInterface> entry : nodes.entrySet()) {
            try {
                entry.getValue().receiveVote(ioc.getId(), -1, decision);
            } catch (Exception e) {
                // Node offline, skip
            }
        }
        System.out.println("🤖 SOC: Vote broadcasted.");
    }
}