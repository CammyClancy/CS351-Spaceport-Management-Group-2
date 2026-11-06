package transport;

import org.junit.jupiter.api.Test;
import transport.collaborator.EventLogger;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the TransportService: load, unload, transfer cargo and view commands.
class TransportServiceTest {

    // A fake event logger that stores messages so we can check they were logged.
    private static class FakeEventLogger implements EventLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }

        List<String> getMessages() {
            return messages;
        }
    }

    @Test
    void loadCargoOnDockedTransportSucceeds() {
        Transport t = new Transport("T001", 100, "2026-11-03T14:30");
        t.transitionTo(TransportState.DOCKED);
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        FakeEventLogger logger = new FakeEventLogger();
        TransportService direct = new TransportService(registry, logger);

        String result = direct.loadCargo("T001", 30);
        assertEquals("Loaded 30 units into T001", result);
        assertEquals(30, t.getCargoQuantity());
        assertEquals(1, logger.getMessages().size());
        assertEquals("Loaded 30 units into T001", logger.getMessages().get(0));
    }

    @Test
    void loadCargoFailsWhenTransportNotFound() {
        TransportRegistry registry = new TransportRegistry();
        FakeEventLogger logger = new FakeEventLogger();
        TransportService direct = new TransportService(registry, logger);

        String result = direct.loadCargo("T999", 10);
        assertEquals("Transport not found: T999", result);
    }

    @Test
    void loadCargoFailsWhenTransportNotInValidState() {
        Transport t = new Transport("T001", 100, "2026-11-03T14:30");
        // T001 starts in WAITING state, cargo ops not allowed
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        FakeEventLogger logger = new FakeEventLogger();
        TransportService direct = new TransportService(registry, logger);

        String result = direct.loadCargo("T001", 10);
        assertEquals("Cannot load cargo: transport is WAITING", result);
    }

    @Test
    void loadCargoFailsWhenExceedsCapacity() {
        Transport t = new Transport("T001", 100, "2026-11-03T14:30");
        t.transitionTo(TransportState.DOCKED);
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        FakeEventLogger logger = new FakeEventLogger();
        TransportService direct = new TransportService(registry, logger);

        String result = direct.loadCargo("T001", 150);
        assertEquals("Cannot load 150 units: exceeds capacity", result);
        assertEquals(0, t.getCargoQuantity());
        assertTrue(logger.getMessages().isEmpty());
    }

    @Test
    void unloadCargoSucceeds() {
        Transport t = new Transport("T001", 100, "2026-11-03T14:30");
        t.transitionTo(TransportState.DOCKED);
        t.addCargo(50);
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        FakeEventLogger logger = new FakeEventLogger();
        TransportService direct = new TransportService(registry, logger);

        String result = direct.unloadCargo("T001", 20);
        assertEquals("Unloaded 20 units from T001", result);
        assertEquals(30, t.getCargoQuantity());
        assertEquals(1, logger.getMessages().size());
    }

    @Test
    void unloadCargoFailsWhenInsufficientCargo() {
        Transport t = new Transport("T001", 100, "2026-11-03T14:30");
        t.transitionTo(TransportState.DOCKED);
        t.addCargo(10);
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        FakeEventLogger logger = new FakeEventLogger();
        TransportService direct = new TransportService(registry, logger);

        String result = direct.unloadCargo("T001", 20);
        assertEquals("Cannot unload 20 units: insufficient cargo", result);
        assertEquals(10, t.getCargoQuantity());
        assertTrue(logger.getMessages().isEmpty());
    }

    @Test
    void transferCargoSucceedsBetweenTwoTransports() {
        Transport from = new Transport("T001", 100, "2026-11-03T14:30");
        from.transitionTo(TransportState.DOCKED);
        from.addCargo(50);
        Transport to = new Transport("T002", 50, "2026-11-03T15:00");
        to.transitionTo(TransportState.DOCKED);
        TransportRegistry registry = new TransportRegistry();
        registry.add(from);
        registry.add(to);
        FakeEventLogger logger = new FakeEventLogger();
        TransportService direct = new TransportService(registry, logger);

        String result = direct.transferCargo("T001", "T002", 20);
        assertEquals("Transferred 20 units from T001 to T002", result);
        assertEquals(30, from.getCargoQuantity());
        assertEquals(20, to.getCargoQuantity());
        assertEquals(1, logger.getMessages().size());
    }

    @Test
    void viewCargoReturnsQuantity() {
        Transport t = new Transport("T001", 100, "2026-11-03T14:30");
        t.transitionTo(TransportState.DOCKED);
        t.addCargo(25);
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        TransportService direct = new TransportService(registry, new FakeEventLogger());

        String result = direct.viewCargo("T001");
        assertEquals("T001 cargo=25/100", result);
    }
}
