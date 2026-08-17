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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
    private Button exportButton;
    private Button clearCollectionButton;
    private CheckBox autoSwipeView;
    private CheckBox inspectOrreView;
    private ProgressBar progressBar;
    private TextView progressLabel;
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
                resetManualProgress();
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
                                this, R.string.csv_export_success, Toast.LENGTH_LONG).show());
                    } catch (IOException exception) {
                        showError(getString(R.string.csv_export_error, exception.getMessage()));
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
                                autoSwipeView != null && autoSwipeView.isChecked())
                        .putExtra(ScreenCaptureService.EXTRA_INSPECT_ORRE,
                                inspectOrreView != null && inspectOrreView.isChecked());
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
        AppLanguage.ensureSpanishDefault(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        configureSystemBars();

        statusView = findViewById(R.id.status);
        resultsView = findViewById(R.id.results);
        previewView = findViewById(R.id.preview);
        limitView = findViewById(R.id.scan_limit);
        scanButton = findViewById(R.id.scan_images);
        floatingButton = findViewById(R.id.start_floating_capture);
        autoSwipeView = findViewById(R.id.auto_swipe);
        inspectOrreView = findViewById(R.id.inspect_orre);
        progressBar = findViewById(R.id.progress);
        progressLabel = findViewById(R.id.manual_progress_label);
        Button selectButton = findViewById(R.id.select_images);
        exportButton = findViewById(R.id.export_csv);
        clearCollectionButton = findViewById(R.id.clear_collection);
        Button checklistButton = findViewById(R.id.open_checklist);
        Button automationButton = findViewById(R.id.prepare_automation);
        ImageButton languageButton = findViewById(R.id.language_button);

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
        clearCollectionButton.setOnClickListener(view -> confirmClearCollection());
        LanguageMenu.attach(this, languageButton);

        renderCollectionState();
        initializeOcr();
        updateAutomationState();
    }

    private void configureSystemBars() {
        View root = findViewById(R.id.app_root);
        View topBar = findViewById(R.id.top_bar);
        int rootStart = root.getPaddingStart();
        int rootTop = root.getPaddingTop();
        int rootEnd = root.getPaddingEnd();
        int rootBottom = root.getPaddingBottom();
        int barStart = topBar.getPaddingStart();
        int barTop = topBar.getPaddingTop();
        int barEnd = topBar.getPaddingEnd();
        int barBottom = topBar.getPaddingBottom();

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navigationBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            topBar.setPaddingRelative(
                    barStart, barTop + statusBars.top, barEnd, barBottom);
            view.setPaddingRelative(
                    rootStart, rootTop, rootEnd, rootBottom + navigationBars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
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
                showError(getString(R.string.ocr_init_error, exception.getMessage()));
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
        progressLabel.setText(getResources().getQuantityString(
                R.plurals.manual_progress_format, total, 0, total));
        progressLabel.setVisibility(View.VISIBLE);
        updateReadyState();

        worker.execute(() -> {
            int succeeded = 0;
            for (int index = 0; index < batch.size(); index++) {
                Uri uri = batch.get(index);
                final int progress = index + 1;
                Bitmap bitmap = null;
                try {
                    bitmap = decodeBitmap(uri);
                    if (bitmap == null) throw new IOException(getString(R.string.invalid_image));
                    PokemonRecord record = analyzer.analyze(bitmap, uri.toString());
                    if (collectionStore.append(record)) {
                        sessionResults.add(record.summary());
                        succeeded++;
                    } else {
                        sessionResults.add(getString(R.string.capture_duplicate, progress));
                    }
                } catch (Exception exception) {
                    sessionResults.add(getString(
                            R.string.capture_analysis_error, progress, exception.getMessage()));
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                }

                final int ok = succeeded;
                runOnUiThread(() -> {
                    progressBar.setProgress(progress);
                    progressLabel.setText(getResources().getQuantityString(
                            R.plurals.manual_progress_format, total, progress, total));
                    statusView.setText(getString(
                            R.string.manual_status_progress, progress, total, ok));
                    renderCollectionState();
                });
            }

            runOnUiThread(() -> {
                scanning = false;
                progressBar.setProgress(total);
                progressLabel.setText(getResources().getQuantityString(
                        R.plurals.manual_progress_complete, total, total, total));
                statusView.setText(getString(
                        R.string.manual_status_done, collectionStore.count()));
                renderCollectionState();
                updateReadyState();
            });
        });
    }

    private void resetManualProgress() {
        if (progressBar == null || progressLabel == null) return;
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        progressLabel.setVisibility(View.GONE);
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException(getString(R.string.capture_open_error));
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
                showError(getString(R.string.preview_error));
            }
        });
    }

    private void updateReadyState() {
        boolean ready = analyzer != null && analyzer.isReady()
                && !selectedImages.isEmpty() && !scanning;
        scanButton.setEnabled(ready);
        if (collectionStore != null) updateCollectionActions(collectionStore.count());
        if (!selectedImages.isEmpty() && !scanning) {
            statusView.setText(getString(
                    R.string.selected_capture_count, selectedImages.size()));
        }
    }

    private void renderCollectionState() {
        int count = collectionStore == null ? 0 : collectionStore.count();
        updateCollectionActions(count);
        if (count == 0 && sessionResults.isEmpty()) {
            resultsView.setText(R.string.collection_empty);
            return;
        }
        StringBuilder text = new StringBuilder(getString(
                R.string.collection_local_count, count)).append("\n\n");
        int start = Math.max(0, sessionResults.size() - 12);
        for (int index = start; index < sessionResults.size(); index++) {
            text.append(sessionResults.get(index)).append('\n');
        }
        resultsView.setText(text.toString().trim());
    }

    private void updateCollectionActions(int count) {
        boolean enabled = count > 0 && !scanning;
        if (exportButton != null) exportButton.setEnabled(enabled);
        if (clearCollectionButton != null) clearCollectionButton.setEnabled(enabled);
    }

    private void confirmClearCollection() {
        if (collectionStore.count() == 0 || scanning) return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.clear_collection_title)
                .setMessage(R.string.clear_collection_message)
                .setNegativeButton(R.string.clear_collection_negative, null)
                .setPositiveButton(R.string.clear_collection_positive,
                        (ignored, which) -> clearLocalCollection())
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.danger)));
        dialog.show();
    }

    private void clearLocalCollection() {
        exportButton.setEnabled(false);
        clearCollectionButton.setEnabled(false);
        worker.execute(() -> {
            try {
                collectionStore.clear();
                runOnUiThread(() -> {
                    sessionResults.clear();
                    renderCollectionState();
                    statusView.setText(R.string.clear_collection_success);
                    Toast.makeText(this, R.string.clear_collection_success,
                            Toast.LENGTH_LONG).show();
                });
            } catch (IOException exception) {
                runOnUiThread(() -> {
                    renderCollectionState();
                    showError(getString(R.string.clear_collection_error));
                });
            }
        });
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
