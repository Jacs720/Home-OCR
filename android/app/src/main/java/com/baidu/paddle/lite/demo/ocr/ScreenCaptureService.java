package com.baidu.paddle.lite.demo.ocr;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.appcompat.widget.AppCompatTextView;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreenCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "projection_result_code";
    public static final String EXTRA_RESULT_DATA = "projection_result_data";
    public static final String EXTRA_LIMIT = "scan_limit";
    public static final String EXTRA_AUTO_SWIPE = "auto_swipe";
    public static final String EXTRA_INSPECT_ORRE = "inspect_orre";
    private static final String ACTION_STOP = "com.jacar.pokemonhomeocr.STOP_CAPTURE";
    private static final String CHANNEL_ID = "pokemon_home_capture";
    private static final int NOTIFICATION_ID = 468;
    private static volatile boolean running;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzerWorker = Executors.newSingleThreadExecutor();
    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private LinearLayout overlayView;
    private DragHandleView overlayStatus;
    private Button captureButton;
    private PokemonAnalyzer analyzer;
    private ShizukuSwipeController swipeController;
    private CollectionStore collectionStore;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private int limit = 1;
    private int capturedCount;
    private boolean autoSwipeRequested;
    private boolean inspectOrreRequested;
    private volatile boolean autoSwipeActive;
    private volatile boolean inspectOrreActive;
    private volatile boolean analyzerReady;
    private volatile boolean captureRequested;
    private volatile boolean processing;
    private volatile boolean detailCapture;
    private volatile int captureSequence;
    private PokemonRecord pendingRecord;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        collectionStore = new CollectionStore(this);
        analyzer = new PokemonAnalyzer(this);
        swipeController = new ShizukuSwipeController(this);
        captureThread = new HandlerThread("pokemon-screen-capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        limit = Math.max(1, intent.getIntExtra(EXTRA_LIMIT, 1));
        autoSwipeRequested = intent.getBooleanExtra(EXTRA_AUTO_SWIPE, false);
        inspectOrreRequested = intent.getBooleanExtra(EXTRA_INSPECT_ORRE, false);
        autoSwipeActive = autoSwipeRequested && swipeController.isReady();
        inspectOrreActive = inspectOrreRequested && swipeController.isReady();
        startCaptureForeground();
        if (overlayView == null) createOverlay();
        if (mediaProjection == null) startProjection(intent);

        analyzerWorker.execute(() -> {
            try {
                analyzer.initialize();
                analyzerReady = true;
                mainHandler.post(() -> {
                    if (autoSwipeActive && inspectOrreActive) {
                        setStatus(getString(R.string.overlay_automatic_orre_ready,
                                capturedCount, limit));
                    } else if (autoSwipeActive) {
                        setStatus(getString(R.string.overlay_automatic_ready,
                                capturedCount, limit));
                    } else if (autoSwipeRequested || inspectOrreRequested) {
                        setStatus(getString(R.string.overlay_shizuku_unavailable));
                    } else {
                        setStatus(getString(R.string.overlay_ready, capturedCount, limit));
                    }
                    updateButtons();
                    if (autoSwipeActive) mainHandler.postDelayed(this::requestCapture, 900);
                });
            } catch (RuntimeException exception) {
                mainHandler.post(() -> setStatus(getString(
                        R.string.overlay_ocr_error, exception.getMessage())));
            }
        });
        return START_NOT_STICKY;
    }

    private void startCaptureForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.capture_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.capture_channel_description));
            manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, ScreenCaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = createNotificationBuilder();
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(getString(R.string.capture_notification_title))
                .setContentText(getString(R.string.capture_notification_text))
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        null, getString(R.string.stop_capture), stopPending).build())
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void startProjection(Intent intent) {
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData = projectionResultData(intent);
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            setStatus(getString(R.string.overlay_permission_cancelled));
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        readScreenMetrics();

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, mainHandler);

        imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "PokemonHomeOCR",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler);
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new LinearLayout(this);
        overlayView.setOrientation(LinearLayout.VERTICAL);
        overlayView.setGravity(Gravity.CENTER);
        int padding = dp(10);
        overlayView.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF012213A);
        background.setStroke(dp(1), 0x5576C7BF);
        background.setCornerRadius(dp(20));
        overlayView.setBackground(background);
        overlayView.setElevation(dp(8));

        overlayStatus = new DragHandleView(this);
        overlayStatus.setTextColor(Color.WHITE);
        overlayStatus.setGravity(Gravity.CENTER);
        overlayStatus.setText(R.string.loading_model);
        overlayStatus.setTextSize(13);
        overlayStatus.setPadding(dp(8), dp(6), dp(8), dp(8));
        overlayView.addView(overlayStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        captureButton = new Button(this);
        captureButton.setText(R.string.capture_now);
        captureButton.setAllCaps(false);
        captureButton.setTextColor(0xFF12213A);
        captureButton.setBackgroundTintList(ColorStateList.valueOf(0xFFF5C542));
        captureButton.setEnabled(false);
        captureButton.setOnClickListener(view -> requestCapture());
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(
                dp(190), dp(48));
        captureParams.topMargin = dp(2);
        overlayView.addView(captureButton, captureParams);

        Button closeButton = new Button(this);
        closeButton.setText(R.string.stop_capture);
        closeButton.setAllCaps(false);
        closeButton.setTextColor(Color.WHITE);
        closeButton.setBackgroundTintList(ColorStateList.valueOf(0xFF1B4E63));
        closeButton.setOnClickListener(view -> stopSelf());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                dp(190), dp(44));
        closeParams.topMargin = dp(6);
        overlayView.addView(closeButton, closeParams);

        int type = overlayWindowType();
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.x = dp(12);
        overlayParams.y = dp(180);
        overlayStatus.setOnTouchListener(new OverlayDragListener());
        windowManager.addView(overlayView, overlayParams);
        updateButtons();
    }

    private void readScreenMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windowManager.getMaximumWindowMetrics();
            Rect bounds = metrics.getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
            screenDensity = getResources().getConfiguration().densityDpi;
        } else {
            readLegacyScreenMetrics();
        }
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder createNotificationBuilder() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
    }

    @SuppressWarnings("deprecation")
    private static Intent projectionResultData(Intent source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return source.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return source.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    @SuppressWarnings("deprecation")
    private static int overlayWindowType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    @SuppressWarnings("deprecation")
    private void readLegacyScreenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    private void requestCapture() {
        requestCapture(false);
    }

    private void requestCapture(boolean details) {
        if (!analyzerReady || processing || captureRequested || imageReader == null) return;
        if (capturedCount >= limit) return;
        processing = true;
        detailCapture = details;
        int requestSequence = ++captureSequence;
        setStatus(details ? getString(R.string.overlay_reading_orre)
                : getString(R.string.overlay_capturing, capturedCount, limit));
        updateButtons();
        overlayView.setVisibility(View.INVISIBLE);
        mainHandler.postDelayed(() -> {
            if (requestSequence == captureSequence) captureRequested = true;
        }, 220);
        mainHandler.postDelayed(() -> {
            if (requestSequence != captureSequence || !captureRequested) return;
            captureRequested = false;
            overlayView.setVisibility(View.VISIBLE);
            if (detailCapture && pendingRecord != null) {
                setStatus(getString(R.string.overlay_detail_failed));
                PokemonRecord record = pendingRecord;
                pendingRecord = null;
                analyzerWorker.execute(() -> {
                    boolean restored = swipeController.scrollUp(screenWidth, screenHeight);
                    if (!restored) autoSwipeActive = false;
                    mainHandler.postDelayed(
                            () -> analyzerWorker.execute(() -> finalizeRecord(record)),
                            restored ? 450 : 0);
                });
            } else {
                processing = false;
                setStatus(getString(R.string.overlay_no_frame));
                updateButtons();
            }
        }, 2200);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        try {
            if (!captureRequested) return;
            captureRequested = false;
            boolean details = detailCapture;
            Bitmap bitmap = imageToBitmap(image);
            mainHandler.post(() -> {
                if (overlayView != null) overlayView.setVisibility(View.VISIBLE);
                setStatus(getString(R.string.overlay_analyzing));
            });
            analyzerWorker.execute(() -> {
                if (details) analyzeOrreDetailFrame(bitmap);
                else analyzeCapturedFrame(bitmap);
            });
        } finally {
            image.close();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        int paddedWidth = screenWidth + rowPadding / pixelStride;
        Bitmap padded = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, screenWidth, screenHeight);
        if (cropped != padded) padded.recycle();
        return cropped;
    }

    private void analyzeCapturedFrame(Bitmap bitmap) {
        try {
            PokemonRecord record = analyzer.analyze(
                    bitmap, "screen://" + System.currentTimeMillis());
            if (inspectOrreActive && analyzer.shouldInspectOrre(record)) {
                pendingRecord = record;
                mainHandler.post(() -> setStatus(getString(
                        R.string.overlay_scrolling_orre)));
                boolean scrolled = swipeController.scrollDown(screenWidth, screenHeight);
                if (scrolled) {
                    mainHandler.post(() -> {
                        processing = false;
                        mainHandler.postDelayed(() -> requestCapture(true), 950);
                    });
                } else {
                    inspectOrreActive = false;
                    pendingRecord = null;
                    finalizeRecord(record);
                }
            } else {
                finalizeRecord(record);
            }
        } catch (Exception exception) {
            mainHandler.post(() -> {
                processing = false;
                setStatus(getString(R.string.overlay_error, exception.getMessage()));
                updateButtons();
            });
        } finally {
            bitmap.recycle();
        }
    }

    private void analyzeOrreDetailFrame(Bitmap bitmap) {
        PokemonRecord original = pendingRecord;
        pendingRecord = null;
        try {
            PokemonRecord record = original == null
                    ? null : analyzer.analyzeOrreDetails(bitmap, original);
            boolean restored = swipeController.scrollUp(screenWidth, screenHeight);
            if (!restored) autoSwipeActive = false;
            if (record == null) {
                throw new IllegalStateException(getString(R.string.overlay_no_pending_record));
            }
            mainHandler.postDelayed(
                    () -> analyzerWorker.execute(() -> finalizeRecord(record)),
                    restored ? 450 : 0);
        } catch (Exception exception) {
            if (original != null) {
                swipeController.scrollUp(screenWidth, screenHeight);
                finalizeRecord(original);
            } else {
                mainHandler.post(() -> {
                    processing = false;
                    setStatus(getString(R.string.overlay_error, exception.getMessage()));
                    updateButtons();
                });
            }
        } finally {
            bitmap.recycle();
        }
    }

    private void finalizeRecord(PokemonRecord record) {
        try {
            boolean inserted = collectionStore.append(record);
            if (inserted) capturedCount++;
            String status = inserted ? record.summary() : getString(R.string.overlay_duplicate);
            boolean shouldContinue = inserted && autoSwipeActive && capturedCount < limit;
            boolean swiped = !shouldContinue
                    || swipeController.swipeRight(screenWidth, screenHeight);
            if (shouldContinue && !swiped) autoSwipeActive = false;
            boolean scheduleNext = shouldContinue && swiped;
            mainHandler.post(() -> {
                processing = false;
                detailCapture = false;
                if (shouldContinue && !swiped) {
                    setStatus(getString(R.string.overlay_shizuku_failed,
                            capturedCount, limit));
                } else {
                    setStatus(getString(R.string.overlay_result,
                            capturedCount, limit, status));
                }
                updateButtons();
                if (scheduleNext) mainHandler.postDelayed(this::requestCapture, 1100);
            });
        } catch (Exception exception) {
            mainHandler.post(() -> {
                processing = false;
                detailCapture = false;
                setStatus(getString(R.string.overlay_error, exception.getMessage()));
                updateButtons();
            });
        }
    }

    private void updateButtons() {
        if (captureButton == null) return;
        boolean idle = analyzerReady && !processing && !captureRequested;
        captureButton.setEnabled(idle && capturedCount < limit);
        captureButton.setText(getString(R.string.capture_progress, capturedCount, limit));
    }

    private void setStatus(String value) {
        if (overlayStatus != null) overlayStatus.setText(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        running = false;
        analyzerReady = false;
        captureRequested = false;
        captureSequence++;
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        if (imageReader != null) imageReader.close();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (swipeController != null) swipeController.close();
        analyzerWorker.execute(() -> analyzer.close());
        analyzerWorker.shutdown();
        if (captureThread != null) captureThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class OverlayDragListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = overlayParams.x;
                startY = overlayParams.y;
                touchX = event.getRawX();
                touchY = event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                overlayParams.x = Math.max(0, startX - Math.round(event.getRawX() - touchX));
                overlayParams.y = Math.max(0, startY + Math.round(event.getRawY() - touchY));
                windowManager.updateViewLayout(overlayView, overlayParams);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick();
                return true;
            }
            return false;
        }
    }

    private static final class DragHandleView extends AppCompatTextView {
        DragHandleView(Context context) {
            super(context);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
