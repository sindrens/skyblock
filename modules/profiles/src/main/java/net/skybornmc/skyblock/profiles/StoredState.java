package net.skybornmc.skyblock.profiles;

/**
 * The saved player state of one profile (table {@code profile_states}): inventory with armor and offhand, ender chest
 * (both {@code ItemStack.serializeItemsAsBytes}) and the rest as YAML (XP, health, food, effects, location).
 * A state with everything {@code null} is a profile that was never played: it starts empty.
 */
record StoredState(byte[] inventory, byte[] enderChest, String data) {

    static final StoredState FRESH = new StoredState(null, null, null);

    boolean fresh() {
        return inventory == null && enderChest == null && data == null;
    }
}
