package com.baidu.paddle.lite.demo.ocr;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Detecta los tipos por el color de los paneles, que no cambia con el idioma de HOME. */
public final class PokemonTypeDetector {
    public enum Type {
        NORMAL(153, 153, 153),
        LUCHA(255, 128, 0),
        VOLADOR(161, 198, 250),
        VENENO(145, 65, 203),
        TIERRA(164, 123, 67),
        ROCA(175, 169, 129),
        BICHO(145, 161, 25),
        FANTASMA(112, 65, 112),
        ACERO(96, 161, 184),
        FUEGO(230, 40, 41),
        AGUA(73, 145, 247),
        PLANTA(103, 189, 66),
        ELECTRICO(250, 192, 0),
        PSIQUICO(237, 108, 128),
        HIELO(63, 216, 255),
        DRAGON(80, 96, 225),
        SINIESTRO(80, 65, 63),
        HADA(239, 112, 239);

        final int red;
        final int green;
        final int blue;

        Type(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    public Result detect(Bitmap image) {
        Slot first = detectSlot(image, 0.552f, 0.759f, 0.397f, 0.426f);
        Slot second = detectSlot(image, 0.766f, 0.975f, 0.397f, 0.426f);
        EnumSet<Type> types = EnumSet.noneOf(Type.class);
        if (first.type != null) types.add(first.type);
        if (second.type != null) types.add(second.type);
        boolean reliable = first.type != null
                && first.confidence >= 0.50f
                && ((second.type != null && second.confidence >= 0.50f) || second.confidentlyEmpty);
        float confidence = first.confidence;
        if (second.type != null) confidence = Math.min(confidence, second.confidence);
        return new Result(types, reliable, confidence);
    }

    private static Slot detectSlot(
            Bitmap image,
            float leftRatio,
            float rightRatio,
            float topRatio,
            float bottomRatio
    ) {
        int left = clamp(Math.round(image.getWidth() * leftRatio), 0, image.getWidth() - 1);
        int right = clamp(Math.round(image.getWidth() * rightRatio), left + 1, image.getWidth());
        int top = clamp(Math.round(image.getHeight() * topRatio), 0, image.getHeight() - 1);
        int bottom = clamp(Math.round(image.getHeight() * bottomRatio), top + 1, image.getHeight());
        int[] counts = new int[4096];
        long[] redSums = new long[4096];
        long[] greenSums = new long[4096];
        long[] blueSums = new long[4096];
        int sampled = 0;
        int veryLight = 0;

        for (int y = top; y < bottom; y += 2) {
            for (int x = left; x < right; x += 2) {
                int color = image.getPixel(x, y);
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                sampled++;
                int maximum = Math.max(red, Math.max(green, blue));
                int minimum = Math.min(red, Math.min(green, blue));
                if (minimum > 218 && maximum - minimum < 28) {
                    veryLight++;
                    continue;
                }
                int bin = (red >> 4) << 8 | (green >> 4) << 4 | (blue >> 4);
                counts[bin]++;
                redSums[bin] += red;
                greenSums[bin] += green;
                blueSums[bin] += blue;
            }
        }

        int bestBin = 0;
        for (int index = 1; index < counts.length; index++) {
            if (counts[index] > counts[bestBin]) bestBin = index;
        }
        boolean confidentlyEmpty = sampled > 0 && veryLight >= sampled * 0.45f
                && counts[bestBin] < sampled * 0.08f;
        if (sampled == 0 || counts[bestBin] < sampled * 0.08f) {
            return new Slot(null, 0f, confidentlyEmpty);
        }

        int red = (int) (redSums[bestBin] / counts[bestBin]);
        int green = (int) (greenSums[bestBin] / counts[bestBin]);
        int blue = (int) (blueSums[bestBin] / counts[bestBin]);
        Type bestType = null;
        double bestDistance = Double.MAX_VALUE;
        for (Type type : Type.values()) {
            double distance = colorDistance(red, green, blue, type.red, type.green, type.blue);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestType = type;
            }
        }
        float confidence = Math.max(0f, 1f - (float) (bestDistance / 140.0));
        if (confidence < 0.40f) return new Slot(null, confidence, false);
        return new Slot(bestType, confidence, false);
    }

    private static double colorDistance(
            int redA, int greenA, int blueA,
            int redB, int greenB, int blueB
    ) {
        // El verde pesa algo más porque el ojo humano y los paneles de HOME lo separan mejor.
        int redMean = (redA + redB) / 2;
        int red = redA - redB;
        int green = greenA - greenB;
        int blue = blueA - blueB;
        return Math.sqrt((2 + redMean / 256.0) * red * red
                + 4.0 * green * green
                + (2 + (255 - redMean) / 256.0) * blue * blue) / 2.0;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Result {
        public final Set<Type> types;
        public final boolean reliable;
        public final float confidence;

        Result(Set<Type> types, boolean reliable, float confidence) {
            this.types = Collections.unmodifiableSet(EnumSet.copyOf(types));
            this.reliable = reliable;
            this.confidence = confidence;
        }

        public boolean exactly(Type... expected) {
            EnumSet<Type> wanted = EnumSet.noneOf(Type.class);
            Collections.addAll(wanted, expected);
            return types.equals(wanted);
        }
    }

    private static final class Slot {
        final Type type;
        final float confidence;
        final boolean confidentlyEmpty;

        Slot(Type type, float confidence, boolean confidentlyEmpty) {
            this.type = type;
            this.confidence = confidence;
            this.confidentlyEmpty = confidentlyEmpty;
        }
    }
}
