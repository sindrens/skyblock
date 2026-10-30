package net.skybornmc.skyblock.common.pet;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pets hook for loadouts (WARDROBE §7.6): a loadout saves the active pet and summons it when applied. Pets don't exist
 * yet: until the pets phase registers an implementation ({@code core.services().register(..., PetHook.class, ...)}),
 * {@link #NONE} is used and the loadout's pet slot shows "Pets are coming" and does nothing. Main thread.
 */
public interface PetHook {

    /** Whether pets exist on this server (false for {@link #NONE}). */
    boolean available();

    /** The active pet id of a profile (profile key, {@code ProfileService#profileKey}), if any. */
    Optional<String> activePet(UUID profileKey);

    /** Pet ids a profile owns. */
    List<String> ownedPets(UUID profileKey);

    /**
     * Summons a pet the player's active profile owns (replacing the active one).
     *
     * @return false if the player doesn't own it or it can't be summoned now
     */
    boolean summon(Player player, String petId);

    /** No pets (yet). */
    PetHook NONE = new PetHook() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Optional<String> activePet(UUID profileKey) {
            return Optional.empty();
        }

        @Override
        public List<String> ownedPets(UUID profileKey) {
            return List.of();
        }

        @Override
        public boolean summon(Player player, String petId) {
            return false;
        }
    };
}
