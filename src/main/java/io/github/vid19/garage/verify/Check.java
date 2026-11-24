package io.github.vid19.garage.verify;

import java.util.ArrayList;
import java.util.List;

/** Minimal assertion collector for the verification programs, so they need no test framework. */
final class Check {

    private final String name;
    private final List<String> failures = new ArrayList<>();
    private int passed;

    Check(String name) {
        this.name = name;
    }

    void that(boolean condition, String description) {
        if (condition) {
            passed++;
            System.out.println("  ok    " + description);
        } else {
            failures.add(description);
            System.out.println("  FAIL  " + description);
        }
    }

    void equal(long expected, long actual, String description) {
        that(expected == actual, description + " (expected " + expected + ", got " + actual + ")");
    }

    boolean report() {
        boolean ok = failures.isEmpty();
        System.out.printf("%s: %s (%d passed, %d failed)%n%n", name, ok ? "PASS" : "FAIL", passed, failures.size());
        return ok;
    }
}
