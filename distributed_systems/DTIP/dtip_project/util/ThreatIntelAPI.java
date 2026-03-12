package util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class for interfacing with external Threat Intelligence APIs.
 * <p>
 * Specifically designed to query AbuseIPDB for IP reputation scores.
 * Includes a fallback offline mode for demonstration purposes when no API key is available.
 */
public class ThreatIntelAPI {

    private static String API_KEY = System.getenv("ABUSEIPDB_KEY");

    /**
     * Queries AbuseIPDB for the reputation of a specific IP address.
     *
     * @param ip The IP address to check.
     * @return A ThreatResult object containing the abuse score and details.
     */
    public static ThreatResult checkIP(String ip) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            return new ThreatResult(ip, -1, "API key not configured", false);
        }

        try {
            String urlStr = "https://api.abuseipdb.com/api/v2/check?ipAddress=" + ip + "&maxAgeInDays=90";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Key", API_KEY);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status != 200) {
                return new ThreatResult(ip, -1, "API error: " + status, false);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            // Simple JSON parsing
            String json = response.toString();
            int abuseScore = extractInt(json, "abuseConfidenceScore");
            int totalReports = extractInt(json, "totalReports");
            String isp = extractString(json, "isp");
            String country = extractString(json, "countryCode");

            String details = String.format("ISP: %s | Country: %s | Reports: %d", isp, country, totalReports);

            return new ThreatResult(ip, abuseScore, details, true);

        } catch (Exception e) {
            return new ThreatResult(ip, -1, "Check failed: " + e.getMessage(), false);
        }
    }

    /**
     * Offline mock implementation for demonstration environments.
     * Simulates threat intelligence lookup based on predefined IP patterns.
     *
     * @param ip The IP address to check.
     * @return A simulated ThreatResult.
     */
    public static ThreatResult checkIPOffline(String ip) {
        // Known malicious IPs (simulated)
        if (ip.startsWith("185.220.") || ip.startsWith("45.33.") || ip.startsWith("198.51.")) {
            return new ThreatResult(ip, 95, "Known C2/Tor exit node", true);
        }
        // Known benign IPs
        if (ip.equals("8.8.8.8") || ip.equals("1.1.1.1") || ip.startsWith("192.168.")) {
            return new ThreatResult(ip, 0, "Known benign (DNS/Private)", true);
        }
        // Unknown
        return new ThreatResult(ip, 50, "Unknown - manual verification needed", true);
    }

    // Helper for manual JSON parsing (avoids huge deps like Jackson/Gson)
    private static int extractInt(String json, String field) {
        try {
            String search = "\"" + field + "\":";
            int idx = json.indexOf(search);
            if (idx < 0) return 0;
            int start = idx + search.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) { return 0; }
    }

    private static String extractString(String json, String field) {
        try {
            String search = "\"" + field + "\":\"";
            int idx = json.indexOf(search);
            if (idx < 0) return "N/A";
            int start = idx + search.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) { return "N/A"; }
    }

    /**
     * Encapsulates the result of a Threat Intelligence lookup.
     */
    public static class ThreatResult {
        public final String ip;
        public final int abuseScore; // 0-100, -1 = error
        public final String details;
        public final boolean success;

        public ThreatResult(String ip, int score, String details, boolean success) {
            this.ip = ip;
            this.abuseScore = score;
            this.details = details;
            this.success = success;
        }

        /**
         * Returns a suggested action based on the abuse score.
         */
        public String getSuggestion() {
            if (abuseScore < 0) return "UNKNOWN";
            if (abuseScore >= 70) return "CONFIRM (High threat)";
            if (abuseScore >= 30) return "MANUAL REVIEW";
            return "REJECT (Low threat / False positive)";
        }

        public String getIcon() {
            if (abuseScore < 0) return "❓";
            if (abuseScore >= 70) return "🔴";
            if (abuseScore >= 30) return "🟡";
            return "🟢";
        }
    }
}