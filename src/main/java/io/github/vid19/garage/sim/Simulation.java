package io.github.vid19.garage.sim;

import io.github.vid19.garage.core.EventLog;
import io.github.vid19.garage.core.Garage;
import io.github.vid19.garage.core.GarageService;
import io.github.vid19.garage.core.GarageStats;
import io.github.vid19.garage.core.SimClock;
import io.github.vid19.garage.db.GarageRepository;
import io.github.vid19.garage.model.BookingResult;
import io.github.vid19.garage.model.Car;
import io.github.vid19.garage.model.GarageView;
import io.github.vid19.garage.model.OccupancySample;
import io.github.vid19.garage.model.ParkedCar;
import io.github.vid19.garage.model.SpotState;
import io.github.vid19.garage.model.SpotView;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * One running simulation: the clock, the garage, and all of the threads that act on it.
 *
 * <pre>
 *   CarGenerator --offer--> gateQueue --take--> EntryGate x N --permit--> GarageService.parkWalkIn
 *                                                                              |
 *                                                     carThreads: wait until leave time, then depart()
 * </pre>
 * Remote clients reach the same {@link GarageService} through {@link #reserve}, {@link #cancel} and
 * {@link #checkIn}, so walk-ins and reservations compete for the same semaphore and lock.
 */
public final class Simulation implements AutoCloseable {

    private static final long SAMPLE_EVERY_MS = 250;
    private static final long REAP_EVERY_MS = 200;

    private final SimulationConfig config;
    private final SimClock clock;
    private final Garage garage;
    private final GarageStats stats = new GarageStats();
    private final GarageService service;
    private final GarageRepository repo;
    private final BlockingQueue<Car> gateQueue;
    private final CarGenerator generator;
    private final ExecutorService arrivals;   // generator + gates
    private final ExecutorService carThreads; // one task per parked car
    private final ScheduledExecutorService housekeeping;
    private boolean started;

    public Simulation(SimulationConfig config, GarageRepository repo, EventLog events) {
        this.config = config;
        this.repo = repo;
        this.clock = new SimClock(config.simMinutesPerSecond());
        this.garage = new Garage(config.levels(), config.spotsPerLevel());
        long runId = repo.startRun(config.levels(), config.spotsPerLevel(), config.arrivalsPerHour(), garage.spotIds());
        this.service = new GarageService(garage, repo, clock, stats, config.pricing(), events, runId,
                config.graceMinutes());
        this.gateQueue = new ArrayBlockingQueue<>(config.queueCapacity());
        this.generator = new CarGenerator(clock, gateQueue, service, config);
        this.arrivals = Executors.newFixedThreadPool(config.gates() + 1, new NamedThreads("gate"));
        this.carThreads = Executors.newCachedThreadPool(new NamedThreads("car"));
        this.housekeeping = Executors.newSingleThreadScheduledExecutor(new NamedThreads("housekeeping"));
        events.publish(0, "Run #%d started: %d levels x %d spots, %.0f cars/hour"
                .formatted(runId, config.levels(), config.spotsPerLevel(), config.arrivalsPerHour()));
    }

    /** Starts all threads and the clock. Calling it again just resumes. */
    public synchronized void start() {
        if (!started) {
            started = true;
            arrivals.submit(logged(generator));
            for (int i = 0; i < config.gates(); i++) {
                arrivals.submit(logged(new EntryGate(clock, garage, service, gateQueue, this::parkThenLeave)));
            }
            housekeeping.scheduleAtFixedRate(logged(service::expireDueReservations),
                    REAP_EVERY_MS, REAP_EVERY_MS, TimeUnit.MILLISECONDS);
            housekeeping.scheduleAtFixedRate(logged(this::sample),
                    SAMPLE_EVERY_MS, SAMPLE_EVERY_MS, TimeUnit.MILLISECONDS);
        }
        clock.start();
    }

    public void pause() {
        clock.pause();
    }

    public boolean isRunning() {
        return clock.isRunning();
    }

    public void setArrivalsPerHour(double rate) {
        generator.setArrivalsPerHour(rate);
    }

    public double arrivalsPerHour() {
        return generator.arrivalsPerHour();
    }

    public void setSpeed(double simMinutesPerSecond) {
        clock.setSpeed(simMinutesPerSecond);
    }

    public double speed() {
        return clock.speed();
    }

    // ---- Operations used by the network server ----

    public BookingResult reserve(String plate, String spotIdOrNull, double arriveWithinMinutes) {
        return service.reserve(plate, spotIdOrNull, arriveWithinMinutes);
    }

    public BookingResult cancel(long reservationId) {
        return service.cancel(reservationId);
    }

    /** Checks the driver in and starts a thread for the car, which leaves after a random stay. */
    public BookingResult checkIn(long reservationId) {
        Optional<ParkedCar> parked = service.checkIn(reservationId, randomStay());
        if (parked.isEmpty()) {
            return BookingResult.failed("Reservation #" + reservationId + " is not active (expired, cancelled or used)");
        }
        parkThenLeave(parked.get());
        return new BookingResult(true, "Checked in to " + parked.get().spotId(), null);
    }

    // ---- Reading ----

    public GarageView view() {
        List<SpotView> spots = garage.snapshot();
        return new GarageView(config.levels(), config.spotsPerLevel(), clock.now(), clock.isRunning(),
                service.surgeActive(), gateQueue.size(), spots, stats.snapshot());
    }

    public List<OccupancySample> samples() {
        return stats.samples();
    }

    public SimulationConfig config() {
        return config;
    }

    public long runId() {
        return service.runId();
    }

    /** Exposed for the verification programs. */
    public Garage garage() {
        return garage;
    }

    /** Exposed for the verification programs. */
    public GarageService service() {
        return service;
    }

    public GarageRepository repository() {
        return repo;
    }

    public int queueLength() {
        return gateQueue.size();
    }

    // ---- Lifecycle ----

    /**
     * Stops new arrivals and the entry gates, and waits for them to finish. Parked cars keep running.
     * After this returns, no thread is between acquiring a permit and taking a spot, so the semaphore
     * invariant can be checked.
     */
    public void stopArrivals() throws InterruptedException {
        arrivals.shutdownNow();
        if (!arrivals.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Gate threads did not stop");
        }
    }

    @Override
    public void close() {
        clock.close();
        arrivals.shutdownNow();
        housekeeping.shutdownNow();
        carThreads.shutdownNow();
        try {
            arrivals.awaitTermination(2, TimeUnit.SECONDS);
            carThreads.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- internals ----

    private void parkThenLeave(ParkedCar car) {
        carThreads.submit(logged(() -> {
            try {
                clock.awaitUntil(car.leaveAt());
                service.depart(car);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // simulation is shutting down; the car simply stops
            }
        }));
    }

    private void sample() {
        if (!clock.isRunning()) {
            return;
        }
        OccupancySample s = new OccupancySample(clock.now(), garage.count(SpotState.OCCUPIED),
                garage.count(SpotState.RESERVED), gateQueue.size());
        stats.addSample(s);
        repo.recordSample(service.runId(), s);
    }

    private double randomStay() {
        return ThreadLocalRandom.current().nextDouble(config.minParkMinutes(), config.maxParkMinutes());
    }

    /** Executors swallow exceptions silently; this wrapper makes sure we see them. */
    private static Runnable logged(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                if (!(Thread.currentThread().isInterrupted())) {
                    System.err.println("[" + Thread.currentThread().getName() + "] " + e);
                    e.printStackTrace();
                }
            }
        };
    }
}
