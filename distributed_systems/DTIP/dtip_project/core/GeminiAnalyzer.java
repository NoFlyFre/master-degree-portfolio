package core;

import model.IoC;
import model.NodeReputation;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ThreatAnalyzer} using Google's Gemini Pro API.
 * <p>
 * This analyzer constructs a prompt describing the IoC and asks the LLM to assign
 * a threat score (0-100). It is used as a fallback when local heuristics are uncertain.
 * <p>
 * <b>Configuration:</b> Requires {@code GEMINI_API_KEY} environment variable.
 */
public class GeminiAnalyzer implements ThreatAnalyzer {

    /** Google Gemini API Endpoint. */
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    
    /** Connection timeout in milliseconds. */
    private static final int TIMEOUT_MS = 10000;

    /** Shared cache for demo mode (simulates a shared threat intel database). */
    private static final ConcurrentHashMap<String, Integer> scoreCache = new ConcurrentHashMap<>();
    
    /** Toggle for demo/production mode. */
    private static final boolean DEMO_MODE = true;

    /** The API Key. */
    private final String apiKey;
    
    /** Availability status. */
    private boolean available = true;

    /**
     * Default constructor. Reads API key from environment.
     */
    public GeminiAnalyzer() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isEmpty()) {
            key = System.getProperty("gemini.api.key", "");
        }
        this.apiKey = key;
        this.available = !apiKey.isEmpty();
    }

    /**
     * Constructor with explicit API key.
     * @param apiKey The Google Cloud API Key.
     */
    public GeminiAnalyzer(String apiKey) {
        this.apiKey = apiKey;
        this.available = apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getName() {
        return "GeminiAnalyzer";
    }

    @Override
    public boolean isAvailable() {
        return available && !apiKey.isEmpty();
    }

    @Override
    public int analyze(IoC ioc, Map<Integer, NodeReputation> reputations) {
        if (!isAvailable()) {
            return 50; // Neutral fallback
        }

        String cacheKey = ioc.getType() + ":" + ioc.getValue();

        if (DEMO_MODE && scoreCache.containsKey(cacheKey)) {
            int cachedScore = scoreCache.get(cacheKey);
            System.out.println("[GeminiAnalyzer] 📦 Using cached score: " + cachedScore);
            return cachedScore;
        }

        try {
            String prompt = buildPrompt(ioc);
            String response = callGeminiAPI(prompt);
            int score = parseScore(response);

            System.out.println("[GeminiAnalyzer] 🤖 API Score: " + score);

            if (DEMO_MODE) {
                scoreCache.put(cacheKey, score);
            }

            return score;

        } catch (Exception e) {
            System.out.println("[GeminiAnalyzer] ❌ API call failed: " + e.getMessage());
            // Don't disable permanently on transient errors
            return 50;
        }
    }

    /**
     * Constructs the LLM prompt.
     */
    private String buildPrompt(IoC ioc) {
        return String.format(
                "You are a cybersecurity threat analyst. Analyze this IoC and return ONLY a score (0-100).\n" +
                        "IoC: %s (Type: %s)\n" +
                        "Context: Confidence %d%%, Tags: %s\n" +
                        "0=Safe, 100=Critical Malware. Respond with integer only.",
                ioc.getValue(),
                ioc.getType(),
                ioc.getConfidence(),
                ioc.getTags() != null ? String.join(", ", ioc.getTags()) : "none");
    }

    /**
     * Executes the HTTP request to Gemini API.
     */
    private String callGeminiAPI(String prompt) throws Exception {
        URL url = new URL(API_URL + "?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        String jsonBody = String.format(
                "{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}]",
                escapeJson(prompt));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("API status " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) response.append(line);
        }

        return response.toString();
    }

    /**
     * Parses the integer score from the JSON response.
     */
    private int parseScore(String jsonResponse) {
        try {
            // Extract "text" field from JSON manually
            int textIdx = jsonResponse.indexOf("\"text\":");
            if (textIdx == -1) return 50;

            int startQuote = jsonResponse.indexOf("\"", textIdx + 7);
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
            String text = jsonResponse.substring(startQuote + 1, endQuote).trim();

            String numStr = text.replaceAll("[^0-9]", "");
            return numStr.isEmpty() ? 50 : Math.max(0, Math.min(100, Integer.parseInt(numStr)));
        } catch (Exception e) {
            return 50;
        }
    }

    /**
     * Escapes string for JSON inclusion.
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\\n", "\\\\n");
    }
}
