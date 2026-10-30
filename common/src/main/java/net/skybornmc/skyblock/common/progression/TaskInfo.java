package net.skybornmc.skyblock.common.progression;

import java.util.List;

/**
 * One task of the challenge book ({@code tasks.yml}, PHASE-5 M5.3).
 *
 * @param id       unique id, e.g. {@code t1_mine}
 * @param type     what is counted or reached
 * @param target   block/entity/item/skill/pillar id, {@code custom:<id>} for custom deliver items, or {@link TaskType#ANY}
 * @param amount   goal: count for counted types, level for reach types (at least 1)
 * @param tier     task tier (chapter of the book; uses Depths tier n content)
 * @param unlocks  keys this task is required for, e.g. {@code upgrade.island_size.1}, {@code mastery.timber.2}
 *                 (an unlock key is open when every task listing it is done, see {@link TaskService#unlocked})
 * @param crystals island crystals paid once to every member when the task is done
 * @param pillar   pillar that gets {@code pillarXp} when the task is done (null = none)
 * @param pillarXp base pillar XP reward (island boosts apply on top)
 */
public record TaskInfo(String id, TaskType type, String target, long amount, int tier, List<String> unlocks,
                       long crystals, Pillar pillar, double pillarXp) {

    public TaskInfo {
        unlocks = unlocks == null ? List.of() : List.copyOf(unlocks);
    }

    /** The shared island counter this task reads ({@code mine:coal_ore}); deliver tasks have their own ({@code deliver:<id>}). */
    public String counterKey() {
        if (!type.counted()) {
            return null;
        }
        return type == TaskType.DELIVER ? "deliver:" + id : type.id() + ":" + target;
    }
}
