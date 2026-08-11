package com.baidu.paddle.lite.demo.ocr;

import com.baidu.paddle.lite.demo.ocr.PokemonTypeDetector.Type;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resuelve formas que HOME conserva usando señales independientes del idioma. */
public final class FormDetector {
    private static final Map<Integer, List<FormRule>> REGIONAL_RULES = new HashMap<>();
    private static final Map<Integer, List<FormRule>> TYPE_FORM_RULES = new HashMap<>();

    /*
     * Formas depositables cuya diferencia principal es visual. Mantenerlas aquí evita
     * exportarlas como "Estándar" mientras se añade un clasificador específico.
     */
    private static final Set<Integer> VISUAL_FORMS = new HashSet<>(Arrays.asList(
            25, 201, 412, 422, 423, 550, 585, 586, 592, 593,
            641, 642, 645, 647, 666, 668, 669, 670, 671, 676, 678,
            718, 774, 801, 854, 855, 869, 876, 893, 901, 902, 905,
            916, 925, 931, 978, 982, 999, 1012, 1013));

    /* Formas conservables que se distinguen mejor por estadísticas, habilidad o naturaleza. */
    private static final Set<Integer> DATA_FORMS = new HashSet<>(Arrays.asList(
            386, 710, 711, 745, 849));

    static {
        regional(19, "Alola", Type.SINIESTRO, Type.NORMAL);
        regional(20, "Alola", Type.SINIESTRO, Type.NORMAL);
        regional(26, "Alola", Type.ELECTRICO, Type.PSIQUICO);
        regional(27, "Alola", Type.HIELO, Type.ACERO);
        regional(28, "Alola", Type.HIELO, Type.ACERO);
        regional(37, "Alola", Type.HIELO);
        regional(38, "Alola", Type.HIELO, Type.HADA);
        regional(50, "Alola", Type.TIERRA, Type.ACERO);
        regional(51, "Alola", Type.TIERRA, Type.ACERO);
        regional(52, "Alola", Type.SINIESTRO);
        regional(52, "Galar", Type.ACERO);
        regional(53, "Alola", Type.SINIESTRO);
        regional(58, "Hisui", Type.FUEGO, Type.ROCA);
        regional(59, "Hisui", Type.FUEGO, Type.ROCA);
        regional(74, "Alola", Type.ROCA, Type.ELECTRICO);
        regional(75, "Alola", Type.ROCA, Type.ELECTRICO);
        regional(76, "Alola", Type.ROCA, Type.ELECTRICO);
        regional(77, "Galar", Type.PSIQUICO);
        regional(78, "Galar", Type.PSIQUICO, Type.HADA);
        regional(79, "Galar", Type.PSIQUICO);
        regional(80, "Galar", Type.VENENO, Type.PSIQUICO);
        regional(83, "Galar", Type.LUCHA);
        regional(88, "Alola", Type.VENENO, Type.SINIESTRO);
        regional(89, "Alola", Type.VENENO, Type.SINIESTRO);
        regional(100, "Hisui", Type.ELECTRICO, Type.PLANTA);
        regional(101, "Hisui", Type.ELECTRICO, Type.PLANTA);
        regional(103, "Alola", Type.PLANTA, Type.DRAGON);
        regional(105, "Alola", Type.FUEGO, Type.FANTASMA);
        regional(110, "Galar", Type.VENENO, Type.HADA);
        regional(122, "Galar", Type.HIELO, Type.PSIQUICO);
        regional(128, "Paldea (Forma Combatiente)", Type.LUCHA);
        regional(128, "Paldea (Forma Ardiente)", Type.LUCHA, Type.FUEGO);
        regional(128, "Paldea (Forma Acuática)", Type.LUCHA, Type.AGUA);
        regional(144, "Galar", Type.PSIQUICO, Type.VOLADOR);
        regional(145, "Galar", Type.LUCHA, Type.VOLADOR);
        regional(146, "Galar", Type.SINIESTRO, Type.VOLADOR);
        regional(157, "Hisui", Type.FUEGO, Type.FANTASMA);
        regional(194, "Paldea", Type.VENENO, Type.TIERRA);
        regional(199, "Galar", Type.VENENO, Type.PSIQUICO);
        regional(211, "Hisui", Type.SINIESTRO, Type.VENENO);
        regional(215, "Hisui", Type.LUCHA, Type.VENENO);
        regional(222, "Galar", Type.FANTASMA);
        regional(263, "Galar", Type.SINIESTRO, Type.NORMAL);
        regional(264, "Galar", Type.SINIESTRO, Type.NORMAL);
        regional(503, "Hisui", Type.AGUA, Type.SINIESTRO);
        regional(549, "Hisui", Type.PLANTA, Type.LUCHA);
        regional(554, "Galar", Type.HIELO);
        regional(555, "Galar", Type.HIELO);
        regional(562, "Galar", Type.TIERRA, Type.FANTASMA);
        regional(570, "Hisui", Type.NORMAL, Type.FANTASMA);
        regional(571, "Hisui", Type.NORMAL, Type.FANTASMA);
        regional(618, "Galar", Type.TIERRA, Type.ACERO);
        regional(628, "Hisui", Type.PSIQUICO, Type.VOLADOR);
        regional(705, "Hisui", Type.ACERO, Type.DRAGON);
        regional(706, "Hisui", Type.ACERO, Type.DRAGON);
        regional(713, "Hisui", Type.HIELO, Type.ROCA);
        regional(724, "Hisui", Type.PLANTA, Type.LUCHA);

        typeForm(413, "Tronco Planta", Type.BICHO, Type.PLANTA);
        typeForm(413, "Tronco Arena", Type.BICHO, Type.TIERRA);
        typeForm(413, "Tronco Basura", Type.BICHO, Type.ACERO);

        typeForm(479, "Rotom", Type.ELECTRICO, Type.FANTASMA);
        typeForm(479, "Rotom Calor", Type.ELECTRICO, Type.FUEGO);
        typeForm(479, "Rotom Lavado", Type.ELECTRICO, Type.AGUA);
        typeForm(479, "Rotom Frío", Type.ELECTRICO, Type.HIELO);
        typeForm(479, "Rotom Ventilador", Type.ELECTRICO, Type.VOLADOR);
        typeForm(479, "Rotom Corte", Type.ELECTRICO, Type.PLANTA);

        typeForm(492, "Forma Tierra", Type.PLANTA);
        typeForm(492, "Forma Cielo", Type.PLANTA, Type.VOLADOR);

        typeForm(720, "Contenido", Type.PSIQUICO, Type.FANTASMA);
        typeForm(720, "Desatado", Type.PSIQUICO, Type.SINIESTRO);

        typeForm(741, "Estilo Apasionado", Type.FUEGO, Type.VOLADOR);
        typeForm(741, "Estilo Animado", Type.ELECTRICO, Type.VOLADOR);
        typeForm(741, "Estilo Plácido", Type.PSIQUICO, Type.VOLADOR);
        typeForm(741, "Estilo Refinado", Type.FANTASMA, Type.VOLADOR);

        typeForm(892, "Estilo Brusco", Type.LUCHA, Type.SINIESTRO);
        typeForm(892, "Estilo Fluido", Type.LUCHA, Type.AGUA);
    }

