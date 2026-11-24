package io.github.vid19.garage.sim;

import io.github.vid19.garage.core.GarageService;
import io.github.vid19.garage.core.SimClock;
import io.github.vid19.garage.model.Car;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Producer in the producer-consumer pattern. Creates cars with exponentially distributed gaps (a Poisson
 * arrival process, the standard model for random arrivals) and puts them in the gate queue.
 */
final class CarGenerator implements Runnable {

    private static final String LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ";

    private final SimClock clock;
    private final BlockingQueue<Car> gateQueue;
    private final GarageService service;
    private final SimulationConfig config;
    private final AtomicLong ids = new AtomicLong();
    private volatile double arrivalsPerHour;

    CarGenerator(SimClock clock, BlockingQueue<Car> gateQueue, GarageService service, SimulationConfig config) {
        this.clock = clock;
        this.gateQueue = gateQueue;
        this.service = service;
        this.config = config;
        this.arrivalsPerHour = config.arrivalsPerHour();
    }

    void setArrivalsPerHour(double rate) {
        this.arrivalsPerHour = rate;
    }

    double arrivalsPerHour() {
        return arrivalsPerHour;
    }

    @Override
    public void run() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double next = clock.now();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                double meanGap = 60.0 / Math.max(0.1, arrivalsPerHour);
                next += -meanGap * Math.log(1.0 - rnd.nextDouble());
                clock.awaitUntil(next);
                Car car = new Car(ids.incrementAndGet(), randomPlate(rnd), clock.now(),
                        config.patienceMinutes() * (0.5 + rnd.nextDouble()),
                        rnd.nextDouble(config.minParkMinutes(), config.maxParkMinutes()));
                if (!gateQueue.offer(car)) {
                    service.turnAway(car); // the line is full, so the driver does not even join it
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String randomPlate(ThreadLocalRandom rnd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(LETTERS.charAt(rnd.nextInt(LETTERS.length())));
        }
        return sb.append('-').append(1000 + rnd.nextInt(9000)).toString();
    }
}
