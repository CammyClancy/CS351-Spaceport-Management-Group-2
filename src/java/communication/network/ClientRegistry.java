package communication.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class ClientRegistry {
    private final List<ClientHandler> connections = new ArrayList<>();

    public synchronized void add(ClientHandler handler) {
        connections.add(handler);
    }

    public synchronized void remove(ClientHandler handler) {
        connections.remove(handler);
    }

    public synchronized boolean signIn(ClientHandler handler, ClientInfo info) {
        for (ClientHandler other : connections) {
            ClientInfo otherInfo = other.getInfo();
            if (Objects.nonNull(otherInfo) && otherInfo.name().equalsIgnoreCase(info.name())) {
                return false;
            }
        }
        handler.setInfo(info);
        return true;
    }

    public synchronized List<ClientInfo> getSignedInClients() {
        List<ClientInfo> clients = new ArrayList<>();
        for (ClientHandler handler : connections) {
            if (Objects.nonNull(handler.getInfo())) {
                clients.add(handler.getInfo());
            }
        }
        return clients;
    }

    public synchronized List<ClientHandler> getSignedInHandlers() {
        List<ClientHandler> handlers = new ArrayList<>();
        for (ClientHandler handler : connections) {
            if (Objects.nonNull(handler.getInfo())) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    public synchronized List<ClientHandler> getAllConnections() {
        return new ArrayList<>(connections);
    }

    public synchronized int getConnectionCount() {
        return connections.size();
    }
}
