package com.baidu.paddle.lite.demo.ocr;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Matches OCR collection rows to a single mark/form/variant checklist target. */
public final class ChecklistMatcher {
    private final Map<String, List<ChecklistEntry>> entriesByTarget = new HashMap<>();

    public ChecklistMatcher(List<ChecklistEntry> entries) {
        for (ChecklistEntry entry : entries) {
            entriesByTarget.computeIfAbsent(
                    key(entry.nationalNumber, entry.mark, entry.shiny),
                    ignored -> new ArrayList<>()).add(entry);
        }
    }

    public MatchResult match(
            int nationalNumber,
            String form,
            String originMark,
            boolean shiny
    ) {
        ChecklistMark mark = ChecklistMark.fromDetected(originMark);
        if (mark == null) return MatchResult.EMPTY;

        List<ChecklistEntry> candidates = entriesByTarget.getOrDefault(
                key(nationalNumber, mark, shiny), Collections.emptyList());
        if (candidates.isEmpty()) return MatchResult.EMPTY;
        if (candidates.size() == 1) return new MatchResult(candidates, 0);

        String wanted = canonicalForm(form);
        List<ChecklistEntry> exact = new ArrayList<>();
        for (ChecklistEntry candidate : candidates) {
            if (formsEquivalent(wanted, canonicalForm(candidate.form))) exact.add(candidate);
        }
        if (exact.size() == 1) return new MatchResult(exact, 0);
        return new MatchResult(Collections.emptyList(), 1);
    }

    static boolean formsEquivalent(String left, String right) {
        if (left.equals(right)) return true;
        return !left.isEmpty() && !right.isEmpty()
                && (left.contains(right) || right.contains(left));
    }

    static String canonicalForm(String value) {
        String form = normalize(value);
        if (form.isEmpty() || form.equals("estandar") || form.equals("standard")
                || form.equals("base") || form.equals("original")) return "standard";
        if (form.startsWith("revisar forma")) return "review";

        return form.replace("paldea forma combatiente", "paldean combat breed")
                .replace("paldea forma ardiente", "paldean blaze breed")
                .replace("paldea forma acuatica", "paldean aqua breed")
                .replace("tronco planta", "plant")
                .replace("tronco arena", "sandy")
                .replace("tronco basura", "trash")
                .replace("rotom calor", "heat")
                .replace("rotom lavado", "wash")
                .replace("rotom frio", "frost")
                .replace("rotom ventilador", "fan")
                .replace("rotom corte", "mow")
                .replace("forma tierra", "land")
                .replace("forma cielo", "sky")
                .replace("contenido", "confined")
                .replace("desatado", "unbound")
                .replace("estilo apasionado", "baile style")
                .replace("estilo animado", "pom pom style")
                .replace("estilo placido", "pa u style")
                .replace("estilo refinado", "sensu style")
                .replace("estilo brusco", "single strike")
                .replace("estilo fluido", "rapid strike")
                .replace("mar oeste", "west sea")
                .replace("mar este", "east sea")
                .replace("raya roja", "red stripe")
                .replace("raya azul", "blue stripe")
                .replace("raya blanca", "white stripe")
                .replace("primavera", "spring")
                .replace("verano", "summer")
                .replace("otono", "fall")
                .replace("invierno", "winter")
                .replace("flor eterna", "eternal flower")
                .replace("forma ataque", "attack")
                .replace("forma defensa", "defense")
                .replace("forma velocidad", "speed")
                .replace("forma normal", "normal")
                .replace("forma ", "")
                .replace("rotom ", "")
                .trim();
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}0-9!?]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String key(int number, ChecklistMark mark, boolean shiny) {
        return number + "|" + mark.code + "|" + shiny;
    }

    public static final class MatchResult {
        static final MatchResult EMPTY = new MatchResult(Collections.emptyList(), 0);
        public final List<ChecklistEntry> entries;
        public final int ambiguous;

        MatchResult(List<ChecklistEntry> entries, int ambiguous) {
            this.entries = entries;
            this.ambiguous = ambiguous;
        }
    }
}
