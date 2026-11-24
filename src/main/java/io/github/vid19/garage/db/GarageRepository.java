package io.github.vid19.garage.db;

import io.github.vid19.garage.model.OccupancySample;
import io.github.vid19.garage.model.RunSummary;
import io.github.vid19.garage.model.SpotState;

import java.util.List;
import java.util.Map;

/**
 * Durable storage for a simulation run. Every method that changes spot state is ONE database transaction:
 * either all of its statements commit, or none do.
 *
 * <p>State-changing methods use conditional updates ({@code ... WHERE state = 'FREE'}) and throw
 * {@link StaleStateException} if the row was not in the expected state.
 */
public interface GarageRepository extends AutoCloseable {

    /** Creates a run and one FREE row per spot. Returns the run id. */
    long startRun(int levels, int spotsPerLevel, double arrivalsPerHour, List<String> spotIds);

    /** FREE to OCCUPIED, plus a parking_session row. Returns the session id. */
    long parkWalkIn(long runId, String spotId, String plate, double arrivedAt, double parkedAt);

    /** Records a car that gave up waiting at the gate. */
    void recordTurnedAway(long runId, String plate, double arrivedAt, double gaveUpAt);

    /** OCCUPIED to FREE, closes the session and inserts a payment. */
    void depart(long runId, long sessionId, String spotId, double leftAt, long amountCents, boolean surge);

    /** FREE to RESERVED, plus a reservation row with status BOOKED. Returns the reservation id. */
    long reserve(long runId, String spotId, String plate, double createdAt, double expiresAt);

    /** BOOKED to CHECKED_IN and RESERVED to OCCUPIED, plus a parking_session row. Returns the session id. */
    long checkIn(long runId, long reservationId, String spotId, String plate, double now);

    /** BOOKED to {@code finalStatus} (CANCELLED or EXPIRED) and RESERVED to FREE. */
    void releaseReservation(long runId, long reservationId, String spotId, String finalStatus);

    void recordSample(long runId, OccupancySample sample);

    Map<String, SpotState> spotStates(long runId);

    List<OccupancySample> samples(long runId);

    List<RunSummary> runSummaries();

    @Override
    void close();
}
