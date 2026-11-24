package io.github.vid19.garage.verify;

import io.github.vid19.garage.core.EventLog;
import io.github.vid19.garage.core.PricingPolicy;
import io.github.vid19.garage.db.InMemoryRepository;
import io.github.vid19.garage.model.StatsSnapshot;
import io.github.vid19.garage.sim.Simulation;
import io.github.vid19.garage.sim.SimulationConfig;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a deliberately overloaded garage at high speed with reservation bots racing walk-in cars, without a
 * database, and checks that no spot is ever handed out twice and every counter adds up.
 *
 * <p>Run: {@code mvn -q exec:java -Dexec.mainClass=io.github.vid19.garage.verify.ConcurrencyStressTest}
 */
public final class ConcurrencyStressTest {

    private ConcurrencyStressTest() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(3_000) ? 0 : 1);
    }

    public static boolean run(long millis) throws Exception {
        System.out.println("== Concurrency stress test (in memory) ==");
        Check check = new Check("Concurrency stress test");
        // 20 spots, 600 cars per simulated hour, 10 simulated hours per real second.
        SimulationConfig cfg = new SimulationConfig(2, 10, 600, 600, 3, 30, 15, 5, 40, 5, PricingPolicy.standard());
        try (Simulation sim = new Simulation(cfg, new InMemoryRepository(), new EventLog())) {
            sim.start();
            AtomicBoolean stopWatcher = new AtomicBoolean();
            AtomicInteger checks = new AtomicInteger();
            AtomicInteger violations = new AtomicInteger();
            Thread watcher = InvariantChecker.watchPermits(sim, stopWatcher, checks, violations);
            ReservationBots bots = new ReservationBots(sim, 8);

            Thread.sleep(millis);
            bots.stopAndWait();
            stopWatcher.set(true);
            watcher.join();

            StatsSnapshot s = sim.view().stats();
            System.out.printf("  %d walk-ins parked, %d departed, %d turned away, %d bookings (%d rejected), %d check-ins%n",
                    s.walkInsParked(), s.departed(), s.turnedAway(), bots.booked.get(), bots.rejected.get(), s.checkIns());
            check.that(s.walkInsParked() > 100 && bots.booked.get() > 100, "enough traffic to be meaningful");
            check.equal(0, violations.get(), "permits never exceeded FREE spots across " + checks.get() + " live checks");
            InvariantChecker.checkQuiescent(sim, check);
        }
        return check.report();
    }
}
