package resources;

import java.time.LocalDateTime;

public class DepartureMonitor implements Runnable {
     private final ResourceService resourceService;

    public DepartureMonitor(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            resourceService.checkDepartures(LocalDateTime.now());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException exception) {
                return;
            }
        }
    }
}