package com.baidu.paddle.lite.demo.ocr;

import android.graphics.Point;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PokemonParser {
    private static final Pattern NUMBER = Pattern.compile("(?i)\\bNo\\.?\\s*0*(\\d{1,4})\\b");
    private static final Pattern LANGUAGE_TOKEN = Pattern.compile("(?:[A-Z]{3}|[A-Z]{2}-[A-Z]{2})");
    private static final Pattern TRAINER_ID = Pattern.compile("\\d{1,6}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}][\\p{L}' .:\\-]{2,}");
    private static final Pattern OT_VALUE = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}' .:\\-・]{0,20}");
    private static final Set<String> LANGUAGES = new HashSet<>(Arrays.asList(
            "JPN", "ENG", "ES-ES", "ES-LA", "CHS", "CHT", "DEU", "FRA", "ITA", "KOR"));

    private final SpeciesCatalog speciesCatalog;

    public PokemonParser(SpeciesCatalog speciesCatalog) {
        this.speciesCatalog = speciesCatalog;
    }

    public String extractLanguage(List<OcrResultModel> results) {
        List<Detection> detections = detections(results);
        for (Detection detection : detections) {
            String language = canonicalLanguage(detection.text);
            if (language != null) return language;
        }
        detections.sort(Comparator.comparingDouble(item -> item.cx));
        StringBuilder joined = new StringBuilder();
        for (Detection detection : detections) joined.append(detection.text);
        String language = canonicalLanguage(joined.toString());
        return language == null ? "Revisar" : language;
    }

    public boolean hasValidNationalNumber(List<OcrResultModel> results) {
        return bestStrictNumber(detections(results)) != null;
    }

    public PokemonRecord parse(
            List<OcrResultModel> rawResults,
            List<OcrResultModel> numberResults,
            List<OcrResultModel> languageResults,
            List<OcrResultModel> otResults,
            int imageWidth,
            int imageHeight,
            TemplateIconDetector.Result ball,
            TemplateIconDetector.Result origin,
            TemplateIconDetector.PresenceResult shiny,
            String source
    ) {
        List<Detection> detections = detections(rawResults);

        NumberCandidate numberCandidate = bestStrictNumber(detections);
        if (numberCandidate == null) {
            numberCandidate = bestStrictNumber(detections(numberResults));
        }
        if (numberCandidate == null) {
            numberCandidate = flexibleNumberFromDedicatedCrop(detections(numberResults));
        }
        if (numberCandidate == null) {
            throw new IllegalArgumentException("No se encontró el número nacional.");
        }
        int nationalNumber = numberCandidate.value;
        String species = speciesCatalog.nameFor(nationalNumber);

        String language = extractLanguage(languageResults);
        if (language.equals("Revisar")) language = extractLanguage(rawResults);

        String ot = bestOtValue(detections(otResults));
        if (ot.equals("Revisar")) {
            Detection otLabel = findLabel(detections, "OT", "EO", "DO", "D.O.", "おや");
            ot = valueToRight(detections, otLabel, imageHeight * 0.035f,
                    item -> validOt(item.text) && !normalize(item.text).contains("IDNO"));
        }
        if (ot.equals("Revisar")) {
            ot = bestTextInRegion(
                    detections,
                    imageWidth * 0.20f,
                    imageWidth * 0.52f,
                    imageHeight * 0.835f,
                    imageHeight * 0.875f,
                    item -> validOt(item.text));
        }

        Detection idLabel = detections.stream()
                .filter(item -> normalize(item.text).contains("IDNO"))
                .findFirst()
                .orElse(null);
        String trainerId = valueToRight(detections, idLabel, imageHeight * 0.035f,
                item -> normalizedTrainerId(item.text) != null);
        if (!trainerId.equals("Revisar")) trainerId = normalizedTrainerId(trainerId);
        if (trainerId == null || trainerId.equals("Revisar")) {
            trainerId = bestTextInRegion(
                    detections,
                    imageWidth * 0.65f,
                    imageWidth * 0.96f,
                    imageHeight * 0.835f,
                    imageHeight * 0.875f,
                    item -> normalizedTrainerId(item.text) != null);
            String normalized = normalizedTrainerId(trainerId);
            trainerId = normalized == null ? "Revisar" : normalized;
        }

        float confidence = average(
                numberCandidate.score,
                Math.max(0f, ball.score),
                Math.max(0f, origin.score),
                speciesCatalog.contains(nationalNumber) ? 1f : 0f,
                language.equals("Revisar") ? 0f : 0.9f,
                ot.equals("Revisar") ? 0f : 0.85f,
                trainerId.equals("Revisar") ? 0f : 0.9f,
                0.9f);

        return new PokemonRecord(
                nationalNumber,
                species,
                "Estándar",
                origin.label,
                shiny.present,
                ot,
                trainerId,
                ball.label,
                language,
                confidence,
                source);
    }

    private NumberCandidate bestStrictNumber(List<Detection> detections) {
        NumberCandidate best = null;
        for (Detection detection : detections) {
            Matcher matcher = NUMBER.matcher(detection.text);
            if (!matcher.find()) continue;
            int value;
            try {
                value = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (!speciesCatalog.contains(value)) continue;
            if (best == null || detection.score > best.score) {
                best = new NumberCandidate(value, detection.score);
            }
        }
        return best;
    }

    private NumberCandidate flexibleNumberFromDedicatedCrop(List<Detection> detections) {
        if (detections.isEmpty()) return null;
        detections.sort(Comparator.comparingDouble(item -> item.cx));
        StringBuilder joined = new StringBuilder();
        float bestScore = 0f;
        for (Detection detection : detections) {
            joined.append(detection.text);
            bestScore = Math.max(bestScore, detection.score);
        }

        Integer value = flexibleNumber(joined.toString(), true);
        if (value == null) {
            for (Detection detection : detections) {
                value = flexibleNumber(detection.text, false);
                if (value != null) {
                    bestScore = detection.score;
                    break;
                }
            }
        }
        if (value == null || !speciesCatalog.contains(value)) return null;
        // Los caracteres ambiguos solo se corrigen dentro del recorte fijo de No.xxxx.
        return new NumberCandidate(value, Math.max(0.35f, bestScore * 0.85f));
    }

    private static Integer flexibleNumber(String raw, boolean requirePrefix) {
        String compact = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        int start = -1;
        for (String prefix : new String[]{"NO", "N0", "NQ"}) {
            int index = compact.indexOf(prefix);
            if (index >= 0) {
                start = index + prefix.length();
                break;
            }
        }
        if (start < 0) {
            if (requirePrefix || compact.length() < 1 || compact.length() > 4) return null;
            start = 0;
        }

        StringBuilder digits = new StringBuilder();
        for (int index = start; index < compact.length() && digits.length() < 4; index++) {
            char mapped = digitLike(compact.charAt(index));
            if (mapped == 0) {
                if (digits.length() > 0) break;
                continue;
            }
            digits.append(mapped);
        }
        if (digits.length() == 0) return null;
        try {
            int value = Integer.parseInt(digits.toString());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static char digitLike(char value) {
        if (value >= '0' && value <= '9') return value;
        switch (value) {
            case 'O': case 'Q': case 'D': return '0';
            case 'I': case 'L': return '1';
            case 'Z': return '2';
            case 'S': return '5';
            case 'B': return '8';
            case 'G': return '9';
            default: return 0;
        }
    }

    private static List<Detection> detections(List<OcrResultModel> rawResults) {
        List<Detection> detections = new ArrayList<>();
        if (rawResults == null) return detections;
        for (OcrResultModel result : rawResults) {
            if (result.getLabel() == null || result.getLabel().trim().isEmpty()) continue;
            detections.add(new Detection(result));
        }
        return detections;
    }

    private static String canonicalLanguage(String raw) {
        String compact = raw == null ? "" : raw.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9-]", "");
        Matcher matcher = LANGUAGE_TOKEN.matcher(compact);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (LANGUAGES.contains(candidate)) return candidate;
        }
        return null;
    }

    private static String bestOtValue(List<Detection> detections) {
        return detections.stream()
                .filter(item -> item.score >= 0.35f)
                .filter(item -> validOt(item.text))
                .filter(item -> {
                    String normalized = normalize(item.text);
                    return !normalized.equals("OT")
                            && !normalized.equals("EO")
                            && !normalized.equals("DO")
                            && !normalized.equals("おや")
                            && !normalized.contains("IDNO");
                })
                .max(Comparator.comparingDouble((Detection item) -> item.score)
                        .thenComparingInt(item -> item.text.length()))
                .map(item -> item.text.trim())
                .orElse("Revisar");
    }

    private static boolean validOt(String value) {
        return value != null && OT_VALUE.matcher(value.trim()).matches();
    }

    private static String normalizedTrainerId(String value) {
        if (value == null) return null;
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            int digit = Character.digit(value.charAt(index), 10);
            if (digit >= 0) digits.append(digit);
        }
        return TRAINER_ID.matcher(digits).matches() ? digits.toString() : null;
    }

    private static Detection findLabel(List<Detection> detections, String... labels) {
        for (Detection detection : detections) {
            String normalized = normalize(detection.text);
            for (String label : labels) {
                if (normalized.equals(normalize(label))) return detection;
            }
        }
        return null;
    }

    private static String valueToRight(
            List<Detection> detections,
            Detection label,
            float maximumVerticalDistance,
            CandidateFilter filter
    ) {
        if (label == null) return "Revisar";
        return detections.stream()
                .filter(item -> item.cx > label.cx)
                .filter(item -> Math.abs(item.cy - label.cy) <= maximumVerticalDistance)
                .filter(item -> item.score >= 0.35f)
                .filter(filter::accept)
                .min(Comparator.comparingDouble(item ->
                        Math.abs(item.cy - label.cy) * 10 + item.cx - label.cx))
                .map(item -> item.text.trim())
                .orElse("Revisar");
    }

    private static String bestTextInRegion(
            List<Detection> detections,
            float left,
            float right,
            float top,
            float bottom,
            CandidateFilter filter
    ) {
        Detection best = null;
        for (Detection item : detections) {
            if (item.cx < left || item.cx > right || item.cy < top || item.cy > bottom) continue;
            if (item.score < 0.35f || !filter.accept(item)) continue;
            if (best == null || item.score > best.score) best = item;
        }
        return best == null ? "Revisar" : best.text.trim();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    private static float average(float... values) {
        float total = 0f;
        for (float value : values) total += Math.max(0f, Math.min(1f, value));
        return values.length == 0 ? 0f : total / values.length;
    }

    private interface CandidateFilter {
        boolean accept(Detection detection);
    }

    private static final class Detection {
        final String text;
        final float score;
        final float cx;
        final float cy;

        Detection(OcrResultModel source) {
            text = source.getLabel().trim();
            score = source.getConfidence();
            float totalX = 0f;
            float totalY = 0f;
            List<Point> points = source.getPoints();
            for (Point point : points) {
                totalX += point.x;
                totalY += point.y;
            }
            cx = points.isEmpty() ? 0f : totalX / points.size();
            cy = points.isEmpty() ? 0f : totalY / points.size();
        }
    }

    private static final class NumberCandidate {
        final int value;
        final float score;

        NumberCandidate(int value, float score) {
            this.value = value;
            this.score = score;
        }
    }
}
