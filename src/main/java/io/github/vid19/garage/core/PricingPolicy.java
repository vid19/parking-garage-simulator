package io.github.vid19.garage.core;

/**
 * Hourly pricing billed in started 15-minute blocks, with a surge multiplier when the garage is busy.
 *
 * @param centsPerHour     base price
 * @param surgeThreshold   occupancy fraction at or above which surge applies (0.8 = 80%)
 * @param surgeMultiplier  factor applied to the whole stay when surge was active on entry
 */
public record PricingPolicy(long centsPerHour, double surgeThreshold, double surgeMultiplier) {

    public static PricingPolicy standard() {
        return new PricingPolicy(400, 0.80, 1.5);
    }

    public boolean isSurge(double occupancy) {
        return occupancy >= surgeThreshold;
    }

    public long priceCents(double minutesParked, boolean surge) {
        long blocks = Math.max(1, (long) Math.ceil(minutesParked / 15.0));
        double cents = blocks * centsPerHour / 4.0;
        if (surge) {
            cents *= surgeMultiplier;
        }
        return Math.round(cents);
    }
}
