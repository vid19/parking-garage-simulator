package io.github.vid19.garage.core;

import io.github.vid19.garage.db.GarageRepository;
import io.github.vid19.garage.model.BookingResult;
import io.github.vid19.garage.model.Car;
import io.github.vid19.garage.model.ParkedCar;
import io.github.vid19.garage.model.Reservation;
import io.github.vid19.garage.model.SpotState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business operations of one run. Each operation keeps the in-memory {@link Garage} and the database in step.
 *
 * <p>Ordering rule, so the two layers never disagree in a way that blocks progress:
 * <ul>
 *   <li>Taking a spot: change memory first (under the lock), then the database. If the database fails,
 *       undo the memory change.</li>
 *   <li>Freeing a spot: change the database first, then memory. Otherwise another thread could take the
 *       spot in memory while the database still shows it taken.</li>
 * </ul>
 *
 * <p>Reservations live in a {@link ConcurrentHashMap}. Whoever successfully {@code remove()}s a reservation
 * owns its next transition, which is how cancel, check-in and expiry are kept from racing each other.
 */
public final class GarageService {

    private final Garage garage;
    private final GarageRepository repo;
    private final SimClock clock;
    private final GarageStats stats;
    private final PricingPolicy pricing;
    private final EventLog events;
    private final long runId;
    private final double graceMinutes;
    private final Map<Long, Reservation> activeReservations = new ConcurrentHashMap<>();

    public GarageService(Garage garage, GarageRepository repo, SimClock clock, GarageStats stats,
                         PricingPolicy pricing, EventLog events, long runId, double graceMinutes) {
        this.garage = garage;
        this.repo = repo;
        this.clock = clock;
        this.stats = stats;
        this.pricing = pricing;
        this.events = events;
        this.runId = runId;
        this.graceMinutes = graceMinutes;
    }

    public long runId() {
        return runId;
    }

    public boolean surgeActive() {
        return pricing.isSurge(garage.occupancy());
    }

    // ---- Walk-in cars ----

    /** Called by an entry gate that already holds a permit for this car. */
    public ParkedCar parkWalkIn(Car car) {
        double now = clock.now();
        boolean surge = surgeActive();
        String spotId = garage.takeAnyFree(car.plate(), SpotState.OCCUPIED);
        long sessionId;
        try {
            sessionId = repo.parkWalkIn(runId, spotId, car.plate(), car.arrivedAt(), now);
        } catch (RuntimeException e) {
            garage.free(spotId, SpotState.OCCUPIED);
            throw e;
        }
        double wait = now - car.arrivedAt();
        stats.walkInParked(wait);
        events.publish(now, "%s parked at %s (waited %.0f min%s)"
                .formatted(car.plate(), spotId, wait, surge ? ", surge" : ""));
        return new ParkedCar(sessionId, spotId, car.plate(), now, now + car.parkMinutes(), surge);
    }

    public void turnAway(Car car) {
        double now = clock.now();
        repo.recordTurnedAway(runId, car.plate(), car.arrivedAt(), now);
        stats.turnedAway();
        events.publish(now, "%s gave up after %.0f min and drove away".formatted(car.plate(), now - car.arrivedAt()));
    }

    /** The car's own thread calls this when its parking time is over. */
    public long depart(ParkedCar p) {
        double now = clock.now();
        long cents = pricing.priceCents(now - p.parkedAt(), p.surge());
        repo.depart(runId, p.sessionId(), p.spotId(), now, cents, p.surge());
        garage.free(p.spotId(), SpotState.OCCUPIED);
        stats.departed(cents);
        events.publish(now, "%s left %s and paid $%.2f".formatted(p.plate(), p.spotId(), cents / 100.0));
        return cents;
    }

    // ---- Reservations ----

