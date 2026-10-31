package net.skybornmc.skyblock.pack;

import net.kyori.adventure.resource.ResourcePackStatus;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.pack.PackService.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackLogicTest {

    @Test
    void selfHostedUrlIsHostPortFile() {
        assertEquals("http://play.skybornmc.net:25580/phoenix-java-1.0.0-bca1f7f3.zip",
                PackUrls.url("", "play.skybornmc.net", 25580, "phoenix-java-1.0.0-bca1f7f3.zip").toString());
        assertEquals("http://1.2.3.4:1/a.zip", PackUrls.url(null, " 1.2.3.4 ", 1, "a.zip").toString());
    }

    @Test
    void configuredUrlWinsAndMustBeHttp() {
        assertEquals("https://cdn.example.com/p.zip", PackUrls.url(" https://cdn.example.com/p.zip ", "x", 1, "a.zip").toString());
        assertThrows(IllegalArgumentException.class, () -> PackUrls.url("ftp://x/p.zip", "x", 1, "a.zip"));
        assertThrows(IllegalArgumentException.class, () -> PackUrls.url("not a url", "x", 1, "a.zip"));
        assertThrows(IllegalArgumentException.class, () -> PackUrls.url("", " ", 1, "a.zip"));
    }

    @Test
    void packIdIsStablePerVariant() {
        UUID safe = PackUrls.packId("safe");
        assertEquals(safe, PackUrls.packId("SAFE"));
        assertNotEquals(safe, PackUrls.packId("shine"));
    }

    @Test
    void statusMapping() {
        assertEquals(Optional.of(Status.LOADED), PackStatuses.terminal(ResourcePackStatus.SUCCESSFULLY_LOADED));
        assertEquals(Optional.of(Status.DECLINED), PackStatuses.terminal(ResourcePackStatus.DECLINED));
        for (ResourcePackStatus s : new ResourcePackStatus[]{ResourcePackStatus.FAILED_DOWNLOAD, ResourcePackStatus.INVALID_URL,
                ResourcePackStatus.FAILED_RELOAD, ResourcePackStatus.DISCARDED}) {
            assertEquals(Optional.of(Status.FAILED), PackStatuses.terminal(s), s.name());
        }
        assertTrue(PackStatuses.terminal(ResourcePackStatus.ACCEPTED).isEmpty());
        assertTrue(PackStatuses.terminal(ResourcePackStatus.DOWNLOADED).isEmpty());
        assertTrue(PackStatuses.terminalWhilePlaying(ResourcePackStatus.DISCARDED).isEmpty(), "a replaced copy is no failure");
        assertEquals(Optional.of(Status.LOADED), PackStatuses.terminalByName("SUCCESSFULLY_LOADED"));
        assertTrue(PackStatuses.terminalByName("NOPE").isEmpty());
        assertTrue(PackStatuses.terminalByName(null).isEmpty());
    }

    @Test
    void kickRules() {
        assertEquals(Optional.of("kick.declined"), PackStatuses.kickKey(Status.DECLINED, true, true));
        assertTrue(PackStatuses.kickKey(Status.DECLINED, false, true).isEmpty(), "optional pack: declining is allowed");
        assertEquals(Optional.of("kick.failed"), PackStatuses.kickKey(Status.FAILED, true, true));
        assertTrue(PackStatuses.kickKey(Status.FAILED, true, false).isEmpty());
        assertTrue(PackStatuses.kickKey(Status.LOADED, true, true).isEmpty());
    }

    @Test
    void hostServesOnlyTheFileWithGet() {
        assertEquals(PackHost.Route.OK, PackHost.route("GET", "/a.zip", "a.zip"));
        assertEquals(PackHost.Route.METHOD_NOT_ALLOWED, PackHost.route("POST", "/a.zip", "a.zip"));
        assertEquals(PackHost.Route.NOT_FOUND, PackHost.route("GET", "/b.zip", "a.zip"));
        assertEquals(PackHost.Route.NOT_FOUND, PackHost.route("GET", "/", "a.zip"));
        assertEquals(PackHost.Route.NOT_FOUND, PackHost.route("GET", "/../a.zip", "a.zip"));
        assertEquals(PackHost.Route.NOT_FOUND, PackHost.route("GET", "/a.zip", null));
    }

    @Test
    void extractVerifiesTheHash(@TempDir Path dir) throws IOException {
        byte[] data = "zipdata".getBytes();
        Path target = dir.resolve("pack/p.zip");
        assertArrayEquals(data, PackFiles.extract(new ByteArrayInputStream(data), target, PackFiles.sha1(data).toUpperCase()));
        assertArrayEquals(data, Files.readAllBytes(target));
        Path bad = dir.resolve("pack/q.zip");
        assertThrows(IOException.class, () -> PackFiles.extract(new ByteArrayInputStream(data), bad, "0".repeat(40)));
        assertFalse(Files.exists(bad), "a file with the wrong hash is never written");
    }

    @Test
    void defaultsAndParsing(@TempDir Path dir) throws IOException {
        PackConfig defaults = new PackConfig();
        assertTrue(defaults.enabled);
        assertEquals(PackConfig.Variant.SAFE, defaults.variant);
        assertEquals(25580, defaults.hostPort);
        assertEquals("play.skybornmc.net", defaults.hostPublicHost);
        assertTrue(defaults.required && defaults.kickOnFail);
        assertEquals(60, defaults.timeoutSeconds);

        Files.writeString(dir.resolve("pack.yml"), "config-version: 1\nvariant: shine\nhost:\n  port: 9000\n  public-host: example.org\nrequired: false\ntimeout-seconds: 20\n");
        PackConfig c = new PackConfig();
        assertTrue(new ConfigManager(dir.toFile(), Logger.getLogger("test")).load(c).isEmpty());
        assertEquals(PackConfig.Variant.SHINE, c.variant);
        assertEquals(9000, c.hostPort);
        assertEquals("example.org", c.hostPublicHost);
        assertFalse(c.required);
        assertEquals(20, c.timeoutSeconds);
        assertTrue(c.kickOnFail, "untouched keys keep their default");
    }
}
