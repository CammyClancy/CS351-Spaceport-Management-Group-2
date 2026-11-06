package communication.network;

import communication.client.NetworkClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import communication.protocol.CommandType;
import communication.protocol.Request;
import communication.protocol.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCommunicationTest {
    private final AtomicInteger handlerCalls = new AtomicInteger();
    private final List<NetworkClient> clients = new CopyOnWriteArrayList<>();
    private final ExecutorService background = Executors.newCachedThreadPool();
    private volatile CommandHandler fakeLogic = (request, client) -> Response.ok(request.toLine());
    private PortWanderServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new PortWanderServer(0, 40, (request, client) -> {
            handlerCalls.incrementAndGet();
            return fakeLogic.handle(request, client);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        for (NetworkClient client : clients) {
            client.disconnect();
        }
        server.stop();
        background.shutdownNow();
    }

    @Test
    void mustSayHelloBeforeAnyOtherCommand() throws Exception {
        Response response = connect(new ArrayList<>()).send(new Request(CommandType.VIEW_ALL));

        assertFalse(response.isSuccess());
        assertEquals(0, handlerCalls.get());
    }

    @Test
    void adminCanSeeEveryConnectedClient() throws Exception {
        signIn("dock_1", "OPERATOR", new ArrayList<>());
        NetworkClient admin = signIn("admin", "ADMIN", new ArrayList<>());

        Response response = admin.send(new Request(CommandType.VIEW_CLIENTS));

        assertEquals(2, response.getLines().size());
        assertTrue(response.getLines().get(0).contains("dock_1 (OPERATOR)"));
        assertTrue(response.getLines().get(1).contains("admin (ADMIN)"));
    }

    @Test
    void twoTerminalsCannotUseTheSameName() throws Exception {
        signIn("dock_1", "OPERATOR", new ArrayList<>());

        Response response = connect(new ArrayList<>()).send(new Request(CommandType.HELLO, "DOCK_1", "OPERATOR"));

        assertFalse(response.isSuccess());
    }

    @Test
    void onlyOneOfManyTerminalsSigningInWithTheSameNameAtOnceSucceeds() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            NetworkClient client = connect(new ArrayList<>());
            results.add(background.submit(() -> {
                start.await();
                return client.send(new Request(CommandType.HELLO, "same_name", "OPERATOR")).isSuccess();
            }));
        }
        start.countDown();

        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get(10, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        assertEquals(1, successes);
    }

    @Test
    void operatorsCannotRunAdminCommands() throws Exception {
        NetworkClient operator = signIn("dock_1", "OPERATOR", new ArrayList<>());

        Response response = operator.send(new Request(CommandType.CLOSE_BAY, "BAY_01"));

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Permission denied"));
        assertEquals(0, handlerCalls.get());
    }

    @Test
    void adminCommandsArePassedToTheServerLogic() throws Exception {
        NetworkClient admin = signIn("admin", "ADMIN", new ArrayList<>());

        assertEquals("CLOSE_BAY BAY_01", admin.send(new Request(CommandType.CLOSE_BAY, "BAY_01")).getMessage());
        assertEquals(1, handlerCalls.get());
    }

    @Test
    void anErrorInTheServerLogicIsSentBackAndTheConnectionKeepsWorking() throws Exception {
        NetworkClient client = signIn("dock_1", "OPERATOR", new ArrayList<>());
        fakeLogic = (request, info) -> {
            throw new IllegalStateException("bug");
        };
        assertFalse(client.send(new Request(CommandType.VIEW_ALL)).isSuccess());

        fakeLogic = (request, info) -> Response.ok("working again");
        assertEquals("working again", client.send(new Request(CommandType.VIEW_ALL)).getMessage());
    }

    @Test
    void badNumbersAreRejectedWithAnError() throws Exception {
        fakeLogic = (request, info) -> Response.ok("Loaded " + request.getPositiveNumber(1));
        NetworkClient client = signIn("dock_1", "OPERATOR", new ArrayList<>());

        assertTrue(client.send(new Request(CommandType.LOAD_CARGO, "T001", "5")).isSuccess());
        assertFalse(client.send(new Request(CommandType.LOAD_CARGO, "T001", "-5")).isSuccess());
    }

    @Test
    void terminalsAreRemovedWhenTheyLeave() throws Exception {
        NetworkClient first = signIn("dock_1", "OPERATOR", new ArrayList<>());
        NetworkClient second = signIn("dock_2", "OPERATOR", new ArrayList<>());
        assertEquals(2, server.getRegistry().getConnectionCount());

        first.disconnect();
        second.disconnect();

        waitUntil(() -> server.getRegistry().getConnectionCount() == 0);
    }

    @Test
    void manyTerminalsCanSendCommandsAtTheSameTime() throws Exception {
        int terminals = 20;
        int commandsEach = 25;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int t = 0; t < terminals; t++) {
            NetworkClient client = signIn("terminal_" + t, "OPERATOR", new ArrayList<>());
            String transport = "T" + t;
            results.add(background.submit(() -> {
                start.await();
                int correct = 0;
                for (int i = 1; i <= commandsEach; i++) {
                    Request request = new Request(CommandType.LOAD_CARGO, transport, String.valueOf(i));
                    if (client.send(request).getMessage().equals(request.toLine())) {
                        correct++;
                    }
                }
                return correct;
            }));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(commandsEach, result.get());
            }
        });
        assertEquals(terminals * commandsEach, handlerCalls.get());
    }

    @Test
    void everyTerminalGetsEveryNotificationExactlyOnce() throws Exception {
        List<List<String>> inboxes = new ArrayList<>();
        for (int t = 0; t < 5; t++) {
            List<String> inbox = new CopyOnWriteArrayList<>();
            inboxes.add(inbox);
            signIn("terminal_" + t, "OPERATOR", inbox);
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> broadcasters = new ArrayList<>();
        for (int b = 0; b < 3; b++) {
            int source = b;
            broadcasters.add(background.submit(() -> {
                start.await();
                for (int i = 0; i < 50; i++) {
                    server.getNotifications().broadcast("event " + source + "-" + i);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> broadcaster : broadcasters) {
            broadcaster.get(10, TimeUnit.SECONDS);
        }

        for (List<String> inbox : inboxes) {
            waitUntil(() -> inbox.size() >= 150);
        }
        Thread.sleep(200);
        for (List<String> inbox : inboxes) {
            assertEquals(150, inbox.size());
            assertEquals(150, new HashSet<>(inbox).size());
        }
    }

    @Test
    void notificationsNeverEndUpInsideAResponse() throws Exception {
        List<String> table = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            table.add("row " + i);
        }
        fakeLogic = (request, info) -> Response.ok("table", table);
        List<String> inbox = new CopyOnWriteArrayList<>();
        NetworkClient client = signIn("viewer", "OPERATOR", inbox);

        Future<?> noise = background.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                server.getNotifications().broadcast("noise");
                Thread.sleep(1);
            }
            return null;
        });
        for (int i = 0; i < 100; i++) {
            assertEquals(table, client.send(new Request(CommandType.VIEW_ALL)).getLines());
        }
        noise.cancel(true);

        assertFalse(inbox.isEmpty());
    }

    @Test
    void stoppingTheServerDisconnectsEveryTerminal() throws Exception {
        List<String> inbox = new CopyOnWriteArrayList<>();
        NetworkClient client = signIn("dock_1", "OPERATOR", inbox);

        server.stop();

        waitUntil(() -> inbox.contains(NetworkClient.CONNECTION_LOST));
        assertFalse(client.isConnected());
    }

    private NetworkClient connect(List<String> inbox) throws IOException {
        NetworkClient client = new NetworkClient(inbox::add);
        client.connect("localhost", server.getPort());
        clients.add(client);
        return client;
    }

    private NetworkClient signIn(String name, String role, List<String> inbox) throws Exception {
        NetworkClient client = connect(inbox);
        assertTrue(client.send(new Request(CommandType.HELLO, name, role)).isSuccess());
        return client;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting");
            }
            Thread.sleep(10);
        }
    }
}
