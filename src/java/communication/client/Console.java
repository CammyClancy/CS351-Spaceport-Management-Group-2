package communication.client;

import java.io.PrintStream;

public class Console {
    private final PrintStream out;
    private String currentPrompt = "";

    public Console(PrintStream out) {
        this.out = out;
    }

    public synchronized void println(String text) {
        currentPrompt = "";
        out.println(text);
    }

    public synchronized void prompt(String text) {
        currentPrompt = text;
        out.print(text);
        out.flush();
    }

    public synchronized void alert(String text) {
        out.println();
        out.println("[ALERT] " + text);
        out.print(currentPrompt);
        out.flush();
    }
}
