package net.skybornmc.skyblock.pack;

/**
 * Pure state of the pack host self-check (GUI-P1-PACK D7): starts unhealthy ("unknown": nobody is sent a dead URL),
 * one success = healthy, {@code failuresToDown} failures in a row = unhealthy. {@link #record} returns the transition so
 * the module alerts once per outage. A host that never came up after start or reload also reports {@code WENT_DOWN}
 * once (a wrong {@code host.public-host} must reach staff), and only a recovery from a reported outage counts as
 * {@link #recoveredFromOutage()} (no "host is back" line at every boot).
 */
final class HostHealth {

    enum Change { STAYED, WENT_DOWN, CAME_UP }

    private volatile boolean healthy;
    private boolean reported;
    private boolean recovered;
    private int failures;
    private int failuresToDown;

    HostHealth(int failuresToDown) {
        this.failuresToDown = Math.max(1, failuresToDown);
    }

    synchronized void failuresToDown(int value) {
        this.failuresToDown = Math.max(1, value);
    }

    synchronized Change record(boolean ok) {
        if (ok) {
            failures = 0;
            if (!healthy) {
                healthy = true;
                recovered = reported;
                reported = false;
                return Change.CAME_UP;
            }
            return Change.STAYED;
        }
        failures++;
        if (failures >= failuresToDown && !reported) {
            healthy = false;
            reported = true;
            return Change.WENT_DOWN;
        }
        return Change.STAYED;
    }

    boolean healthy() {
        return healthy;
    }

    /** After a {@code CAME_UP}: true when an outage alert had been sent before (then staff get the "back" line). */
    synchronized boolean recoveredFromOutage() {
        return recovered;
    }

    synchronized int failures() {
        return failures;
    }

    /** Back to the start state (after a reload: the URL or the file may have changed). */
    synchronized void reset() {
        healthy = false;
        reported = false;
        recovered = false;
        failures = 0;
    }
}
