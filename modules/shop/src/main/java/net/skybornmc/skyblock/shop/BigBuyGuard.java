package net.skybornmc.skyblock.shop;

import java.util.Objects;

/**
 * F14 "second click for big buys" (SHOP_DESIGN §4): the first click on a buy over half your money only <b>arms</b> that
 * button (it turns orange); a second click on the same button within the window (5 s) buys. Any other click or the
 * timeout disarms it. One per open shop page. Pure (unit-tested).
 */
final class BigBuyGuard {

    private String armed;
    private long until;

    /**
     * A click on the buy button {@code key} (e.g. {@code amount:64}) at {@code now}.
     *
     * @param big whether this buy needs a second click (over the limit and the player's setting is on)
     * @return true when the buy should go ahead now; false when this click only armed the button
     */
    boolean click(String key, boolean big, long now, long windowMillis) {
        if (!big) {
            disarm();
            return true;
        }
        if (Objects.equals(armed, key) && now < until) {
            disarm();
            return true;
        }
        armed = key;
        until = now + Math.max(0, windowMillis);
        return false;
    }

    /** True while {@code key} is armed at {@code now}. */
    boolean armed(String key, long now) {
        return armed != null && armed.equals(key) && now < until;
    }

    /** True when something was armed and its window ran out at {@code now} (the page then repaints the button). */
    boolean expired(long now) {
        return armed != null && now >= until;
    }

    void disarm() {
        armed = null;
        until = 0;
    }
}
