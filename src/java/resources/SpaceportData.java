package resources;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import transport.TransportRegistry;

public class SpaceportData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final TransportRegistry registry;
    private final List<DockingBay> bays;
    private final List<MaintenanceTeam> teams;
    private final SpaceportEventLog eventLog;

    public SpaceportData(TransportRegistry registry, List<DockingBay> bays,
                         List<MaintenanceTeam> teams, SpaceportEventLog eventLog) {
        this.registry = registry;
        this.bays = new ArrayList<>(bays);
        this.teams = new ArrayList<>(teams);
        this.eventLog = eventLog;
    }

    public TransportRegistry getRegistry() {
        return registry;
    }

    public List<DockingBay> getBays() {
        return new ArrayList<>(bays);
    }

    public List<MaintenanceTeam> getTeams() {
        return new ArrayList<>(teams);
    }

    public SpaceportEventLog getEventLog() {
        return eventLog;
    }

    public void save(Path file) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            try (ObjectOutputStream output =
                         new ObjectOutputStream(Files.newOutputStream(temporary))) {
                output.writeObject(this);
            }

            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static SpaceportData load(Path file)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input =
                     new ObjectInputStream(Files.newInputStream(file))) {
            return (SpaceportData) input.readObject();
        }
    }
}