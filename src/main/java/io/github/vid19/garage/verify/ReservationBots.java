package io.github.vid19.garage.verify;

import io.github.vid19.garage.model.BookingResult;
import io.github.vid19.garage.sim.Simulation;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Threads that hammer the reservation API with random book / cancel / check-in calls while the simulation
 * runs, so reservations race with walk-in cars and with each other.
 */
final class ReservationBots {

    private final Simulation sim;
    private final ExecutorService pool;
    private final AtomicBoolean stop = new AtomicBoolean();
    final AtomicInteger booked = new AtomicInteger();
    final AtomicInteger rejected = new AtomicInteger();
    final AtomicInteger cancelled = new AtomicInteger();
    final AtomicInteger checkedIn = new AtomicInteger();

    ReservationBots(Simulation sim, int threads) {
        this.sim = sim;
        this.pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            int botId = i;
            pool.submit(() -> loop(botId));
        }
    }

    private void loop(int botId) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> spots = sim.garage().spotIds();
        int n = 0;
        while (!stop.get()) {
            String plate = "BOT%d-%d".formatted(botId, n++);
            // Half the time everyone fights over the first three spots, to force collisions.
            String spot = switch (rnd.nextInt(3)) {
                case 0 -> null;
                case 1 -> spots.get(rnd.nextInt(Math.min(3, spots.size())));
                default -> spots.get(rnd.nextInt(spots.size()));
            };
            BookingResult r = sim.reserve(plate, spot, rnd.nextDouble(0, 20));
            if (!r.ok()) {
                rejected.incrementAndGet();
                continue;
            }
            booked.incrementAndGet();
            switch (rnd.nextInt(3)) {
                case 0 -> {
                    if (sim.cancel(r.reservation().id()).ok()) {
                        cancelled.incrementAndGet();
                    }
                }
                case 1 -> {
                    if (sim.checkIn(r.reservation().id()).ok()) {
                        checkedIn.incrementAndGet();
                    }
                }
                default -> {
                    // no-show: leave it to expire
                }
            }
        }
    }

    void stopAndWait() throws InterruptedException {
        stop.set(true);
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Bots did not stop");
        }
    }
}
