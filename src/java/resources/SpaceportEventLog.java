package resources;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import transport.collaborator.EventLogger;

public class SpaceportEventLog implements EventLogger, Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> entries = new ArrayList<>();

    @Override
    public synchronized void log(String message) {
        entries.add(LocalDateTime.now() + " " + message);
    }

    public synchronized List<String> getEntries() {
        return new ArrayList<>(entries);
    }
}