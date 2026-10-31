package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

/**
 * The short help shown when a player clicks an NPC (help role or sneak-click) or a clickable hologram (owner answer
 * 44: things explained where they are, while item lores stay clean). Java: a few chat lines with a clickable
 * {@code [Open]}; Bedrock (no clickable chat): a small 3-row menu with the same text and an Open button.
 */
final class HelpDialog {

    private final NpcsModule module;

    HelpDialog(NpcsModule module) {
        this.module = module;
    }

    /** Help of an NPC role ({@code roles.<role>.help}). */
    void showNpc(Player player, Spots.Npc npc, RoleRules.Action action) {
        String nameRaw = module.npcNameRaw(npc.id(), npc.role());
        String key = "roles." + npc.role() + ".help";
        show(player, nameRaw, module.msg().has(key) ? key : "help.missing", action.hasCommand() ? action.command() : null);
    }

    /** True when the NPC's role has its own help text. */
    boolean hasNpcHelp(Spots.Npc npc) {
        return module.msg().has("roles." + npc.role() + ".help");
    }

    /**
     * The short help of an action NPC as plain chat lines, printed next to running its action (owner U5: one click talks
     * and explains). No [Open] button and no Bedrock menu: the action opens its own screen, and plain chat reads on both.
     */
    void printNpc(Player player, Spots.Npc npc) {
        String key = "roles." + npc.role() + ".help";
        if (!module.msg().has(key)) {
            return;
        }
        player.sendMessage(module.msg().component(player, "help.header", Msg.mm("name", module.npcNameRaw(npc.id(), npc.role()))));
        player.sendMessage(module.msg().component(player, key));
    }

    /** Help of a hologram or leaderboard ({@code hologram-help.<id>}). */
    void showHologram(Player player, Placed placed) {
        String titleRaw = module.core().messages().raw(NpcsModule.ID, "hologram-names." + placed.pointId());
        if (titleRaw == null || titleRaw.isBlank()) {
            titleRaw = module.core().messages().raw(NpcsModule.ID, "leaderboard.titles." + placed.pointId());
        }
        if (titleRaw == null || titleRaw.isBlank()) {
            titleRaw = NpcsModule.pretty(placed.pointId());
        }
        String key = "hologram-help." + placed.pointId();
        show(player, titleRaw, module.msg().has(key) ? key : "help.missing", null);
    }

    private void show(Player player, String nameRaw, String linesKey, String command) {
        module.core().sounds().play(player, "click");
        if (module.core().bedrock().isBedrock(player.getUniqueId())) {
            module.core().menus().open(player, new HelpMenu(module, nameRaw, linesKey, command));
            return;
        }
        player.sendMessage(module.msg().component(player, "help.header", Msg.mm("name", nameRaw)));
        player.sendMessage(module.msg().component(player, linesKey));
        if (command != null) {
            Component open = module.msg().component(player, "help.open")
                    .clickEvent(ClickEvent.runCommand("/" + command))
                    .hoverEvent(HoverEvent.showText(module.msg().component(player, "help.open-hover", Msg.p("command", "/" + command))));
            player.sendMessage(open);
        }
    }
}
