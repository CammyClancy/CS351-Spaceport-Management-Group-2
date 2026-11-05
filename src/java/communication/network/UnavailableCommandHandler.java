package communication.network;

import communication.protocol.Request;
import communication.protocol.Response;

public class UnavailableCommandHandler implements CommandHandler {
    @Override
    public Response handle(Request request, ClientInfo client) {
        return Response.error(request.getType() + " is not available yet");
    }
}
