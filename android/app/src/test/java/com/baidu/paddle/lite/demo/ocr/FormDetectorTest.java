package com.baidu.paddle.lite.demo.ocr;

import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.ACERO;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.AGUA;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.BICHO;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.ELECTRICO;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.FUEGO;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.LUCHA;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.NORMAL;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.PLANTA;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.SINIESTRO;
import static com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type.VOLADOR;
import static org.junit.Assert.assertEquals;

import java.util.EnumSet;

import org.junit.Test;

public final class FormDetectorTest {
    private final FormDetector detector = new FormDetector();

    @Test
    public void detectsAlolanRaticateFromTypes() {
        assertEquals("Alola", detector.detect(
                record(20, "Raticate", "Let's Go", "Tatianna", "121106"),
                types(SINIESTRO, NORMAL)));
    }

    @Test
    public void keepsStandardRaticateWhenTypesAreNormal() {
        assertEquals("Estándar", detector.detect(
                record(20, "Raticate", "Let's Go", "Tatianna", "121106"),
                types(NORMAL)));
    }

    @Test
    public void detectsGalarianMeowthFromSteelType() {
        assertEquals("Galar", detector.detect(
                record(52, "Meowth", "Galar", "Jacs", "1"), types(ACERO)));
    }

    @Test
    public void doesNotGuessRegionalFormWithoutReliableTypes() {
        assertEquals("Revisar forma regional", detector.detect(
                record(20, "Raticate", "Let's Go", "Tatianna", "121106"), null));
    }

    @Test
    public void flagsAppearanceOnlyForms() {
        assertEquals("Revisar forma visual", detector.detect(
                record(422, "Shellos", "Sin marca", "Jacs", "1"), types(NORMAL)));
    }

    @Test
    public void detectsHisuianLilligantFromTypes() {
        assertEquals("Hisui", detector.detect(
                record(549, "Lilligant", "Hisui", "Jacs", "1"), types(PLANTA, LUCHA)));
    }

    @Test
    public void detectsRotomAppliancesFromTypes() {
        assertEquals("Rotom Calor", detector.detect(
                record(479, "Rotom", "Sin marca", "Jacs", "1"), types(ELECTRICO, FUEGO)));
        assertEquals("Rotom Lavado", detector.detect(
                record(479, "Rotom", "Sin marca", "Jacs", "1"), types(ELECTRICO, AGUA)));
        assertEquals("Rotom Corte", detector.detect(
                record(479, "Rotom", "Sin marca", "Jacs", "1"), types(ELECTRICO, PLANTA)));
    }

    @Test
    public void detectsShayminSkyFromTypes() {
        assertEquals("Forma Cielo", detector.detect(
                record(492, "Shaymin", "Sin marca", "Jacs", "1"), types(PLANTA, VOLADOR)));
    }

    @Test
    public void detectsWormadamCloakFromTypes() {
        assertEquals("Tronco Planta", detector.detect(
                record(413, "Wormadam", "Sin marca", "Jacs", "1"), types(BICHO, PLANTA)));
    }

    private static PokemonTypeDetector.Result types(PokemonTypeDetector.Type... values) {
        EnumSet<PokemonTypeDetector.Type> set = EnumSet.noneOf(PokemonTypeDetector.Type.class);
        for (PokemonTypeDetector.Type value : values) set.add(value);
        return new PokemonTypeDetector.Result(set, true, 1f);
    }

    private static PokemonRecord record(
            int number, String species, String origin, String ot, String trainerId
    ) {
        return new PokemonRecord(
                number, species, "Estándar", origin, false, ot, trainerId,
                "Poké Ball", "ENG", 1f, "test");
    }
}
