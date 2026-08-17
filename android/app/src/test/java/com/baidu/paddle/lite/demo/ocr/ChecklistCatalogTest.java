package com.baidu.paddle.lite.demo.ocr;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChecklistCatalogTest {
    @Test
    public void catalogContainsOnlyUniqueNormalAndShinyTargetsByMark() throws IOException {
        List<List<String>> rows = ChecklistCsv.parse(new String(
                Files.readAllBytes(catalogPath()), StandardCharsets.UTF_8));
        Set<String> targets = new HashSet<>();
        Set<String> marks = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        int normal = 0;
        int shiny = 0;
        boolean shinyNoMarkReshiram = false;
        boolean shinyNoMarkZekrom = false;
        boolean whiteStripeNoMark = false;

        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            assertEquals(6, row.size());
            int number = ChecklistRepository.parseNumber(row.get(1));
            String form = row.get(3);
            String mark = row.get(4);
            boolean isShiny = ChecklistRepository.truthy(row.get(5));
            numbers.add(number);
            marks.add(mark);
            assertTrue(targets.add(mark + "|" + number + "|" + form + "|" + isShiny));
            if (isShiny) shiny++;
            else normal++;
            if (mark.equals("NO_MARK") && number == 643 && isShiny) shinyNoMarkReshiram = true;
            if (mark.equals("NO_MARK") && number == 644 && isShiny) shinyNoMarkZekrom = true;
            if (mark.equals("NO_MARK") && number == 550
                    && form.equals("White Stripe")) whiteStripeNoMark = true;
        }

        assertEquals(13430, rows.size());
        assertEquals(6729, normal);
        assertEquals(6700, shiny);
        assertEquals(1025, numbers.size());
        assertEquals(Set.of("NO_MARK", "GB", "GO", "P", "USUM", "LGPE",
                "SWSH", "LA", "BDSP", "SV", "LZA"), marks);
        assertFalse(shinyNoMarkReshiram);
        assertFalse(shinyNoMarkZekrom);
        assertFalse(whiteStripeNoMark);
    }

    private static Path catalogPath() {
        Path fromAndroidRoot = Path.of("app", "src", "main", "assets", "checklist_catalog.csv");
        if (Files.exists(fromAndroidRoot)) return fromAndroidRoot;
        Path fromAppRoot = Path.of("src", "main", "assets", "checklist_catalog.csv");
        assertTrue("checklist_catalog.csv not found", Files.exists(fromAppRoot));
        return fromAppRoot;
    }
}
