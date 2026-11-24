package io.github.vid19.garage.model;

/** The three states a parking spot can be in. FREE is the only state that holds a semaphore permit. */
public enum SpotState {
    FREE,
    OCCUPIED,
    RESERVED
}
