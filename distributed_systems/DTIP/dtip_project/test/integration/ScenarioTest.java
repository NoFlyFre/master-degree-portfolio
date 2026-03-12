package integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import core.DTIPNode;
import core.VectorClock;
import model.IoC;
import java.util.Arrays;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * Scenario-based integration tests for DTIP system.
 * Tests complete end-to-end flows without RMI (in-memory simulation).
 *
 * Network: 5 nodes (Banca, Retail, Energia, Sanita, Trasporti)
 * Quorum: 3 votes
 */
public class ScenarioTest {

    private DTIPNode[] nodes;
    private static final int TOTAL_NODES = 5;
    private static final String[] NODE_NAMES = {"Banca", "Retail", "Energia", "Sanita", "Trasporti"};

    @BeforeEach
    public void setup() throws Exception {
        nodes = new DTIPNode[TOTAL_NODES];
        for (int i = 0; i < TOTAL_NODES; i++) {
            nodes[i] = new DTIPNode(i, NODE_NAMES[i], TOTAL_NODES);
        }
    }

    // ================================================================
    // SCENARIO 1: Valid Threat -> VERIFIED
    // ================================================================

    @Test
    @DisplayName("Scenario 1: Valid ransomware -> all CONFIRM -> VERIFIED")
    public void testScenario1_ValidThreat_AllConfirm_Verified() throws Exception {
        IoC ioc = new IoC(
                IoC.IoCType.HASH,
                "a1b2c3d4e5f6ransomware",
                90,
                Arrays.asList("ransomware", "lockbit"),
                0,
                "Banca",
                TOTAL_NODES);

        assertNotNull(ioc.getId());
        assertEquals(IoC.IoCStatus.PENDING, ioc.getStatus());

        // Simulate VectorClock tick
        VectorClock vc0 = getInternalVectorClock(nodes[0]);
        vc0.tick();
        int[] vcAfter = nodes[0].getVectorClockArray();

        assertEquals(1, vcAfter[0], "VectorClock[0] should increment");

        // Simulate gossip propagation
        for (int i = 1; i < TOTAL_NODES; i++) {
            VectorClock vcNode = getInternalVectorClock(nodes[i]);
            vcNode.update(vcAfter);
        }

        // All nodes vote CONFIRM
        for (int i = 0; i < TOTAL_NODES; i++) {
            ioc.addVote(i, IoC.VoteType.CONFIRM);
        }

        assertEquals(IoC.IoCStatus.VERIFIED, ioc.getStatus(), "5 CONFIRM -> VERIFIED");
    }

    // ================================================================
    // SCENARIO 2: Suspicious IoC -> REJECTED
    // ================================================================

    @Test
    @DisplayName("Scenario 2: Low confidence IP -> REJECTED")
    public void testScenario2_SuspiciousIoC_Rejected() throws Exception {
        IoC ioc = new IoC(
                IoC.IoCType.IP,
                "8.8.8.8",
                20,
                null,
                0,
                "Banca",
                TOTAL_NODES);

        // All nodes vote REJECT
        for (int i = 0; i < TOTAL_NODES; i++) {
            ioc.addVote(i, IoC.VoteType.REJECT);
        }

        assertEquals(IoC.IoCStatus.REJECTED, ioc.getStatus(), "5 REJECT -> REJECTED");
    }

    // ================================================================
    // SCENARIO 3: Tie -> SOC Intervention
    // ================================================================

    @Test
    @DisplayName("Scenario 3: Tie 2-2 -> SOC intervenes -> VERIFIED")
    public void testScenario3_Tie_SOCIntervention_Verified() throws Exception {
        // 4 active nodes (1 offline)
        IoC ioc = new IoC(
                IoC.IoCType.DOMAIN,
                "unknown-domain.xyz",
                55,
                null,
                0,
                "Banca",
                TOTAL_NODES,
                4); // 4 active

        assertEquals(3, ioc.getQuorumSize());

        // Create 2-2 tie
        ioc.addVote(0, IoC.VoteType.CONFIRM);
        ioc.addVote(1, IoC.VoteType.REJECT);
        ioc.addVote(2, IoC.VoteType.CONFIRM);
        ioc.addVote(3, IoC.VoteType.REJECT);

        assertEquals(IoC.IoCStatus.AWAITING_SOC, ioc.getStatus(), "2-2 tie -> AWAITING_SOC");

        // SOC intervenes
        ioc.addVote(-1, IoC.VoteType.CONFIRM);

        assertEquals(IoC.IoCStatus.VERIFIED, ioc.getStatus(), "SOC CONFIRM -> VERIFIED");
    }

