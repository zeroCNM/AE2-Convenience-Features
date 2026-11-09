package com.ae2conveniencefeatures.wireless;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Standalone regression checks: runs on a plain JDK without starting Minecraft. */
public final class InventoryViewCacheTest {

    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    private static Map<String, InventoryViewCache.Stock> stock(String key, long count, long time) {
        Map<String, InventoryViewCache.Stock> result = new HashMap<String, InventoryViewCache.Stock>();
        result.put(key, new InventoryViewCache.Stock(count, false, time));
        return result;
    }

    private static void displayAndRefreshChecks() {
        InventoryViewCache<String> cache = new InventoryViewCache<String>();
        LinkedHashSet<String> items = new LinkedHashSet<String>(Arrays.asList("stone", "glass"));
        cache.context("screen-A", items);
        long request = cache.begin(0);
        cache.accept(request, 7, 1, stock("stone", 12, 0));
        check(cache.peek("stone", 600_000_000L) == null, "display retention does not extend action TTL");
        check(cache.peekForDisplay("stone", 600_000_000L).count == 12, "refresh latency does not blank the number");
        cache.inventoryChanged(7, 2);
        check(cache.peek("stone", 100) == null, "dirty stock is unavailable to middle-click decisions");
        check(cache.peekForDisplay("stone", 100).count == 12, "change notification preserves presentation");
        request = cache.begin(100);
        check(!cache.accept(request, 7, 1, stock("stone", 999, 100)), "old reply cannot restore invalidated stock");
        check(cache.peekForDisplay("stone", 100).count == 12, "rejected reply cannot overwrite displayed stock");
        request = cache.begin(200);
        cache.accept(request, 7, 2, stock("stone", 8, 200));
        check(cache.peekForDisplay("stone", 200).count == 8, "fresh number replaces old number directly");
        request = cache.begin(300);
        cache.accept(request, 7, 2, stock("glass", 3, 300));
        request = cache.begin(400);
        cache.accept(request, 7, 3, stock("stone", 9, 400));
        check(cache.peek("glass", 400) == null, "new revision invalidates other batches for actions");
        check(cache.peekForDisplay("glass", 400).count == 3, "new revision does not blink other batches");
        request = cache.begin(500);
        cache.refresh();
        check(cache.peek("stone", 500) == null, "local take request invalidates decision cache");
        check(cache.peekForDisplay("stone", 500).count == 9, "local take request retains number during refresh");
        check(!cache.accept(request, 7, 3, stock("stone", 999, 500)), "local refresh rejects old in-flight reply");
        request = cache.begin(600);
        boolean switched = cache.context("screen-A", new LinkedHashSet<String>(Arrays.asList("stone", "iron")));
        check(!switched, "visible-set update preserves terminal metadata");
        check(cache.peekForDisplay("stone", 600).count == 9, "unchanged visible item does not blink on list update");
        check(cache.peekForDisplay("glass", 600) == null, "removed visible item is discarded");
        check(!cache.accept(request, 7, 3, stock("iron", 999, 600)), "old visible-set batch still rejected");
        cache.context("screen-B", items);
        check(cache.peekForDisplay("stone", 700) == null, "world or terminal switch clears presentation too");
        request = cache.begin(800);
        cache.accept(request, 9, 1, stock("stone", 5, 800));
        request = cache.begin(900);
        cache.accept(request, 10, 1, stock("glass", 7, 900));
        check(cache.peekForDisplay("stone", 900) == null, "server grid switch cannot show previous network stock");
        cache.invalidate(0);
        check(cache.peekForDisplay("glass", 900) == null, "access rejection removes retained presentation");
        request = cache.begin(1_000);
        cache.accept(request, 10, 1, stock("stone", 6, 1_000));
        check(
            cache.peekForDisplay("stone", 1_000 + InventoryViewCache.DISPLAY_HOLD_NANOS - 1).count == 6,
            "presentation survives temporary request loss");
        check(
            cache.peekForDisplay("stone", 1_000 + InventoryViewCache.DISPLAY_HOLD_NANOS) == null,
            "prolonged connection failure cannot show an old number forever");

        InventoryViewCache<String> batches = new InventoryViewCache<String>();
        LinkedHashSet<String> largeView = new LinkedHashSet<String>(Arrays.asList("a", "b", "c", "d", "e"));
        batches.context("busy-grid", largeView);
        for (int round = 1; round <= 3; round++) {
            long now = round * 100_000_000L;
            List<String> candidates = batches.refreshCandidates(now, 2);
            check(candidates.size() <= 2, "batch size remains bounded");
            Map<String, InventoryViewCache.Stock> snapshot = new HashMap<String, InventoryViewCache.Stock>();
            for (String key : candidates) snapshot.put(key, new InventoryViewCache.Stock(round, false, now));
            request = batches.begin(now);
            batches.accept(request, 20, round, snapshot);
            batches.inventoryChanged(20, round + 1);
        }
        boolean allVisible = true;
        for (String key : largeView) allVisible &= batches.peekForDisplay(key, 300_000_001L) != null;
        check(allVisible, "busy grid revisions cannot starve later panels or batches");
        check(
            batches.refreshCandidates(300_000_001L, 2)
                .equals(Arrays.asList("b", "c")),
            "oldest remaining visible items refresh first");
    }

