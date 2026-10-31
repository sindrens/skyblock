package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The {@code /crates} fragments tile (REVIEW3 R3-18): key fragments from the Depths (tier 11+) in the inventory become
 * Phoenix keys, 3 for 1, through {@link FragmentExchange} (take, save the player file, one key transaction, give back
 * on any failure). Keys from fragments follow {@code keys.tradeable-sources} (source {@code fragment}: tradeable).
 * One exchange per player at a time. Main thread.
 */
final class Fragments {

    /** Key source for the bound/tradeable rule and the audit row. */
    static final String SOURCE = "fragment";

    private final CratesModule m;
    private final Set<UUID> running = new HashSet<>();

    Fragments(CratesModule module) {
        this.m = module;
    }

    boolean enabled() {
        return m.settings.fragmentsEnabled && m.catalog().crate(m.settings.fragmentsCrate).isPresent()
                && m.core.items().get(m.settings.fragmentsItem).isPresent();
    }

    /** Fragments in the player's inventory (storage slots only). */
    int count(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (ItemRegistry.is(stack, m.settings.fragmentsItem)) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    /**
     * Converts fragments into up to {@code wantKeys} keys; {@code done} runs on the main thread afterwards (refresh).
     */
    void exchange(Player player, int wantKeys, Consumer<FragmentExchange.Result> done) {
        if (!enabled()) {
            m.fail(player, "fragments.disabled");
            return;
        }
        UUID id = player.getUniqueId();
        if (!running.add(id)) {
            m.fail(player, "fragments.busy");
            return;
        }
        int perKey = m.settings.fragmentsPerKey;
        int have = count(player);
        UUID profile = m.profiles().profileKey(player);
        String crate = m.settings.fragmentsCrate;
        FragmentExchange.Steps steps = new FragmentExchange.Steps() {
            @Override
            public int take(int fragments) {
                return remove(player.getInventory(), fragments);
            }

            @Override
            public boolean save() {
                return PlayerSaves.save(player, "key fragment exchange");
            }

            @Override
            public CompletableFuture<Boolean> grant(int keys) {
                return m.keys().giveKeys(profile, crate, keys, SOURCE, Actor.player(player));
            }

            @Override
            public void refund(int fragments) {
                m.main().execute(() -> giveBack(player, profile, fragments));
            }
        };
        CompletableFuture<FragmentExchange.Result> result;
        try {
            result = FragmentExchange.run(have, perKey, Math.max(1, Math.min(wantKeys, m.settings.fragmentsMaxKeysPerClick)), steps);
        } catch (RuntimeException e) {
            running.remove(id);
            throw e;
        }
        result.whenCompleteAsync((r, error) -> {
            running.remove(id);
            FragmentExchange.Result outcome = error != null || r == null ? FragmentExchange.Result.FAILED : r;
            report(player, profile, outcome, have, perKey);
            if (done != null) {
                done.accept(outcome);
            }
        }, m.main());
    }

    private void report(Player player, UUID profile, FragmentExchange.Result r, int have, int perKey) {
        switch (r.status()) {
            case DONE -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("fragments", r.fragments());
                d.put("keys", r.keys());
                d.put("crate", m.settings.fragmentsCrate);
                d.put("profile", profile.toString());
                m.audit("fragment_exchange", Actor.player(player), player.getUniqueId(), player.getName(), (long) r.keys(), d);
                if (player.isOnline()) {
                    m.msg.send(player, "fragments.done", Msg.number("fragments", r.fragments()), Msg.number("keys", r.keys()),
                            Msg.mm("key", m.keyMini(m.settings.fragmentsCrate)));
                    m.core.sounds().play(player, "success");
                }
            }
            case NOT_ENOUGH -> {
                if (player.isOnline()) {
                    m.fail(player, "fragments.not-enough", Msg.number("have", have), Msg.number("need", perKey));
                }
            }
            case FAILED -> {
                if (player.isOnline()) {
                    m.fail(player, "fragments.failed");
                }
            }
        }
    }

    /** Removes exactly {@code amount} fragments when the inventory has them (else nothing). Returns the amount removed. */
    private int remove(PlayerInventory inv, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        int have = 0;
        for (ItemStack stack : contents) {
            if (ItemRegistry.is(stack, m.settings.fragmentsItem)) {
                have += stack.getAmount();
            }
        }
        if (have < amount) {
            return 0;
        }
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!ItemRegistry.is(stack, m.settings.fragmentsItem)) {
                continue;
            }
            int use = Math.min(left, stack.getAmount());
            left -= use;
            if (use == stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - use);
            }
        }
        inv.setStorageContents(contents);
        return amount - left;
    }

    /** Gives fragments back after a failed exchange: inventory, then /box (offline: the box of the profile). */
    private void giveBack(Player player, UUID profile, int fragments) {
        List<ItemStack> items = new ArrayList<>();
        int maxStack = Math.max(1, m.core.items().create(m.settings.fragmentsItem, 1, BoxSource.CRATE, null).getMaxStackSize());
        for (int left = fragments; left > 0; left -= maxStack) {
            items.add(m.core.items().create(m.settings.fragmentsItem, Math.min(left, maxStack), BoxSource.CRATE, null));
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("fragments", fragments);
        m.audit("fragment_refund", Actor.system(CratesModule.ID), player.getUniqueId(), player.getName(), (long) fragments, d);
        if (player.isOnline()) {
            ItemBox.give(m.core.services(), player, BoxSource.CRATE, items);
            PlayerSaves.save(player, "key fragment refund");
        } else {
            ItemBox.of(m.core.services()).ifPresentOrElse(
                    box -> box.deliver(profile, items, BoxSource.CRATE, Actor.system(CratesModule.ID)),
                    () -> m.core.plugin().getLogger().severe("Crates: " + fragments + " key fragment(s) of " + player.getName()
                            + " could not be given back (no /box); give them by hand"));
        }
    }
}
