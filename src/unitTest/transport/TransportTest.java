package transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

// Tests the Transport class: starting state, state changes, and cargo add/remove.
class TransportTest {

    @Test
    void newTransportStartsWaitingWithZeroCargo() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        assertEquals("T001", transport.getId());
        assertEquals(TransportState.WAITING, transport.getState());
        assertEquals(0, transport.getCargoQuantity());
        assertEquals(100, transport.getCapacity());
        assertNull(transport.getBayId());
        assertNull(transport.getTeamId());
    }

    @Test
    void canTransitionWaitingToDocked() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        assertTrue(transport.transitionTo(TransportState.DOCKED));
        assertEquals(TransportState.DOCKED, transport.getState());
    }

    @Test
    void cannotTransitionWaitingToServicing() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        assertFalse(transport.transitionTo(TransportState.SERVICING));
        assertEquals(TransportState.WAITING, transport.getState());
    }

    @Test
    void canTransitionDockedToServicingThenReady() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        transport.transitionTo(TransportState.DOCKED);
        transport.transitionTo(TransportState.SERVICING);
        transport.transitionTo(TransportState.READY);
        assertEquals(TransportState.READY, transport.getState());
    }

    @Test
    void cannotSkipStates() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        assertFalse(transport.transitionTo(TransportState.READY));
        assertEquals(TransportState.WAITING, transport.getState());
    }

    @Test
    void addCargoSucceedsWithinCapacity() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        assertTrue(transport.addCargo(50));
        assertEquals(50, transport.getCargoQuantity());
    }

    @Test
    void addCargoFailsWhenExceedsCapacity() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        assertTrue(transport.addCargo(80));
        assertFalse(transport.addCargo(30));
        assertEquals(80, transport.getCargoQuantity());
    }

    @Test
    void addCargoFailsForZeroOrNegative() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        assertFalse(transport.addCargo(0));
        assertFalse(transport.addCargo(-5));
        assertEquals(0, transport.getCargoQuantity());
    }

    @Test
    void removeCargoSucceedsWhenEnoughCargo() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        transport.addCargo(50);
        assertTrue(transport.removeCargo(30));
        assertEquals(20, transport.getCargoQuantity());
    }

    @Test
    void removeCargoFailsWhenNotEnoughCargo() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        transport.addCargo(10);
        assertFalse(transport.removeCargo(20));
        assertEquals(10, transport.getCargoQuantity());
    }

    @Test
    void removeCargoFailsForZeroOrNegative() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        transport.addCargo(50);
        assertFalse(transport.removeCargo(0));
        assertFalse(transport.removeCargo(-5));
        assertEquals(50, transport.getCargoQuantity());
    }

    @Test
    void setBayIdAndTeamId() {
        Transport transport = new Transport("T001", 100, "2026-11-03T14:30");
        transport.setBayId("BAY_01");
        transport.setTeamId("TEAM_A");
        assertEquals("BAY_01", transport.getBayId());
        assertEquals("TEAM_A", transport.getTeamId());
    }
}
