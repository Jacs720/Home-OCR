package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpeciesCatalog {
    private static final String ASSET = "pokemon_species_names.csv";
    private static final String SPANISH_LANGUAGE_ID = "7";
    private final Map<Integer, String> names = new HashMap<>();

    public SpeciesCatalog(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(ASSET), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                List<String> columns = parseCsvLine(line);
                if (columns.size() < 3 || !SPANISH_LANGUAGE_ID.equals(columns.get(1))) continue;
                try {
                    names.put(Integer.parseInt(columns.get(0)), columns.get(2));
                } catch (NumberFormatException ignored) {
                    // Ignora filas incompletas sin invalidar el catálogo entero.
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar la Pokédex local.", exception);
        }
    }

    public String nameFor(int nationalNumber) {
        return names.getOrDefault(nationalNumber, "Revisar");
    }

    public boolean contains(int nationalNumber) {
        return names.containsKey(nationalNumber);
    }

    public int size() {
        return names.size();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        result.add(value.toString());
        return result;
    }
}
