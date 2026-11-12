package resources;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import transport.Transport;
import transport.TransportRegistry;
import transport.TransportService;
import transport.TransportState;
import transport.collaborator.EventLogger;

public class ResourceService {
    private final List<DockingBay> bays;
    private final List<MaintenanceTeam> teams;
    private final TransportRegistry registry;
    private final TransportService transportService;
    private final EventLogger eventLogger;

    public ResourceService(List<DockingBay> bays, List<MaintenanceTeam> teams,
                           TransportRegistry registry, TransportService transportService,
                           EventLogger eventLogger) {
        this.bays = new ArrayList<>(bays);
        this.teams = new ArrayList<>(teams);
        this.registry = registry;
        this.transportService = transportService;
        this.eventLogger = eventLogger;
    }

    public String addTransport(String id, int capacity, String departureTime) {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
            return "Invalid transport ID";
        }
        if (capacity <= 0) {
            return "Capacity must be greater than zero";
        }
        if (departureTime == null) {
            return "Invalid departure time";
        }

        try {
            LocalDateTime.parse(departureTime);
        } catch (DateTimeParseException exception) {
            return "Invalid departure time";
        }

        synchronized (transportService) {
            Transport transport = new Transport(id, capacity, departureTime);
            if (!registry.add(transport)) {
                return "Transport already exists: " + id;
            }

            String message = "Added transport " + id;
            eventLogger.log(message);
            return message;
        }
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

    public String exchangeBays(String firstId, String secondId) {
        synchronized (transportService) {
            Transport first = registry.get(firstId);
            Transport second = registry.get(secondId);

            if (first == null || second == null) {
                return "Transport not found";
            }
            if (first == second) {
                return "Choose two different transports";
            }
            if (!canExchange(first) || !canExchange(second)) {
                return "Both transports must be DOCKED or READY";
            }

            DockingBay firstBay = findBay(first.getBayId());
            DockingBay secondBay = findBay(second.getBayId());
            if (firstBay == null || secondBay == null
                    || !firstId.equals(firstBay.getTransportId())
                    || !secondId.equals(secondBay.getTransportId())) {
                return "Transport and bay assignments do not match";
            }

            // Update the bay recorded on each transport.
            first.setBayId(secondBay.getId());
            second.setBayId(firstBay.getId());

            // Update the transport recorded in each bay.
            firstBay.occupy(secondId);
            secondBay.occupy(firstId);

            String message = "Exchanged " + firstBay.getId() + " and " + secondBay.getId()
                    + " for " + firstId + " and " + secondId;
            eventLogger.log(message);
            return message;
        }
    }

    private boolean canExchange(Transport transport) {
        TransportState state = transport.getState();
        return state == TransportState.DOCKED || state == TransportState.READY;
    }

    private DockingBay findBay(String bayId) {
        for (DockingBay bay : bays) {
            if (bay.getId().equals(bayId)) {
                return bay;
            }
        }
        return null;
    }

    public void checkDepartures(LocalDateTime now) {
        for (Transport transport : registry.getAll()) {
            synchronized (transportService) {
                if (transport.getState() != TransportState.READY) {
                    continue;
                }

                String departureTime = transport.getDepartureTime();
                if (departureTime == null) {
                    continue;
                }

                LocalDateTime scheduled;
                try {
                    scheduled = LocalDateTime.parse(departureTime);
                } catch (DateTimeParseException exception) {
                    continue;
                }

                if (now.isBefore(scheduled)) {
                    continue;
                }

                DockingBay bay = findBay(transport.getBayId());
                if (bay == null || !transport.getId().equals(bay.getTransportId())) {
                    continue;
                }

                if (transport.transitionTo(TransportState.DEPARTED)) {
                    bay.release();
                    transport.setBayId(null);
                    eventLogger.log("Departed " + transport.getId()
                            + " and released " + bay.getId());
                }
            }
        }
    }

    public String assignTeam(String transportId, String teamId) {
        synchronized (transportService) {
            Transport transport = registry.get(transportId);
            if (transport == null) {
                return "Transport not found: " + transportId;
            }

            MaintenanceTeam team = findTeam(teamId);
            if (team == null) {
                return "Team not found: " + teamId;
            }
            if (team.getState() == TeamState.BUSY) {
                return "Team is busy: " + teamId;
            }

            // Only a DOCKED transport can move to SERVICING.
            if (!transport.transitionTo(TransportState.SERVICING)) {
                return "Transport must be DOCKED: " + transportId;
            }

            team.assignTo(transportId);
            transport.setTeamId(teamId);

            String message = "Assigned " + teamId + " to " + transportId;
            eventLogger.log(message);
            return message;
        }
    }

    public String completeMaintenance(String transportId) {
        synchronized (transportService) {
            Transport transport = registry.get(transportId);
            if (transport == null) {
                return "Transport not found: " + transportId;
            }

            MaintenanceTeam team = findTeam(transport.getTeamId());
            if (team == null || !transportId.equals(team.getTransportId())) {
                return "No maintenance team assigned to " + transportId;
            }

            // Only a SERVICING transport can move to READY.
            if (!transport.transitionTo(TransportState.READY)) {
                return "Transport must be SERVICING: " + transportId;
            }

            team.release();
            transport.setTeamId(null);

            String message = "Completed maintenance on " + transportId
                    + " and released " + team.getId();
            eventLogger.log(message);
            return message;
        }
    }

    public String viewBays() {
        synchronized (transportService) {
            if (bays.isEmpty()) {
                return "No docking bays";
            }

            List<String> entries = new ArrayList<>();
            for (DockingBay bay : bays) {
                String entry = bay.getId() + " " + bay.getState();
                String transportId = bay.getTransportId();
                if (transportId != null) {
                    entry += " (" + transportId + ")";
                }
                entries.add(entry);
            }
            return String.join(", ", entries);
        }
    }

    public String viewTeams() {
        synchronized (transportService) {
            if (teams.isEmpty()) {
                return "No maintenance teams";
            }

            List<String> entries = new ArrayList<>();
            for (MaintenanceTeam team : teams) {
                String entry = team.getId() + " " + team.getState();
                String transportId = team.getTransportId();
                if (transportId != null) {
                    entry += " (" + transportId + ")";
                }
                entries.add(entry);
            }
            return String.join(", ", entries);
        }
    }

    private MaintenanceTeam findTeam(String teamId) {
        for (MaintenanceTeam team : teams) {
            if (team.getId().equals(teamId)) {
                return team;
            }
        }
        return null;
    }
}