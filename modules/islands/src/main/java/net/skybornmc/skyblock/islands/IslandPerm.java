package net.skybornmc.skyblock.islands;

import java.util.Locale;
import java.util.Optional;

/**
 * Everything a player can be allowed or denied on an island (M2.2). Each role of an island (and its visitors) has a set
 * of these: defaults from {@code islands.yml → permissions.defaults}, per-island overrides in {@code island_role_perms}
 * (edited by the M2.3 permission matrix GUI). The stored key ({@link #key()}) never changes; the enum order may.
 */
public enum IslandPerm {
    /** Break blocks, fill buckets, trample crops, harvest berries, extinguish fire. */
    BREAK("break"),
    /** Place blocks, empty buckets, edit signs, use bone meal, strip/till/wax blocks, end crystals. */
    PLACE("place"),
    /** Doors, trapdoors, fence gates, buttons, levers, pressure plates, bells, beds, workstations. */
    INTERACT("interact"),
    /** Chests, barrels, furnaces, hoppers, shulkers, brewing stands, lecterns, jukeboxes, storage minecarts … */
    CONTAINERS("containers"),
    /** Repeaters, comparators, daylight detectors, note blocks, redstone dust shape. */
    REDSTONE("redstone"),
    /** Hurt, breed, feed, shear, milk, leash, ride, bucket and name animals. */
    ANIMALS("animals"),
    /** Trade with (or hurt) villagers and wandering traders. */
    VILLAGERS("villagers"),
    /** Pick up items and arrows. */
    PICKUP("pickup"),
    /** Drop items. */
    DROP("drop"),
    /** Armor stands, item frames and paintings: place, break, change. */
    ARMOR_STANDS("armor-stands"),
    /** Boats and minecarts: place, enter, break. */
    VEHICLES("vehicles"),
    /** Flint and steel, fire charges, lighting TNT, campfires and candles. */
    FIRE("fire"),
    /** Ender pearls and chorus fruit teleports onto the island. */
    ENDER_PEARLS("ender-pearls"),
    /** Spawners and trial spawners (change, break). Custom spawners arrive later. */
    SPAWNERS("spawners"),
    /** ISL2 (owner #25): hurt hostile mobs (zombies, skeletons, creepers …). Defaults per role: own list in islands.yml. */
    HURT_MONSTERS("hurt-monsters", true, false),
    /** ISL2: change the island settings ({@code /is settings}). Management: stored members only, default Leader + Admin. */
    SETTINGS("settings", true, true),
    /** NOTES (owner N1): read the island notes ({@code /is notes}). Default Admin, Mod, Member and Coop (read only). */
    NOTES_READ("notes-read", true, false),
    /** NOTES: add notes, tick them done/open, change, pin and delete your own. Stored members only, default Admin/Mod/Member. */
    NOTES_WRITE("notes-write", true, true),
    /** NOTES: change, pin, unpin and delete anyone's notes, clear done ones. Stored members only, default Admin + Mod. */
    NOTES_MANAGE("notes-manage", true, true),
    /** M3.3: put money into the island bank. Stored members only (coop never, REVIEW A4), default Admin/Mod/Member. */
    BANK_DEPOSIT("bank-deposit", true, true),
    /** M3.3: take money out of the island bank (daily limit per role in bank.yml). Stored members only, default Admin. */
    BANK_WITHDRAW("bank-withdraw", true, true),
    /** M3.3: see the island bank history (Island, Bank, History). Stored members only, default Admin + Mod. */
    BANK_HISTORY("bank-history", true, true),
    /** PHASE-5 M5.4 (owner 170): buy island upgrades with the island bank. Stored members only, default Admin (P5-10). */
    UPGRADES("upgrades", true, true);

    private static final IslandPerm[] VALUES = values();

    private final String key;
    private final boolean ownDefaults;
    private final boolean management;

    IslandPerm(String key) {
        this(key, false, false);
    }

    IslandPerm(String key, boolean ownDefaults, boolean management) {
        this.key = key;
        this.ownDefaults = ownDefaults;
        this.management = management;
    }

    /**
     * True for permissions added after the first release (ISL2): their default roles come from their own list
     * ({@code permissions.role-defaults.<key>}), never from {@code "*"} in a role's list, so existing islands.yml files
     * get the owner's defaults without being rewritten.
     */
    public boolean ownDefaults() {
        return ownDefaults;
    }

    /** A management right (not a build/use action): only stored members (Admin, Mod, Member) can ever have it. */
    public boolean management() {
        return management;
    }

    /** True if {@code role} can have this permission at all (management rights: stored members only). */
    public boolean appliesTo(PermRole role) {
        return !management || role.stored();
    }

    /** Stable key used in islands.yml, the database and message keys. */
    public String key() {
        return key;
    }

    /** This permission's bit in a role mask. */
    public long bit() {
        return 1L << ordinal();
    }

    /** Every permission set. */
    public static long all() {
        return (1L << VALUES.length) - 1;
    }

    /** What {@code "*"} in a role's default list means: everything except the permissions with their own defaults. */
    public static long wildcard() {
        long mask = 0;
        for (IslandPerm perm : VALUES) {
            if (!perm.ownDefaults) {
                mask |= perm.bit();
            }
        }
        return mask;
    }

    /** Bits of the permissions {@code role} can't have at all (management rights for coop players and visitors). */
    public static long notApplicable(PermRole role) {
        long mask = 0;
        for (IslandPerm perm : VALUES) {
            if (!perm.appliesTo(role)) {
                mask |= perm.bit();
            }
        }
        return mask;
    }

    public static Optional<IslandPerm> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (IslandPerm perm : VALUES) {
            if (perm.key.equals(k)) {
                return Optional.of(perm);
            }
        }
        return Optional.empty();
    }

    static IslandPerm[] cached() {
        return VALUES;
    }
}
