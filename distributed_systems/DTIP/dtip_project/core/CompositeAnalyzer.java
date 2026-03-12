package core;

import model.IoC;
import model.NodeReputation;
import java.util.Map;

/**
 * A composite implementation of {@link ThreatAnalyzer} that combines multiple strategies.
 * <p>
 * This class implements a <b>Tiered Analysis Strategy</b>:
 * <ol>
 *   <li><b>Tier 1 (Fast path):</b> Uses {@link HeuristicAnalyzer} for rapid, low-cost evaluation.</li>
 *   <li><b>Tier 2 (Deep analysis):</b> If the heuristic score is "uncertain" (40-60), it falls back to an LLM.</li>
 * </ol>
 * <p>
 * <b>LLM Priority:</b> Prefers a local LLM (Ollama) for privacy and zero cost. If unavailable, falls back to Google Gemini (Cloud).
 */
public class CompositeAnalyzer implements ThreatAnalyzer {

    /** The primary heuristic analyzer for fast, rule-based scoring. */
    private final HeuristicAnalyzer heuristicAnalyzer;
    
    /** The fallback LLM analyzer (either Ollama or Gemini). */
    private final ThreatAnalyzer llmAnalyzer;

    /** Lower bound of the uncertainty score range. Scores above this trigger LLM check. */
    private static final int UNCERTAIN_LOW = 40;
    
    /** Upper bound of the uncertainty score range. Scores below this trigger LLM check. */
    private static final int UNCERTAIN_HIGH = 60;

    /** Runtime flag to toggle LLM usage. True if an LLM provider is available and enabled. */
    private boolean llmEnabled;

    /**
     * Default constructor. Automatically detects available LLM providers.
     * Priority: Ollama > Gemini > None (Heuristics only).
     */
    public CompositeAnalyzer() {
        this.heuristicAnalyzer = new HeuristicAnalyzer();

        // Auto-detection logic
        OllamaAnalyzer ollama = new OllamaAnalyzer();
        if (ollama.isAvailable()) {
            this.llmAnalyzer = ollama;
            this.llmEnabled = true;
            System.out.println("[CompositeAnalyzer] 🦙 Using Ollama (local LLM)");
        } else {
            GeminiAnalyzer gemini = new GeminiAnalyzer();
            this.llmAnalyzer = gemini;
            this.llmEnabled = gemini.isAvailable();
            if (llmEnabled) {
                System.out.println("[CompositeAnalyzer] ☁️ Using Gemini (cloud LLM)");
            } else {
                System.out.println("[CompositeAnalyzer] ⚠️ No LLM available, heuristics only");
            }
        }
    }

    /**
     * Constructor forcing a specific local Ollama model.
     *
     * @param ollamaModel The name of the Ollama model to use (e.g., "llama3:8b").
     */
    public CompositeAnalyzer(String ollamaModel) {
        this.heuristicAnalyzer = new HeuristicAnalyzer();
        this.llmAnalyzer = new OllamaAnalyzer(ollamaModel);
        this.llmEnabled = llmAnalyzer.isAvailable();
    }

    @Override
    public String getName() {
        String llmName = llmEnabled ? llmAnalyzer.getName() : "None";
        return "CompositeAnalyzer (Heuristic + " + llmName + ")";
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available because heuristics are always available
    }

    /**
     * Analyzes an IoC using the tiered strategy.
     *
     * @param ioc         The IoC to analyze.
     * @param reputations Reputation map for context.
     * @return A final threat score (0-100).
     */
    @Override
    public int analyze(IoC ioc, Map<Integer, NodeReputation> reputations) {
        // Step 1: Fast heuristic analysis
        int heuristicScore = heuristicAnalyzer.analyze(ioc, reputations);

        System.out.println("[CompositeAnalyzer] Heuristic score for " + ioc.getValue() + ": " + heuristicScore);

        // Step 2: Check if score is in the "uncertainty zone"
        if (isUncertain(heuristicScore) && llmEnabled && llmAnalyzer.isAvailable()) {
            System.out.println("[CompositeAnalyzer] ⚠️ Score uncertain (" + heuristicScore +
                    "), consulting LLM...");

            try {
                int llmScore = llmAnalyzer.analyze(ioc, reputations);
                System.out.println("[CompositeAnalyzer] 🤖 LLM score: " + llmScore);

                // Weighted average: 40% heuristic, 60% LLM (LLM has more semantic context)
                int finalScore = (int) (heuristicScore * 0.4 + llmScore * 0.6);
                System.out.println("[CompositeAnalyzer] Final blended score: " + finalScore);

                return finalScore;

            } catch (Exception e) {
                System.out.println("[CompositeAnalyzer] ❌ LLM failed, using heuristic score");
            }
        }

        return heuristicScore;
    }

    /**
     * Checks if a heuristic score falls within the uncertainty range requiring LLM verification.
     * @param score The heuristic score to check.
     * @return true if uncertain.
     */
    private boolean isUncertain(int score) {
        return score >= UNCERTAIN_LOW && score <= UNCERTAIN_HIGH;
    }

    /**
     * Enable or disable LLM fallback at runtime.
     * @param enabled True to enable LLM usage if available.
     */
    public void setLlmEnabled(boolean enabled) {
        this.llmEnabled = enabled && llmAnalyzer.isAvailable();
    }

    /**
     * Checks if LLM analysis is currently enabled.
     * @return true if enabled.
     */
    public boolean isLlmEnabled() {
        return llmEnabled && llmAnalyzer.isAvailable();
    }

    /**
     * Forces an analysis using the LLM, bypassing heuristics.
     * Useful for tie-breaking scenarios where a "second opinion" is needed.
     *
     * @param ioc The IoC to analyze.
     * @param reputations Reputation context.
     * @return The score directly from the LLM.
     */
    public int forceAnalyzeWithLLM(IoC ioc, Map<Integer, NodeReputation> reputations) {
        if (llmEnabled && llmAnalyzer.isAvailable()) {
            System.out.println("[CompositeAnalyzer] 🚨 Forced LLM Analysis for Tie-Breaking...");
            return llmAnalyzer.analyze(ioc, reputations);
        }
        // Fallback if LLM unavailable
        return heuristicAnalyzer.analyze(ioc, reputations);
    }

    /**
     * Retrieves the name of the currently active LLM analyzer.
     * @return Name of the LLM strategy.
     */
    public String getLlmType() {
        return llmAnalyzer.getName();
    }
}