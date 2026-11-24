package io.github.vid19.garage.model;

/**
 * A car arriving at the garage. All times are simulated minutes.
 *
 * @param patienceMinutes how long the driver waits at the gate before giving up
 * @param parkMinutes     how long the car stays once it has a spot
 */
public record Car(long id, String plate, double arrivedAt, double patienceMinutes, double parkMinutes) {

    public double giveUpAt() {
        return arrivedAt + patienceMinutes;
    }
}
