package resources;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceServiceTest {
    private final Transport first = new Transport("T001", 100, "2026-11-10T14:00");
    private final Transport second = new Transport("T002", 100, "2026-11-10T15:00");
    private final DockingBay bay = new DockingBay("BAY_01");
    private final DockingBay secondBay = new DockingBay("BAY_02");
    private final MaintenanceTeam team = new MaintenanceTeam("TEAM_A");
    // These lists can safely receive messages from different threads.
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final List<String> notifications = Collections.synchronizedList(new ArrayList<>());
    private final ResourceService service;
    private final TransportService cargo;
    private final TransportRegistry registry = new TransportRegistry();

    ResourceServiceTest() {
        registry.add(first);
        registry.add(second);

        EventLogger logger = events::add;
        cargo = new TransportService(registry, logger);

        service = new ResourceService(List.of(bay, secondBay), List.of(team),
                registry, logger, notifications::add);
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
        assertEquals(events, notifications);
    }

    @Test
    void closeAndReopen() {
        assertEquals("Closed BAY_01", service.closeBay("BAY_01"));
        assertEquals(BayState.CLOSED, bay.getState());

        // A waiting transport cannot take a closed bay.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.allocateBay("T001", "BAY_01"));
        assertEquals("Bay is unavailable: BAY_01", exception.getMessage());
        assertEquals(TransportState.WAITING, first.getState());
        assertNull(first.getBayId());
        assertEquals(List.of("Closed BAY_01"), notifications);

        assertEquals("Reopened BAY_01", service.reopenBay("BAY_01"));
        assertEquals(BayState.AVAILABLE, bay.getState());
        assertEquals(List.of("Closed BAY_01", "Reopened BAY_01"), events);

        // Once reopened, the same bay can be allocated.
        assertEquals("Allocated BAY_01 to T001", service.allocateBay("T001", "BAY_01"));
        assertEquals(events, notifications);
    }

    @Test
    void closeOccupied() {
        service.allocateBay("T001", "BAY_01");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.closeBay("BAY_01"));
        assertEquals("Cannot close bay: OCCUPIED", exception.getMessage());
        assertEquals(BayState.OCCUPIED, bay.getState());
        assertEquals("T001", bay.getTransportId());
        assertEquals("BAY_01", first.getBayId());
        assertEquals(List.of("Allocated BAY_01 to T001"), events);
        assertEquals(events, notifications);
    }

    @Test
    void bayTaken() {
        service.allocateBay("T001", "BAY_01");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.allocateBay("T002", "BAY_01"));
        assertEquals("Bay is unavailable: BAY_01", exception.getMessage());
        assertEquals("T001", bay.getTransportId());
        assertEquals(TransportState.WAITING, second.getState());
        assertNull(second.getBayId());
        assertEquals(List.of("Allocated BAY_01 to T001"), events);
        assertEquals(events, notifications);
    }

    @Test
    void sameBayRace() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> firstRequest = pool.submit(() -> {
                start.await();
                try {
                    return service.allocateBay("T001", "BAY_01");
                } catch (IllegalArgumentException exception) {
                    return exception.getMessage();
                }
            });

            Future<String> secondRequest = pool.submit(() -> {
                start.await();
                try {
                    return service.allocateBay("T002", "BAY_01");
                } catch (IllegalArgumentException exception) {
                    return exception.getMessage();
                }
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
            assertEquals(events, notifications);
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
        assertEquals(events, notifications);
    }

    @Test
    void swapRejected() {
        service.allocateBay("T001", "BAY_01");
        service.allocateBay("T002", "BAY_02");
        second.transitionTo(TransportState.SERVICING);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.exchangeBays("T001", "T002"));
        assertEquals("Both transports must be DOCKED or READY",
                exception.getMessage());
        assertEquals("BAY_01", first.getBayId());
        assertEquals("BAY_02", second.getBayId());
        assertEquals("T001", bay.getTransportId());
        assertEquals("T002", secondBay.getTransportId());
        assertEquals(2, events.size());
        assertEquals(events, notifications);
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
        assertEquals(events, notifications);
    }

    @Test
    void duplicateId() {
        service.addTransport("T003", 80, "2026-11-10T17:00");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addTransport(
                        "T003", 200, "2026-11-10T19:00"));

        assertEquals("Transport already exists: T003", exception.getMessage());
        assertEquals(80, registry.get("T003").getCapacity());
        assertEquals(
                "2026-11-10T17:00",
                registry.get("T003").getDepartureTime());

        assertEquals(List.of("Added transport T003"), events);
        assertEquals(events, notifications);
    }

    @Test
    void invalidTransport() {
        IllegalArgumentException invalidId = assertThrows(
                IllegalArgumentException.class,
                () -> service.addTransport(
                        "T 003", 80, "2026-11-10T17:00"));

        assertEquals("Invalid transport ID", invalidId.getMessage());

        IllegalArgumentException invalidCapacity = assertThrows(
                IllegalArgumentException.class,
                () -> service.addTransport(
                        "T003", 0, "2026-11-10T17:00"));

        assertEquals(
                "Capacity must be greater than zero",
                invalidCapacity.getMessage());

        IllegalArgumentException invalidTime = assertThrows(
                IllegalArgumentException.class,
                () -> service.addTransport("T003", 80, "tomorrow"));

        assertEquals("Invalid departure time", invalidTime.getMessage());

        // Only the two transports from the test setup should exist.
        assertEquals(2, registry.getAll().size());
        assertEquals(List.of(), events);
        assertEquals(List.of(), notifications);
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
        assertEquals(events, notifications);
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

        assertEquals(List.of("Allocated BAY_01 to T001", "Assigned TEAM_A to T001", result), events);
        assertEquals(events, notifications);
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

                try {
                    return service.assignTeam("T001", "TEAM_A");
                } catch (IllegalArgumentException exception) {
                    return exception.getMessage();
                }
            });

            Future<String> secondRequest = pool.submit(() -> {
                start.await();

                try {
                    return service.assignTeam("T002", "TEAM_A");
                } catch (IllegalArgumentException exception) {
                    return exception.getMessage();
                }
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
            assertEquals(events, notifications);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void viewBays() {
        service.allocateBay("T001", "BAY_01");

        assertEquals("BAY_01 OCCUPIED (T001), BAY_02 AVAILABLE", service.viewBays());
        // Viewing bays should not send another notification.
        assertEquals(List.of("Allocated BAY_01 to T001"), notifications);
    }

    @Test
    void viewTeams() {
        assertEquals("TEAM_A AVAILABLE", service.viewTeams());
        assertEquals(List.of(), notifications);

        service.allocateBay("T001", "BAY_01");
        assertEquals("Assigned TEAM_A to T001", service.assignTeam("T001", "TEAM_A"));

        assertEquals("TEAM_A BUSY (T001)", service.viewTeams());
        // Viewing teams should not send another notification.
        assertEquals(List.of("Allocated BAY_01 to T001", "Assigned TEAM_A to T001"), notifications);
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
        assertEquals(3, notifications.size());

        service.checkDepartures(departure);
        assertEquals(TransportState.DEPARTED, first.getState());
        assertNull(first.getBayId());
        assertEquals(BayState.AVAILABLE, bay.getState());
        assertNull(bay.getTransportId());

        // Checking again must not log or notify a second departure.
        service.checkDepartures(departure.plusSeconds(1));
        assertEquals(List.of(
                "Allocated BAY_01 to T001",
                "Assigned TEAM_A to T001",
                "Completed maintenance on T001 and released TEAM_A",
                "Departed T001 and released BAY_01"
        ), events);

        assertEquals(events, notifications);
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
        assertEquals(events, notifications);
    }

    @Test
    void loadDuringDeparture() throws Exception {
        service.allocateBay("T001", "BAY_01");
        service.assignTeam("T001", "TEAM_A");
        service.completeMaintenance("T001");

        // Only check messages from the two competing operations.
        events.clear();
        notifications.clear();

        LocalDateTime departure = LocalDateTime.parse("2026-11-10T14:00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> load = pool.submit(() -> {
                start.await();

                try {
                    return cargo.loadCargo("T001", 10);
                } catch (IllegalArgumentException exception) {
                    return exception.getMessage();
                }
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

            // Cargo notifications belong to TransportCommandHandler.
            // This test calls TransportService directly.
            assertEquals(List.of(departed), notifications);
        } finally {
            pool.shutdownNow();
        }
    }
    @Test
    void closeAndAllocateRaceLeavesConsistentState()
            throws Exception {

        ExecutorService pool =
                Executors.newFixedThreadPool(2);

        CountDownLatch start =
                new CountDownLatch(1);

        Future<String> close =
                pool.submit(() -> {
                    start.await();

                    try {
                        return service.closeBay("BAY_01");
                    } catch (IllegalArgumentException exception) {
                        return exception.getMessage();
                    }
                });

        Future<String> allocate =
                pool.submit(() -> {
                    start.await();

                    try {
                        return service.allocateBay(
                                "T001",
                                "BAY_01");
                    } catch (IllegalArgumentException exception) {
                        return exception.getMessage();
                    }
                });

        start.countDown();

        String closeResult =
                close.get(5, TimeUnit.SECONDS);

        String allocateResult =
                allocate.get(5, TimeUnit.SECONDS);

        pool.shutdownNow();

        boolean closeSucceeded =
                closeResult.equals("Closed BAY_01");

        boolean allocationSucceeded =
                allocateResult.equals(
                        "Allocated BAY_01 to T001");

        assertTrue(
                closeSucceeded ^ allocationSucceeded);

        if (closeSucceeded) {
            assertEquals(
                    BayState.CLOSED,
                    bay.getState());

            assertEquals(
                    TransportState.WAITING,
                    first.getState());

            assertNull(first.getBayId());
        } else {
            assertEquals(
                    BayState.OCCUPIED,
                    bay.getState());

            assertEquals(
                    "T001",
                    bay.getTransportId());

            assertEquals(
                    TransportState.DOCKED,
                    first.getState());

            assertEquals(
                    "BAY_01",
                    first.getBayId());
        }
    }
    @Test
    void departureMonitorMovesReadyTransportToDeparted()
            throws Exception {

        Transport transport =
                new Transport(
                        "T001",
                        100,
                        "2000-01-01T00:00");

        TransportRegistry localRegistry =
                new TransportRegistry();

        localRegistry.add(transport);

        DockingBay localBay =
                new DockingBay("BAY_01");

        MaintenanceTeam localTeam =
                new MaintenanceTeam("TEAM_A");

        List<String> events =
                Collections.synchronizedList(
                        new ArrayList<>());

        ResourceService localService =
                new ResourceService(
                        List.of(localBay),
                        List.of(localTeam),
                        localRegistry,
                        events::add,
                        message -> {});

        localService.allocateBay(
                "T001",
                "BAY_01");

        localService.assignTeam(
                "T001",
                "TEAM_A");

        localService.completeMaintenance(
                "T001");

        DepartureMonitor monitor =
                new DepartureMonitor(localService);

        Thread thread =
                new Thread(monitor);

        thread.start();

        long deadline =
                System.currentTimeMillis() + 5000;

        while (transport.getState()
                != TransportState.DEPARTED
                && System.currentTimeMillis() < deadline) {

            Thread.sleep(50);
        }

        thread.interrupt();
        thread.join(2000);

        assertEquals(
                TransportState.DEPARTED,
                transport.getState());

        assertEquals(
                BayState.AVAILABLE,
                localBay.getState());

        assertEquals(
                null,
                transport.getBayId());

        assertTrue(
                events.stream()
                        .anyMatch(event ->
                                event.contains("Departed T001")));
    }
}