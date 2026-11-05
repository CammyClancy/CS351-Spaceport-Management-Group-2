package communication.network;

import communication.protocol.ProtocolException;
import communication.protocol.Request;
import communication.protocol.Response;


public interface CommandHandler {
    Response handle(Request request, ClientInfo client) throws ProtocolException;
}
