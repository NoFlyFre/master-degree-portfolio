package core;

import model.IoC;
import model.NodeReputation;
import util.ThreatIntelAPI;
import java.util.List;
import java.util.Map;

/**
 * A fast, rule-based implementation of {@link ThreatAnalyzer}.
 * <p>
 * This analyzer calculates a threat score based on static properties of the IoC:
 * <ul>
 *   <li><b>Type Confidence:</b> Base score derived from the reporter's confidence level.</li>
 *   <li><b>Keyword Matching:</b> Scans domains/URLs for suspicious words (e.g., "login", "bank").</li>
 *   <li><b>Threat Intel:</b> Checks IPs against known malicious ranges or external APIs (AbuseIPDB).</li>
 *   <li><b>Reputation:</b> Adjusts score based on the reliability of the publisher node.</li>
 * </ul>
 */
public class HeuristicAnalyzer implements ThreatAnalyzer {

    /** Known safe domains (major tech/services) to reduce false positives. */
    private static final String[] SAFE_DOMAINS = {
            "google.com", "microsoft.com", "apple.com", "amazon.com", "github.com",
            "cloudflare.com", "akamai.com", "fastly.com", "amazonaws.com", "azure.com",
            "facebook.com", "twitter.com", "linkedin.com", "youtube.com", "netflix.com"
    };

    /** Known safe IPs (public DNS, CDNs) to whitelist. */
    private static final String[] SAFE_IPS = {
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9",
            "208.67.222.222", "208.67.220.220"
    };

    /** High-threat keywords in domains that indicate malware/phishing. */
    private static final String[] HIGH_THREAT_KEYWORDS = {
            "malware", "phishing", "evil", "hack", "trojan", "virus", "ransomware",
            "exploit", "payload", "c2", "botnet", "keylog", "stealer", "cryptolocker",
            "wannacry", "emotet", "trickbot"
    };

    /** Keywords commonly used in phishing campaigns. */
    private static final String[] PHISHING_KEYWORDS = {
            "bank", "login", "secure", "account", "verify", "update", "confirm",
            "paypal", "amazon", "microsoft", "apple"
    };

    /** Suspicious Top-Level Domains (TLDs) often used by attackers. */
    private static final String[] SUSPICIOUS_TLDS = {
            ".xyz", ".top", ".club", ".work", ".click", ".loan",
            ".tk", ".ml", ".ga", ".cf", ".gq", ".buzz", ".icu"
    };

    /** Known malicious IP ranges (simulated for demo). */
    private static final String[] MALICIOUS_RANGES = {
            "45.33.", "185.220.", "89.248.", "193.169."
    };

    /**
     * Default constructor.
     */
    public HeuristicAnalyzer() {
        // No initialization needed for stateless heuristics
    }

    @Override
    public String getName() {
        return "HeuristicAnalyzer";
    }

    /**
     * Calculates the heuristic threat score.
     *
     * @param ioc The IoC to analyze.
     * @param reputations Map of node reputations.
     * @return Score between 0 and 100.
     */
    @Override
    public int analyze(IoC ioc, Map<Integer, NodeReputation> reputations) {
        int score = 0;
        String value = ioc.getValue().toLowerCase();

        // 1. CONFIDENCE-BASED SCORING
        score += scoreByConfidence(ioc.getConfidence());

        // 2. TYPE-BASED SCORING
        score += scoreByType(ioc, value);

        // 3. TAG-BASED SCORING
        score += scoreByTags(ioc.getTags());

        // 4. PUBLISHER REPUTATION
        score += scoreByReputation(ioc.getPublisherId(), reputations);

        // 5. THREAT INTEL API (IPs only)
        if (ioc.getType() == IoC.IoCType.IP) {
            score += scoreByThreatIntel(ioc.getValue());
        }

        return Math.max(0, Math.min(100, score)); // Clamp to 0-100
    }

    // ========== SCORING LOGIC ==========

