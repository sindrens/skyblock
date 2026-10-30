package net.skybornmc.skyblock.common.command;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Declarative command definition. Example:
 * <pre>{@code
 * Arg<String> target = Args.offlinePlayer("player");
 * commands.register(CommandSpec.command("unban")
 *         .description("Unban a player or IP")
 *         .category("Staff")
 *         .permission("skyblock.staff.unban")
 *         .arg(target)
 *         .example("unban Steve")
 *         .executes(ctx -> unban(ctx.sender(), ctx.get(target))));
 * }</pre>
 * Trees: {@code CommandSpec.command("skyblock").sub(CommandSpec.sub("reload")…).sub(…)}.
 * A node can have both an executor and sub-commands.
 */
public final class CommandSpec {

    /** Command body. Throw {@link CommandFailure} for player-facing errors. */
    @FunctionalInterface
    public interface Executor {
        void run(CmdContext ctx) throws Exception;
    }

    private final String name;
    private String description = "";
    private String category = "General";
    private String permission;
    private boolean playerOnly;
    private boolean consoleOnly;
    private boolean hidden;
    private Duration cooldown = Duration.ZERO;
    private int confirmSeconds;
    private java.util.function.Predicate<CmdContext> confirmWhen;
    private String confirmModule;
    private String confirmKey;
    private java.util.function.Function<CmdContext, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]> confirmPlaceholders;
    private String example;
    private final List<String> aliases = new ArrayList<>();
    private final List<Arg<?>> args = new ArrayList<>();
    private int requiredArgs;
    private final List<CommandSpec> children = new ArrayList<>();
    private Executor executor;

    private CommandSpec(String name) {
        if (name == null || !name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid command name '" + name + "'");
        }
        this.name = name;
    }

    /** A top-level command ({@code /name}). */
    public static CommandSpec command(String name) {
        return new CommandSpec(name);
    }

    /** A sub-command (a literal inside another command). Same builder, different intent. */
    public static CommandSpec sub(String name) {
        return new CommandSpec(name);
    }

    public CommandSpec description(String text) {
        this.description = Objects.requireNonNull(text);
        return this;
    }

    /** Help category, e.g. "Island", "Economy", "Staff". Sub-commands inherit the parent's when left as default. */
    public CommandSpec category(String category) {
        this.category = Objects.requireNonNull(category);
        return this;
    }

    /** Needed to see, tab-complete and run this node (and everything below it). */
    public CommandSpec permission(String permission) {
        this.permission = permission;
        return this;
    }

    public CommandSpec playerOnly() {
        this.playerOnly = true;
        return this;
    }

    public CommandSpec consoleOnly() {
        this.consoleOnly = true;
        return this;
    }

    /** Not listed in the help index (still tab-completes for those with permission). */
    public CommandSpec hidden() {
        this.hidden = true;
        return this;
    }

    /** Per-player cooldown after a successful run (bypass: {@code skyblock.cooldown.bypass}). Overridable in commands.yml. */
    public CommandSpec cooldown(Duration cooldown) {
        this.cooldown = Objects.requireNonNull(cooldown);
        return this;
    }

    /** Dangerous action: first run asks to confirm (click or {@code /<cmd> confirm}) within {@code seconds}. */
    public CommandSpec confirm(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("confirm seconds must be > 0");
        }
        this.confirmSeconds = seconds;
        return this;
    }

    /**
     * Asks to confirm only when {@code when} is true for the parsed arguments (e.g. {@code /pay} above $1M); otherwise the
     * command runs at once. Typing {@code confirm} when no confirmation is needed simply runs the command.
     */
    public CommandSpec confirmIf(int seconds, java.util.function.Predicate<CmdContext> when) {
        confirm(seconds);
        this.confirmWhen = Objects.requireNonNull(when, "when");
        return this;
    }

    /**
     * Uses {@code key} in {@code messages/<module>.yml} instead of the generic confirm text. Placeholders {@code <command>}
     * (the line to run with {@code confirm}, without slash) and {@code <time>} are added to {@code placeholders}' result.
     */
    public CommandSpec confirmMessage(String module, String key,
                                      java.util.function.Function<CmdContext, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]> placeholders) {
        this.confirmModule = Objects.requireNonNull(module, "module");
        this.confirmKey = Objects.requireNonNull(key, "key");
        this.confirmPlaceholders = Objects.requireNonNull(placeholders, "placeholders");
        return this;
    }

    /** Default aliases (top-level only); the owner can change them in commands.yml. */
    public CommandSpec aliases(String... aliases) {
        Collections.addAll(this.aliases, aliases);
        return this;
    }

    /** Clickable example without the slash, e.g. {@code "pay Steve 1.5k"}. */
    public CommandSpec example(String example) {
        this.example = example;
        return this;
    }

    /** A required argument. Required arguments must come before optional ones. */
    public CommandSpec arg(Arg<?> arg) {
        if (args.size() > requiredArgs) {
            throw new IllegalStateException("Command '" + name + "': required argument '" + arg.name() + "' after an optional one");
        }
        addArg(arg);
        requiredArgs++;
        return this;
    }

    /** An optional trailing argument (read it with {@link CmdContext#getOr}). */
    public CommandSpec optional(Arg<?> arg) {
        addArg(arg);
        return this;
    }

    private void addArg(Arg<?> arg) {
        Objects.requireNonNull(arg, "arg");
        if (!args.isEmpty() && args.getLast().greedy()) {
            throw new IllegalStateException("Command '" + name + "': nothing can follow the text argument '" + args.getLast().name() + "'");
        }
        if (args.stream().anyMatch(a -> a.name().equals(arg.name()))) {
            throw new IllegalStateException("Command '" + name + "': duplicate argument '" + arg.name() + "'");
        }
        args.add(arg);
    }

    public CommandSpec sub(CommandSpec child) {
        if (children.stream().anyMatch(c -> c.name.equals(child.name))) {
            throw new IllegalStateException("Command '" + name + "': duplicate sub-command '" + child.name + "'");
        }
        children.add(child);
        return this;
    }

    public CommandSpec executes(Executor executor) {
        this.executor = Objects.requireNonNull(executor);
        return this;
    }

    // ---------------------------------------------------------------- accessors (package)

    public String name() {
        return name;
    }

    String description() {
        return description;
    }

    String category() {
        return category;
    }

    String permission() {
        return permission;
    }

    boolean isPlayerOnly() {
        return playerOnly;
    }

    boolean isConsoleOnly() {
        return consoleOnly;
    }

    boolean isHidden() {
        return hidden;
    }

    Duration cooldown() {
        return cooldown;
    }

    int confirmSeconds() {
        return confirmSeconds;
    }

    String example() {
        return example;
    }

    /** Whether this run needs a confirmation (always when {@link #confirm} was used without a condition). */
    boolean needsConfirm(CmdContext ctx) {
        return confirmSeconds > 0 && (confirmWhen == null || confirmWhen.test(ctx));
    }

    String confirmModule() {
        return confirmModule;
    }

    String confirmKey() {
        return confirmKey;
    }

    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] confirmPlaceholders(CmdContext ctx) {
        return confirmPlaceholders == null ? new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]
                : confirmPlaceholders.apply(ctx);
    }

    List<String> aliases() {
        return aliases;
    }

    List<Arg<?>> args() {
        return args;
    }

    int requiredArgs() {
        return requiredArgs;
    }

    List<CommandSpec> children() {
        return children;
    }

    Executor executor() {
        return executor;
    }

    /** Checks the tree before registration so mistakes fail at startup, not when a player types. */
    void validate(String path) {
        if (executor == null && children.isEmpty()) {
            throw new IllegalStateException("Command '/" + path + "' has no executor and no sub-commands");
        }
        if (executor == null && !args.isEmpty()) {
            throw new IllegalStateException("Command '/" + path + "' has arguments but no executor");
        }
        if (confirmSeconds > 0 && !args.isEmpty() && args.getLast().greedy()) {
            throw new IllegalStateException("Command '/" + path + "': confirm() can't be combined with a trailing text argument");
        }
        if (playerOnly && consoleOnly) {
            throw new IllegalStateException("Command '/" + path + "' can't be both player-only and console-only");
        }
        // Sub-commands next to arguments are allowed only when every argument is optional (e.g. /skills [player] plus
        // /skills set|add): Brigadier tries the literal sub-commands first, then the argument.
        if (!args.isEmpty() && !children.isEmpty() && requiredArgs() > 0) {
            throw new IllegalStateException("Command '/" + path + "' mixes required arguments and sub-commands; make the arguments optional or put them on a sub-command");
        }
        for (CommandSpec child : children) {
            if (child.category.equals("General")) {
                child.category = category;
            }
            child.validate(path + " " + child.name);
        }
    }

    /** {@code "/unban <player>"} or {@code "/skyblock <modules|reload>"}. */
    String usage(String path) {
        StringBuilder sb = new StringBuilder("/").append(path);
        for (int i = 0; i < args.size(); i++) {
            sb.append(' ').append(i < requiredArgs ? "<" : "[").append(args.get(i).name()).append(i < requiredArgs ? ">" : "]");
        }
        if (!children.isEmpty()) {
            sb.append(' ').append(executor == null ? "<" : "[");
            sb.append(String.join("|", children.stream().map(c -> c.name).toList()));
            sb.append(executor == null ? ">" : "]");
        }
        return sb.toString();
    }
}
