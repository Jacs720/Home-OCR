package com.baidu.paddle.lite.demo.ocr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VisualFormDetectorTest {
    @Test
    public void classifiesFourDeerlingSeasons() {
        assertEquals("Primavera", VisualFormDetector.classifySeason(.796, .714, .660));
        assertEquals("Verano", VisualFormDetector.classifySeason(.676, .722, .574));
        assertEquals("Otoño", VisualFormDetector.classifySeason(.828, .735, .615));
        assertEquals("Invierno", VisualFormDetector.classifySeason(.748, .693, .626));
    }
}
