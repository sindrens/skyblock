package net.skybornmc.skyblock.tutorial;

import java.util.Locale;
import java.util.Optional;

/**
 * What finishes a tutorial step. Every type listens to a real event (owner: never commands typed), see
 * {@link TutorialListener} and {@link AuditSignals}. {@code target} meaning per type is in tutorial.yml's header.
 */
enum StepType {
    /** Talk to an NPC of a role (npcs module, {@code NpcClickEvent}); done when its dialog ends. target = role. */
    NPC(true),
    /** A menu opened ({@code MenuOpenedEvent}). target = menu id(s), {@code hub|island}. */
    MENU(true),
    /** Standing on your own island (checked on teleport and every second). */
    ISLAND(false),
    /** Standing in a place of {@code places} in the settings ({@code farm}, {@code mine}, {@code spawn}). target = place id. */
    PLACE(true),
    /** Harvests at /farm or breaks at /mine ({@code GrindBreakEvent}). target = {@code farm|mine[:MATERIAL]}. */
    GRIND(true),
    /** Items sold to the server ({@code ItemsSoldEvent}); amount = sales. */
    SELL(false),
    /** Money put into a bank ({@code BankDepositEvent}); amount = money in total. */
    BANK(false),
    /** An audit row another module writes. target = {@code category:action[|action][:detail=value]}. */
    AUDIT(true),
    /** A crop placed on your own island ({@code BlockPlaceEvent}). target = blocks, {@code WHEAT|CARROTS}. */
    PLANT(true),
    /** A chest opened on your own island (the starter chest). */
    CHEST(false),
    /** Cobblestone or stone formed on your island while you were near it (a cobblestone generator works). */
    GENERATOR(false);

    private final boolean needsTarget;

    StepType(boolean needsTarget) {
        this.needsTarget = needsTarget;
    }

    boolean needsTarget() {
        return needsTarget;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<StepType> of(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String k = raw.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (StepType t : values()) {
            if (t.name().equals(k)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
