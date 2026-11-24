package io.github.vid19.garage.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simulated time, in minutes since the garage opened (minute 0 is 07:00 on day 1).
 *
 * <p>A ticker thread advances time every {@value #TICK_MS} ms while running and calls {@code signalAll()} on a
 * {@link Condition}. Threads that need to wait for a simulated moment (a car waiting to leave) block in
 * {@link #awaitUntil(double)}. Pausing simply stops the ticker from advancing, so every waiting car freezes.
 */
public final class SimClock implements AutoCloseable {

    static final long TICK_MS = 20;
    private static final int OPENING_MINUTE = 7 * 60;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition ticked = lock.newCondition();
    private final ScheduledExecutorService ticker;
    private double now; // guarded by lock
    private volatile double minutesPerSecond;
    private volatile boolean running;

    public SimClock(double minutesPerSecond) {
        setSpeed(minutesPerSecond);
        this.ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-clock");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (!running) {
            return;
        }
        lock.lock();
        try {
            now += minutesPerSecond * TICK_MS / 1000.0;
            ticked.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public double now() {
        lock.lock();
        try {
            return now;
        } finally {
            lock.unlock();
        }
    }

    /** Blocks until simulated time reaches {@code minute}. Responds to interruption. */
    public void awaitUntil(double minute) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (now < minute) {
                ticked.await();
            }
        } finally {
            lock.unlock();
        }
    }

    public void start() {
        running = true;
    }

    public void pause() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public void setSpeed(double minutesPerSecond) {
        if (minutesPerSecond <= 0) {
            throw new IllegalArgumentException("Speed must be positive");
        }
        this.minutesPerSecond = minutesPerSecond;
    }

    public double speed() {
        return minutesPerSecond;
    }

    @Override
    public void close() {
        running = false;
        ticker.shutdownNow();
    }

    /** Formats simulated minutes as a wall-clock label, e.g. {@code "D1 09:45"}. */
    public static String format(double minute) {
        long total = (long) Math.floor(minute) + OPENING_MINUTE;
        long day = total / (24 * 60) + 1;
        long hh = (total / 60) % 24;
        long mm = total % 60;
        return "D%d %02d:%02d".formatted(day, hh, mm);
    }
}
