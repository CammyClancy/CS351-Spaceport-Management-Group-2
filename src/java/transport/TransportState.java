package transport;

// The possible states a transport can be in.
// States must change in order: WAITING -> DOCKED -> SERVICING -> READY -> DEPARTED
public enum TransportState {
    WAITING,
    DOCKED,
    SERVICING,
    READY,
    DEPARTED;

    // Checks if this state can move to the next state.
    // Only the next state in the order is allowed.
    public boolean canTransitionTo(TransportState next) {
        if (this == WAITING && next == DOCKED) {
            return true;
        }
        if (this == DOCKED && next == SERVICING) {
            return true;
        }
        if (this == SERVICING && next == READY) {
            return true;
        }
        if (this == READY && next == DEPARTED) {
            return true;
        }
        return false;
    }
}
