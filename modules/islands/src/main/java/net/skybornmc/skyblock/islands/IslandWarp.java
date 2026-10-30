package net.skybornmc.skyblock.islands;

import java.util.UUID;

/**
 * One cached island warp (main thread only). Changes are saved first ({@link VisitStore}) and applied here after the
 * database confirmed.
 */
final class IslandWarp {

    /** Block position of a sign warp's sign. */
    record SignPos(int x, int y, int z) {
    }

    private final long id;
    private final UUID islandId;
    private final String name;
    private WarpCategory category;
    private String icon;
    private boolean isPublic;
    private Island.Home location;
    private SignPos sign;
    private int visits;
    private final UUID createdBy;
    private final long createdAt;

    IslandWarp(long id, UUID islandId, String name, WarpCategory category, String icon, boolean isPublic, Island.Home location,
               SignPos sign, int visits, UUID createdBy, long createdAt) {
        this.id = id;
        this.islandId = islandId;
        this.name = name;
        this.category = category;
        this.icon = icon;
        this.isPublic = isPublic;
        this.location = location;
        this.sign = sign;
        this.visits = visits;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    long id() {
        return id;
    }

    UUID islandId() {
        return islandId;
    }

    String name() {
        return name;
    }

    String key() {
        return WarpRules.key(name);
    }

    WarpCategory category() {
        return category;
    }

    void category(WarpCategory category) {
        this.category = category;
    }

    String icon() {
        return icon;
    }

    void icon(String icon) {
        this.icon = icon;
    }

    boolean isPublic() {
        return isPublic;
    }

    void isPublic(boolean value) {
        this.isPublic = value;
    }

    Island.Home location() {
        return location;
    }

    void location(Island.Home location, SignPos sign) {
        this.location = location;
        this.sign = sign;
    }

    /** The sign of a sign warp, or null. */
    SignPos sign() {
        return sign;
    }

    int visits() {
        return visits;
    }

    void addVisit() {
        visits++;
    }

    UUID createdBy() {
        return createdBy;
    }

    long createdAt() {
        return createdAt;
    }
}
