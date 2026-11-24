package io.github.vid19.garage.model;

import java.io.Serializable;

/** Running totals for one simulation run, copied out of the live atomic counters. */
public record StatsSnapshot(
        int walkInsParked,
        int checkIns,
        int departed,
        int turnedAway,
        int reservationsMade,
        int reservationsCancelled,
        int reservationsExpired,
        long revenueCents,
        double avgWaitMinutes) implements Serializable {
}