    // ================================================================
    // SCENARIO 4: Vector Clock Causality
    // ================================================================

    @Test
    @DisplayName("Scenario 4: Vector Clock preserves causal ordering")
    public void testScenario4_VectorClock_CausalOrdering() throws Exception {
        // Node0 generates event
        VectorClock vc0 = getInternalVectorClock(nodes[0]);
        vc0.tick();
        int[] snapshot0 = nodes[0].getVectorClockArray();

        // Node1 receives from Node0
        VectorClock vc1 = getInternalVectorClock(nodes[1]);
        vc1.update(snapshot0);
        int[] snapshot1 = nodes[1].getVectorClockArray();

        // Node2 receives from Node1
        VectorClock vc2 = getInternalVectorClock(nodes[2]);
        vc2.update(snapshot1);
        int[] snapshot2 = nodes[2].getVectorClockArray();

        // Verify transitivity
        assertTrue(happenedBefore(snapshot0, snapshot1));
        assertTrue(happenedBefore(snapshot1, snapshot2));
        assertTrue(happenedBefore(snapshot0, snapshot2));

        // Node3 generates independent event (concurrent)
        VectorClock vc3 = getInternalVectorClock(nodes[3]);
        vc3.tick();
        int[] snapshot3 = nodes[3].getVectorClockArray();

        assertFalse(happenedBefore(snapshot3, snapshot1), "Concurrent events");
        assertFalse(happenedBefore(snapshot1, snapshot3), "Concurrent events");
    }

    // ================================================================
    // SCENARIO 5: Duplicate IoC -> Same ID
    // ================================================================

    @Test
    @DisplayName("Scenario 5: Duplicate IoC -> same ID -> deduplication")
    public void testScenario5_DuplicateIoC_SameID() throws Exception {
        IoC ioc1 = new IoC(IoC.IoCType.HASH, "duplicate_hash", 80, null, 0, "Banca", TOTAL_NODES);
        IoC ioc2 = new IoC(IoC.IoCType.HASH, "duplicate_hash", 75, null, 1, "Retail", TOTAL_NODES);

        assertEquals(ioc1.getId(), ioc2.getId(), "Same type+value = same ID");
    }

    // ================================================================
    // SCENARIO 6: Quorum with 3 votes
    // ================================================================

    @Test
    @DisplayName("Scenario 6: Quorum reached with exactly 3 votes")
    public void testScenario6_QuorumWith3Votes() throws Exception {
        IoC ioc = new IoC(IoC.IoCType.IP, "192.168.1.1", 80, null, 0, "Banca", TOTAL_NODES);

        ioc.addVote(0, IoC.VoteType.CONFIRM);
        assertEquals(IoC.IoCStatus.PENDING, ioc.getStatus());

        ioc.addVote(1, IoC.VoteType.CONFIRM);
        assertEquals(IoC.IoCStatus.PENDING, ioc.getStatus());

        ioc.addVote(2, IoC.VoteType.CONFIRM);
        assertEquals(IoC.IoCStatus.VERIFIED, ioc.getStatus(), "3 CONFIRM = quorum");
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private VectorClock getInternalVectorClock(DTIPNode node) throws Exception {
        Field field = DTIPNode.class.getDeclaredField("vectorClock");
        field.setAccessible(true);
        return (VectorClock) field.get(node);
    }

    private boolean happenedBefore(int[] vc1, int[] vc2) {
        boolean allLessOrEqual = true;
        boolean existsStrictlyLess = false;

        for (int i = 0; i < vc1.length; i++) {
            if (vc1[i] > vc2[i]) {
                allLessOrEqual = false;
                break;
            }
            if (vc1[i] < vc2[i]) {
                existsStrictlyLess = true;
            }
        }

        return allLessOrEqual && existsStrictlyLess;
    }
}
