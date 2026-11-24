package io.github.vid19.garage.verify;

import io.github.vid19.garage.core.GarageService;
import io.github.vid19.garage.model.GarageView;
import io.github.vid19.garage.model.SpotState;
import io.github.vid19.garage.model.StatsSnapshot;
import io.github.vid19.garage.sim.Simulation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared checks used by both stress tests. */
final class InvariantChecker {

    private InvariantChecker() {
    }

    /** Starts a thread that checks the always-true semaphore invariant as fast as it can. */
    static Thread watchPermits(Simulation sim, AtomicBoolean stop, AtomicInteger checks, AtomicInteger violations) {
        Thread t = new Thread(() -> {
            while (!stop.get()) {
                if (!sim.garage().permitsNeverExceedFreeSpots()) {
                    violations.incrementAndGet();
                }
                checks.incrementAndGet();
                Thread.onSpinWait();
            }
        }, "invariant-watcher");
        t.start();
        return t;
    }

    /**
     * Brings the run to a quiet state (no arrivals, clock paused) and checks that every counter adds up.
     * Call after the bots have stopped.
     */
    static void checkQuiescent(Simulation sim, Check check) throws InterruptedException {
        sim.stopArrivals();
        sim.pause();
        Thread.sleep(400); // let any in-flight housekeeping or departure finish

        GarageService service = sim.service();
        GarageView v = sim.view();
        StatsSnapshot s = v.stats();
        int free = v.count(SpotState.FREE);
        int occupied = v.count(SpotState.OCCUPIED);
        int reserved = v.count(SpotState.RESERVED);

        check.equal(free, sim.garage().availablePermits(), "semaphore permits == FREE spots");
        check.equal(service.activeReservations().size(), reserved, "RESERVED spots == active reservations");
        check.equal((long) s.walkInsParked() + s.checkIns() - s.departed(), occupied,
                "OCCUPIED spots == walk-ins + check-ins - departures");
        check.equal(s.reservationsMade(),
                (long) service.activeReservations().size() + s.reservationsCancelled() + s.reservationsExpired()
                        + s.checkIns(),
                "every reservation was cancelled, expired, checked in, or is still active");
        long distinctPlates = v.spots().stream().filter(x -> x.plate() != null).map(x -> x.plate()).distinct().count();
        check.equal(occupied + reserved, distinctPlates, "no plate holds two spots");
    }
}
