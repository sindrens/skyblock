package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One open trade between two players: the in-memory state machine (main thread only). Pure logic, unit-tested.
 *
 * <pre>
 *   OPEN ──both ready──▶ COUNTDOWN ──time up──▶ COMPLETING ──▶ ENDED
 *     ▲                     │                       │
 *     └──── any change ─────┘◀── money short ───────┘
 * </pre>
 * <ul>
 *   <li>Every change (an item added or taken back, money changed) by either side clears <b>both</b> ready flags and stops a
 *       running countdown.</li>
 *   <li>While an item is on its way into or out of the escrow ({@link Side#inFlight}) nobody can confirm and the countdown
 *       does not finish, so the swap always works on stored rows only.</li>
 *   <li>In COMPLETING nothing can change; the database decides (one transaction) and the session ends or returns to OPEN.</li>
 * </ul>
 */
final class TradeSession {

    enum State {
        OPEN,
        COUNTDOWN,
        COMPLETING,
        ENDED
    }

    /** Result of {@link #confirm}. */
    enum Confirm {
        /** This side is ready; waiting for the other. */
        READY,
        /** Both are ready: the countdown started. */
        COUNTDOWN,
        /** Already ready. */
        ALREADY,
        /** The trade is completing or over. */
        LOCKED,
        /** An item is still being stored or returned. */
        BUSY,
        /** Neither side offers anything. */
        EMPTY
    }

    /** One player's half of the trade. */
    static final class Side {
        final UUID player;
        final UUID profile;
        final String name;
        private final List<Offer> items = new ArrayList<>();
        private final EnumMap<Currency, Long> money = new EnumMap<>(Currency.class);
        private boolean ready;
        private int inFlight;

        Side(UUID player, UUID profile, String name) {
            this.player = Objects.requireNonNull(player, "player");
            this.profile = Objects.requireNonNull(profile, "profile");
            this.name = Objects.requireNonNull(name, "name");
        }

        List<Offer> items() {
            return Collections.unmodifiableList(items);
        }

        Map<Currency, Long> money() {
            return Collections.unmodifiableMap(money);
        }

        long money(Currency currency) {
            return money.getOrDefault(currency, 0L);
        }

        boolean ready() {
            return ready;
        }

        int inFlight() {
            return inFlight;
        }

        /** Items offered plus items still on their way into the escrow. */
        int slotsUsed() {
            return items.size() + inFlight;
        }

        boolean offersNothing() {
            return items.isEmpty() && money.isEmpty() && inFlight == 0;
        }

        List<Long> rowIds() {
            List<Long> ids = new ArrayList<>(items.size());
            for (Offer o : items) {
                ids.add(o.rowId());
            }
            return ids;
        }
    }

    final long id;
    final Side a;
    final Side b;
    final long createdAt;
    private State state = State.OPEN;
    private long countdownEndsAt;
    private int version;
    private String endReason;

    TradeSession(long id, Side a, Side b, long createdAt) {
        if (a.player.equals(b.player)) {
            throw new IllegalArgumentException("a trade needs two players");
        }
        this.id = id;
        this.a = a;
        this.b = b;
        this.createdAt = createdAt;
    }

    // ------------------------------------------------------------------ reading

    State state() {
        return state;
    }

    /** Increases with every change (menus use it to know they are stale). */
    int version() {
        return version;
    }

    String endReason() {
        return endReason;
    }

    boolean involves(UUID player) {
        return a.player.equals(player) || b.player.equals(player);
    }

    Side side(UUID player) {
        if (a.player.equals(player)) {
            return a;
        }
        if (b.player.equals(player)) {
            return b;
        }
        throw new IllegalArgumentException(player + " is not part of trade " + id);
    }

    Side other(UUID player) {
        return side(player) == a ? b : a;
    }

    /** Items and money can still change (OPEN or COUNTDOWN). */
    boolean editable() {
        return state == State.OPEN || state == State.COUNTDOWN;
    }

    boolean ended() {
        return state == State.ENDED;
    }

    /** Whole seconds left in the countdown (rounded up), 0 when not counting down. */
    int secondsLeft(long now) {
        if (state != State.COUNTDOWN) {
            return 0;
        }
        long left = countdownEndsAt - now;
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    // ------------------------------------------------------------------ changes

    /** Any change: both sides must confirm again; a running countdown stops. */
    private void changed() {
        a.ready = false;
        b.ready = false;
        if (state == State.COUNTDOWN) {
            state = State.OPEN;
        }
        version++;
    }

    /**
     * Reserves a slot for an item that is about to go into the escrow (the item already left the inventory).
     *
     * @return false if the trade can't take it (locked, or {@code maxItems} reached)
     */
    boolean beginAdd(UUID player, int maxItems) {
        Side s = side(player);
        if (!editable() || s.slotsUsed() >= maxItems) {
            return false;
        }
        s.inFlight++;
        changed();
        return true;
    }

    /** The escrow stored the item ({@code offer.rowId() > 0}). */
    void finishAdd(UUID player, Offer offer) {
        if (offer.rowId() <= 0) {
            throw new IllegalArgumentException("offer is not stored");
        }
        Side s = side(player);
        s.inFlight = Math.max(0, s.inFlight - 1);
        s.items.add(offer);
        version++;
    }

    /** The item could not be stored (it goes back to the player). */
    void failAdd(UUID player) {
        Side s = side(player);
        s.inFlight = Math.max(0, s.inFlight - 1);
        version++;
    }

    /**
     * Takes an offered item off the table (it is on its way back to the player).
     *
     * @return the offer, or empty if it isn't offered or the trade is locked
     */
    Optional<Offer> beginRemove(UUID player, long rowId) {
        Side s = side(player);
        if (!editable()) {
            return Optional.empty();
        }
        for (int i = 0; i < s.items.size(); i++) {
            if (s.items.get(i).rowId() == rowId) {
                Offer o = s.items.remove(i);
                s.inFlight++;
                changed();
                return Optional.of(o);
            }
        }
        return Optional.empty();
    }

    /** The escrow released the item. */
    void finishRemove(UUID player) {
        Side s = side(player);
        s.inFlight = Math.max(0, s.inFlight - 1);
        version++;
    }

    /** The escrow could not release it: it is offered again (both confirm again, nothing was lost). */
    void failRemove(UUID player, Offer offer) {
        Side s = side(player);
        s.inFlight = Math.max(0, s.inFlight - 1);
        if (state != State.ENDED) {
            s.items.add(offer);
        }
        changed();
    }

    /**
     * Sets the amount of one currency this side offers (0 removes it).
     *
     * @return false if the trade is locked, the amount is negative or nothing changed
     */
    boolean setMoney(UUID player, Currency currency, long amount) {
        Side s = side(player);
        if (!editable() || amount < 0 || s.money(currency) == amount) {
            return false;
        }
        if (amount == 0) {
            s.money.remove(currency);
        } else {
            s.money.put(currency, amount);
        }
        changed();
        return true;
    }

    /** This side confirms the trade as it is now. */
    Confirm confirm(UUID player, long now, long countdownMillis) {
        Side s = side(player);
        if (!editable()) {
            return Confirm.LOCKED;
        }
        if (a.inFlight > 0 || b.inFlight > 0) {
            return Confirm.BUSY;
        }
        if (a.offersNothing() && b.offersNothing()) {
            return Confirm.EMPTY;
        }
        if (s.ready) {
            return Confirm.ALREADY;
        }
        s.ready = true;
        version++;
        if (a.ready && b.ready) {
            state = State.COUNTDOWN;
            countdownEndsAt = now + Math.max(0, countdownMillis);
            return Confirm.COUNTDOWN;
        }
        return Confirm.READY;
    }

    /** This side takes its confirmation back (stops a countdown). */
    boolean unconfirm(UUID player) {
        Side s = side(player);
        if (!editable() || !s.ready) {
            return false;
        }
        s.ready = false;
        if (state == State.COUNTDOWN) {
            state = State.OPEN;
        }
        version++;
        return true;
    }

    /** True when the countdown is over and the swap may start now (both still ready, nothing in flight). */
    boolean countdownDone(long now) {
        return state == State.COUNTDOWN && now >= countdownEndsAt && a.ready && b.ready && a.inFlight == 0 && b.inFlight == 0;
    }

    /** Locks the trade for the swap. */
    boolean beginCompleting() {
        if (state != State.COUNTDOWN || !a.ready || !b.ready || a.inFlight > 0 || b.inFlight > 0) {
            return false;
        }
        state = State.COMPLETING;
        version++;
        return true;
    }

    /** The swap did not happen but the trade can go on (e.g. money short): both confirm again. */
    void reopen() {
        if (state == State.COMPLETING) {
            state = State.OPEN;
            a.ready = false;
            b.ready = false;
            version++;
        }
    }

    /** The trade is over (completed or cancelled). Final. */
    void end(String reason) {
        if (state != State.ENDED) {
            state = State.ENDED;
            endReason = reason;
            version++;
        }
    }

    /** A copy of the offers for the swap (taken on the main thread before the database work starts). */
    Snapshot snapshot() {
        return new Snapshot(id, a.player, a.profile, a.name, List.copyOf(a.items), new EnumMap<>(a.money),
                b.player, b.profile, b.name, List.copyOf(b.items), new EnumMap<>(b.money));
    }

    /** Frozen view of both offers. */
    record Snapshot(long id, UUID playerA, UUID profileA, String nameA, List<Offer> itemsA, Map<Currency, Long> moneyA,
                    UUID playerB, UUID profileB, String nameB, List<Offer> itemsB, Map<Currency, Long> moneyB) {

        Snapshot {
            moneyA = moneyA.isEmpty() ? Map.of() : Map.copyOf(moneyA);
            moneyB = moneyB.isEmpty() ? Map.of() : Map.copyOf(moneyB);
        }

        List<Long> idsA() {
            return itemsA.stream().map(Offer::rowId).toList();
        }

        List<Long> idsB() {
            return itemsB.stream().map(Offer::rowId).toList();
        }
    }
}
