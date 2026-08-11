package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.List;

public final class PaddleLiteOcrEngine implements OcrEngine {
    private static final String MODEL_PATH = "models/ch_PP-OCRv2";
    private static final String LABEL_PATH = "labels/ppocr_keys_v1.txt";
    private final Predictor predictor = new Predictor();

    @Override
    public void initialize(Context context) {
        if (!predictor.init(
                context.getApplicationContext(),
                MODEL_PATH,
                LABEL_PATH,
                0,
                4,
                "LITE_POWER_HIGH",
                1280,
                0.45f)) {
            throw new IllegalStateException("No se pudo cargar PaddleOCR.");
        }
    }

    @Override
    public List<OcrResultModel> recognize(Bitmap bitmap) {
        if (!isReady()) {
            throw new IllegalStateException("PaddleOCR no está inicializado.");
        }
        predictor.setInputImage(bitmap);
        if (!predictor.runModel(1, 0, 1)) {
            throw new IllegalStateException("PaddleOCR no pudo procesar la captura.");
        }
        return predictor.results();
    }

    @Override
    public boolean isReady() {
        return predictor.isLoaded();
    }

    @Override
    public void close() {
        predictor.releaseModel();
    }
}
