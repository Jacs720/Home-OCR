package com.baidu.paddle.lite.demo.ocr;

import java.util.Locale;

public final class PokemonRecord {
    public static final String CSV_HEADER =
            "No.,Especie,Forma,Marca de origen,Shiny,OT,IDNo.,Bola,Idioma,Confianza,Archivo,"
                    + "Origen especial,Cinta Nacional,Lugar lejano";

    public final int nationalNumber;
    public final String species;
    public final String form;
    public final String originMark;
    public final boolean shiny;
    public final String ot;
    public final String trainerId;
    public final String ball;
    public final String language;
    public final float confidence;
    public final String source;
    public final String specialOrigin;
    public final boolean nationalRibbon;
    public final boolean distantLand;

    public PokemonRecord(
            int nationalNumber,
            String species,
            String form,
            String originMark,
            boolean shiny,
            String ot,
            String trainerId,
            String ball,
            String language,
            float confidence,
            String source
    ) {
        this(nationalNumber, species, form, originMark, shiny, ot, trainerId, ball,
                language, confidence, source, "", false, false);
    }

    public PokemonRecord(
            int nationalNumber,
            String species,
            String form,
            String originMark,
            boolean shiny,
            String ot,
            String trainerId,
            String ball,
            String language,
            float confidence,
            String source,
            String specialOrigin,
            boolean nationalRibbon,
            boolean distantLand
    ) {
        this.nationalNumber = nationalNumber;
        this.species = species;
        this.form = form;
        this.originMark = originMark;
        this.shiny = shiny;
        this.ot = ot;
        this.trainerId = trainerId;
        this.ball = ball;
        this.language = language;
        this.confidence = confidence;
        this.source = source;
        this.specialOrigin = specialOrigin == null ? "" : specialOrigin;
        this.nationalRibbon = nationalRibbon;
        this.distantLand = distantLand;
    }

    public String toCsvRow() {
        return String.join(",",
                csv(String.format(Locale.ROOT, "%04d", nationalNumber)),
                csv(species),
                csv(form),
                csv(originMark),
                csv(shiny ? "Sí" : "No"),
                csv(ot),
                csv(trainerId),
                csv(ball),
                csv(language),
                csv(String.format(Locale.ROOT, "%.3f", confidence)),
                csv(source),
                csv(specialOrigin),
                csv(nationalRibbon ? "Sí" : "No"),
                csv(distantLand ? "Sí" : "No"));
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "#%04d %s · %s · %s · %s · %s%s",
                nationalNumber,
                species,
                form,
                ball,
                originMark,
                language,
                shiny ? " · Shiny" : "")
                + (specialOrigin.isEmpty() ? "" : " · " + specialOrigin);
    }

    public PokemonRecord withForm(String detectedForm) {
        return new PokemonRecord(
                nationalNumber,
                species,
                detectedForm,
                originMark,
                shiny,
                ot,
                trainerId,
                ball,
                language,
                confidence,
                source,
                specialOrigin,
                nationalRibbon,
                distantLand);
    }

    public PokemonRecord withOrreEvidence(OrreOriginDetector.Result evidence) {
        if (evidence == null || !evidence.matched()) return this;
        return new PokemonRecord(
                nationalNumber,
                species,
                form,
                originMark,
                shiny,
                ot,
                trainerId,
                ball,
                language,
                confidence,
                source,
                evidence.specialOrigin,
                evidence.nationalRibbon,
                evidence.distantLand);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
