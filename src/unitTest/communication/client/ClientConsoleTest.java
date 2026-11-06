package communication.client;

import org.junit.jupiter.api.Test;
import communication.protocol.CommandType;
import communication.protocol.Request;
import communication.protocol.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConsoleTest {
    private final List<Request> sent = new ArrayList<>();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private Response reply = Response.ok("done");

    @Test
    void eachMenuOptionSendsTheRightCommand() {
        runMenu(false,
                "1",
                "7", "T001", "BAY_01",
                "8", "T001", "T002",
                "9", "T001", "TEAM_A",
                "10", "T001",
                "11", "T001", "40",
                "12", "T001", "15",
                "13", "T001", "T002", "10",
                "0");

        assertEquals(List.of(
                new Request(CommandType.VIEW_ALL),
                new Request(CommandType.ALLOCATE_BAY, "T001", "BAY_01"),
                new Request(CommandType.EXCHANGE_BAYS, "T001", "T002"),
                new Request(CommandType.ASSIGN_TEAM, "T001", "TEAM_A"),
                new Request(CommandType.COMPLETE_MAINTENANCE, "T001"),
                new Request(CommandType.LOAD_CARGO, "T001", "40"),
                new Request(CommandType.UNLOAD_CARGO, "T001", "15"),
                new Request(CommandType.TRANSFER_CARGO, "T001", "T002", "10")), sent);
    }

    @Test
    void badInputIsRejectedBeforeAnythingIsSent() {
        runMenu(false, "11", "T001", "lots", "7", "T 001", "0");

        assertTrue(sent.isEmpty());
        assertTrue(output().contains("Invalid input"));
    }

    @Test
    void operatorsCannotUseAdminOptions() {
        runMenu(false, "21", "0");

        assertTrue(sent.isEmpty());
        assertTrue(output().contains("Invalid option"));
        assertFalse(output().contains("Close docking bay"));
    }

    @Test
    void adminsCanUseAdminOptions() {
        runMenu(true, "20", "T009", "500", "2026-11-03T14:30", "21", "BAY_03", "23", "0");

        assertEquals(List.of(
                new Request(CommandType.ADD_TRANSPORT, "T009", "500", "2026-11-03T14:30"),
                new Request(CommandType.CLOSE_BAY, "BAY_03"),
                new Request(CommandType.VIEW_CLIENTS)), sent);
    }

    @Test
    void printsTheResponse() {
        reply = Response.ok("2 transports", List.of("T001 DOCKED", "T002 READY"));
        runMenu(false, "2", "0");

        assertTrue(output().contains("OK: 2 transports"));
        assertTrue(output().contains("T002 READY"));
    }

    @Test
    void printsErrorsFromTheServer() {
        reply = Response.error("Bay BAY_01 is closed");
        runMenu(false, "7", "T001", "BAY_01", "0");

        assertTrue(output().contains("Error: Bay BAY_01 is closed"));
    }

    @Test
    void stopsWhenTheInputEnds() {
        runMenu(false, "7", "T001");

        assertTrue(sent.isEmpty());
    }

    @Test
    void stopsWhenTheConnectionIsLost() {
        CommandSender broken = request -> {
            sent.add(request);
            throw new IOException("Connection reset");
        };
        new ClientConsole(input("1", "1", "1"), new Console(new PrintStream(output)), broken, false).run();

        assertEquals(1, sent.size());
        assertTrue(output().contains("Lost connection"));
    }

    private void runMenu(boolean admin, String... lines) {
        CommandSender fakeServer = request -> {
            sent.add(request);
            return reply;
        };
        new ClientConsole(input(lines), new Console(new PrintStream(output)), fakeServer, admin).run();
    }

    private static Scanner input(String... lines) {
        return new Scanner(String.join("\n", lines) + "\n");
    }

    private String output() {
        return output.toString();
    }
}
