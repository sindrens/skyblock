package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentLogicTest {

    @Test
    void stepsParse() {
        assertEquals(Punishment.Type.WARN, Templates.Step.parse("warn").type());
        Templates.Step mute = Templates.Step.parse("mute 1h");
        assertEquals(Punishment.Type.MUTE, mute.type());
        assertEquals(Duration.ofHours(1), mute.duration());
        assertNull(Templates.Step.parse("ban perm").duration());
        assertTrue(Templates.Step.parse("ipban 30d").ipWide());
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("jail 1h"));
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("ban soon"));
    }

    @Test
    void ladderRepeatsLastStep() {
        Templates.Template t = new Templates.Template("toxicity", "chat", "Toxicity", org.bukkit.Material.PAPER,
                List.of(Templates.Step.parse("warn"), Templates.Step.parse("mute 1h"), Templates.Step.parse("mute perm")));
        assertEquals(Punishment.Type.WARN, t.stepFor(0).type());
        assertEquals(Duration.ofHours(1), t.stepFor(1).duration());
        assertNull(t.stepFor(2).duration());
        assertNull(t.stepFor(9).duration());
    }

    @Test
    void reasonFlags() {
        ModerationModule.Reason r = ModerationModule.Reason.parse("Hacking -s  fly -ip");
        assertEquals("Hacking fly", r.text());
        assertTrue(r.silent());
        assertTrue(r.ipWide());
        assertFalse(ModerationModule.Reason.parse("spam").silent());
        assertEquals("", ModerationModule.Reason.parse("").text());
    }

    @Test
    void statusAndActivity() {
        long now = 1_000_000;
        Punishment temp = punishment(Punishment.Type.BAN, now - 10, now + 1000, null);
        assertTrue(temp.active(now));
        assertEquals(Punishment.Status.EXPIRED, temp.status(now + 2000));
        Punishment removed = punishment(Punishment.Type.MUTE, now - 10, null, now - 5);
        assertFalse(removed.active(now));
        assertEquals(Punishment.Status.REMOVED, removed.status(now));
        Punishment kick = punishment(Punishment.Type.KICK, now - 10, null, null);
        assertFalse(kick.active(now));
        assertEquals(Punishment.Status.DONE, kick.status(now));
    }

    @Test
    void templatesFileValidation(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("punishments.yml");
        Files.writeString(file, """
                categories:
                  chat:
                    name: Chat
                    reasons:
                      spam:
                        name: Spam
                        ladder: ["warn", "mute 30m"]
                      broken:
                        ladder: ["jail 1h"]
                """);
        Templates templates = new Templates();
        List<String> problems = templates.load(file.toFile());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("broken"));
        assertEquals(List.of("spam"), templates.templateIds());
        assertTrue(templates.template("SPAM").isPresent());
    }

    private static Punishment punishment(Punishment.Type type, long created, Long expires, Long removedAt) {
        return new Punishment(1, type, UUID.randomUUID(), "Steve", null, false, "x", null, null, null, "Mod", created, expires,
                false, removedAt, null, removedAt == null ? null : "Admin", removedAt == null ? null : "appeal");
    }
}
