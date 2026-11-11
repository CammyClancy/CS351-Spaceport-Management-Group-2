package resources;

import communication.network.ClientInfo;
import communication.network.CommandHandler;
import communication.protocol.CommandType;
import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;

// Handles transport registration, docking bay, maintenance team and event log commands.
public class ResourceCommandHandler implements CommandHandler {
    private final ResourceService resources;
    private final SpaceportEventLog eventLog;

    public ResourceCommandHandler(ResourceService resources, SpaceportEventLog eventLog) {
        this.resources = resources;
        this.eventLog = eventLog;
    }

    @Override
    public Response handle(Request request, ClientInfo client) throws ProtocolException {
        CommandType type = request.getType();

        if (type == CommandType.ADD_TRANSPORT) {
            return handleAddTransport(request);
        }
        if (type == CommandType.ALLOCATE_BAY) {
            return handleAllocateBay(request);
        }
        if (type == CommandType.EXCHANGE_BAYS) {
            return handleExchangeBays(request);
        }
        if (type == CommandType.CLOSE_BAY) {
            return handleCloseBay(request);
        }
        if (type == CommandType.REOPEN_BAY) {
            return handleReopenBay(request);
        }
        if (type == CommandType.ASSIGN_TEAM) {
            return handleAssignTeam(request);
        }
        if (type == CommandType.COMPLETE_MAINTENANCE) {
            return handleCompleteMaintenance(request);
        }
        if (type == CommandType.VIEW_BAYS) {
            return handleViewBays();
        }
        if (type == CommandType.VIEW_TEAMS) {
            return handleViewTeams();
        }
        if (type == CommandType.VIEW_LOG) {
            return handleViewLog();
        }

        return Response.error("Unsupported resource command: " + type);
    }

    private Response handleAddTransport(Request request) throws ProtocolException {
        String transportId = request.getArgument(0);
        int capacity = request.getPositiveNumber(1);
        String departureTime = request.getArgument(2);

        String message = resources.addTransport(transportId, capacity, departureTime);
        return result(message, "Added transport ");
    }

    private Response handleAllocateBay(Request request) {
        String transportId = request.getArgument(0);
        String bayId = request.getArgument(1);

        String message = resources.allocateBay(transportId, bayId);
        return result(message, "Allocated ");
    }

    private Response handleExchangeBays(Request request) {
        String firstTransportId = request.getArgument(0);
        String secondTransportId = request.getArgument(1);

        String message = resources.exchangeBays(firstTransportId, secondTransportId);
        return result(message, "Exchanged ");
    }

    private Response handleCloseBay(Request request) {
        String bayId = request.getArgument(0);
        String message = resources.closeBay(bayId);
        return result(message, "Closed ");
    }

    private Response handleReopenBay(Request request) {
        String bayId = request.getArgument(0);
        String message = resources.reopenBay(bayId);
        return result(message, "Reopened ");
    }

    private Response handleAssignTeam(Request request) {
        String transportId = request.getArgument(0);
        String teamId = request.getArgument(1);

        String message = resources.assignTeam(transportId, teamId);
        return result(message, "Assigned ");
    }

    private Response handleCompleteMaintenance(Request request) {
        String transportId = request.getArgument(0);
        String message = resources.completeMaintenance(transportId);
        return result(message, "Completed maintenance ");
    }

    private Response handleViewBays() {
        return Response.ok(resources.viewBays());
    }

    private Response handleViewTeams() {
        return Response.ok(resources.viewTeams());
    }

    private Response handleViewLog() {
        return Response.ok("Event log", eventLog.getEntries());
    }

    private Response result(String message, String successStart) {
        if (message.startsWith(successStart)) {
            return Response.ok(message);
        }
        return Response.error(message);
    }
}