package io.github.vid19.garage.model;

import java.io.Serializable;

/** Immutable picture of one spot at a moment in time. Safe to hand to the GUI or send over the network. */
public record SpotView(String id, int level, int slot, SpotState state, String plate) implements Serializable {
}
