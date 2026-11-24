package io.github.vid19.garage.core;

import io.github.vid19.garage.model.OccupancySample;
import io.github.vid19.garage.model.StatsSnapshot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free counters for one run. Many threads update these at once, so each one is an atomic type
 * instead of a plain int guarded by a lock.
 */
public final class GarageStats {

    private final AtomicInteger walkInsParked = new AtomicInteger();
    private final AtomicInteger checkIns = new AtomicInteger();
    private final AtomicInteger departed = new AtomicInteger();
    private final AtomicInteger turnedAway = new AtomicInteger();
    private final AtomicInteger reservationsMade = new AtomicInteger();
    private final AtomicInteger reservationsCancelled = new AtomicInteger();
    private final AtomicInteger reservationsExpired = new AtomicInteger();
    private final AtomicLong revenueCents = new AtomicLong();
    private final DoubleAdder totalWait = new DoubleAdder();
    private final LongAdder waits = new LongAdder();
    private final List<OccupancySample> samples = new CopyOnWriteArrayList<>();

    void walkInParked(double waitMinutes) {
        walkInsParked.incrementAndGet();
        totalWait.add(waitMinutes);
        waits.increment();
    }

    void checkedIn() {
        checkIns.incrementAndGet();
    }

    void departed(long cents) {
        departed.incrementAndGet();
        revenueCents.addAndGet(cents);
    }

    void turnedAway() {
        turnedAway.incrementAndGet();
    }

    void reservationMade() {
        reservationsMade.incrementAndGet();
    }

    void reservationCancelled() {
        reservationsCancelled.incrementAndGet();
    }

    void reservationExpired() {
        reservationsExpired.incrementAndGet();
    }

    public void addSample(OccupancySample s) {
        samples.add(s);
    }

    /** Returns the live list; it is a CopyOnWriteArrayList, so iterating it is safe while others add. */
    public List<OccupancySample> samples() {
        return samples;
    }

    public StatsSnapshot snapshot() {
        long n = waits.sum();
        return new StatsSnapshot(
                walkInsParked.get(),
                checkIns.get(),
                departed.get(),
                turnedAway.get(),
                reservationsMade.get(),
                reservationsCancelled.get(),
                reservationsExpired.get(),
                revenueCents.get(),
                n == 0 ? 0.0 : totalWait.sum() / n);
    }
}
