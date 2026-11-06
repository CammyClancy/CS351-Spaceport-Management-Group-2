package communication.client;

import communication.protocol.CommandType;
import communication.protocol.Protocol;
import communication.protocol.Request;
import communication.protocol.Response;
import communication.protocol.Role;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ClientApp {
    public static void main(String[] args) {
        boolean admin = args.length > 0 && args[0].equalsIgnoreCase("--admin");
        Scanner scanner = new Scanner(System.in);
        Console console = new Console(System.out);
        NetworkClient client = new NetworkClient(console::alert);

        console.println("Port Wander " + (admin ? "Admin" : "Client") + " Terminal");
        console.prompt("Enter server IP (default '" + Protocol.DEFAULT_HOST + "'): ");
        String serverIP = scanner.nextLine().trim();
        if (serverIP.isEmpty()) {
            serverIP = Protocol.DEFAULT_HOST;
        }

        try {
            client.connect(serverIP, Protocol.DEFAULT_PORT);
        } catch (IOException exception) {
            console.println("Could not connect to the server: " + exception.getMessage());
            return;
        }
        console.println("Connected to Port Wander Server.");

        try {
            if (signIn(scanner, console, client, admin ? Role.ADMIN : Role.OPERATOR)) {
                new ClientConsole(scanner, console, client, admin).run();
            }
        } finally {
            client.disconnect();
            console.println("Disconnected from Port Wander Server.");
        }
    }

    private static boolean signIn(Scanner scanner, Console console, NetworkClient client, Role role) {
        try {
            while (true) {
                console.prompt("Enter terminal name: ");
                String name = scanner.nextLine().trim();
                if (!name.matches("[A-Za-z0-9_-]+")) {
                    console.println("Use letters, numbers, _ or - only.");
                    continue;
                }
                Response response = client.send(new Request(CommandType.HELLO, name, role.name()));
                console.println(response.getMessage());
                if (response.isSuccess()) {
                    return true;
                }
            }
        } catch (IOException exception) {
            console.println("Lost connection to the server: " + exception.getMessage());
            return false;
        } catch (NoSuchElementException exception) {
            return false;
        }
    }
}
