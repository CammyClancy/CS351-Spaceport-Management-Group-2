package transport;

import java.io.Serializable;

// Represents one space transport.
// Holds its id, state, bay, team, capacity, cargo and departure time.
public class Transport implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private TransportState state;
    private String bayId;
    private String teamId;
    private final int capacity;
    private int cargoQuantity;
    private String departureTime;

    public Transport(String id, int capacity, String departureTime) {
        this.id = id;
        this.state = TransportState.WAITING;
        this.bayId = null;
        this.teamId = null;
        this.capacity = capacity;
        this.cargoQuantity = 0;
        this.departureTime = departureTime;
    }

    public String getId() {
        return id;
    }

    public synchronized TransportState getState() {
        return state;
    }

    public synchronized String getBayId() {
        return bayId;
    }

    public synchronized String getTeamId() {
        return teamId;
    }

    public synchronized int getCapacity() {
        return capacity;
    }

    public synchronized int getCargoQuantity() {
        return cargoQuantity;
    }

    public synchronized String getDepartureTime() {
        return departureTime;
    }

    public synchronized void setBayId(String bayId) {
        this.bayId = bayId;
    }

    public synchronized void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public synchronized void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    // Changes the transport state if the move is allowed.
    public synchronized boolean transitionTo(TransportState next) {
        if (state.canTransitionTo(next)) {
            state = next;
            return true;
        }
        return false;
    }

    // Adds cargo if there is enough capacity.
    public synchronized boolean addCargo(int amount) {
        if (amount <= 0) {
            return false;
        }
        if (amount > capacity - cargoQuantity) {
            return false;
        }
        cargoQuantity = cargoQuantity + amount;
        return true;
    }

    // Removes cargo if there is enough on board.
    public synchronized boolean removeCargo(int amount) {
        if (amount <= 0) {
            return false;
        }
        if (cargoQuantity < amount) {
            return false;
        }
        cargoQuantity = cargoQuantity - amount;
        return true;
    }

    @Override
    public String toString() {
        return id + " " + state + " cargo=" + cargoQuantity + "/" + capacity;
    }
}
