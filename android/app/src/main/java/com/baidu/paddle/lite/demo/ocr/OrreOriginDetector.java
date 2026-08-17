package com.baidu.paddle.lite.demo.ocr;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Detects evidence left by purified Shadow Pokémon on the scrolled summary page. */
public final class OrreOriginDetector {
    public static final String COLOSSEUM = "Pokémon Colosseum";
    public static final String COLOSSEUM_OR_XD = "Pokémon Colosseum/XD";

    private static final String[] NATIONAL_RIBBON_NAMES = {
            "national ribbon", "cinta nacional", "ruban national",
            "band der nation", "fiocco nazionale", "ナショナルリボン",
            "내셔널리본", "國家獎章", "国家奖章"
    };

    private static final String[] DISTANT_LAND_PHRASES = {
            "first met in a distant land", "met in a distant land", "distant land",
            "lugar lejano", "lugar muy lejano", "un lugar lejano",
            "terre lointaine", "pays lointain", "lieu lointain",
            "terra lontana", "luogo lontano",
            "fernen land", "fernes land",
            "とおくはなれた土地", "遠く離れた土地",
            "먼 곳에서", "먼 지방에서",
            "遙遠的地方", "遥远的地方"
    };

    public boolean requiresInspection(PokemonRecord record) {
        if (record == null) return false;
        String origin = normalize(record.originMark);
        return origin.isEmpty() || origin.equals("revisar")
                || origin.contains("sin marca") || origin.contains("no origin mark");
    }

    public Result detect(List<OcrResultModel> results, PokemonRecord record) {
        if (!requiresInspection(record)) return Result.NONE;
        StringBuilder combined = new StringBuilder();
        if (results != null) {
            for (OcrResultModel result : results) {
                if (result != null && result.getLabel() != null) {
                    combined.append(' ').append(result.getLabel());
                }
            }
        }
        String original = combined.toString().toLowerCase(Locale.ROOT);
        String normalized = normalize(original);
        boolean nationalRibbon = containsAny(original, NATIONAL_RIBBON_NAMES)
                || containsAny(normalized, NATIONAL_RIBBON_NAMES);
        boolean distantLand = containsAny(original, DISTANT_LAND_PHRASES)
                || containsAny(normalized, DISTANT_LAND_PHRASES);
        if (!nationalRibbon && !distantLand) return Result.NONE;
        String origin = record != null && record.shiny ? COLOSSEUM : COLOSSEUM_OR_XD;
        return new Result(origin, nationalRibbon, distantLand);
    }

    private static boolean containsAny(String text, String[] values) {
        for (String value : values) {
            if (text.contains(value) || text.contains(normalize(value))) return true;
        }
        return false;
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static final class Result {
        static final Result NONE = new Result("", false, false);

        public final String specialOrigin;
        public final boolean nationalRibbon;
        public final boolean distantLand;

        Result(String specialOrigin, boolean nationalRibbon, boolean distantLand) {
            this.specialOrigin = specialOrigin;
            this.nationalRibbon = nationalRibbon;
            this.distantLand = distantLand;
        }

        public boolean matched() {
            return !specialOrigin.isEmpty();
        }
    }
}
