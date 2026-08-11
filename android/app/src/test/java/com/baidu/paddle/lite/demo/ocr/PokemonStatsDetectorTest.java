package com.baidu.paddle.lite.demo.ocr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PokemonStatsDetectorTest {
    @Test
    public void classifiesAllFourDeoxysForms() {
        assertEquals("Forma Ataque",
                PokemonStatsDetector.classifyDeoxys(89, 17, 81, 17, 59));
        assertEquals("Forma Defensa",
                PokemonStatsDetector.classifyDeoxys(49, 81, 45, 90, 52));
        assertEquals("Forma Velocidad",
                PokemonStatsDetector.classifyDeoxys(47, 40, 47, 45, 90));
        assertEquals("Forma Normal",
                PokemonStatsDetector.classifyDeoxys(96, 40, 99, 43, 98));
    }
}
