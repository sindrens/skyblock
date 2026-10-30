package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.moderation.SanctionService.Use;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P7 pure logic: island ladder steps (spec 13.2: owner never island-banned from their own island, unavailable provider
 * falls to the next step), sanction checks per path and the economy safety net (13.1), expiry, "take at most the
 * balance", approval rules for sanctions, and the shipped config.
 */
class P7LogicTest {

    private static final Path BUNDLED = Path.of("src/main/resources/punishments.yml");
    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");
    private static final UUID STEVE = TestData.STEVE;
    private static final UUID ALEX = TestData.ALEX;
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final long NOW = 1_800_000_000_000L;

    private static List<Templates.Step> ladder(String... steps) {
        List<Templates.Step> out = new ArrayList<>();
        for (String s : steps) {
            out.add(Templates.Step.parse(s));
        }
        return out;
    }

    private static final IslandSteps.Context MEMBER = new IslandSteps.Context(true, ISLAND, "Alex's island", false);
    private static final IslandSteps.Context OWNER = new IslandSteps.Context(true, ISLAND, "Steve's island", true);

    // ================================================================== island steps (13.2)

    @Test
    void islandStepsParseAndRoundTrip() {
        for (String raw : List.of("island-kick", "island-ban 1d", "island-ban perm", "coop-remove", "coop-block 3d", "coop-block perm")) {
            Templates.Step step = Templates.Step.parse(raw);
            assertTrue(step.type().island(), raw);
            assertEquals(raw, step.describe());
            assertEquals(step, Templates.Step.parse(step.describe()));
        }
        assertEquals(Punishment.Type.ISLAND_BAN, Templates.Step.parse("island-ban 1d").type());
        assertEquals(Duration.ofDays(7), Templates.Step.parse("coop-block 7d").duration());
        assertEquals("coop-block 1w", Templates.Step.parse("coop-block 7d").describe(), "7d is written as 1w");
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("island-kick 1d"));
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("coop-remove 2h"));
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("island-bann 1d"));
        assertTrue(Templates.Step.KINDS.contains("coop-block"), "did-you-mean knows the island kinds");
    }

    @Test
    void islandKickAndCoopRemoveAreOneOffLikeAKick() {
        long created = NOW - 1000;
        Punishment kick = TestData.punishment(1, Punishment.Type.ISLAND_KICK, created, null, null, null);
        Punishment remove = TestData.punishment(2, Punishment.Type.COOP_REMOVE, created, null, null, null);
        Punishment ban = TestData.punishment(3, Punishment.Type.ISLAND_BAN, created, NOW + 1000, null, null);
        Punishment block = TestData.punishment(4, Punishment.Type.COOP_BLOCK, created, null, null, null);
        assertFalse(kick.active(NOW));
        assertEquals(Punishment.Status.DONE, remove.status(NOW));
        assertTrue(ban.active(NOW));
        assertFalse(ban.active(NOW + 2000), "island bans expire");
        assertTrue(block.active(NOW), "perm coop block");
        assertTrue(Punishment.Type.ISLAND_BAN.timed() && Punishment.Type.COOP_BLOCK.timed());
        assertFalse(Punishment.Type.ISLAND_KICK.timed());
        assertEquals(Undo.Result.NOT_UNDOABLE, Undo.check(kick, ALEX, true, Duration.ofMinutes(2), NOW));
    }

    @Test
    void ownerIsNeverIslandBannedFromTheirOwnIsland() {
        List<Templates.Step> ladder = ladder("warn", "island-ban 1d", "coop-block 7d", "ban 7d");
        List<Templates.Step> eff = IslandSteps.effective(ladder, OWNER);
        assertEquals("warn", eff.get(0).describe());
        assertEquals("coop-block 1w", eff.get(1).describe(), "skipped to coop-block");
        assertEquals("coop-block 1w", eff.get(2).describe());
        assertEquals("ban 1w", eff.get(3).describe());
        assertEquals(IslandSteps.Why.OWNER, IslandSteps.replaced(ladder, 1, OWNER));
        assertNull(IslandSteps.replaced(ladder, 2, OWNER), "coop-block itself works for owners");
        for (Templates.Step s : eff) {
            assertFalse(s.type() == Punishment.Type.ISLAND_BAN || s.type() == Punishment.Type.ISLAND_KICK
                    || s.type() == Punishment.Type.COOP_REMOVE, "no island step on their own island: " + s.describe());
        }
    }

    @Test
    void membersGetTheLadderAsWritten() {
        List<Templates.Step> ladder = ladder("warn", "island-kick", "island-ban 1d", "coop-remove", "coop-block 7d");
        assertEquals(ladder, IslandSteps.effective(ladder, MEMBER));
        Templates.Template t = new Templates.Template("coop-griefing", "gameplay", null, null, ladder);
        assertSame(t, IslandSteps.adapt(t, MEMBER), "nothing replaced = same template");
    }

    @Test
    void unavailableProviderFallsToTheNextStep() {
        List<Templates.Step> ladder = ladder("warn", "island-ban 1d", "coop-block 7d", "ban 7d");
        List<Templates.Step> eff = IslandSteps.effective(ladder, IslandSteps.Context.UNAVAILABLE);
        assertEquals(List.of("warn", "ban 1w", "ban 1w", "ban 1w"), eff.stream().map(Templates.Step::describe).toList());
        assertEquals(IslandSteps.Why.UNAVAILABLE, IslandSteps.replaced(ladder, 2, IslandSteps.Context.UNAVAILABLE));
        assertEquals(IslandSteps.Why.UNAVAILABLE, IslandSteps.replaced(ladder, 2, null));
    }

    @Test
    void withoutATeamIslandOnlyCoopBlockIsPossible() {
        List<Templates.Step> ladder = ladder("island-kick", "coop-remove", "coop-block 3d", "ban 1d");
        List<Templates.Step> eff = IslandSteps.effective(ladder, IslandSteps.Context.NO_ISLAND);
        assertEquals(List.of("coop-block 3d", "coop-block 3d", "coop-block 3d", "ban 1d"),
                eff.stream().map(Templates.Step::describe).toList());
        assertEquals(IslandSteps.Why.NO_ISLAND, IslandSteps.replaced(ladder, 0, IslandSteps.Context.NO_ISLAND));
    }

    @Test
    void lastStepFallsBackToAnEarlierOneOrAWarning() {
        assertEquals("mute 1h", IslandSteps.effective(ladder("mute 1h", "island-ban 1d"), OWNER).get(1).describe());
        assertEquals("warn", IslandSteps.effective(ladder("island-ban 1d"), IslandSteps.Context.UNAVAILABLE).getFirst().describe());
    }

    @Test
    void adaptIsIdempotentAndKeepsTheReason() {
        Templates.Template t = new Templates.Template("coop-griefing", "gameplay", null, null,
                ladder("warn", "island-ban 1d", "coop-block 7d", "ban 7d"), Duration.ofDays(90), true, false);
        Templates.Template once = IslandSteps.adapt(t, OWNER);
        assertEquals("coop-griefing", once.id());
        assertEquals(Duration.ofDays(90), once.offenceExpire());
        assertFalse(once.appealable());
        assertEquals(once.ladder(), IslandSteps.adapt(once, OWNER).ladder());
        assertEquals(t.ladder().size(), once.ladder().size(), "the offence count still picks the same index");
    }

    @Test
    void probationRaiseWorksOnTheEffectiveLadder() {
        List<Templates.Step> eff = IslandSteps.effective(ladder("warn", "island-ban 1d", "coop-block 7d", "ban 7d"), OWNER);
        int raised = Probation.raise(0, 1, eff.size());
        assertEquals("coop-block 1w", eff.get(raised).describe(), "offence #1 + probation = step 2 (owner: coop-block)");
    }

    @Test
    void anotherActiveCoopBlockKeepsItsEnd() {
        Punishment lifted = TestData.punishment(5, Punishment.Type.COOP_BLOCK, NOW - 1000, NOW + 60_000, null, null);
        Punishment other = TestData.punishment(6, Punishment.Type.COOP_BLOCK, NOW - 2000, NOW + 120_000, null, null);
        Punishment expired = TestData.punishment(7, Punishment.Type.COOP_BLOCK, NOW - 9000, NOW - 1, null, null);
        assertEquals(NOW + 120_000, IslandPunish.remainingBlock(List.of(lifted, other, expired), 5, NOW));
        assertEquals(0, IslandPunish.remainingBlock(List.of(lifted, expired), 5, NOW));
        Punishment perm = TestData.punishment(8, Punishment.Type.COOP_BLOCK, NOW - 2000, null, null, null);
        assertEquals(Long.MAX_VALUE, IslandPunish.remainingBlock(List.of(lifted, perm), 5, NOW));
    }

    @Test
    void islandStepsNeedTheirOwnPermissionAndNeverAnApproval() {
        assertEquals(ModerationModule.PERM_ISLAND, ModerationModule.permissionFor(Punishment.Type.ISLAND_BAN, false));
        assertEquals(ModerationModule.PERM_ISLAND, ModerationModule.permissionFor(Punishment.Type.COOP_REMOVE, false));
        PunishDecision.Actor helper = new PunishDecision.Actor("Alex", false, java.util.Set.of(Punishment.Type.WARN),
                false, false, Duration.ofDays(1), false, ALEX, false, false);
        PunishDecision.Input in = new PunishDecision.Input(helper, Punishment.Type.COOP_BLOCK, null, false, false, false,
                false, true, null, null, NOW);
        PunishDecision.Verdict v = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT).decide(in);
        assertEquals("permission", v.stage(), "no island permission = blocked");
        PunishDecision.Actor staff = new PunishDecision.Actor("Alex", false, java.util.Set.of(Punishment.Type.values()),
                false, false, Duration.ofDays(1), false, ALEX, false, false);
        assertTrue(PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT).decide(in.withActor(staff)).applied(),
                "a perm coop block needs no approval and is not limited by the ban/mute rank limit");
    }

    @Test
    void suggestionsNeverCarryIslandSteps() {
        Templates.Template t = new Templates.Template("coop-griefing", "gameplay", null, null,
                ladder("island-ban 1d", "ban 7d"));
        ApiSuggestions.Plan plan = ApiSuggestions.plan(t, List.of(), "test", "x", NOW, null);
        assertEquals("ban 1w", plan.step().describe());
    }

    // ================================================================== sanctions (13.1)

    private static Sanction sanction(long id, Sanction.Kind kind, Long expires, Sanction.State state) {
        return new Sanction(id, kind, STEVE, "Steve", null, ALEX, "Alex", "Duping", NOW - 1000, expires, null, null, null,
                state, null, null, null, null);
    }

    private static Function<UUID, Collection<Sanction>> of(Map<UUID, List<Sanction>> map) {
        return u -> map.getOrDefault(u, List.of());
    }

    @Test
    void eachPathIsBlockedByTheRightSanction() {
        SanctionRules.Settings s = SanctionRules.Settings.DEFAULT;
        List<Sanction> freeze = List.of(sanction(1, Sanction.Kind.FREEZE, null, Sanction.State.ACTIVE));
        List<Sanction> trade = List.of(sanction(2, Sanction.Kind.TRADE, NOW + 1000, Sanction.State.ACTIVE));
        for (Use use : Use.values()) {
            assertTrue(SanctionRules.blocking(freeze, use, s, NOW).isPresent(), "freeze blocks " + use);
        }
        for (Use use : List.of(Use.PAY, Use.NOTE, Use.AUCTION_LIST, Use.AUCTION_BUY, Use.TRADE)) {
            assertTrue(SanctionRules.blocking(trade, use, s, NOW).isPresent(), "trade blocks " + use);
        }
        assertFalse(SanctionRules.blocking(trade, Use.BANK, s, NOW).isPresent(), "block trading leaves the bank alone");
        assertFalse(SanctionRules.blocking(trade, Use.SHOP, s, NOW).isPresent(), "block trading leaves the shop alone");
        List<Sanction> both = List.of(trade.getFirst(), freeze.getFirst());
        assertEquals(Sanction.Kind.FREEZE, SanctionRules.blocking(both, Use.PAY, s, NOW).orElseThrow().kind(), "freeze reported first");
    }

    @Test
    void expiredLiftedAndOneOffSanctionsBlockNothing() {
        SanctionRules.Settings s = SanctionRules.Settings.DEFAULT;
        Sanction ran = sanction(1, Sanction.Kind.FREEZE, NOW, Sanction.State.ACTIVE);
        Sanction lifted = sanction(2, Sanction.Kind.FREEZE, null, Sanction.State.LIFTED);
        Sanction money = sanction(3, Sanction.Kind.MONEY, null, Sanction.State.ACTIVE);
        assertTrue(SanctionRules.blocking(List.of(ran, lifted, money), Use.PAY, s, NOW).isEmpty());
        assertTrue(ran.expiredAt(NOW), "ran out exactly now = expired (marked by the timer)");
        assertTrue(sanction(4, Sanction.Kind.TRADE, NOW + 1, Sanction.State.ACTIVE).blocksAt(NOW));
        assertTrue(money.restorable());
        assertFalse(lifted.restorable());
    }

    @Test
    void switchedOffBlocksNothing() {
        SanctionRules.Settings d = SanctionRules.Settings.DEFAULT;
        SanctionRules.Settings off = new SanctionRules.Settings(false, d.durations(), d.reasons(), d.freezeBlocks(),
                d.freezeCurrencies(), d.freezeExempt(), d.freezeReceive(), d.tradeBlocks(), d.tradeReasons(), d.moneyMax(),
                d.dupeSuggest(), d.dupeReason());
        List<Sanction> freeze = List.of(sanction(1, Sanction.Kind.FREEZE, null, Sanction.State.ACTIVE));
        assertTrue(SanctionRules.blocking(freeze, Use.PAY, off, NOW).isEmpty());
        Transaction pay = Transaction.transfer(AccountRef.player(STEVE), AccountRef.player(ALEX), Currency.MONEY, 5, TxReason.PAY);
        assertFalse(SanctionRules.blocks(pay, of(Map.of(STEVE, freeze)), u -> u, off, NOW));
    }

    @Test
    void economySafetyNetForAFrozenBalance() {
        SanctionRules.Settings s = SanctionRules.Settings.DEFAULT;
        UUID profile2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        Function<UUID, UUID> owner = u -> u.equals(profile2) ? STEVE : u;
        Function<UUID, Collection<Sanction>> active = of(Map.of(STEVE, List.of(sanction(1, Sanction.Kind.FREEZE, null,
                Sanction.State.ACTIVE))));
        // money leaving any profile or bank of the frozen player
        assertTrue(SanctionRules.blocks(Transaction.transfer(AccountRef.player(STEVE), AccountRef.player(ALEX), Currency.MONEY, 5,
                TxReason.PAY), active, owner, s, NOW));
        assertTrue(SanctionRules.blocks(Transaction.withdraw(AccountRef.player(profile2), Currency.MONEY, 5, TxReason.SHOP_BUY),
                active, owner, s, NOW), "every profile of the player (account-wide)");
        assertTrue(SanctionRules.blocks(Transaction.transfer(AccountRef.bank(profile2), AccountRef.player(profile2), Currency.MONEY,
                5, TxReason.BANK_WITHDRAW), active, owner, s, NOW), "personal bank");
        assertTrue(SanctionRules.blocks(Transaction.transfer(AccountRef.island(7L), AccountRef.player(profile2), Currency.MONEY, 5,
                TxReason.BANK_WITHDRAW), active, owner, s, NOW), "taking money out of an island bank");
        assertTrue(SanctionRules.blocks(Transaction.transfer(AccountRef.island(7L), AccountRef.player(ALEX), Currency.MONEY, 5,
                TxReason.BANK_WITHDRAW).actor(Actor.player(STEVE, "Steve")), active, owner, s, NOW), "acting for someone else");
        // allowed: earning, staff actions, other currencies, other players
        assertFalse(SanctionRules.blocks(Transaction.deposit(AccountRef.player(STEVE), Currency.MONEY, 5, TxReason.SELL),
                active, owner, s, NOW));
        assertFalse(SanctionRules.blocks(Transaction.withdraw(AccountRef.player(STEVE), Currency.MONEY, 5, TxReason.SANCTION_REMOVE),
                active, owner, s, NOW), "removing money as a sanction");
        assertFalse(SanctionRules.blocks(Transaction.withdraw(AccountRef.player(STEVE), Currency.MONEY, 5, TxReason.ADMIN_TAKE),
                active, owner, s, NOW));
        assertFalse(SanctionRules.blocks(Transaction.withdraw(AccountRef.player(STEVE), Currency.CRYSTALS, 5, "enchant"),
                active, owner, s, NOW), "only money is frozen by default");
        assertFalse(SanctionRules.blocks(Transaction.transfer(AccountRef.player(ALEX), AccountRef.player(profile2),
                Currency.MONEY, 5, TxReason.PAY), active, owner, s, NOW), "receiving a payment is fine for a frozen player");
    }

    @Test
    void economySafetyNetForBlockedTrading() {
        SanctionRules.Settings s = SanctionRules.Settings.DEFAULT;
        Function<UUID, Collection<Sanction>> active = of(Map.of(STEVE, List.of(sanction(1, Sanction.Kind.TRADE, null,
                Sanction.State.ACTIVE))));
        Function<UUID, UUID> owner = u -> u;
        for (String reason : List.of(TxReason.PAY, TxReason.TRADE, TxReason.AH_BUY, TxReason.NOTE_WITHDRAW)) {
            assertTrue(SanctionRules.blocks(Transaction.transfer(AccountRef.player(STEVE), AccountRef.player(ALEX), Currency.MONEY,
                    5, reason), active, owner, s, NOW), "from: " + reason);
        }
        assertTrue(SanctionRules.blocks(Transaction.transfer(AccountRef.player(ALEX), AccountRef.player(STEVE), Currency.MONEY,
                5, TxReason.PAY), active, owner, s, NOW), "receiving a payment is trading too");
        assertFalse(SanctionRules.blocks(Transaction.withdraw(AccountRef.player(STEVE), Currency.MONEY, 5, TxReason.SHOP_BUY),
                active, owner, s, NOW), "the shop is not trading");
        assertFalse(SanctionRules.blocks(Transaction.deposit(AccountRef.player(STEVE), Currency.MONEY, 5, TxReason.AH_SALE),
                active, owner, s, NOW), "a sale that finishes is still paid out");
    }

    @Test
    void removeMoneyTakesAtMostTheBalance() {
        assertEquals(500, SanctionRules.takeable(1000, 500, Long.MAX_VALUE));
        assertEquals(300, SanctionRules.takeable(300, 500, Long.MAX_VALUE));
        assertEquals(100, SanctionRules.takeable(300, 500, 100));
        assertEquals(0, SanctionRules.takeable(300, 0, 100), "never negative");
        assertEquals(0, SanctionRules.takeable(300, -50, 100));
        assertEquals(0, SanctionRules.takeable(0, 500, 100));
    }

    @Test
    void restoreAndLiftRules() {
        assertEquals("not-found", SanctionRules.restoreProblem(null));
        assertEquals("not-restorable", SanctionRules.restoreProblem(sanction(1, Sanction.Kind.FREEZE, null, Sanction.State.ACTIVE)));
        assertEquals("already", SanctionRules.restoreProblem(sanction(2, Sanction.Kind.MONEY, null, Sanction.State.RESTORED)));
        assertEquals("not-held", SanctionRules.restoreProblem(sanction(3, Sanction.Kind.ITEM, null, Sanction.State.PENDING)),
                "a pending item (crash between write and take) is never restorable: no duplicate");
        assertNull(SanctionRules.restoreProblem(sanction(4, Sanction.Kind.ITEM, null, Sanction.State.ACTIVE)));
        assertEquals("not-liftable", SanctionRules.liftProblem(sanction(5, Sanction.Kind.MONEY, null, Sanction.State.ACTIVE), NOW));
        assertEquals("not-active", SanctionRules.liftProblem(sanction(6, Sanction.Kind.TRADE, NOW - 1, Sanction.State.ACTIVE), NOW));
        assertNull(SanctionRules.liftProblem(sanction(7, Sanction.Kind.TRADE, null, Sanction.State.ACTIVE), NOW));
        assertNull(SanctionRules.expires(null, NOW));
        assertEquals(NOW + Duration.ofDays(1).toMillis(), SanctionRules.expires(Duration.ofDays(1), NOW));
    }

    @Test
    void activeOfAKindIsTheLongest() {
        Sanction shortOne = sanction(1, Sanction.Kind.FREEZE, NOW + 10, Sanction.State.ACTIVE);
        Sanction perm = sanction(2, Sanction.Kind.FREEZE, null, Sanction.State.ACTIVE);
        assertEquals(2, SanctionRules.active(List.of(shortOne, perm), Sanction.Kind.FREEZE, NOW).orElseThrow().id());
        assertTrue(SanctionRules.active(List.of(shortOne), Sanction.Kind.TRADE, NOW).isEmpty());
    }

    @Test
    void sanctionApprovalRules() {
        assertEquals("sanction money", Approval.Rule.parse("sanction money").text());
        assertEquals("sanction *", Approval.Rule.parse("Sanction *").text());
        assertThrows(IllegalArgumentException.class, () -> Approval.Rule.parse("sanction gold"));
        assertThrows(IllegalArgumentException.class, () -> Approval.Rule.parse("sanction"));
        Approval.Settings d = Approval.Settings.DEFAULT;
        assertTrue(Approval.sanctionNeedsApproval(d, Sanction.Kind.MONEY));
        assertTrue(Approval.sanctionNeedsApproval(d, Sanction.Kind.ITEM));
        assertFalse(Approval.sanctionNeedsApproval(d, Sanction.Kind.FREEZE));
        assertFalse(Approval.sanctionNeedsApproval(Approval.Settings.DISABLED, Sanction.Kind.MONEY));
        Approval.Settings all = new Approval.Settings(true, Duration.ofHours(1), false, List.of(Approval.Rule.parse("sanction *")), List.of());
        assertTrue(Approval.sanctionNeedsApproval(all, Sanction.Kind.TRADE));
        assertFalse(Approval.Rule.parse("sanction *").matches(Punishment.Type.BAN, false, null), "never matches a punishment");
        assertNull(d.matching(Punishment.Type.WARN, false, null));
    }

    // ================================================================== config

    @Test
    void shippedConfigHasTheP7Defaults() throws Exception {
        for (Path file : List.of(BUNDLED, TRACKED)) {
            PunishConfigParser.Result r = PunishConfigParser.parse(Files.readString(file), TestData.ICONS);
            assertTrue(r.ok(), file + ": " + r.errors());
            SanctionRules.Settings s = r.config().extras().sanctions();
            SanctionRules.Settings d = SanctionRules.Settings.DEFAULT;
            assertEquals(d.durations(), s.durations());
            assertEquals(d.reasons(), s.reasons());
            assertEquals(d.freezeBlocks(), s.freezeBlocks());
            assertEquals(d.tradeBlocks(), s.tradeBlocks());
            assertEquals(d.freezeCurrencies(), s.freezeCurrencies());
            assertEquals(d.freezeExempt(), s.freezeExempt());
            assertEquals(d.freezeReceive(), s.freezeReceive());
            assertEquals(d.tradeReasons(), s.tradeReasons());
            assertEquals(d.moneyMax(), s.moneyMax());
            assertEquals("exploiting", s.dupeReason());
            Templates t = new Templates();
            t.use(r.config());
            assertTrue(t.template(s.dupeReason()).isPresent(), "the dupe suggestion reason exists");
            Templates.Template coop = t.template("coop-griefing").orElseThrow();
            assertEquals(List.of("warn", "island-ban 1d", "coop-block 1w", "ban 1w"),
                    coop.ladder().stream().map(Templates.Step::describe).toList());
        }
        assertEquals(Files.readString(BUNDLED), Files.readString(TRACKED), "bundled default and tracked file are identical");
    }

    @Test
    void badP7KeysAreRejectedWithALine() throws Exception {
        String base = Files.readString(BUNDLED);
        PunishConfigParser.Result r = PunishConfigParser.parse(base.replace("freeze-blocks: [pay, note,", "freeze-blocks: [payy, note,"),
                TestData.ICONS);
        assertFalse(r.ok());
        ConfigProblem p = r.errors().getFirst();
        assertEquals("config.unknown-key", p.key());
        assertEquals("pay", p.arg("suggestion"));
        assertTrue(p.line() > 0);
        assertFalse(PunishConfigParser.parse(base.replace("durations: [1d, 3d, 7d, 30d, perm]", "durations: [1d, soon]"),
                TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("freeze-currencies: [money]", "freeze-currencies: [gold]"),
                TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("money-max: 1000000000000", "money-max: -5"), TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("\"coop-block 7d\", \"ban 7d\"]", "\"coop-blok 7d\", \"ban 7d\"]"),
                TestData.ICONS).ok(), "a typo in an island step");
        assertFalse(PunishConfigParser.parse(base.replace("\"sanction money\"", "\"sanction gold\""), TestData.ICONS).ok());
        PunishConfigParser.Result perm = PunishConfigParser.parse(base.replace("durations: [1d, 3d, 7d, 30d, perm]",
                "durations: [perm]"), TestData.ICONS);
        assertTrue(perm.ok());
        assertNull(perm.config().extras().sanctions().durations().getFirst(), "perm = until lifted");
    }

    @Test
    void islandStepsAreNeverWarnRuleActions() throws Exception {
        String base = Files.readString(BUNDLED);
        assertFalse(PunishConfigParser.parse(base.replace("repeat-action: \"mute 1d\"", "repeat-action: \"coop-block 1d\""),
                TestData.ICONS).ok());
    }

    @Test
    void everyP7TextExists() {
        for (Path file : List.of(Path.of("../../plugin/src/main/resources/messages/moderation.yml"),
                Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml"))) {
            YamlConfiguration msg = YamlConfiguration.loadConfiguration(file.toFile());
            List<String> keys = new ArrayList<>(List.of("island-punish.kicked", "island-punish.banned", "island-punish.coop-blocked",
                    "island-steps.replaced", "island-steps.apply-failed", "undo.player-island", "stats.sanctions",
                    "sanctions.usage", "sanctions.bad-id", "sanctions.until-lifted", "sanctions.blocked.freeze",
                    "sanctions.blocked.trade", "sanctions.blocked.other", "sanctions.listings-cancelled", "sanctions.unavailable",
                    "sanctions.no-permission", "sanctions.needs-approval", "sanctions.already", "sanctions.money-nothing",
                    "sanctions.money-failed", "sanctions.confiscate-offline", "sanctions.confiscate-moved",
                    "sanctions.dupe-source", "sanctions.dupe-details", "sanctions.history.header", "sanctions.history.line",
                    "sanctions.menu.title", "sanctions.menu.head.name", "sanctions.menu.entry.name", "sanctions.menu.entry-lift.name",
                    "sanctions.menu.entry-restore.name", "sanctions.menu.length.name", "sanctions.menu.reason.name",
                    "sanctions.money.picker", "sanctions.confiscate-menu.title-inventory", "sanctions.confiscate-menu.title-ender",
                    "sanctions.confiscate-menu.show-ender.name", "sanctions.confiscate-menu.show-inventory.name",
                    "punish.buttons.sanctions-ready.name", "punish.buttons.sanctions-locked.name", "reasons.coop-griefing",
                    "sanctions.lift-reason", "sanctions.restore-reason", "sanctions.restore-word"));
            for (String s : List.of("freeze", "trade", "money", "confiscated", "lifted", "ended", "money-restored", "item-restored")) {
                keys.add("sanctions.you." + s);
            }
            for (String s : List.of("started", "lifted", "money", "confiscated", "restored")) {
                keys.add("sanctions.notify." + s);
            }
            for (String s : List.of("not-found", "not-restorable", "already", "not-held", "offline", "profile", "full")) {
                keys.add("sanctions.restore-" + s);
            }
            for (String s : List.of("not-found", "not-liftable", "not-active")) {
                keys.add("sanctions.lift-" + s);
            }
            for (Sanction.Kind k : Sanction.Kind.values()) {
                keys.add("sanctions.kinds." + k.id());
                for (String suffix : List.of("", "-locked", "-unavailable")) {
                    keys.add("sanctions.menu." + k.id() + suffix + ".name");
                }
            }
            keys.add("sanctions.menu.freeze-on.name");
            keys.add("sanctions.menu.trade-on.name");
            for (Sanction.State st : Sanction.State.values()) {
                keys.add("sanctions.states." + st.name().toLowerCase(java.util.Locale.ROOT));
            }
            for (Use u : Use.values()) {
                keys.add("sanctions.uses." + u.id());
            }
            for (String r : SanctionRules.Settings.DEFAULT.reasons()) {
                keys.add("sanctions.reasons." + r);
            }
            for (IslandSteps.Why w : IslandSteps.Why.values()) {
                keys.add("island-steps.why." + w.name().toLowerCase(java.util.Locale.ROOT));
            }
            for (Punishment.Type type : Punishment.Type.values()) {
                keys.add("types." + type.key());
                keys.add("discord.types." + type.key());
                if (type.island()) {
                    keys.add("notify." + type.key());
                    keys.add("steps." + new Templates.Step(type, null, false).kindId());
                }
            }
            for (String key : keys) {
                assertNotNull(msg.get(key), file + " misses " + key);
            }
        }
    }
}
