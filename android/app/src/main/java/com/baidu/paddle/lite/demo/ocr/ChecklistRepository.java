package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChecklistRepository {
    private static final String ASSET_NAME = "checklist_catalog.csv";
    private static final String PREFERENCES = "shiny_checklist";
    private static final String OWNED_IDS = "owned_ids";
    private static final String SEEDED_VERSION = "seeded_version";
    private static final int CATALOG_VERSION = 1;

    private final Context context;
    private final SharedPreferences preferences;
    private final List<ChecklistEntry> entries = new ArrayList<>();
    private final Map<String, ChecklistEntry> entriesById = new HashMap<>();
    private final Set<String> ownedIds = new HashSet<>();
    private ChecklistMatcher matcher;

    public ChecklistRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized void load() throws IOException {
        if (!entries.isEmpty()) return;
        String catalog;
        try (InputStream input = context.getAssets().open(ASSET_NAME)) {
            catalog = readUtf8(input);
        }
        List<List<String>> rows = ChecklistCsv.parse(catalog);
        if (rows.size() < 2) throw new IOException("El catálogo del checklist está vacío.");

        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() < 7) continue;
            int number = parseNumber(row.get(1));
            if (number <= 0) continue;
            ChecklistEntry entry = new ChecklistEntry(
                    row.get(0),
                    number,
                    row.get(2),
                    row.get(3),
                    row.get(4),
                    row.get(5),
                    truthy(row.get(6)));
            entries.add(entry);
            entriesById.put(entry.id, entry);
        }
        if (entries.isEmpty()) throw new IOException("No se encontraron objetivos en el catálogo.");

        Set<String> saved = preferences.getStringSet(OWNED_IDS, Collections.emptySet());
        ownedIds.addAll(saved == null ? Collections.emptySet() : saved);
        if (preferences.getInt(SEEDED_VERSION, 0) < CATALOG_VERSION) {
            for (ChecklistEntry entry : entries) {
                if (entry.ownedInitial) ownedIds.add(entry.id);
            }
            preferences.edit()
                    .putStringSet(OWNED_IDS, new HashSet<>(ownedIds))
                    .putInt(SEEDED_VERSION, CATALOG_VERSION)
                    .apply();
        }
        for (ChecklistEntry entry : entries) entry.owned = ownedIds.contains(entry.id);
        matcher = new ChecklistMatcher(entries);
    }

    public synchronized List<ChecklistEntry> entries() {
        return new ArrayList<>(entries);
    }

    public synchronized List<String> targets() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ChecklistEntry entry : entries) values.add(entry.originMark);
        return new ArrayList<>(values);
    }

    public synchronized int countOwned() {
        return ownedIds.size();
    }

    public synchronized int countTotal() {
        return entries.size();
    }

    public synchronized void setOwned(String id, boolean owned) {
        ChecklistEntry entry = entriesById.get(id);
        if (entry == null) return;
        entry.owned = owned;
        if (owned) ownedIds.add(id);
        else ownedIds.remove(id);
        persist();
    }

    public synchronized ImportResult importCsv(String csv) {
        ImportResult result = new ImportResult();
        if (csv == null || csv.trim().isEmpty() || matcher == null) return result;
        List<List<String>> rows = ChecklistCsv.parse(csv);
        if (rows.isEmpty()) return result;

        Map<String, Integer> headers = headers(rows.get(0));
        int numberIndex = findHeader(headers, "no", "numero", "national number", "nationalnumber");
        int formIndex = findHeader(headers, "forma", "form");
        int originIndex = findHeader(headers, "marca de origen", "origin mark", "originmark");
        int shinyIndex = findHeader(headers, "shiny", "variocolor");
        int ballIndex = findHeader(headers, "bola", "ball", "poke ball", "pokeball");
        if (numberIndex < 0 || shinyIndex < 0) {
            result.invalidFormat = true;
            return result;
        }

        boolean changed = false;
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            result.rowsRead++;
            int number = parseNumber(value(row, numberIndex));
            if (number <= 0 || !truthy(value(row, shinyIndex))) {
                result.skippedNonShiny++;
                continue;
            }
            result.shinyRows++;
            ChecklistMatcher.MatchResult matched = matcher.match(
                    number,
                    value(row, formIndex),
                    value(row, originIndex),
                    value(row, ballIndex));
            result.ambiguous += matched.ambiguous;
            if (matched.entries.isEmpty()) {
                result.unmatched++;
                continue;
            }
            result.matchedRows++;
            for (ChecklistEntry entry : matched.entries) {
                if (ownedIds.add(entry.id)) {
                    entry.owned = true;
                    result.newTargets++;
                    changed = true;
                } else {
                    result.alreadyOwned++;
                }
            }
        }
        if (changed) persist();
        return result;
    }

    private void persist() {
        preferences.edit().putStringSet(OWNED_IDS, new HashSet<>(ownedIds)).apply();
    }

    private static Map<String, Integer> headers(List<String> row) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < row.size(); index++) {
            result.put(ChecklistMatcher.normalize(row.get(index)), index);
        }
        return result;
    }

    private static int findHeader(Map<String, Integer> headers, String... names) {
        for (String name : names) {
            Integer index = headers.get(ChecklistMatcher.normalize(name));
            if (index != null) return index;
        }
        return -1;
    }

    private static String value(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    static int parseNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static boolean truthy(String value) {
        String normalized = ChecklistMatcher.normalize(value);
        return normalized.equals("1") || normalized.equals("true")
                || normalized.equals("si") || normalized.equals("yes")
                || normalized.equals("shiny");
    }

    public static String readUtf8(InputStream input) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) text.append(buffer, 0, read);
        }
        return text.toString();
    }

    public static final class ImportResult {
        public int rowsRead;
        public int shinyRows;
        public int matchedRows;
        public int newTargets;
        public int alreadyOwned;
        public int unmatched;
        public int ambiguous;
        public int skippedNonShiny;
        public boolean invalidFormat;

        public String summary() {
            if (invalidFormat) {
                return "CSV no reconocido: hacen falta las columnas No. y Shiny.";
            }
            return String.format(Locale.ROOT,
                    "%d shiny leídos · %d casillas nuevas · %d sin coincidencia · %d ambiguas",
                    shinyRows, newTargets, unmatched, ambiguous);
        }
    }
}
