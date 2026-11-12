package resources;

import java.io.Serializable;

public class DockingBay implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private BayState state = BayState.AVAILABLE;
    private String transportId;

    public DockingBay(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public synchronized BayState getState() {
        return state;
    }

    public synchronized String getTransportId() {
        return transportId;
    }

    synchronized void occupy(String transportId) {
        this.transportId = transportId;
        state = BayState.OCCUPIED;
    }

    synchronized void release() {
        transportId = null;
        state = BayState.AVAILABLE;
    }

    synchronized boolean close() {
        if (state != BayState.AVAILABLE) {
            return false;
        }
        state = BayState.CLOSED;
        return true;
    }

    synchronized boolean reopen() {
        if (state != BayState.CLOSED) {
            return false;
        }
        state = BayState.AVAILABLE;
        return true;
    }

}