package resources;

import java.time.LocalDateTime;
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
    private final DockingBay bay = new DockingBay("BAY_01");
    private final DockingBay secondBay = new DockingBay("BAY_02");
    private final MaintenanceTeam team = new MaintenanceTeam("TEAM_A");
    private final List<String> events = new ArrayList<>();
    private final ResourceService service;
    private final TransportService cargo;
    private final TransportRegistry registry = new TransportRegistry();

    ResourceServiceTest() {
        registry.add(first);
        registry.add(second);

        EventLogger logger = events::add;
        cargo = new TransportService(registry, logger);

        service = new ResourceService(List.of(bay, secondBay), List.of(team),
                registry, cargo, logger);
    }

    @Test
    void allocateBay() {
        String result = service.allocateBay("T001", "BAY_01");

        assertEquals("Allocated BAY_01 to T001", result);
        assertEquals(TransportState.DOCKED, first.getState());
        assertEquals("BAY_01", first.getBayId());
        assertEquals(BayState.OCCUPIED, bay.getState());
        assertEquals("T001", bay.getTransportId());
        assertEquals(List.of(result), events);
    }

    @Test
    void closeAndReopen() {
        assertEquals("Closed BAY_01", service.closeBay("BAY_01"));
        assertEquals(BayState.CLOSED, bay.getState());

        // A waiting transport cannot take a closed bay.
        assertEquals("Bay is unavailable: BAY_01", service.allocateBay("T001", "BAY_01"));
        assertEquals(TransportState.WAITING, first.getState());
        assertNull(first.getBayId());

        assertEquals("Reopened BAY_01", service.reopenBay("BAY_01"));
        assertEquals(BayState.AVAILABLE, bay.getState());
        assertEquals(List.of("Closed BAY_01", "Reopened BAY_01"), events);

        // Once reopened, the same bay can be allocated.
        assertEquals("Allocated BAY_01 to T001", service.allocateBay("T001", "BAY_01"));
    }

    @Test
    void closeOccupied() {
        service.allocateBay("T001", "BAY_01");

        assertEquals("Cannot close bay: OCCUPIED", service.closeBay("BAY_01"));
        assertEquals(BayState.OCCUPIED, bay.getState());
        assertEquals("T001", bay.getTransportId());
        assertEquals("BAY_01", first.getBayId());
        assertEquals(List.of("Allocated BAY_01 to T001"), events);
    }

    @Test
    void bayTaken() {
        service.allocateBay("T001", "BAY_01");
        String result = service.allocateBay("T002", "BAY_01");

        assertEquals("Bay is unavailable: BAY_01", result);
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
            Future<String> firstRequest = pool.submit(() -> {
                start.await();
                return service.allocateBay("T001", "BAY_01");
            });

            Future<String> secondRequest = pool.submit(() -> {
                start.await();
                return service.allocateBay("T002", "BAY_01");
            });

            start.countDown();
            String firstReply = firstRequest.get(5, TimeUnit.SECONDS);
            String secondReply = secondRequest.get(5, TimeUnit.SECONDS);

            // Either transport can get the bay first.
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

            assertEquals("Allocated BAY_01 to " + winner.getId(), success);
            assertEquals("Bay is unavailable: BAY_01", failure);
            assertEquals(BayState.OCCUPIED, bay.getState());
            assertEquals(winner.getId(), bay.getTransportId());
            assertEquals(TransportState.DOCKED, winner.getState());
            assertEquals("BAY_01", winner.getBayId());
            assertEquals(TransportState.WAITING, waiting.getState());
            assertNull(waiting.getBayId());
            assertEquals(List.of(success), events);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void swapBays() {
        service.allocateBay("T001", "BAY_01");
        service.allocateBay("T002", "BAY_02");
        second.transitionTo(TransportState.SERVICING);
        second.transitionTo(TransportState.READY);

        String result = service.exchangeBays("T001", "T002");

        assertEquals("Exchanged BAY_01 and BAY_02 for T001 and T002", result);
        assertEquals("BAY_02", first.getBayId());
        assertEquals("BAY_01", second.getBayId());
        assertEquals("T002", bay.getTransportId());
        assertEquals("T001", secondBay.getTransportId());
        assertEquals(TransportState.DOCKED, first.getState());
        assertEquals(TransportState.READY, second.getState());

        // Two allocations and one exchange.
        assertEquals(3, events.size());
        assertEquals(result, events.get(2));
    }

    @Test
    void swapRejected() {
        service.allocateBay("T001", "BAY_01");
        service.allocateBay("T002", "BAY_02");
        second.transitionTo(TransportState.SERVICING);

        String result = service.exchangeBays("T001", "T002");

        assertEquals("Both transports must be DOCKED or READY", result);
        assertEquals("BAY_01", first.getBayId());
        assertEquals("BAY_02", second.getBayId());
        assertEquals("T001", bay.getTransportId());
        assertEquals("T002", secondBay.getTransportId());
        assertEquals(2, events.size());
    }

    @Test
    void addTransport() {
        String result = service.addTransport("T003", 80, "2026-11-10T17:00");

        assertEquals("Added transport T003", result);

        Transport added = registry.get("T003");
        assertEquals(TransportState.WAITING, added.getState());
        assertEquals(80, added.getCapacity());
        assertEquals(0, added.getCargoQuantity());
        assertEquals("2026-11-10T17:00", added.getDepartureTime());
        assertEquals(List.of(result), events);
    }

    @Test
    void duplicateId() {
        service.addTransport("T003", 80, "2026-11-10T17:00");
        String result = service.addTransport("T003", 200, "2026-11-10T19:00");

        assertEquals("Transport already exists: T003", result);
        assertEquals(80, registry.get("T003").getCapacity());
        assertEquals("2026-11-10T17:00", registry.get("T003").getDepartureTime());
        assertEquals(List.of("Added transport T003"), events);
    }

    @Test
    void invalidTransport() {
        assertEquals("Invalid transport ID",
                service.addTransport("T 003", 80, "2026-11-10T17:00"));
        assertEquals("Capacity must be greater than zero",
                service.addTransport("T003", 0, "2026-11-10T17:00"));
        assertEquals("Invalid departure time",
                service.addTransport("T003", 80, "tomorrow"));

        // Only the two transports from the test setup should exist.
        assertEquals(2, registry.getAll().size());
        assertEquals(List.of(), events);
    }

    @Test
    void assignTeam() {
        service.allocateBay("T001", "BAY_01");

        String result = service.assignTeam("T001", "TEAM_A");

        assertEquals("Assigned TEAM_A to T001", result);
        assertEquals(TransportState.SERVICING, first.getState());
        assertEquals("TEAM_A", first.getTeamId());
        assertEquals(TeamState.BUSY, team.getState());
        assertEquals("T001", team.getTransportId());
        assertEquals(List.of("Allocated BAY_01 to T001", result), events);
    }

    @Test
    void completeMaintenance() {
        service.allocateBay("T001", "BAY_01");
        service.assignTeam("T001", "TEAM_A");

        String result = service.completeMaintenance("T001");

        assertEquals("Completed maintenance on T001 and released TEAM_A", result);
        assertEquals(TransportState.READY, first.getState());
        assertNull(first.getTeamId());
        assertEquals(TeamState.AVAILABLE, team.getState());
        assertNull(team.getTransportId());

        // The transport keeps its bay until departure.
        assertEquals("BAY_01", first.getBayId());
        assertEquals("T001", bay.getTransportId());
        assertEquals(List.of("Allocated BAY_01 to T001", "Assigned TEAM_A to T001", result),
                events);
    }

    @Test
    void sameTeamRace() throws Exception {
        service.allocateBay("T001", "BAY_01");
        service.allocateBay("T002", "BAY_02");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> firstRequest = pool.submit(() -> {
                start.await();
                return service.assignTeam("T001", "TEAM_A");
            });

            Future<String> secondRequest = pool.submit(() -> {
                start.await();
                return service.assignTeam("T002", "TEAM_A");
            });

            start.countDown();
            String firstReply = firstRequest.get(5, TimeUnit.SECONDS);
            String secondReply = secondRequest.get(5, TimeUnit.SECONDS);

            // Either transport can get the team first.
            Transport winner = first;
            Transport waiting = second;
            String success = firstReply;
            String failure = secondReply;

            if ("T002".equals(team.getTransportId())) {
                winner = second;
                waiting = first;
                success = secondReply;
                failure = firstReply;
            }

            assertEquals("Assigned TEAM_A to " + winner.getId(), success);
            assertEquals("Team is busy: TEAM_A", failure);
            assertEquals(TeamState.BUSY, team.getState());
            assertEquals(winner.getId(), team.getTransportId());
            assertEquals(TransportState.SERVICING, winner.getState());
            assertEquals("TEAM_A", winner.getTeamId());
            assertEquals(TransportState.DOCKED, waiting.getState());
            assertNull(waiting.getTeamId());

            // Two bay allocations and one successful team assignment.
            assertEquals(3, events.size());
            assertEquals(success, events.get(2));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void viewBays() {
        service.allocateBay("T001", "BAY_01");

        assertEquals("BAY_01 OCCUPIED (T001), BAY_02 AVAILABLE", service.viewBays());
    }

    @Test
    void viewTeams() {
        assertEquals("TEAM_A AVAILABLE", service.viewTeams());

        service.allocateBay("T001", "BAY_01");
        assertEquals("Assigned TEAM_A to T001", service.assignTeam("T001", "TEAM_A"));

        assertEquals("TEAM_A BUSY (T001)", service.viewTeams());
    }

    @Test
    void departOnTime() {
        service.allocateBay("T001", "BAY_01");
        service.assignTeam("T001", "TEAM_A");
        service.completeMaintenance("T001");

        LocalDateTime departure = LocalDateTime.parse("2026-11-10T14:00");

        service.checkDepartures(departure.minusSeconds(1));
        assertEquals(TransportState.READY, first.getState());
        assertEquals("BAY_01", first.getBayId());
        assertEquals("T001", bay.getTransportId());

        service.checkDepartures(departure);
        assertEquals(TransportState.DEPARTED, first.getState());
        assertNull(first.getBayId());
        assertEquals(BayState.AVAILABLE, bay.getState());
        assertNull(bay.getTransportId());

        service.checkDepartures(departure.plusSeconds(1));
        assertEquals(List.of(
                "Allocated BAY_01 to T001",
                "Assigned TEAM_A to T001",
                "Completed maintenance on T001 and released TEAM_A",
                "Departed T001 and released BAY_01"
        ), events);
    }

    @Test
    void stillServicing() {
        service.allocateBay("T001", "BAY_01");
        service.assignTeam("T001", "TEAM_A");

        service.checkDepartures(LocalDateTime.parse("2026-11-10T14:05"));

        assertEquals(TransportState.SERVICING, first.getState());
        assertEquals("BAY_01", first.getBayId());
        assertEquals("T001", bay.getTransportId());
        assertEquals("TEAM_A", first.getTeamId());
        assertEquals("T001", team.getTransportId());
        assertEquals(List.of("Allocated BAY_01 to T001", "Assigned TEAM_A to T001"), events);
    }

    @Test
    void loadDuringDeparture() throws Exception {
        service.allocateBay("T001", "BAY_01");
        service.assignTeam("T001", "TEAM_A");
        service.completeMaintenance("T001");
        events.clear(); // Only check events from the two competing operations.

        LocalDateTime departure = LocalDateTime.parse("2026-11-10T14:00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> load = pool.submit(() -> {
                start.await();
                return cargo.loadCargo("T001", 10);
            });

            Future<Void> depart = pool.submit(() -> {
                start.await();
                service.checkDepartures(departure);
                return null;
            });

            start.countDown();
            String loadReply = load.get(5, TimeUnit.SECONDS);
            depart.get(5, TimeUnit.SECONDS);

            assertEquals(TransportState.DEPARTED, first.getState());
            assertNull(first.getBayId());
            assertEquals(BayState.AVAILABLE, bay.getState());
            assertNull(bay.getTransportId());

            String loaded = "Loaded 10 units into T001";
            String departed = "Departed T001 and released BAY_01";

            if (loadReply.equals(loaded)) {
                assertEquals(10, first.getCargoQuantity());
                assertEquals(List.of(loaded, departed), events);
            } else {
                assertEquals("Cannot load cargo: transport is DEPARTED", loadReply);
                assertEquals(0, first.getCargoQuantity());
                assertEquals(List.of(departed), events);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}