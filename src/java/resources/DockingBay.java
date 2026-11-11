package resources;

public class DockingBay {
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