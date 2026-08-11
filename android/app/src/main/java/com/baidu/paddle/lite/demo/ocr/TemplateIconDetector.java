package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TemplateIconDetector {
    private static final int CANVAS = 64;
    private final AssetManager assets;

    public TemplateIconDetector(Context context) {
        assets = context.getApplicationContext().getAssets();
    }

    public Result detectBall(Bitmap image) {
        int side = Math.max(40, Math.round(image.getWidth() * 0.045f));
        int centerX = Math.round(image.getWidth() * 0.055f);
        int centerY = Math.round(image.getHeight() * 0.070f);
        Bitmap query = cropAndScale(image, centerX, centerY, side);
        return bestColorMatch(query, "templates/balls", 0.52f);
    }

    public Result detectOrigin(Bitmap image) {
        Bitmap query = normalizeOrigin(image);
        if (query == null) {
            return new Result("Sin marca (Gen 3-5)", 1f);
        }
        Result result = bestMaskMatch(query, "templates/origin_marks", 0.72f);
        query.recycle();
        return result;
    }

    public PresenceResult detectShiny(Bitmap image) {
        int left = Math.round(image.getWidth() * 0.120f);
        int right = Math.round(image.getWidth() * 0.176f);
        int top = Math.round(image.getHeight() * 0.447f);
        int bottom = Math.round(image.getHeight() * 0.488f);
        left = clamp(left, 0, image.getWidth() - 1);
        right = clamp(right, left + 1, image.getWidth());
        top = clamp(top, 0, image.getHeight() - 1);
        bottom = clamp(bottom, top + 1, image.getHeight());

        Bitmap crop = Bitmap.createBitmap(image, left, top, right - left, bottom - top);
        int[] source = pixels(crop);
        int width = crop.getWidth();
        int height = crop.getHeight();
        List<Integer> border = new ArrayList<>();
        int thickness = Math.min(4, Math.min(width, height) / 2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x < thickness || x >= width - thickness || y < thickness || y >= height - thickness) {
                    border.add(gray(source[y * width + x]));
                }
            }
        }
        int[] values = new int[border.size()];
        for (int i = 0; i < values.length; i++) values[i] = border.get(i);
        Arrays.sort(values);
        int background = values.length == 0 ? 255 : values[values.length / 2];

        int foreground = 0;
        for (int color : source) {
            int maximum = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
            int minimum = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
            boolean differentBrightness = gray(color) < background - 32;
            boolean colored = maximum - minimum > 38 && gray(color) < background - 12;
            if (differentBrightness || colored) foreground++;
        }
        crop.recycle();
        int minimumPixels = Math.max(18, Math.round(source.length * 0.004f));
        float score = Math.min(1f, foreground / (float) Math.max(minimumPixels * 4, 1));
        return new PresenceResult(foreground >= minimumPixels, score, foreground);
    }

    private Result bestColorMatch(Bitmap query, String directory, float minimum) {
        Result best = new Result("Revisar", -1f);
        for (String file : listPng(directory)) {
            Bitmap template = loadBitmap(directory + "/" + file);
            if (template == null) continue;
            template = Bitmap.createScaledBitmap(template, CANVAS, CANVAS, true);
            float score = colorScore(query, template);
            if (score > best.score) {
                best = new Result(ballLabel(file), score);
            }
        }
        return best.score >= minimum ? best : new Result("Revisar", best.score);
    }

    private Result bestMaskMatch(Bitmap query, String directory, float minimum) {
        Result best = new Result("Revisar", -1f);
        float secondBest = -1f;
        for (String file : listPng(directory)) {
            if (file.startsWith("Sin_marca")) continue;
            Bitmap template = loadBitmap(directory + "/" + file);
            if (template == null) continue;
            Bitmap normalized = normalizeDarkObject(template);
            template.recycle();
            template = normalized;
            if (template == null) continue;
            float score = maskScore(query, template);
            if (score > best.score) {
                secondBest = best.score;
                best = new Result(originLabel(file), score);
            } else if (score > secondBest) {
                secondBest = score;
            }
            template.recycle();
        }
        boolean separated = secondBest < 0f || best.score - secondBest >= 0.05f;
        return best.score >= minimum && separated ? best : new Result("Revisar", best.score);
    }

    private float colorScore(Bitmap query, Bitmap template) {
        int[] q = pixels(query);
        int[] t = pixels(template);
        boolean inferred = isOpaque(t);
        int corner = averageCorners(t);
        float best = -1f;

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                long difference = 0;
                int compared = 0;
                for (int y = 0; y < CANVAS; y++) {
                    int ty = y - dy;
                    if (ty < 0 || ty >= CANVAS) continue;
                    for (int x = 0; x < CANVAS; x++) {
                        int tx = x - dx;
                        if (tx < 0 || tx >= CANVAS) continue;
                        int templatePixel = t[ty * CANVAS + tx];
                        int alpha = Color.alpha(templatePixel);
                        if (inferred && rgbDistance(templatePixel, corner) < 28) alpha = 0;
                        if (alpha < 32) continue;
                        int queryPixel = q[y * CANVAS + x];
                        difference += Math.abs(Color.red(queryPixel) - Color.red(templatePixel));
                        difference += Math.abs(Color.green(queryPixel) - Color.green(templatePixel));
                        difference += Math.abs(Color.blue(queryPixel) - Color.blue(templatePixel));
                        compared += 3;
                    }
                }
                if (compared > 120) {
                    best = Math.max(best, 1f - difference / (255f * compared));
                }
            }
        }
        return best;
    }

    private float maskScore(Bitmap query, Bitmap template) {
        int[] q = pixels(query);
        int[] t = pixels(template);
        float best = -1f;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int intersection = 0;
                int union = 0;
                for (int y = 0; y < CANVAS; y++) {
                    for (int x = 0; x < CANVAS; x++) {
                        boolean qm = Color.alpha(q[y * CANVAS + x]) > 32;
                        int tx = x - dx;
                        int ty = y - dy;
                        boolean tm = tx >= 0 && tx < CANVAS && ty >= 0 && ty < CANVAS
                                && Color.alpha(t[ty * CANVAS + tx]) > 32;
                        if (qm && tm) intersection++;
                        if (qm || tm) union++;
                    }
                }
                if (union > 0) best = Math.max(best, intersection / (float) union);
            }
        }
        return best;
    }

    private Bitmap normalizeOrigin(Bitmap image) {
        int left = Math.round(image.getWidth() * 0.177f);
        int right = Math.round(image.getWidth() * 0.255f);
        int top = Math.round(image.getHeight() * 0.451f);
        int bottom = Math.round(image.getHeight() * 0.488f);
        left = clamp(left, 0, image.getWidth() - 1);
        right = clamp(right, left + 1, image.getWidth());
        top = clamp(top, 0, image.getHeight() - 1);
        bottom = clamp(bottom, top + 1, image.getHeight());
        Bitmap crop = Bitmap.createBitmap(image, left, top, right - left, bottom - top);
        Bitmap normalized = normalizeDarkObject(crop);
        crop.recycle();
        return normalized;
    }

    private Bitmap normalizeDarkObject(Bitmap crop) {
        int width = crop.getWidth();
        int height = crop.getHeight();
        int[] source = pixels(crop);

        List<Integer> border = new ArrayList<>();
        int thickness = Math.min(4, Math.min(width, height) / 2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x < thickness || x >= width - thickness || y < thickness || y >= height - thickness) {
                    border.add(gray(source[y * width + x]));
                }
            }
        }
        int[] values = new int[border.size()];
        for (int i = 0; i < values.length; i++) values[i] = border.get(i);
        Arrays.sort(values);
        int background = values.length == 0 ? 255 : values[values.length / 2];

        int minX = width, minY = height, maxX = -1, maxY = -1, dark = 0;
        boolean[] mask = new boolean[source.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (gray(source[index]) < background - 35) {
                    mask[index] = true;
                    dark++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (dark < 12 || maxX < minX || maxY < minY) return null;

        int objectWidth = maxX - minX + 1;
        int objectHeight = maxY - minY + 1;
        float scale = Math.min(48f / objectWidth, 48f / objectHeight);
        int outWidth = Math.max(1, Math.round(objectWidth * scale));
        int outHeight = Math.max(1, Math.round(objectHeight * scale));
        Bitmap object = Bitmap.createBitmap(objectWidth, objectHeight, Bitmap.Config.ARGB_8888);
        int[] objectPixels = new int[objectWidth * objectHeight];
        for (int y = 0; y < objectHeight; y++) {
            for (int x = 0; x < objectWidth; x++) {
                int sourceIndex = (minY + y) * width + minX + x;
                int color = source[sourceIndex];
                objectPixels[y * objectWidth + x] = mask[sourceIndex]
                        ? Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))
                        : Color.TRANSPARENT;
            }
        }
        object.setPixels(objectPixels, 0, objectWidth, 0, 0, objectWidth, objectHeight);
        Bitmap scaled = Bitmap.createScaledBitmap(object, outWidth, outHeight, true);
        Bitmap canvas = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas draw = new android.graphics.Canvas(canvas);
        draw.drawBitmap(scaled, (CANVAS - outWidth) / 2f, (CANVAS - outHeight) / 2f, null);
        object.recycle();
        scaled.recycle();
        return canvas;
    }

    private Bitmap cropAndScale(Bitmap image, int centerX, int centerY, int side) {
        int left = clamp(centerX - side / 2, 0, Math.max(0, image.getWidth() - side));
        int top = clamp(centerY - side / 2, 0, Math.max(0, image.getHeight() - side));
        int width = Math.min(side, image.getWidth() - left);
        int height = Math.min(side, image.getHeight() - top);
        Bitmap crop = Bitmap.createBitmap(image, left, top, width, height);
        return Bitmap.createScaledBitmap(crop, CANVAS, CANVAS, true);
    }

    private List<String> listPng(String directory) {
        try {
            String[] names = assets.list(directory);
            if (names == null) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (String name : names) if (name.toLowerCase(Locale.ROOT).endsWith(".png")) result.add(name);
            return result;
        } catch (IOException exception) {
            return Collections.emptyList();
        }
    }

    private Bitmap loadBitmap(String path) {
        try (InputStream stream = assets.open(path)) {
            return BitmapFactory.decodeStream(stream);
        } catch (IOException exception) {
            return null;
        }
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }

    private static boolean isOpaque(int[] pixels) {
        for (int pixel : pixels) if (Color.alpha(pixel) < 250) return false;
        return true;
    }

    private static int averageCorners(int[] pixels) {
        int[] indexes = {0, CANVAS - 1, (CANVAS - 1) * CANVAS, CANVAS * CANVAS - 1};
        int r = 0, g = 0, b = 0;
        for (int index : indexes) {
            r += Color.red(pixels[index]);
            g += Color.green(pixels[index]);
            b += Color.blue(pixels[index]);
        }
        return Color.rgb(r / 4, g / 4, b / 4);
    }

    private static int rgbDistance(int a, int b) {
        return (Math.abs(Color.red(a) - Color.red(b))
                + Math.abs(Color.green(a) - Color.green(b))
                + Math.abs(Color.blue(a) - Color.blue(b))) / 3;
    }

    private static int gray(int color) {
        return Math.round(0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String ballLabel(String filename) {
        String stem = filename.substring(0, filename.length() - 4).split("__", 2)[0];
        return stem.replace('_', ' ');
    }

    private static String originLabel(String filename) {
        String stem = filename.substring(0, filename.length() - 4);
        switch (stem) {
            case "BDSP": return "Sinnoh (BDSP)";
            case "Consola_Virtual": return "Consola Virtual";
            case "Legends_Arceus": return "Hisui (Legends: Arceus)";
            case "Legends_ZA": return "Legends: Z-A";
            case "Lets_GO": return "Let's Go";
            case "Pokemon_GO": return "Pokémon GO";
            default: return stem.replace('_', ' ');
        }
    }

    public static final class Result {
        public final String label;
        public final float score;

        public Result(String label, float score) {
            this.label = label;
            this.score = score;
        }
    }

    public static final class PresenceResult {
        public final boolean present;
        public final float score;
        public final int foregroundPixels;

        public PresenceResult(boolean present, float score, int foregroundPixels) {
            this.present = present;
            this.score = score;
            this.foregroundPixels = foregroundPixels;
        }
    }
}
