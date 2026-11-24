package io.github.vid19.garage.model;

/** One row of the run-history table, aggregated by SQL from a past simulation run. */
public record RunSummary(
        long runId,
        String startedAt,
        int levels,
        int spotsPerLevel,
        double arrivalsPerHour,
        int parked,
        int turnedAway,
        int reservations,
        long revenueCents,
        double avgWaitMinutes) {
}
