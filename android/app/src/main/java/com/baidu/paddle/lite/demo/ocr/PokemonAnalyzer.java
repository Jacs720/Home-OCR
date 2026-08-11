package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.Collections;
import java.util.List;

public final class PokemonAnalyzer implements AutoCloseable {
    private final Context context;
    private final OcrEngine ocrEngine = new PaddleLiteOcrEngine();
    private final TemplateIconDetector iconDetector;
    private final PokemonParser parser;
    private final FormDetector formDetector = new FormDetector();
    private final PokemonTypeDetector typeDetector = new PokemonTypeDetector();
    private final PokemonStatsDetector statsDetector = new PokemonStatsDetector();
    private final VisualFormDetector visualFormDetector = new VisualFormDetector();
    private final UnownFormDetector unownFormDetector;

    public PokemonAnalyzer(Context context) {
        this.context = context.getApplicationContext();
        iconDetector = new TemplateIconDetector(this.context);
        SpeciesCatalog catalog = new SpeciesCatalog(this.context);
        parser = new PokemonParser(catalog);
        unownFormDetector = new UnownFormDetector(this.context);
    }

    public void initialize() {
        if (!ocrEngine.isReady()) ocrEngine.initialize(context);
    }

    public boolean isReady() {
        return ocrEngine.isReady();
    }

    public synchronized PokemonRecord analyze(Bitmap bitmap, String source) {
        if (!isReady()) throw new IllegalStateException("PaddleOCR no está inicializado.");

        TemplateIconDetector.Result ball = iconDetector.detectBall(bitmap);
        TemplateIconDetector.Result origin = iconDetector.detectOrigin(bitmap);
        TemplateIconDetector.PresenceResult shiny = iconDetector.detectShiny(bitmap);
        List<OcrResultModel> fullResults = ocrEngine.recognize(bitmap);

        List<OcrResultModel> numberResults = Collections.emptyList();
        if (!parser.hasValidNationalNumber(fullResults)) {
            numberResults = recognizeRegion(bitmap, 0.015f, 0.255f, 0.383f, 0.432f, 3f);
        }

        List<OcrResultModel> languageResults = Collections.emptyList();
        if (parser.extractLanguage(fullResults).equals("Revisar")) {
            languageResults = recognizeRegion(bitmap, 0.595f, 0.775f, 0.032f, 0.092f, 2f);
        }
        List<OcrResultModel> otResults = recognizeRegion(
                bitmap, 0.200f, 0.515f, 0.832f, 0.878f, 2f);

        PokemonRecord record = parser.parse(
                fullResults,
                numberResults,
                languageResults,
                otResults,
                bitmap.getWidth(),
                bitmap.getHeight(),
                ball,
                origin,
                shiny,
                source);
        PokemonTypeDetector.Result types = typeDetector.detect(bitmap);
        String form = formDetector.detect(record, types);
        if (record.nationalNumber == 201) {
            String visualForm = unownFormDetector.detect(bitmap);
            if (visualForm != null) form = visualForm;
        }
        String visualForm = visualFormDetector.detect(bitmap, record.nationalNumber);
        if (visualForm != null) form = visualForm;
        if (record.nationalNumber == 386) {
            String statsForm = statsDetector.detectDeoxys(
                    fullResults, bitmap.getWidth(), bitmap.getHeight());
            if (statsForm != null) form = statsForm;
        }
        return record.withForm(form);
    }

    private List<OcrResultModel> recognizeRegion(
            Bitmap image,
            float leftRatio,
            float rightRatio,
            float topRatio,
            float bottomRatio,
            float scale
    ) {
        int left = clamp(Math.round(image.getWidth() * leftRatio), 0, image.getWidth() - 1);
        int right = clamp(Math.round(image.getWidth() * rightRatio), left + 1, image.getWidth());
        int top = clamp(Math.round(image.getHeight() * topRatio), 0, image.getHeight() - 1);
        int bottom = clamp(Math.round(image.getHeight() * bottomRatio), top + 1, image.getHeight());
        Bitmap crop = Bitmap.createBitmap(image, left, top, right - left, bottom - top);
        Bitmap enlarged = Bitmap.createScaledBitmap(
                crop,
                Math.max(1, Math.round(crop.getWidth() * scale)),
                Math.max(1, Math.round(crop.getHeight() * scale)),
                true);
        crop.recycle();
        try {
            return ocrEngine.recognize(enlarged);
        } finally {
            enlarged.recycle();
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public void close() {
        ocrEngine.close();
    }
}
