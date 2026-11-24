package io.github.vid19.garage.model;

/**
 * Handle for a car that currently holds a spot. Owned by the car's own thread until it leaves.
 *
 * @param sessionId database id of the parking session
 * @param surge     whether surge pricing applied when the car entered
 */
public record ParkedCar(long sessionId, String spotId, String plate, double parkedAt, double leaveAt, boolean surge) {
}
