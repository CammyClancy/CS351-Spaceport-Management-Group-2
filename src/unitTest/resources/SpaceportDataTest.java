package resources;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import transport.Transport;
import transport.TransportRegistry;
import transport.TransportService;
import transport.TransportState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpaceportDataTest {

    @Test
    void saveAndLoad(@TempDir Path folder) throws Exception {
        TransportRegistry registry = new TransportRegistry();
        SpaceportEventLog log = new SpaceportEventLog();
        DockingBay bay = new DockingBay("BAY_01");
        MaintenanceTeam team = new MaintenanceTeam("TEAM_A");

        SpaceportData data = new SpaceportData(
                registry, List.of(bay), List.of(team), log);

        TransportService cargo = new TransportService(registry, log);
        ResourceService service = new ResourceService(
                data.getBays(), data.getTeams(), registry, cargo, log);

        service.addTransport("T001", 100, "2026-11-10T14:00");
        service.allocateBay("T001", "BAY_01");
        cargo.loadCargo("T001", 25);
        service.assignTeam("T001", "TEAM_A");

        List<String> originalEvents = log.getEntries();
        assertEquals(4, originalEvents.size());

        Path file = folder.resolve("spaceport.ser");
        data.save(file);
        SpaceportData loaded = SpaceportData.load(file);

        Transport transport = loaded.getRegistry().get("T001");
        assertNotNull(transport);
        assertEquals(TransportState.SERVICING, transport.getState());
        assertEquals(100, transport.getCapacity());
        assertEquals(25, transport.getCargoQuantity());
        assertEquals("2026-11-10T14:00", transport.getDepartureTime());
        assertEquals("BAY_01", transport.getBayId());
        assertEquals("TEAM_A", transport.getTeamId());

        DockingBay loadedBay = loaded.getBays().getFirst();
        assertEquals("BAY_01", loadedBay.getId());
        assertEquals(BayState.OCCUPIED, loadedBay.getState());
        assertEquals("T001", loadedBay.getTransportId());

        MaintenanceTeam loadedTeam = loaded.getTeams().getFirst();
        assertEquals("TEAM_A", loadedTeam.getId());
        assertEquals(TeamState.BUSY, loadedTeam.getState());
        assertEquals("T001", loadedTeam.getTransportId());

        assertEquals(originalEvents, loaded.getEventLog().getEntries());
    }
}