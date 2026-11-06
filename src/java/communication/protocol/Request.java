package communication.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Request {
    private final CommandType type;
    private final List<String> arguments;

    public Request(CommandType type, String... arguments) {
        if (arguments.length != type.getArgumentCount()) {
            throw new IllegalArgumentException("Usage: " + type.getUsage());
        }
        this.type = type;
        this.arguments = List.of(arguments);
    }

    public static Request parse(String line) throws ProtocolException {
        if (Objects.isNull(line) || line.isBlank()) {
            throw new ProtocolException("Empty command");
        }
        String[] parts = line.trim().split("\\s+");
        CommandType type;
        try {
            type = CommandType.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("Unknown command '" + parts[0] + "'");
        }
        String[] arguments = Arrays.copyOfRange(parts, 1, parts.length);
        if (arguments.length != type.getArgumentCount()) {
            throw new ProtocolException("Usage: " + type.getUsage());
        }
        return new Request(type, arguments);
    }

    public CommandType getType() {
        return type;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String getArgument(int index) {
        return arguments.get(index);
    }

    public int getPositiveNumber(int index) throws ProtocolException {
        try {
            int number = Integer.parseInt(arguments.get(index));
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException exception) {
        }
        throw new ProtocolException("'" + arguments.get(index) + "' is not a whole number above zero");
    }

    public String toLine() {
        return arguments.isEmpty() ? type.name() : type.name() + " " + String.join(" ", arguments);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Request && type == ((Request) other).type && arguments.equals(((Request) other).arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, arguments);
    }

    @Override
    public String toString() {
        return toLine();
    }
}
