package dev.ahmed.liquidglass.ui.search;

import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.hud.HudManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SearchIndex {
    public record SearchEntry(String id, String title, String category, Runnable onActivate) {}

    private static final SearchIndex INSTANCE = new SearchIndex();
    private final List<SearchEntry> entries = new ArrayList<>();
    private final Map<String, List<SearchEntry>> queryCache = new ConcurrentHashMap<>(64);

    private SearchIndex() {}

    public static SearchIndex get() { return INSTANCE; }

    public void rebuildIndex() {
        entries.clear();
        queryCache.clear();
        for (HudElement el : HudManager.get().elements()) {
            entries.add(new SearchEntry(
                el.id(),
                el.displayName(),
                "HUD",
                () -> el.setEnabled(!el.isEnabled())
            ));
        }
    }

    public List<SearchEntry> search(String rawQuery) {
        if (entries.isEmpty()) rebuildIndex();
        String q = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return Collections.unmodifiableList(entries);

        if (queryCache.size() > 128) queryCache.clear();
        return queryCache.computeIfAbsent(q, key -> {
            List<SearchEntry> matches = new ArrayList<>();
            for (SearchEntry entry : entries) {
                if (entry.title().toLowerCase(Locale.ROOT).contains(key)
                    || entry.category().toLowerCase(Locale.ROOT).contains(key)) {
                    matches.add(entry);
                }
            }
            return Collections.unmodifiableList(matches);
        });
    }
}
