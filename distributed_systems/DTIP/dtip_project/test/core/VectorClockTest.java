package core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VectorClock class
 * Tests implementation of Lamport/Fidge vector clocks for causal ordering
 */
public class VectorClockTest {

    @Test
    public void testInitialization() {
        VectorClock vc = new VectorClock(0, 3);
        int[] expected = { 0, 0, 0 };
        assertArrayEquals(expected, vc.getClock(), "Vector clock should initialize with all zeros");
    }

    @Test
    public void testTick() {
        VectorClock vc = new VectorClock(1, 3);
        vc.tick();

        int[] expected = { 0, 1, 0 };
        assertArrayEquals(expected, vc.getClock(), "tick() should increment own index");

        // Tick again
        vc.tick();
        int[] expected2 = { 0, 2, 0 };
        assertArrayEquals(expected2, vc.getClock(), "tick() should continue incrementing");
    }

    @Test
    public void testUpdate() {
        VectorClock vc1 = new VectorClock(0, 3);
        vc1.tick(); // [1,0,0]

        VectorClock vc2 = new VectorClock(1, 3);
        // update() does merge + auto-tick (Lamport rule: receive = merge + increment)
        vc2.update(vc1.getClock()); // max([0,0,0], [1,0,0]) + tick = [1,1,0]

        int[] expected = { 1, 1, 0 };
        assertArrayEquals(expected, vc2.getClock(), "update() should apply max rule and auto-tick");
    }

    @Test
    public void testHappenedBefore() {
        // Scenario: e1 -> e2 (e1 happened before e2)
        VectorClock vc1 = new VectorClock(0, 3);
        vc1.tick(); // [1,0,0]

        VectorClock vc2 = new VectorClock(1, 3);
        // update() does merge + auto-tick, so vc2 = [1,1,0] after update
        vc2.update(vc1.getClock()); // [1,1,0]

        assertTrue(happenedBefore(vc1.getClock(), vc2.getClock()),
                "VC1=[1,0,0] should happen-before VC2=[1,1,0]");
        assertFalse(happenedBefore(vc2.getClock(), vc1.getClock()),
                "VC2=[1,1,0] should NOT happen-before VC1=[1,0,0]");
    }

    @Test
    public void testConcurrentEvents() {
        // Scenario: e1 || e2 (concurrent events)
        VectorClock vc1 = new VectorClock(0, 2);
        vc1.tick(); // [1,0]

        VectorClock vc2 = new VectorClock(1, 2);
        vc2.tick(); // [0,1]

        // Neither happened-before the other => concurrent
        assertFalse(happenedBefore(vc1.getClock(), vc2.getClock()),
                "VC1=[1,0] and VC2=[0,1] should be concurrent (not vc1 < vc2)");
        assertFalse(happenedBefore(vc2.getClock(), vc1.getClock()),
                "VC1=[1,0] and VC2=[0,1] should be concurrent (not vc2 < vc1)");
    }

    @Test
    public void testMultipleUpdates() {
        // Simulate message passing: Node0 -> Node1 -> Node2
        // update() includes auto-tick (Lamport receive rule)
        VectorClock vc0 = new VectorClock(0, 3);
        vc0.tick(); // [1,0,0]

        VectorClock vc1 = new VectorClock(1, 3);
        vc1.update(vc0.getClock()); // max([0,0,0], [1,0,0]) + tick = [1,1,0]

        VectorClock vc2 = new VectorClock(2, 3);
        vc2.update(vc1.getClock()); // max([0,0,0], [1,1,0]) + tick = [1,1,1]

        int[] expected = { 1, 1, 1 };
        assertArrayEquals(expected, vc2.getClock(),
                "VC2 should reflect all previous events via transitivity");
    }

    @Test
    public void testMaxRule() {
        VectorClock vc1 = new VectorClock(0, 3);
        vc1.tick(); // [1,0,0]
        vc1.tick(); // [2,0,0]

        VectorClock vc2 = new VectorClock(1, 3);
        vc2.tick(); // [0,1,0]
        vc2.tick(); // [0,2,0]

        // Simulate receiving message from vc1
        // update() does max + auto-tick
        vc2.update(vc1.getClock()); // max([0,2,0], [2,0,0]) + tick = [2,3,0]

        int[] expected = { 2, 3, 0 };
        assertArrayEquals(expected, vc2.getClock(),
                "update() should apply component-wise max and auto-tick");
    }

    @Test
    public void testUpdateCreatesHappenedBeforeRelation() {
        // After update(), the receiver's clock is strictly greater
        // This is correct: update = merge + tick, so VC2 > VC1
        VectorClock vc1 = new VectorClock(0, 2);
        vc1.tick(); // [1,0]

        VectorClock vc2 = new VectorClock(1, 2);
        vc2.update(vc1.getClock()); // max([0,0], [1,0]) + tick = [1,1]

        // vc1=[1,0] happened-before vc2=[1,1]
        assertTrue(happenedBefore(vc1.getClock(), vc2.getClock()),
                "VC1=[1,0] should happen-before VC2=[1,1] after update");
        assertFalse(happenedBefore(vc2.getClock(), vc1.getClock()),
                "VC2=[1,1] should NOT happen-before VC1=[1,0]");
    }

    // Helper method: VC1 < VC2 (happened-before)
    // Definition: VC1 <= VC2 AND exists i where VC1[i] < VC2[i]
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
