package communication.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseTest {

    @Test
    void okAndErrorResponsesEndWithEnd() {
        assertEquals(List.of("OK Loaded 5 units", "END"), Response.ok("Loaded 5 units").toProtocolLines());
        assertEquals(List.of("ERR Bay is closed", "END"), Response.error("Bay is closed").toProtocolLines());
    }

    @Test
    void extraLinesArePrefixedWithData() {
        Response response = Response.ok("2 transports", List.of("T001 DOCKED", "T002 READY"));

        assertEquals(List.of("OK 2 transports", "DATA T001 DOCKED", "DATA T002 READY", "END"), response.toProtocolLines());
    }

    @Test
    void canBeRebuiltFromTheLinesSent() throws Exception {
        Response original = Response.ok("Event log", List.of("10:00 T001 docked", "END", "NOTIFY not a real one"));
        List<String> sent = original.toProtocolLines();

        Response received = Response.fromProtocolLines(sent.subList(0, sent.size() - 1));

        assertTrue(received.isSuccess());
        assertEquals("Event log", received.getMessage());
        assertEquals(original.getLines(), received.getLines());
    }

    @Test
    void lineBreaksAreRemovedSoTheyCantBreakTheFormat() {
        Response response = Response.error("first\nsecond");

        assertEquals("first second", response.getMessage());
        assertEquals(2, response.toProtocolLines().size());
    }

    @Test
    void rejectsBadlyFormedResponses() {
        assertThrows(ProtocolException.class, () -> Response.fromProtocolLines(List.of()));
        assertThrows(ProtocolException.class, () -> Response.fromProtocolLines(List.of("HELLO")));
        assertThrows(ProtocolException.class, () -> Response.fromProtocolLines(List.of("OK fine", "not data")));
    }

    @Test
    void errorResponsesAreRebuiltAsErrors() throws Exception {
        Response received = Response.fromProtocolLines(List.of("ERR Bay is closed"));

        assertFalse(received.isSuccess());
        assertEquals("Bay is closed", received.getMessage());
    }
}
