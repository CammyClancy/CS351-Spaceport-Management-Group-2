package communication.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

class FakeServer {
    private final ServerSocket serverSocket = new ServerSocket(0);
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    FakeServer() throws IOException {
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    void acceptClient() throws IOException {
        socket = serverSocket.accept();
        socket.setSoTimeout(5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    String readLine() throws IOException {
        return in.readLine();
    }

    void send(String... lines) {
        for (String line : lines) {
            out.print(line + "\n");
        }
        out.flush();
    }

    void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
        serverSocket.close();
    }
}
