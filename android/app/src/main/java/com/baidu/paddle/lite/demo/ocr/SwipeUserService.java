package com.baidu.paddle.lite.demo.ocr;

import android.os.Parcel;
import android.os.RemoteException;

/** Runs with Shizuku's shell identity and exposes only the gestures used by the scanner. */
public final class SwipeUserService extends ISwipeUserService.Stub {
    private static final int USER_SERVICE_DESTROY = 16777114;

    public SwipeUserService() {
    }

    @Override
    public boolean swipe(int screenWidth, int screenHeight) {
        int startX = Math.round(screenWidth * 0.24f);
        int endX = Math.round(screenWidth * 0.78f);
        int y = Math.round(screenHeight * 0.52f);
        return performSwipe(startX, y, endX, y, 280);
    }

    @Override
    public boolean scrollDown(int screenWidth, int screenHeight) {
        int x = Math.round(screenWidth * 0.50f);
        return performSwipe(
                x, Math.round(screenHeight * 0.78f),
                x, Math.round(screenHeight * 0.28f),
                360);
    }

    @Override
    public boolean scrollUp(int screenWidth, int screenHeight) {
        int x = Math.round(screenWidth * 0.50f);
        return performSwipe(
                x, Math.round(screenHeight * 0.28f),
                x, Math.round(screenHeight * 0.78f),
                360);
    }

    private boolean performSwipe(
            int startX,
            int startY,
            int endX,
            int endY,
            int duration
    ) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "/system/bin/input", "swipe",
                    Integer.toString(startX), Integer.toString(startY),
                    Integer.toString(endX), Integer.toString(endY),
                    Integer.toString(duration))
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == USER_SERVICE_DESTROY) {
            System.exit(0);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
}
