package integration;

import communication.client.NetworkClient;
import communication.network.MutableCommandHandler;
import communication.network.PortWanderServer;
import communication.network.SpaceportCommandHandler;
import communication.protocol.CommandType;
import communication.protocol.Request;
import communication.protocol.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import resources.DockingBay;
import resources.MaintenanceTeam;
import resources.ResourceCommandHandler;
import resources.ResourceService;
import resources.SpaceportEventLog;
import transport.TransportCommandHandler;
import transport.TransportRegistry;
import transport.TransportService;
import transport.TransportState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceportIntegrationTest {
    private PortWanderServer server;
    private NetworkClient client;

    private final List<NetworkClient> clients =
            new ArrayList<>();

    private TransportRegistry registry;
    private SpaceportEventLog eventLog;

    @BeforeEach
    void setUp() throws IOException {
        MutableCommandHandler mutableHandler =
                new MutableCommandHandler();

        server = new PortWanderServer(
                0,
                20,
                mutableHandler
        );

        registry = new TransportRegistry();
        eventLog = new SpaceportEventLog();

        List<DockingBay> bays = List.of(
                new DockingBay("BAY_01"),
                new DockingBay("BAY_02")
        );

        List<MaintenanceTeam> teams = List.of(
                new MaintenanceTeam("TEAM_A"),
                new MaintenanceTeam("TEAM_B")
        );

        TransportService transportService =
                new TransportService(
                        registry,
                        eventLog
                );

        ResourceService resourceService =
                new ResourceService(
                        bays,
                        teams,
                        registry,
                        eventLog,
                        server.getNotifications()::broadcast
                );

        TransportCommandHandler transportHandler =
                new TransportCommandHandler(
                        transportService,
                        server.getNotifications()
                );

        ResourceCommandHandler resourceHandler =
                new ResourceCommandHandler(
                        resourceService,
                        eventLog
                );

        SpaceportCommandHandler combinedHandler =
                new SpaceportCommandHandler(
                        transportHandler,
                        resourceHandler
                );

        mutableHandler.setDelegate(combinedHandler);

        server.start();

        client = connect(
                "integration-admin",
                "ADMIN"
        );
    }

    @AfterEach
    void tearDown() {
        for (NetworkClient current : clients) {
            current.disconnect();
        }

        if (server != null) {
            server.stop();
        }
    }

    @Test
    void completeTransportWorkflowWorksThroughNetwork()
            throws Exception {

        Response added =
                client.send(new Request(
                        CommandType.ADD_TRANSPORT,
                        "T001",
                        "100",
                        "2099-11-10T14:00"
                ));

        assertTrue(added.isSuccess());

        Response allocated =
                client.send(new Request(
                        CommandType.ALLOCATE_BAY,
                        "T001",
                        "BAY_01"
                ));

        assertTrue(allocated.isSuccess());

        assertEquals(
                TransportState.DOCKED,
                registry.get("T001").getState()
        );

        Response assigned =
                client.send(new Request(
                        CommandType.ASSIGN_TEAM,
                        "T001",
                        "TEAM_A"
                ));

        assertTrue(assigned.isSuccess());

        assertEquals(
                TransportState.SERVICING,
                registry.get("T001").getState()
        );

        Response loaded =
                client.send(new Request(
                        CommandType.LOAD_CARGO,
                        "T001",
                        "25"
                ));

        assertTrue(loaded.isSuccess());

        assertEquals(
                25,
                registry.get("T001").getCargoQuantity()
        );

        Response completed =
                client.send(new Request(
                        CommandType.COMPLETE_MAINTENANCE,
                        "T001"
                ));

        assertTrue(completed.isSuccess());

        assertEquals(
                TransportState.READY,
                registry.get("T001").getState()
        );

        Response status =
                client.send(new Request(
                        CommandType.VIEW_ALL
                ));

        assertTrue(status.isSuccess());

        assertTrue(
                status.getLines()
                        .stream()
                        .anyMatch(line ->
                                line.contains("T001"))
        );
    }

    @Test
    void invalidCargoOperationDoesNotChangeState()
            throws Exception {

        client.send(new Request(
                CommandType.ADD_TRANSPORT,
                "T001",
                "100",
                "2099-11-10T14:00"
        ));

        client.send(new Request(
                CommandType.ALLOCATE_BAY,
                "T001",
                "BAY_01"
        ));

        int eventsBefore =
                eventLog.getEntries().size();

        Response response =
                client.send(new Request(
                        CommandType.LOAD_CARGO,
                        "T001",
                        "1000"
                ));

        assertFalse(response.isSuccess());

        assertEquals(
                0,
                registry.get("T001").getCargoQuantity()
        );

        assertEquals(
                eventsBefore,
                eventLog.getEntries().size()
        );
    }

    @Test
    void twoClientsCannotAllocateTheSameBay()
            throws Exception {

        client.send(new Request(
                CommandType.ADD_TRANSPORT,
                "T001",
                "100",
                "2099-11-10T14:00"
        ));

        client.send(new Request(
                CommandType.ADD_TRANSPORT,
                "T002",
                "100",
                "2099-11-10T15:00"
        ));

        NetworkClient secondClient =
                connect(
                        "second-client",
                        "OPERATOR"
                );

        Response first =
                client.send(new Request(
                        CommandType.ALLOCATE_BAY,
                        "T001",
                        "BAY_01"
                ));

        Response second =
                secondClient.send(new Request(
                        CommandType.ALLOCATE_BAY,
                        "T002",
                        "BAY_01"
                ));

        assertTrue(
                first.isSuccess()
                        ^ second.isSuccess()
        );

        String firstBay =
                registry.get("T001").getBayId();

        String secondBay =
                registry.get("T002").getBayId();

        boolean firstTransportWon =
                "BAY_01".equals(firstBay)
                        && secondBay == null;

        boolean secondTransportWon =
                "BAY_01".equals(secondBay)
                        && firstBay == null;

        assertTrue(
                firstTransportWon
                        || secondTransportWon
        );
    }

    @Test
    void failedTransferIsAtomic()
            throws Exception {

        client.send(new Request(
                CommandType.ADD_TRANSPORT,
                "T001",
                "100",
                "2099-11-10T14:00"
        ));

        client.send(new Request(
                CommandType.ADD_TRANSPORT,
                "T002",
                "10",
                "2099-11-10T15:00"
        ));

        client.send(new Request(
                CommandType.ALLOCATE_BAY,
                "T001",
                "BAY_01"
        ));

        client.send(new Request(
                CommandType.ALLOCATE_BAY,
                "T002",
                "BAY_02"
        ));

        client.send(new Request(
                CommandType.LOAD_CARGO,
                "T001",
                "50"
        ));

        client.send(new Request(
                CommandType.LOAD_CARGO,
                "T002",
                "10"
        ));

        int sourceBefore =
                registry.get("T001").getCargoQuantity();

        int destinationBefore =
                registry.get("T002").getCargoQuantity();

        int eventCountBefore =
                eventLog.getEntries().size();

        Response response =
                client.send(new Request(
                        CommandType.TRANSFER_CARGO,
                        "T001",
                        "T002",
                        "5"
                ));

        assertFalse(response.isSuccess());

        assertEquals(
                sourceBefore,
                registry.get("T001").getCargoQuantity()
        );

        assertEquals(
                destinationBefore,
                registry.get("T002").getCargoQuantity()
        );

        assertEquals(
                eventCountBefore,
                eventLog.getEntries().size()
        );
    }

    @Test
    void allSuccessfulStateChangesCreateOneEvent()
            throws Exception {

        client.send(new Request(
                CommandType.ADD_TRANSPORT,
                "T001",
                "100",
                "2099-11-10T14:00"
        ));

        client.send(new Request(
                CommandType.ALLOCATE_BAY,
                "T001",
                "BAY_01"
        ));

        client.send(new Request(
                CommandType.ASSIGN_TEAM,
                "T001",
                "TEAM_A"
        ));

        client.send(new Request(
                CommandType.LOAD_CARGO,
                "T001",
                "10"
        ));

        client.send(new Request(
                CommandType.COMPLETE_MAINTENANCE,
                "T001"
        ));

        assertEquals(
                5,
                eventLog.getEntries().size()
        );
    }

    @Test
    void secondClientReceivesSuccessfulOperationNotification()
            throws Exception {

        List<String> notifications =
                new CopyOnWriteArrayList<>();

        NetworkClient secondClient =
                connect(
                        "observer",
                        "OPERATOR",
                        notifications
                );

        Response added =
                client.send(new Request(
                        CommandType.ADD_TRANSPORT,
                        "T001",
                        "100",
                        "2099-11-10T14:00"
                ));

        assertTrue(added.isSuccess());

        long deadline =
                System.currentTimeMillis() + 5000;

        while (
                notifications.stream()
                        .noneMatch(message ->
                                message.contains(
                                        "Added transport T001"))
                        && System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20);
        }

        assertTrue(
                notifications.stream()
                        .anyMatch(message ->
                                message.contains(
                                        "Added transport T001"))
        );
    }

    private NetworkClient connect(
            String name,
            String role)
            throws IOException {

        List<String> notifications =
                new CopyOnWriteArrayList<>();

        return connect(
                name,
                role,
                notifications
        );
    }

    private NetworkClient connect(
            String name,
            String role,
            List<String> notifications)
            throws IOException {

        NetworkClient result =
                new NetworkClient(
                        notifications::add
                );

        result.connect(
                "localhost",
                server.getPort()
        );

        clients.add(result);

        try {
            Response response =
                    result.send(new Request(
                            CommandType.HELLO,
                            name,
                            role
                    ));

            assertTrue(response.isSuccess());

            return result;
        } catch (Exception exception) {
            result.disconnect();
            throw exception;
        }
    }
}