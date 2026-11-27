package communication.network;

import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;

public class MutableCommandHandler implements CommandHandler {
    private volatile CommandHandler delegate;

    public void setDelegate(CommandHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response handle(Request request, ClientInfo client)
            throws ProtocolException {

        CommandHandler current = delegate;

        if (current == null) {
            return Response.error("Server is still starting");
        }

        return current.handle(request, client);
    }
}