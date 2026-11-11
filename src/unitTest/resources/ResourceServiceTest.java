package resources;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import transport.Transport;
import transport.TransportRegistry;
import transport.TransportService;
import transport.TransportState;
import transport.collaborator.EventLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourceServiceTest {
    private final Transport first = new Transport("T001", 100, "2026-11-10T14:00");
    private final Transport second = new Transport("T002", 100, "2026-11-10T15:00");
    private final DockingBay bay = new DockingBay("B01");
    private final List<String> events = new ArrayList<>();
    private final ResourceService service;

    ResourceServiceTest() {
        TransportRegistry registry = new TransportRegistry();
        registry.add(first);
        registry.add(second);
        EventLogger logger = events::add;
        TransportService cargo = new TransportService(registry, logger);
        service = new ResourceService(List.of(bay), registry, cargo, logger);
    }

    @Test
    void allocateBay() {
        String result = service.allocateBay("T001", "B01");

        assertEquals("Allocated B01 to T001", result);
        assertEquals(TransportState.DOCKED, first.getState());
        assertEquals("B01", first.getBayId());
        assertEquals(BayState.OCCUPIED, bay.getState());
        assertEquals("T001", bay.getTransportId());
        assertEquals(List.of(result), events);
    }

    @Test
    void closeAndReopen() {
        assertEquals("Closed B01", service.closeBay("B01"));
        assertEquals(BayState.CLOSED, bay.getState());

        // A waiting transport cannot take a closed bay.
        assertEquals("Bay is unavailable: B01", service.allocateBay("T001", "B01"));
        assertEquals(TransportState.WAITING, first.getState());
        assertNull(first.getBayId());

        assertEquals("Reopened B01", service.reopenBay("B01"));
        assertEquals(BayState.AVAILABLE, bay.getState());
        assertEquals(List.of("Closed B01", "Reopened B01"), events);

        // Once reopened, the same bay can be allocated.
        assertEquals("Allocated B01 to T001", service.allocateBay("T001", "B01"));
    }

    @Test
    void closeOccupied() {
        service.allocateBay("T001", "B01");

        assertEquals("Cannot close bay: OCCUPIED", service.closeBay("B01"));
        assertEquals(BayState.OCCUPIED, bay.getState());
        assertEquals("T001", bay.getTransportId());
        assertEquals("B01", first.getBayId());
        assertEquals(List.of("Allocated B01 to T001"), events);
    }

    @Test
    void bayTaken() {
        service.allocateBay("T001", "B01");
        String result = service.allocateBay("T002", "B01");

        assertEquals("Bay is unavailable: B01", result);
        assertEquals("T001", bay.getTransportId());
        assertEquals(TransportState.WAITING, second.getState());
        assertNull(second.getBayId());
        assertEquals(1, events.size());
    }

    @Test
    void sameBayRace() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> a = pool.submit(() -> {
                start.await();
                return service.allocateBay("T001", "B01");
            });
            Future<String> b = pool.submit(() -> {
                start.await();
                return service.allocateBay("T002", "B01");
            });

            start.countDown();
            String firstReply = a.get(5, TimeUnit.SECONDS);
            String secondReply = b.get(5, TimeUnit.SECONDS);

            Transport winner = first;
            Transport waiting = second;
            String success = firstReply;
            String failure = secondReply;
            if ("T002".equals(bay.getTransportId())) {
                winner = second;
                waiting = first;
                success = secondReply;
                failure = firstReply;
            }
            assertEquals("Allocated B01 to " + winner.getId(), success);
            assertEquals("Bay is unavailable: B01", failure);
            assertEquals(BayState.OCCUPIED, bay.getState());
            assertEquals(winner.getId(), bay.getTransportId());
            assertEquals(TransportState.DOCKED, winner.getState());
            assertEquals("B01", winner.getBayId());
            assertEquals(TransportState.WAITING, waiting.getState());
            assertNull(waiting.getBayId());
            assertEquals(List.of(success), events);
        } finally {
            pool.shutdownNow();
        }
    }
}