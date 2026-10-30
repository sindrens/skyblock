package net.skybornmc.skyblock.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.text.Sounds;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registers {@link CommandSpec}s on Paper's Brigadier and gives every command the same behavior:
 * permission-filtered visibility and tab completion, player/console-only checks, cooldowns, confirmation for
 * dangerous actions, typed arguments with clear errors, usage with a clickable example, configurable aliases
 * ({@code commands.yml}) and a help index.
 *
 * <p>Modules call {@link #register} in {@code enable()}; the tree is built when Paper fires the COMMANDS event.</p>
 */
public final class CommandService {

    /** Message file for framework texts: {@code messages/commands.yml}. */
    public static final String MESSAGES = "commands";
    public static final String PERM_COOLDOWN_BYPASS = "skyblock.cooldown.bypass";
    private static final String CONFIRM = "confirm";

    private record Node(CommandSpec spec, String path, CommandSpec top, List<String> permissions) {
    }

    private record Pending(String input, long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Sounds sounds;
    private final CommandsFile file;
    private final Map<String, CommandSpec> specs = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public CommandService(JavaPlugin plugin, Messages messages, Sounds sounds) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.file = new CommandsFile(new File(plugin.getDataFolder(), "commands.yml"));
        messages.load(MESSAGES);
        plugin.getServer().getPluginManager().registerEvents(new InputListener(), plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            List<CommandSpec> all;
            synchronized (specs) {
                all = List.copyOf(specs.values());
            }
            file.load(all).forEach(plugin.getLogger()::warning);
            for (CommandSpec spec : all) {
                event.registrar().register(literal(new Node(spec, spec.name(), spec, permissions(List.of(), spec))).build(),
                        spec.description(), file.get(spec).aliases());
            }
        });
    }

    /**
     * Adds a command. Validates the whole tree immediately (missing executors, bad argument order, …) so mistakes
     * show at startup with a clear message. A broken command is skipped and logged as SEVERE instead of disabling the
     * whole module (a bad {@code /pnote} once took all punishments down with it).
     */
    public void register(CommandSpec spec) {
        try {
            spec.validate(spec.name());
        } catch (IllegalStateException e) {
            plugin.getLogger().severe("Command not registered: " + e.getMessage());
            return;
        }
        if (specs.putIfAbsent(spec.name(), spec) != null) {
            throw new IllegalStateException("Command '/" + spec.name() + "' is registered twice");
        }
    }

    /** Re-reads {@code commands.yml} (cooldowns apply now, aliases after a restart) and the framework messages. */
    public List<String> reload() {
        messages.load(MESSAGES);
        List<CommandSpec> all;
        synchronized (specs) {
            all = List.copyOf(specs.values());
        }
        return file.load(all);
    }

    /** Current problems in {@code commands.yml}. */
    public List<String> problems() {
        return file.problems();
    }

    /** Every runnable, non-hidden command, in registration order. */
    public List<HelpEntry> helpIndex() {
        List<HelpEntry> out = new ArrayList<>();
        synchronized (specs) {
            for (CommandSpec spec : specs.values()) {
                collect(new Node(spec, spec.name(), spec, permissions(List.of(), spec)), out);
            }
        }
        return out;
    }

    /** The help entries {@code sender} may use (all permissions on the path). */
    public List<HelpEntry> helpIndex(CommandSender sender) {
        return helpIndex().stream().filter(e -> e.permissions().stream().allMatch(sender::hasPermission)).toList();
    }

    private void collect(Node node, List<HelpEntry> out) {
        CommandSpec spec = node.spec();
        if (spec.executor() != null && !spec.isHidden()) {
            out.add(new HelpEntry(node.path(), spec.usage(node.path()), example(node), spec.description(), spec.category(),
                    node.permissions(), spec == node.top() ? file.get(spec).aliases() : List.of(), spec.isPlayerOnly()));
        }
        for (CommandSpec child : spec.children()) {
            collect(new Node(child, node.path() + " " + child.name(), node.top(), permissions(node.permissions(), child)), out);
        }
    }

    private static List<String> permissions(List<String> parent, CommandSpec spec) {
        if (spec.permission() == null || spec.permission().isBlank()) {
            return parent;
        }
        List<String> list = new ArrayList<>(parent);
        list.add(spec.permission());
        return List.copyOf(list);
    }

    /** Forgets a player's cooldowns (staff tools / tests). */
    public void clearCooldowns(java.util.UUID player) {
        String prefix = player + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ---------------------------------------------------------------- tree building

    private LiteralArgumentBuilder<CommandSourceStack> literal(Node node) {
        CommandSpec spec = node.spec();
        LiteralArgumentBuilder<CommandSourceStack> lit = Commands.literal(spec.name());
        if (spec.permission() != null && !spec.permission().isBlank()) {
            lit.requires(src -> src.getSender().hasPermission(spec.permission()));
        }
        for (CommandSpec child : spec.children()) {
            lit.then(literal(new Node(child, node.path() + " " + child.name(), node.top(), permissions(node.permissions(), child))));
        }

        List<Arg<?>> args = spec.args();
        ArgumentBuilder<CommandSourceStack, ?> next = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            Arg<?> arg = args.get(i);
            RequiredArgumentBuilder<CommandSourceStack, String> argNode = Commands.argument(arg.name(), arg.brigadierType());
            argNode.suggests(arg.suggestions());
            boolean runnable = i + 1 >= spec.requiredArgs();
            argNode.executes(ctx -> runnable ? execute(ctx, node, false) : usage(ctx.getSource().getSender(), node));
            if (runnable && spec.confirmSeconds() > 0) {
                argNode.then(Commands.literal(CONFIRM).executes(ctx -> execute(ctx, node, true)));
            }
            if (next != null) {
                argNode.then(next);
            }
            next = argNode;
        }
        if (next != null) {
            lit.then(next);
        }
        if (spec.executor() != null && spec.requiredArgs() == 0) {
            lit.executes(ctx -> execute(ctx, node, false));
            if (spec.confirmSeconds() > 0) {
                lit.then(Commands.literal(CONFIRM).executes(ctx -> execute(ctx, node, true)));
            }
        } else {
            lit.executes(ctx -> usage(ctx.getSource().getSender(), node));
        }
        return lit;
    }

    // ---------------------------------------------------------------- wrong input (FIXES1)

    /**
     * Before Brigadier parses a player's line: input our tree can't take (unknown sub-command, words after the last
     * argument) gets our own usage line instead of Brigadier's grey "Incorrect argument for command ... <--[HERE]".
     */
    private final class InputListener implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
        public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
            List<String> words = InputCheck.words(event.getMessage());
            if (words.isEmpty()) {
                return;
            }
            CommandSpec top = find(words.getFirst());
            Player player = event.getPlayer();
            if (top == null || (top.permission() != null && !top.permission().isBlank() && !player.hasPermission(top.permission()))) {
                return;
            }
            InputCheck.Problem problem = InputCheck.check(top, words.subList(1, words.size()), player::hasPermission);
            if (problem == null) {
                return;
            }
            event.setCancelled(true);
            Node node = new Node(problem.spec(), problem.path(), top, permissions(List.of(), problem.spec()));
            messages.send(player, MESSAGES, "unknown-input", Msg.p("input", problem.input()), Msg.p("usage", usageText(player, node)),
                    Msg.p("example", example(node)), Msg.p("description", problem.spec().description()));
            playError(player);
        }
    }

    /** Our top-level command for a typed label (name or alias, with or without the plugin namespace), or null. */
    private CommandSpec find(String label) {
        String name = label.toLowerCase(java.util.Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            if (!name.substring(0, colon).equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))) {
                return null;
            }
            name = name.substring(colon + 1);
        }
        synchronized (specs) {
            CommandSpec direct = specs.get(name);
            if (direct != null) {
                return direct;
            }
            for (CommandSpec spec : specs.values()) {
                if (file.get(spec).aliases().contains(name)) {
                    return spec;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- execution

    private int usage(CommandSender sender, Node node) {
        CommandSpec spec = node.spec();
        messages.send(sender, MESSAGES, "usage", Msg.p("usage", usageText(sender, node)), Msg.p("example", example(node)),
                Msg.p("description", spec.description()));
        playError(sender);
        return 0;
    }

    /** {@code /is <create|home|…>} (only sub-commands the sender may use) or {@code /pay <player> <amount>}. */
    private static String usageText(CommandSender sender, Node node) {
        CommandSpec spec = node.spec();
        if (!spec.children().isEmpty()) {
            List<String> visible = spec.children().stream()
                    .filter(c -> c.permission() == null || c.permission().isBlank() || sender.hasPermission(c.permission()))
                    .map(CommandSpec::name).toList();
            if (spec.executor() == null) {
                return "/" + node.path() + " <" + String.join("|", visible) + ">";
            }
            StringBuilder sb = new StringBuilder(spec.usage(node.path()).replaceFirst(" \\[[^\\]]*\\]$", ""));
            return visible.isEmpty() ? sb.toString() : sb.append(" [").append(String.join("|", visible)).append(']').toString();
        }
        return spec.usage(node.path());
    }

    private int execute(CommandContext<CommandSourceStack> bctx, Node node, boolean viaConfirm) {
        CommandSender sender = bctx.getSource().getSender();
        CommandSpec spec = node.spec();
        String input = stripSlash(bctx.getInput());
        try {
            if (spec.isPlayerOnly() && !(sender instanceof Player)) {
                throw CommandFailure.of("players-only");
            }
            if (spec.isConsoleOnly() && sender instanceof Player) {
                throw CommandFailure.of("console-only");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Arg<?> arg : spec.args()) {
                String raw;
                try {
                    raw = bctx.getArgument(arg.name(), String.class);
                } catch (IllegalArgumentException absent) {
                    continue;
                }
                values.put(arg.name(), arg.parse(sender, raw));
            }
            Duration cooldown = spec == node.top() ? file.get(spec).cooldown() : spec.cooldown();
            String cooldownKey = sender instanceof Player p ? p.getUniqueId() + ":" + node.path() : null;
            checkCooldown(sender, cooldownKey);

            String base = viaConfirm && input.endsWith(" " + CONFIRM) ? input.substring(0, input.length() - CONFIRM.length() - 1) : input;
            CmdContext context = new CmdContext(sender, values, base);
            if (spec.needsConfirm(context)) {
                String senderKey = sender instanceof Player p ? p.getUniqueId().toString() : "console:" + sender.getName();
                if (!viaConfirm) {
                    pending.put(senderKey, new Pending(input, System.currentTimeMillis() + spec.confirmSeconds() * 1000L));
                    TagResolver[] extra = spec.confirmPlaceholders(context);
                    TagResolver[] all = java.util.Arrays.copyOf(extra, extra.length + 2);
                    all[extra.length] = Msg.p("command", input);
                    all[extra.length + 1] = Msg.p("time", Format.duration(Duration.ofSeconds(spec.confirmSeconds())));
                    if (spec.confirmKey() != null) {
                        messages.send(sender, spec.confirmModule(), spec.confirmKey(), all);
                    } else {
                        messages.send(sender, MESSAGES, "confirm", all);
                    }
                    if (sender instanceof Player p) {
                        sounds.play(p, "warning");
                    }
                    return Command.SINGLE_SUCCESS;
                }
                Pending request = pending.remove(senderKey);
                if (request == null || request.expiresAt() < System.currentTimeMillis() || !request.input().equalsIgnoreCase(base)) {
                    throw CommandFailure.of("confirm-expired", Msg.p("command", base));
                }
            }

            spec.executor().run(context);
            if (cooldownKey != null && !cooldown.isZero() && !sender.hasPermission(PERM_COOLDOWN_BYPASS)) {
                cooldowns.put(cooldownKey, System.currentTimeMillis() + cooldown.toMillis());
            }
            return Command.SINGLE_SUCCESS;
        } catch (CommandFailure failure) {
            messages.send(sender, failure.module(), failure.key(), failure.placeholders());
            playError(sender);
            return 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Command '/" + input + "' by " + sender.getName() + " failed", e);
            messages.send(sender, MESSAGES, "error");
            playError(sender);
            return 0;
        }
    }

    private void checkCooldown(CommandSender sender, String key) {
        if (key == null || sender.hasPermission(PERM_COOLDOWN_BYPASS)) {
            return;
        }
        Long until = cooldowns.get(key);
        long now = System.currentTimeMillis();
        if (until == null) {
            return;
        }
        if (until <= now) {
            cooldowns.remove(key);
            return;
        }
        throw CommandFailure.of("cooldown", Msg.p("time", Format.duration(Duration.ofMillis(until - now + 999))));
    }

    private String example(Node node) {
        String example = node.spec().example();
        if (example != null && !example.isBlank()) {
            return "/" + stripSlash(example);
        }
        return "/" + node.path() + " ";
    }

    private static String stripSlash(String input) {
        return input.startsWith("/") ? input.substring(1) : input;
    }

    private void playError(CommandSender sender) {
        if (sender instanceof Player p) {
            sounds.play(p, "error");
        }
    }
}
