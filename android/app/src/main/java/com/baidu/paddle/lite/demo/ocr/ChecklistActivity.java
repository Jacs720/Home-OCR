package com.baidu.paddle.lite.demo.ocr;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChecklistActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<ChecklistEntry> allEntries = new ArrayList<>();
    private final List<String> targetFilters = new ArrayList<>();

    private ChecklistRepository repository;
    private ChecklistAdapter adapter;
    private EditText searchView;
    private Spinner modeFilterView;
    private Spinner targetFilterView;
    private Spinner statusFilterView;
    private TextView summaryView;
    private TextView resultCountView;
    private TextView importStatusView;
    private ProgressBar completionView;
    private ProgressBar loadingView;
    private Button importButton;
    private Button syncButton;
    private Button clearButton;
    private String selectedMode = ChecklistEntry.MODE_LIVING_DEX;
    private boolean configuringFilters;

    private final ActivityResultLauncher<String[]> csvPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importCsv(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checklist);

        searchView = findViewById(R.id.checklist_search);
        modeFilterView = findViewById(R.id.checklist_mode_filter);
        targetFilterView = findViewById(R.id.checklist_target_filter);
        statusFilterView = findViewById(R.id.checklist_status_filter);
        summaryView = findViewById(R.id.checklist_summary);
        resultCountView = findViewById(R.id.checklist_result_count);
        importStatusView = findViewById(R.id.checklist_import_status);
        completionView = findViewById(R.id.checklist_completion);
        loadingView = findViewById(R.id.checklist_loading);
        importButton = findViewById(R.id.checklist_import_csv);
        syncButton = findViewById(R.id.checklist_sync_local);
        clearButton = findViewById(R.id.checklist_clear_progress);
        ListView listView = findViewById(R.id.checklist_list);
        TextView emptyView = findViewById(R.id.checklist_empty);
        Button backButton = findViewById(R.id.checklist_back);

        repository = new ChecklistRepository(this);
        adapter = new ChecklistAdapter(this, (entry, owned) -> {
            repository.setOwned(entry.id, owned);
            updateSummary();
            applyFilters();
            updateClearButton();
        });
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);

        backButton.setOnClickListener(view -> finish());
        importButton.setOnClickListener(view -> csvPicker.launch(new String[]{
                "text/csv", "text/comma-separated-values", "application/csv", "text/plain"}));
        syncButton.setOnClickListener(view -> syncLocalCollection(true));
        clearButton.setOnClickListener(view -> confirmClearProgress());

        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                applyFilters();
            }
            @Override public void afterTextChanged(Editable value) { }
        });

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        targetFilterView.setOnItemSelectedListener(filterListener);
        statusFilterView.setOnItemSelectedListener(filterListener);
        modeFilterView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (configuringFilters) return;
                String nextMode = position == 1
                        ? ChecklistEntry.MODE_ULTIMATE : ChecklistEntry.MODE_LIVING_DEX;
                if (!nextMode.equals(selectedMode)) {
                    selectedMode = nextMode;
                    repository.setSelectedMode(selectedMode);
                    targetFilterView.setSelection(0);
                }
                updateModeUi();
                updateSummary();
                applyFilters();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        loadChecklist();
    }

    private void loadChecklist() {
        setBusy(true);
        worker.execute(() -> {
            try {
                repository.load();
                ChecklistRepository.ImportResult local = repository.shouldAutoSyncLocal()
                        ? repository.importCsv(new CollectionStore(this).readCsvText())
                        : new ChecklistRepository.ImportResult();
                runOnUiThread(() -> {
                    selectedMode = repository.selectedMode();
                    allEntries.clear();
                    allEntries.addAll(repository.entries());
                    configureFilters();
                    updateSummary();
                    applyFilters();
                    importStatusView.setText(local.rowsRead == 0
                            ? getString(R.string.checklist_ready)
                            : local.summary());
                    setBusy(false);
                });
            } catch (IOException exception) {
                showError("No se pudo abrir el checklist: " + exception.getMessage());
            }
        });
    }

    private void configureFilters() {
        configuringFilters = true;
        ArrayAdapter<CharSequence> modeAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.checklist_modes,
                android.R.layout.simple_spinner_item);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeFilterView.setAdapter(modeAdapter);
        modeFilterView.setSelection(ChecklistEntry.MODE_ULTIMATE.equals(selectedMode) ? 1 : 0);

        targetFilters.clear();
        targetFilters.add(getString(R.string.checklist_all_targets));
        targetFilters.addAll(repository.targets(ChecklistEntry.MODE_ULTIMATE));
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, targetFilters);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetFilterView.setAdapter(targetAdapter);

        ArrayAdapter<CharSequence> statusAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.checklist_status_filters,
                android.R.layout.simple_spinner_item);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusFilterView.setAdapter(statusAdapter);
        configuringFilters = false;
        updateModeUi();
    }

    private void updateModeUi() {
        boolean ultimate = ChecklistEntry.MODE_ULTIMATE.equals(selectedMode);
        targetFilterView.setVisibility(ultimate ? View.VISIBLE : View.GONE);
    }

    private void importCsv(Uri uri) {
        setBusy(true);
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("no se pudo abrir el archivo");
                ChecklistRepository.ImportResult result = repository.importCsv(
                        ChecklistRepository.readUtf8(input));
                runOnUiThread(() -> {
                    importStatusView.setText(result.summary());
                    updateSummary();
                    applyFilters();
                    setBusy(false);
                    Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show();
                });
            } catch (IOException exception) {
                showError("No se pudo importar el CSV: " + exception.getMessage());
            }
        });
    }

    private void syncLocalCollection(boolean showToast) {
        setBusy(true);
        worker.execute(() -> {
            try {
                ChecklistRepository.ImportResult result = repository.importCsv(
                        new CollectionStore(this).readCsvText());
                repository.setAutoSyncLocal(true);
                runOnUiThread(() -> {
                    importStatusView.setText(result.rowsRead == 0
                            ? getString(R.string.checklist_local_empty)
                            : result.summary());
                    updateSummary();
                    applyFilters();
                    setBusy(false);
                    if (showToast) Toast.makeText(this,
                            importStatusView.getText(), Toast.LENGTH_LONG).show();
                });
            } catch (IOException exception) {
                showError("No se pudo sincronizar la colección local: " + exception.getMessage());
            }
        });
    }

    private void applyFilters() {
        if (adapter == null || allEntries.isEmpty()) return;
        String query = ChecklistMatcher.normalize(searchView.getText().toString());
        int targetIndex = targetFilterView.getSelectedItemPosition();
        String selectedTarget = ChecklistEntry.MODE_ULTIMATE.equals(selectedMode)
                && targetIndex > 0 && targetIndex < targetFilters.size()
                ? targetFilters.get(targetIndex) : null;
        int status = statusFilterView.getSelectedItemPosition();
        List<ChecklistEntry> visible = new ArrayList<>();
        for (ChecklistEntry entry : allEntries) {
            if (!selectedMode.equals(entry.mode())) continue;
            if (selectedTarget != null && !selectedTarget.equals(entry.originMark)) continue;
            if (status == 1 && entry.owned) continue;
            if (status == 2 && !entry.owned) continue;
            if (!query.isEmpty()) {
                String haystack = ChecklistMatcher.normalize(String.format(Locale.ROOT,
                        "%04d %s %s %s", entry.nationalNumber, entry.pokemon,
                        entry.form, entry.originMark));
                if (!haystack.contains(query)) continue;
            }
            visible.add(entry);
        }
        adapter.setEntries(visible);
        resultCountView.setText(getResources().getQuantityString(
                R.plurals.checklist_visible_count, visible.size(), visible.size()));
    }

    private void updateSummary() {
        if (repository == null) return;
        int total = repository.countTotal(selectedMode);
        int owned = repository.countOwned(selectedMode);
        int pending = Math.max(0, total - owned);
        int percentage = total == 0 ? 0 : Math.round(owned * 100f / total);
        summaryView.setText(getResources().getQuantityString(
                R.plurals.checklist_summary_format,
                owned, owned, total, pending, percentage));
        completionView.setMax(Math.max(1, total));
        completionView.setProgress(owned);
        updateClearButton();
    }

    private void confirmClearProgress() {
        if (repository.countOwnedAll() == 0) return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.checklist_clear_title)
                .setMessage(R.string.checklist_clear_message)
                .setNegativeButton(R.string.checklist_clear_negative, null)
                .setPositiveButton(R.string.checklist_clear_positive,
                        (ignored, which) -> clearProgress())
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.danger)));
        dialog.show();
    }

    private void clearProgress() {
        repository.clearProgress();
        importStatusView.setText(R.string.checklist_clear_success);
        updateSummary();
        applyFilters();
        updateClearButton();
        Toast.makeText(this, R.string.checklist_clear_success, Toast.LENGTH_LONG).show();
    }

    private void setBusy(boolean busy) {
        loadingView.setVisibility(busy ? View.VISIBLE : View.GONE);
        importButton.setEnabled(!busy);
        syncButton.setEnabled(!busy);
        clearButton.setEnabled(!busy && repository != null && repository.countOwnedAll() > 0);
    }

    private void updateClearButton() {
        if (clearButton != null && repository != null) {
            clearButton.setEnabled(repository.countOwnedAll() > 0
                    && loadingView.getVisibility() != View.VISIBLE);
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            importStatusView.setText(message);
            setBusy(false);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
