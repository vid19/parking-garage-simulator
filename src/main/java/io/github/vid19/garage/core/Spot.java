package io.github.vid19.garage.core;

import io.github.vid19.garage.model.SpotState;
import io.github.vid19.garage.model.SpotView;

/**
 * One parking spot. Mutable, but only {@link Garage} touches it, and only while holding the garage lock.
 * That is why the fields are package-private and not volatile: the lock provides visibility.
 */
final class Spot {

    final String id;
    final int level;
    final int slot;
    SpotState state = SpotState.FREE;
    String plate;

    Spot(int level, int slot) {
        this.level = level;
        this.slot = slot;
        this.id = idFor(level, slot);
    }

    static String idFor(int level, int slot) {
        return "L%d-%02d".formatted(level, slot);
    }

    SpotView view() {
        return new SpotView(id, level, slot, state, plate);
    }
}
