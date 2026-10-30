package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the challenge book (PHASE-5 M5.3): {@code tasks-settings.yml}. The tasks themselves are the catalog
 * {@code tasks.yml} (one section, every key a task, no plain settings there).
 */
public final class TasksConfig extends ModuleConfig {

    @Override
    public String fileName() {
        return "tasks-settings.yml";
    }

    @Override
    public String header() {
        return """
                Challenge book settings (Phase 5, M5.3). The tasks are in tasks.yml. Progress is shared by every member of an
                island (never coop players or visitors) and counts from the island's creation. Texts: messages/progression.yml.
                Reload: /skyblock reload progression""";
    }

    @Setting(path = "enabled", desc = "Count task progress and show the challenge book (off = nothing is counted or completed).")
    public boolean enabled = true;

    @Setting(path = "flush-seconds", desc = "Task counters are kept in memory and written in batches this often (and on shutdown). Deliveries are saved at once.", min = 2, max = 300)
    public int flushSeconds = 10;

    @Setting(path = "check-seconds", desc = "How often islands with online members are checked for finished tasks (skill, pillar and island level tasks, new tasks).", min = 5, max = 600)
    public int checkSeconds = 30;

    @Setting(path = "placed-memory", desc = "Anti place-and-break: how many placed block positions each island remembers (mine/chop/farm targets only). Breaking them again counts nothing.", min = 0, max = 100_000)
    public int placedMemory = 5000;

    @Setting(path = "natural-harvest", desc = "Harvest blocks without a ripe age that count for farm tasks when no player placed them (sugar cane, melons ...).", level = Setting.Level.OWNER)
    public List<String> naturalHarvest = List.of("sugar_cane", "cactus", "bamboo", "melon", "pumpkin", "torchflower");

    @Setting(path = "kill-damage-causes", desc = "Last damage that must have killed the mob for kill tasks (the player's own hit or arrow). Fall, lava and stack kills count nothing.", level = Setting.Level.OWNER)
    public List<String> killCauses = List.of("ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "PROJECTILE");

    @Setting(path = "excluded-sell-sources", desc = "Sale sources that never count for sell/earn tasks (afk = the AFK pool).", level = Setting.Level.OWNER)
    public List<String> excludedSellSources = List.of("afk");

    @Setting(path = "notify.chat", desc = "Tell online members in chat when a task is done (each player can hide it in /settings).")
    public boolean notifyChat = true;

    @Setting(path = "notify.action-bar", desc = "Show a short action bar line to online members when a task is done.")
    public boolean notifyActionBar = true;

    @Setting(path = "notify.sound", desc = "Sound key (sounds.yml) for a finished task.")
    public String doneSound = "unlock";

    @Setting(path = "deliver.sound", desc = "Sound key (sounds.yml) for a successful hand-in.")
    public String deliverSound = "success";
}
