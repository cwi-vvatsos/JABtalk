package com.jabstone.jabtalk.basic.activity;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.storage.DataStore;
import com.jabstone.jabtalk.basic.storage.Ideogram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class StatsHelper {

    private StatsHelper() {}

    public static class Row {
        public final String label;
        public final int count;
        public Row(String label, int count) { this.label = label; this.count = count; }
    }

    // Words — aggregated across all months (or a specific month if provided).
    public static List<Row> topWords(int limit, String monthOrNull) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Ideogram> e : JTApp.getDataStore().getIdeogramMap().entrySet()) {
            Ideogram g = e.getValue();
            if (g == null || g.getType() != Ideogram.Type.Word) continue;
            int count;
            if (monthOrNull == null) {
                count = 0;
                for (Integer v : g.getPlaysByMonth().values()) count += v;
            } else {
                count = g.getPlayCount(monthOrNull);
            }
            if (count > 0) {
                rows.add(new Row(g.getLabel(), count));
            }
        }
        sortDesc(rows);
        return limit > 0 && rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    public static List<Row> topSentences(int limit) {
        return topFromMap(JTApp.getDataStore().getSentencePhrases(), limit);
    }

    // Combines sentence-mode bigrams and freehand-mode bigrams under a single ranking.
    public static List<Row> topBigrams(int limit) {
        HashMap<String, Integer> merged = new HashMap<>(JTApp.getDataStore().getSentenceBigrams());
        for (Map.Entry<String, Integer> e : JTApp.getDataStore().getFreehandBigrams().entrySet()) {
            Integer existing = merged.get(e.getKey());
            merged.put(e.getKey(), (existing == null ? 0 : existing) + e.getValue());
        }
        return topFromMap(merged, limit);
    }

    private static List<Row> topFromMap(Map<String, Integer> map, int limit) {
        List<Row> rows = new ArrayList<>();
        DataStore ds = JTApp.getDataStore();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            String label = labelForIdKey(ds, e.getKey());
            rows.add(new Row(label, e.getValue()));
        }
        sortDesc(rows);
        return limit > 0 && rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    private static String labelForIdKey(DataStore ds, String key) {
        if (key == null || key.isEmpty()) return "";
        String[] parts = key.split("\\|");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(" → ");   // " → "
            Ideogram g = ds.getIdeogram(parts[i]);
            sb.append(g != null ? g.getLabel() : "?");
        }
        return sb.toString();
    }

    private static void sortDesc(List<Row> rows) {
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return Integer.compare(b.count, a.count);
            }
        });
    }

    // All month keys used across the data set, sorted newest-first.
    public static List<String> knownMonths() {
        TreeSet<String> months = new TreeSet<>(Collections.reverseOrder());
        for (Ideogram g : JTApp.getDataStore().getIdeogramMap().values()) {
            months.addAll(g.getPlaysByMonth().keySet());
        }
        return new ArrayList<>(months);
    }
}
