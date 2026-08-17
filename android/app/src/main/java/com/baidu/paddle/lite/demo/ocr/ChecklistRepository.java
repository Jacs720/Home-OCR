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
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChecklistRepository {
    private static final String ASSET_NAME = "checklist_catalog.csv";
    private static final String PREFERENCES = "shiny_checklist";
    private static final String OWNED_IDS = "owned_ids";
    private static final String AUTO_SYNC_LOCAL = "auto_sync_local";

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
        if (rows.size() < 2) throw new IOException("Checklist catalog is empty.");
        SpeciesCatalog speciesCatalog = new SpeciesCatalog(context);

        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() < 6) continue;
            int number = parseNumber(row.get(1));
            ChecklistMark mark = ChecklistMark.fromCode(row.get(4));
            if (number <= 0 || mark == null) continue;
            ChecklistEntry entry = new ChecklistEntry(
                    row.get(0),
                    number,
                    speciesCatalog.nameFor(number),
                    row.get(3),
                    mark,
                    truthy(row.get(5)));
            entries.add(entry);
            entriesById.put(entry.id, entry);
        }
        if (entries.isEmpty()) throw new IOException("Checklist catalog has no valid targets.");

        Set<String> saved = preferences.getStringSet(OWNED_IDS, Collections.emptySet());
        ownedIds.addAll(saved == null ? Collections.emptySet() : saved);
        ownedIds.retainAll(entriesById.keySet());
        for (ChecklistEntry entry : entries) entry.owned = ownedIds.contains(entry.id);
        persist();
        matcher = new ChecklistMatcher(entries);
    }

    public synchronized List<ChecklistEntry> entries() {
        return new ArrayList<>(entries);
    }

    public synchronized int countOwned(ChecklistMark mark, Boolean shiny) {
        int count = 0;
        for (ChecklistEntry entry : entries) {
            if (matches(entry, mark, shiny) && entry.owned) count++;
        }
        return count;
    }

    public synchronized int countTotal(ChecklistMark mark, Boolean shiny) {
        int count = 0;
        for (ChecklistEntry entry : entries) {
            if (matches(entry, mark, shiny)) count++;
        }
        return count;
    }

    public synchronized int countOwnedAll() {
        int count = 0;
        for (ChecklistEntry entry : entries) if (entry.owned) count++;
        return count;
    }

    public synchronized boolean shouldAutoSyncLocal() {
        return preferences.getBoolean(AUTO_SYNC_LOCAL, true);
    }

    public synchronized void setAutoSyncLocal(boolean enabled) {
        preferences.edit().putBoolean(AUTO_SYNC_LOCAL, enabled).apply();
    }

    public synchronized void setOwned(String id, boolean owned) {
        ChecklistEntry entry = entriesById.get(id);
        if (entry == null) return;
        entry.owned = owned;
        if (owned) ownedIds.add(id);
        else ownedIds.remove(id);
        persist();
    }

    public synchronized void clearProgress() {
        ownedIds.clear();
        for (ChecklistEntry entry : entries) entry.owned = false;
        preferences.edit()
                .putStringSet(OWNED_IDS, new HashSet<>())
                .putBoolean(AUTO_SYNC_LOCAL, false)
                .apply();
    }

    public synchronized ImportResult importCsv(String csv) {
        ImportResult result = new ImportResult();
        if (csv == null || csv.trim().isEmpty() || matcher == null) return result;
        List<List<String>> rows = ChecklistCsv.parse(csv);
        if (rows.isEmpty()) return result;

        Map<String, Integer> headers = headers(rows.get(0));
        int numberIndex = findHeader(headers,
                "no", "numero", "national number", "nationalnumber", "nr", "n",
                "番号", "번호", "编号", "編號");
        int formIndex = findHeader(headers,
                "forma", "form", "forme", "フォルム", "폼", "形态", "形態");
        int originIndex = findHeader(headers,
                "marca de origen", "origin mark", "originmark", "marque d origine",
                "marchio origine", "herkunftssymbol", "出身マーク", "출신 마크",
                "来源标记", "來源標記");
        int shinyIndex = findHeader(headers,
                "shiny", "variocolor", "chromatique", "schillernd", "色違い",
                "이로치", "异色", "異色");
        if (numberIndex < 0) {
            result.invalidFormat = true;
            return result;
        }

        boolean changed = false;
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            result.rowsRead++;
            int number = parseNumber(value(row, numberIndex));
            if (number <= 0) {
                result.unmatched++;
                continue;
            }

            boolean shiny = shinyIndex >= 0 && truthy(value(row, shinyIndex));
            if (shiny) result.shinyRows++;
            else result.normalRows++;
            ChecklistMatcher.MatchResult match = matcher.match(
                    number,
                    value(row, formIndex),
                    value(row, originIndex),
                    shiny);
            result.ambiguous += match.ambiguous;
            if (match.entries.isEmpty()) {
                result.unmatched++;
                continue;
            }

            result.matchedRows++;
            for (ChecklistEntry entry : match.entries) {
                if (ownedIds.add(entry.id)) {
                    entry.owned = true;
                    result.newTargets++;
                    if (entry.shiny) result.newShinyTargets++;
                    else result.newNormalTargets++;
                    changed = true;
                } else {
                    result.alreadyOwned++;
                }
            }
        }
        if (changed) persist();
        return result;
    }

    private static boolean matches(ChecklistEntry entry, ChecklistMark mark, Boolean shiny) {
        return (mark == null || entry.mark == mark)
                && (shiny == null || entry.shiny == shiny);
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
                || normalized.equals("oui") || normalized.equals("ja")
                || normalized.equals("はい") || normalized.equals("예")
                || normalized.equals("네") || normalized.equals("是")
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
        public int normalRows;
        public int shinyRows;
        public int matchedRows;
        public int newTargets;
        public int newNormalTargets;
        public int newShinyTargets;
        public int alreadyOwned;
        public int unmatched;
        public int ambiguous;
        public boolean invalidFormat;
    }
}
