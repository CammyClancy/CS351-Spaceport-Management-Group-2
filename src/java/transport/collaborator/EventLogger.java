package transport.collaborator;

// Interface for logging events.
// Daniela will provide the real implementation that writes to the event log.
public interface EventLogger {
    void log(String message);
}