    public static void main(String[] args) {
        InventoryViewCache<String> cache = new InventoryViewCache<String>();
        LinkedHashSet<String> items = new LinkedHashSet<String>(Arrays.asList("stone", "glass"));
        check(cache.context("terminal-A", items), "initial terminal establishes context");
        long request = cache.begin(0);
        check(cache.accept(request, 7, 0, stock("stone", 12, 0)), "matching reply accepted");
        check(cache.peek("stone", 499_999_999L).count == 12, "value valid up to TTL");
        check(cache.peek("stone", 500_000_000L) == null, "value expires exactly at 500 ms");
        check(!cache.context("terminal-A", items), "unchanged view preserves state");
        request = cache.begin(10);
        cache.invalidate(8);
        check(cache.accept(request, 7, 0, stock("stone", 8, 10)), "unrelated grid invalidation ignored");
        request = cache.begin(20);
        cache.invalidate(7);
        check(!cache.accept(request, 7, 0, stock("stone", 999, 20)), "in-flight stale snapshot rejected");
        check(cache.peek("stone", 20) == null, "inventory change clears old count");
        request = cache.begin(30);
        check(cache.accept(request, 7, 0, stock("stone", 2, 30)), "refresh after invalidation accepted");
        long old = cache.begin(40);
        cache.context("terminal-B", items);
        check(!cache.accept(old, 7, 0, stock("stone", 999, 40)), "terminal switch rejects late reply");
        request = cache.begin(50);
        check(cache.accept(request, 9, 0, stock("stone", 1, 50)), "new terminal uses new grid");
        request = cache.begin(60);
        check(cache.accept(request, 9, 0, stock("glass", 3, 60)), "second visible batch accepted");
        check(cache.peek("stone", 60).count == 1 && cache.peek("glass", 60).count == 3, "same-grid batches coexist");
        request = cache.begin(70);
        cache.accept(request, 10, 0, stock("glass", 5, 70));
        check(cache.peek("stone", 70) == null, "server grid switch discards previous grid's entries");
        request = cache.begin(80);
        cache.context("terminal-B", Collections.singleton("stone"));
        check(!cache.accept(request, 10, 0, stock("glass", 1000, 80)), "item-set change rejects old view reply");
        request = cache.begin(100);
        check(cache.pending(3_000_000_099L), "pending request retained before timeout");
        check(!cache.pending(3_000_000_100L), "lost request times out");
        long next = cache.begin(3_000_000_101L);
        check(!cache.accept(request, 10, 0, stock("stone", 99, 100)), "timed-out reply cannot replace newer request");
        check(cache.accept(next, 10, 0, stock("stone", 4, 3_000_000_101L)), "retry succeeds");
        cache.context("revision-terminal", items);
        request = cache.begin(400);
        cache.accept(request, 20, 10, stock("stone", 10, 400));
        request = cache.begin(500);
        cache.inventoryChanged(20, 11);
        check(cache.peek("stone", 500) == null, "change invalidates old stock immediately");
        check(
            cache.accept(request, 20, 12, stock("stone", 12, 500)),
            "fresh in-flight reply survives earlier change notification");
        cache.inventoryChanged(20, 11);
        check(cache.peek("stone", 500).count == 12, "older notification does not discard newer snapshot");
        request = cache.begin(600);
        cache.inventoryChanged(20, 14);
        check(
            !cache.accept(request, 20, 13, stock("stone", 13, 600)),
            "snapshot older than inventory invalidation rejected");
        request = cache.begin(700);
        check(cache.accept(request, 20, 14, stock("stone", 14, 700)), "new enough retry accepted");
        check(
            InventoryViewCache.formatCount(-1)
                .equals("0"),
            "negative count clamped");
        check(
            InventoryViewCache.formatCount(999)
                .equals("999"),
            "unabbreviated small count");
        check(
            InventoryViewCache.formatCount(1000)
                .equals("1.0k"),
            "thousand suffix");
        check(
            InventoryViewCache.formatCount(1200)
                .equals("1.2k"),
            "one decimal digit");
        check(
            InventoryViewCache.formatCount(999950)
                .equals("1.0M"),
            "rounding promotes suffix");
        check(
            InventoryViewCache.formatCount(3400000)
                .equals("3.4M"),
            "million suffix");
        check(
            InventoryViewCache.formatCount(Long.MAX_VALUE)
                .equals("9.2E"),
            "long maximum does not overflow");
        displayAndRefreshChecks();
        System.out.println("PASS: " + checks + " cache/quantity regression checks");
    }
}
