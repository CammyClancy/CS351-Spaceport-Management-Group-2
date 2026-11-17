package resources;

import java.util.List;

import org.junit.jupiter.api.Test;

import communication.network.ClientInfo;
import communication.protocol.CommandType;
import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;
import communication.protocol.Role;
import transport.Transport;
import transport.TransportRegistry;
import transport.TransportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCommandHandlerTest {
    private final TransportRegistry registry = new TransportRegistry();
    private final ClientInfo client = new ClientInfo(1, "Admin1", Role.ADMIN, "localhost");
    private final ResourceCommandHandler handler;

    ResourceCommandHandlerTest() {
        registry.add(new Transport("T001", 100, "2026-11-10T14:00"));
        registry.add(new Transport("T002", 80, "2026-11-10T15:00"));

        SpaceportEventLog log = new SpaceportEventLog();
        TransportService cargo = new TransportService(registry, log);
        DockingBay secondBay = new DockingBay("BAY_02");
        DockingBay firstBay = new DockingBay("BAY_01");
        ResourceService resources = new ResourceService(List.of(firstBay, secondBay),
                List.of(new MaintenanceTeam("TEAM_A")), registry, cargo, log);
        handler = new ResourceCommandHandler(resources, log);
    }

    private Response send(CommandType type, String... arguments) throws ProtocolException {
        return handler.handle(new Request(type, arguments), client);
    }

    @Test
    void allocateBay() throws ProtocolException {
        Response first = send(CommandType.ALLOCATE_BAY, "T001", "BAY_01");
        assertTrue(first.isSuccess());
        assertEquals("Allocated BAY_01 to T001", first.getMessage());

        Response second = send(CommandType.ALLOCATE_BAY, "T002", "BAY_01");
        assertFalse(second.isSuccess());
        assertEquals("Bay is unavailable: BAY_01", second.getMessage());
    }

    @Test
    void addTransport() throws ProtocolException {
        Response reply = send(CommandType.ADD_TRANSPORT, "T003", "120", "2026-11-10T16:00");
        assertTrue(reply.isSuccess());
        assertEquals("Added transport T003", reply.getMessage());

        Transport added = registry.get("T003");
        assertNotNull(added);
        assertEquals(120, added.getCapacity());
        assertEquals("2026-11-10T16:00", added.getDepartureTime());
    }

    @Test
    void closeAndReopen() throws ProtocolException {
        Response closed = send(CommandType.CLOSE_BAY, "BAY_01");
        assertTrue(closed.isSuccess());
        Response bays = send(CommandType.VIEW_BAYS);
        assertTrue(bays.isSuccess());
        assertEquals("BAY_01 CLOSED, BAY_02 AVAILABLE", bays.getMessage());

        Response reopened = send(CommandType.REOPEN_BAY, "BAY_01");
        assertTrue(reopened.isSuccess());
        bays = send(CommandType.VIEW_BAYS);
        assertTrue(bays.isSuccess());
        assertEquals("BAY_01 AVAILABLE, BAY_02 AVAILABLE", bays.getMessage());
    }

    @Test
    void teamCommands() throws ProtocolException {
        send(CommandType.ALLOCATE_BAY, "T001", "BAY_01");

        Response assigned = send(CommandType.ASSIGN_TEAM, "T001", "TEAM_A");
        assertTrue(assigned.isSuccess());
        assertEquals("Assigned TEAM_A to T001", assigned.getMessage());

        Response teams = send(CommandType.VIEW_TEAMS);
        assertTrue(teams.isSuccess());
        assertEquals("TEAM_A BUSY (T001)", teams.getMessage());

        Response completed = send(CommandType.COMPLETE_MAINTENANCE, "T001");
        assertTrue(completed.isSuccess());
        assertEquals("Completed maintenance on T001 and released TEAM_A",
                completed.getMessage());

        teams = send(CommandType.VIEW_TEAMS);
        assertTrue(teams.isSuccess());
        assertEquals("TEAM_A AVAILABLE", teams.getMessage());
    }

    @Test
    void swapAndViewLog() throws ProtocolException {
        send(CommandType.ALLOCATE_BAY, "T001", "BAY_01");
        send(CommandType.ALLOCATE_BAY, "T002", "BAY_02");

        Response swap = send(CommandType.EXCHANGE_BAYS, "T001", "T002");
        assertTrue(swap.isSuccess());
        assertEquals("Exchanged BAY_01 and BAY_02 for T001 and T002",
                swap.getMessage());

        Response bays = send(CommandType.VIEW_BAYS);
        assertTrue(bays.isSuccess());
        assertEquals("BAY_01 OCCUPIED (T002), BAY_02 OCCUPIED (T001)",
                bays.getMessage());

        Response history = send(CommandType.VIEW_LOG);
        assertTrue(history.isSuccess());

        // Two allocations followed by one exchange.
        List<String> entries = history.getLines();
        assertEquals(3, entries.size());
        assertTrue(entries.get(2).endsWith(" " + swap.getMessage()));
    }

}
