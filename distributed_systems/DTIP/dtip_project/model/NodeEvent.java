package model;

import java.io.Serializable;

/**
 * Represents a loggable system event.
 * <p>
 * Used to transport logs from the Core backend to the TUI dashboard via the WebBridge.
 * Allows the dashboard to visualize algorithms like Gossip and Consensus in real-time.
 */
public class NodeEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private long timestamp;    // Epoch time
    private String type;       // Event category: "PUBLISH", "VOTE", "MUTEX", etc.
    private String detail;     // Human-readable description
    private String iocId;      // Associated IoC ID (optional)

    public NodeEvent(long timestamp, String type, String detail) {
        this(timestamp, type, detail, null);
    }

    public NodeEvent(long timestamp, String type, String detail, String iocId) {
        this.timestamp = timestamp;
        this.type = type;
        this.detail = detail;
        this.iocId = iocId;
    }

    // Getters
    public long getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getDetail() { return detail; }
    public String getIocId() { return iocId; }

    public void setIocId(String iocId) { this.iocId = iocId; }

    @Override
    public String toString() {
        return String.format("[%d] %s: %s", timestamp, type, detail);
    }
}