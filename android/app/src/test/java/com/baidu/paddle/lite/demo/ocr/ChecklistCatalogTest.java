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
    public void catalogCoversEveryNationalNumberAndCorrectedNames() throws IOException {
        Path path = catalogPath();
        List<List<String>> rows = ChecklistCsv.parse(new String(
                Files.readAllBytes(path), StandardCharsets.UTF_8));
        Set<Integer> numbers = new HashSet<>();
        boolean cutiefly = false;
        boolean containsKnownTypo = false;
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int number = ChecklistRepository.parseNumber(row.get(1));
            numbers.add(number);
            String pokemon = row.get(2);
            if (number == 742 && pokemon.equals("Cutiefly")) cutiefly = true;
            if (pokemon.equals("Venosaur") || pokemon.equals("Simpour")
                    || pokemon.equals("Atctozolt") || pokemon.equals("Archaludom")) {
                containsKnownTypo = true;
            }
        }

        assertEquals(7715, rows.size());
        assertEquals(1020, numbers.size());
        assertFalse(numbers.contains(721));
        assertFalse(numbers.contains(801));
        assertFalse(numbers.contains(896));
        assertFalse(numbers.contains(897));
        assertFalse(numbers.contains(898));
        assertTrue(cutiefly);
        assertFalse(containsKnownTypo);
    }

    private static Path catalogPath() {
        Path fromAndroidRoot = Path.of("app", "src", "main", "assets", "checklist_catalog.csv");
        if (Files.exists(fromAndroidRoot)) return fromAndroidRoot;
        Path fromAppRoot = Path.of("src", "main", "assets", "checklist_catalog.csv");
        assertTrue("No se encontró checklist_catalog.csv", Files.exists(fromAppRoot));
        return fromAppRoot;
    }
}
