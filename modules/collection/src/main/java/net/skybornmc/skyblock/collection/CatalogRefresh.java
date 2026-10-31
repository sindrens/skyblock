package net.skybornmc.skyblock.collection;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PERF11: the periodic Crates page refresh ({@code collection:catalog}) builds and compares the catalog <b>off the main
 * thread</b>; the main thread only gets a task when the catalog really changed. A {@code version} (moved by every
 * rebuild on the main thread: enable, reload, an applied refresh, disable) makes sure an off-thread result that was
 * built before such a rebuild is dropped instead of overwriting a newer catalog. Pure and thread-safe.
 */
final class CatalogRefresh {

    /** An off-thread build that differs from the catalog shown when it started. */
    record Result(Catalog catalog, String signature, List<String> problems, int version) {
    }

    private final AtomicInteger version = new AtomicInteger();
    private volatile String signature = signature(Catalog.EMPTY);

    /** Main thread: a catalog was built and put in place there (enable, reload); older off-thread results are dropped. */
    void shown(Catalog catalog) {
        signature = signature(catalog);
        version.incrementAndGet();
    }

    /** Main thread (disable): every off-thread result still on its way is dropped. */
    void invalidate() {
        version.incrementAndGet();
    }

    /** Any thread: the version an off-thread build starts from. */
    int version() {
        return version.get();
    }

    /** Any thread: the build result when its catalog differs from the one shown, else {@code null} (nothing to do). */
    Result compare(Catalog fresh, List<String> problems, int startedAt) {
        String sig = signature(fresh);
        return sig.equals(signature) ? null : new Result(fresh, sig, List.copyOf(problems), startedAt);
    }

    /** Main thread: true when the result may replace the shown catalog (nothing rebuilt it meanwhile, still different). */
    boolean accept(Result result) {
        if (result == null || version.get() != result.version() || result.signature().equals(signature)) {
            return false;
        }
        signature = result.signature();
        version.incrementAndGet();
        return true;
    }

    /** Page ids and entry ids in order: two catalogs with the same signature show the same log. */
    static String signature(Catalog c) {
        StringBuilder sb = new StringBuilder();
        for (Catalog.Page p : c.pages()) {
            sb.append(p.id()).append('[');
            p.entries().forEach(e -> sb.append(e.id()).append(e.nameMini()).append(','));
            sb.append(']');
        }
        return sb.toString();
    }
}
