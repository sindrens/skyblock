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
 * Pick a whole number between {@code min} and {@code max}: −/+ step buttons (default 1/10/100), a "type a value"
 * chat prompt (accepts 1.5k / 2M) and Confirm. Works without shift-click (Bedrock).
 *
 * <pre>
 * 19 −100  20 −10  21 −1   22 value   23 +1  24 +10  25 +100
 *                          31 type a value
 *                          40 confirm
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
        return title != null ? title.apply(viewer) : service.text(viewer, "number.title");
    }

    @Override
    public String infoKey() {
        return "number.info";
    }

    @Override
    protected void build() {
        frame();
        for (int i = 0; i < Math.min(3, steps.length); i++) {
            long step = steps[i];
            set(MINUS_SLOTS[i], stepItem(Material.RED_DYE, "number.minus", step), click -> change(-step));
            set(PLUS_SLOTS[i], stepItem(Material.LIME_DYE, "number.plus", step), click -> change(step));
        }
        set(22, service.item(Material.GOLD_NUGGET, viewer, "number.value",
                Msg.p("value", Format.number(value)), Msg.p("min", Format.number(min)), Msg.p("max", Format.number(max))));
        set(31, service.item(Material.NAME_TAG, viewer, "number.custom"), click -> askCustom(click.player()));
        set(40, service.item(Material.LIME_CONCRETE, viewer, "number.confirm", Msg.p("value", Format.number(value))), click -> {
            click.player().closeInventory();
            onConfirm.accept(click.player(), value);
        });
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
