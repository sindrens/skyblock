package net.skybornmc.skyblock.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where a card's place is, so players already there don't get it ("no /farm card while you are at /farm").
 * One line per card in chat.yml {@code broadcasts.places}: {@code card=world,centerX,halfWidth}, e.g.
 * {@code farm=grind,0,500} (the temporary /farm is around x 0 of world grind, /mine around x 1000). A player in that
 * world whose x is within {@code halfWidth} of {@code centerX} is "at" the place. {@code card=world} alone means the
 * whole world. Pure, unit-tested.
 */
final class BroadcastPlaces {

    /** One parsed line. */
    record Place(String card, String world, Integer centerX, int halfWidth) {

        /** True if a player in {@code world} at block x {@code x} is at this place. */
        boolean contains(String inWorld, double x) {
            if (inWorld == null || !inWorld.equalsIgnoreCase(world)) {
                return false;
            }
            return centerX == null || Math.abs(x - centerX) <= halfWidth;
        }
    }

    /** The parsed lines and the problems (lines that could not be read, skipped). */
    record Parsed(List<Place> places, List<String> problems) {

        /** True if the player is at the place of {@code card}. */
        boolean at(String card, String world, double x) {
            for (Place p : places) {
                if (p.card().equals(card) && p.contains(world, x)) {
                    return true;
                }
            }
            return false;
        }
    }

    private BroadcastPlaces() {
    }

    static Parsed parse(List<String> lines) {
        List<Place> places = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (lines == null) {
            return new Parsed(List.of(), List.of());
        }
        for (String raw : lines) {
            String line = raw == null ? "" : raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                problems.add("'" + line + "' needs card=world,centerX,halfWidth (example: farm=grind,0,500)");
                continue;
            }
            String card = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String[] parts = line.substring(eq + 1).split(",");
            String world = parts[0].strip();
            if (world.isEmpty()) {
                problems.add("'" + line + "': the world is missing");
                continue;
            }
            if (parts.length == 1) {
                places.add(new Place(card, world, null, 0));
                continue;
            }
            if (parts.length != 3) {
                problems.add("'" + line + "' needs card=world,centerX,halfWidth (example: farm=grind,0,500)");
                continue;
            }
            try {
                int center = Integer.parseInt(parts[1].strip());
                int half = Integer.parseInt(parts[2].strip());
                if (half < 0) {
                    problems.add("'" + line + "': halfWidth can't be negative");
                    continue;
                }
                places.add(new Place(card, world, center, half));
            } catch (NumberFormatException e) {
                problems.add("'" + line + "': centerX and halfWidth must be whole numbers");
            }
        }
        return new Parsed(List.copyOf(places), List.copyOf(problems));
    }
}
