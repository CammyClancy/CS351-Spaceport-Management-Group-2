package transport;

import org.junit.jupiter.api.Test;
import transport.collaborator.EventLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import java.time.Duration;

// Tests that cargo operations work correctly when many threads run at the same time.
class TransportServiceConcurrencyTest {

    // A dummy value for event logger that counts how many times log was called.
    private static class CountingEventLogger implements EventLogger {
        private int count = 0;

        @Override
        public synchronized void log(String message) {
            count = count + 1;
        }

        public int getCount() {
            return count;
        }
    }

    // Creates a transport in DOCKED state with some cargo already on board.
    private Transport setupTransportWithCargo(int initialCargo) {
        Transport t = new Transport("T001", 1000, "2026-11-03T14:30");
        t.transitionTo(TransportState.DOCKED);
        t.addCargo(initialCargo);
        return t;
    }

    @Test
    void concurrentLoadsOnSameTransportResultInExactTotal() throws Exception {
        Transport t = setupTransportWithCargo(0);
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        CountingEventLogger logger = new CountingEventLogger();
        TransportService service = new TransportService(registry, logger);

        int threads = 10;
        int loadsEach = 10;
        int amount = 5;
        int expectedTotal = threads * loadsEach * amount;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < loadsEach; j++) {
                    service.loadCargo("T001", amount);
                }
                return null;
            }));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            start.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        });

        pool.shutdown();

        assertEquals(expectedTotal, t.getCargoQuantity());
        assertEquals(expectedTotal / amount, logger.getCount());
    }

    @Test
    void concurrentUnloadsOnSameTransportResultInExactTotal() throws Exception {
        Transport t = setupTransportWithCargo(500);
        TransportRegistry registry = new TransportRegistry();
        registry.add(t);
        CountingEventLogger logger = new CountingEventLogger();
        TransportService service = new TransportService(registry, logger);

        int threads = 5;
        int unloadsEach = 10;
        int amount = 10;
        int expectedRemoved = threads * unloadsEach * amount;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < unloadsEach; j++) {
                    service.unloadCargo("T001", amount);
                }
                return null;
            }));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            start.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        });

        pool.shutdown();

        assertEquals(500 - expectedRemoved, t.getCargoQuantity());
    }
}
