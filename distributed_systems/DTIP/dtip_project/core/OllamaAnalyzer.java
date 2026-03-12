package core;

import model.IoC;
import model.NodeReputation;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ThreatAnalyzer} using a local LLM via Ollama.
 * <p>
 * This allows for private, offline, and cost-free threat analysis.
 * <p>
 * <b>Requirements:</b>
 * <ul>
 *   <li>Ollama installed and running (`ollama serve`).</li>
 *   <li>Target model pulled (e.g., `ollama pull llama3:8b`).</li>
 * </ul>
 */
public class OllamaAnalyzer implements ThreatAnalyzer {

    private static final String DEFAULT_URL = "http://localhost:11434/api/generate";
    private static final String DEFAULT_MODEL = "llama3:8b";
    private static final int TIMEOUT_MS = 30000;

    private static final ConcurrentHashMap<String, Integer> scoreCache = new ConcurrentHashMap<>();
    private static final boolean DEMO_MODE = true;

    private final String ollamaUrl;
    private final String model;
    private boolean available = true;

    public OllamaAnalyzer() {
        this.ollamaUrl = System.getenv("OLLAMA_URL") != null ? System.getenv("OLLAMA_URL") : DEFAULT_URL;
        this.model = System.getenv("OLLAMA_MODEL") != null ? System.getenv("OLLAMA_MODEL") : DEFAULT_MODEL;
        checkAvailability();
    }

    public OllamaAnalyzer(String model) {
        this.ollamaUrl = DEFAULT_URL;
        this.model = model;
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            // Check Ollama API health
            URL url = URI.create("http://localhost:11434/api/tags").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            this.available = (conn.getResponseCode() == 200);
            if (available) {
                System.out.println("[OllamaAnalyzer] ✅ Connected to Ollama (" + model + ")");
            }
        } catch (Exception e) {
            this.available = false;
            System.out.println("[OllamaAnalyzer] ⚠️ Ollama not available: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "OllamaAnalyzer (" + model + ")";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int analyze(IoC ioc, Map<Integer, NodeReputation> reputations) {
        if (!isAvailable()) return 50;

        String cacheKey = ioc.getType() + ":" + ioc.getValue();
        if (DEMO_MODE && scoreCache.containsKey(cacheKey)) {
            return scoreCache.get(cacheKey);
        }

        try {
            String prompt = buildPrompt(ioc);
            String response = callOllama(prompt);
            int score = parseScore(response);

            System.out.println("[OllamaAnalyzer] 🦙 Score: " + score);
            if (DEMO_MODE) scoreCache.put(cacheKey, score);
            return score;

        } catch (Exception e) {
            System.out.println("[OllamaAnalyzer] ❌ Failed: " + e.getMessage());
            return 50;
        }
    }

    /**
     * Constructs the LLM prompt.
     */
    private String buildPrompt(IoC ioc) {
        return String.format(
                "Analyze IoC: %s (Type: %s). Return threat score 0-100. 0=Safe, 100=Critical. Return integer only.",
                ioc.getValue(), ioc.getType());
    }

    /**
     * Executes HTTP POST to Ollama.
     */
    private String callOllama(String prompt) throws Exception {
        URL url = URI.create(ollamaUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);

        String jsonBody = String.format("{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
                model, escapeJson(prompt));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
        }

        if (conn.getResponseCode() != 200) throw new Exception("Ollama Error " + conn.getResponseCode());

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) response.append(line);
        }
        return response.toString();
    }

    /**
     * Parses the score from the Ollama response.
     */
    private int parseScore(String jsonResponse) {
        try {
            int respIdx = jsonResponse.indexOf("\"response\":");
            if (respIdx == -1) return 50;
            int startQuote = jsonResponse.indexOf("\"", respIdx + 11);
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
            String text = jsonResponse.substring(startQuote + 1, endQuote).trim();
            String numStr = text.replaceAll("[^0-9]", "");
            return numStr.isEmpty() ? 50 : Math.max(0, Math.min(100, Integer.parseInt(numStr)));
        } catch (Exception e) { return 50; }
    }

    /**
     * Escapes string for JSON inclusion.
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\\n", "\\\\n");
    }
}