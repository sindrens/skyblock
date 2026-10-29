package net.skybornmc.skyblock.common.item;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Item framework settings ({@code items.yml}). */
public final class ItemsConfig extends ModuleConfig {

    @Setting(path = "dupe-check.enabled", desc = "Scan online inventories for duplicated unique items.")
    public boolean dupeCheckEnabled = true;

    @Setting(path = "dupe-check.scan-seconds", desc = "Seconds between full scans of all online players.", min = 10, max = 3600)
    public int dupeScanSeconds = 60;

    @Setting(path = "dupe-check.event-scan-min-ticks",
            desc = "Minimum ticks between scans triggered by joins and opening/closing inventories (20 ticks = 1 second).",
            min = 1, max = 1200)
    public int dupeEventScanMinTicks = 40;

    @Setting(path = "dupe-check.alert-cooldown-minutes", desc = "Minutes before staff are alerted again about the same item.",
            min = 1, max = 1440)
    public int dupeAlertCooldownMinutes = 10;

    @Override
    public String fileName() {
        return "items.yml";
    }

    @Override
    public String header() {
        return """
                Custom item framework settings (texts are in messages/items.yml).
                Reload: /skyblock reload core""";
    }
}
