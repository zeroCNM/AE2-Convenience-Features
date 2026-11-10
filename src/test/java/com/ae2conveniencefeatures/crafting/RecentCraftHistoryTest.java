package com.ae2conveniencefeatures.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RecentCraftHistoryTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        RecentCraftHistory<String, String> history = new RecentCraftHistory<>();
        long minute = RecentCraftHistory.LIFETIME_NANOS;
        check(
            history.newestFirst(0)
                .isEmpty(),
            "new terminal has no blank recent rows");
        history.add("a", "A", 1);
        history.add("b", "B", 10);
        check(
            history.newestFirst(20)
                .equals(Arrays.asList("B", "A")),
            "latest completed order is first");
        List<String> cachedOrder = history.newestFirst(20);
        check(history.newestFirst(21) == cachedOrder, "unchanged history reuses its ordered snapshot");
        history.add("a", "A refreshed", 30);
        check(cachedOrder.equals(Arrays.asList("B", "A")), "adding an order does not mutate an older snapshot");
        try {
            cachedOrder.clear();
            throw new AssertionError("history snapshots must be immutable");
        } catch (UnsupportedOperationException expected) {
            check(true, "callers cannot mutate cached history order");
        }
        check(
            history.newestFirst(40)
                .equals(Arrays.asList("A refreshed", "B")),
            "repeat order moves one entry to front");
        check(!history.expire(minute), "rows stay until full minute from first display");
        check(history.expire(minute + 1), "later completions do not extend the first deadline");
        check(
            history.newestFirst(minute + 1)
                .isEmpty(),
            "entire row group expires together");
        history.add("c", "C", minute + 100);
        check(
            history.newestFirst(minute + 101)
                .equals(Collections.singletonList("C")),
            "new completion starts fresh rows after expiry");
        history.clear();
        check(
            history.newestFirst(minute + 102)
                .isEmpty(),
            "closing the terminal consumes displayed history");
        for (int i = 0; i < RecentCraftHistory.MAX_ITEMS + 10; i++) history.add("item" + i, "item" + i, 1000 + i);
        List<String> bounded = history.newestFirst(2000);
        check(bounded.size() == RecentCraftHistory.MAX_ITEMS, "long session history is bounded");
        check(
            bounded.get(0)
                .equals("item265") && !bounded.contains("item0"),
            "bound drops oldest, retains newest");

        List<String> normal = Arrays.asList("stone", "iron", "glass", "gold");
        List<String> view = new ArrayList<>(normal);
        check(
            RecentCraftHistory.prepend(view, Arrays.asList("glass", "iron"), 9) == 9,
            "partial row reserves a whole row");
        check(
            view.get(0)
                .equals("glass")
                && view.get(1)
                    .equals("iron"),
            "completion order overrides ordinary sorting");
        check(
            view.subList(2, 9)
                .equals(Collections.nCopies(7, null)),
            "unused recent positions are empty");
        check(
            view.subList(9, view.size())
                .equals(Arrays.asList("stone", "gold")),
            "normal items keep relative order");
        check(Collections.frequency(view, "iron") == 1, "promoted item is moved, not duplicated");
        check(
            normal.equals(Arrays.asList("stone", "iron", "glass", "gold")),
            "backing native view snapshot is unchanged");
        view = new ArrayList<>(normal);
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) ten.add("crafted" + i);
        check(RecentCraftHistory.prepend(view, ten, 9) == 18, "tenth completion adds a second row");
        check(
            view.get(9)
                .equals("crafted9")
                && view.subList(10, 18)
                    .equals(Collections.nCopies(8, null)),
            "second row pads only after its item");
        check(
            view.subList(18, view.size())
                .equals(normal),
            "unrelated native items are preserved");
        List<String> wide = new ArrayList<>(normal);
        check(RecentCraftHistory.prepend(wide, ten, 12) == 12, "wide terminals use actual column count");
        List<String> empty = new ArrayList<>(normal);
        check(
            RecentCraftHistory.prepend(empty, Collections.emptyList(), 9) == 0 && empty.equals(normal),
            "no recent entries leave no empty row");
        List<String> duplicates = new ArrayList<>(normal);
        check(
            RecentCraftHistory.prepend(duplicates, Arrays.asList("iron", "iron", null), 9) == 9
                && Collections.frequency(duplicates, "iron") == 1,
            "duplicate and null identities cannot occupy additional cells");

        check(RecentCraftHistory.visibleRows(18, 9, 0, 45) == 2, "native pins follow both top recent rows");
        check(
            RecentCraftHistory.visibleRows(18, 9, 1, 45) == 1,
            "scrolling one row keeps remaining recent row above pins");
        check(
            RecentCraftHistory.visibleRows(18, 9, 2, 45) == 0,
            "pins return to native top after recent rows scroll away");
        check(
            RecentCraftHistory.visibleRows(180, 9, 0, 45) == 5,
            "large history cannot shift pins outside the viewport");
        check(RecentCraftHistory.visibleRows(0, 9, 0, 45) == 0, "expired rows restore native pin positions");

        RecentCraftView<String> decoration = new RecentCraftView<>();
        view = new ArrayList<>(normal);
        decoration.apply(view, Arrays.asList("glass", "iron"), 9);
        check(decoration.prefixSlots() == 9, "view decoration tracks the full prefix");
        decoration.apply(view, Collections.singletonList("gold"), 9);
        check(
            view.get(0)
                .equals("gold") && Collections.frequency(view, "iron") == 1,
            "local completion refresh replaces the prefix instead of decorating it twice");
        check(
            view.subList(9, view.size())
                .equals(Arrays.asList("stone", "iron", "glass")),
            "local refresh preserves native search/sort order without sorting again");
        decoration.restore(view);
        check(view.equals(normal) && !view.contains(null), "native paused-update input never contains our padding");
        decoration.restore(view);
        check(view.equals(normal), "repeated restoration is harmless");
        view.clear();
        view.addAll(Arrays.asList("glass", "stone"));
        decoration.apply(view, Collections.singletonList("iron"), 12);
        check(
            decoration.prefixSlots() == 12 && view.get(0)
                .equals("iron"),
            "updated native search view allows independent recent targets and resized columns");
        decoration.apply(view, Collections.emptyList(), 12);
        check(
            view.equals(Arrays.asList("glass", "stone")) && decoration.prefixSlots() == 0,
            "expiry restores the latest native view, not an older search result");
        decoration.apply(view, Arrays.asList(null, null), 9);
        check(
            view.equals(Arrays.asList("glass", "stone")) && decoration.prefixSlots() == 0,
            "unavailable targets do not retain a snapshot or add a row");
        List<Object> largeNative = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) largeNative.add(new Object());
        List<Object> large = new ArrayList<>(largeNative);
        RecentCraftView<Object> largeDecoration = new RecentCraftView<>();
        largeDecoration.apply(large, largeNative.subList(99900, 100000), 9);
        check(
            largeDecoration.prefixSlots() == 108 && large.get(0) == largeNative.get(99900),
            "large terminal promotes actual references with complete padding");
        largeDecoration.restore(large);
        check(large.equals(largeNative), "100000-item terminal restores all native references and exact order");
        System.out.println("PASS: " + checks + " recent craft lifetime/order/layout regression checks");
    }
}
