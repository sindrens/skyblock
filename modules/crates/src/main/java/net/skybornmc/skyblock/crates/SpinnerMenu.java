package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The B40 crate spinner (3 rows, Bedrock-safe: a normal chest menu). The reward is already decided and saved when this
 * opens; the rewards of the crate scroll through slots 10-16 and slow down until the won one sits in slot 13 under the
 * marker in slot 4 (~3 s, soft tick per step). A click on the row or the marker skips to the end; closing the menu or
 * leaving shows the result at once. Bedrock: at most {@link SpinnerMath#BEDROCK_MAX_UPDATES} menu updates.
 */
final class SpinnerMenu extends Menu {

    static final int SLOT_MARKER = 4;
    static final int FIRST_SLOT = 10;

    private final CratesModule m;
    private final OpenPlan.Plan plan;
    private final OpenPlan.Grant grant;
    private final CrateShow.Reveal reveal = new CrateShow.Reveal();
    /** The strip as reward indexes ({@code -1} = the won reward, shown with what it really gave). */
    private List<Integer> strip;
    private int[] schedule;
    private long elapsed;
    private int shown = -1;
    private String openerName;
    /** Display items of the crate's rewards for this viewer (built once). */
    private final Map<Integer, ItemStack> items = new HashMap<>();

    SpinnerMenu(CratesModule module, OpenPlan.Plan plan) {
        this.m = module;
        this.plan = plan;
        this.grant = plan.grants().getFirst();
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, CratesModule.ID, "menu.title"), m.mini(viewer, m.crateMini(plan.crate())));
    }

    @Override
    public String messageModule() {
        return CratesModule.ID;
    }

    @Override
    public String infoKey() {
        return "spinner.info";
    }

    @Override
    public String helpTopic() {
        return "crates";
    }

    @Override
    public int tickInterval() {
        return 1;
    }

    @Override
    protected void build() {
        frame();
        if (strip == null) {
            openerName = viewer.getName();
            int frames = SpinnerMath.frames(m.bedrock(viewer), m.settings.spinnerFrames, m.settings.spinnerBedrockFrames);
            schedule = SpinnerMath.schedule(frames, m.settings.spinnerTicks);
            strip = strip(plan.crate(), grant, frames);
        }
        if (reveal.done()) {
            showResult();
        } else {
            set(SLOT_MARKER, service.item(Material.HOPPER, viewer, CratesModule.ID, "spinner.marker"), this::skip);
            show(Math.max(0, shown), true);
        }
    }

    /** The strip of reward indexes: decoration by chance with the won reward ({@code -1}) where the spin stops. */
    static List<Integer> strip(CrateCatalog.Crate crate, OpenPlan.Grant grant, int frames) {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < crate.rewards().size(); i++) {
            pool.add(i);
        }
        return SpinnerMath.strip(pool, i -> crate.rewards().get(i).chance(), -1, frames, ThreadLocalRandom.current());
    }

    @Override
    protected void tick() {
        if (reveal.done() || schedule == null) {
            return;
        }
        elapsed++;
        int frame = SpinnerMath.frameAt(schedule, elapsed);
        if (frame != shown) {
            show(frame, false);
        }
        if (frame == schedule.length - 1) {
            finish();
        }
    }

    /** Draws one frame (slots 10-16) and ticks. */
    private void show(int frame, boolean redraw) {
        List<Integer> window = SpinnerMath.window(strip, frame);
        for (int k = 0; k < window.size(); k++) {
            set(FIRST_SLOT + k, item(window.get(k)), this::skip);
        }
        if (!redraw && frame != shown) {
            m.show().play(viewer, m.settings.spinnerTickSound);
        }
        shown = frame;
    }

    private ItemStack item(int index) {
        return items.computeIfAbsent(index, i -> {
            if (i < 0) {
                return m.show().grantItem(viewer, plan.crate(), grant);
            }
            CrateCatalog.Reward r = plan.crate().rewards().get(i);
            String name = m.rewardMini(plan.crate(), r, m.previewAmount(r, m.tier(viewer)));
            return service.item(new ItemStack(m.icon(r)), m.mini(viewer, name), List.of());
        });
    }

    private void skip(MenuClick click) {
        if (!reveal.done()) {
            show(schedule.length - 1, false);
            finish();
        }
    }

    /** The spin is over (or skipped): the won reward stays in slot 13, the rest dims; the result message comes now. */
    private void finish() {
        if (!reveal.claim()) {
            return;
        }
        showResult();
        m.show().revealed(viewer, openerName, plan);
    }

    private void showResult() {
        ItemStack dim = service.item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int k = 0; k < SpinnerMath.WINDOW; k++) {
            if (k != SpinnerMath.CENTER) {
                set(FIRST_SLOT + k, dim);
            }
        }
        set(FIRST_SLOT + SpinnerMath.CENTER, item(-1));
        set(SLOT_MARKER, service.item(Material.NETHER_STAR, viewer, CratesModule.ID, grant.top() ? "spinner.won-top" : "spinner.won",
                Msg.c("reward", m.opener().grantName(viewer, plan.crate(), grant))));
    }

    @Override
    protected void onClose() {
        // Closed (or left the server) before the end: the reward is already given; show the result now.
        if (reveal.claim()) {
            Player p = viewer;
            m.show().revealed(p, openerName == null ? p.getName() : openerName, plan);
        } else {
            m.show().ended(viewer.getUniqueId());
        }
    }
}
