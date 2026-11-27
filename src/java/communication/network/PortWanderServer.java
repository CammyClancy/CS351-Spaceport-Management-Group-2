package communication.network;

import communication.protocol.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import resources.DepartureMonitor;
import resources.DockingBay;
import resources.MaintenanceTeam;
import resources.ResourceCommandHandler;
import resources.ResourceService;
import resources.SpaceportData;
import resources.SpaceportEventLog;

import transport.TransportCommandHandler;
import transport.TransportRegistry;
import transport.TransportService;

import java.nio.file.Path;
import java.util.List;


public class PortWanderServer {
    public static final int DEFAULT_MAX_CLIENTS = 20;

    private final int port;
    private final CommandHandler commandHandler;
    private final ExecutorService clientPool;
    private final ClientRegistry registry = new ClientRegistry();
    private final NotificationService notifications = new NotificationService(registry);

    private ServerSocket serverSocket;
    private volatile boolean running;
    private int nextClientId = 1;

    public PortWanderServer(int port, int maxClients, CommandHandler commandHandler) {
        this.port = port;
        this.commandHandler = commandHandler;
        this.clientPool = Executors.newFixedThreadPool(maxClients);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread acceptThread = new Thread(this::acceptConnections, "accept-connections");
        acceptThread.start();
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(nextClientId++, socket, commandHandler, registry);
                registry.add(handler);
                clientPool.execute(handler);
            } catch (IOException exception) {
                if (running) {
                    System.err.println("Could not accept connection: " + exception.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException exception) {
        }
        for (ClientHandler handler : registry.getAllConnections()) {
            handler.close();
        }
        notifications.shutdown();
        clientPool.shutdown();
        try {
            clientPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public ClientRegistry getRegistry() {
        return registry;
    }

    public NotificationService getNotifications() {
        return notifications;
    }

    public static void main(String[] args) throws IOException {

        MutableCommandHandler mutableHandler = new MutableCommandHandler();

        PortWanderServer server = new PortWanderServer(Protocol.DEFAULT_PORT, DEFAULT_MAX_CLIENTS, mutableHandler);
        SpaceportEventLog eventLog =
                new SpaceportEventLog();

        TransportRegistry registry =
                new TransportRegistry();

        List<DockingBay> bays = List.of(
                new DockingBay("BAY_01"),
                new DockingBay("BAY_02"),
                new DockingBay("BAY_03")
        );

        List<MaintenanceTeam> teams = List.of(
                new MaintenanceTeam("TEAM_A"),
                new MaintenanceTeam("TEAM_B")
        );

        SpaceportData data = new SpaceportData(registry, bays, teams, eventLog);
        TransportService transportService = new TransportService( registry, eventLog);

        ResourceService resourceService = new ResourceService(bays, teams, registry, eventLog, server.getNotifications()::broadcast);

        TransportCommandHandler transportHandler = new TransportCommandHandler(transportService, server.getNotifications());

        ResourceCommandHandler resourceHandler = new ResourceCommandHandler(resourceService, eventLog);
        SpaceportCommandHandler combinedHandler = new SpaceportCommandHandler(transportHandler, resourceHandler);

        mutableHandler.setDelegate(combinedHandler);

        DepartureMonitor departureMonitor = new DepartureMonitor(resourceService);

        Thread departureThread = new Thread(departureMonitor, "departure-monitor");

        Path saveFile = Path.of("spaceport-data.ser");


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    departureThread.interrupt();

                    try {
                        departureThread.join(3000);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }

                    try {
                        resourceService.save(saveFile, data);
                        System.out.println(
                                "Spaceport data saved to " + saveFile);
                    } catch (IOException exception) {
                        System.err.println(
                                "Could not save spaceport data: "
                                        + exception.getMessage());
                    }

                    server.stop();
                })
        );
        server.start();
        System.out.println("Port Wander server running on port " + server.getPort());
    }
}
