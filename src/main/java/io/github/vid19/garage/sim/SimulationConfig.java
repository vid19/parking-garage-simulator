package io.github.vid19.garage.sim;

import io.github.vid19.garage.core.PricingPolicy;

/**
 * Settings for one simulation run. Times are simulated minutes unless the name says otherwise.
 *
 * @param simMinutesPerSecond how many simulated minutes pass per real second (the speed slider)
 * @param gates               number of entry gate threads consuming the arrival queue
 * @param queueCapacity       cars that can wait in line; an arrival that finds the line full drives off
 * @param patienceMinutes     average time a driver waits at the gate before giving up
 * @param graceMinutes        how long a reservation is held past the promised arrival window
 */
public record SimulationConfig(
        int levels,
        int spotsPerLevel,
        double arrivalsPerHour,
        double simMinutesPerSecond,
        int gates,
        int queueCapacity,
        double patienceMinutes,
        double minParkMinutes,
        double maxParkMinutes,
        double graceMinutes,
        PricingPolicy pricing) {

    public static SimulationConfig defaults() {
        return new SimulationConfig(3, 20, 24, 30, 2, 25, 20, 30, 240, 15, PricingPolicy.standard());
    }

    public SimulationConfig withSize(int newLevels, int newSpotsPerLevel) {
        return new SimulationConfig(newLevels, newSpotsPerLevel, arrivalsPerHour, simMinutesPerSecond, gates,
                queueCapacity, patienceMinutes, minParkMinutes, maxParkMinutes, graceMinutes, pricing);
    }

    public SimulationConfig withRates(double newArrivalsPerHour, double newSimMinutesPerSecond) {
        return new SimulationConfig(levels, spotsPerLevel, newArrivalsPerHour, newSimMinutesPerSecond, gates,
                queueCapacity, patienceMinutes, minParkMinutes, maxParkMinutes, graceMinutes, pricing);
    }
}
