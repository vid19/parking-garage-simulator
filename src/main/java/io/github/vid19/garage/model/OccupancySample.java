package io.github.vid19.garage.model;

import java.io.Serializable;

/** One point on the occupancy chart. */
public record OccupancySample(double simMinute, int occupied, int reserved, int queueLength) implements Serializable {
}
