package io.github.vid19.garage.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Observer pattern: worker threads publish human-readable events, and listeners (the GUI log, the console)
 * receive them on the publishing thread. Listeners that touch Swing must hop to the EDT themselves.
 */
public final class EventLog {

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public void publish(double simMinute, String message) {
        String line = SimClock.format(simMinute) + "  " + message;
        for (Consumer<String> l : listeners) {
            try {
                l.accept(line);
            } catch (RuntimeException e) {
                System.err.println("Event listener failed: " + e);
            }
        }
    }
}
