package net.skybornmc.skyblock.moderation;

import org.bukkit.Material;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * One validated, immutable {@code punishments.yml}. Texts are never here (they live in {@code messages/moderation.yml});
 * this only holds ids, slots, icons, ladders and rules.
 */
record PunishConfig(List<Templates.Category> categories, MenuLayout menu, WarnRules.Settings warnRules, Extras extras,
                    RuleBook rules) {

    static final PunishConfig EMPTY = new PunishConfig(List.of(), MenuLayout.EMPTY, WarnRules.Settings.DISABLED, Extras.DEFAULT,
            RuleBook.EMPTY);

    /** Without rule numbers (spec 14.2: every reason falls back to {@code extras.rules-link}). */
    PunishConfig(List<Templates.Category> categories, MenuLayout menu, WarnRules.Settings warnRules, Extras extras) {
        this(categories, menu, warnRules, extras, RuleBook.EMPTY);
    }

    PunishConfig(List<Templates.Category> categories, MenuLayout menu, WarnRules.Settings warnRules) {
        this(categories, menu, warnRules, Extras.DEFAULT);
    }

    /**
     * {@code extras:} (spec 12.13). P2 + P3 keys; later milestones add theirs.
     *
     * @param undoWindow       how long [Undo] works after a punishment (12.1)
     * @param duplicateWindow  same target + reason within this time is blocked (12.2)
     * @param duplicateConfirm a second click within this time applies anyway (12.2)
     * @param offenceExpire    default for reasons without their own {@code offence-expire}; {@code null} = never (12.3)
     * @param statsZone        time zone of the /pstats periods (owner: Europe/Oslo)
     * @param statsCache       how long /pstats results are reused
     * @param statsPeriod      period when none is typed
     * @param statsTop         lines in /pstats top
     * @param closeReports     close the punishing staff member's claimed reports about the target (12.9)
     * @param evidence         chat evidence saved with mutes/warns (12.4)
     * @param notes            staff notes (12.5)
     * @param ack              warning acknowledgement (12.6)
     * @param rulesLink        link in the warning message (12.6)
     * @param discord          Discord log (12.11; the webhook URL itself is a secret, never here)
     * @param playerView       /mypunishments (12.12)
     * @param chatSuggestions  detector settings for the suggestion API (12.8), name → settings
     * @param approval         approval for big punishments (13.4)
     * @param watchlist        watchlist (13.5)
     * @param probation        probation after a natural end (13.7)
     * @param muteEnded        "mute ended" / "welcome back" message (13.13)
     * @param bulk             bulk punish (13.8)
     * @param appeal           appeals (13.10)
     * @param neverAuto        reason ids never applied without a staff click, also in warn-rules auto mode (13.3)
     * @param lockdown         raid lockdown (13.9)
     * @param raid             raid detection (13.9)
     * @param network          network-ready storage and scope filter (13.12)
     * @param sanctions        economy and item sanctions (13.1)
     * @param training         training mode for new staff (14.6)
     * @param notifications    notification settings: always-on types, Discord channel per type (14.9)
     */
    record Extras(Duration undoWindow, Duration duplicateWindow, Duration duplicateConfirm, Duration offenceExpire,
                  java.time.ZoneId statsZone, Duration statsCache, PunishStats.Period statsPeriod, int statsTop,
                  boolean closeReports, Evidence evidence, Notes notes, Ack ack, String rulesLink, Discord discord,
                  PlayerView playerView, Map<String, ChatSuggestion> chatSuggestions, Approval.Settings approval,
                  WatchSettings watchlist, Probation.Settings probation, MuteEnded muteEnded, Bulk bulk, Appeals appeal,
                  List<String> neverAuto, Lockdown.Settings lockdown, Lockdown.Raid raid, NetworkScope.Settings network,
                  SanctionRules.Settings sanctions, Training.Settings training, NotifyRules.Settings notifications) {

        /** The P7 set of keys; the P8 ones get their defaults. */
        Extras(Duration undoWindow, Duration duplicateWindow, Duration duplicateConfirm, Duration offenceExpire,
               java.time.ZoneId statsZone, Duration statsCache, PunishStats.Period statsPeriod, int statsTop,
               boolean closeReports, Evidence evidence, Notes notes, Ack ack, String rulesLink, Discord discord,
               PlayerView playerView, Map<String, ChatSuggestion> chatSuggestions, Approval.Settings approval,
               WatchSettings watchlist, Probation.Settings probation, MuteEnded muteEnded, Bulk bulk, Appeals appeal,
               List<String> neverAuto, Lockdown.Settings lockdown, Lockdown.Raid raid, NetworkScope.Settings network,
               SanctionRules.Settings sanctions) {
            this(undoWindow, duplicateWindow, duplicateConfirm, offenceExpire, statsZone, statsCache, statsPeriod, statsTop,
                    closeReports, evidence, notes, ack, rulesLink, discord, playerView, chatSuggestions, approval, watchlist,
                    probation, muteEnded, bulk, appeal, neverAuto, lockdown, raid, network, sanctions,
                    Training.Settings.DEFAULT, NotifyRules.Settings.DEFAULT);
        }

        /** The P6 set of keys; the P7 ones get their defaults. */
        Extras(Duration undoWindow, Duration duplicateWindow, Duration duplicateConfirm, Duration offenceExpire,
               java.time.ZoneId statsZone, Duration statsCache, PunishStats.Period statsPeriod, int statsTop,
               boolean closeReports, Evidence evidence, Notes notes, Ack ack, String rulesLink, Discord discord,
               PlayerView playerView, Map<String, ChatSuggestion> chatSuggestions, Approval.Settings approval,
               WatchSettings watchlist, Probation.Settings probation, MuteEnded muteEnded, Bulk bulk, Appeals appeal,
               List<String> neverAuto, Lockdown.Settings lockdown, Lockdown.Raid raid, NetworkScope.Settings network) {
            this(undoWindow, duplicateWindow, duplicateConfirm, offenceExpire, statsZone, statsCache, statsPeriod, statsTop,
                    closeReports, evidence, notes, ack, rulesLink, discord, playerView, chatSuggestions, approval, watchlist,
                    probation, muteEnded, bulk, appeal, neverAuto, lockdown, raid, network, SanctionRules.Settings.DEFAULT);
        }

        /** Shipped default of {@code never-auto} (spec 13.15). */
        static final List<String> NEVER_AUTO = List.of("hacking", "xray", "macro");

        static final Extras DEFAULT = new Extras(Duration.ofMinutes(2), Duration.ofSeconds(15), Duration.ofSeconds(5),
                Duration.ofDays(90), java.time.ZoneId.of("Europe/Oslo"), Duration.ofSeconds(60), PunishStats.Period.D7, 10,
                true, Evidence.DEFAULT, Notes.DEFAULT, Ack.DEFAULT, "https://skybornmc.net/rules", Discord.DEFAULT,
                PlayerView.DEFAULT, Map.of());

        /** The P3 set of keys; the P4 ones get their defaults. */
        Extras(Duration undoWindow, Duration duplicateWindow, Duration duplicateConfirm, Duration offenceExpire,
               java.time.ZoneId statsZone, Duration statsCache, PunishStats.Period statsPeriod, int statsTop,
               boolean closeReports, Evidence evidence, Notes notes, Ack ack, String rulesLink, Discord discord,
               PlayerView playerView, Map<String, ChatSuggestion> chatSuggestions) {
            this(undoWindow, duplicateWindow, duplicateConfirm, offenceExpire, statsZone, statsCache, statsPeriod, statsTop,
                    closeReports, evidence, notes, ack, rulesLink, discord, playerView, chatSuggestions, Approval.Settings.DEFAULT,
                    WatchSettings.DEFAULT, Probation.Settings.DEFAULT, MuteEnded.DEFAULT);
        }

        /** The P4 set of keys; the P5 ones get their defaults. */
        Extras(Duration undoWindow, Duration duplicateWindow, Duration duplicateConfirm, Duration offenceExpire,
               java.time.ZoneId statsZone, Duration statsCache, PunishStats.Period statsPeriod, int statsTop,
               boolean closeReports, Evidence evidence, Notes notes, Ack ack, String rulesLink, Discord discord,
               PlayerView playerView, Map<String, ChatSuggestion> chatSuggestions, Approval.Settings approval,
               WatchSettings watchlist, Probation.Settings probation, MuteEnded muteEnded) {
            this(undoWindow, duplicateWindow, duplicateConfirm, offenceExpire, statsZone, statsCache, statsPeriod, statsTop,
                    closeReports, evidence, notes, ack, rulesLink, discord, playerView, chatSuggestions, approval, watchlist,
                    probation, muteEnded, Bulk.DEFAULT, Appeals.DEFAULT);
        }

        /** The P5 set of keys; the P6 ones get their defaults. */
        Extras(Duration undoWindow, Duration duplicateWindow, Duration duplicateConfirm, Duration offenceExpire,
               java.time.ZoneId statsZone, Duration statsCache, PunishStats.Period statsPeriod, int statsTop,
               boolean closeReports, Evidence evidence, Notes notes, Ack ack, String rulesLink, Discord discord,
               PlayerView playerView, Map<String, ChatSuggestion> chatSuggestions, Approval.Settings approval,
               WatchSettings watchlist, Probation.Settings probation, MuteEnded muteEnded, Bulk bulk, Appeals appeal) {
            this(undoWindow, duplicateWindow, duplicateConfirm, offenceExpire, statsZone, statsCache, statsPeriod, statsTop,
                    closeReports, evidence, notes, ack, rulesLink, discord, playerView, chatSuggestions, approval, watchlist,
                    probation, muteEnded, bulk, appeal, NEVER_AUTO, Lockdown.Settings.DEFAULT, Lockdown.Raid.DEFAULT,
                    NetworkScope.Settings.DEFAULT);
        }
    }

    /**
     * Bulk punish (13.8): {@code /pbulk} and the Bulk button of {@code /p}.
     *
     * @param max most players in one bulk action
     */
    record Bulk(int max) {
        static final Bulk DEFAULT = new Bulk(20);
    }

    /**
     * Appeals (13.10): {@code /appeal} for players, {@code /appeals} for staff, and the appeals webhook (off by default).
     *
     * @param types          punishment types that can be appealed (a reason can still say {@code appealable: false})
     * @param cooldown       after a denied appeal, the same punishment can be appealed again after this
     * @param minLength      shortest appeal text
     * @param maxLength      longest appeal text
     * @param reminder       open appeals older than this are listed to staff when they join
     * @param reduceMaxDays  Reduce on a permanent punishment offers up to this many days
     * @param denyReasons    preset deny buttons (ids, texts {@code appeal.deny-reasons.<id>}), max 6
     * @param discordEnabled post new appeals and decisions to the {@code discordChannel} webhook (also needs the secret)
     */
    record Appeals(boolean enabled, java.util.Set<Punishment.Type> types, Duration cooldown, int minLength, int maxLength,
                   Duration reminder, int reduceMaxDays, List<String> denyReasons, boolean discordEnabled,
                   String discordChannel) {
        static final Appeals DEFAULT = new Appeals(true, java.util.Set.of(Punishment.Type.MUTE, Punishment.Type.BAN),
                Duration.ofDays(7), 20, 500, Duration.ofHours(48), 365, List.of("clear-evidence", "fair", "no-reason"),
                false, "appeals");
    }

    /**
     * Watchlist (13.5).
     *
     * @param expire          an entry is gone after this ({@code null} = until removed)
     * @param reasonMaxLength longest reason
     */
    record WatchSettings(Duration expire, int reasonMaxLength) {
        static final WatchSettings DEFAULT = new WatchSettings(Duration.ofDays(30), 100);
    }

    /**
     * "Mute ended" (13.13): the message when a mute ends naturally (online, or once on the next join) and "Welcome back"
     * after a ban ended.
     *
     * @param sound named sound (sounds.yml), empty = none
     */
    record MuteEnded(boolean enabled, String sound) {
        static final MuteEnded DEFAULT = new MuteEnded(true, "notify");
    }

    /**
     * Evidence snapshot (12.4).
     *
     * @param chatLines     the target's last chat lines saved
     * @param contextLines  other players' lines before them
     * @param retention     evidence is deleted after this
     * @param maxLineLength one line is cut to this many characters
     * @param maxChars      all lines together never exceed this (oldest lines are dropped first)
     */
    record Evidence(boolean enabled, int chatLines, int contextLines, Duration retention, int maxLineLength, int maxChars) {
        static final Evidence DEFAULT = new Evidence(true, 10, 5, Duration.ofDays(180), 200, 6000);
    }

    /**
     * Staff notes (12.5).
     *
     * @param maxLength longest note
     * @param shown     notes shown on the head of /p
     * @param cut       a note on the head is cut to this many characters
     */
    record Notes(int maxLength, int shown, int cut) {
        static final Notes DEFAULT = new Notes(200, 3, 40);
    }

    /** Warning acknowledgement (12.6): repeat interval and count, block until acknowledged, Bedrock menu. */
    record Ack(Duration repeat, int maxRepeats, boolean blockUntilAck, boolean bedrockMenu) {
        static final Ack DEFAULT = new Ack(Duration.ofSeconds(30), 3, false, true);
    }

    /**
     * Discord log (12.11). Posts only when {@code enabled} and a webhook URL for {@code channel} is set on the server.
     *
     * @param colors embed colour per kind (ban, mute, warn, kick, pardon, undo), 0xRRGGBB
     */
    record Discord(boolean enabled, String channel, int queueSize, int maxAttempts, Map<String, Integer> colors) {
        static final List<String> KINDS = List.of("ban", "mute", "warn", "kick", "pardon", "undo", "bulk", "appeal", "lockdown");
        static final Discord DEFAULT = new Discord(false, "punishments", 100, 3, Map.of("ban", 0xE74C3C, "mute", 0xE67E22,
                "warn", 0xF1C40F, "kick", 0x95A5A6, "pardon", 0x2ECC71, "undo", 0x3498DB, "bulk", 0x9B59B6,
                "appeal", 0x1ABC9C, "lockdown", 0xE91E63));

        int color(String kind) {
            return colors.getOrDefault(kind, 0x95A5A6);
        }
    }

    /** /mypunishments (12.12): show staff names, how many recent entries. */
    record PlayerView(boolean showStaff, int lastEntries) {
        static final PlayerView DEFAULT = new PlayerView(false, 5);
    }

    /** One detector of the suggestion API (12.8): {@code hits} within {@code within} suggest {@code reason}. */
    record ChatSuggestion(int hits, Duration within, String reason) {
    }

    /** A fixed bottom-row button ({@code kick}, {@code history}, ...). */
    record Button(String id, int slot, Material icon) {
    }

    /**
     * A plain warn button (bottom-left quarter): one warn with a preset reason text ({@code warn-buttons.<id>.reason}).
     * {@code weight} in tenths (spec 12.7: {@code weight: 0.5} = 5).
     */
    record WarnButton(String id, int slot, Material icon, int weight) {

        WarnButton(String id, int slot, Material icon) {
            this(id, slot, icon, Weights.ONE);
        }
    }

    /** Fixed button ids, in config order. Dry run (P8, spec 14.6) toggles the whole menu into dry-run mode. */
    static final List<String> BUTTONS = List.of("sanctions", "kick", "bulk", "history", "close", "more-reasons", "dry-run");

    /** Quarters of the menu, in config order. */
    static final List<String> QUARTERS = List.of("mutes", "bans", "small");

    /**
     * The single-screen {@code /p} layout.
     *
     * @param reasonSlots reason id → slot for every placed reason (all quarters)
     * @param quarterOf   reason id → quarter name ({@code mutes}, {@code bans}, {@code small})
     */
    record MenuLayout(boolean confirm, boolean keepOpen, Material filler, Material divider, List<Integer> dividerSlots,
                      int summarySlot, int warnCounterSlot, Material warnCounterIcon, Material unavailableIcon,
                      Map<String, Button> buttons, Map<String, Integer> reasonSlots, Map<String, String> quarterOf,
                      List<WarnButton> warns) {

        static final MenuLayout EMPTY = new MenuLayout(false, true, Material.GRAY_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE, List.of(), 4, 31, Material.YELLOW_CONCRETE, Material.GRAY_DYE, Map.of(),
                Map.of(), Map.of(), List.of());
    }

    /** Durations kept as text for display. */
    static String text(Duration d) {
        return d == null ? "perm" : Templates.Step.compact(d);
    }
}
