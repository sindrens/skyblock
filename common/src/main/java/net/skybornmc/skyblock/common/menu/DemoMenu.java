package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Staff demo of every framework building block ({@code /skyblock demo-menu}): paged list, number picker, toggle,
 * confirm dialog, player picker, chat text prompt, live-updating slot and async loading slot.
 */
public final class DemoMenu extends PagedMenu<Integer> {

    private static final Material[] SAMPLE_ICONS = {
            Material.GRASS_BLOCK, Material.IRON_PICKAXE, Material.EMERALD, Material.GOLD_INGOT, Material.BARREL,
            Material.TRIPWIRE_HOOK, Material.WRITABLE_BOOK, Material.EXPERIENCE_BOTTLE, Material.ENCHANTING_TABLE,
            Material.BONE, Material.CLOCK, Material.ENDER_PEARL, Material.BOOKSHELF, Material.NAME_TAG};
    private static final List<Integer> SAMPLES = IntStream.rangeClosed(1, 60).boxed().toList();

    private long amount = 100;
    private boolean toggled = true;
    private String typed = "-";

    @Override
    public Component title() {
        return service.text(viewer, "demo.title");
    }

    @Override
    public String infoKey() {
        return "demo.info";
    }

    @Override
    public String helpTopic() {
        return "menus";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected List<Integer> entries() {
        return SAMPLES;
    }

    @Override
    protected MenuButton entry(Integer n) {
        Material icon = SAMPLE_ICONS[(n - 1) % SAMPLE_ICONS.length];
        return MenuButton.of(service.item(icon, viewer, "demo.entry", Msg.p("number", n)),
                click -> service.messages().send(click.player(), MenuService.MODULE, "demo.clicked",
                        Msg.p("number", n), Msg.p("click", click.type().name().toLowerCase())));
    }

    @Override
    protected void decorate() {
        set(1, service.item(Material.PLAYER_HEAD, viewer, "demo.player-picker"), click -> openChild(
                new PlayerPickerMenu(null, p -> true, (who, picked) -> {
                    service.messages().send(who, MenuService.MODULE, "demo.picked", Msg.p("player", picked.getName()));
                    back();
                })));
        set(2, service.item(Material.GOLD_NUGGET, viewer, "demo.number", Msg.p("value", Format.number(amount))), click -> openChild(
                new NumberPickerMenu(null, 1, 1_000_000, amount, new long[]{1, 10, 100}, (who, value) -> {
                    amount = value;
                    service.messages().send(who, MenuService.MODULE, "demo.number-chosen", Msg.p("value", Format.number(value)));
                    service.open(who, this);
                })));
        updateLive();
        set(4, service.toggle(viewer, MenuService.MODULE, "demo.toggle", toggled, value -> toggled = value));
        setAsync(5, CompletableFuture.supplyAsync(
                () -> MenuButton.display(service.item(Material.CHEST, viewer, "demo.async")),
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)));
        set(6, service.item(Material.TNT, viewer, "demo.confirm"), click -> openChild(new ConfirmMenu(null,
                service.item(Material.TNT, viewer, "demo.confirm"),
                v -> service.lines(v, "demo.confirm-details"),
                who -> service.messages().send(who, MenuService.MODULE, "demo.confirmed"),
                who -> service.messages().send(who, MenuService.MODULE, "demo.cancelled"))));
        set(7, service.item(Material.OAK_SIGN, viewer, "demo.text", Msg.p("value", typed)), click ->
                service.prompt(click.player(), MenuService.MODULE, "demo.text-ask", 32, text -> {
                    typed = text;
                    service.open(click.player(), this);
                }, () -> service.open(click.player(), this)));
    }

    @Override
    protected void tick() {
        updateLive();
    }

    private void updateLive() {
        set(3, service.item(Material.CLOCK, viewer, "demo.live",
                Msg.p("time", Format.duration(Duration.ofSeconds(ticksOpen() / 20)))));
    }
}
