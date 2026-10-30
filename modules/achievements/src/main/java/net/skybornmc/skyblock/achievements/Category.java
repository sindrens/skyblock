package net.skybornmc.skyblock.achievements;

/**
 * A group of personal achievements in the menu ({@code achievements.yml → categories}).
 *
 * @param slot  inner slot of the 6-row achievements menu
 * @param boost what the category's boost points give (AC2; {@link PersonalBoost#NONE} for nothing)
 */
record Category(String id, String name, String icon, int slot, String description, PersonalBoost boost) {

    Category(String id, String name, String icon, int slot, String description) {
        this(id, name, icon, slot, description, PersonalBoost.NONE);
    }
}
