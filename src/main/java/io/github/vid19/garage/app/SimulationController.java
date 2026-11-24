package io.github.vid19.garage.app;

import io.github.vid19.garage.core.EventLog;
import io.github.vid19.garage.db.GarageRepository;
import io.github.vid19.garage.sim.Simulation;
import io.github.vid19.garage.sim.SimulationConfig;

/**
 * Owns the current {@link Simulation} and replaces it on reset. The GUI and the network server always ask
 * for {@link #current()} instead of keeping their own reference, so a reset is picked up everywhere.
 */
public final class SimulationController implements AutoCloseable {

    private final GarageRepository repo;
    private final EventLog events = new EventLog();
    private volatile Simulation current;

    public SimulationController(GarageRepository repo) {
        this.repo = repo;
    }

    /** Stops the current run (if any) and starts a fresh one with {@code config}. */
    public synchronized Simulation reset(SimulationConfig config, boolean autoStart) {
        Simulation old = current;
        if (old != null) {
            old.close();
        }
        Simulation next = new Simulation(config, repo, events);
        current = next;
        if (autoStart) {
            next.start();
        }
        return next;
    }

    public Simulation current() {
        Simulation s = current;
        if (s == null) {
            throw new IllegalStateException("No simulation has been started");
        }
        return s;
    }

    public EventLog events() {
        return events;
    }

    public GarageRepository repository() {
        return repo;
    }

    @Override
    public synchronized void close() {
        if (current != null) {
            current.close();
        }
        repo.close();
    }
}
