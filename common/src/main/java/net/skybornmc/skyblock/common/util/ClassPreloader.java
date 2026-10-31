package net.skybornmc.skyblock.common.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the plugin's own classes on a background thread right after start (PERF6). Without it the <b>first</b> run of a
 * code path after every restart reads its classes from the jar on the main thread (zip read, parse, define, plugin class
 * loader lookups): the first join, the first teleport, the first bank settle, the first menu of a kind. That is why
 * tasks like {@code tutorial-rejoin} and {@code bank:join} warned again after every restart although their own work
 * is a few map lookups.
 *
 * <p>Classes are only <b>loaded</b> ({@code Class.forName(name, false, loader)}), never initialized: no static
 * initializer runs off the main thread, so this is free of side effects. Bukkit's plugin class loader is parallel
 * capable, so the main thread loading the same class at the same moment simply waits for it. A class that can't be
 * loaded (an optional plugin's API is missing) is skipped.</p>
 */
public final class ClassPreloader {

    /** What a run did. */
    public record Result(int loaded, int skipped, long millis) {
    }

    private ClassPreloader() {
    }

    /**
     * The binary class names of the jar entries below {@code packagePath} (e.g. {@code net/skybornmc/skyblock/}), in
     * jar order. Skips directories, resources, {@code module-info}/{@code package-info} and multi-release copies. Pure.
     */
    public static List<String> classNames(Iterable<String> entryNames, String packagePath) {
        List<String> out = new ArrayList<>();
        for (String entry : entryNames) {
            if (entry == null || !entry.endsWith(".class") || !entry.startsWith(packagePath) || entry.startsWith("META-INF/")) {
                continue;
            }
            String simple = entry.substring(entry.lastIndexOf('/') + 1);
            if (simple.equals("module-info.class") || simple.equals("package-info.class")) {
                continue;
            }
            out.add(entry.substring(0, entry.length() - ".class".length()).replace('/', '.'));
        }
        return out;
    }

    /** Reads the entry names of {@code jar} and loads every class below {@code packagePath} with {@code loader}. */
    public static Result preload(File jar, ClassLoader loader, String packagePath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (JarFile file = new JarFile(jar)) {
            for (JarEntry entry : Collections.list(file.entries())) {
                entries.add(entry.getName());
            }
        }
        return load(classNames(entries, packagePath), loader);
    }

    /** Loads (without initializing) every named class; failures are counted, never thrown. */
    public static Result load(List<String> classNames, ClassLoader loader) {
        long start = System.nanoTime();
        int loaded = 0;
        int skipped = 0;
        for (String name : classNames) {
            try {
                Class.forName(name, false, loader);
                loaded++;
            } catch (ClassNotFoundException | LinkageError e) {
                skipped++;
            }
        }
        return new Result(loaded, skipped, (System.nanoTime() - start) / 1_000_000L);
    }
}
