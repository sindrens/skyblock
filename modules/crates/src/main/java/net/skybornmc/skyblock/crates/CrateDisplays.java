package net.skybornmc.skyblock.crates;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.skybornmc.skyblock.common.pack.PackIds;
import net.skybornmc.skyblock.common.pack.PackService;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The custom crate models on the pedestals: two item displays per crate (body and lid, PAPER with the pack's custom model
 * data string), the lid swinging on its hinge with display interpolation ({@link CrateHinge}). They exist for every
 * viewer's server but are shown only to viewers with the pack ({@code setVisibleByDefault(false)} +
 * {@code Player#showEntity}); everyone else keeps the vanilla crate block. Never saved, main thread only.
 */
final class CrateDisplays {

    /** One step of an animation: the poses to reach and the ticks the client takes to get there. */
    private record Step(CrateHinge.Pose body, CrateHinge.Pose lid, double angle, int ticks) {
    }

    /** The two displays of one crate. */
    private static final class Rig {
        final Pedestals.Pedestal pedestal;
        final PackIds.Crate ids;
        final ItemDisplay body;
        final ItemDisplay lid;
        final Set<UUID> shown = new HashSet<>();
        double angle;
        Scheduler.Task anim = Scheduler.Task.NONE;

        Rig(Pedestals.Pedestal pedestal, PackIds.Crate ids, ItemDisplay body, ItemDisplay lid) {
            this.pedestal = pedestal;
            this.ids = ids;
            this.body = body;
            this.lid = lid;
        }
    }

    private final CratesModule m;
    private final BiConsumer<Entity, Pedestals.Pedestal> tagger;
    private final Map<String, Rig> rigs = new HashMap<>();

    CrateDisplays(CratesModule module, BiConsumer<Entity, Pedestals.Pedestal> tagger) {
        this.m = module;
        this.tagger = tagger;
    }

    private PackService pack() {
        return m.core.services().optional(PackService.class).orElse(null);
    }

    /** True for one of our displays. */
    boolean owns(Entity e) {
        for (Rig r : rigs.values()) {
            if (r.body == e || r.lid == e) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== lifecycle

    /** (Re)creates the models of a pedestal facing {@code yaw}; nothing without the pack module or a model for the crate. */
    void place(World w, Pedestals.Pedestal p, float yaw) {
        remove(p.crateId());
        PackService pack = pack();
        if (pack == null || !m.settings.displaysEnabled) {
            return;
        }
        PackIds.Crate ids = pack.ids().crates().get(p.crateId());
        if (ids == null || ids.body() == null || ids.lid() == null) {
            return;
        }
        double s = m.settings.displayScale;
        Location at = new Location(w, p.x() + 0.5, p.y() + 0.5 + m.settings.displayYOffset, p.z() + 0.5, yaw, 0f);
        ItemDisplay body = spawn(at, ids.body(), p, CrateHinge.lid(ids.hingeY(), ids.hingeZ(), s, 0));
        ItemDisplay lid = spawn(at, ids.lid(), p, CrateHinge.lid(ids.hingeY(), ids.hingeZ(), s, 0));
        Rig rig = new Rig(p, ids, body, lid);
        rigs.put(p.crateId(), rig);
        for (Player online : Bukkit.getOnlinePlayers()) {
            sync(rig, online);
        }
    }

    private ItemDisplay spawn(Location at, String model, Pedestals.Pedestal p, CrateHinge.Pose pose) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            ItemStack stack = new ItemStack(Material.PAPER);
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(model).build());
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setViewRange((float) m.settings.displayViewRange);
            d.setVisibleByDefault(false);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(1);
            d.setTransformation(transformation(pose));
            if (m.settings.displayBrightness >= 0) {
                int b = Math.min(15, m.settings.displayBrightness);
                d.setBrightness(new Display.Brightness(b, b));
            }
            tagger.accept(d, p);
        });
    }

    private static Transformation transformation(CrateHinge.Pose pose) {
        float s = pose.scale();
        return new Transformation(pose.translation(), pose.rotation(), new Vector3f(s, s, s), new Quaternionf());
    }

    void remove(String crateId) {
        Rig r = rigs.remove(crateId);
        if (r != null) {
            r.anim.cancel();
            if (m.settings.displayHideBlock) {
                for (UUID id : new ArrayList<>(r.shown)) {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) {
                        block(r, pl, false);
                    }
                }
            }
            r.body.remove();
            r.lid.remove();
        }
    }

    void removeAll() {
        new ArrayList<>(rigs.keySet()).forEach(this::remove);
    }

    // ================================================================== who sees them

    /** Shows or hides the models of every crate for one player, by their pack status (cheap; only acts on a change). */
    void sync(Player player) {
        for (Rig r : rigs.values()) {
            sync(r, player);
        }
    }

    void syncAll() {
        if (rigs.isEmpty()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            sync(p);
        }
    }

    private void sync(Rig r, Player player) {
        PackService pack = pack();
        boolean want = pack != null && pack.hasPack(player) && r.body.isValid() && r.lid.isValid();
        boolean has = r.shown.contains(player.getUniqueId());
        if (want && !has) {
            player.showEntity(m.core.plugin(), r.body);
            player.showEntity(m.core.plugin(), r.lid);
            r.shown.add(player.getUniqueId());
            block(r, player, true);
        } else if (!want && has) {
            player.hideEntity(m.core.plugin(), r.body);
            player.hideEntity(m.core.plugin(), r.lid);
            r.shown.remove(player.getUniqueId());
            block(r, player, false);
        }
    }

    /** The player left: forget them (their hidden-entity state is gone with their connection). */
    void quit(Player player) {
        rigs.values().forEach(r -> r.shown.remove(player.getUniqueId()));
    }

    /** The player's client got a chunk: put the invisible block over the crate again (a chunk resend restores the chest). */
    void chunkSent(Player player, int chunkX, int chunkZ) {
        if (!m.settings.displayHideBlock) {
            return;
        }
        for (Rig r : rigs.values()) {
            if (r.pedestal.chunkX() == chunkX && r.pedestal.chunkZ() == chunkZ && r.shown.contains(player.getUniqueId())) {
                block(r, player, true);
            }
        }
    }

    private void block(Rig r, Player player, boolean hide) {
        if (!m.settings.displayHideBlock) {
            return;
        }
        Material as = Material.matchMaterial(m.settings.displayHideBlockAs);
        World w = r.body.getWorld();
        Pedestals.Pedestal p = r.pedestal;
        if (as == null || !as.isBlock() || !player.getWorld().equals(w)) {
            return;
        }
        player.sendBlockChange(new Location(w, p.x(), p.y(), p.z()),
                hide ? as.createBlockData() : w.getBlockAt(p.x(), p.y(), p.z()).getBlockData());
    }

    // ================================================================== lid

    /** Shake and swing the lid open (or just swing it when {@code shake} is false: fast opens). */
    void open(String crateId, boolean shake) {
        Rig r = rigs.get(crateId);
        if (r == null) {
            return;
        }
        CratesSettings c = m.settings;
        double s = c.displayScale;
        Deque<Step> steps = new ArrayDeque<>();
        if (shake && c.lidShakeDegrees > 0) {
            int seg = Math.max(1, c.lidShakeTicks / 3);
            double d = c.lidShakeDegrees;
            for (double roll : new double[] {d, -d, 0}) {
                CrateHinge.Pose p = roll == 0 ? CrateHinge.lid(r.ids.hingeY(), r.ids.hingeZ(), s, 0) : CrateHinge.roll(s, roll);
                steps.add(new Step(p, p, 0, seg));
            }
        }
        addSwing(steps, r, r.angle, c.lidAngle, c.lidOpenTicks, c.lidOvershoot);
        run(r, steps);
        sound(r, c.lidOpenSound);
    }

    /** Swings the lid shut. */
    void close(String crateId) {
        Rig r = rigs.get(crateId);
        if (r == null || r.angle <= 0.01) {
            return;
        }
        Deque<Step> steps = new ArrayDeque<>();
        addSwing(steps, r, r.angle, 0, m.settings.lidCloseTicks, 0);
        run(r, steps);
        sound(r, m.settings.lidCloseSound);
    }

    private void addSwing(Deque<Step> steps, Rig r, double from, double to, int totalTicks, double back) {
        double s = m.settings.displayScale;
        int n = Math.max(1, m.settings.lidSteps);
        int seg = CrateHinge.segmentTicks(totalTicks, n);
        CrateHinge.Pose still = CrateHinge.lid(r.ids.hingeY(), r.ids.hingeZ(), s, 0);
        float[] angles = CrateHinge.path(from, to, n, back);
        for (float a : angles) {
            steps.add(new Step(still, CrateHinge.lid(r.ids.hingeY(), r.ids.hingeZ(), s, a), a, seg));
        }
        // After a swing past open the last frame is exactly the target (path guarantees it).
    }

    private void run(Rig r, Deque<Step> steps) {
        r.anim.cancel();
        next(r, steps);
    }

    private void next(Rig r, Deque<Step> steps) {
        Step st = steps.poll();
        if (st == null || !r.body.isValid() || rigs.get(r.pedestal.crateId()) != r) {
            return;
        }
        apply(r.body, st.body(), st.ticks());
        apply(r.lid, st.lid(), st.ticks());
        r.angle = st.angle();
        r.anim = steps.isEmpty() ? Scheduler.Task.NONE
                : m.core.scheduler().later("crates:lid", st.ticks(), () -> next(r, steps));
    }

    private static void apply(ItemDisplay d, CrateHinge.Pose pose, int ticks) {
        if (d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(transformation(pose));
        }
    }

    private void sound(Rig r, String spec) {
        CrateShow.sound(spec).ifPresent(snd -> r.body.getWorld().playSound(snd, r.pedestal.x() + 0.5, r.pedestal.y() + 0.5,
                r.pedestal.z() + 0.5));
    }
}
