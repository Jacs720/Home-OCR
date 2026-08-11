package com.baidu.paddle.lite.demo.ocr;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class ChecklistCsvTest {
    @Test
    public void parsesBomQuotesCommasAndLineBreaks() {
        String csv = "\uFEFFNo.,Especie,Archivo\r\n"
                + "\"0025\",\"Pikachu\",\"captura, 1.png\"\r\n"
                + "\"0201\",\"Unown\",\"línea 1\n línea 2\"\r\n";

        List<List<String>> rows = ChecklistCsv.parse(csv);

        assertEquals(3, rows.size());
        assertEquals("No.", rows.get(0).get(0));
        assertEquals("captura, 1.png", rows.get(1).get(2));
        assertEquals("línea 1\n línea 2", rows.get(2).get(2));
    }
}
