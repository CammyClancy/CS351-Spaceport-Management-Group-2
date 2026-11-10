package transport;

import java.util.ArrayList;
import java.util.List;

import transport.collaborator.EventLogger;

// Handles cargo operations: load, unload and transfer.
// All methods are synchronized for thread safety.
public class TransportService {
    private final TransportRegistry registry;
    private final EventLogger eventLogger;

    public TransportService(TransportRegistry registry, EventLogger eventLogger) {
        this.registry = registry;
        this.eventLogger = eventLogger;
    }

    // Loads cargo onto a transport.
    public synchronized String loadCargo(String transportId, int amount) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            return "Transport not found: " + transportId;
        }
        if (!canDoCargoOps(transport)) {
            return "Cannot load cargo: transport is " + transport.getState();
        }
        if (!transport.addCargo(amount)) {
            return "Cannot load " + amount + " units: exceeds capacity";
        }
        eventLogger.log("Loaded " + amount + " units into " + transportId);
        return "Loaded " + amount + " units into " + transportId;
    }

    // Unloads cargo from a transport.
    public synchronized String unloadCargo(String transportId, int amount) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            return "Transport not found: " + transportId;
        }
        if (!canDoCargoOps(transport)) {
            return "Cant unload cargo as transport is " + transport.getState();
        }
        if (!transport.removeCargo(amount)) {
            return "Cant unload " + amount + " units: insufficient cargo";
        }
        eventLogger.log("Unloaded " + amount + " units from " + transportId);
        return "Unloaded " + amount + " units from " + transportId;
    }

    // Moves cargo from one transport to another.
    // Either both transports change or neither does.
    public synchronized String transferCargo(String fromId, String toId, int amount) {
        Transport from = registry.get(fromId);
        if (from == null) {
            return "Transport not found: " + fromId;
        }
        Transport to = registry.get(toId);
        if (to == null) {
            return "Transport not found: " + toId;
        }
        if (!canDoCargoOps(from) || !canDoCargoOps(to)) {
            return "Cant transfer as one or both transports not in a valid state";
        }
        if (!from.removeCargo(amount)) {
            return "Cant transfer " + amount + " units: insufficient cargo in " + fromId;
        }
        if (!to.addCargo(amount)) {
            from.addCargo(amount);
            return "Cannot transfer " + amount + " units: insufficient capacity in " + toId;
        }
        eventLogger.log("Transferred " + amount + " units from " + fromId + " to " + toId);
        return "Transferred " + amount + " units from " + fromId + " to " + toId;
    }

    // Shows cargo for one transport.
    public synchronized String viewCargo(String transportId) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            return "Transport not found: " + transportId;
        }
        return transportId + " cargo=" + transport.getCargoQuantity() + "/" + transport.getCapacity();
    }

    // Shows state and cargo for one transport.
    public synchronized String viewTransport(String transportId) {
        Transport transport = registry.get(transportId);
        if (transport == null) {
            return "Transport not found: " + transportId;
        }
        return transportId + " " + transport.getState() + " bay=" + transport.getBayId() + " cargo=" + transport.getCargoQuantity();
    }

    // Returns a summary line for every transport.
    public synchronized List<String> getAllTransportsSummary() {
        List<String> lines = new ArrayList<>();
        for (Transport transport : registry.getAll()) {
            lines.add(transport.getId() + " " + transport.getState() + " cargo=" + transport.getCargoQuantity() + "/" + transport.getCapacity());
        }
        return lines;
    }

    // Returns a cargo summary line for every transport.
    public synchronized List<String> getAllCargoSummary() {
        List<String> lines = new ArrayList<>();
        for (Transport transport : registry.getAll()) {
            lines.add(transport.getId() + " cargo=" + transport.getCargoQuantity() + "/" + transport.getCapacity());
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