    /**
     * Books a spot right now. Never waits: if the garage is full, or the requested spot is taken, it fails.
     *
     * @param requestedSpot a specific spot id, or null for any free spot
     */
    public BookingResult reserve(String plate, String requestedSpot, double arriveWithinMinutes) {
        if (requestedSpot != null && !garage.exists(requestedSpot)) {
            return BookingResult.failed("No such spot " + requestedSpot);
        }
        if (!garage.tryAcquirePermit()) {
            return BookingResult.failed("Garage is full");
        }
        String spotId;
        if (requestedSpot == null) {
            spotId = garage.takeAnyFree(plate, SpotState.RESERVED);
        } else if (garage.takeSpecific(requestedSpot, plate, SpotState.RESERVED)) {
            spotId = requestedSpot;
        } else {
            garage.releasePermit();
            return BookingResult.failed("Spot " + requestedSpot + " is already taken");
        }

        double now = clock.now();
        double expires = now + Math.max(0, arriveWithinMinutes) + graceMinutes;
        long id;
        try {
            id = repo.reserve(runId, spotId, plate, now, expires);
        } catch (RuntimeException e) {
            garage.free(spotId, SpotState.RESERVED);
            return BookingResult.failed("Database rejected the booking: " + e.getMessage());
        }
        Reservation r = new Reservation(id, spotId, plate, now, expires);
        activeReservations.put(id, r);
        stats.reservationMade();
        events.publish(now, "Reservation #%d: %s holds %s until %s"
                .formatted(id, plate, spotId, SimClock.format(expires)));
        return BookingResult.booked(r);
    }

    public BookingResult cancel(long reservationId) {
        Reservation r = activeReservations.remove(reservationId);
        if (r == null) {
            return BookingResult.failed("Reservation #" + reservationId + " is not active");
        }
        release(r, "CANCELLED");
        stats.reservationCancelled();
        events.publish(clock.now(), "Reservation #%d cancelled, %s is free".formatted(r.id(), r.spotId()));
        return new BookingResult(true, "Cancelled #" + r.id(), r);
    }

    /**
     * The driver has arrived. Moves the spot from RESERVED to OCCUPIED and returns the parked car, whose
     * thread the caller must start.
     */
    public Optional<ParkedCar> checkIn(long reservationId, double parkMinutes) {
        Reservation r = activeReservations.remove(reservationId);
        if (r == null) {
            return Optional.empty();
        }
        double now = clock.now();
        if (now > r.expiresAt()) {
            expire(r);
            return Optional.empty();
        }
        if (!garage.transition(r.spotId(), SpotState.RESERVED, SpotState.OCCUPIED)) {
            throw new IllegalStateException("Reserved spot " + r.spotId() + " was not RESERVED");
        }
        long sessionId;
        try {
            sessionId = repo.checkIn(runId, r.id(), r.spotId(), r.plate(), now);
        } catch (RuntimeException e) {
            garage.transition(r.spotId(), SpotState.OCCUPIED, SpotState.RESERVED);
            activeReservations.put(r.id(), r);
            throw e;
        }
        stats.checkedIn();
        events.publish(now, "%s checked in to reserved %s".formatted(r.plate(), r.spotId()));
        return Optional.of(new ParkedCar(sessionId, r.spotId(), r.plate(), now, now + parkMinutes, false));
    }

    /** Releases every reservation whose time is up. Run periodically by a housekeeping thread. */
    public void expireDueReservations() {
        double now = clock.now();
        for (Reservation r : List.copyOf(activeReservations.values())) {
            if (r.expiresAt() <= now && activeReservations.remove(r.id(), r)) {
                expire(r);
            }
        }
    }

    public List<Reservation> activeReservations() {
        return List.copyOf(activeReservations.values());
    }

    private void expire(Reservation r) {
        release(r, "EXPIRED");
        stats.reservationExpired();
        events.publish(clock.now(), "Reservation #%d expired (no-show), %s is free".formatted(r.id(), r.spotId()));
    }

    private void release(Reservation r, String finalStatus) {
        repo.releaseReservation(runId, r.id(), r.spotId(), finalStatus);
        garage.free(r.spotId(), SpotState.RESERVED);
    }
}
