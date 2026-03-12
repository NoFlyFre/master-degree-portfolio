package model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IoC (Indicator of Compromise) model.
 * Tests consensus voting logic and status transitions.
 *
 * Network configuration: 5 nodes, Quorum = 3
 */
public class IoCTest {

    @Test
    public void testIoCCreation() {
        IoC ioc = new IoC(
                IoC.IoCType.IP,
                "192.168.1.100",
                75,
                null,
                0,
                "TestNode",
                5); // 5 nodes

        assertEquals("192.168.1.100", ioc.getValue());
        assertEquals(IoC.IoCType.IP, ioc.getType());
        assertEquals(75, ioc.getConfidence());
        assertEquals(IoC.IoCStatus.PENDING, ioc.getStatus());
        assertEquals(3, ioc.getQuorumSize(), "Quorum for 5 nodes should be 3");
    }

    @Test
    public void testQuorumConfirm() {
        // 5 nodes, quorum = 3
        IoC ioc = new IoC(IoC.IoCType.IP, "1.2.3.4", 80, null, 0, "Node0", 5);

        ioc.addVote(0, IoC.VoteType.CONFIRM);
        assertEquals(IoC.IoCStatus.PENDING, ioc.getStatus(), "1 vote: still PENDING");

        ioc.addVote(1, IoC.VoteType.CONFIRM);
        assertEquals(IoC.IoCStatus.PENDING, ioc.getStatus(), "2 votes: still PENDING");

        ioc.addVote(2, IoC.VoteType.CONFIRM);
        assertEquals(IoC.IoCStatus.VERIFIED, ioc.getStatus(), "3 CONFIRM = quorum reached");
    }

    @Test
    public void testQuorumReject() {
        IoC ioc = new IoC(IoC.IoCType.IP, "8.8.8.8", 20, null, 0, "Node0", 5);

        ioc.addVote(0, IoC.VoteType.REJECT);
        ioc.addVote(1, IoC.VoteType.REJECT);
        ioc.addVote(2, IoC.VoteType.REJECT);

        assertEquals(IoC.IoCStatus.REJECTED, ioc.getStatus(), "3 REJECT = quorum reached");
    }

    @Test
    public void testTieAwaitingSOC() {
        // 4 active nodes (1 offline), quorum = 3
        // Votes: 2-2 tie -> AWAITING_SOC
        IoC ioc = new IoC(IoC.IoCType.IP, "10.0.0.1", 50, null, 0, "Node0", 5, 4);

        assertEquals(3, ioc.getQuorumSize(), "Quorum for 4 active nodes is 3");

        ioc.addVote(0, IoC.VoteType.CONFIRM);
        ioc.addVote(1, IoC.VoteType.REJECT);
        ioc.addVote(2, IoC.VoteType.CONFIRM);
        ioc.addVote(3, IoC.VoteType.REJECT);

        // 2-2 tie with all 4 active nodes voted -> AWAITING_SOC
        assertEquals(IoC.IoCStatus.AWAITING_SOC, ioc.getStatus(),
                "Tie (2-2) with all active nodes voted should be AWAITING_SOC");
    }

    @Test
    public void testSOCOverrideConfirm() {
        IoC ioc = new IoC(IoC.IoCType.DOMAIN, "malicious.com", 50, null, 0, "Node0", 5, 4);

        // Create 2-2 tie
        ioc.addVote(0, IoC.VoteType.CONFIRM);
        ioc.addVote(1, IoC.VoteType.REJECT);
        ioc.addVote(2, IoC.VoteType.CONFIRM);
        ioc.addVote(3, IoC.VoteType.REJECT);

        assertEquals(IoC.IoCStatus.AWAITING_SOC, ioc.getStatus());

        // SOC votes CONFIRM (nodeId = -1)
        ioc.addVote(-1, IoC.VoteType.CONFIRM);

        assertEquals(IoC.IoCStatus.VERIFIED, ioc.getStatus(),
                "SOC CONFIRM should override -> VERIFIED");
    }

