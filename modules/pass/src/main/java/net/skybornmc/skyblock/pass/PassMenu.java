package net.skybornmc.skyblock.pass;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /pass} (PHASE-10 M10.8, GUI-1 framework). Seven levels per page side by side:
 * <ul>
 *   <li>row 1 (10-16): the level line (reached / in progress with a bar / still to go),</li>
 *   <li>row 2 (19-25): the free track (label on 18),</li>
 *   <li>row 3 (28-34): the premium track, cosmetics only (label and premium state on 27).</li>
 * </ul>
 * Slot 4 sums up level, XP and season; 46 is "Claim all"; 52/53 turn the page. Every item name starts with the key fact
 * ("Level 12 · Click to claim", "· Claimed", "· Locked") so Bedrock players see it without the lore. Every action is a
 * plain tap.
 */
final class PassMenu extends Menu {

    static final int PER_PAGE = 7;
    static final int SLOT_SUMMARY = 4;
    static final int SLOT_FREE_LABEL = 18;
    static final int SLOT_PREMIUM_LABEL = 27;
    static final int SLOT_CLAIM_ALL = 46;
    static final int SLOT_STATE = 22;
    static final int ROW_LEVELS = 10;
    static final int ROW_FREE = 19;
    static final int ROW_PREMIUM = 28;

    private final PassModule m;
    private int page = -1; // -1 = pick the page with the next thing to do on the first build

    PassMenu(PassModule module) {
        this.m = module;
    }

    /** Free track tiers shown: all levels, plus overflow steps up to the next one while overflow exists. */
    static int shownTiers(PassMath.Curve curve, PassTable table, long xp) {
        if (!table.hasOverflow() || curve.overflow() == 0) {
            return curve.max();
        }
        int over = Math.max(0, curve.tier(xp) - curve.max());
        return curve.max() + Math.min(PassRules.MAX_OVERFLOW_TIERS, over + 1);
    }

    static int pages(int tiers) {
        return Math.max(1, (tiers + PER_PAGE - 1) / PER_PAGE);
    }

    /** The page to open on: the first claimable tier, else the level being worked on. */
    static int startPage(List<PassRules.TierRef> claimable, int currentTier, int tiers) {
        int target = claimable.isEmpty() ? currentTier + 1 : claimable.getFirst().tier();
        target = Math.max(1, Math.min(target, tiers));
        return (target - 1) / PER_PAGE;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        PassModule.ProfileState s = m.state(viewer);
        int pages = pages(shownTiers(m.curve(), m.table(), s == null ? 0 : s.xp));
        if (page < 0 && s != null) {
            page = startPage(m.claimableTiers(viewer, Integer.MAX_VALUE), m.curve().tier(s.xp), shownTiers(m.curve(), m.table(), s.xp));
        }
        Component suffix = pages > 1 && page >= 0
                ? service.text(viewer, "title.page", Msg.p("page", Math.min(page, pages - 1) + 1), Msg.p("pages", pages)) : null;
        return service.title(viewer, List.of(service.text(viewer, PassModule.ID, "menu.title")), suffix);
    }

    @Override
    public String messageModule() {
        return PassModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "pass";
    }

