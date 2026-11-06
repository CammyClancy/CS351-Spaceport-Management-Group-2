package communication.client;

import communication.protocol.Request;
import communication.protocol.Response;

import java.io.IOException;

public interface CommandSender {
    Response send(Request request) throws IOException;
}
