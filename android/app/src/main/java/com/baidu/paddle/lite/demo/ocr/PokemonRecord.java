package com.baidu.paddle.lite.demo.ocr;

import java.util.Locale;

public final class PokemonRecord {
    public static final String CSV_HEADER =
            "No.,Especie,Forma,Marca de origen,Shiny,OT,IDNo.,Bola,Idioma,Confianza,Archivo";

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
                csv(source));
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
                shiny ? " · Shiny" : "");
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
                source);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
