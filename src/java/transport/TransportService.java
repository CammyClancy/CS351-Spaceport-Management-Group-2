package transport;

import java.util.ArrayList;
import java.util.List;

import transport.collaborator.EventLogger;

// Handles cargo operations: load, unload and transfer.
// Each method locks the affected transports directly so independent
// transports can progress concurrently without unnecessary blocking.
public class TransportService {
    private final TransportRegistry registry;
    private final EventLogger eventLogger;

    public TransportService(TransportRegistry registry, EventLogger eventLogger) {
        this.registry = registry;
        this.eventLogger = eventLogger;
    }

    // Loads cargo onto a transport.
    public String loadCargo(String transportId, int amount) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            throw new IllegalArgumentException("Transport not found: " + transportId);
        }
        synchronized (transport) {
            if (!canDoCargoOps(transport)) {
                throw new IllegalArgumentException("Cannot load cargo: transport is " + transport.getState());
            }
            if (!transport.addCargo(amount)) {
                throw new IllegalArgumentException("Cannot load " + amount + " units: exceeds capacity");
            }
            eventLogger.log("Loaded " + amount + " units into " + transportId);
            return "Loaded " + amount + " units into " + transportId;
        }
    }

    // Unloads cargo from a transport.
    public String unloadCargo(String transportId, int amount) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            throw new IllegalArgumentException("Transport not found: " + transportId);
        }
        synchronized (transport) {
            if (!canDoCargoOps(transport)) {
                throw new IllegalArgumentException("Cannot unload cargo: transport is " + transport.getState());
            }
            if (!transport.removeCargo(amount)) {
                throw new IllegalArgumentException("Cannot unload " + amount + " units: insufficient cargo");
            }
            eventLogger.log("Unloaded " + amount + " units from " + transportId);
            return "Unloaded " + amount + " units from " + transportId;
        }
    }

    // Moves cargo from one transport to another.
    // Either both transports change or neither does.
    public String transferCargo(String fromId, String toId, int amount) {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Cannot transfer cargo: source and destination must be different transports");
        }
        Transport from = registry.get(fromId);
        if (from == null) {
            throw new IllegalArgumentException("Transport not found: " + fromId);
        }
        Transport to = registry.get(toId);
        if (to == null) {
            throw new IllegalArgumentException("Transport not found: " + toId);
        }
        Transport first = fromId.compareTo(toId) < 0 ? from : to;
        Transport second = fromId.compareTo(toId) < 0 ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (!canDoCargoOps(from) || !canDoCargoOps(to)) {
                    throw new IllegalArgumentException("Cannot transfer: one or both transports not in a valid state");
                }
                if (!from.removeCargo(amount)) {
                    throw new IllegalArgumentException("Cannot transfer " + amount + " units: insufficient cargo in " + fromId);
                }
                if (!to.addCargo(amount)) {
                    from.addCargo(amount);
                    throw new IllegalArgumentException("Cannot transfer " + amount + " units: insufficient capacity in " + toId);
                }
                eventLogger.log("Transferred " + amount + " units from " + fromId + " to " + toId);
                return "Transferred " + amount + " units from " + fromId + " to " + toId;
            }
        }
    }

    // Shows cargo for one transport.
    public String viewCargo(String transportId) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            throw new IllegalArgumentException("Transport not found: " + transportId);
        }
        synchronized (transport) {
            return transportId + " cargo=" + transport.getCargoQuantity() + "/" + transport.getCapacity();
        }
    }

    // Shows state and cargo for one transport.
    public String viewTransport(String transportId) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            throw new IllegalArgumentException("Transport not found: " + transportId);
        }
        synchronized (transport) {
            return transportId + " " + transport.getState() + " bay=" + transport.getBayId() + " cargo=" + transport.getCargoQuantity();
        }
    }

    // Returns a summary line for every transport.
    public List<String> getAllTransportsSummary() {
        List<String> lines = new ArrayList<>();
        for (Transport transport : registry.getAll()) {
            synchronized (transport) {
                lines.add(transport.getId() + " " + transport.getState() + " cargo=" + transport.getCargoQuantity() + "/" + transport.getCapacity());
            }
        }
        return lines;
    }

    // Returns a cargo summary line for every transport.
    public List<String> getAllCargoSummary() {
        List<String> lines = new ArrayList<>();
        for (Transport transport : registry.getAll()) {
            synchronized (transport) {
                lines.add(transport.getId() + " cargo=" + transport.getCargoQuantity() + "/" + transport.getCapacity());
            }
        }
        return lines;
    }

    // Checks if a transport is in a state that allows cargo operations.
    private boolean canDoCargoOps(Transport transport) {
        TransportState state = transport.getState();
        return state == TransportState.DOCKED
            || state == TransportState.SERVICING
            || state == TransportState.READY;
    }
}
