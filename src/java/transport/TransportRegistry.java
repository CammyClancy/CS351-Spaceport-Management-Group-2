package transport;

import java.util.ArrayList;
import java.util.List;

// Stores all transports in a list.
// All methods are synchronized so multiple threads can use it safely.
public class TransportRegistry {
    private final List<Transport> transports = new ArrayList<>();

    public synchronized boolean add(Transport transport) {
        if (contains(transport.getId())) {
            return false;
        }
        transports.add(transport);
        return true;
    }

    public synchronized Transport get(String id) {
        for (Transport transport : transports) {
            if (transport.getId().equals(id)) {
                return transport;
            }
        }
        return null;
    }

    public synchronized boolean contains(String id) {
        return get(id) != null;
    }

    public synchronized List<Transport> getAll() {
        List<Transport> copy = new ArrayList<>();
        for (Transport transport : transports) {
            copy.add(transport);
        }
        return copy;
    }
}
