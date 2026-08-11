package com.baidu.paddle.lite.demo.ocr;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Clasificadores visuales calibrados sobre la zona fija del modelo 3D en HOME. */
public final class VisualFormDetector {
    public String detect(Bitmap image, int nationalNumber) {
        switch (nationalNumber) {
            case 422:
            case 423:
                return detectSea(image);
            case 550:
                return detectBasculinStripe(image);
            case 585:
                return detectDeerlingSeason(image);
            default:
                return null;
        }
    }

    private static String detectSea(Bitmap image) {
        Region region = Region.of(image, 0.35f, 0.65f, 0.20f, 0.35f);
        int sampled = 0;
        int westPixels = 0;
        float[] hsv = new float[3];
        for (int y = region.top; y < region.bottom; y += 2) {
            for (int x = region.left; x < region.right; x += 2) {
                Color.colorToHSV(image.getPixel(x, y), hsv);
                sampled++;
                if (hsv[1] >= 0.28f && hsv[2] >= 0.22f
                        && (hsv[0] <= 30f || hsv[0] >= 330f)) {
                    westPixels++;
                }
            }
        }
        if (sampled == 0) return null;
        return westPixels / (float) sampled >= 0.020f ? "Mar Oeste" : "Mar Este";
    }

    private static String detectBasculinStripe(Bitmap image) {
        Region region = Region.of(image, 0.42f, 0.56f, 0.22f, 0.29f);
        int sampled = 0;
        int red = 0;
        int darkBlue = 0;
        float[] hsv = new float[3];
        for (int y = region.top; y < region.bottom; y += 2) {
            for (int x = region.left; x < region.right; x += 2) {
                Color.colorToHSV(image.getPixel(x, y), hsv);
                sampled++;
                if (hsv[1] > 0.35f && (hsv[0] <= 25f || hsv[0] >= 335f)) red++;
                if (hsv[1] > 0.35f && hsv[0] >= 180f && hsv[0] <= 250f
                        && hsv[2] < 0.65f) {
                    darkBlue++;
                }
            }
        }
        if (sampled == 0) return null;
        if (red / (float) sampled >= 0.010f) return "Raya Roja";
        if (darkBlue / (float) sampled >= 0.010f) return "Raya Azul";
        return "Raya Blanca";
    }

    private static String detectDeerlingSeason(Bitmap image) {
        Region region = Region.of(image, 0.42f, 0.54f, 0.275f, 0.32f);
        double red = 0;
        double green = 0;
        double blue = 0;
        int count = 0;
        float[] hsv = new float[3];
        for (int y = region.top; y < region.bottom; y += 2) {
            for (int x = region.left; x < region.right; x += 2) {
                int color = image.getPixel(x, y);
                Color.colorToHSV(color, hsv);
                if (hsv[1] < 0.12f || hsv[2] >= 0.90f) continue;
                red += Color.red(color) / 255.0;
                green += Color.green(color) / 255.0;
                blue += Color.blue(color) / 255.0;
                count++;
            }
        }
        if (count < 20) return null;
        return classifySeason(red / count, green / count, blue / count);
    }

    static String classifySeason(double red, double green, double blue) {
        if (green - red > 0.020) return "Verano";
        if (red - green >= 0.070) {
            return blue >= green - 0.075 ? "Primavera" : "Otoño";
        }
        return "Invierno";
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Region {
        final int left;
        final int right;
        final int top;
        final int bottom;

        Region(int left, int right, int top, int bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        static Region of(
                Bitmap image, float left, float right, float top, float bottom
        ) {
            int x1 = clamp(Math.round(image.getWidth() * left), 0, image.getWidth() - 1);
            int x2 = clamp(Math.round(image.getWidth() * right), x1 + 1, image.getWidth());
            int y1 = clamp(Math.round(image.getHeight() * top), 0, image.getHeight() - 1);
            int y2 = clamp(Math.round(image.getHeight() * bottom), y1 + 1, image.getHeight());
            return new Region(x1, x2, y1, y2);
        }
    }
}