    /** Adjusts score based on publisher's confidence level. */
    private int scoreByConfidence(int confidence) {
        if (confidence >= 90) return 45;
        if (confidence >= 70) return 30;
        if (confidence >= 50) return 15;
        if (confidence >= 30) return 0;
        return -20; // Low confidence implies uncertainty/unreliability
    }

    /** Adjusts score based on IoC type and lexical analysis. */
    private int scoreByType(IoC ioc, String value) {
        int score = 0;
        switch (ioc.getType()) {
            case HASH:
                score += 25; // Hashes are specific, high-fidelity indicators
                break;
            case DOMAIN:
                score += 15;
                score += scoreDomain(value);
                break;
            case IP:
                score += 10;
                score += scoreIP(value);
                break;
            default:
                score += 10;
                break;
        }
        return score;
    }

    /** Domain-specific scoring logic. */
    private int scoreDomain(String domain) {
        int score = 0;
        // Check for high-threat keywords
        for (String kw : HIGH_THREAT_KEYWORDS) {
            if (domain.contains(kw)) { score += 50; break; }
        }
        // Check for phishing keywords
        if (!isKnownSafeDomain(domain)) {
            for (String kw : PHISHING_KEYWORDS) {
                if (domain.contains(kw)) { score += 25; break; }
            }
        }
        // Check TLD
        for (String tld : SUSPICIOUS_TLDS) {
            if (domain.endsWith(tld)) { score += 20; break; }
        }
        // Safe domain whitelist
        if (isKnownSafeDomain(domain)) {
            score -= 60;
        }
        return score;
    }

    /** IP-specific scoring logic. */
    private int scoreIP(String ip) {
        int score = 0;
        // Private IPs
        if (isPrivateIP(ip)) { score -= 50; }
        // Localhost
        if (ip.startsWith("127.") || ip.equals("::1") || ip.equals("0.0.0.0")) { score -= 60; }
        // Safe IPs
        for (String safe : SAFE_IPS) {
            if (ip.equals(safe)) { score -= 50; break; }
        }
        // Malicious ranges
        for (String range : MALICIOUS_RANGES) {
            if (ip.startsWith(range)) { score += 35; break; }
        }
        return score;
    }

    /** Tag-based scoring logic. */
    private int scoreByTags(List<String> tags) {
        if (tags == null) return 0;
        int score = 0;
        if (tags.contains("ransomware")) score += 25;
        if (tags.contains("c2")) score += 20;
        if (tags.contains("dropper")) score += 20;
        if (tags.contains("apt")) score += 25;
        if (tags.contains("lockbit") || tags.contains("revil")) score += 30;
        
        if (tags.contains("suspicious")) score -= 10;
        if (tags.contains("dns") || tags.contains("cdn")) score -= 25;
        if (tags.contains("whitelist") || tags.contains("safe")) score -= 40;
        return score;
    }

    /** Reputation-based adjustment. */
    private int scoreByReputation(int publisherId, Map<Integer, NodeReputation> reputations) {
        if (reputations == null) return 0;
        NodeReputation rep = reputations.get(publisherId);
        if (rep == null) return 0;

        double accuracy = rep.getAccuracyRate();
        if (accuracy > 0.8) return 20;
        if (accuracy < 0.3) return -15;
        return 0;
    }

    /** Checks external threat intel (AbuseIPDB stub). */
    private int scoreByThreatIntel(String ip) {
        try {
            ThreatIntelAPI.ThreatResult result = ThreatIntelAPI.checkIPOffline(ip);
            if (result.abuseScore >= 70) return 35;
            if (result.abuseScore <= 20) return -25;
        } catch (Exception e) {}
        return 0;
    }

    // Helpers
    
    /** Checks if domain is in whitelist. */
    private boolean isKnownSafeDomain(String domain) {
        for (String safe : SAFE_DOMAINS) {
            if (domain.equals(safe) || domain.endsWith("." + safe)) return true;
        }
        return false;
    }

    /** Checks if IP is private/local. */
    private boolean isPrivateIP(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.");
    }
}