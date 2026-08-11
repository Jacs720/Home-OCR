package com.baidu.paddle.lite.demo.ocr;

import android.os.Parcel;
import android.os.RemoteException;

/** Se ejecuta con identidad shell dentro de Shizuku y solo expone un gesto horizontal. */
public final class SwipeUserService extends ISwipeUserService.Stub {
    private static final int USER_SERVICE_DESTROY = 16777114;

    public SwipeUserService() {
    }

    @Override
    public boolean swipe(int screenWidth, int screenHeight) {
        int startX = Math.round(screenWidth * 0.24f);
        int endX = Math.round(screenWidth * 0.78f);
        int y = Math.round(screenHeight * 0.52f);
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "/system/bin/input", "swipe",
                    Integer.toString(startX), Integer.toString(y),
                    Integer.toString(endX), Integer.toString(y),
                    "280")
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
