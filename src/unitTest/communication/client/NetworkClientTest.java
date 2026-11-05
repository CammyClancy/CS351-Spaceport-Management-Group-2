package communication.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import communication.protocol.CommandType;
import communication.protocol.Request;
import communication.protocol.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NetworkClientTest {
    private final List<String> notifications = new CopyOnWriteArrayList<>();
    private final ExecutorService background = Executors.newCachedThreadPool();
    private FakeServer server;
    private NetworkClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeServer();
        client = new NetworkClient(notifications::add);
        client.connect("localhost", server.getPort());
        server.acceptClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.disconnect();
        server.close();
        background.shutdownNow();
    }

    @Test
    void sendsTheCommandAndReturnsTheResponse() throws Exception {
        Future<Response> reply = sendInBackground(new Request(CommandType.LOAD_CARGO, "T001", "5"));

        assertEquals("LOAD_CARGO T001 5", server.readLine());
        server.send("OK Loaded 5 units", "END");

        Response response = reply.get(5, TimeUnit.SECONDS);
        assertTrue(response.isSuccess());
        assertEquals("Loaded 5 units", response.getMessage());
    }

    @Test
    void returnsMultiLineResponsesInFull() throws Exception {
        Future<Response> reply = sendInBackground(new Request(CommandType.VIEW_TRANSPORTS));
        server.readLine();
        server.send("OK 2 transports", "DATA T001 DOCKED", "DATA T002 READY", "END");

        assertEquals(List.of("T001 DOCKED", "T002 READY"), reply.get(5, TimeUnit.SECONDS).getLines());
    }

    @Test
    void aNotificationArrivingBeforeTheResponseDoesNotGetMixedUpWithIt() throws Exception {
        Future<Response> reply = sendInBackground(new Request(CommandType.VIEW_ALL));
        server.readLine();
        server.send("NOTIFY T009 departed", "OK All status", "END");

        assertEquals("All status", reply.get(5, TimeUnit.SECONDS).getMessage());
        assertEquals(List.of("T009 departed"), notifications);
    }

    @Test
    void notificationsArriveWhenNoCommandIsBeingSent() throws Exception {
        server.send("NOTIFY Bay BAY_02 closed");

        waitUntil(() -> notifications.size() == 1);
        assertEquals("Bay BAY_02 closed", notifications.getFirst());
    }

    @Test
    void losingTheConnectionFailsTheWaitingSendAndNotifiesTheUser() throws Exception {
        Future<Response> reply = sendInBackground(new Request(CommandType.VIEW_ALL));
        server.readLine();
        server.close();

        ExecutionException exception = assertThrows(ExecutionException.class, () -> reply.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, exception.getCause());
        waitUntil(() -> notifications.contains(NetworkClient.CONNECTION_LOST));
        assertFalse(client.isConnected());
    }

    @Test
    void timesOutIfTheServerNeverReplies() throws Exception {
        client.disconnect();
        server.close();
        server = new FakeServer();
        client = new NetworkClient(notifications::add, 200);
        client.connect("localhost", server.getPort());
        server.acceptClient();

        assertThrows(IOException.class, () -> client.send(new Request(CommandType.VIEW_ALL)));
        assertFalse(client.isConnected());
    }

    @Test
    void disconnectSendsQuit() throws Exception {
        client.disconnect();

        assertEquals("QUIT", server.readLine());
        assertFalse(client.isConnected());
        assertThrows(IOException.class, () -> client.send(new Request(CommandType.VIEW_ALL)));
    }

    @Test
    void severalThreadsSharingTheClientEachGetTheirOwnResponse() throws Exception {
        background.submit(() -> {
            String line;
            while ((line = server.readLine()) != null && !line.equals("QUIT")) {
                server.send("NOTIFY noise", "OK " + line, "END");
            }
            return null;
        });

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int t = 0; t < 5; t++) {
            String transport = "T00" + t;
            results.add(background.submit(() -> {
                start.await();
                int correct = 0;
                for (int i = 1; i <= 20; i++) {
                    Request request = new Request(CommandType.LOAD_CARGO, transport, String.valueOf(i));
                    if (client.send(request).getMessage().equals(request.toLine())) {
                        correct++;
                    }
                }
                return correct;
            }));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(20, result.get());
            }
        });
    }

    private Future<Response> sendInBackground(Request request) {
        return background.submit(() -> client.send(request));
    }

    static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting");
            }
            Thread.sleep(10);
        }
    }
}
