package com.ae2conveniencefeatures.crafting;

import java.util.ArrayList;
import java.util.List;

/** Reversible display decoration; local history changes never require a native filter or sort. */
public final class RecentCraftView<T> {

    private final List<T> normal = new ArrayList<>();
    private int prefixSlots;

    public int prefixSlots() {
        return prefixSlots;
    }

    public void restore(List<T> view) {
        if (prefixSlots == 0) return;
        view.clear();
        view.addAll(normal);
        normal.clear();
        prefixSlots = 0;
    }

    public void apply(List<T> view, List<T> recent, int columns) {
        restore(view);
        if (recent.isEmpty() || columns <= 0) return;
        normal.addAll(view);
        prefixSlots = RecentCraftHistory.prepend(view, recent, columns);
        if (prefixSlots == 0) normal.clear();
    }
}
