package net.skybornmc.skyblock.blackmarket;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Buying (PHASE-10 M10.11): every rule is checked on the click and again when the confirm runs; the payment, the shared
 * stock, the limits and the buy row are then one database transaction ({@link EconomyService#executeGuarded}) with an
 * idempotency key per buy. Only after it committed is the reward handed out ({@link Deliveries}).
 */
final class Purchases {

    /** What is about to be bought. {@code slotIndex} is -1 for the fixed row and the charter. */
    record Plan(OfferCatalog.Offer offer, String offerId, String kind, int amount, Currency currency, long price, int slotIndex,
                long rotationId, UUID island, int charterSize) {

        boolean charter() {
            return offer == null;
        }
    }

    /** The Island Charter as the viewer sees it right now. */
    record CharterView(CharterRules.State state, CharterRules.Step step, IslandService.IslandInfo island, boolean allowed) {
    }

    static final String CHARTER_KIND = "charter";

    private final BlackMarketModule m;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    Purchases(BlackMarketModule module) {
        this.m = module;
    }

    void forget(UUID account) {
        inFlight.remove(account);
    }

    boolean busy(UUID account) {
        return inFlight.contains(account);
    }

    // ================================================================== checks (main thread)

    /** Why this offer can't be bought by the viewer right now (a message key), or empty. */
    Optional<String> problem(Player p, OfferCatalog.Offer offer) {
        if (!m.settings.enabled) {
            return Optional.of("disabled");
        }
        if (m.economy().isEmpty() || !m.live(offer)) {
            return Optional.of("buy.unavailable");
        }
        if (offer.slot() != OfferCatalog.Slot.FIXED) {
            Optional<BlackMarketStore.RotationRow> row = m.market().row(offer.id());
            if (row.isEmpty() || !m.market().ready()) {
                return Optional.of("buy.rotated");
            }
            if (row.get().left() == 0) {
                return Optional.of("buy.sold-out");
            }
        }
        if (offer.kind() == OfferCatalog.Kind.TAG
                && m.cosmetics().map(c -> c.owns(p.getUniqueId(), Cosmetics.Kind.TAG, offer.ref())).orElse(false)) {
            return Optional.of("buy.owned");
        }
        if (m.counts().left(p.getUniqueId(), offer) <= 0) {
            return Optional.of("buy.limit");
        }
        return Optional.empty();
    }

    /** The viewer's Island Charter state. */
    CharterView charter(Player p) {
        IslandService islands = m.islands().orElse(null);
        IslandService.IslandInfo island = islands == null ? null : islands.islandOf(p.getUniqueId()).orElse(null);
        if (island == null) {
            return new CharterView(CharterRules.State.NO_STEP, null, null, false);
        }
        CharterRules.Next next = CharterRules.next(island.sizeChunks(), m.settings.fromSize, m.charterSteps(), island.maxSizeChunks());
        boolean allowed = islands.hasIslandPermission(island.id(), p.getUniqueId(), IslandService.PERM_UPGRADES);
        return new CharterView(next.state(), next.step(), island, allowed);
    }

    /** Why the viewer can't buy the next charter step with {@code currency} (a message key), or empty. */
    Optional<String> charterProblem(Player p, CharterView view, Currency currency) {
        if (!m.settings.enabled) {
            return Optional.of("disabled");
        }
        if (!m.settings.charterEnabled || m.economy().isEmpty() || m.islands().isEmpty()) {
            return Optional.of("buy.unavailable");
        }
        if (view.island() == null) {
            return Optional.of("charter.no-island");
        }
        return switch (view.state()) {
            case NEEDS_NORMAL_MAX -> Optional.of("charter.needs-normal");
            case MAXED -> Optional.of("charter.maxed");
            case NO_STEP -> Optional.of("charter.no-step");
            case READY -> !view.allowed() ? Optional.of("charter.no-permission")
                    : priceOf(view.step(), currency) <= 0 ? Optional.of("buy.unavailable") : Optional.empty();
        };
    }

    static long priceOf(CharterRules.Step step, Currency currency) {
        return step == null ? 0 : currency == Currency.GEMS ? step.gems() : step.shards();
    }

    // ================================================================== plans

    /** A rotating or fixed offer: the plan, or empty after telling the player why not. */
    Optional<Plan> plan(Player p, OfferCatalog.Offer offer) {
        Optional<String> problem = problem(p, offer);
        if (problem.isPresent()) {
            m.fail(p, problem.get());
            return Optional.empty();
        }
        if (offer.kind().item() && ItemBox.refuses(m.core.services(), p)) {
            return Optional.empty(); // the box module told the player its box is full
        }
        int slot = offer.slot() == OfferCatalog.Slot.FIXED ? -1 : m.market().row(offer.id()).map(BlackMarketStore.RotationRow::index).orElse(-1);
        long rotationId = slot >= 0 ? m.market().currentId() : m.rotation().id(System.currentTimeMillis());
        return Optional.of(new Plan(offer, offer.id(), offer.kind().key(), offer.amount(), Currency.SHARDS, offer.price(), slot,
                rotationId, null, 0));
    }

    /** The next Island Charter step paid with {@code currency}: the plan, or empty after telling the player why not. */
    Optional<Plan> charterPlan(Player p, Currency currency) {
        CharterView view = charter(p);
        Optional<String> problem = charterProblem(p, view, currency);
        if (problem.isPresent()) {
            m.fail(p, problem.get(), Msg.p("size", sizeText(m.settings.fromSize)));
            return Optional.empty();
        }
        int size = view.step().size();
        return Optional.of(new Plan(null, "charter_" + size, CHARTER_KIND, 1, currency, priceOf(view.step(), currency), -1,
                m.rotation().id(System.currentTimeMillis()), view.island().id(), size));
    }

    static String sizeText(int size) {
        return size + "x" + size;
    }

    // ================================================================== buying

    /** Runs a confirmed plan: rules again, then the transaction. {@code after} runs on the main thread when it is done. */
    void buy(Player p, Plan plan, Runnable after) {
        UUID account = p.getUniqueId();
        Optional<Plan> fresh = plan.charter() ? charterPlan(p, plan.currency()) : plan(p, plan.offer());
        if (fresh.isEmpty()) {
            after.run();
            return;
        }
        Plan now = fresh.get();
        if (!now.offerId().equals(plan.offerId()) || now.price() != plan.price() || now.rotationId() != plan.rotationId()) {
            m.fail(p, "buy.changed");
            after.run();
            return;
        }
        EconomyService economy = m.economy().orElse(null);
        if (economy == null) {
            m.fail(p, "buy.unavailable");
            after.run();
            return;
        }
        if (!inFlight.add(account)) {
            m.fail(p, "buy.busy");
            after.run();
            return;
        }
        String ref = chooseRef(now);
        if (ref == null) {
            inFlight.remove(account);
            m.fail(p, "buy.unavailable");
            after.run();
            return;
        }
        UUID profile = m.profiles().profileKey(p);
        long time = System.currentTimeMillis();
        UUID buyId = UUID.randomUUID();
        BlackMarketStore.BuyRow row = new BlackMarketStore.BuyRow(buyId, account, profile, now.rotationId(), now.offerId(), now.kind(),
                ref, now.amount(), now.currency().key(), now.price(), now.island(), BlackMarketStore.PENDING, time);
        List<BlackMarketStore.Count> limits = new ArrayList<>();
        String periodKey = null;
        if (!now.charter()) {
            periodKey = m.counts().periodKey(now.offer(), time);
            limits.add(new BlackMarketStore.Count(account.toString(), now.offerId(), periodKey, now.offer().limit()));
        }
        BlackMarketStore.Count charter = now.charter()
                ? new BlackMarketStore.Count("island:" + now.island(), now.offerId(), "s:" + m.settings.seasonId, 1) : null;
        AtomicReference<BlackMarketStore.Outcome> outcome = new AtomicReference<>(BlackMarketStore.Outcome.OK);
        TxGuard guard = c -> {
            BlackMarketStore.Outcome o = BlackMarketStore.buy(c, row, now.slotIndex(), limits, charter, time);
            outcome.set(o);
            return o == BlackMarketStore.Outcome.OK;
        };
        Transaction tx = Transaction.withdraw(AccountRef.player(profile), now.currency(), now.price(), TxReason.BLACK_MARKET)
                .actor(Actor.player(p))
                .reference("bm:" + now.offerId())
                .idempotencyKey("bm:" + buyId)
                .detail("offer", now.offerId())
                .detail("rotation", now.rotationId())
                .detail("buy", buyId.toString());
        String counted = periodKey;
        economy.executeGuarded(tx, guard).whenCompleteAsync((result, error) -> {
            inFlight.remove(account);
            TxResult.Status status = error != null || result == null ? TxResult.Status.ERROR : result.status();
            switch (status) {
                case SUCCESS -> bought(p, now, row, counted, result.fromBalance());
                case INSUFFICIENT_FUNDS -> m.fail(p, now.currency() == Currency.GEMS ? "buy.no-gems" : "buy.no-shards",
                        Msg.p("price", m.price(now.currency(), now.price())));
                case DUPLICATE -> refused(p, outcome.get());
                default -> {
                    if (error != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Black Market: buy of " + now.offerId() + " by " + p.getName()
                                + " failed; nothing was paid", Database.cause(error));
                    }
                    m.fail(p, "buy.failed");
                }
            }
            if (now.slotIndex() >= 0) {
                m.market().refreshStock();
            }
            after.run();
        }, m.main());
    }

    /** The concrete reward of a random offer (book enchant, spawner type), chosen now so the buy row and audit name it. */
    String chooseRef(Plan plan) {
        if (plan.charter()) {
            return String.valueOf(plan.charterSize());
        }
        return chooseRef(m, plan.offer());
    }

    static String chooseRef(BlackMarketModule m, OfferCatalog.Offer offer) {
        return switch (offer.kind()) {
            case ENCHANT_BOOK -> {
                List<String> books = m.books(offer.ref());
                yield books.isEmpty() ? null : books.get(ThreadLocalRandom.current().nextInt(books.size())) + ":" + offer.level();
            }
            case SPAWNER -> {
                List<String> types = m.spawnerTypes(offer);
                yield types.isEmpty() ? null : types.get(ThreadLocalRandom.current().nextInt(types.size()));
            }
            case SCROLL -> "scroll";
            case COMING_SOON -> null;
            default -> offer.ref();
        };
    }

    private void refused(Player p, BlackMarketStore.Outcome outcome) {
        String key = switch (outcome) {
            case SOLD_OUT -> "buy.sold-out";
            case LIMIT -> "buy.limit";
            case CHARTER_TAKEN -> "charter.taken";
            case STALE -> "buy.rotated";
            default -> "buy.changed";
        };
        m.fail(p, key);
        m.counts().load(p);
    }

    private void bought(Player p, Plan plan, BlackMarketStore.BuyRow row, String periodKey, Long balanceAfter) {
        if (periodKey != null) {
            m.counts().add(p.getUniqueId(), plan.offerId(), periodKey);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("buy", row.id().toString());
        d.put("offer", plan.offerId());
        d.put("reward", row.kind() + ":" + row.ref());
        d.put("amount", row.amount());
        d.put("currency", row.currency());
        d.put("profile", row.profile().toString());
        d.put("rotation", row.rotationId());
        if (row.island() != null) {
            d.put("island", row.island().toString());
        }
        if (balanceAfter != null) {
            d.put("balance_after", balanceAfter);
        }
        m.audit("buy", Actor.player(p), p.getUniqueId(), p.getName(), plan.price(), d);
        Component name = plan.charter() ? m.msg.component(p, "charter.name", Msg.p("size", sizeText(plan.charterSize())))
                : m.offerName(p, plan.offer());
        m.msg.send(p, "buy.done", Msg.c("offer", name), Msg.p("price", m.price(plan.currency(), plan.price())),
                Msg.p("balance", balanceAfter == null ? "?" : m.price(plan.currency(), balanceAfter)));
        m.sound(p, m.settings.buySound);
        m.deliveries().handOut(row);
    }
}
