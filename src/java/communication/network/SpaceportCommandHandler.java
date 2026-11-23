package communication.network;

import communication.protocol.CommandType;
import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;
import resources.ResourceCommandHandler;
import transport.TransportCommandHandler;

import java.util.ArrayList;
import java.util.List;

public class SpaceportCommandHandler implements CommandHandler {
    private final TransportCommandHandler transportHandler;
    private final ResourceCommandHandler resourceHandler;

    public SpaceportCommandHandler(
            TransportCommandHandler transportHandler,
            ResourceCommandHandler resourceHandler) {

        this.transportHandler = transportHandler;
        this.resourceHandler = resourceHandler;
    }

    @Override
    public Response handle(Request request, ClientInfo client)
            throws ProtocolException {

        CommandType type = request.getType();

        if (isTransportCommand(type)) {
            return transportHandler.handle(request, client);
        }

        if (isResourceCommand(type)) {
            return resourceHandler.handle(request, client);
        }

        if (type == CommandType.VIEW_ALL) {
            return viewAll(client);
        }

        return Response.error("Unsupported command: " + type);
    }

    private boolean isTransportCommand(CommandType type) {
        return type == CommandType.LOAD_CARGO
                || type == CommandType.UNLOAD_CARGO
                || type == CommandType.TRANSFER_CARGO
                || type == CommandType.VIEW_CARGO
                || type == CommandType.VIEW_TRANSPORTS;
    }

    private boolean isResourceCommand(CommandType type) {
        return type == CommandType.ADD_TRANSPORT
                || type == CommandType.ALLOCATE_BAY
                || type == CommandType.EXCHANGE_BAYS
                || type == CommandType.ASSIGN_TEAM
                || type == CommandType.COMPLETE_MAINTENANCE
                || type == CommandType.CLOSE_BAY
                || type == CommandType.REOPEN_BAY
                || type == CommandType.VIEW_BAYS
                || type == CommandType.VIEW_TEAMS
                || type == CommandType.VIEW_LOG;
    }

    private Response viewAll(ClientInfo client)
            throws ProtocolException {

        Response transports = transportHandler.handle(
                new Request(CommandType.VIEW_TRANSPORTS), client);

        Response cargo = transportHandler.handle(
                new Request(CommandType.VIEW_CARGO), client);

        Response bays = resourceHandler.handle(
                new Request(CommandType.VIEW_BAYS), client);

        Response teams = resourceHandler.handle(
                new Request(CommandType.VIEW_TEAMS), client);

        List<String> lines = new ArrayList<>();

        lines.add("TRANSPORTS");
        lines.addAll(transports.getLines());

        lines.add("CARGO");
        lines.addAll(cargo.getLines());

        lines.add("DOCKING BAYS");
        lines.addAll(bays.getLines());

        lines.add("MAINTENANCE TEAMS");
        lines.addAll(teams.getLines());

        return Response.ok("All spaceport status", lines);
    }
}