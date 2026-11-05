package communication.network;

import communication.protocol.CommandType;
import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;
import communication.protocol.Role;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class ClientHandler implements Runnable {
    private final int id;
    private final Socket socket;
    private final CommandHandler commandHandler;
    private final ClientRegistry registry;

    private volatile PrintWriter out;
    private volatile ClientInfo info;
    private volatile boolean closed;
    private boolean quit;

    public ClientHandler(int id, Socket socket, CommandHandler commandHandler, ClientRegistry registry) {
        this.id = id;
        this.socket = socket;
        this.commandHandler = commandHandler;
        this.registry = registry;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String line;
            while (!quit && Objects.nonNull(line = in.readLine())) {
                sendLines(process(line).toProtocolLines());
            }
        } catch (IOException exception) {
        } finally {
            close();
        }
    }

    private Response process(String line) {
        Request request;
        try {
            request = Request.parse(line);
        } catch (ProtocolException exception) {
            return Response.error(exception.getMessage());
        }

        CommandType type = request.getType();
        if (type == CommandType.QUIT) {
            quit = true;
            return Response.ok("Goodbye");
        }
        if (type == CommandType.HELLO) {
            return signIn(request);
        }
        if (Objects.isNull(info)) {
            return Response.error("Send HELLO <name> <role> first");
        }
        if (type.isAdminOnly() && info.role() != Role.ADMIN) {
            return Response.error("Permission denied: " + type + " is for admin terminals only");
        }
        if (type == CommandType.VIEW_CLIENTS) {
            List<String> clients = registry.getSignedInClients().stream().map(ClientInfo::toString).toList();
            return Response.ok(clients.size() + " client(s) connected", clients);
        }

        try {
            return commandHandler.handle(request, info);
        } catch (ProtocolException exception) {
            return Response.error(exception.getMessage());
        } catch (RuntimeException exception) {
            System.err.println("Error handling " + request + ": " + exception);
            return Response.error("Server error while handling " + type);
        }
    }

    private Response signIn(Request request) {
        if (Objects.nonNull(info)) {
            return Response.error("Already signed in as " + info.name());
        }
        Role role;
        try {
            role = Role.valueOf(request.getArgument(1).toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Response.error("Role must be OPERATOR or ADMIN");
        }
        ClientInfo newInfo = new ClientInfo(id, request.getArgument(0), role, String.valueOf(socket.getRemoteSocketAddress()));
        if (!registry.signIn(this, newInfo)) {
            return Response.error("The name " + newInfo.name() + " is already in use");
        }
        return Response.ok("Welcome " + newInfo.name() + ", signed in as " + role);
    }

    public synchronized boolean sendLines(List<String> lines) {
        if (closed || Objects.isNull(out)) {
            return false;
        }
        for (String line : lines) {
            out.print(line + "\n");
        }
        out.flush();
        return !out.checkError();
    }

    public ClientInfo getInfo() {
        return info;
    }

    void setInfo(ClientInfo info) {
        this.info = info;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        registry.remove(this);
        try {
            socket.close();
        } catch (IOException exception) {
        }
    }
}
