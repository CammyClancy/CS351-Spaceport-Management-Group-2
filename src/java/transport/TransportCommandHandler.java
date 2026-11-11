package transport;

import java.util.List;

import communication.network.ClientInfo;
import communication.network.CommandHandler;
import communication.network.NotificationService;
import communication.protocol.CommandType;
import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;
import transport.collaborator.EventLogger;

// Handles cargo commands from the client.
// Delegates bay and team commands to member 3's code.
// Broadcasts notifications to all terminals after successful cargo ops.
public class TransportCommandHandler implements CommandHandler {
    private final TransportService transportService;
    private final NotificationService notifications;

    public TransportCommandHandler(TransportService transportService, NotificationService notifications) {
        this.transportService = transportService;
        this.notifications = notifications;
    }

    @Override
    public Response handle(Request request, ClientInfo client) throws ProtocolException {
        CommandType type = request.getType();

        if (type == CommandType.LOAD_CARGO) {
            return handleLoadCargo(request);
        }
        if (type == CommandType.UNLOAD_CARGO) {
            return handleUnloadCargo(request);
        }
        if (type == CommandType.TRANSFER_CARGO) {
            return handleTransferCargo(request);
        }
        if (type == CommandType.VIEW_CARGO) {
            return handleViewCargo();
        }
        if (type == CommandType.VIEW_TRANSPORTS) {
            return handleViewTransports();
        }

        //Will be implemented by Daniela when moving on to Docking bay and team management, we only have one handler just now
        //When Daniele implements her handler we can combine them together, this is to make sure it doesn't fail hard if someone enters a response we dont know how to answer yet
        return Response.error(type + " is handled by resource management, not transport service");
    }

    // Handles LOAD_CARGO command.
    private Response handleLoadCargo(Request request) throws ProtocolException {
        String id = request.getArgument(0);
        int amount = request.getPositiveNumber(1);
        try {
            String result = transportService.loadCargo(id, amount);
            notifications.broadcast(result);
            return Response.ok(result);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        }
    }

    // Handles UNLOAD_CARGO command.
    private Response handleUnloadCargo(Request request) throws ProtocolException {
        String id = request.getArgument(0);
        int amount = request.getPositiveNumber(1);
        try {
            String result = transportService.unloadCargo(id, amount);
            notifications.broadcast(result);
            return Response.ok(result);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        }
    }

    // Handles TRANSFER_CARGO command.
    private Response handleTransferCargo(Request request) throws ProtocolException {
        String fromId = request.getArgument(0);
        String toId = request.getArgument(1);
        int amount = request.getPositiveNumber(2);
        try {
            String result = transportService.transferCargo(fromId, toId, amount);
            notifications.broadcast(result);
            return Response.ok(result);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        }
    }

    // Handles VIEW_CARGO command.
    private Response handleViewCargo() {
        List<String> lines = transportService.getAllCargoSummary();
        return Response.ok(lines.size() + " transport(s)", lines);
    }

    // Handles VIEW_TRANSPORTS command.
    private Response handleViewTransports() {
        List<String> lines = transportService.getAllTransportsSummary();
        return Response.ok(lines.size() + " transport(s)", lines);
    }
}
