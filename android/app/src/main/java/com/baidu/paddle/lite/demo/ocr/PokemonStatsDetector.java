package com.baidu.paddle.lite.demo.ocr;

import android.graphics.Point;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee los seis valores mostrados por HOME para distinguir formas como Deoxys. */
public final class PokemonStatsDetector {
    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)");

    public String detectDeoxys(
            List<OcrResultModel> results, int imageWidth, int imageHeight
    ) {
        Integer hp = numberNear(results, imageWidth, imageHeight, 0.31f, 0.526f);
        Integer attack = numberNear(results, imageWidth, imageHeight, 0.17f, 0.568f);
        Integer defense = numberNear(results, imageWidth, imageHeight, 0.17f, 0.640f);
        Integer specialAttack = numberNear(results, imageWidth, imageHeight, 0.45f, 0.568f);
        Integer specialDefense = numberNear(results, imageWidth, imageHeight, 0.45f, 0.640f);
        Integer speed = numberNear(results, imageWidth, imageHeight, 0.31f, 0.675f);
        if (hp == null || attack == null || defense == null || specialAttack == null
                || specialDefense == null || speed == null) {
            return null;
        }
        return classifyDeoxys(attack, defense, specialAttack, specialDefense, speed);
    }

    static String classifyDeoxys(
            int attack, int defense, int specialAttack, int specialDefense, int speed
    ) {
        double offense = (attack + specialAttack) / 2.0;
        double guard = (defense + specialDefense) / 2.0;
        if (offense > guard * 1.60 && offense > speed * 1.15) {
            return "Forma Ataque";
        }
        if (guard > offense * 1.25 && guard > speed * 1.15) {
            return "Forma Defensa";
        }
        if (speed > Math.max(offense, guard) * 1.35) {
            return "Forma Velocidad";
        }
        return "Forma Normal";
    }

    private static Integer numberNear(
            List<OcrResultModel> results,
            int width,
            int height,
            float targetX,
            float targetY
    ) {
        Integer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (OcrResultModel result : results) {
            Matcher matcher = NUMBER.matcher(result.getLabel() == null ? "" : result.getLabel());
            if (!matcher.find() || result.getPoints() == null || result.getPoints().isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (value <= 0) continue;
            double centerX = 0;
            double centerY = 0;
            for (Point point : result.getPoints()) {
                centerX += point.x;
                centerY += point.y;
            }
            centerX /= result.getPoints().size();
            centerY /= result.getPoints().size();
            double dx = Math.abs(centerX / width - targetX) / 0.085;
            double dy = Math.abs(centerY / height - targetY) / 0.045;
            double distance = dx * dx + dy * dy;
            if (distance < 2.25 && distance < bestDistance) {
                bestDistance = distance;
                best = value;
            }
        }
        return best;
    }
}
