package model;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) for basic node metadata.
 * Used for lightweight discovery and listing operations.
 */
public class NodeInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private int nodeId;
    private String nodeName;
    private int iocCount;
    private int peerCount;

    public NodeInfo(int nodeId, String nodeName, int iocCount, int peerCount) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.iocCount = iocCount;
        this.peerCount = peerCount;
    }

    public int getNodeId() { return nodeId; }
    public String getNodeName() { return nodeName; }
    public int getIocCount() { return iocCount; }
    public int getPeerCount() { return peerCount; }
}