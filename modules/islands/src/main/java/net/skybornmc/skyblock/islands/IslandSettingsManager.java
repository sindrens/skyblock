package net.skybornmc.skyblock.islands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /is settings [setting] [value]} (ISL2): the settings menu ({@link SettingsMenu}) or a direct change. Who may
 * change: stored members whose role has the {@code settings} permission (default Leader + Admin, see
 * {@link MemberRules#canChangeSettings}); every member can look. Without a value a toggle flips and a choice moves to
 * the next value. Saved through {@link IslandManager#setFlag} (atomic, logged {@code island_setting}); the members are
 * told.
 */
final class IslandSettingsManager {

    private final IslandsModule module;
    private final IslandManager islands;
    private final Set<UUID> busy = new HashSet<>();

    IslandSettingsManager(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
    }

    /** True if {@code player} may change the settings of {@code island}. */
    boolean canChange(Island island, Player player) {
        return MemberRules.canChangeSettings(island.roleOrNull(player.getUniqueId()),
                islands.roleAllows(island, player.getUniqueId(), IslandPerm.SETTINGS));
    }

    /** Opens the settings menu of {@code island} (from {@code /is members}: Back returns there). */
    void openFor(Player player, Island island, net.skybornmc.skyblock.common.menu.Menu parent) {
        SettingsMenu menu = new SettingsMenu(module, islands, this, island);
        if (parent != null && parent.isOpen()) {
            parent.openChild(menu);
        } else {
            module.core().menus().open(player, menu);
        }
    }

    /** {@code /is settings [setting] [value]}. */
    void command(Player player, String flagKey, String value) {
        Optional<Island> own = islands.islandOfPlayer(player.getUniqueId());
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        if (flagKey == null) {
            module.core().menus().open(player, new SettingsMenu(module, islands, this, island));
            return;
        }
        Optional<IslandFlag> flag = IslandFlag.byKey(flagKey);
        if (flag.isEmpty()) {
            islands.fail(player, "flags.unknown", Msg.p("setting", flagKey), Msg.p("settings", String.join(", ", keys())));
            return;
        }
        String next;
        if (value == null) {
            next = flag.get().next(islands.flag(island, flag.get()));
        } else {
            Optional<String> parsed = flag.get().parse(value);
            if (parsed.isEmpty()) {
                islands.fail(player, "flags.bad-value", Msg.p("setting", name(flag.get())), Msg.p("value", value),
                        Msg.p("values", String.join(", ", flag.get().choices())));
                return;
            }
            next = parsed.get();
        }
        change(player, island, flag.get(), next, null);
    }

    /**
     * Changes one setting (command or menu click). {@code done} (nullable) runs on the main thread after the save
     * attempt, e.g. to refresh the menu.
     *
     * @return false if nothing was started (not allowed, same value, busy); {@code done} is not called then
     */
    boolean change(Player player, Island island, IslandFlag flag, String value, Runnable done) {
        if (islands.byId(island.id()) != island) {
            islands.fail(player, "error.island-gone");
            return false;
        }
        if (!canChange(island, player)) {
            islands.fail(player, "flags.not-allowed");
            return false;
        }
        if (value.equals(islands.flag(island, flag))) {
            module.msg().send(player, "flags.already", Msg.p("setting", name(flag)), Msg.p("value", valueName(value)));
            return false;
        }
        if (!busy.add(player.getUniqueId())) {
            module.msg().send(player, "member.busy");
            return false;
        }
        islands.setFlag(island.id(), flag, value, Actor.player(player)).whenComplete((ok, error) ->
                module.core().scheduler().ensureMain("island-setting-done", () -> {
                    busy.remove(player.getUniqueId());
                    if (error != null || !Boolean.TRUE.equals(ok)) {
                        if (player.isOnline()) {
                            islands.fail(player, "error.database");
                        }
                    } else {
                        if (player.isOnline()) {
                            module.msg().send(player, "flags.changed", Msg.p("setting", name(flag)), Msg.p("value", valueName(value)));
                            module.core().sounds().play(player, "success");
                        }
                        islands.memberManager().notifyMembers(island, player.getUniqueId(), "notify.setting",
                                Msg.p("setting", name(flag)), Msg.p("value", valueName(value)), Msg.p("by", player.getName()));
                    }
                    if (done != null) {
                        done.run();
                    }
                }));
        return true;
    }

    String name(IslandFlag flag) {
        return module.raw("flags." + flag.key() + ".label");
    }

    String valueName(String value) {
        return module.raw("flags.value." + value);
    }

    static List<String> keys() {
        return Arrays.stream(IslandFlag.cached()).map(IslandFlag::key).toList();
    }

    /** Tab completion of the value: the choices of the setting typed before it (on/off for toggles). */
    SuggestionProvider<CommandSourceStack> values(String flagArg) {
        return (ctx, builder) -> {
            for (String value : Suggest.filter(valuesFor(ctx, flagArg), builder.getRemaining())) {
                builder.suggest(value);
            }
            return builder.buildFuture();
        };
    }

    private static List<String> valuesFor(CommandContext<CommandSourceStack> ctx, String flagArg) {
        try {
            return IslandFlag.byKey(ctx.getArgument(flagArg, String.class)).map(IslandFlag::choices).orElse(List.of());
        } catch (IllegalArgumentException e) {
            return List.of(IslandFlag.ON, IslandFlag.OFF);
        }
    }

    void forget(UUID player) {
        busy.remove(player);
    }
}
