package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clasificador de silueta para las 28 formas de Unown, tolerante a giro y traslación. */
public final class UnownFormDetector {
    private static final int NORMALIZED_SIZE = 64;
    private static final int MIN_COMPONENT_PIXELS = 180;
    private final List<Reference> references = new ArrayList<>();

    public UnownFormDetector(Context context) {
        loadReferences(context);
    }

    public String detect(Bitmap screenshot) {
        if (references.isEmpty()) return null;
        Mask extracted = extractPokemon(screenshot);
        if (extracted == null) return null;
        boolean[] query = normalize(extracted);
        if (query == null) return null;

        String bestForm = null;
        double best = 0.0;
        double second = 0.0;
        for (Reference reference : references) {
            double score = overlap(query, reference.mask);
            if (score > best) {
                second = best;
                best = score;
                bestForm = reference.form;
            } else if (score > second) {
                second = score;
            }
        }
        if (bestForm == null || best < 0.62 || best - second < 0.018) return null;
        if (bestForm.equals("exclamation")) return "Forma !";
        if (bestForm.equals("question")) return "Forma ?";
        return "Forma " + bestForm.toUpperCase(Locale.ROOT);
    }

    private void loadReferences(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("unown_shape_templates.csv"), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                int comma = line.indexOf(',');
                if (comma <= 0) continue;
                String form = line.substring(0, comma);
                boolean[] mask = decodeRuns(line.substring(comma + 1));
                if (mask != null) references.add(new Reference(form, mask));
            }
        } catch (IOException ignored) {
            references.clear();
        }
    }

    private static boolean[] decodeRuns(String encoded) {
        boolean[] result = new boolean[NORMALIZED_SIZE * NORMALIZED_SIZE];
        String[] runs = encoded.split(";");
        int position = 0;
        boolean value = false;
        try {
            for (String run : runs) {
                int length = Integer.parseInt(run);
                if (length < 0 || position + length > result.length) return null;
                if (value) {
                    for (int index = position; index < position + length; index++) result[index] = true;
                }
                position += length;
                value = !value;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return position == result.length ? result : null;
    }

    private static Mask extractPokemon(Bitmap image) {
        int left = clamp(Math.round(image.getWidth() * 0.16f), 0, image.getWidth() - 1);
        int right = clamp(Math.round(image.getWidth() * 0.82f), left + 1, image.getWidth());
        int top = clamp(Math.round(image.getHeight() * 0.105f), 0, image.getHeight() - 1);
        int bottom = clamp(Math.round(image.getHeight() * 0.365f), top + 1, image.getHeight());
        int width = right - left;
        int height = bottom - top;
        int[] pixels = new int[width * height];
        image.getPixels(pixels, 0, width, left, top, width, height);

        boolean[] blue = new boolean[pixels.length];
        boolean[] normal = new boolean[pixels.length];
        int bluePixels = 0;
        for (int index = 0; index < pixels.length; index++) {
            int red = Color.red(pixels[index]);
            int green = Color.green(pixels[index]);
            int valueBlue = Color.blue(pixels[index]);
            int maximum = Math.max(red, Math.max(green, valueBlue));
            int minimum = Math.min(red, Math.min(green, valueBlue));
            boolean whiteEye = red > 165 && green > 165 && valueBlue > 165 && maximum - minimum < 45;
            boolean blueBody = valueBlue > green + 18 && green > red + 18 && green > 48;
            boolean normalBody = maximum - minimum < 48 && maximum < 165 && minimum > 18;
            blue[index] = blueBody || whiteEye;
            normal[index] = normalBody || whiteEye;
            if (blueBody) bluePixels++;
        }

        boolean useBlue = bluePixels >= MIN_COMPONENT_PIXELS;
        boolean[] component = largestComponent(useBlue ? blue : normal, width, height, !useBlue);
        if (count(component) < MIN_COMPONENT_PIXELS) return null;
        return new Mask(fillHoles(component, width, height), width, height);
    }

    private static boolean[] largestComponent(boolean[] source, int width, int height, boolean rejectBorder) {
        boolean[] seen = new boolean[source.length];
        int[] queue = new int[source.length];
        int[] best = new int[0];
        for (int start = 0; start < source.length; start++) {
            if (!source[start] || seen[start]) continue;
            int head = 0;
            int tail = 0;
            boolean touchesBorder = false;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                int position = queue[head++];
                int x = position % width;
                int y = position / width;
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touchesBorder = true;
                if (x > 0) tail = enqueue(position - 1, source, seen, queue, tail);
                if (x + 1 < width) tail = enqueue(position + 1, source, seen, queue, tail);
                if (y > 0) tail = enqueue(position - width, source, seen, queue, tail);
                if (y + 1 < height) tail = enqueue(position + width, source, seen, queue, tail);
            }
            if ((!rejectBorder || !touchesBorder) && tail > best.length) {
                best = new int[tail];
                System.arraycopy(queue, 0, best, 0, tail);
            }
        }
        boolean[] result = new boolean[source.length];
        for (int position : best) result[position] = true;
        return result;
    }

    private static int enqueue(
            int position, boolean[] source, boolean[] seen, int[] queue, int tail
    ) {
        if (source[position] && !seen[position]) {
            seen[position] = true;
            queue[tail++] = position;
        }
        return tail;
    }

    private static boolean[] fillHoles(boolean[] mask, int width, int height) {
        boolean[] outside = new boolean[mask.length];
        int[] queue = new int[mask.length];
        int head = 0;
        int tail = 0;
        for (int x = 0; x < width; x++) {
            tail = enqueueOutside(x, mask, outside, queue, tail);
            tail = enqueueOutside((height - 1) * width + x, mask, outside, queue, tail);
        }
        for (int y = 0; y < height; y++) {
            tail = enqueueOutside(y * width, mask, outside, queue, tail);
            tail = enqueueOutside(y * width + width - 1, mask, outside, queue, tail);
        }
        while (head < tail) {
            int position = queue[head++];
            int x = position % width;
            int y = position / width;
            if (x > 0) tail = enqueueOutside(position - 1, mask, outside, queue, tail);
            if (x + 1 < width) tail = enqueueOutside(position + 1, mask, outside, queue, tail);
            if (y > 0) tail = enqueueOutside(position - width, mask, outside, queue, tail);
            if (y + 1 < height) tail = enqueueOutside(position + width, mask, outside, queue, tail);
        }
        boolean[] result = new boolean[mask.length];
        for (int index = 0; index < result.length; index++) result[index] = mask[index] || !outside[index];
        return result;
    }

    private static int enqueueOutside(
            int position, boolean[] mask, boolean[] outside, int[] queue, int tail
    ) {
        if (!mask[position] && !outside[position]) {
            outside[position] = true;
            queue[tail++] = position;
        }
        return tail;
    }

    private static boolean[] normalize(Mask source) {
        int minX = source.width;
        int minY = source.height;
        int maxX = -1;
        int maxY = -1;
        for (int index = 0; index < source.values.length; index++) {
            if (!source.values[index]) continue;
            int x = index % source.width;
            int y = index / source.width;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        if (maxX < minX || maxY < minY) return null;
        int cropWidth = maxX - minX + 1;
        int cropHeight = maxY - minY + 1;
        int target = Math.round(NORMALIZED_SIZE * 0.78f);
        float scale = target / (float) Math.max(cropWidth, cropHeight);
        int scaledWidth = Math.max(1, Math.round(cropWidth * scale));
        int scaledHeight = Math.max(1, Math.round(cropHeight * scale));
        boolean[] result = new boolean[NORMALIZED_SIZE * NORMALIZED_SIZE];
        int offsetX = (NORMALIZED_SIZE - scaledWidth) / 2;
        int offsetY = (NORMALIZED_SIZE - scaledHeight) / 2;
        for (int y = 0; y < scaledHeight; y++) {
            int sourceY = minY + Math.min(cropHeight - 1, Math.round(y / scale));
            for (int x = 0; x < scaledWidth; x++) {
                int sourceX = minX + Math.min(cropWidth - 1, Math.round(x / scale));
                if (source.values[sourceY * source.width + sourceX]) {
                    result[(offsetY + y) * NORMALIZED_SIZE + offsetX + x] = true;
                }
            }
        }
        return result;
    }

    private static double overlap(boolean[] query, boolean[] reference) {
        double best = 0.0;
        boolean[] rotated = new boolean[query.length];
        double center = (NORMALIZED_SIZE - 1) / 2.0;
        for (int angle = -24; angle <= 24; angle += 4) {
            double radians = Math.toRadians(angle);
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            for (int y = 0; y < NORMALIZED_SIZE; y++) {
                for (int x = 0; x < NORMALIZED_SIZE; x++) {
                    double dx = x - center;
                    double dy = y - center;
                    int sourceX = (int) Math.round(cosine * dx + sine * dy + center);
                    int sourceY = (int) Math.round(-sine * dx + cosine * dy + center);
                    rotated[y * NORMALIZED_SIZE + x] = sourceX >= 0 && sourceX < NORMALIZED_SIZE
                            && sourceY >= 0 && sourceY < NORMALIZED_SIZE
                            && query[sourceY * NORMALIZED_SIZE + sourceX];
                }
            }
            for (int shiftY = -2; shiftY <= 2; shiftY++) {
                for (int shiftX = -2; shiftX <= 2; shiftX++) {
                    int intersection = 0;
                    int union = 0;
                    for (int y = 0; y < NORMALIZED_SIZE; y++) {
                        int queryY = y - shiftY;
                        for (int x = 0; x < NORMALIZED_SIZE; x++) {
                            int queryX = x - shiftX;
                            boolean left = queryX >= 0 && queryX < NORMALIZED_SIZE
                                    && queryY >= 0 && queryY < NORMALIZED_SIZE
                                    && rotated[queryY * NORMALIZED_SIZE + queryX];
                            boolean right = reference[y * NORMALIZED_SIZE + x];
                            if (left && right) intersection++;
                            if (left || right) union++;
                        }
                    }
                    if (union > 0) best = Math.max(best, intersection / (double) union);
                }
            }
        }
        return best;
    }

    private static int count(boolean[] values) {
        int total = 0;
        for (boolean value : values) if (value) total++;
        return total;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Mask {
        final boolean[] values;
        final int width;
        final int height;

        Mask(boolean[] values, int width, int height) {
            this.values = values;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Reference {
        final String form;
        final boolean[] mask;

        Reference(String form, boolean[] mask) {
            this.form = form;
            this.mask = mask;
        }
    }
}
