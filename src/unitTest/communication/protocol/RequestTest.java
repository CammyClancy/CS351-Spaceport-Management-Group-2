package communication.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestTest {

    @Test
    void parsesCommandAndArguments() throws Exception {
        Request request = Request.parse("LOAD_CARGO T001 25");

        assertEquals(CommandType.LOAD_CARGO, request.getType());
        assertEquals(List.of("T001", "25"), request.getArguments());
    }

    @Test
    void ignoresCaseAndExtraSpaces() throws Exception {
        assertEquals(new Request(CommandType.LOAD_CARGO, "T001", "25"), Request.parse("  load_cargo   T001   25 "));
    }

    @Test
    void toLineAndParseGiveBackTheSameRequest() throws Exception {
        Request request = new Request(CommandType.TRANSFER_CARGO, "T001", "T002", "5");

        assertEquals("TRANSFER_CARGO T001 T002 5", request.toLine());
        assertEquals(request, Request.parse(request.toLine()));
    }

    @Test
    void rejectsEmptyAndUnknownCommands() {
        assertThrows(ProtocolException.class, () -> Request.parse(""));
        assertThrows(ProtocolException.class, () -> Request.parse("   "));
        assertThrows(ProtocolException.class, () -> Request.parse("FLY_AWAY T001"));
    }

    @Test
    void rejectsTheWrongNumberOfArguments() {
        assertThrows(ProtocolException.class, () -> Request.parse("LOAD_CARGO T001"));
        assertThrows(ProtocolException.class, () -> Request.parse("LOAD_CARGO T001 5 6"));
        assertThrows(ProtocolException.class, () -> Request.parse("VIEW_ALL extra"));
        assertThrows(IllegalArgumentException.class, () -> new Request(CommandType.ALLOCATE_BAY, "T001"));
    }

    @Test
    void getPositiveNumberOnlyAcceptsWholeNumbersAboveZero() throws Exception {
        assertEquals(25, Request.parse("LOAD_CARGO T001 25").getPositiveNumber(1));
        for (String amount : List.of("abc", "0", "-3", "2.5")) {
            Request request = Request.parse("LOAD_CARGO T001 " + amount);
            assertThrows(ProtocolException.class, () -> request.getPositiveNumber(1), amount);
        }
    }
}
