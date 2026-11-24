package io.github.vid19.garage.model;

import java.io.Serializable;
import java.util.List;

/** Everything a screen needs to draw the garage, captured at one instant. Sent to remote clients as-is. */
public record GarageView(
        int levels,
        int spotsPerLevel,
        double simMinute,
        boolean running,
        boolean surgeActive,
        int queueLength,
        List<SpotView> spots,
        StatsSnapshot stats) implements Serializable {

    public int capacity() {
        return levels * spotsPerLevel;
    }

    public int count(SpotState state) {
        int n = 0;
        for (SpotView s : spots) {
            if (s.state() == state) {
                n++;
            }
        }
        return n;
    }
}
