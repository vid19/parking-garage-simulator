package io.github.vid19.garage.model;

/** Outcome of a reservation attempt: either a reservation, or a human-readable reason it failed. */
public record BookingResult(boolean ok, String message, Reservation reservation) {

    public static BookingResult booked(Reservation r) {
        return new BookingResult(true, "Reserved " + r.spotId() + " (#" + r.id() + ")", r);
    }

    public static BookingResult failed(String reason) {
        return new BookingResult(false, reason, null);
    }
}
