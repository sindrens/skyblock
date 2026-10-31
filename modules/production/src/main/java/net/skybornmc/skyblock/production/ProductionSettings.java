package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of the production module ({@code production.yml}). Texts live in {@code messages/production.yml}. Every
 * number is a default the owner can change (PHASE-7-PRODUCTION.md M7.1).
 */
public final class ProductionSettings extends ModuleConfig {

    // ------------------------------------------------------------------ the switch

    @Setting(path = "fuel.enabled", desc = "Island Fuel on/off (master switch, OFF until the owner enables production). On: islands "
            + "earn fuel while a member plays on them, burn it while nobody does, and fall asleep with an empty tank; Fuel Cells "
            + "work, the AFK pool delivers its banked fuel, the daily calendar and crates give Fuel Cells, the Fuel Tank "
            + "upgrade and the Fuel Efficiency mastery can be bought. Off: nothing changes anywhere (spawners and crops work "
            + "as before, AFK fuel stays banked). Apply with /skyblock reload production.", level = Setting.Level.OWNER)
    public boolean enabled = false;

    @Setting(path = "fuel.pause-when-asleep", desc = "While Island Fuel is on: an Asleep island (empty tank, no active member) "
            + "pauses production: its spawners stop (even next to a player) and its crops don't grow. Off = the states and the "
            + "tank still work, but nothing pauses.", level = Setting.Level.OWNER)
    public boolean pauseWhenAsleep = true;

    // ------------------------------------------------------------------ states and activity

    @Setting(path = "fuel.check-seconds", desc = "How often every island's state (Active / Fueled / Asleep) is checked and its "
            + "fuel added or burned.", min = 10, max = 600)
    public int checkSeconds = 60;

    @Setting(path = "fuel.active-minutes", desc = "A member counts as active on their island for this long after a real action "
            + "(break, place, hit a mob, harvest, fish, use a menu, chat, walk). Standing AFK burns fuel.", min = 1, max = 120,
            level = Setting.Level.OWNER)
    public int activeMinutes = 15;

    @Setting(path = "fuel.walk-blocks", desc = "Blocks a member must walk (not in water, a vehicle or a bubble column) for it to "
            + "count as an action.", min = 1, max = 64)
    public int walkBlocks = 5;

    // ------------------------------------------------------------------ tank and burn

    @Setting(path = "fuel.tank-start-hours", desc = "Tank size without the Fuel Tank upgrade (PD3: 12 h).", min = 1, max = 240,
            level = Setting.Level.OWNER)
    public int tankStartHours = 12;

    @Setting(path = "fuel.tank-hours-per-level", desc = "Extra tank hours per Fuel Tank upgrade level (PD3: +2 h, 36 h at level 12).",
            min = 0, max = 48, level = Setting.Level.OWNER)
    public int tankHoursPerLevel = 2;

    @Setting(path = "fuel.burn-rate", desc = "Fuel minutes burned per real minute while Fueled, before Fuel Efficiency (1.0 = one "
            + "minute). Fuel Efficiency makes fuel last longer: rate ÷ (1 + efficiency%).", min = 0.1, max = 10,
            level = Setting.Level.OWNER)
    public double burnRate = 1.0;

    @Setting(path = "fuel.save-seconds", desc = "How often changed fuel is saved (and on stop). A crash loses at most this much "
            + "burning or earning.", min = 5, max = 300)
    public int saveSeconds = 30;

    // ------------------------------------------------------------------ messages

    @Setting(path = "fuel.join-message", desc = "Tell members on join when their island is asleep (\"ran out of fuel 2h ago\").")
    public boolean joinMessage = true;

    @Setting(path = "fuel.asleep-message", desc = "Tell online members once when their island falls asleep.")
    public boolean asleepMessage = true;

    @Setting(path = "fuel.join-delay-ticks", desc = "Ticks after joining before the join message (the profile loads first).",
            min = 1, max = 1_200)
    public int joinDelayTicks = 60;

    // ------------------------------------------------------------------ Fuel Cells

    @Setting(path = "cells.small-minutes", desc = "Fuel in the small Fuel Cell (PD4: 1 h).", min = 1, max = 2_880, level = Setting.Level.OWNER)
    public int smallCellMinutes = 60;

    @Setting(path = "cells.medium-minutes", desc = "Fuel in the medium Fuel Cell (PD4: 3 h).", min = 1, max = 2_880, level = Setting.Level.OWNER)
    public int mediumCellMinutes = 180;

    @Setting(path = "cells.large-minutes", desc = "Fuel in the large Fuel Cell (PD4: 6 h).", min = 1, max = 2_880, level = Setting.Level.OWNER)
    public int largeCellMinutes = 360;

    @Setting(path = "cells.max-per-hand-out", desc = "Most Fuel Cells one reward may turn into (safety net against a typo in a "
            + "reward table).", min = 1, max = 64)
    public int maxCellsPerHandOut = 16;

    @Setting(path = "cells.material", desc = "Item of Fuel Cells (read at start).", restart = true)
    public String cellMaterial = "magma_cream";

    @Setting(path = "cells.save-player-data", desc = "Save the player file right after a Fuel Cell was used (so a crash can't bring "
            + "the item back).")
    public boolean savePlayerData = true;

    // ------------------------------------------------------------------ menus and sounds

    @Setting(path = "menu.gauge-material", desc = "Item of the fuel gauge in the island menu.")
    public String gaugeMaterial = "blaze_powder";

    @Setting(path = "sounds.cell", desc = "Sound (sounds.yml key) when a Fuel Cell adds fuel.")
    public String cellSound = "success";

    @Setting(path = "sounds.asleep", desc = "Sound (sounds.yml key) when members hear their island fell asleep.")
    public String asleepSound = "error";

    @Override
    public String fileName() {
        return "production.yml";
    }

    @Override
    public String header() {
        return "Island production (PHASE-7). M7.1 Island Fuel: states, tank, burn, Fuel Cells and the fuel gate.\n"
                + "OFF by default: set fuel.enabled: true, then /skyblock reload production.\n"
                + "Texts: messages/production.yml. Reload: /skyblock reload production";
    }

    /** The cell sizes, largest first (minutes, duplicates and broken values left out). */
    long[] cellSizes() {
        return FuelMath.cellSizes(smallCellMinutes, mediumCellMinutes, largeCellMinutes);
    }
}
