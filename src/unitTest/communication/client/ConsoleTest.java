package communication.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final Console console = new Console(new PrintStream(output));

    @Test
    void anAlertWhileTheUserIsTypingShowsThePromptAgain() {
        console.prompt("Enter choice: ");
        console.alert("T001 departed");

        assertTrue(output.toString().endsWith("[ALERT] T001 departed" + System.lineSeparator() + "Enter choice: "));
    }

    @Test
    void linesFromDifferentThreadsAreNeverMixedTogether() throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            String text = "thread" + t + "-thread" + t + "-thread" + t;
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 300; i++) {
                    console.println(text);
                    console.alert(text);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        for (String line : output.toString().split(System.lineSeparator())) {
            assertTrue(line.isEmpty() || line.matches("(\\[ALERT] )?thread(\\d)-thread\\2-thread\\2"), line);
        }
    }
}
