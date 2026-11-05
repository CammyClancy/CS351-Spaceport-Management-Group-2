package communication.network;

import communication.protocol.Protocol;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;


public class NotificationService {
    private final ClientRegistry registry;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    public NotificationService(ClientRegistry registry) {
        this.registry = registry;
    }

    public void broadcast(String message) {
        String line = Protocol.NOTIFY + " " + Protocol.oneLine(message);
        try {
            sender.execute(() -> {
                for (ClientHandler handler : registry.getSignedInHandlers()) {
                    handler.sendLines(List.of(line));
                }
            });
        } catch (RejectedExecutionException exception) {
        }
    }

    public void shutdown() {
        sender.shutdownNow();
    }
}
