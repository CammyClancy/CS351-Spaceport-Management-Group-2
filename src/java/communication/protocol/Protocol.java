package communication.protocol;

public final class Protocol {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8080;

    public static final String OK = "OK";
    public static final String ERR = "ERR";
    public static final String DATA = "DATA";
    public static final String END = "END";
    public static final String NOTIFY = "NOTIFY";

    private Protocol() {
    }

    public static String oneLine(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
    }
}