    @Override
    protected void build() {
        frame();
        if (!m.settings.enabled) {
            set(SLOT_STATE, service.item(Material.GRAY_DYE, viewer, PassModule.ID, "menu.disabled"));
            return;
        }
        PassModule.ProfileState s = m.state(viewer);
        PassModule.AccountState a = m.account(viewer);
        if (s == null || a == null) {
            if (!m.loading(viewer)) {
                m.load(viewer);
            }
            setLoading(SLOT_STATE);
            return;
        }
        PassMath.Curve curve = m.curve();
        PassTable table = m.table();
        boolean premium = m.hasPremium(viewer);
        List<PassRules.TierRef> claimable = m.claimableTiers(viewer, Integer.MAX_VALUE);
        int tiers = shownTiers(curve, table, s.xp);
        int pages = pages(tiers);
        if (page < 0) {
            page = startPage(claimable, curve.tier(s.xp), tiers);
        }
        page = Math.max(0, Math.min(page, pages - 1));

        set(SLOT_SUMMARY, summary(s, premium));
        set(SLOT_FREE_LABEL, service.item(Material.CHEST, viewer, PassModule.ID, "menu.free-label"));
        set(SLOT_PREMIUM_LABEL, service.item(premium ? Material.NETHER_STAR : Material.ENDER_EYE, viewer, PassModule.ID,
                premium ? "menu.premium-label.unlocked" : "menu.premium-label.locked"));
        for (int i = 0; i < PER_PAGE; i++) {
            int tier = page * PER_PAGE + i + 1;
            if (tier > tiers) {
                break;
            }
            set(ROW_LEVELS + i, levelItem(tier, s.xp, curve));
            tierButton(ROW_FREE + i, PassTable.Track.FREE, tier, s.xp, curve, table, s.freeClaimed, premium);
            tierButton(ROW_PREMIUM + i, PassTable.Track.PREMIUM, tier, s.xp, curve, table, a.premiumClaimed, premium);
        }
        int ready = claimable.size();
        if (ready > 0) {
            ItemStack all = service.item(Material.HOPPER, service.text(viewer, PassModule.ID, "menu.claim-all.name", Msg.number("count", ready)),
                    service.lines(viewer, PassModule.ID, "menu.claim-all.lore", Msg.number("count", ready),
                            Msg.number("max", m.settings.maxPerClaim)));
            all.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            all.setAmount(Math.max(1, Math.min(64, ready)));
            set(SLOT_CLAIM_ALL, all, click -> m.claim(click.player(), m.claimableTiers(click.player(), m.settings.maxPerClaim), true));
        } else {
            set(SLOT_CLAIM_ALL, service.item(Material.GRAY_DYE, viewer, PassModule.ID, "menu.claim-all.none"));
        }
        if (page > 0) {
            set(SLOT_PREVIOUS, service.navItem("previous", Material.ARROW, viewer, "frame.previous", Msg.p("page", page),
                    Msg.p("pages", pages)), click -> turn(-1));
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, service.navItem("next", Material.ARROW, viewer, "frame.next", Msg.p("page", page + 2),
                    Msg.p("pages", pages)), click -> turn(1));
        }
    }

    private void turn(int delta) {
        page += delta;
        redraw();
    }

    private ItemStack summary(PassModule.ProfileState s, boolean premium) {
        PassMath.Curve curve = m.curve();
        long[] p = curve.progress(s.xp);
        int level = curve.level(s.xp);
        List<Component> lore = new ArrayList<>(service.lines(viewer, PassModule.ID, "menu.summary.lore",
                Msg.number("level", level), Msg.number("max", curve.max()), Msg.number("xp", s.xp),
                Msg.number("step", p[0]), Msg.number("need", p[1]), Msg.mm("bar", bar(p[0], p[1])), Msg.p("season", m.season())));
        int days = m.daysLeft();
        if (days > 0) {
            lore.addAll(service.lines(viewer, PassModule.ID, "menu.summary.days", Msg.number("days", days)));
        }
        if (m.catchUpActive(s.xp)) {
            lore.addAll(service.lines(viewer, PassModule.ID, "menu.summary.catch-up",
                    Msg.number("percent", Math.round((m.settings.catchUpMultiplier - 1) * 100))));
        }
        lore.addAll(service.lines(viewer, PassModule.ID, premium ? "menu.summary.premium-on" : "menu.summary.premium-off"));
        return service.item(Material.EXPERIENCE_BOTTLE, service.text(viewer, PassModule.ID, "menu.summary.name",
                Msg.number("level", level), Msg.number("max", curve.max())), lore);
    }

    /** Ten dots, the done ones in the first look (messages: bar.filled / bar.empty). */
    private String bar(long have, long need) {
        int filled = need <= 0 ? 10 : (int) Math.min(10, have * 10 / need);
        String on = m.core.messages().raw(PassModule.ID, "bar.filled");
        String off = m.core.messages().raw(PassModule.ID, "bar.empty");
        return (on == null ? "#" : on).repeat(filled) + (off == null ? "-" : off).repeat(10 - filled);
    }

    private ItemStack levelItem(int tier, long xp, PassMath.Curve curve) {
        int reached = curve.tier(xp);
        boolean overflow = tier > curve.max();
        String base = overflow ? "menu.level.overflow-" : "menu.level.";
        int shown = overflow ? tier - curve.max() : tier;
        if (tier <= reached) {
            return service.item(Material.LIME_STAINED_GLASS_PANE, viewer, PassModule.ID, base + "reached", Msg.number("level", shown));
        }
        long need = curve.xpFor(tier) - xp;
        if (tier == reached + 1) {
            long[] p = curve.progress(xp);
            return service.item(Material.YELLOW_STAINED_GLASS_PANE, viewer, PassModule.ID, base + "current", Msg.number("level", shown),
                    Msg.number("step", p[0]), Msg.number("need", p[1]), Msg.number("left", need), Msg.mm("bar", bar(p[0], p[1])));
        }
        return service.item(Material.GRAY_STAINED_GLASS_PANE, viewer, PassModule.ID, base + "later", Msg.number("level", shown),
                Msg.number("left", need));
    }

    private void tierButton(int slot, PassTable.Track track, int tier, long xp, PassMath.Curve curve, PassTable table, Set<Integer> claimed,
                            boolean premium) {
        List<PassTable.Reward> rewards = table.rewards(track, tier, curve.max());
        PassRules.Block block = PassRules.block(track, tier, xp, curve, table, claimed, premium, m::available);
        if (block == PassRules.Block.NOTHING) {
            set(slot, service.filler(Material.BLACK_STAINED_GLASS_PANE));
            return;
        }
        boolean overflow = tier > curve.max();
        int shown = overflow ? tier - curve.max() : tier;
        String state = block == null ? "claim" : switch (block) {
            case CLAIMED -> "claimed";
            case LOCKED -> "locked";
            case NO_PREMIUM -> "premium";
            default -> "unavailable";
        };
        List<Component> lore = new ArrayList<>(service.lines(viewer, PassModule.ID, "menu.tier.rewards"));
        for (PassTable.Reward r : rewards) {
            lore.add(rewardLine(r, overflow));
        }
        lore.add(Component.empty());
        lore.addAll(service.lines(viewer, PassModule.ID, "menu.tier.state." + state, Msg.number("left", Math.max(0, curve.xpFor(tier) - xp))));
        Material icon = block == PassRules.Block.CLAIMED ? Material.LIME_DYE
                : block == PassRules.Block.UNAVAILABLE ? Material.GRAY_DYE : icon(rewards.getFirst());
        String nameKey = (overflow ? "menu.tier.overflow-name." : "menu.tier.name.") + state;
        ItemStack item = service.item(icon, service.text(viewer, PassModule.ID, nameKey, Msg.number("level", shown)), lore);
        if (block == null) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            set(slot, item, click -> m.claim(click.player(), List.of(new PassRules.TierRef(track, tier)), true));
        } else {
            set(slot, item);
        }
    }

    static Material icon(PassTable.Reward r) {
        return switch (r.kind()) {
            case MONEY -> Material.GOLD_NUGGET;
            case CRYSTALS -> Material.PRISMARINE_CRYSTALS;
            case KEY -> Material.TRIPWIRE_HOOK;
            case BOOSTER -> Material.POTION;
            case TITLE -> Material.NAME_TAG;
            case TAG -> Material.PAPER;
        };
    }

    /** One reward as a lore line; money and crystals at the viewer's tier now. */
    private Component rewardLine(PassTable.Reward r, boolean overflow) {
        Player v = viewer;
        int tier = m.tier(v);
        return switch (r.kind()) {
            case MONEY -> m.msg.component(v, "reward.money",
                    Msg.p("amount", m.money(PassMath.money(r.minutes(), tier, m.refPerMinute()))));
            case CRYSTALS -> m.msg.component(v, "reward.crystals",
                    Msg.p("amount", m.crystals(PassMath.crystals(r.amount(), tier, m.settings.crystalGrowth))));
            case KEY -> m.msg.component(v, overflow ? "reward.key-bound" : "reward.key", Msg.number("amount", r.amount()),
                    Msg.p("crate", m.crateName(r.ref())));
            case BOOSTER -> {
                String[] p = r.ref().split(":");
                String type = m.core.messages().raw(PassModule.ID, "boosters." + p[0]);
                yield m.msg.component(v, "reward.booster", Msg.p("type", type == null ? p[0] : type), Msg.p("percent", p[1]),
                        Msg.p("minutes", p[2]));
            }
            case TITLE -> m.msg.component(v, "reward.title", Msg.c("name", m.cosmeticName(v, r)));
            case TAG -> m.msg.component(v, "reward.tag", Msg.c("name", m.cosmeticName(v, r)));
        };
    }
}
