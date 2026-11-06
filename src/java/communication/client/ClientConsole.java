package communication.client;

import communication.protocol.CommandType;
import communication.protocol.Request;
import communication.protocol.Response;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;

public class ClientConsole {
    private final Scanner scanner;
    private final Console console;
    private final CommandSender sender;
    private final boolean admin;

    public ClientConsole(Scanner scanner, Console console, CommandSender sender, boolean admin) {
        this.scanner = scanner;
        this.console = console;
        this.sender = sender;
        this.admin = admin;
    }

    public void run() {
        try {
            while (true) {
                printMenu();
                String choice = ask("Enter choice: ");
                if (choice.equals("0")) {
                    return;
                }

                Request request;
                try {
                    request = buildRequest(choice);
                } catch (IllegalArgumentException exception) {
                    console.println("Invalid input: " + exception.getMessage());
                    continue;
                }
                if (Objects.isNull(request)) {
                    console.println("Invalid option. Please try again.");
                    continue;
                }

                try {
                    printResponse(sender.send(request));
                } catch (IOException exception) {
                    console.println("Lost connection to the server: " + exception.getMessage());
                    return;
                }
            }
        } catch (NoSuchElementException exception) {
        }
    }

    private Request buildRequest(String choice) {
        switch (choice) {
            case "1":
                return new Request(CommandType.VIEW_ALL);
            case "2":
                return new Request(CommandType.VIEW_TRANSPORTS);
            case "3":
                return new Request(CommandType.VIEW_BAYS);
            case "4":
                return new Request(CommandType.VIEW_TEAMS);
            case "5":
                return new Request(CommandType.VIEW_CARGO);
            case "6":
                return new Request(CommandType.VIEW_LOG);
            case "7":
                return new Request(CommandType.ALLOCATE_BAY, askId("Transport ID (e.g. T001): "), askId("Bay ID (e.g. BAY_01): "));
            case "8":
                return new Request(CommandType.EXCHANGE_BAYS, askId("First transport ID: "), askId("Second transport ID: "));
            case "9":
                return new Request(CommandType.ASSIGN_TEAM, askId("Transport ID: "), askId("Maintenance team ID: "));
            case "10":
                return new Request(CommandType.COMPLETE_MAINTENANCE, askId("Transport ID: "));
            case "11":
                return new Request(CommandType.LOAD_CARGO, askId("Transport ID: "), askAmount("Cargo units to load: "));
            case "12":
                return new Request(CommandType.UNLOAD_CARGO, askId("Transport ID: "), askAmount("Cargo units to unload: "));
            case "13":
                return new Request(CommandType.TRANSFER_CARGO, askId("From transport ID: "), askId("To transport ID: "), askAmount("Cargo units to transfer: "));
        }
        if (!admin) {
            return null;
        }
        switch (choice) {
            case "20":
                return new Request(CommandType.ADD_TRANSPORT, askId("New transport ID: "), askAmount("Cargo capacity: "), askTime("Departure time (e.g. 2026-11-03T14:30): "));
            case "21":
                return new Request(CommandType.CLOSE_BAY, askId("Bay ID: "));
            case "22":
                return new Request(CommandType.REOPEN_BAY, askId("Bay ID: "));
            case "23":
                return new Request(CommandType.VIEW_CLIENTS);
            default:
                return null;
        }
    }

    private void printResponse(Response response) {
        console.println((response.isSuccess() ? "OK: " : "Error: ") + response.getMessage());
        for (String line : response.getLines()) {
            console.println("  " + line);
        }
    }

    private String ask(String label) {
        console.prompt(label);
        return scanner.nextLine().trim();
    }

    private String askId(String label) {
        String id = ask(label);
        if (!id.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("'" + id + "' is not a valid ID (letters, numbers, _ or - only)");
        }
        return id;
    }

    private String askAmount(String label) {
        String amount = ask(label);
        try {
            if (Integer.parseInt(amount) > 0) {
                return amount;
            }
        } catch (NumberFormatException exception) {
        }
        throw new IllegalArgumentException("'" + amount + "' is not a whole number above zero");
    }

    private String askTime(String label) {
        String time = ask(label);
        try {
            LocalDateTime.parse(time);
            return time;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("'" + time + "' is not a date and time like 2026-11-03T14:30");
        }
    }

    private void printMenu() {
        console.println("");
        console.println("Port Wander Menu");
        console.println(" 1. View all status            8. Exchange docking bays");
        console.println(" 2. View transports            9. Assign maintenance team");
        console.println(" 3. View docking bays         10. Complete maintenance");
        console.println(" 4. View maintenance teams    11. Load cargo");
        console.println(" 5. View cargo                12. Unload cargo");
        console.println(" 6. View event log            13. Transfer cargo");
        console.println(" 7. Allocate docking bay");
        if (admin) {
            console.println("--- Admin ---");
            console.println("20. Add transport             22. Reopen docking bay");
            console.println("21. Close docking bay         23. View connected clients");
        }
        console.println(" 0. Exit");
    }
}
