package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.npc.NpcClickEvent;
import net.skybornmc.skyblock.common.pack.PackWait;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The start flow (owner 3 Nov 2026, replaces the join tutorial):
 * <ol>
 *   <li>a new player (and, with {@code start.island-for-islandless}, anyone without an island) gets the island created right
 *       after joining ({@link IslandService#enterFromPortal}, the same flow as the island portal at spawn; busy or refused
 *       = they stay at spawn and can use {@code /is});</li>
 *   <li>a boss bar "Go to spawn and talk to Rowan" and the scoreboard {@code objective} placeholder stay until the player
 *       talked to the guide NPC at spawn (role {@code start.guide-role}) or used {@code /start}; remembered per account
 *       ({@link StartRules#MET_KEY});</li>
 *   <li>the guide's click runs {@code /start}, which opens {@link StartMenu} (Island, Farm, Mine).</li>
 * </ol>
 * The guide's name is the NPC's name ({@code messages/npcs.yml roles.<role>.name}), so it is set in one place.
 */
final class StartGuide extends CommandGroup implements Listener {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    StartGuide(Core core, EssentialsConfig config, ModuleMessages msg) {
        super(core, config, msg);
    }

    @Override
    void register() {
        core.commands().register(CommandSpec.command("start")
                .description("Talk to the guide: your island, the farm and the mine").category("Info")
                .permission(Perms.START).playerOnly().executes(ctx -> open(ctx.player())));
        core.placeholders().registerText(MODULE, "objective", this::objective);
    }

    /** Hides every boss bar (module disable). */
    void shutdown() {
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = core.plugin().getServer().getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        bars.clear();
    }

    // ------------------------------------------------------------------ guide name, objective

    /** The guide's name as MiniMessage (the NPC's name; {@code start.guide-name} if the npcs texts are missing). */
    String guideName() {
        String npc = core.messages().raw("npcs", "roles." + config.startGuideRole.strip().toLowerCase(Locale.ROOT) + ".name");
        if (npc != null && !npc.isBlank()) {
            return npc;
        }
        String own = core.messages().raw(MODULE, "start.guide-name");
        return own == null ? "Rowan" : own;
    }

    private boolean met(Player player) {
        return core.players().get(player.getUniqueId()).map(d -> StartRules.met(d.setting(StartRules.MET_KEY, null)))
                .orElse(true); // not loaded: show nothing rather than a wrong bar
    }

    /** Scoreboard "Next:" text while the guide is not met yet; empty otherwise (the line hides). */
    private String objective(Player player) {
        if (player == null || !config.startEnabled || met(player)) {
            return "";
        }
        Component text = msg.component(player, "start.objective", Msg.mm("guide", guideName()));
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    // ------------------------------------------------------------------ join: island + boss bar

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean first = core.players().get(player.getUniqueId()).map(PlayerData::isNew).orElse(!player.hasPlayedBefore());
        core.scheduler().later("essentials:start", config.startIslandDelayTicks, () -> PackWait.run(core.services(), player, () -> {
            if (!player.isOnline()) {
                return;
            }
            startIsland(player, first);
            showBar(player);
        }));
    }

    /**
     * Owner 4 Nov 2026: a new profile starts on an island. Switching to a new (empty) profile and a profile wipe (the
     * player now plays on a fresh profile) get the island right away, like a first join; the portal flow still offers
     * pending invites first.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent event) {
        Player player = event.getPlayer();
        if (event.to() == null || event.to().playtimeMillis() > 0) {
            return; // an old profile (or one staff restored) is left as it is: only a new profile gets its island
        }
        core.scheduler().later("essentials:start-profile", Math.max(1, config.startIslandDelayTicks), () -> {
            if (player.isOnline()) {
                startIsland(player, true); // only when the new profile has no island yet
            }
        });
    }

    private void startIsland(Player player, boolean first) {
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        if (islands.isEmpty() || ProfileService.of(core.services()).isSwitching(player.getUniqueId())) {
            return;
        }
        GameMode mode = player.getGameMode();
        boolean playing = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
        boolean has = islands.get().islandOf(player.getUniqueId()).isPresent();
        if (!StartRules.createIsland(first, has, config.startIslandOnFirstJoin, config.startIslandForIslandless, playing)) {
            return;
        }
        // The island portal's flow: creates the island (invite choice, join block, starter chest) and brings them there.
        // BUSY / REFUSED: the islands module told the player; they stay at spawn and can use /is.
        islands.get().enterFromPortal(player, created -> showBar(player));
    }

    private void showBar(Player player) {
        if (!player.isOnline() || !StartRules.showGuide(config.startEnabled, metValue(player)) || bars.containsKey(player.getUniqueId())) {
            return;
        }
        BossBar bar = BossBar.bossBar(msg.component(player, "start.bossbar", Msg.mm("guide", guideName())), 1f,
                color(config.startBarColor), overlay(config.startBarOverlay));
        bars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    private String metValue(Player player) {
        return core.players().get(player.getUniqueId()).map(d -> d.setting(StartRules.MET_KEY, null)).orElse("loading");
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bars.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ meeting the guide

    /** Clicking the guide NPC counts as talking to it (its action runs /start, which opens the menu). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpc(NpcClickEvent event) {
        if (StartRules.guideRole(event.role(), config.startGuideRole)) {
            meet(event.getPlayer());
        }
    }

    private void open(Player player) {
        meet(player);
        core.menus().open(player, new StartMenu(this));
    }

    /** Marks the guide as met (saved with the account), hides the bar and says hello once. */
    private void meet(Player player) {
        Optional<PlayerData> data = core.players().get(player.getUniqueId());
        if (data.isEmpty() || StartRules.met(data.get().setting(StartRules.MET_KEY, null))) {
            hideBar(player);
            return;
        }
        data.get().setSetting(StartRules.MET_KEY, Long.toString(System.currentTimeMillis()));
        hideBar(player);
        msg.send(player, "start.welcome", Msg.p("player", player.getName()), Msg.mm("guide", guideName()));
        core.sounds().play(player, "unlock");
    }

    // ------------------------------------------------------------------ menu helpers

    EssentialsConfig config() {
        return config;
    }

    private static BossBar.Color color(String raw) {
        try {
            return BossBar.Color.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return BossBar.Color.YELLOW;
        }
    }

    private static BossBar.Overlay overlay(String raw) {
        try {
            return BossBar.Overlay.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
