package resources;

import java.io.Serializable;

public class MaintenanceTeam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String transportId;

    public MaintenanceTeam(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public synchronized TeamState getState() {
        if (transportId == null) {
            return TeamState.AVAILABLE;
        }
        return TeamState.BUSY;
    }

    public synchronized String getTransportId() {
        return transportId;
    }

    synchronized void assignTo(String transportId) {
        this.transportId = transportId;
    }

    synchronized void release() {
        transportId = null;
    }
}
