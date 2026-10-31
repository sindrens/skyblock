package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.common.quest.GameActionEvent;
import net.skybornmc.skyblock.common.sell.ItemsSoldEvent;
import net.skybornmc.skyblock.common.tools.ToolLevelUpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;

/**
 * Quest progress from server events, never from the client (PHASE-10 M10.7). Every handler runs at MONITOR (after
 * protection: cancelled actions never count) and is O(open quests). The anti-abuse rules come with the events:
 * <ul>
 *   <li>sales: real sales only (the economy fires it after the money was paid), never the AFK pool
 *       ({@code rules.excluded-sell-sources});</li>
 *   <li>tool levels: the tools module fires a level-up only for XP the player earned with the tool;</li>
 *   <li>forge, enchant, visit, like: {@link GameActionEvent} after the owning module saved it (a visit counts once per island
 *       per day, own islands never);</li>
 *   <li>harvest: {@link GameActionEvent#HARVEST_CROP} from the production module, ripe crops on the own island only (members,
 *       never bone-mealed or placed blocks).</li>
 * </ul>
 * Depths blocks and kills come from {@link DepthsQuestListener}.
 */
final class QuestSources implements Listener {

    private final QuestsModule m;

    QuestSources(QuestsModule module) {
        this.m = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSold(ItemsSoldEvent event) {
        String source = event.source() == null ? "" : event.source().toLowerCase(Locale.ROOT);
        if (m.excludedSellSources().contains(source)) {
            return;
        }
        m.engine().count(event.getPlayer(), QuestType.SELL, 0, event.value());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToolLevel(ToolLevelUpEvent event) {
        int gained = event.getTool().level() - event.getFromLevel();
        m.engine().count(event.getPlayer(), QuestType.TOOL_LEVELS, 0, Math.max(1, gained));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAction(GameActionEvent event) {
        QuestType type = switch (event.action()) {
            case GameActionEvent.FORGE -> QuestType.FORGE;
            case GameActionEvent.ENCHANT -> QuestType.ENCHANT;
            case GameActionEvent.VISIT_ISLAND -> QuestType.VISIT;
            case GameActionEvent.LIKE_ISLAND -> QuestType.LIKE;
            case GameActionEvent.HARVEST_CROP -> QuestType.HARVEST;
            default -> null;
        };
        if (type != null) {
            m.engine().count(event.getPlayer(), type, 0, event.amount());
        }
    }
}
