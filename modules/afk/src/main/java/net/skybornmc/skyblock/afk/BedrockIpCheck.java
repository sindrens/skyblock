package net.skybornmc.skyblock.afk;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner R3-3 / brief §3.9 (pure, tested): spots Bedrock players whose IP is the proxy's instead of their own. Then the
 * one-account-per-connection rule would treat every Bedrock player as one connection. A loopback address is always a
 * finding; otherwise {@code threshold} or more Bedrock players online on one IP is ("looks like", since a real household
 * can share an IP too).
 */
final class BedrockIpCheck {

    /** @param ip the shared IP, {@code players} Bedrock players on it, {@code loopback} true for 127.x / ::1 */
    record Finding(String ip, int players, boolean loopback) {
    }

    private BedrockIpCheck() {
    }

    static Optional<Finding> check(Map<UUID, String> bedrockIps, int threshold) {
        Map<String, Integer> counts = new HashMap<>();
        for (String ip : bedrockIps.values()) {
            if (ip == null) {
                continue;
            }
            if (loopback(ip)) {
                return Optional.of(new Finding(ip, (int) bedrockIps.values().stream().filter(ip::equals).count(), true));
            }
            counts.merge(ip, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= Math.max(2, threshold))
                .max(Map.Entry.comparingByValue())
                .map(e -> new Finding(e.getKey(), e.getValue(), false));
    }

    static boolean loopback(String ip) {
        String s = ip.trim().toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("127.") || s.equals("::1") || s.equals("0:0:0:0:0:0:0:1") || s.equals("localhost");
    }
}
