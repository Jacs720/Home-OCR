package com.baidu.paddle.lite.demo.ocr;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/** Conecta el capturador con un UserService mínimo de Shizuku. */
public final class ShizukuSwipeController implements AutoCloseable {
    private final Shizuku.UserServiceArgs serviceArgs;
    private volatile ISwipeUserService remoteService;
    private volatile CountDownLatch binding;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remoteService = ISwipeUserService.Stub.asInterface(service);
            CountDownLatch latch = binding;
            if (latch != null) latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remoteService = null;
        }
    };

    public ShizukuSwipeController(Context context) {
        serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(context, SwipeUserService.class))
                .processNameSuffix("pokemon_swipe")
                .tag("pokemon_home_swipe")
                .version(2)
                .daemon(false);
    }

    public boolean isReady() {
        try {
            return Shizuku.pingBinder()
                    && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean swipeRight(int screenWidth, int screenHeight) {
        if (!isReady() || !bindIfNecessary()) return false;
        try {
            return remoteService.swipe(screenWidth, screenHeight);
        } catch (RemoteException | RuntimeException exception) {
            remoteService = null;
            return false;
        }
    }

    public boolean scrollDown(int screenWidth, int screenHeight) {
        if (!isReady() || !bindIfNecessary()) return false;
        try {
            return remoteService.scrollDown(screenWidth, screenHeight);
        } catch (RemoteException | RuntimeException exception) {
            remoteService = null;
            return false;
        }
    }

    public boolean scrollUp(int screenWidth, int screenHeight) {
        if (!isReady() || !bindIfNecessary()) return false;
        try {
            return remoteService.scrollUp(screenWidth, screenHeight);
        } catch (RemoteException | RuntimeException exception) {
            remoteService = null;
            return false;
        }
    }

    private boolean bindIfNecessary() {
        ISwipeUserService current = remoteService;
        if (current != null && current.asBinder().pingBinder()) return true;
        CountDownLatch latch = new CountDownLatch(1);
        binding = latch;
        try {
            Shizuku.bindUserService(serviceArgs, connection);
            return latch.await(4, TimeUnit.SECONDS)
                    && remoteService != null
                    && remoteService.asBinder().pingBinder();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            binding = null;
        }
    }

    @Override
    public void close() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.unbindUserService(serviceArgs, connection, true);
            }
        } catch (RuntimeException ignored) {
            // Shizuku puede haberse detenido antes que el servicio de captura.
        } finally {
            remoteService = null;
        }
    }
}
