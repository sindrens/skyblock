package net.skybornmc.skyblock.islands;

import java.util.Locale;
import java.util.Optional;

/**
 * Life cycle of one 512 × 512 grid slot (pure, M2.7). Stored lowercase in {@code island_slots.state}.
 * <pre>
 *   (new spiral slot) ──create──▶ USED ──disband / delete / reset──▶ CLEARING ──region emptied──▶ FREE
 *                                  ▲                                                               │
 *                                  └────────────────────────── create (lowest free slot first) ◀────┘
 * </pre>
 * Only a {@link #FREE} slot can be handed to a new island; a {@link #CLEARING} slot keeps its old island id until the
 * clearer has emptied the region, so a half-cleared slot is never reused (a restart resumes the clearing).
 */
enum SlotState {
    FREE,
    USED,
    CLEARING;

    /** Database key ({@code free}, {@code used}, {@code clearing}). */
    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<SlotState> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (SlotState state : values()) {
            if (state.key().equalsIgnoreCase(key.trim())) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }

    /** True if a slot in this state may move to {@code next} (the only allowed moves are the arrows above). */
    boolean canMoveTo(SlotState next) {
        return switch (this) {
            case FREE -> next == USED;
            case USED -> next == CLEARING;
            case CLEARING -> next == FREE;
        };
    }

    /** A new island can be placed here. */
    boolean reusable() {
        return this == FREE;
    }

    /** The slot's region may still hold blocks of an island (used, or not emptied yet). */
    boolean holdsBlocks() {
        return this != FREE;
    }
}
