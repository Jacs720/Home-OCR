package com.baidu.paddle.lite.demo.ocr;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class ChecklistMatcherTest {
    @Test
    public void matchesOriginAliasesAndRegionalForms() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("base", 19, "Rattata", "Original", ChecklistMark.USUM, false),
                entry("alola", 19, "Rattata", "Alolan", ChecklistMark.USUM, false));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult result = matcher.match(
                19, "Alola", "Clover Mark", false);

        assertEquals(1, result.entries.size());
        assertEquals("alola", result.entries.get(0).id);
        assertEquals(0, result.ambiguous);
    }

    @Test
    public void matchesSpanishVisualFormToCatalogForm() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("west", 422, "Shellos", "West Sea", ChecklistMark.SV, true),
                entry("east", 422, "Shellos", "East Sea", ChecklistMark.SV, true));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult result = matcher.match(
                422, "Mar Oeste", "Paldea", true);

        assertEquals(1, result.entries.size());
        assertEquals("west", result.entries.get(0).id);
    }

    @Test
    public void keepsNormalAndShinyTargetsIndependent() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("normal", 1, "Bulbasaur", "", ChecklistMark.NO_MARK, false),
                entry("shiny", 1, "Bulbasaur", "", ChecklistMark.NO_MARK, true));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult normal = matcher.match(
                1, "Estándar", "Sin marca (Gen 3-5)", false);
        ChecklistMatcher.MatchResult shiny = matcher.match(
                1, "Estándar", "No mark", true);

        assertEquals("normal", normal.entries.get(0).id);
        assertEquals("shiny", shiny.entries.get(0).id);
    }

    @Test
    public void reportsAmbiguousReviewFormsInsteadOfGuessing() {
        List<ChecklistEntry> entries = Arrays.asList(
                entry("red", 550, "Basculin", "Red Stripe", ChecklistMark.SWSH, false),
                entry("blue", 550, "Basculin", "Blue Stripe", ChecklistMark.SWSH, false));
        ChecklistMatcher matcher = new ChecklistMatcher(entries);

        ChecklistMatcher.MatchResult result = matcher.match(
                550, "Revisar forma visual", "Galar", false);

        assertEquals(0, result.entries.size());
        assertEquals(1, result.ambiguous);
    }

    private static ChecklistEntry entry(
            String id,
            int number,
            String pokemon,
            String form,
            ChecklistMark mark,
            boolean shiny
    ) {
        return new ChecklistEntry(id, number, pokemon, form, mark, shiny);
    }
}
