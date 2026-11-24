package io.github.vid19.garage.core;

import io.github.vid19.garage.model.SpotState;
import io.github.vid19.garage.model.SpotView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The in-memory garage: the single place where spot state changes.
 *
 * <p>Two layers of protection:
 * <ol>
 *   <li>A counting {@link Semaphore} holds exactly one permit per FREE spot. A caller must acquire a permit
 *       before taking a spot, so no more callers can proceed than there are free spots.</li>
 *   <li>A {@link ReentrantLock} guards the spot array, so two permit holders can never pick the same spot.</li>
 * </ol>
 *
 * <p>Invariant (when no caller is between acquiring a permit and taking a spot):
 * {@code availablePermits() == count(FREE)}.
 */
public final class Garage {

    private final int levels;
    private final int spotsPerLevel;
    private final List<Spot> spots;
    private final Map<String, Spot> byId;
    private final Semaphore freePermits;
    private final ReentrantLock lock = new ReentrantLock();

    public Garage(int levels, int spotsPerLevel) {
        if (levels < 1 || spotsPerLevel < 1) {
            throw new IllegalArgumentException("A garage needs at least one level and one spot per level");
        }
        this.levels = levels;
        this.spotsPerLevel = spotsPerLevel;
        List<Spot> list = new ArrayList<>();
        Map<String, Spot> map = new HashMap<>();
        for (int level = 1; level <= levels; level++) {
            for (int slot = 1; slot <= spotsPerLevel; slot++) {
                Spot s = new Spot(level, slot);
                list.add(s);
                map.put(s.id, s);
            }
        }
        this.spots = List.copyOf(list);
        this.byId = Map.copyOf(map);
        this.freePermits = new Semaphore(spots.size(), true);
    }

    public int levels() {
        return levels;
    }

    public int spotsPerLevel() {
        return spotsPerLevel;
    }

    public int capacity() {
        return spots.size();
    }

    public List<String> spotIds() {
        return spots.stream().map(s -> s.id).toList();
    }

    public boolean exists(String spotId) {
        return byId.containsKey(spotId);
    }

    // ---- Permits (the semaphore layer) ----

    /** Takes a permit if one is free right now. Used by remote reservations, which never wait. */
    public boolean tryAcquirePermit() {
        return freePermits.tryAcquire();
    }

    /** Waits up to the timeout for a permit. Used by entry gates. */
    public boolean tryAcquirePermit(long timeout, TimeUnit unit) throws InterruptedException {
        return freePermits.tryAcquire(timeout, unit);
    }

    /** Gives back a permit the caller acquired but did not use. */
    public void releasePermit() {
        freePermits.release();
    }

    public int availablePermits() {
        return freePermits.availablePermits();
    }

    // ---- Spot changes (the lock layer) ----

    /**
     * Takes the lowest-numbered FREE spot, so levels fill bottom-up. The caller must already hold a permit;
     * the permit is consumed by the spot leaving the FREE state.
     *
     * @return the id of the spot taken
     */
    public String takeAnyFree(String plate, SpotState as) {
        requireTakenState(as);
        lock.lock();
        try {
            for (Spot s : spots) {
                if (s.state == SpotState.FREE) {
                    s.state = as;
                    s.plate = plate;
                    return s.id;
                }
            }
        } finally {
            lock.unlock();
        }
        throw new IllegalStateException("A permit was held but no spot was FREE: semaphore and spots disagree");
    }

    /**
     * Takes one specific spot. The caller must already hold a permit.
     *
     * @return false if the spot is not FREE, in which case the caller must release its permit
     */
    public boolean takeSpecific(String spotId, String plate, SpotState as) {
        requireTakenState(as);
        Spot s = spot(spotId);
        lock.lock();
        try {
            if (s.state != SpotState.FREE) {
                return false;
            }
            s.state = as;
            s.plate = plate;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Moves a spot between two taken states, e.g. RESERVED to OCCUPIED on check-in. No permit changes hands. */
    public boolean transition(String spotId, SpotState from, SpotState to) {
        requireTakenState(from);
        requireTakenState(to);
        Spot s = spot(spotId);
        lock.lock();
        try {
            if (s.state != from) {
                return false;
            }
            s.state = to;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Frees a spot that must currently be in {@code expected}, then returns its permit to the semaphore. */
    public void free(String spotId, SpotState expected) {
        requireTakenState(expected);
        Spot s = spot(spotId);
        lock.lock();
        try {
            if (s.state != expected) {
                throw new IllegalStateException("Spot %s is %s, expected %s".formatted(spotId, s.state, expected));
            }
            s.state = SpotState.FREE;
            s.plate = null;
        } finally {
            lock.unlock();
        }
        freePermits.release();
    }

    // ---- Reading ----

    public List<SpotView> snapshot() {
        lock.lock();
        try {
            List<SpotView> out = new ArrayList<>(spots.size());
            for (Spot s : spots) {
                out.add(s.view());
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    public int count(SpotState state) {
        lock.lock();
        try {
            int n = 0;
            for (Spot s : spots) {
                if (s.state == state) {
                    n++;
                }
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks, while holding the lock, that the semaphore never shows more permits than there are FREE spots.
     * This holds at every instant: a permit is taken before a spot leaves FREE, and given back only after a
     * spot has become FREE again. (Equality holds only when no thread is mid-way through taking a spot.)
     */
    public boolean permitsNeverExceedFreeSpots() {
        lock.lock();
        try {
            int free = 0;
            for (Spot s : spots) {
                if (s.state == SpotState.FREE) {
                    free++;
                }
            }
            return freePermits.availablePermits() <= free;
        } finally {
            lock.unlock();
        }
    }

    /** Fraction of spots that are not FREE, from 0.0 to 1.0. */
    public double occupancy() {
        return 1.0 - (double) count(SpotState.FREE) / capacity();
    }

    private Spot spot(String spotId) {
        Spot s = byId.get(spotId);
        if (s == null) {
            throw new IllegalArgumentException("Unknown spot " + spotId);
        }
        return s;
    }

    private static void requireTakenState(SpotState s) {
        if (s == SpotState.FREE) {
            throw new IllegalArgumentException("Use free() to make a spot FREE");
        }
    }
}