    @Test
    public void testSOCOverrideReject() {
        IoC ioc = new IoC(IoC.IoCType.HASH, "abc123def456", 50, null, 0, "Node0", 5, 4);

        // Create 2-2 tie
        ioc.addVote(0, IoC.VoteType.CONFIRM);
        ioc.addVote(1, IoC.VoteType.REJECT);
        ioc.addVote(2, IoC.VoteType.CONFIRM);
        ioc.addVote(3, IoC.VoteType.REJECT);

        assertEquals(IoC.IoCStatus.AWAITING_SOC, ioc.getStatus());

        // SOC votes REJECT
        ioc.addVote(-1, IoC.VoteType.REJECT);

        assertEquals(IoC.IoCStatus.REJECTED, ioc.getStatus(),
                "SOC REJECT should override -> REJECTED");
    }

    @Test
    public void testPartialVoting() {
        IoC ioc = new IoC(IoC.IoCType.IP, "172.16.0.1", 60, null, 0, "Node0", 5);

        ioc.addVote(0, IoC.VoteType.CONFIRM);
        ioc.addVote(1, IoC.VoteType.CONFIRM);

        assertEquals(IoC.IoCStatus.PENDING, ioc.getStatus(),
                "Should remain PENDING with only 2 votes (quorum = 3)");
    }

    @Test
    public void testMajorityWins() {
        IoC ioc = new IoC(IoC.IoCType.IP, "198.51.100.1", 85, null, 0, "Node0", 5);

        ioc.addVote(0, IoC.VoteType.CONFIRM);
        ioc.addVote(1, IoC.VoteType.CONFIRM);
        ioc.addVote(2, IoC.VoteType.CONFIRM);
        ioc.addVote(3, IoC.VoteType.CONFIRM);
        ioc.addVote(4, IoC.VoteType.REJECT);

        assertEquals(IoC.IoCStatus.VERIFIED, ioc.getStatus(),
                "4 CONFIRM vs 1 REJECT should be VERIFIED");
    }

    @Test
    public void testIdempotentVoting() {
        IoC ioc = new IoC(IoC.IoCType.IP, "192.0.2.1", 55, null, 0, "Node0", 5);

        ioc.addVote(0, IoC.VoteType.CONFIRM);
        ioc.addVote(0, IoC.VoteType.REJECT); // Same node votes again

        assertEquals(1, ioc.getVotes().size(), "Only one vote per node should exist");
    }

    @Test
    public void testDifferentIoCTypes() {
        IoC ipIoC = new IoC(IoC.IoCType.IP, "1.1.1.1", 60, null, 0, "Node0", 5);
        IoC domainIoC = new IoC(IoC.IoCType.DOMAIN, "evil.com", 70, null, 1, "Node1", 5);
        IoC hashIoC = new IoC(IoC.IoCType.HASH, "d41d8cd98f00b204e9800998ecf8427e", 80, null, 2, "Node2", 5);

        assertEquals(IoC.IoCType.IP, ipIoC.getType());
        assertEquals(IoC.IoCType.DOMAIN, domainIoC.getType());
        assertEquals(IoC.IoCType.HASH, hashIoC.getType());
    }

    @Test
    public void testDynamicQuorum() {
        // Test quorum calculation for different active node counts
        IoC ioc5 = new IoC(IoC.IoCType.IP, "test1", 50, null, 0, "Node0", 5, 5);
        assertEquals(3, ioc5.getQuorumSize(), "5 active -> quorum = 3");

        IoC ioc4 = new IoC(IoC.IoCType.IP, "test2", 50, null, 0, "Node0", 5, 4);
        assertEquals(3, ioc4.getQuorumSize(), "4 active -> quorum = 3");

        IoC ioc3 = new IoC(IoC.IoCType.IP, "test3", 50, null, 0, "Node0", 5, 3);
        assertEquals(2, ioc3.getQuorumSize(), "3 active -> quorum = 2");
    }
}
