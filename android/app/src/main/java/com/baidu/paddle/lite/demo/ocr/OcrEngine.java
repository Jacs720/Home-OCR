package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.List;

public interface OcrEngine extends AutoCloseable {
    void initialize(Context context);
    List<OcrResultModel> recognize(Bitmap bitmap);
    boolean isReady();
    @Override void close();
}
