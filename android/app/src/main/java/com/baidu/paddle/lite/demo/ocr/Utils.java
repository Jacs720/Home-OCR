package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Utils {
    private Utils() {}

    public static void copyDirectoryFromAssets(Context context, String source, String destination) {
        try {
            copyAsset(context, source, new File(destination));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron preparar los modelos OCR.", exception);
        }
    }

    private static void copyAsset(Context context, String source, File destination) throws IOException {
        String[] children = context.getAssets().list(source);
        if (children != null && children.length > 0) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("No se pudo crear " + destination);
            }
            for (String child : children) {
                copyAsset(context, source + "/" + child, new File(destination, child));
            }
            return;
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent);
        }
        try (InputStream input = new BufferedInputStream(context.getAssets().open(source));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }
    }
}