    public String detect(PokemonRecord record, PokemonTypeDetector.Result types) {
        if (record.nationalNumber == 670
                && record.originMark.equals("Legends: Z-A")
                && normalize(record.ot).equals("AZ")
                && stripLeadingZeroes(record.trainerId).equals("1")) {
            return "Flor Eterna";
        }

        List<FormRule> typeForms = TYPE_FORM_RULES.get(record.nationalNumber);
        if (typeForms != null) {
            String detected = matchingForm(typeForms, types);
            if (detected != null) return detected;
            return "Revisar forma por tipos";
        }

        List<FormRule> regionalForms = REGIONAL_RULES.get(record.nationalNumber);
        if (regionalForms != null) {
            String detected = matchingForm(regionalForms, types);
            if (detected != null) return detected;
            if (types != null && types.reliable) return "Estándar";
            if (record.nationalNumber == 58
                    && record.originMark.equals("Hisui (Legends: Arceus)")) {
                return "Hisui";
            }
            return "Revisar forma regional";
        }
        if (VISUAL_FORMS.contains(record.nationalNumber)) return "Revisar forma visual";
        if (DATA_FORMS.contains(record.nationalNumber)) return "Revisar forma por datos";
        return "Estándar";
    }

    private static String matchingForm(
            List<FormRule> rules, PokemonTypeDetector.Result types
    ) {
        if (types == null || !types.reliable) return null;
        for (FormRule rule : rules) {
            if (types.types.equals(rule.types)) return rule.form;
        }
        return null;
    }

    private static void regional(int number, String form, Type... types) {
        addRule(REGIONAL_RULES, number, form, types);
    }

    private static void typeForm(int number, String form, Type... types) {
        addRule(TYPE_FORM_RULES, number, form, types);
    }

    private static void addRule(
            Map<Integer, List<FormRule>> target,
            int number,
            String form,
            Type... types
    ) {
        target.computeIfAbsent(number, ignored -> new ArrayList<>())
                .add(new FormRule(form, new HashSet<>(Arrays.asList(types))));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String stripLeadingZeroes(String value) {
        String stripped = value == null ? "" : value.replaceFirst("^0+(?!$)", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private static final class FormRule {
        final String form;
        final Set<Type> types;

        FormRule(String form, Set<Type> types) {
            this.form = form;
            this.types = types;
        }
    }
}
