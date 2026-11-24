package io.github.vid19.garage.model;

import java.io.Serializable;

/**
 * An active booking that holds a spot in the RESERVED state until the driver checks in,
 * cancels, or the reservation expires at {@code expiresAt} (simulated minutes).
 */
public record Reservation(long id, String spotId, String plate, double createdAt, double expiresAt)
        implements Serializable {
}
