package resources;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaceportEventLogTest {
    @Test
    void logsEvents() {
        SpaceportEventLog log = new SpaceportEventLog();

        log.log("Allocated BAY_01 to T001");
        log.log("Assigned TEAM_A to T001");

        List<String> entries = log.getEntries();
        assertEquals(2, entries.size());

        String[] first = entries.get(0).split(" ", 2);
        String[] second = entries.get(1).split(" ", 2);

        LocalDateTime.parse(first[0]);
        LocalDateTime.parse(second[0]);
        assertEquals("Allocated BAY_01 to T001", first[1]);
        assertEquals("Assigned TEAM_A to T001", second[1]);
    }
}