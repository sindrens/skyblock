package net.skybornmc.skyblock.box;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Typed settings of the item mailbox ({@code box.yml}). Owner answer 7: items are never dropped; overflow goes to /box. */
public final class BoxConfig extends ModuleConfig {

    @Setting(path = "cap.max-entries", desc = "Stacks a profile's box should hold at most. Above it items are STILL stored (never dropped or deleted); the player is warned (and staff see box_over_cap in the logs). Future voluntary paths can refuse while full (ItemBox#full).", min = 10, max = 100000, level = Setting.Level.OWNER)
    public int maxEntries = 500;

    @Setting(path = "staff-items.flag-sources", desc = "Owner answer 332: a staff item (/give, /i, creative) arriving in another "
            + "player's box from one of these sources is logged as staff_item_transfer (trade and auction refuse them already).")
    public java.util.List<String> staffItemFlagSources = java.util.List.of("trade", "auction", "gift");

    @Setting(path = "cap.warn-percent", desc = "From this share of the cap the player is warned that the box is almost full (100 = only when full).", min = 1, max = 100)
    public int warnPercent = 90;

    @Setting(path = "notify.join-delay-ticks", desc = "Ticks after joining (or switching profile) before 'You have N items in /box' is shown.", min = 1, max = 600)
    public int joinDelayTicks = 60;

    @Setting(path = "notify.on-delivery", desc = "Tell an online player in chat when items went to their box.")
    public boolean notifyOnDelivery = true;

    @Setting(path = "notify.action-bar", desc = "Also show the box line on the action bar (join and deliveries).")
    public boolean actionBar = true;

    @Setting(path = "menu.load-limit", desc = "Most stacks loaded into the /box menu at once (oldest first). Taking items shows the next ones.", min = 28, max = 10000)
    public int loadLimit = 2000;

    @Setting(path = "menu.click-cooldown-ms", desc = "Minimum time between two take clicks of one player (anti spam).", min = 0, max = 5000)
    public int clickCooldownMs = 250;

    @Setting(path = "database.retries", desc = "How often a failed delivery is retried before it is written to the spool file (box/spool.txt).", min = 0, max = 10)
    public int retries = 3;

    @Setting(path = "database.retry-delay-ms", desc = "Wait between two delivery retries.", min = 0, max = 10000)
    public int retryDelayMs = 500;

    @Setting(path = "spool.import-seconds", desc = "How often spooled deliveries (database was down) are imported into the database.", min = 5, max = 3600)
    public int spoolImportSeconds = 60;

    @Override
    public String fileName() {
        return "box.yml";
    }

    @Override
    public String header() {
        return "Item mailbox /box (owner answer 7): items that do not fit an inventory wait here instead of being dropped.\n"
                + "Texts: messages/box.yml. Reload: /skyblock reload box";
    }
}
