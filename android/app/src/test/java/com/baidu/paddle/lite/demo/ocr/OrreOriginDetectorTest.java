package com.baidu.paddle.lite.demo.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OrreOriginDetectorTest {
    private final OrreOriginDetector detector = new OrreOriginDetector();

    @Test
    public void nationalRibbonConfirmsShinyAsColosseum() {
        PokemonRecord record = record("Sin marca (Gen 3-5)", true);

        OrreOriginDetector.Result result = detector.detect(labels("National Ribbon"), record);

        assertTrue(result.matched());
        assertTrue(result.nationalRibbon);
        assertEquals(OrreOriginDetector.COLOSSEUM, result.specialOrigin);
    }

    @Test
    public void localizedRibbonAndDistantLandNamesAreRecognized() {
        PokemonRecord record = record("Sin marca (Gen 3-5)", false);

        OrreOriginDetector.Result result = detector.detect(
                labels("國家獎章", "在遙遠的地方相遇"), record);

        assertTrue(result.nationalRibbon);
        assertTrue(result.distantLand);
        assertEquals(OrreOriginDetector.COLOSSEUM_OR_XD, result.specialOrigin);
    }

    @Test
    public void unrelatedDetailsDoNotProduceAnOrreOrigin() {
        OrreOriginDetector.Result result = detector.detect(
                labels("Ability", "Height", "Classic Ribbon"),
                record("Sin marca (Gen 3-5)", false));

        assertFalse(result.matched());
    }

    @Test
    public void markedPokemonDoesNotRequestTheExtraPass() {
        assertFalse(detector.requiresInspection(record("Alola", false)));
        assertTrue(detector.requiresInspection(record("Revisar", false)));
    }

    private static PokemonRecord record(String origin, boolean shiny) {
        return new PokemonRecord(
                197, "Umbreon", "Standard", origin, shiny, "Jacs", "123456",
                "Poké Ball", "ENG", 0.9f, "test://record");
    }

    private static List<OcrResultModel> labels(String... values) {
        if (values.length == 0) return Collections.emptyList();
        OcrResultModel[] results = new OcrResultModel[values.length];
        for (int index = 0; index < values.length; index++) {
            results[index] = new OcrResultModel();
            results[index].setLabel(values[index]);
        }
        return Arrays.asList(results);
    }
}
