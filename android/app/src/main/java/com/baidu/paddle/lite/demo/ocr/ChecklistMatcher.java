package com.baidu.paddle.lite.demo.ocr;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChecklistMatcher {
    private final Map<String, List<ChecklistEntry>> entriesByTarget = new HashMap<>();

    public ChecklistMatcher(List<ChecklistEntry> entries) {
        for (ChecklistEntry entry : entries) {
            entriesByTarget.computeIfAbsent(key(
                    entry.nationalNumber, entry.targetType, entry.originMark),
                    ignored -> new ArrayList<>()).add(entry);
        }
    }

    public MatchResult match(
            int nationalNumber,
            String form,
            String originMark,
            String ball
    ) {
        List<ChecklistEntry> matches = new ArrayList<>();
        int ambiguous = 0;

        String canonicalOrigin = canonicalOrigin(originMark);
        if (canonicalOrigin != null) {
            TargetMatch target = matchTarget(
                    nationalNumber,
                    ChecklistEntry.TYPE_ORIGIN_MARK,
                    canonicalOrigin,
                    form);
            matches.addAll(target.entries);
            ambiguous += target.ambiguous;
        }

        if (normalize(ball).contains("dream ball")) {
            TargetMatch target = matchTarget(
                    nationalNumber,
                    ChecklistEntry.TYPE_DREAM_BALL,
                    "Dream Ball (V)",
                    form);
            matches.addAll(target.entries);
            ambiguous += target.ambiguous;
        }
        return new MatchResult(matches, ambiguous);
    }

    private TargetMatch matchTarget(
            int nationalNumber,
            String targetType,
            String target,
            String detectedForm
    ) {
        List<ChecklistEntry> candidates = entriesByTarget.getOrDefault(
                key(nationalNumber, targetType, target), new ArrayList<>());
        if (candidates.isEmpty()) return TargetMatch.EMPTY;
        if (candidates.size() == 1) return new TargetMatch(candidates, 0);

        String wanted = canonicalForm(detectedForm);
        List<ChecklistEntry> exact = new ArrayList<>();
        for (ChecklistEntry candidate : candidates) {
            if (formsEquivalent(wanted, canonicalForm(candidate.form))) exact.add(candidate);
        }
        if (exact.size() == 1) return new TargetMatch(exact, 0);
        return new TargetMatch(new ArrayList<>(), 1);
    }

    static boolean formsEquivalent(String left, String right) {
        if (left.equals(right)) return true;
        return !left.isEmpty() && !right.isEmpty()
                && (left.contains(right) || right.contains(left));
    }

    static String canonicalForm(String value) {
        String form = normalize(value);
        if (form.isEmpty() || form.equals("estandar") || form.equals("standard")
                || form.equals("base")) return "standard";
        if (form.startsWith("revisar forma")) return "review";

        form = form.replace("paldea forma combatiente", "combat")
                .replace("paldea forma ardiente", "blaze")
                .replace("paldea forma acuatica", "aqua")
                .replace("tronco planta", "green")
                .replace("tronco arena", "yellow")
                .replace("tronco basura", "pink")
                .replace("rotom calor", "heat")
                .replace("rotom lavado", "wash")
                .replace("rotom frio", "fridge")
                .replace("rotom ventilador", "fan")
                .replace("rotom corte", "mow")
                .replace("forma tierra", "land")
                .replace("forma cielo", "sky")
                .replace("contenido", "confined")
                .replace("desatado", "unbound")
                .replace("estilo apasionado", "baile")
                .replace("estilo animado", "pompom")
                .replace("estilo placido", "pa u")
                .replace("estilo refinado", "sensu")
                .replace("estilo brusco", "single strike")
                .replace("estilo fluido", "rapid strike")
                .replace("mar oeste", "west")
                .replace("mar este", "east")
                .replace("raya roja", "red")
                .replace("raya azul", "blue")
                .replace("raya blanca", "white")
                .replace("primavera", "spring")
                .replace("verano", "summer")
                .replace("otono", "autumn")
                .replace("invierno", "winter")
                .replace("forma ataque", "attack")
                .replace("forma defensa", "defense")
                .replace("forma velocidad", "speed")
                .replace("forma normal", "normal")
                .replace("forma ", "")
                .replace("rotom ", "")
                .trim();
        return form;
    }

    static String canonicalOrigin(String value) {
        String mark = normalize(value);
        if (mark.isEmpty()) return null;
        if (mark.contains("consola virtual") || mark.contains("gameboy")
                || mark.contains("game boy")) return "Consola Virtual";
        if (mark.contains("sin marca") || mark.contains("no mark")) {
            return "Sin marca (Gen 3-5)";
        }
        if (mark.contains("pentagon") || mark.equals("kalos")) return "Kalos";
        if (mark.contains("clover") || mark.equals("alola")) return "Alola";
        if (mark.contains("pokemon go") || mark.equals("go") || mark.contains("go mark")) {
            return "Pokémon GO";
        }
        if (mark.contains("let s go") || mark.contains("lets go") || mark.contains("lgpe")) {
            return "Let's Go";
        }
        if (mark.equals("galar") || mark.contains("galar mark")) return "Galar";
        if (mark.contains("bdsp") || mark.contains("sinnoh")) return "Sinnoh (BDSP)";
        if (mark.contains("hisui") || mark.contains("legends arceus")
                || mark.equals("pla") || mark.equals("la")) {
            return "Hisui (Legends: Arceus)";
        }
        if (mark.equals("paldea") || mark.contains("scarlet violet") || mark.equals("sv")) {
            return "Paldea";
        }
        if (mark.contains("legends z a") || mark.equals("lza")) return "Legends: Z-A";
        return null;
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}0-9]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String key(int number, String type, String target) {
        return number + "|" + type + "|" + normalize(target);
    }

    public static final class MatchResult {
        public final List<ChecklistEntry> entries;
        public final int ambiguous;

        MatchResult(List<ChecklistEntry> entries, int ambiguous) {
            this.entries = entries;
            this.ambiguous = ambiguous;
        }
    }

    private static final class TargetMatch {
        static final TargetMatch EMPTY = new TargetMatch(new ArrayList<>(), 0);
        final List<ChecklistEntry> entries;
        final int ambiguous;

        TargetMatch(List<ChecklistEntry> entries, int ambiguous) {
            this.entries = entries;
            this.ambiguous = ambiguous;
        }
    }
}
