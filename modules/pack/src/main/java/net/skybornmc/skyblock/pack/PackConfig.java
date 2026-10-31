package net.skybornmc.skyblock.pack;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.Map;

/** Typed settings of {@code pack.yml} (GUI-P1-PACK 5.1). Kick and staff texts are in {@code messages/pack.yml}. */
public final class PackConfig extends ModuleConfig {

    @Setting(desc = "Send the icon pack to Java players. false = nothing is sent, nobody is kicked, every icon is its plain base item.")
    public boolean enabled = true;

    @Setting(desc = "A Java player who declines the pack (or whose download fails) is kicked with a friendly text. false = offer only, never kick.")
    public boolean required = true;

    @Setting(desc = "Full download URL of the zip when it is hosted elsewhere (its content must equal the bundled pack; the health check verifies it). "
            + "<file> is replaced by the current file name (phoenix-icons-<hash>.zip). Empty = this server hosts it.")
    public String url = "";

    @Setting(path = "host.bind", desc = "Address the built-in pack web server listens on. Empty = all addresses.", restart = true)
    public String hostBind = "";

    @Setting(path = "host.port", desc = "TCP port of the built-in pack web server. Open it in the firewall: ufw allow 25580/tcp.",
            min = 1, max = 65535, restart = true)
    public int hostPort = 25580;

    @Setting(path = "host.public-host", desc = "Host name (or IP) players download the pack from; builds the URL when 'url' is empty.")
    public String hostPublicHost = "play.skybornmc.net";

    @Setting(path = "host.check-url", desc = "URL the self-check downloads. Empty = the URL players use. Set http://127.0.0.1:25580/<file> "
            + "if the server cannot reach its own public address (hairpin NAT); <file> is replaced by the current file name.")
    public String hostCheckUrl = "";

    @Setting(path = "host.check-seconds", desc = "Seconds between two self-checks of the pack host.", min = 10, max = 600)
    public int hostCheckSeconds = 60;

    @Setting(path = "host.check-failures", desc = "Failed self-checks in a row before the host counts as down (then the pack is not required and staff get one alert).",
            min = 1, max = 10)
    public int hostCheckFailures = 2;

    @Setting(desc = "Prompt shown on the client's resource pack screen (MiniMessage).")
    public String prompt = "<gradient:#ff9a3c:#ff3d2e><bold>Phoenix Skyblock icons</bold></gradient> <gray>A tiny pack (about 100 KB): menus get their own icons.";

    @Setting(path = "timeout-seconds", desc = "Seconds to wait for the client's final answer before counting the pack as failed.", min = 5, max = 600)
    public int timeoutSeconds = 60;

    @Setting(path = "join.wait-seconds", desc = "The welcome and the tutorial camera wait at most this long for the pack answer (0 = never wait).",
            min = 0, max = 60)
    public int joinWaitSeconds = 15;

    @Override
    public String fileName() {
        return "pack.yml";
    }

    @Override
    public String header() {
        return "Phoenix icon pack (I7 v3). Reload: /pack reload";
    }

    @Override
    public Map<String, String> removedKeys() {
        return Map.of("variant", "pack v1 (safe/shine) was removed; v2 has one icon pack",
                "kick-on-fail", "v2: a failed download is handled like a decline (see 'required')");
    }
}
