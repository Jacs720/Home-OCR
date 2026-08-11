package com.baidu.paddle.lite.demo.ocr;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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
import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;

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
    private Spinner targetFilterView;
    private Spinner statusFilterView;
    private TextView summaryView;
    private TextView resultCountView;
    private TextView importStatusView;
    private ProgressBar completionView;
    private ProgressBar loadingView;
    private Button importButton;
    private Button syncButton;

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
        targetFilterView = findViewById(R.id.checklist_target_filter);
        statusFilterView = findViewById(R.id.checklist_status_filter);
        summaryView = findViewById(R.id.checklist_summary);
        resultCountView = findViewById(R.id.checklist_result_count);
        importStatusView = findViewById(R.id.checklist_import_status);
        completionView = findViewById(R.id.checklist_completion);
        loadingView = findViewById(R.id.checklist_loading);
        importButton = findViewById(R.id.checklist_import_csv);
        syncButton = findViewById(R.id.checklist_sync_local);
        ListView listView = findViewById(R.id.checklist_list);
        TextView emptyView = findViewById(R.id.checklist_empty);
        Button backButton = findViewById(R.id.checklist_back);

        repository = new ChecklistRepository(this);
        adapter = new ChecklistAdapter(this, (entry, owned) -> {
            repository.setOwned(entry.id, owned);
            updateSummary();
            applyFilters();
        });
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);

        backButton.setOnClickListener(view -> finish());
        importButton.setOnClickListener(view -> csvPicker.launch(new String[]{
                "text/csv", "text/comma-separated-values", "application/csv", "text/plain"}));
        syncButton.setOnClickListener(view -> syncLocalCollection(true));

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

        loadChecklist();
    }

    private void loadChecklist() {
        setBusy(true);
        worker.execute(() -> {
            try {
                repository.load();
                ChecklistRepository.ImportResult local = repository.importCsv(
                        new CollectionStore(this).readCsvText());
                runOnUiThread(() -> {
                    allEntries.clear();
                    allEntries.addAll(repository.entries());
                    configureFilters();
                    updateSummary();
                    applyFilters();
                    importStatusView.setText(local.shinyRows == 0
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
        targetFilters.clear();
        targetFilters.add(getString(R.string.checklist_all_targets));
        targetFilters.addAll(repository.targets());
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
                runOnUiThread(() -> {
                    importStatusView.setText(result.shinyRows == 0
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
        String selectedTarget = targetIndex > 0 && targetIndex < targetFilters.size()
                ? targetFilters.get(targetIndex) : null;
        int status = statusFilterView.getSelectedItemPosition();
        List<ChecklistEntry> visible = new ArrayList<>();
        for (ChecklistEntry entry : allEntries) {
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
        int total = repository.countTotal();
        int owned = repository.countOwned();
        int pending = Math.max(0, total - owned);
        int percentage = total == 0 ? 0 : Math.round(owned * 100f / total);
        summaryView.setText(getString(
                R.string.checklist_summary_format, owned, total, pending, percentage));
        completionView.setMax(Math.max(1, total));
        completionView.setProgress(owned);
    }

    private void setBusy(boolean busy) {
        loadingView.setVisibility(busy ? View.VISIBLE : View.GONE);
        importButton.setEnabled(!busy);
        syncButton.setEnabled(!busy);
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
