package transport;

import java.util.List;

import communication.network.ClientInfo;
import communication.network.CommandHandler;
import communication.protocol.CommandType;
import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;
import transport.collaborator.EventLogger;

// Handles cargo commands from the client.
// Delegates bay and team commands to member 3's code.
public class TransportCommandHandler implements CommandHandler {
    private final TransportService transportService;

    public TransportCommandHandler(TransportService transportService) {
        this.transportService = transportService;
    }

    // Called by the server for every command.
    // Only handles cargo commands for now.
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

        return Response.error(type + " needs implemented still");
    }

    // Handles LOAD_CARGO command.
    private Response handleLoadCargo(Request request) throws ProtocolException {
        String id = request.getArgument(0);
        int amount = request.getPositiveNumber(1);
        String result = transportService.loadCargo(id, amount);
        if (result.startsWith("Loaded")) {
            return Response.ok(result);
        }
        return Response.error(result);
    }

    // Handles UNLOAD_CARGO command.
    private Response handleUnloadCargo(Request request) throws ProtocolException {
        String id = request.getArgument(0);
        int amount = request.getPositiveNumber(1);
        String result = transportService.unloadCargo(id, amount);
        if (result.startsWith("Unloaded")) {
            return Response.ok(result);
        }
        return Response.error(result);
    }

    // Handles TRANSFER_CARGO command.
    private Response handleTransferCargo(Request request) throws ProtocolException {
        String fromId = request.getArgument(0);
        String toId = request.getArgument(1);
        int amount = request.getPositiveNumber(2);
        String result = transportService.transferCargo(fromId, toId, amount);
        if (result.startsWith("Transferred")) {
            return Response.ok(result);
        }
        return Response.error(result);
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
