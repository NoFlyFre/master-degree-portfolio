package core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import model.IoC;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Unit tests for DTIPNode class.
 * Tests threat scoring and node behavior.
 *
 * Network configuration: 5 nodes (Banca, Retail, Energia, Sanita, Trasporti)
 */
public class DTIPNodeTest {

    private static final int TOTAL_NODES = 5;
    private static final String[] NODE_NAMES = {"Banca", "Retail", "Energia", "Sanita", "Trasporti"};

    // ========================================
    // Threat Score Tests
    // ========================================

    @Test
    public void testComputeThreatScore_HighConfidenceHash() throws Exception {
        DTIPNode node = createNode(0);
        IoC ioc = new IoC(IoC.IoCType.HASH, "abc123", 90,
                Arrays.asList("ransomware", "lockbit"), 0, "Node0", TOTAL_NODES);

        int score = invokeComputeThreatScore(node, ioc);

        assertTrue(score >= 80, "High-confidence ransomware hash should have score >= 80, got: " + score);
    }

    @Test
    public void testComputeThreatScore_LowConfidenceIP() throws Exception {
        DTIPNode node = createNode(0);
        IoC ioc = new IoC(IoC.IoCType.IP, "8.8.8.8", 20, null, 0, "Node0", TOTAL_NODES);

        int score = invokeComputeThreatScore(node, ioc);

        assertTrue(score < 50, "Low-confidence IP should have low score, got: " + score);
    }

    @Test
    public void testComputeThreatScore_SuspiciousDomain() throws Exception {
        DTIPNode node = createNode(0);
        IoC ioc = new IoC(IoC.IoCType.DOMAIN, "malicious-phish-bank.com", 70,
                Arrays.asList("c2"), 0, "Node0", TOTAL_NODES);

        int score = invokeComputeThreatScore(node, ioc);

        assertTrue(score >= 70, "Suspicious phishing domain should have high score, got: " + score);
    }

    // ========================================
    // Node Creation Tests
    // ========================================

    @Test
    public void testNodeCreation() throws Exception {
        DTIPNode node = createNode(0);
        assertEquals(0, node.getId(), "Node ID should match constructor argument");
    }

    @Test
    public void testVectorClockInitialization() throws Exception {
        DTIPNode node = createNode(2);
        int[] vc = node.getVectorClockArray();

        assertNotNull(vc, "Vector clock should not be null");
        assertEquals(TOTAL_NODES, vc.length, "Vector clock should have 5 components");

        for (int i = 0; i < vc.length; i++) {
            assertEquals(0, vc[i], "Vector clock component " + i + " should be 0 initially");
        }
    }

    @Test
    public void testMultipleNodesCreation() throws Exception {
        DTIPNode[] nodes = new DTIPNode[TOTAL_NODES];
        for (int i = 0; i < TOTAL_NODES; i++) {
            nodes[i] = createNode(i);
            assertEquals(i, nodes[i].getId());
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private DTIPNode createNode(int nodeId) throws Exception {
        String name = nodeId < NODE_NAMES.length ? NODE_NAMES[nodeId] : "Node" + nodeId;
        return new DTIPNode(nodeId, name, TOTAL_NODES);
    }

    private int invokeComputeThreatScore(DTIPNode node, IoC ioc) throws Exception {
        Method method = DTIPNode.class.getDeclaredMethod("computeThreatScore", IoC.class);
        method.setAccessible(true);
        return (int) method.invoke(node, ioc);
    }
}
