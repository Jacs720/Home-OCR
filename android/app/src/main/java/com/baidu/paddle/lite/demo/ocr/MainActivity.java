package com.baidu.paddle.lite.demo.ocr;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class MainActivity extends AppCompatActivity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 4680;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Uri> selectedImages = new ArrayList<>();
    private final List<String> sessionResults = new ArrayList<>();

    private PokemonAnalyzer analyzer;
    private CollectionStore collectionStore;
    private MediaProjectionManager mediaProjectionManager;
    private TextView statusView;
    private TextView resultsView;
    private ImageView previewView;
    private EditText limitView;
    private Button scanButton;
    private Button floatingButton;
    private CheckBox autoSwipeView;
    private ProgressBar progressBar;
    private volatile boolean scanning;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST || statusView == null) return;
                boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                autoSwipeView.setChecked(granted);
                statusView.setText(granted
                        ? R.string.automation_ready
                        : R.string.automation_denied);
            };

    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                selectedImages.clear();
                if (uris != null) selectedImages.addAll(uris);
                if (!selectedImages.isEmpty()) showPreview(selectedImages.get(0));
                updateReadyState();
            });

    private final ActivityResultLauncher<String> csvExporter = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri == null) return;
                worker.execute(() -> {
                    try {
                        collectionStore.exportTo(uri);
                        runOnUiThread(() -> Toast.makeText(
                                this, "CSV exportado correctamente.", Toast.LENGTH_LONG).show());
                    } catch (IOException exception) {
                        showError("No se pudo exportar: " + exception.getMessage());
                    }
                });
            });

    private final ActivityResultLauncher<Intent> overlayPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> requestScreenCapture());

    private final ActivityResultLauncher<Intent> screenCapturePermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    statusView.setText(R.string.capture_permission_cancelled);
                    return;
                }
                Intent service = new Intent(this, ScreenCaptureService.class)
                        .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.getResultCode())
                        .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.getData())
                        .putExtra(ScreenCaptureService.EXTRA_LIMIT, requestedLimit())
                        .putExtra(ScreenCaptureService.EXTRA_AUTO_SWIPE,
                                autoSwipeView != null && autoSwipeView.isChecked());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
                statusView.setText(R.string.capture_started);
                moveTaskToBack(true);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.status);
        resultsView = findViewById(R.id.results);
        previewView = findViewById(R.id.preview);
        limitView = findViewById(R.id.scan_limit);
        scanButton = findViewById(R.id.scan_images);
        floatingButton = findViewById(R.id.start_floating_capture);
        autoSwipeView = findViewById(R.id.auto_swipe);
        progressBar = findViewById(R.id.progress);
        Button selectButton = findViewById(R.id.select_images);
        Button exportButton = findViewById(R.id.export_csv);
        Button checklistButton = findViewById(R.id.open_checklist);
        Button automationButton = findViewById(R.id.prepare_automation);

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);

        collectionStore = new CollectionStore(this);
        analyzer = new PokemonAnalyzer(this);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        selectButton.setOnClickListener(view ->
                imagePicker.launch(new String[]{"image/png", "image/jpeg", "image/webp"}));
        scanButton.setOnClickListener(view -> scanSelectedImages());
        floatingButton.setOnClickListener(view -> requestScreenCapture());
        automationButton.setOnClickListener(view -> prepareAutomation());
        checklistButton.setOnClickListener(view ->
                startActivity(new Intent(this, ChecklistActivity.class)));
        exportButton.setOnClickListener(view -> {
            if (collectionStore.count() == 0) {
                Toast.makeText(this, R.string.collection_empty, Toast.LENGTH_SHORT).show();
            } else {
                csvExporter.launch("pokemon_collection.csv");
            }
        });

        renderCollectionState();
        initializeOcr();
        updateAutomationState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resultsView != null) renderCollectionState();
        if (floatingButton != null) floatingButton.setEnabled(!ScreenCaptureService.isRunning());
    }

    private void initializeOcr() {
        statusView.setText(R.string.loading_model);
        worker.execute(() -> {
            try {
                analyzer.initialize();
                runOnUiThread(() -> {
                    statusView.setText(R.string.ocr_ready);
                    updateReadyState();
                });
            } catch (RuntimeException exception) {
                showError("No se pudo iniciar PaddleOCR: " + exception.getMessage());
            }
        });
    }

    private void requestScreenCapture() {
        if (ScreenCaptureService.isRunning()) {
            statusView.setText(R.string.capture_already_running);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermission.launch(permission);
            return;
        }
        screenCapturePermission.launch(mediaProjectionManager.createScreenCaptureIntent());
    }

    private void prepareAutomation() {
        if (!Shizuku.pingBinder()) {
            statusView.setText(R.string.automation_unavailable);
            Intent launch = getPackageManager().getLaunchIntentForPackage(
                    "moe.shizuku.privileged.api");
            if (launch != null) startActivity(launch);
            return;
        }
        try {
            if (Shizuku.isPreV11()) {
                statusView.setText(R.string.automation_unavailable);
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                autoSwipeView.setChecked(true);
                statusView.setText(R.string.automation_ready);
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                autoSwipeView.setChecked(false);
                statusView.setText(R.string.automation_denied);
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
            }
        } catch (RuntimeException exception) {
            autoSwipeView.setChecked(false);
            statusView.setText(R.string.automation_unavailable);
        }
    }

    private void updateAutomationState() {
        try {
            if (Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                autoSwipeView.setChecked(true);
            }
        } catch (RuntimeException ignored) {
            // La captura manual no depende de Shizuku.
        }
    }

    private int requestedLimit() {
        try {
            return Math.max(1, Integer.parseInt(limitView.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private void scanSelectedImages() {
        if (scanning || !analyzer.isReady() || selectedImages.isEmpty()) return;
        final int total = Math.min(selectedImages.size(), requestedLimit());
        final List<Uri> batch = new ArrayList<>(selectedImages.subList(0, total));
        scanning = true;
        progressBar.setMax(total);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        updateReadyState();

        worker.execute(() -> {
            int succeeded = 0;
            for (int index = 0; index < batch.size(); index++) {
                Uri uri = batch.get(index);
                final int progress = index + 1;
                Bitmap bitmap = null;
                try {
                    bitmap = decodeBitmap(uri);
                    if (bitmap == null) throw new IOException("imagen no válida");
                    PokemonRecord record = analyzer.analyze(bitmap, uri.toString());
                    if (collectionStore.append(record)) {
                        sessionResults.add(record.summary());
                        succeeded++;
                    } else {
                        sessionResults.add("↷ Captura " + progress + ": ya estaba registrada.");
                    }
                } catch (Exception exception) {
                    sessionResults.add("⚠ Captura " + progress + ": " + exception.getMessage());
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                }

                final int ok = succeeded;
                runOnUiThread(() -> {
                    progressBar.setProgress(progress);
                    statusView.setText(String.format(Locale.ROOT,
                            "Procesadas %d/%d · correctas %d", progress, total, ok));
                    renderCollectionState();
                });
            }

            runOnUiThread(() -> {
                scanning = false;
                progressBar.setVisibility(View.GONE);
                statusView.setText(String.format(Locale.ROOT,
                        "Análisis terminado. Colección: %d registros.", collectionStore.count()));
                updateReadyState();
            });
        });
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("no se pudo abrir la captura");
            return BitmapFactory.decodeStream(stream);
        }
    }

    private void showPreview(Uri uri) {
        worker.execute(() -> {
            try {
                Bitmap bitmap = decodeBitmap(uri);
                runOnUiThread(() -> {
                    previewView.setPadding(0, 0, 0, 0);
                    previewView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    previewView.setImageBitmap(bitmap);
                });
            } catch (IOException exception) {
                showError("No se pudo mostrar la captura.");
            }
        });
    }

    private void updateReadyState() {
        boolean ready = analyzer != null && analyzer.isReady()
                && !selectedImages.isEmpty() && !scanning;
        scanButton.setEnabled(ready);
        if (!selectedImages.isEmpty() && !scanning) {
            statusView.setText(String.format(Locale.ROOT,
                    "%d captura(s) seleccionada(s).", selectedImages.size()));
        }
    }

    private void renderCollectionState() {
        int count = collectionStore == null ? 0 : collectionStore.count();
        if (count == 0 && sessionResults.isEmpty()) {
            resultsView.setText(R.string.collection_empty);
            return;
        }
        StringBuilder text = new StringBuilder("Colección local: ")
                .append(count).append(" registro(s)\n\n");
        int start = Math.max(0, sessionResults.size() - 12);
        for (int index = start; index < sessionResults.size(); index++) {
            text.append(sessionResults.get(index)).append('\n');
        }
        resultsView.setText(text.toString().trim());
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            statusView.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        worker.execute(analyzer::close);
        worker.shutdown();
        super.onDestroy();
    }
}
