package resources;

import java.util.ArrayList;
import java.util.List;
import transport.Transport;
import transport.TransportRegistry;
import transport.TransportService;
import transport.TransportState;
import transport.collaborator.EventLogger;

public class ResourceService {
    private final List<DockingBay> bays;
    private final TransportRegistry registry;
    private final TransportService transportService;
    private final EventLogger eventLogger;

    public ResourceService(List<DockingBay> bays, TransportRegistry registry,
                           TransportService transportService, EventLogger eventLogger) {
        this.bays = new ArrayList<>(bays);
        this.registry = registry;
        this.transportService = transportService;
        this.eventLogger = eventLogger;
    }

    public String allocateBay(String transportId, String bayId) {
        // Cargo operations use this same service lock.
        synchronized (transportService) {
            Transport transport = registry.get(transportId);
            if (transport == null) {
                return "Transport not found: " + transportId;
            }

            DockingBay bay = findBay(bayId);
            if (bay == null) {
                return "Bay not found: " + bayId;
            }
            if (bay.getState() != BayState.AVAILABLE) {
                return "Bay is unavailable: " + bayId;
            }

            // Only a WAITING transport can move to DOCKED.
            if (!transport.transitionTo(TransportState.DOCKED)) {
                return "Transport is not waiting: " + transportId;
            }

            transport.setBayId(bayId);
            bay.occupy(transportId);
            String message = "Allocated " + bayId + " to " + transportId;
            eventLogger.log(message);
            return message;
        }
    }

    public String closeBay(String bayId) {
        synchronized (transportService) {
            DockingBay bay = findBay(bayId);
            if (bay == null) {
                return "Bay not found: " + bayId;
            }
            if (!bay.close()) {
                return "Cannot close bay: " + bay.getState();
            }

            String message = "Closed " + bayId;
            eventLogger.log(message);
            return message;
        }
    }

    public String reopenBay(String bayId) {
        synchronized (transportService) {
            DockingBay bay = findBay(bayId);
            if (bay == null) {
                return "Bay not found: " + bayId;
            }
            if (!bay.reopen()) {
                return "Cannot reopen bay: " + bay.getState();
            }

            String message = "Reopened " + bayId;
            eventLogger.log(message);
            return message;
        }
    }

    private DockingBay findBay(String bayId) {
        for (DockingBay bay : bays) {
            if (bay.getId().equals(bayId)) {
                return bay;
            }
        }
        return null;
    }
}