package com.baidu.paddle.lite.demo.ocr;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class ChecklistMatcherTest {
    @Test
    public void matchesOriginAliasesAndRegionalForms() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("base", 19, "Rattata", "Estándar", "Alola", ChecklistEntry.TYPE_ORIGIN_MARK),
                entry("alola", 19, "Rattata alola", "alola", "Alola", ChecklistEntry.TYPE_ORIGIN_MARK));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult result = matcher.match(19, "Alola", "Clover Mark", "Poké Ball");

        assertEquals(1, result.entries.size());
        assertEquals("alola", result.entries.get(0).id);
        assertEquals(0, result.ambiguous);
    }

    @Test
    public void matchesSpanishVisualFormToWorkbookForm() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("west", 422, "Shellos west", "west", "Paldea", ChecklistEntry.TYPE_ORIGIN_MARK),
                entry("east", 422, "Shellos east", "east", "Paldea", ChecklistEntry.TYPE_ORIGIN_MARK));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult result = matcher.match(422, "Mar Oeste", "Paldea", "Poké Ball");

        assertEquals(1, result.entries.size());
        assertEquals("west", result.entries.get(0).id);
    }

    @Test
    public void marksOriginAndDreamBallTargetsFromOneRecord() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("origin", 1, "Bulbasaur", "Estándar", "Kalos", ChecklistEntry.TYPE_ORIGIN_MARK),
                entry("dream", 1, "Bulbasaur", "Estándar", "Dream Ball (V)", ChecklistEntry.TYPE_DREAM_BALL));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult result = matcher.match(1, "Estándar", "Pentagon Mark", "Dream Ball");

        assertEquals(2, result.entries.size());
    }

    @Test
    public void reportsAmbiguousReviewFormsInsteadOfGuessing() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("red", 550, "Basculin red", "red", "Galar", ChecklistEntry.TYPE_ORIGIN_MARK),
                entry("blue", 550, "Basculin blue", "blue", "Galar", ChecklistEntry.TYPE_ORIGIN_MARK));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult result = matcher.match(
                550, "Revisar forma visual", "Galar", "Poké Ball");

        assertEquals(0, result.entries.size());
        assertEquals(1, result.ambiguous);
    }

    private static ChecklistEntry entry(
            String id, int number, String pokemon, String form, String mark, String type
    ) {
        return new ChecklistEntry(id, number, pokemon, form, mark, type, false);
    }
}
