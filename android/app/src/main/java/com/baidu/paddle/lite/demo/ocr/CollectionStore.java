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
        String escapedTail = ",\"" + source.replace("\"", "\"\"") + "\"";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(collectionFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith(escapedTail)) return true;
            }
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

    public synchronized void exportTo(Uri target) throws IOException {
        if (!collectionFile.exists()) {
            throw new IOException("La colección está vacía.");
        }
        try (FileInputStream input = new FileInputStream(collectionFile);
             OutputStream output = context.getContentResolver().openOutputStream(target, "wt")) {
            if (output == null) {
                throw new IOException("No se pudo abrir el destino.");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
