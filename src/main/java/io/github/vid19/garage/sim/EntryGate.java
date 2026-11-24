package io.github.vid19.garage.sim;

import io.github.vid19.garage.core.Garage;
import io.github.vid19.garage.core.GarageService;
import io.github.vid19.garage.core.SimClock;
import io.github.vid19.garage.model.Car;
import io.github.vid19.garage.model.ParkedCar;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Consumer in the producer-consumer pattern. Takes the next car from the queue and waits for a semaphore
 * permit (a free spot). If the driver's patience runs out first, the car is turned away.
 */
final class EntryGate implements Runnable {

    private static final long POLL_MS = 50;

    private final SimClock clock;
    private final Garage garage;
    private final GarageService service;
    private final BlockingQueue<Car> gateQueue;
    private final Consumer<ParkedCar> onParked;

    EntryGate(SimClock clock, Garage garage, GarageService service, BlockingQueue<Car> gateQueue,
              Consumer<ParkedCar> onParked) {
        this.clock = clock;
        this.garage = garage;
        this.service = service;
        this.gateQueue = gateQueue;
        this.onParked = onParked;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                admit(gateQueue.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void admit(Car car) throws InterruptedException {
        while (true) {
            if (clock.now() >= car.giveUpAt()) {
                service.turnAway(car);
                return;
            }
            // Poll in short real-time slices so patience is measured in simulated time (and pauses freeze it).
            if (garage.tryAcquirePermit(POLL_MS, TimeUnit.MILLISECONDS)) {
                try {
                    onParked.accept(service.parkWalkIn(car));
                } catch (RuntimeException e) {
                    System.err.println("Could not park " + car.plate() + ": " + e.getMessage());
                }
                return;
            }
        }
    }
}
