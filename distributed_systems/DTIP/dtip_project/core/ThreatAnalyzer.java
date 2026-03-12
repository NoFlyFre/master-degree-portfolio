package core;

import model.IoC;
import model.NodeReputation;
import java.util.Map;

/**
 * Interface defining the strategy for threat analysis.
 * <p>
 * Implementations of this interface (e.g., Heuristic, AI-based) encapsulate
 * different algorithms for evaluating the risk level of an Indicator of Compromise.
 * <p>
 * This allows the system to switch between or combine different analysis methods dynamically.
 */
public interface ThreatAnalyzer {

    /**
     * Analyzes an IoC and returns a threat score.
     *
     * @param ioc         The Indicator of Compromise to analyze.
     * @param reputations A map of node reputations, used to weight the reliability of the IoC's publisher.
     * @return A threat score between 0 (safe) and 100 (critical threat).
     */
    int analyze(IoC ioc, Map<Integer, NodeReputation> reputations);

    /**
     * Retrieves the human-readable name of this analyzer strategy.
     *
     * @return The analyzer name (e.g., "HeuristicAnalyzer", "OllamaAnalyzer").
     */
    String getName();

    /**
     * Checks if this analyzer is currently operational.
     * <p>
     * For example, an API-based analyzer might return false if the service is unreachable
     * or the API key is missing.
     *
     * @return true if the analyzer can perform analysis, false otherwise.
     */
    default boolean isAvailable() {
        return true;
    }
}