package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Owner answer 251 (round 1): kicking can never take everything from a member for good. When a stored member is kicked
 * (by the island or by staff/moderation), a backup of their island profile is saved: island role, the profile's money
 * and, when the profile is online and active at that moment, its inventory and ender chest. Admins restore it with
 * {@code /isadmin restore <player>} (a list of that player's backups; a click restores after a confirm screen):
 * <ol>
 *   <li>membership with the old role (one transaction, at most once; slots and one island per profile still apply),</li>
 *   <li>money topped up to the amount at the kick (never taken away; one ledger deposit, idempotent per backup),</li>
 *   <li>items, only into an <b>empty</b> inventory and ender chest of that profile while it is online and active, so a
 *       restore can never duplicate items the player still has (else they wait in the backup; click again later).</li>
 * </ol>
 * Everything is audited (category island: {@code island_kick_backup}, {@code island_kick_restore},
 * {@code island_kick_restore_items}).
 */
final class KickBackups {

    /** Ledger reason (and economy audit action) of a restored money top-up. */
    static final String TX_REASON = "island_kick_restore";
    /** A kick note that means "the profile itself is gone": no backup (there is nothing left to restore it to). */
    static final String NOTE_PROFILE_DELETED = "profile deleted";
    private static final int LIST_LIMIT = 45;
    private static final int NAMES_LIMIT = 200;

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final KickBackupStore store;
    private final ModuleMessages msg;
    private final Executor main;
    /** Names of players with open backups (tab completion; refreshed after every save and restore). */
    private final List<String> names = new CopyOnWriteArrayList<>();

    KickBackups(IslandsModule module, IslandManager islands, KickBackupStore store) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.store = store;
        this.msg = module.msg();
        this.main = new MainThread(core.plugin());
    }

    /** Names for {@code /isadmin restore <player>} suggestions. */
    List<String> names() {
        return List.copyOf(names);
    }

    void refreshNames() {
        store.playersWithOpenBackups(NAMES_LIMIT).whenCompleteAsync((players, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Islands: loading kick backup names failed", Database.cause(error));
                return;
            }
            List<String> fresh = new ArrayList<>();
            for (UUID player : players) {
                String name = islands.name(player);
                if (!"?".equals(name) && !fresh.contains(name)) {
                    fresh.add(name);
                }
            }
            names.clear();
            names.addAll(fresh);
        }, main);
    }

    /** Whether a removal gets a backup: kicks and staff/moderation removals of stored members, never leaves or deletions. Pure. */
    static boolean wanted(String reason, Role oldRole, String note) {
        boolean kick = MemberStore.LEFT_KICK.equals(reason) || MemberStore.LEFT_REMOVED.equals(reason);
        return kick && oldRole != null && oldRole != Role.LEADER && oldRole != Role.COOP && !NOTE_PROFILE_DELETED.equals(note);
    }

    // ------------------------------------------------------------------ capture (after the kick was saved, main thread)

    void capture(Island island, UUID profile, Role oldRole, String reason, Actor actor) {
        UUID player = islands.person(profile);
        String playerName = islands.name(profile);
        Player online = islands.online(profile);
        byte[] inventory = null;
        byte[] ender = null;
        if (online != null && !islands.profiles().isSwitching(online.getUniqueId())) {
            inventory = ItemStack.serializeItemsAsBytes(nonNull(online.getInventory().getContents()));
            ender = ItemStack.serializeItemsAsBytes(nonNull(online.getEnderChest().getContents()));
        }
        byte[] inv = inventory;
        byte[] end = ender;
        String islandName = islands.displayName(island);
        long now = System.currentTimeMillis();
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        CompletableFuture<Long> money = economy.isEmpty() ? CompletableFuture.completedFuture(null)
                : economy.get().balance(AccountRef.player(profile), Currency.MONEY).handle((v, e) -> e == null ? v : null);
        money.thenCompose(m -> store.save(new KickBackupStore.NewBackup(island.id(), islandName, profile, player, playerName, oldRole, m,
                inv, end, reason, actor.name(), now)).thenApply(id -> new long[]{id, m == null ? -1 : m}))
                .whenCompleteAsync((saved, error) -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.SEVERE, "Islands: saving the kick backup of " + playerName + " failed",
                                Database.cause(error));
                        return;
                    }
                    Map<String, Object> details = new HashMap<>();
                    details.put("backup", saved[0]);
                    details.put("player", profile.toString());
                    details.put("player_name", playerName);
                    details.put("role", MemberStore.key(oldRole));
                    details.put("money", saved[1]);
                    details.put("items_captured", inv != null);
                    details.put("reason", reason);
                    core.audit().log(AuditCategory.ISLAND, "island_kick_backup", actor, island.id().toString(), islandName, null, null, details);
                    refreshNames();
                }, main);
    }

    // ------------------------------------------------------------------ owner answer 591: disband / reset

    /** Ledger reason of the money a profile reset takes (restored with {@link #TX_REASON}). */
    static final String TX_PROFILE_RESET = "island_profile_reset";
    /** Ledger reason of island bank money given back from a disband/reset backup (into the leader's purse). */
    static final String TX_BANK_RESTORE = "island_bank_restore";

    /** The backup removal value for an island removal reason (disband or reset; delete counts as reset). Pure. */
    static String removalOf(String reason) {
        return IslandStore.REASON_DISBAND.equals(reason) ? KickBackupStore.REMOVAL_DISBAND : KickBackupStore.REMOVAL_RESET;
    }

    /** Money a reset may take: what was backed up, never more than the profile has now (never negative). Pure. */
    static long resetAmount(Long backedUp, long now) {
        return backedUp == null ? 0 : Math.max(0, Math.min(backedUp, now));
    }

    /**
     * Owner answer 591: the island was disbanded or reset. Every member profile gets a backup (role, money, and, when the
     * profile is online and active, inventory + ender chest; the leader's row also keeps {@code bankMoney}), then the
     * profile is reset: its money is taken and the captured inventory and ender chest are emptied. Nothing is reset before
     * its backup is saved. Main thread.
     */
    void resetProfiles(Island island, String islandName, Map<UUID, Role> members, UUID leader, String reason, Actor actor, Long bankMoney) {
        String removal = removalOf(reason);
        for (Map.Entry<UUID, Role> member : members.entrySet()) {
            UUID profile = member.getKey();
            if (member.getValue() == Role.COOP) {
                continue;
            }
            resetProfile(island, islandName, profile, member.getValue(), removal, actor, profile.equals(leader) ? bankMoney : null);
        }
    }

    private void resetProfile(Island island, String islandName, UUID profile, Role role, String removal, Actor actor, Long bankMoney) {
        UUID player = islands.person(profile);
        String playerName = islands.name(profile);
        Player online = islands.online(profile);
        boolean items = module.config().profileResetItems && online != null && !islands.profiles().isSwitching(online.getUniqueId());
        byte[] inv = items ? ItemStack.serializeItemsAsBytes(nonNull(online.getInventory().getContents())) : null;
        byte[] end = items ? ItemStack.serializeItemsAsBytes(nonNull(online.getEnderChest().getContents())) : null;
        long now = System.currentTimeMillis();
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        CompletableFuture<Long> money = economy.isEmpty() ? CompletableFuture.completedFuture(null)
                : economy.get().balance(AccountRef.player(profile), Currency.MONEY).handle((v, e) -> e == null ? v : null);
        money.thenCompose(m -> store.save(new KickBackupStore.NewBackup(island.id(), islandName, profile, player, playerName, role, m,
                        inv, end, removal, actor.name(), now, bankMoney)).thenApply(id -> new Object[]{id, m}))
                .whenCompleteAsync((saved, error) -> {
                    if (error != null) {
                        // No backup, no reset: the profile keeps everything.
                        core.plugin().getLogger().log(Level.SEVERE, "Islands: saving the reset backup of " + playerName + " failed; "
                                + "the profile was not reset", Database.cause(error));
                        return;
                    }
                    long backupId = (Long) saved[0];
                    Long backedUp = (Long) saved[1];
                    boolean cleared = inv != null && clearItems(profile, inv, end);
                    if (inv != null && !cleared) {
                        store.dropItems(backupId); // the player still has them: a restore must not give them twice
                    }
                    Map<String, Object> details = new HashMap<>();
                    details.put("backup", backupId);
                    details.put("player", profile.toString());
                    details.put("player_name", playerName);
                    details.put("role", MemberStore.key(role));
                    details.put("money", backedUp == null ? -1 : backedUp);
                    details.put("bank_money", bankMoney == null ? -1 : bankMoney);
                    details.put("items_cleared", cleared);
                    details.put("removal", removal);
                    core.audit().log(AuditCategory.ISLAND, "island_profile_reset", actor, island.id().toString(), islandName, null, null,
                            details);
                    takeMoney(profile, backedUp, backupId, actor);
                    Player target = islands.online(profile);
                    if (target != null) {
                        msg.send(target, "reset-profile.you", Msg.p("island", islandName));
                    }
                    refreshNames();
                }, main);
    }

    /** Empties the inventory and ender chest only if they still hold exactly what was backed up. Main thread. */
    private boolean clearItems(UUID profile, byte[] inv, byte[] end) {
        Player now = islands.online(profile);
        if (now == null || islands.profiles().isSwitching(now.getUniqueId())) {
            return false;
        }
        byte[] invNow = ItemStack.serializeItemsAsBytes(nonNull(now.getInventory().getContents()));
        byte[] endNow = ItemStack.serializeItemsAsBytes(nonNull(now.getEnderChest().getContents()));
        if (!java.util.Arrays.equals(inv, invNow) || !java.util.Arrays.equals(end, endNow)) {
            return false;
        }
        now.getInventory().clear();
        now.getEnderChest().clear();
        now.updateInventory();
        return true;
    }

    private void takeMoney(UUID profile, Long backedUp, long backupId, Actor actor) {
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        if (!module.config().profileResetMoney || backedUp == null || backedUp <= 0 || economy.isEmpty()) {
            return;
        }
        AccountRef account = AccountRef.player(profile);
        economy.get().balance(account, Currency.MONEY).thenCompose(current -> {
            long amount = resetAmount(backedUp, current);
            if (amount <= 0) {
                return CompletableFuture.completedFuture(null);
            }
            return economy.get().execute(Transaction.withdraw(account, Currency.MONEY, amount, TX_PROFILE_RESET).actor(actor)
                    .reference("island reset backup " + backupId).idempotencyKey(TX_PROFILE_RESET + ":" + backupId));
        }).whenComplete((result, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Islands: taking the money of a reset profile failed (backup "
                        + backupId + ")", Database.cause(error));
            }
        });
    }

    /** Restore of a disband/reset backup: no island to rejoin; money (and the bank money into the purse) plus items. */
    private void restoreRemoval(Player staff, KickBackupStore.Backup backup) {
        Actor actor = Actor.player(staff);
        store.claimRemoval(backup.id(), actor.name(), System.currentTimeMillis()).whenCompleteAsync((claimed, error) -> {
            if (error != null) {
                islands.databaseError(staff, "reset restore", error);
                return;
            }
            if (!claimed) {
                islands.fail(staff, "admin.restore.done-already");
                return;
            }
            topUpMoney(backup, actor).thenCombine(restoreBank(backup, actor), (added, bank) -> new long[]{added, bank})
                    .whenCompleteAsync((amounts, failure) -> {
                        long money = failure == null ? amounts[0] : 0;
                        long bank = failure == null ? amounts[1] : 0;
                        Map<String, Object> details = new HashMap<>();
                        details.put("backup", backup.id());
                        details.put("player", backup.profileId().toString());
                        details.put("player_name", backup.playerName());
                        details.put("money_added", money);
                        details.put("bank_added", bank);
                        core.audit().log(AuditCategory.ISLAND, "island_reset_restore", actor, backup.islandId().toString(),
                                backup.islandName(), null, null, details);
                        msg.send(staff, "admin.restore.reset-done", Msg.p("player", backup.playerName()),
                                Msg.p("island", backup.islandName()), Msg.p("money", net.skybornmc.skyblock.common.util.Format.number(money)),
                                Msg.p("bank", net.skybornmc.skyblock.common.util.Format.number(bank)));
                        core.sounds().play(staff, "success");
                        refreshNames();
                        if (backup.itemsOpen()) {
                            restoreItems(staff, backup);
                        }
                    }, main);
        }, main);
    }

    /** The island bank money of a disband/reset backup, into the profile's purse (once per backup). */
    private CompletableFuture<Long> restoreBank(KickBackupStore.Backup backup, Actor actor) {
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        if (backup.bankMoney() == null || backup.bankMoney() <= 0 || economy.isEmpty()) {
            return CompletableFuture.completedFuture(0L);
        }
        long amount = backup.bankMoney();
        return economy.get().execute(Transaction.deposit(AccountRef.player(backup.profileId()), Currency.MONEY, amount, TX_BANK_RESTORE)
                        .actor(actor).reference("island reset backup " + backup.id()).idempotencyKey(TX_BANK_RESTORE + ":" + backup.id()))
                .thenApply(r -> r.ok() ? amount : 0L);
    }

    private static ItemStack[] nonNull(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            out[i] = contents[i] == null ? ItemStack.empty() : contents[i];
        }
        return out;
    }

    // ------------------------------------------------------------------ /isadmin restore <player>

    /** Opens the backups of {@code playerName} (newest first). */
    void open(Player staff, String playerName) {
        OfflinePlayer target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(playerName);
        }
        if (target == null) {
            islands.fail(staff, "admin.restore.unknown", Msg.p("player", playerName));
            return;
        }
        UUID player = target.getUniqueId();
        String shown = target.getName() == null ? playerName : target.getName();
        store.ofPlayer(player, LIST_LIMIT).whenCompleteAsync((list, error) -> {
            if (error != null) {
                islands.databaseError(staff, "kick backups", error);
                return;
            }
            if (!staff.isOnline()) {
                return;
            }
            core.menus().open(staff, new KickBackupsMenu(module, this, shown, list));
        }, main);
    }

    /** A click on a backup: confirm, then restore what is still open (membership + money, then items). */
    void confirmRestore(Player staff, KickBackupStore.Backup backup) {
        if (!backup.open() && !backup.itemsOpen()) {
            islands.fail(staff, "admin.restore.done-already");
            return;
        }
        var placeholders = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                Msg.p("player", backup.playerName()), Msg.p("island", backup.islandName()),
                Msg.p("role", module.raw("role." + MemberStore.key(backup.role())))};
        core.menus().open(staff, new ConfirmMenu(
                viewer -> module.text(viewer, "admin.restore.crumb"),
                core.menus().item(Material.TOTEM_OF_UNDYING, staff, IslandsModule.ID, "admin.restore.shown", placeholders),
                viewer -> module.lines(viewer, "admin.restore.details", placeholders),
                viewer -> restore(viewer, backup),
                viewer -> msg.send(viewer, "admin.cancelled")));
    }

    private void restore(Player staff, KickBackupStore.Backup backup) {
        if (!islands.profiles().isLive(backup.profileId())) {
            islands.fail(staff, "admin.restore.profile-deleted", Msg.p("player", backup.playerName()));
            return;
        }
        if (!backup.open()) {
            restoreItems(staff, backup);
            return;
        }
        if (backup.islandRemoval()) {
            restoreRemoval(staff, backup);
            return;
        }
        Island island = islands.byId(backup.islandId());
        if (island == null) {
            islands.fail(staff, "admin.restore.island-gone", Msg.p("island", backup.islandName()));
            return;
        }
        Actor actor = Actor.player(staff);
        store.restoreMembership(backup.id(), actor.name(), System.currentTimeMillis(), module.config().memberLimits())
                .whenCompleteAsync((result, error) -> {
                    if (error != null) {
                        islands.databaseError(staff, "kick restore", error);
                        return;
                    }
                    switch (result) {
                        case KickBackupStore.RestoreResult.AlreadyRestored ignored -> islands.fail(staff, "admin.restore.done-already");
                        case KickBackupStore.RestoreResult.Refused refused -> refusedMessage(staff, backup, refused.reason());
                        case KickBackupStore.RestoreResult.Restored restored -> restored(staff, island, backup, restored, actor);
                    }
                    refreshNames();
                }, main);
    }

    private void refusedMessage(Player staff, KickBackupStore.Backup backup, MemberStore.AddResult reason) {
        switch (reason) {
            case MemberStore.AddResult.HasIsland ignored -> islands.fail(staff, "admin.restore.has-island", Msg.p("player", backup.playerName()));
            case MemberStore.AddResult.Full full -> islands.fail(staff, "admin.restore.full", Msg.p("island", backup.islandName()),
                    Msg.p("slots", full.slots()));
            case MemberStore.AddResult.IslandGone ignored -> islands.fail(staff, "admin.restore.island-gone", Msg.p("island", backup.islandName()));
            case MemberStore.AddResult.OwnProfile ignored -> islands.fail(staff, "admin.addmember.own-profile", Msg.p("player", backup.playerName()));
            case MemberStore.AddResult.Added ignored -> islands.fail(staff, "error.database");
        }
    }

    private void restored(Player staff, Island island, KickBackupStore.Backup backup, KickBackupStore.RestoreResult.Restored done, Actor actor) {
        Role role = backup.role() == Role.LEADER || backup.role() == Role.COOP ? Role.MEMBER : backup.role();
        if (islands.byId(island.id()) == island) {
            islands.memberManager().invites().removeTarget(backup.profileId());
            islands.cacheJoin(island, backup.profileId(), role, done.joinedAt(), null);
            if (done.converted()) {
                island.markTeam();
            }
            islands.refreshBorders(island);
        }
        topUpMoney(backup, actor).whenCompleteAsync((added, error) -> {
            long money = error == null && added != null ? added : 0;
            Map<String, Object> details = new HashMap<>();
            details.put("backup", backup.id());
            details.put("player", backup.profileId().toString());
            details.put("player_name", backup.playerName());
            details.put("role", MemberStore.key(role));
            details.put("money_added", money);
            core.audit().log(AuditCategory.ISLAND, "island_kick_restore", actor, island.id().toString(), backup.islandName(), null, null, details);
            msg.send(staff, "admin.restore.done", Msg.p("player", backup.playerName()), Msg.p("island", backup.islandName()),
                    Msg.p("role", module.raw("role." + MemberStore.key(role))), Msg.p("money", net.skybornmc.skyblock.common.util.Format.number(money)));
            core.sounds().play(staff, "success");
            Player target = islands.online(backup.profileId());
            if (target != null) {
                msg.send(target, "admin.restore.you", Msg.p("island", backup.islandName()));
            }
            if (backup.itemsOpen()) {
                restoreItems(staff, backup);
            }
        }, main);
    }

    /** Deposits what is missing to reach the money at the kick (never removes money). Completes with the amount added. */
    private CompletableFuture<Long> topUpMoney(KickBackupStore.Backup backup, Actor actor) {
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        if (backup.money() == null || backup.money() <= 0 || economy.isEmpty()) {
            return CompletableFuture.completedFuture(0L);
        }
        AccountRef account = AccountRef.player(backup.profileId());
        return economy.get().balance(account, Currency.MONEY).thenCompose(current -> {
            long missing = missingMoney(backup.money(), current);
            if (missing <= 0) {
                return CompletableFuture.completedFuture(0L);
            }
            return economy.get().execute(Transaction.deposit(account, Currency.MONEY, missing, TX_REASON).actor(actor)
                    .reference("island kick backup " + backup.id()).idempotencyKey(TX_REASON + ":" + backup.id()))
                    .thenApply(r -> r.ok() ? missing : 0L);
        });
    }

    /** Money to add so the profile has at least what it had at the kick (0 when it has that much or more). Pure. */
    static long missingMoney(long atKick, long now) {
        return Math.max(0, atKick - Math.max(0, now));
    }

    /** Items only into an empty inventory and ender chest of the online, active profile (no duplicates possible). */
    private void restoreItems(Player staff, KickBackupStore.Backup backup) {
        if (!backup.itemsOpen()) {
            return;
        }
        Player target = islands.online(backup.profileId());
        if (target == null || islands.profiles().isSwitching(target.getUniqueId())) {
            msg.send(staff, "admin.restore.items-offline", Msg.p("player", backup.playerName()));
            return;
        }
        if (!empty(target.getInventory().getContents()) || !empty(target.getEnderChest().getContents())) {
            msg.send(staff, "admin.restore.items-not-empty", Msg.p("player", backup.playerName()));
            return;
        }
        store.claimItems(backup.id(), System.currentTimeMillis()).whenCompleteAsync((claimed, error) -> {
            if (error != null) {
                islands.databaseError(staff, "kick restore items", error);
                return;
            }
            if (!claimed) {
                islands.fail(staff, "admin.restore.done-already");
                return;
            }
            Player now = islands.online(backup.profileId());
            if (now == null || !empty(now.getInventory().getContents()) || !empty(now.getEnderChest().getContents())) {
                store.releaseItems(backup.id());
                msg.send(staff, "admin.restore.items-offline", Msg.p("player", backup.playerName()));
                return;
            }
            now.getInventory().setContents(fit(ItemStack.deserializeItemsFromBytes(backup.inventory()), now.getInventory().getSize()));
            now.getEnderChest().setContents(fit(ItemStack.deserializeItemsFromBytes(backup.enderChest()), now.getEnderChest().getSize()));
            core.audit().log(AuditCategory.ISLAND, "island_kick_restore_items", Actor.player(staff), backup.islandId().toString(),
                    backup.islandName(), null, null, Map.of("backup", backup.id(), "player", backup.profileId().toString(),
                            "player_name", backup.playerName()));
            msg.send(staff, "admin.restore.items-done", Msg.p("player", backup.playerName()));
            msg.send(now, "admin.restore.items-you");
            core.sounds().play(staff, "success");
        }, main);
    }

    private static boolean empty(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack[] fit(ItemStack[] items, int size) {
        ItemStack[] out = new ItemStack[size];
        for (int i = 0; i < Math.min(size, items.length); i++) {
            out[i] = items[i] == null || items[i].isEmpty() ? null : items[i];
        }
        return out;
    }
}
