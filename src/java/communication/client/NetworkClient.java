package communication.client;

import communication.protocol.Protocol;
import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NetworkClient implements CommandSender {
    public static final String CONNECTION_LOST = "Lost connection to the server";

    private final Consumer<String> notificationHandler;
    private final long timeoutMillis;
    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();

    private Socket socket;
    private PrintWriter out;
    private volatile boolean connected;
    private volatile boolean disconnecting;

    public NetworkClient(Consumer<String> notificationHandler) {
        this(notificationHandler, 10_000);
    }

    public NetworkClient(Consumer<String> notificationHandler, long timeoutMillis) {
        this.notificationHandler = notificationHandler;
        this.timeoutMillis = timeoutMillis;
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        responses.clear();
        connected = true;
        disconnecting = false;

        Thread listenerThread = new Thread(() -> listen(in), "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized Response send(Request request) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to the server");
        }
        out.println(request.toLine());
        if (out.checkError()) {
            disconnect();
            throw new IOException(CONNECTION_LOST);
        }

        Response response;
        try {
            response = responses.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the server");
        }
        if (Objects.isNull(response)) {
            disconnect();
            throw new IOException("The server did not respond in time");
        }
        if (!connected && response.getMessage().equals(CONNECTION_LOST)) {
            throw new IOException(CONNECTION_LOST);
        }
        return response;
    }

    public void disconnect() {
        disconnecting = true;
        if (connected) {
            out.println("QUIT");
        }
        connected = false;
        try {
            if (Objects.nonNull(socket)) {
                socket.close();
            }
        } catch (IOException exception) {
        }
    }

    private void listen(BufferedReader in) {
        List<String> currentResponse = new ArrayList<>();
        try {
            String line;
            while (Objects.nonNull(line = in.readLine())) {
                if (line.startsWith(Protocol.NOTIFY + " ")) {
                    showNotification(line.substring(Protocol.NOTIFY.length() + 1));
                } else if (line.equals(Protocol.END)) {
                    responses.add(toResponse(currentResponse));
                    currentResponse = new ArrayList<>();
                } else {
                    currentResponse.add(line);
                }
            }
        } catch (IOException exception) {
        }

        connected = false;
        responses.add(Response.error(CONNECTION_LOST));
        if (!disconnecting) {
            showNotification(CONNECTION_LOST);
        }
    }

    private Response toResponse(List<String> lines) {
        try {
            return Response.fromProtocolLines(lines);
        } catch (ProtocolException exception) {
            return Response.error("Bad response from server: " + exception.getMessage());
        }
    }

    private void showNotification(String text) {
        try {
            notificationHandler.accept(text);
        } catch (RuntimeException exception) {
            System.err.println("Could not show notification: " + exception);
        }
    }
}
