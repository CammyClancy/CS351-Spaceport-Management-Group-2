package transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests that transport states can only change in the correct order:
// WAITING -> DOCKED -> SERVICING -> READY -> DEPARTED
class TransportStateTest {

    @Test
    void waitingCanTransitionToDocked() {
        assertTrue(TransportState.WAITING.canTransitionTo(TransportState.DOCKED));
    }

    @Test
    void dockedCanTransitionToServicing() {
        assertTrue(TransportState.DOCKED.canTransitionTo(TransportState.SERVICING));
    }

    @Test
    void servicingCanTransitionToReady() {
        assertTrue(TransportState.SERVICING.canTransitionTo(TransportState.READY));
    }

    @Test
    void readyCanTransitionToDeparted() {
        assertTrue(TransportState.READY.canTransitionTo(TransportState.DEPARTED));
    }

    @Test
    void waitingCannotTransitionDirectlyToServicing() {
        assertFalse(TransportState.WAITING.canTransitionTo(TransportState.SERVICING));
    }

    @Test
    void waitingCannotTransitionDirectlyToReady() {
        assertFalse(TransportState.WAITING.canTransitionTo(TransportState.READY));
    }

    @Test
    void waitingCannotTransitionDirectlyToDeparted() {
        assertFalse(TransportState.WAITING.canTransitionTo(TransportState.DEPARTED));
    }

    @Test
    void dockedCannotTransitionBackToWaiting() {
        assertFalse(TransportState.DOCKED.canTransitionTo(TransportState.WAITING));
    }

    @Test
    void departedCannotTransitionAnywhere() {
        assertFalse(TransportState.DEPARTED.canTransitionTo(TransportState.WAITING));
        assertFalse(TransportState.DEPARTED.canTransitionTo(TransportState.DOCKED));
        assertFalse(TransportState.DEPARTED.canTransitionTo(TransportState.SERVICING));
        assertFalse(TransportState.DEPARTED.canTransitionTo(TransportState.READY));
        assertFalse(TransportState.DEPARTED.canTransitionTo(TransportState.DEPARTED));
    }

    @Test
    void sameStateIsNotAValidTransition() {
        assertFalse(TransportState.WAITING.canTransitionTo(TransportState.WAITING));
        assertFalse(TransportState.DOCKED.canTransitionTo(TransportState.DOCKED));
    }
}
