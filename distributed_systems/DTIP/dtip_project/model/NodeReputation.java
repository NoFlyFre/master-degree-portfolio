package model;

import java.io.Serializable;

/**
 * Tracks the reputation score and statistics of a peer node.
 * <p>
 * Reputation is used by the Heuristic Analyzer to weigh the reliability
 * of IoCs published by other nodes.
 * <p>
 * <b>Scoring Rules:</b>
 * <ul>
 *   <li>Initial Score: 10</li>
 *   <li>IoC Verified: +5 points</li>
 *   <li>IoC Rejected: -3 points</li>
 *   <li>Correct Vote: +1 point</li>
 *   <li>Incorrect Vote: -1 point</li>
 * </ul>
 */
public class NodeReputation implements Serializable {
    private static final long serialVersionUID = 1L;

    private int nodeId;
    private String nodeName;

    // Statistics
    private int iocPublished;
    private int iocVerified;
    private int iocRejected;
    private int votesCorrect;
    private int votesIncorrect;

    // Calculated score
    private int score;

    public NodeReputation(int nodeId, String nodeName) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.score = 10; // Default starting score
    }

    /**
     * Called when this node publishes a new IoC.
     */
    public void onIoCPublished() {
        iocPublished++;
    }

    /**
     * Called when an IoC published by this node is VERIFIED by the network.
     * Significant reputation boost.
     */
    public void onIoCVerified() {
        iocVerified++;
        score += 5;
    }

    /**
     * Called when an IoC published by this node is REJECTED by the network.
     * Reputation penalty for false positives.
     */
    public void onIoCRejected() {
        iocRejected++;
        score -= 3;
        if (score < 0) score = 0;
    }

    /**
     * Called when this node's vote aligns with the final consensus.
     */
    public void onCorrectVote() {
        votesCorrect++;
        score += 1;
    }

    /**
     * Called when this node's vote disagrees with the final consensus.
     */
    public void onIncorrectVote() {
        votesIncorrect++;
        score -= 1;
        if (score < 0) score = 0;
    }

    /**
     * Calculates the success rate of published IoCs.
     * @return Ratio of verified IoCs to total published (0.0 to 1.0).
     */
    public double getSuccessRate() {
        if (iocPublished == 0) return 0.0;
        return (double) iocVerified / iocPublished;
    }

    /**
     * Calculates the accuracy of voting history.
     * @return Ratio of correct votes to total votes (0.0 to 1.0).
     */
    public double getVoteAccuracy() {
        int totalVotes = votesCorrect + votesIncorrect;
        if (totalVotes == 0) return 0.0;
        return (double) votesCorrect / totalVotes;
    }

    /**
     * Computes a composite accuracy rate.
     * Used by the Threat Analyzer to determine trust weight.
     *
     * @return A weighted score (0.0 to 1.0), prioritizing publishing quality (60%).
     */
    public double getAccuracyRate() {
        double iocRate = getSuccessRate();
        double voteRate = getVoteAccuracy();

        // If no history, assume neutral trust
        if (iocPublished == 0 && (votesCorrect + votesIncorrect) == 0) {
            return 0.5;
        }

        // Weighted average: Publishing quality is more critical than voting
        return (iocRate * 0.6) + (voteRate * 0.4);
    }

    /**
     * Returns a graphical representation of the trust level.
     */
    public String getReputationLevel() {
        if (score >= 50) return "⭐⭐⭐⭐⭐ TRUSTED";
        if (score >= 30) return "⭐⭐⭐⭐ HIGH";
        if (score >= 15) return "⭐⭐⭐ MEDIUM";
        if (score >= 5)  return "⭐⭐ LOW";
        return "⭐ UNTRUSTED";
    }

    // Getters
    public int getScore() { return score; }
    public int getNodeId() { return nodeId; }
    public String getNodeName() { return nodeName; }
    public int getIocPublished() { return iocPublished; }
    public int getIocVerified() { return iocVerified; }
}