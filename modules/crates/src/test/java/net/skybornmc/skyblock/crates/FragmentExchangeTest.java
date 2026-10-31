package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** REVIEW3 R3-18: 3 fragments → 1 Phoenix key, all or nothing, never a duplicate. */
class FragmentExchangeTest {

    /** A fake inventory + database that records every step. */
    static final class Fake implements FragmentExchange.Steps {
        int inventory;
        int keys;
        int refunded;
        int saves;
        boolean saveOk = true;
        CompletableFuture<Boolean> grantResult;
        int takeLimit = Integer.MAX_VALUE;

        Fake(int inventory) {
            this.inventory = inventory;
        }

        @Override
        public int take(int fragments) {
            int t = Math.min(Math.min(fragments, inventory), takeLimit);
            if (t < fragments && takeLimit == Integer.MAX_VALUE) {
                return 0; // real take is all or nothing
            }
            inventory -= t;
            return t;
        }

        @Override
        public boolean save() {
            saves++;
            return saveOk;
        }

        @Override
        public CompletableFuture<Boolean> grant(int k) {
            if (grantResult != null) {
                return grantResult;
            }
            keys += k;
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public void refund(int fragments) {
            refunded += fragments;
            inventory += fragments;
        }
    }

    private static FragmentExchange.Result run(Fake f, int want) {
        return FragmentExchange.run(f.inventory, 3, want, f).join();
    }

    @Test
    void keysForFragments() {
        assertEquals(0, FragmentExchange.keysFor(2, 3, 64));
        assertEquals(1, FragmentExchange.keysFor(3, 3, 64));
        assertEquals(3, FragmentExchange.keysFor(11, 3, 64));
        assertEquals(2, FragmentExchange.keysFor(11, 3, 2), "capped per click");
        assertEquals(0, FragmentExchange.keysFor(11, 0, 64));
    }

    @Test
    void threeFragmentsMakeOneKey() {
        Fake f = new Fake(7);
        FragmentExchange.Result r = run(f, 1);
        assertEquals(FragmentExchange.Status.DONE, r.status());
        assertEquals(1, r.keys());
        assertEquals(3, r.fragments());
        assertEquals(4, f.inventory);
        assertEquals(1, f.keys);
        assertEquals(1, f.saves, "player file saved before the key is written");
        assertEquals(0, f.refunded);
    }

    @Test
    void combineAllUsesOnlyFullSets() {
        Fake f = new Fake(11);
        FragmentExchange.Result r = run(f, 64);
        assertEquals(3, r.keys());
        assertEquals(2, f.inventory, "the 2 left over stay");
        assertEquals(3, f.keys);
    }

    @Test
    void notEnoughTakesNothing() {
        Fake f = new Fake(2);
        assertEquals(FragmentExchange.Status.NOT_ENOUGH, run(f, 1).status());
        assertEquals(2, f.inventory);
        assertEquals(0, f.keys);
        assertEquals(0, f.saves);
    }

    @Test
    void fragmentsGoneBeforeTheTakeGiveNothing() {
        Fake f = new Fake(3);
        int seen = f.inventory;
        f.inventory = 1; // dropped two between the count and the take
        assertEquals(FragmentExchange.Status.NOT_ENOUGH, FragmentExchange.run(seen, 3, 1, f).join().status());
        assertEquals(1, f.inventory);
        assertEquals(0, f.keys);
    }

    @Test
    void partialTakeIsGivenBack() {
        Fake f = new Fake(6);
        f.takeLimit = 2;
        assertEquals(FragmentExchange.Status.NOT_ENOUGH, run(f, 2).status());
        assertEquals(6, f.inventory, "the 2 taken came back");
        assertEquals(0, f.keys);
    }

    @Test
    void failedSaveWritesNoKeyAndRefunds() {
        Fake f = new Fake(3);
        f.saveOk = false;
        assertEquals(FragmentExchange.Status.FAILED, run(f, 1).status());
        assertEquals(0, f.keys, "no key next to an unsaved removal");
        assertEquals(3, f.inventory);
        assertEquals(3, f.refunded);
    }

    @Test
    void failedTransactionRefunds() {
        Fake f = new Fake(9);
        f.grantResult = CompletableFuture.completedFuture(false);
        assertEquals(FragmentExchange.Status.FAILED, run(f, 64).status());
        assertEquals(9, f.inventory);
        assertEquals(0, f.keys);

        Fake g = new Fake(3);
        g.grantResult = CompletableFuture.failedFuture(new IllegalStateException("db down"));
        assertEquals(FragmentExchange.Status.FAILED, run(g, 1).status());
        assertEquals(3, g.inventory);
        assertEquals(3, g.refunded);
    }

    @Test
    void sumOfFragmentsAndKeysNeverGrows() {
        for (int have = 0; have < 20; have++) {
            for (int want = 1; want < 8; want++) {
                Fake f = new Fake(have);
                run(f, want);
                assertEquals(have, f.inventory + f.keys * 3, "have " + have + " want " + want);
            }
        }
    }
}
