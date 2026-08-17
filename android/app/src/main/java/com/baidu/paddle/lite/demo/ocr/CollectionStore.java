package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CollectionStore {
    private static final String FILE_NAME = "pokemon_collection.csv";
    private final Context context;
    private final File collectionFile;

    public CollectionStore(Context context) {
        this.context = context.getApplicationContext();
        this.collectionFile = new File(context.getFilesDir(), FILE_NAME);
    }

    public synchronized boolean append(PokemonRecord record) throws IOException {
        ensureCurrentSchema();
        if (containsSource(record.source)) {
            return false;
        }
        boolean writeHeader = !collectionFile.exists() || collectionFile.length() == 0;
        try (FileOutputStream output = new FileOutputStream(collectionFile, true)) {
            if (writeHeader) {
                output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                output.write((PokemonRecord.CSV_HEADER + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            output.write((record.toCsvRow() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return true;
    }

    private boolean containsSource(String source) throws IOException {
        if (!collectionFile.exists()) return false;
        List<List<String>> rows = ChecklistCsv.parse(readCsvText());
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() > 10 && source.equals(row.get(10))) return true;
        }
        return false;
    }

    public synchronized int count() {
        if (!collectionFile.exists()) {
            return 0;
        }
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(collectionFile), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                lines++;
            }
        } catch (IOException ignored) {
            return 0;
        }
        return Math.max(0, lines - 1);
    }

    public synchronized List<String> recentRows(int maximum) {
        List<String> rows = new ArrayList<>();
        if (!collectionFile.exists()) {
            return rows;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(collectionFile), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                rows.add(line);
            }
        } catch (IOException ignored) {
            return rows;
        }
        int from = Math.max(0, rows.size() - maximum);
        return new ArrayList<>(rows.subList(from, rows.size()));
    }

    public synchronized String readCsvText() throws IOException {
        if (!collectionFile.exists()) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(collectionFile), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) text.append(buffer, 0, read);
        }
        return text.toString();
    }

    public synchronized void clear() throws IOException {
        if (collectionFile.exists() && !collectionFile.delete()) {
            throw new IOException("No se pudo borrar la colección local.");
        }
    }

    public synchronized void exportTo(Uri target) throws IOException {
        if (!collectionFile.exists()) {
            throw new IOException("La colección está vacía.");
        }
        ensureCurrentSchema();
        List<List<String>> rows = ChecklistCsv.parse(readCsvText());
        SpeciesCatalog catalog = new SpeciesCatalog(context);
        try (OutputStream output = context.getContentResolver().openOutputStream(target, "wt")) {
            if (output == null) {
                throw new IOException("No se pudo abrir el destino.");
            }
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            output.write((context.getString(R.string.csv_header) + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            for (int index = 1; index < rows.size(); index++) {
                List<String> row = new ArrayList<>(rows.get(index));
                if (row.size() < 2) continue;
                int number = ChecklistRepository.parseNumber(row.get(0));
                if (number > 0) row.set(1, catalog.nameFor(number));
                localizeBoolean(row, 4);
                localizeBoolean(row, 12);
                localizeBoolean(row, 13);
                output.write((ChecklistCsv.serializeRow(row) + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void localizeBoolean(List<String> row, int index) {
        if (index >= row.size()) return;
        boolean truthy = ChecklistRepository.truthy(row.get(index));
        row.set(index, context.getString(truthy ? R.string.csv_yes : R.string.csv_no));
    }

    private void ensureCurrentSchema() throws IOException {
        if (!collectionFile.exists() || collectionFile.length() == 0) return;
        List<List<String>> rows = ChecklistCsv.parse(readCsvText());
        if (rows.isEmpty() || rows.get(0).size() >= 14) return;

        File migrated = new File(collectionFile.getParentFile(), FILE_NAME + ".migrating");
        try (FileOutputStream output = new FileOutputStream(migrated, false)) {
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            output.write((PokemonRecord.CSV_HEADER + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (int index = 1; index < rows.size(); index++) {
                List<String> row = new ArrayList<>(rows.get(index));
                while (row.size() < 11) row.add("");
                row.addAll(Arrays.asList("", "No", "No"));
                output.write((ChecklistCsv.serializeRow(row) + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
        File backup = new File(collectionFile.getParentFile(), FILE_NAME + ".backup");
        if (backup.exists() && !backup.delete()) {
            migrated.delete();
            throw new IOException("No se pudo actualizar el formato de la colección.");
        }
        if (!collectionFile.renameTo(backup)) {
            migrated.delete();
            throw new IOException("No se pudo respaldar la colección antes de actualizarla.");
        }
        if (!migrated.renameTo(collectionFile)) {
            backup.renameTo(collectionFile);
            throw new IOException("No se pudo activar el formato actualizado de la colección.");
        }
        backup.delete();
    }
}
