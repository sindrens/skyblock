package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.logging.Level;

/**
 * M2.3 members and roles (main thread): invites (solo → team warning, clickable, 5 min expiry), accept/deny, kick,
 * leave, promote/demote, transfer, coop/uncoop, join limits (REVIEW A5), notifications and audit (category island).
 * Every change is saved first ({@link MemberStore}, one transaction that re-checks the rules) and then applied to the
 * {@link IslandManager} cache, logged and announced to the island's online members.
 */
final class MemberManager {

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final ModuleMessages msg;
    private final MemberStore store;
    private final Invites invites = new Invites();
    /** Players who chose "join" in the portal choice menu (TQ19): they are brought to the island after the join. */
    private final java.util.Set<UUID> portalJoins = new java.util.HashSet<>();
    private final Executor main;
    /** Players with a member action waiting for the database (one at a time, against double clicks). */
    private final Set<UUID> busy = new HashSet<>();
    private Scheduler.Task sweepTask;

    MemberManager(IslandsModule module, IslandManager islands, MemberStore store) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.msg = module.msg();
        this.store = store;
        this.main = new MainThread(core.plugin());
    }

    private IslandsConfig config() {
        return module.config();
    }

    /** The /settings toggle (F1): players can refuse island invites. */
    static final String SETTING_INVITES = "island-invites";

    /** Registers the island-invites toggle (one tick after enable: the menus module enables later). */
    void hookSettings() {
        core.services().optional(PlayerSettings.class).ifPresent(service -> {
            try {
                service.register(new SettingToggle(IslandsModule.ID, SETTING_INVITES, SettingCategory.SOCIAL, Material.GRASS_BLOCK, true,
                        null, "navigation", "settings.toggles." + SETTING_INVITES, false));
            } catch (RuntimeException e) {
                core.plugin().getLogger().warning("Islands: could not register the island-invites setting: " + e.getMessage());
            }
        });
    }

    /** Whether {@code target} accepts island invites (on while the settings service is not there). Read live: instant. */
    private boolean acceptsInvites(Player target) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(target, SETTING_INVITES)).orElse(true);
    }

    /** Loads pending invites and drops coop trust on islands without an online leader/mod (blocking, enable only). */
    void load() {
        invites.clear();
        store.loadInvitesBlocking(System.currentTimeMillis()).forEach(invites::put);
        for (Island island : List.copyOf(islands.islands())) {
            if (!island.coops().isEmpty() && !managerOnline(island, null)) {
                clearCoops(island, "no-manager-online", Actor.system("islands"));
            }
        }
    }

    /** (Re)starts the invite expiry sweep. */
    void startSweep() {
        stopSweep();
        long period = config().inviteSweepSeconds * 20L;
        sweepTask = core.scheduler().repeat("island-invite-sweep", period, period, this::sweep);
    }

    void stopSweep() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    Invites invites() {
        return invites;
    }

    // ------------------------------------------------------------------ menus

    /** {@code /is members}: the member list (stored members only; coop players have no island to manage). */
    void openMembers(Player player) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        if (!MemberRules.canView(island.roleOrNull(islands.key(player)))) {
            islands.fail(player, "no-island");
            return;
        }
        core.menus().open(player, new MembersMenu(module, islands, island));
    }

    // ------------------------------------------------------------------ invite

    /** {@code /is invite <player>}: checks, then (solo island) the Team Island warning, then the invite. */
    void invite(Player player, Player target, Menu parent) {
        if (net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), player, net.skybornmc.skyblock.common.tutorial.Gates.ISLAND_TEAM)) {
            return; // TUTORIAL §4: sending invites opens after the first-crop step (accepting is always open)
        }
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        Role role = island.roleOrNull(islands.key(player));
        if (!MemberRules.canInvite(role)) {
            islands.fail(player, "invite.not-allowed");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            islands.fail(player, "invite.self");
            return;
        }
        if (!acceptsInvites(target)) {
            islands.fail(player, "invite.disabled", Msg.p("player", target.getName()));
            return;
        }
        // P3: a player's other profile is (or was) on this island: their profiles never share one.
        UUID other = islands.ownOtherProfileOn(island, islands.key(target), null);
        if (other != null) {
            islands.profiles().blockOwnTransfer(AuditCategory.ISLAND, "island_invite", other, islands.key(target), Actor.player(player));
            islands.fail(player, "invite.own-profile", Msg.p("player", target.getName()));
            return;
        }
        Optional<Island> theirs = islands.islandOfPlayer(islands.key(target));
        if (theirs.isPresent()) {
            islands.fail(player, theirs.get() == island ? "invite.already-member" : "invite.has-island", Msg.p("player", target.getName()));
            return;
        }
        int slots = islands.slots(island);
        if (island.memberCount() >= slots) {
            islands.fail(player, "invite.full", Msg.p("slots", slots));
            return;
        }
        long now = System.currentTimeMillis();
        if (invites.get(island.id(), islands.key(target), now).isEmpty()
                && invites.pendingFrom(island.id(), now) >= config().maxPendingInvites) {
            islands.fail(player, "invite.too-many", Msg.p("max", config().maxPendingInvites));
            return;
        }
        if (island.team()) {
            sendInvite(player, island, islands.key(target), target.getName());
            return;
        }
        UUID targetId = islands.key(target);
        String targetName = target.getName();
        open(player, parent, new ConfirmMenu(
                viewer -> module.text(viewer, "invite.solo.crumb"),
                core.menus().item(Material.PLAYER_HEAD, player, IslandsModule.ID, "invite.solo.shown", Msg.p("player", targetName)),
                viewer -> module.lines(viewer, "invite.solo.details", Msg.p("player", targetName)),
                viewer -> sendInvite(viewer, island, targetId, targetName),
                viewer -> msg.send(viewer, "invite.solo.cancelled")));
    }

    private void sendInvite(Player player, Island island, UUID targetId, String targetName) {
        if (!begin(player)) {
            return;
        }
        long expiry = Duration.ofSeconds(config().inviteExpirySeconds).toMillis();
        store.invite(island.id(), islands.key(player), targetId, islands.person(targetId), expiry, config().memberLimits()).whenCompleteAsync((result, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, "invite", error);
                return;
            }
            switch (result) {
                case MemberStore.InviteResult.Created created -> invited(player, island, targetId, targetName, created);
                case MemberStore.InviteResult.IslandGone ignored -> islands.fail(player, "error.island-gone");
                case MemberStore.InviteResult.AlreadyMember ignored -> islands.fail(player, "invite.already-member", Msg.p("player", targetName));
                case MemberStore.InviteResult.HasIsland ignored -> islands.fail(player, "invite.has-island", Msg.p("player", targetName));
                case MemberStore.InviteResult.Full full -> islands.fail(player, "invite.full", Msg.p("slots", full.slots()));
                case MemberStore.InviteResult.Limited limited -> limitedForInviter(player, targetName, limited.check());
                case MemberStore.InviteResult.OwnProfile ignored -> islands.fail(player, "invite.own-profile", Msg.p("player", targetName));
            }
        }, main);
    }

    private void invited(Player player, Island island, UUID targetId, String targetName, MemberStore.InviteResult.Created created) {
        if (islands.byId(island.id()) != island) {
            return; // the island was removed meanwhile (M2.7)
        }
        invites.put(created.invite());
        if (created.converted()) {
            island.markTeam();
        }
        String name = islands.displayName(island);
        core.audit().log(AuditCategory.ISLAND, "island_invite", Actor.player(player), island.id().toString(), name, null, null,
                Map.of("target", targetId.toString(), "target_name", targetName, "result", "sent",
                        "expires_at", created.invite().expiresAt(), "became_team", created.converted()));
        String time = Format.duration(Duration.ofSeconds(config().inviteExpirySeconds));
        msg.send(player, "invite.sent", Msg.p("player", targetName), Msg.p("time", time));
        core.sounds().play(player, "success");
        if (created.converted()) {
            msg.send(player, "invite.now-team");
        }
        notifyMembers(island, islands.key(player), "notify.invited", Msg.p("player", player.getName()), Msg.p("target", targetName));
        Player target = islands.online(targetId);
        if (target != null) {
            msg.send(target, "invite.received", Msg.p("player", player.getName()), command("from", player.getName()),
                    Msg.p("island", name), Msg.p("time", time),
                    Msg.p("members", island.memberCount()), Msg.p("slots", islands.slots(island)));
            core.sounds().play(target, "notify");
        }
    }

    /** A player name for use inside a click command argument (pre-processed; quotes and tags stripped). */
    static TagResolver command(String name, String value) {
        String safe = value.replace("'", "").replace("<", "").replace(">", "").replace("\\", "");
        return TagResolver.resolver(name, net.kyori.adventure.text.minimessage.tag.Tag.preProcessParsed(safe));
    }

    private void limitedForInviter(Player player, String targetName, JoinRules.Check check) {
        switch (check) {
            case JoinRules.Check.Blocked blocked -> islands.fail(player, "invite.target-blocked", Msg.p("player", targetName));
            case JoinRules.Check.SeasonLimit limit -> islands.fail(player, "invite.target-season-limit", Msg.p("player", targetName),
                    Msg.p("max", limit.max()));
            case JoinRules.Check.Ok ok -> {
                // not a limit
            }
        }
    }

    // ------------------------------------------------------------------ accept / deny

    /** {@code /is accept [player]}: the newest invite, or the one from {@code from}'s island. */
    // Owner answers 131/203 (round 1): no new-account limit for joining a team or coop. Accepting stays open during the
    // join tutorial (TUTORIAL §7); only sending invites and coop wait for its first-crop step.
    void accept(Player player, String from) {
        findInvite(player, from).ifPresent(invite -> accept(player, invite));
    }

    /** TQ19: accept from the island portal choice; the player is teleported to the island once the join is saved. */
    void acceptFromPortal(Player player, Invites.Invite pending) {
        portalJoins.add(player.getUniqueId());
        accept(player, pending);
        core.scheduler().later("island-portal-join", 20L * 30, () -> portalJoins.remove(player.getUniqueId()));
    }

    /** Accepts one pending invite (also from {@code /is invites}); the database re-checks everything. */
    void accept(Player player, Invites.Invite pending) {
        UUID key = islands.key(player);
        Optional<Invites.Invite> invite = invites.get(pending.islandId(), key, System.currentTimeMillis());
        if (invite.isEmpty()) {
            islands.fail(player, "accept.expired");
            return;
        }
        if (islands.islandOfPlayer(islands.key(player)).isPresent()) {
            islands.fail(player, "accept.has-island");
            return;
        }
        if (!begin(player)) {
            return;
        }
        UUID islandId = invite.get().islandId();
        // P3: never two profiles of one player on the same island (also checked in the transaction with the history).
        Island target = islands.byId(islandId);
        if (target != null && islands.ownOtherProfileOn(target, key, null) != null) {
            end(player);
            islands.profiles().blockOwnTransfer(AuditCategory.ISLAND, "island_join", islands.ownOtherProfileOn(target, key, null), key,
                    Actor.player(player));
            islands.fail(player, "accept.own-profile");
            return;
        }
        store.accept(islandId, key, player.getUniqueId(), config().memberLimits()).whenCompleteAsync((result, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, "accept", error);
                return;
            }
            switch (result) {
                case MemberStore.AcceptResult.Joined joined -> joined(player, joined);
                case MemberStore.AcceptResult.NoInvite ignored -> {
                    invites.remove(islandId, key);
                    islands.fail(player, "accept.expired");
                }
                case MemberStore.AcceptResult.IslandGone ignored -> {
                    invites.removeIsland(islandId);
                    islands.fail(player, "error.island-gone");
                }
                case MemberStore.AcceptResult.HasIsland ignored -> islands.fail(player, "accept.has-island");
                case MemberStore.AcceptResult.Full full -> islands.fail(player, "accept.full", Msg.p("slots", full.slots()));
                case MemberStore.AcceptResult.Limited limited -> limitedForTarget(player, limited.check());
                case MemberStore.AcceptResult.OwnProfile ignored -> {
                    core.audit().logPlayer(AuditCategory.ISLAND, ProfileService.AUDIT_BLOCKED, Actor.player(player),
                            player.getUniqueId(), player.getName(), null, null,
                            Map.of("path", "island_join", "to_profile", key.toString(), "island", islandId.toString()));
                    islands.fail(player, "accept.own-profile");
                }
            }
        }, main);
    }

    private void joined(Player player, MemberStore.AcceptResult.Joined joined) {
        invites.removeTarget(islands.key(player));
        Island island = islands.byId(joined.islandId());
        if (island == null) {
            // Saved, but the island left the cache meanwhile (M2.7 disband): reload fixes it; tell the player.
            islands.fail(player, "error.island-gone");
            return;
        }
        if (joined.converted()) {
            island.markTeam();
        }
        islands.cacheJoin(island, islands.key(player), Role.MEMBER, joined.joinedAt(), joined.inviter());
        String name = islands.displayName(island);
        core.audit().log(AuditCategory.ISLAND, "island_join", Actor.player(player), island.id().toString(), name, null, null,
                Map.of("player", player.getUniqueId().toString(), "inviter", joined.inviter().toString(),
                        "season", config().seasonId, "members", island.memberCount(), "slots", islands.slots(island),
                        "became_team", joined.converted()));
        msg.send(player, "accept.joined", Msg.p("island", name));
        if (joined.converted()) {
            notifyMembers(island, islands.key(player), "invite.now-team");
        }
        msg.title(player, "accept.title", Msg.p("island", name));
        core.sounds().play(player, "unlock");
        if (portalJoins.remove(player.getUniqueId())) {
            islands.teleportHome(player); // TQ19: joined from the island portal choice
        }
        // TUTORIAL §6.1: a new player who joins a team gets the Starter pack instead of an island chest.
        org.bukkit.Bukkit.getPluginManager().callEvent(new net.skybornmc.skyblock.common.island.IslandJoinedEvent(player, island.id()));
        notifyMembers(island, islands.key(player), "notify.joined", Msg.p("player", player.getName()),
                Msg.p("members", island.memberCount()), Msg.p("slots", islands.slots(island)));
    }

    private void limitedForTarget(Player player, JoinRules.Check check) {
        switch (check) {
            case JoinRules.Check.Blocked blocked -> islands.fail(player, "accept.blocked");
            case JoinRules.Check.SeasonLimit limit -> islands.fail(player, "accept.season-limit", Msg.p("max", limit.max()));
            case JoinRules.Check.Ok ok -> {
                // not a limit
            }
        }
    }

    /** {@code /is deny [player]}. */
    void deny(Player player, String from) {
        findInvite(player, from).ifPresent(invite -> deny(player, invite));
    }

    /** Declines one pending invite (also from {@code /is invites}). */
    void deny(Player player, Invites.Invite invite) {
        if (!invites.remove(invite.islandId(), islands.key(player))) {
            islands.fail(player, "accept.expired");
            return;
        }
        Island island = islands.byId(invite.islandId());
        String islandName = island == null ? "?" : islands.displayName(island);
        store.deleteInvite(invite.islandId(), islands.key(player)).exceptionally(error -> {
            core.plugin().getLogger().log(Level.WARNING, "Islands: deleting an invite failed", Database.cause(error));
            return false;
        });
        core.audit().log(AuditCategory.ISLAND, "island_invite", Actor.player(player), invite.islandId().toString(), islandName, null, null,
                Map.of("target", player.getUniqueId().toString(), "target_name", player.getName(), "result", "denied"));
        msg.send(player, "deny.done", Msg.p("island", islandName));
        Player inviter = islands.online(invite.inviter());
        if (inviter != null) {
            msg.send(inviter, "deny.notify", Msg.p("player", player.getName()));
        }
    }

    // ------------------------------------------------------------------ invites menu (ISL2)

    /** {@code /is invites}: invites your island sent (cancel) and invites you received (accept, deny). */
    void openInvites(Player player) {
        core.menus().open(player, new InvitesMenu(module, islands, this));
    }

    /** Cancels an invite the player's island sent (leader, admin or mod). Saved, logged and both sides told. */
    void cancelInvite(Player player, Invites.Invite invite) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        if (!invite.islandId().equals(island.id()) || !MemberRules.canInvite(island.roleOrNull(islands.key(player)))) {
            islands.fail(player, "invites.cancel-not-allowed");
            return;
        }
        if (!invites.remove(island.id(), invite.target())) {
            islands.fail(player, "invites.gone");
            return;
        }
        String targetName = islands.name(invite.target());
        store.deleteInvite(island.id(), invite.target()).exceptionally(error -> {
            core.plugin().getLogger().log(Level.WARNING, "Islands: deleting an invite failed", Database.cause(error));
            return false;
        });
        String name = islands.displayName(island);
        core.audit().log(AuditCategory.ISLAND, "island_invite", Actor.player(player), island.id().toString(), name, null, null,
                Map.of("target", invite.target().toString(), "target_name", targetName, "result", "cancelled"));
        msg.send(player, "invites.cancelled", Msg.p("player", targetName));
        core.sounds().play(player, "success");
        Player target = islands.online(invite.target());
        if (target != null) {
            msg.send(target, "invites.cancelled-target", Msg.p("island", name));
        }
    }

    /** The invite {@code /is accept|deny [from]} means; tells the player when there is none. */
    private Optional<Invites.Invite> findInvite(Player player, String from) {
        List<Invites.Invite> mine = invites.of(islands.key(player), System.currentTimeMillis());
        if (mine.isEmpty()) {
            islands.fail(player, "accept.none");
            return Optional.empty();
        }
        if (from == null || from.isBlank()) {
            return Optional.of(mine.getFirst());
        }
        for (Invites.Invite invite : mine) {
            if (matches(invite, from)) {
                return Optional.of(invite);
            }
        }
        islands.fail(player, "accept.none-from", Msg.p("player", from));
        return Optional.empty();
    }

    /** {@code name} is the inviter or any member of the inviting island. */
    private boolean matches(Invites.Invite invite, String name) {
        if (islands.name(invite.inviter()).equalsIgnoreCase(name)) {
            return true;
        }
        Island island = islands.byId(invite.islandId());
        if (island == null) {
            return false;
        }
        for (UUID member : island.members().keySet()) {
            if (islands.name(member).equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Tab completion of accept/deny: the inviters of the sender's pending invites. */
    List<String> inviterNames(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return safely(() -> {
            List<String> names = new ArrayList<>();
            for (Invites.Invite invite : invites.of(islands.key(player), System.currentTimeMillis())) {
                names.add(islands.name(invite.inviter()));
            }
            return names;
        });
    }

    // ------------------------------------------------------------------ kick / leave

    /** {@code /is kick <player>} (confirm). */
    void kick(Player player, String targetName, Menu parent) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        UUID target = memberByName(island, targetName);
        if (target == null) {
            islands.fail(player, "member.not-found", Msg.p("player", targetName));
            return;
        }
        Role actor = island.roleOrNull(islands.key(player));
        Role role = island.roleOrNull(target);
        if (target.equals(islands.key(player))) {
            islands.fail(player, "kick.self");
            return;
        }
        if (!MemberRules.canKick(actor, role)) {
            islands.fail(player, "kick.not-allowed", Msg.p("player", islands.name(target)));
            return;
        }
        String name = islands.name(target);
        open(player, parent, new ConfirmMenu(
                viewer -> module.text(viewer, "kick.crumb"),
                head(player, target, "kick.shown", Msg.p("player", name)),
                viewer -> module.lines(viewer, "kick.details", Msg.p("player", name)),
                viewer -> remove(viewer, island, target, MemberStore.LEFT_KICK, "", Actor.player(viewer)),
                null));
    }

    /** {@code /is leave} (confirm). The leader must transfer first. */
    void leave(Player player, Menu parent) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        Role role = island.roleOrNull(islands.key(player));
        if (role == Role.LEADER) {
            islands.fail(player, "leave.leader");
            return;
        }
        if (!MemberRules.canLeave(role)) {
            islands.fail(player, "no-island");
            return;
        }
        String name = islands.displayName(island);
        open(player, parent, new ConfirmMenu(
                viewer -> module.text(viewer, "leave.crumb"),
                core.menus().item(Material.OAK_DOOR, player, IslandsModule.ID, "leave.shown", Msg.p("island", name)),
                viewer -> module.lines(viewer, "leave.details", Msg.p("island", name)),
                viewer -> remove(viewer, island, islands.key(viewer), MemberStore.LEFT_LEAVE, "", Actor.player(viewer)),
                null));
    }

    /**
     * Removes a stored member: {@code reason} = leave | kick | removed. Saves, updates the cache, logs, tells the player
     * (and sends them off the island) and notifies the members. {@code note} = moderation reason (may be blank).
     */
    private CompletableFuture<Boolean> remove(Player initiator, Island island, UUID target, String reason, String note, Actor actor) {
        if (initiator != null && !begin(initiator)) {
            return CompletableFuture.completedFuture(false);
        }
        return store.removeMember(island.id(), target, islands.person(target), reason, config().seasonId).handleAsync((role, error) -> {
            if (initiator != null) {
                end(initiator);
            }
            if (error != null) {
                if (initiator != null) {
                    islands.databaseError(initiator, "remove member", error);
                } else {
                    core.plugin().getLogger().log(Level.SEVERE, "Islands: removing a member failed", Database.cause(error));
                }
                return false;
            }
            if (role.isEmpty()) {
                if (initiator != null) {
                    islands.fail(initiator, "member.not-found", Msg.p("player", islands.name(target)));
                }
                return false;
            }
            removed(island, target, role.get(), reason, note, actor, initiator);
            return true;
        }, main);
    }

    private void removed(Island island, UUID target, Role oldRole, String reason, String note, Actor actor, Player initiator) {
        String targetName = islands.name(target);
        if (islands.byId(island.id()) == island) {
            islands.cacheLeave(island, target);
        }
        String name = islands.displayName(island);
        boolean leave = MemberStore.LEFT_LEAVE.equals(reason);
        Map<String, Object> details = new HashMap<>();
        details.put("player", target.toString());
        details.put("player_name", targetName);
        details.put("role", MemberStore.key(oldRole));
        details.put("reason", reason);
        if (note != null && !note.isBlank()) {
            details.put("note", note);
        }
        core.audit().log(AuditCategory.ISLAND, leave ? "island_leave" : "island_kick", actor, island.id().toString(), name, null, null, details);
        KickBackups backups = module.kickBackups();
        if (backups != null && KickBackups.wanted(reason, oldRole, note)) {
            backups.capture(island, target, oldRole, reason, actor); // owner answer 251: staff can restore a kicked member
        }
        Player player = islands.online(target);
        if (leave) {
            if (player != null) {
                core.sounds().play(player, "success");
                msg.send(player, "leave.done-free", Msg.p("island", name)); // no join cooldown (owner answer 161)
            }
            notifyMembers(island, target, "notify.left", Msg.p("player", targetName));
        } else {
            if (player != null) {
                if (note != null && !note.isBlank()) {
                    msg.send(player, "kick.you-reason", Msg.p("island", name), Msg.p("reason", note));
                } else {
                    msg.send(player, "kick.you", Msg.p("island", name), Msg.p("player", actor.name()));
                }
                core.sounds().play(player, "error");
            }
            if (initiator != null) {
                msg.send(initiator, "kick.done", Msg.p("player", targetName));
                core.sounds().play(initiator, "success");
            }
            notifyMembers(island, initiator == null ? null : islands.key(initiator), "notify.kicked", Msg.p("player", targetName),
                    Msg.p("by", actor.name()));
        }
        if (player != null) {
            islands.sendOff(player, island);
        }
        checkCoops(island);
        if (island.memberCount() == 1) {
            // DESIGN §3.2: emptying the island never turns it back into a solo island; say so kindly.
            Player leader = islands.online(island.owner());
            if (leader != null) {
                msg.send(leader, "notify.still-team");
            }
        }
    }

    // ------------------------------------------------------------------ promote / demote / transfer

    void promote(Player player, String targetName) {
        changeRole(player, targetName, true);
    }

    void demote(Player player, String targetName) {
        changeRole(player, targetName, false);
    }

    private void changeRole(Player player, String targetName, boolean up) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        UUID target = memberByName(island, targetName);
        if (target == null) {
            islands.fail(player, "member.not-found", Msg.p("player", targetName));
            return;
        }
        Role actor = island.roleOrNull(islands.key(player));
        Role role = island.roleOrNull(target);
        String name = islands.name(target);
        boolean allowed = up ? MemberRules.canPromote(actor, role) : MemberRules.canDemote(actor, role);
        if (!allowed) {
            String key = actor != Role.LEADER && actor != Role.ADMIN ? "role.admin-only"
                    : up ? "promote.not-possible" : "demote.not-possible";
            islands.fail(player, key, Msg.p("player", name));
            return;
        }
        Role next = up ? MemberRules.promoted(role) : MemberRules.demoted(role);
        if (!begin(player)) {
            return;
        }
        store.setRole(island.id(), target, role, next).whenCompleteAsync((saved, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, up ? "promote" : "demote", error);
                return;
            }
            if (!saved || islands.byId(island.id()) != island) {
                islands.fail(player, "member.changed");
                return;
            }
            islands.cacheRole(island, target, next);
            core.audit().log(AuditCategory.ISLAND, up ? "island_promote" : "island_demote", Actor.player(player), island.id().toString(),
                    islands.displayName(island), null, null, Map.of("player", target.toString(), "player_name", name,
                            "old", MemberStore.key(role), "new", MemberStore.key(next)));
            String roleName = module.raw("role." + MemberStore.key(next));
            msg.send(player, up ? "promote.done" : "demote.done", Msg.p("player", name), Msg.p("role", roleName));
            core.sounds().play(player, "success");
            Player targetPlayer = islands.online(target);
            if (targetPlayer != null) {
                msg.send(targetPlayer, up ? "promote.you" : "demote.you", Msg.p("role", roleName), Msg.p("player", player.getName()));
                core.sounds().play(targetPlayer, up ? "level-up" : "warning");
            }
            notifyMembers(island, Set.of(islands.key(player), target), up ? "notify.promoted" : "notify.demoted",
                    Msg.p("player", name), Msg.p("role", roleName), Msg.p("by", player.getName()));
            checkCoops(island);
        }, main);
    }

    /** {@code /is transfer <player>}: two confirm screens, then the new leader takes over and the old one becomes an admin. */
    void transfer(Player player, String targetName, Menu parent) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        UUID target = memberByName(island, targetName);
        if (target == null) {
            islands.fail(player, "member.not-found", Msg.p("player", targetName));
            return;
        }
        Role actor = island.roleOrNull(islands.key(player));
        if (!MemberRules.canTransfer(actor, island.roleOrNull(target))) {
            islands.fail(player, actor != Role.LEADER ? "role.leader-only" : "transfer.not-possible", Msg.p("player", islands.name(target)));
            return;
        }
        String name = islands.name(target);
        ConfirmMenu second = new ConfirmMenu(
                viewer -> module.text(viewer, "transfer.final.crumb"),
                head(player, target, "transfer.final.shown", Msg.p("player", name)),
                viewer -> module.lines(viewer, "transfer.final.details", Msg.p("player", name)),
                viewer -> doTransfer(viewer, island, target, name),
                null);
        open(player, parent, new ConfirmMenu(
                viewer -> module.text(viewer, "transfer.crumb"),
                head(player, target, "transfer.shown", Msg.p("player", name)),
                viewer -> module.lines(viewer, "transfer.details", Msg.p("player", name)),
                viewer -> core.menus().open(viewer, second),
                null));
    }

    private void doTransfer(Player player, Island island, UUID target, String name) {
        UUID old = islands.key(player);
        if (island.roleOrNull(old) != Role.LEADER || !begin(player)) {
            return;
        }
        store.transfer(island.id(), old, target).whenCompleteAsync((saved, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, "transfer", error);
                return;
            }
            if (!saved || islands.byId(island.id()) != island) {
                islands.fail(player, "member.changed");
                return;
            }
            islands.cacheTransfer(island, old, target);
            String islandName = islands.displayName(island);
            core.audit().log(AuditCategory.ISLAND, "island_transfer", Actor.player(player), island.id().toString(), islandName, null, null,
                    Map.of("old_leader", old.toString(), "new_leader", target.toString(), "new_leader_name", name));
            msg.send(player, "transfer.done", Msg.p("player", name));
            core.sounds().play(player, "success");
            Player newLeader = islands.online(target);
            if (newLeader != null) {
                msg.send(newLeader, "transfer.you", Msg.p("island", islandName), Msg.p("player", player.getName()));
                msg.title(newLeader, "transfer.title");
                core.sounds().play(newLeader, "unlock");
            }
            notifyMembers(island, Set.of(old, target), "notify.transferred", Msg.p("player", name), Msg.p("by", player.getName()));
        }, main);
    }

    // ------------------------------------------------------------------ coop

    /** {@code /is coop <player>}: temporary trust while a leader or mod is online (REVIEW A4). */
    void coop(Player player, Player target) {
        if (net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), player, net.skybornmc.skyblock.common.tutorial.Gates.ISLAND_TEAM)) {
            return; // TUTORIAL §4: coop opens after the first-crop step
        }
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        if (!MemberRules.canCoop(island.roleOrNull(islands.key(player)))) {
            islands.fail(player, "coop.not-allowed");
            return;
        }
        UUID targetId = islands.key(target);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            islands.fail(player, "coop.self");
            return;
        }
        // P3/P4: a player's other profile can never be trusted on this island.
        UUID other = islands.ownOtherProfileOn(island, targetId, null);
        if (other != null) {
            islands.profiles().blockOwnTransfer(AuditCategory.ISLAND, "island_coop", other, targetId, Actor.player(player));
            islands.fail(player, "coop.own-profile", Msg.p("player", target.getName()));
            return;
        }
        if (island.role(targetId).isPresent()) {
            islands.fail(player, "coop.member", Msg.p("player", target.getName()));
            return;
        }
        if (island.coops().contains(targetId)) {
            islands.fail(player, "coop.already", Msg.p("player", target.getName()));
            return;
        }
        if (island.coops().size() >= config().maxCoops) {
            islands.fail(player, "coop.full", Msg.p("max", config().maxCoops));
            return;
        }
        if (!begin(player)) {
            return;
        }
        UUID grantedBy = islands.key(player);
        store.blockedUntil(target.getUniqueId()).thenCompose(blocked -> {
            if (blocked.isPresent()) {
                return CompletableFuture.completedFuture(false);
            }
            return store.addCoop(island.id(), targetId, grantedBy).thenApply(ignored -> true);
        }).whenCompleteAsync((added, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, "coop", error);
                return;
            }
            if (!added) {
                islands.fail(player, "coop.blocked", Msg.p("player", target.getName()));
                return;
            }
            if (islands.byId(island.id()) != island || island.role(targetId).isPresent()) {
                store.removeCoop(island.id(), targetId);
                return;
            }
            island.addCoop(targetId);
            String name = islands.displayName(island);
            core.audit().log(AuditCategory.ISLAND, "island_coop", Actor.player(player), island.id().toString(), name, null, null,
                    Map.of("player", targetId.toString(), "player_name", target.getName(), "action", "add"));
            msg.send(player, "coop.done", Msg.p("player", target.getName()));
            core.sounds().play(player, "success");
            if (target.isOnline() && islands.key(target).equals(targetId)) {
                msg.send(target, "coop.you", Msg.p("island", name), Msg.p("player", player.getName()));
                core.sounds().play(target, "notify");
            }
            notifyMembers(island, islands.key(player), "notify.cooped", Msg.p("player", target.getName()), Msg.p("by", player.getName()));
        }, main);
    }

    /** {@code /is uncoop <player>}. */
    void uncoop(Player player, String targetName) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        if (!MemberRules.canCoop(island.roleOrNull(islands.key(player)))) {
            islands.fail(player, "coop.not-allowed");
            return;
        }
        UUID target = null;
        for (UUID coop : island.coops()) {
            if (islands.name(coop).equalsIgnoreCase(targetName)) {
                target = coop;
            }
        }
        if (target == null) {
            islands.fail(player, "coop.not-coop", Msg.p("player", targetName));
            return;
        }
        removeCoop(island, target, "uncoop", "", Actor.player(player), player);
    }

    private CompletableFuture<Boolean> removeCoop(Island island, UUID target, String reason, String note, Actor actor, Player initiator) {
        return store.removeCoop(island.id(), target).handleAsync((deleted, error) -> {
            if (error != null) {
                if (initiator != null) {
                    islands.databaseError(initiator, "uncoop", error);
                } else {
                    core.plugin().getLogger().log(Level.SEVERE, "Islands: removing a coop failed", Database.cause(error));
                }
                return false;
            }
            if (!island.removeCoop(target) && !deleted) {
                return false;
            }
            String targetName = islands.name(target);
            String name = islands.displayName(island);
            Map<String, Object> details = new HashMap<>();
            details.put("player", target.toString());
            details.put("player_name", targetName);
            details.put("action", "remove");
            details.put("reason", reason);
            if (note != null && !note.isBlank()) {
                details.put("note", note);
            }
            core.audit().log(AuditCategory.ISLAND, "island_coop", actor, island.id().toString(), name, null, null, details);
            if (initiator != null) {
                msg.send(initiator, "coop.removed", Msg.p("player", targetName));
                core.sounds().play(initiator, "success");
            }
            Player player = islands.online(target);
            if (player != null) {
                msg.send(player, note != null && !note.isBlank() ? "coop.you-removed-reason" : "coop.you-removed",
                        Msg.p("island", name), Msg.p("reason", note == null ? "" : note));
            }
            notifyMembers(island, initiator == null ? null : islands.key(initiator), "notify.uncooped", Msg.p("player", targetName));
            return true;
        }, main);
    }

    /** Ends every coop of {@code island} (no leader/mod online, or moderation). */
    private void clearCoops(Island island, String reason, Actor actor) {
        if (island.coops().isEmpty()) {
            return;
        }
        List<UUID> coops = List.copyOf(island.coops());
        island.clearCoops();
        String name = islands.displayName(island);
        store.clearCoops(island.id()).exceptionally(error -> {
            core.plugin().getLogger().log(Level.SEVERE, "Islands: clearing coops of " + island.id() + " failed", Database.cause(error));
            return 0;
        });
        for (UUID coop : coops) {
            core.audit().log(AuditCategory.ISLAND, "island_coop", actor, island.id().toString(), name, null, null,
                    Map.of("player", coop.toString(), "player_name", islands.name(coop), "action", "remove", "reason", reason));
            Player player = islands.online(coop);
            if (player != null) {
                msg.send(player, "coop.ended", Msg.p("island", name));
            }
        }
    }

    /** Ends the island's coop trust when no leader or mod is online anymore (after a kick, leave or demotion). */
    void checkCoops(Island island) {
        if (!island.coops().isEmpty() && islands.byId(island.id()) == island && !managerOnline(island, null)) {
            clearCoops(island, "no-manager-online", Actor.system("islands"));
        }
    }

    /** True if a leader, admin or mod of {@code island} (other than {@code except}) is online. */
    boolean managerOnline(Island island, UUID except) {
        for (Map.Entry<UUID, Role> entry : island.members().entrySet()) {
            if (MemberRules.isManager(entry.getValue()) && !entry.getKey().equals(except) && islands.online(entry.getKey()) != null) {
                return true;
            }
        }
        return false;
    }

    /** A leader/mod quit: coop trust ends when nobody who can manage the island is left online. */
    void onQuit(Player player) {
        busy.remove(player.getUniqueId());
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty() || own.get().coops().isEmpty() || !MemberRules.isManager(own.get().roleOrNull(islands.key(player)))) {
            return;
        }
        Island island = own.get();
        UUID quitter = islands.key(player);
        core.scheduler().later("island-coop-check", 1, () -> {
            if (islands.byId(island.id()) == island && !managerOnline(island, quitter)) {
                clearCoops(island, "no-manager-online", Actor.system("islands"));
            }
        });
    }

    // ------------------------------------------------------------------ service hooks (moderation P7, upgrades)

    CompletableFuture<Boolean> removeByService(UUID islandId, UUID player, String reason, Actor actor) {
        return core.scheduler().supplyMain("island-remove-member", () -> islands.byId(islandId)).thenCompose(island -> {
            if (island == null) {
                return CompletableFuture.completedFuture(false);
            }
            String note = reason == null ? "" : reason;
            if (island.coops().contains(player)) {
                return removeCoop(island, player, "removed", note, actor, null);
            }
            if (island.role(player).isEmpty() || island.owner().equals(player)) {
                return CompletableFuture.completedFuture(false);
            }
            return remove(null, island, player, MemberStore.LEFT_REMOVED, note, actor);
        });
    }

    CompletableFuture<Boolean> blockJoining(UUID player, long until, String reason, Actor actor) {
        return store.blockJoining(player, until, reason, actor.name()).handleAsync((ignored, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Islands: saving a join block failed", Database.cause(error));
                return false;
            }
            core.audit().logPlayer(AuditCategory.ISLAND, "island_join_block", actor, player, islands.name(player), null, null,
                    Map.of("until", until, "reason", reason == null ? "" : reason, "lifted", until <= System.currentTimeMillis()));
            if (until > System.currentTimeMillis()) {
                // Join blocks are per player (account): every coop trust of any of their profiles ends.
                for (Island island : List.copyOf(islands.islands())) {
                    for (UUID coop : List.copyOf(island.coops())) {
                        if (islands.person(coop).equals(player)) {
                            removeCoop(island, coop, "blocked", reason, actor, null);
                        }
                    }
                }
            }
            return true;
        }, main);
    }

    // ------------------------------------------------------------------ staff hooks (M2.7 /isadmin)

    /**
     * Staff adds {@code target} as a member (no invite; season join limit and join cooldown skipped, slots and one island
     * per player kept). Cached, logged ({@code island_join} with {@code staff}), the player and the members are told.
     */
    CompletableFuture<MemberStore.AddResult> addByStaff(Island island, UUID target, Actor actor) {
        return store.addMember(island.id(), target, islands.person(target), actor.uuid(), config().memberLimits()).thenApplyAsync(result -> {
            if (!(result instanceof MemberStore.AddResult.Added added) || islands.byId(island.id()) != island) {
                return result;
            }
            invites.removeTarget(target);
            islands.cacheJoin(island, target, Role.MEMBER, added.joinedAt(), actor.uuid());
            if (added.converted()) {
                island.markTeam();
            }
            String name = islands.displayName(island);
            String targetName = islands.name(target);
            core.audit().log(AuditCategory.ISLAND, "island_join", actor, island.id().toString(), name, null, null,
                    Map.of("player", target.toString(), "player_name", targetName, "staff", true, "season", config().seasonId,
                            "members", island.memberCount(), "slots", islands.slots(island), "became_team", added.converted()));
            Player player = islands.online(target);
            if (player != null) {
                msg.send(player, "admin.addmember.you", Msg.p("island", name));
                core.sounds().play(player, "unlock");
            }
            notifyMembers(island, target, "notify.joined", Msg.p("player", targetName), Msg.p("members", island.memberCount()),
                    Msg.p("slots", islands.slots(island)));
            return result;
        }, main);
    }

    /**
     * Staff makes the member {@code newLeader} the leader (the old leader becomes an admin, like {@code /is transfer}).
     * Cached, logged ({@code island_transfer} with {@code staff}), both players and the members are told.
     */
    CompletableFuture<Boolean> setOwnerByStaff(Island island, UUID newLeader, Actor actor) {
        UUID old = island.owner();
        return store.transfer(island.id(), old, newLeader).thenApplyAsync(saved -> {
            if (!saved || islands.byId(island.id()) != island) {
                return false;
            }
            islands.cacheTransfer(island, old, newLeader);
            String name = islands.displayName(island);
            String newName = islands.name(newLeader);
            core.audit().log(AuditCategory.ISLAND, "island_transfer", actor, island.id().toString(), name, null, null,
                    Map.of("old_leader", old.toString(), "new_leader", newLeader.toString(), "new_leader_name", newName, "staff", true));
            Player leader = islands.online(newLeader);
            if (leader != null) {
                msg.send(leader, "admin.setowner.you", Msg.p("island", name));
                msg.title(leader, "transfer.title");
                core.sounds().play(leader, "unlock");
            }
            Player before = islands.online(old);
            if (before != null) {
                msg.send(before, "admin.setowner.old", Msg.p("player", newName));
                core.sounds().play(before, "warning");
            }
            notifyMembers(island, Set.of(old, newLeader), "notify.transferred", Msg.p("player", newName), Msg.p("by", actor.name()));
            return true;
        }, main);
    }

    CompletableFuture<Optional<Long>> joinBlockedUntil(UUID player) {
        return store.blockedUntil(player);
    }

    CompletableFuture<Boolean> setMemberSlots(UUID islandId, int slots, Actor actor) {
        if (slots < 1 || slots > config().maxSlots) {
            return CompletableFuture.completedFuture(false);
        }
        return store.setMemberSlots(islandId, slots).handleAsync((saved, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Islands: saving member slots failed", Database.cause(error));
                return false;
            }
            Island island = islands.byId(islandId);
            if (!saved || island == null) {
                return false;
            }
            int before = islands.slots(island);
            island.memberSlots(slots);
            core.audit().log(AuditCategory.ISLAND, "island_member_slots", actor, island.id().toString(), islands.displayName(island),
                    null, null, Map.of("old", before, "new", slots));
            return true;
        }, main);
    }

    // ------------------------------------------------------------------ sweep, notifications, helpers

    private void sweep() {
        long now = System.currentTimeMillis();
        List<Invites.Invite> expired = invites.expire(now);
        if (expired.isEmpty()) {
            return;
        }
        for (Invites.Invite invite : expired) {
            Player inviter = islands.online(invite.inviter());
            if (inviter != null) {
                msg.send(inviter, "invite.expired-inviter", Msg.p("player", islands.name(invite.target())));
            }
            Player target = islands.online(invite.target());
            if (target != null) {
                Island island = islands.byId(invite.islandId());
                msg.send(target, "invite.expired-target", Msg.p("island", island == null ? "?" : islands.displayName(island)));
            }
        }
        store.deleteExpiredInvites(now).exceptionally(error -> {
            core.plugin().getLogger().log(Level.WARNING, "Islands: deleting expired invites failed", Database.cause(error));
            return 0;
        });
    }

    /** Tells every online stored member of {@code island} except {@code except} (nullable). */
    void notifyMembers(Island island, UUID except, String key, TagResolver... placeholders) {
        notifyMembers(island, except == null ? Set.of() : Set.of(except), key, placeholders);
    }

    void notifyMembers(Island island, Set<UUID> except, String key, TagResolver... placeholders) {
        for (UUID member : List.copyOf(island.members().keySet())) {
            if (except.contains(member)) {
                continue;
            }
            Player player = islands.online(member);
            if (player != null) {
                msg.send(player, key, placeholders);
                core.sounds().play(player, "notify");
            }
        }
    }

    /** The sender's island, or tells them they have none. */
    private Island ownIsland(Player player) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return null;
        }
        return own.get();
    }

    /** The stored member of {@code island} named {@code name} (case-insensitive), or null. */
    UUID memberByName(Island island, String name) {
        if (name == null) {
            return null;
        }
        for (UUID member : island.members().keySet()) {
            if (islands.name(member).equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    /** Tab completion: names of the sender's island members that pass {@code filter(actorRole, memberRole)}. */
    List<String> memberNames(CommandSender sender, BiPredicate<Role, Role> filter) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return safely(() -> {
            Optional<Island> own = islands.islandOfPlayer(islands.key(player));
            if (own.isEmpty()) {
                return List.of();
            }
            Role actor = own.get().roleOrNull(islands.key(player));
            List<String> names = new ArrayList<>();
            for (Map.Entry<UUID, Role> entry : Map.copyOf(own.get().members()).entrySet()) {
                if (!entry.getKey().equals(islands.key(player)) && filter.test(actor, entry.getValue())) {
                    names.add(islands.name(entry.getKey()));
                }
            }
            return names;
        });
    }

    /** Tab completion of uncoop: the sender's island's coop players. */
    List<String> coopNames(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return safely(() -> islands.islandOfPlayer(islands.key(player))
                .map(island -> Set.copyOf(island.coops()).stream().map(islands::name).toList()).orElse(List.of()));
    }

    /** Suggestions may run off the main thread: a concurrent change only means no suggestions this time. */
    private static List<String> safely(java.util.function.Supplier<List<String>> names) {
        try {
            return names.get();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private org.bukkit.inventory.ItemStack head(Player viewer, UUID target, String key, TagResolver... placeholders) {
        org.bukkit.inventory.ItemStack head = core.menus().item(Material.PLAYER_HEAD, viewer, IslandsModule.ID, key, placeholders);
        head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(target)));
        return head;
    }

    private void open(Player player, Menu parent, Menu menu) {
        if (parent != null && parent.isOpen()) {
            parent.openChild(menu);
        } else {
            core.menus().open(player, menu);
        }
    }

    private boolean begin(Player player) {
        if (!busy.add(player.getUniqueId())) {
            msg.send(player, "member.busy");
            return false;
        }
        return true;
    }

    private void end(Player player) {
        busy.remove(player.getUniqueId());
    }
}
