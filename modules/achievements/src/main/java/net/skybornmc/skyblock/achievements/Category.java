package net.skybornmc.skyblock.achievements;

/**
 * A group of achievements in the menu ({@code achievements.yml → categories}).
 *
 * @param slot inner slot of the 6-row achievements menu
 */
record Category(String id, String name, String icon, int slot, String description) {
}
