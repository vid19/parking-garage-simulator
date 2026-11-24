package io.github.vid19.garage.db;

import io.github.vid19.garage.model.OccupancySample;
import io.github.vid19.garage.model.RunSummary;
import io.github.vid19.garage.model.SpotState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A repository that stores nothing and only hands out ids. Used by the in-memory stress test to measure
 * the concurrency layer on its own, without database time in the way.
 */
public final class InMemoryRepository implements GarageRepository {

    private final AtomicLong ids = new AtomicLong();

    @Override
    public long startRun(int levels, int spotsPerLevel, double arrivalsPerHour, List<String> spotIds) {
        return ids.incrementAndGet();
    }

    @Override
    public long parkWalkIn(long runId, String spotId, String plate, double arrivedAt, double parkedAt) {
        return ids.incrementAndGet();
    }

    @Override
    public void recordTurnedAway(long runId, String plate, double arrivedAt, double gaveUpAt) {
    }

    @Override
    public void depart(long runId, long sessionId, String spotId, double leftAt, long amountCents, boolean surge) {
    }

    @Override
    public long reserve(long runId, String spotId, String plate, double createdAt, double expiresAt) {
        return ids.incrementAndGet();
    }

    @Override
    public long checkIn(long runId, long reservationId, String spotId, String plate, double now) {
        return ids.incrementAndGet();
    }

    @Override
    public void releaseReservation(long runId, long reservationId, String spotId, String finalStatus) {
    }

    @Override
    public void recordSample(long runId, OccupancySample sample) {
    }

    @Override
    public Map<String, SpotState> spotStates(long runId) {
        return Map.of();
    }

    @Override
    public List<OccupancySample> samples(long runId) {
        return List.of();
    }

    @Override
    public List<RunSummary> runSummaries() {
        return List.of();
    }

    @Override
    public void close() {
    }
}
