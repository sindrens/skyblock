package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Pick a whole number between {@code min} and {@code max}: −/+ step buttons (default 1/10/100), minimum/maximum
 * shortcuts, a "type a value" chat prompt (accepts 1.5k / 2M) and Confirm. Works with taps only (Bedrock).
 *
 * <pre>
 *  R O B B B B B O R
 *  B · · · · · · · B
 *  B −100 −10 −1 [value] +1 +10 +100 B      19-21 / 22 / 23-25
 *  B · · Min [type] Max · · B               30 / 31 / 32
 *  B · · ✔ [CONFIRM] ✔ · · B                39-41
 *  ← O B ?  ✘  H B O R                      45 back, 48 info, 49 close, 50 help
 * </pre>
 */
public final class NumberPickerMenu extends Menu {

    private static final int[] MINUS_SLOTS = {21, 20, 19};
    private static final int[] PLUS_SLOTS = {23, 24, 25};

    private final Function<Player, Component> title;
    private final long min;
    private final long max;
    private final long[] steps;
    private final BiConsumer<Player, Long> onConfirm;
    private long value;

    /**
     * @param title     chest title ({@code null} = default "Choose an amount")
     * @param initial   starting value (clamped)
     * @param steps     up to 3 step sizes, smallest first (e.g. 1, 10, 100)
     * @param onConfirm runs with the chosen value after the menu closes
     */
    public NumberPickerMenu(Function<Player, Component> title, long min, long max, long initial, long[] steps,
                            BiConsumer<Player, Long> onConfirm) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        this.title = title;
        this.min = min;
        this.max = max;
        this.steps = steps == null || steps.length == 0 ? new long[]{1, 10, 100} : steps;
        this.onConfirm = onConfirm;
        this.value = clamp(initial, min, max);
    }

    public NumberPickerMenu(long min, long max, long initial, BiConsumer<Player, Long> onConfirm) {
        this(null, min, max, initial, null, onConfirm);
    }

    /** Clamps into [min, max] without overflow. */
    static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /** {@code value + delta} clamped, overflow-safe. */
    static long add(long value, long delta, long min, long max) {
        long sum;
        try {
            sum = Math.addExact(value, delta);
        } catch (ArithmeticException e) {
            sum = delta > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return clamp(sum, min, max);
    }

    public long value() {
        return value;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, title != null ? title.apply(viewer) : service.text(viewer, "number.crumb"));
    }

    @Override
    public String infoKey() {
        return "number.info";
    }

    @Override
    protected void build() {
        frame();
        ItemStack inner = service.innerFiller();
        for (int slot = 10; slot <= 43; slot++) {
            int col = slot % 9;
            if (col != 0 && col != 8) {
                set(slot, inner);
            }
        }
        for (int i = 0; i < Math.min(3, steps.length); i++) {
            long step = steps[i];
            set(MINUS_SLOTS[i], stepItem(Material.RED_DYE, "number.minus", step), click -> change(-step));
            set(PLUS_SLOTS[i], stepItem(Material.LIME_DYE, "number.plus", step), click -> change(step));
        }
        set(22, service.item(Material.SUNFLOWER, viewer, "number.value", Msg.p("value", Format.number(value)),
                Msg.p("compact", Format.compact(value)), Msg.p("min", Format.number(min)), Msg.p("max", Format.number(max))));
        set(30, service.item(Material.BUCKET, viewer, "number.set-min", Msg.p("min", Format.number(min))), click -> setTo(min));
        set(31, service.item(Material.NAME_TAG, viewer, "number.custom"), click -> askCustom(click.player()));
        set(32, service.item(Material.WATER_BUCKET, viewer, "number.set-max", Msg.p("max", Format.number(max))), click -> setTo(max));
        ItemStack confirm = service.item(Material.LIME_CONCRETE, viewer, "number.confirm", Msg.p("value", Format.number(value)));
        ItemStack confirmSide = service.item(Material.LIME_STAINED_GLASS_PANE, viewer, "number.confirm", Msg.p("value", Format.number(value)));
        for (int slot = 39; slot <= 41; slot++) {
            set(slot, slot == 40 ? confirm : confirmSide, click -> {
                click.player().closeInventory();
                service.sound(click.player(), "success");
                onConfirm.accept(click.player(), value);
            });
        }
    }

    private void setTo(long target) {
        if (target == value) {
            service.sound(viewer, "error");
            return;
        }
        value = target;
        refresh();
    }

    private ItemStack stepItem(Material material, String key, long step) {
        ItemStack item = service.item(material, viewer, key, Msg.p("step", Format.number(step)));
        item.setAmount((int) Math.max(1, Math.min(64, step)));
        return item;
    }

    private void change(long delta) {
        long next = add(value, delta, min, max);
        if (next == value) {
            service.sound(viewer, "error");
            return;
        }
        value = next;
        refresh();
    }

    private void askCustom(Player player) {
        service.prompt(player, MenuService.MODULE, "number.ask", 32, text -> {
            try {
                long parsed = Format.parseAmount(text);
                if (parsed < min || parsed > max) {
                    throw new IllegalArgumentException("out of range");
                }
                value = parsed;
            } catch (IllegalArgumentException e) {
                service.messages().send(player, MenuService.MODULE, "number.invalid",
                        Msg.p("min", Format.number(min)), Msg.p("max", Format.number(max)));
                service.sound(player, "error");
            }
            service.open(player, this);
        }, () -> service.open(player, this), Msg.p("min", Format.number(min)), Msg.p("max", Format.number(max)));
    }
}
