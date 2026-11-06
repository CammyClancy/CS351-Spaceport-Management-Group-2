package communication.protocol;

import java.util.ArrayList;
import java.util.List;

public class Response {
    private final boolean success;
    private final String message;
    private final List<String> lines;

    private Response(boolean success, String message, List<String> lines) {
        this.success = success;
        this.message = Protocol.oneLine(message);
        this.lines = new ArrayList<>();
        for (String line : lines) {
            this.lines.add(Protocol.oneLine(line));
        }
    }

    public static Response ok(String message) {
        return new Response(true, message, List.of());
    }

    public static Response ok(String message, List<String> lines) {
        return new Response(true, message, lines);
    }

    public static Response error(String message) {
        return new Response(false, message, List.of());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getLines() {
        return lines;
    }

    public List<String> toProtocolLines() {
        List<String> result = new ArrayList<>();
        result.add((success ? Protocol.OK : Protocol.ERR) + " " + message);
        for (String line : lines) {
            result.add(Protocol.DATA + " " + line);
        }
        result.add(Protocol.END);
        return result;
    }

    public static Response fromProtocolLines(List<String> received) throws ProtocolException {
        if (received.isEmpty()) {
            throw new ProtocolException("Empty response");
        }
        String status = received.getFirst();
        boolean success;
        if (status.startsWith(Protocol.OK + " ")) {
            success = true;
        } else if (status.startsWith(Protocol.ERR + " ")) {
            success = false;
        } else {
            throw new ProtocolException("Response should start with OK or ERR: " + status);
        }
        String message = status.substring(status.indexOf(' ') + 1);

        List<String> lines = new ArrayList<>();
        for (String line : received.subList(1, received.size())) {
            if (!line.startsWith(Protocol.DATA + " ")) {
                throw new ProtocolException("Unexpected line in response: " + line);
            }
            lines.add(line.substring(Protocol.DATA.length() + 1));
        }
        return new Response(success, message, lines);
    }

    @Override
    public String toString() {
        return (success ? "OK " : "ERR ") + message;
    }
}
