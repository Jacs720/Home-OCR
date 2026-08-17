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
import android.widget.ImageButton;
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

    private ChecklistRepository repository;
    private ChecklistAdapter adapter;
    private EditText searchView;
    private Spinner markFilterView;
    private Spinner variantFilterView;
    private Spinner statusFilterView;
    private TextView summaryView;
    private TextView resultCountView;
    private TextView importStatusView;
    private ProgressBar completionView;
    private ProgressBar loadingView;
    private Button importButton;
    private Button syncButton;
    private Button clearButton;

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
        markFilterView = findViewById(R.id.checklist_mark_filter);
        variantFilterView = findViewById(R.id.checklist_variant_filter);
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
        ImageButton languageButton = findViewById(R.id.checklist_language_button);

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
        LanguageMenu.attach(this, languageButton);
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
                updateSummary();
                applyFilters();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        markFilterView.setOnItemSelectedListener(filterListener);
        variantFilterView.setOnItemSelectedListener(filterListener);
        statusFilterView.setOnItemSelectedListener(filterListener);

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
                    allEntries.clear();
                    allEntries.addAll(repository.entries());
                    configureFilters();
                    updateSummary();
                    applyFilters();
                    importStatusView.setText(local.rowsRead == 0
                            ? getString(R.string.checklist_ready)
                            : formatImportResult(local));
                    setBusy(false);
                });
            } catch (IOException exception) {
                showError(getString(R.string.checklist_open_error));
            }
        });
    }

    private void configureFilters() {
        List<String> marks = new ArrayList<>();
        marks.add(getString(R.string.checklist_all_marks));
        for (ChecklistMark mark : ChecklistMark.values()) marks.add(mark.label(this));
        setSpinner(markFilterView, marks);

        ArrayAdapter<CharSequence> variantAdapter = ArrayAdapter.createFromResource(
                this, R.array.checklist_variant_filters, android.R.layout.simple_spinner_item);
        variantAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        variantFilterView.setAdapter(variantAdapter);

        ArrayAdapter<CharSequence> statusAdapter = ArrayAdapter.createFromResource(
                this, R.array.checklist_status_filters, android.R.layout.simple_spinner_item);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusFilterView.setAdapter(statusAdapter);
    }

    private void setSpinner(Spinner spinner, List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void importCsv(Uri uri) {
        setBusy(true);
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Unable to open CSV.");
                ChecklistRepository.ImportResult result = repository.importCsv(
                        ChecklistRepository.readUtf8(input));
                runOnUiThread(() -> {
                    String summary = formatImportResult(result);
                    importStatusView.setText(summary);
                    updateSummary();
                    applyFilters();
                    setBusy(false);
                    Toast.makeText(this, summary, Toast.LENGTH_LONG).show();
                });
            } catch (IOException exception) {
                showError(getString(R.string.checklist_import_error));
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
                    String summary = result.rowsRead == 0
                            ? getString(R.string.checklist_local_empty)
                            : formatImportResult(result);
                    importStatusView.setText(summary);
                    updateSummary();
                    applyFilters();
                    setBusy(false);
                    if (showToast) Toast.makeText(this, summary, Toast.LENGTH_LONG).show();
                });
            } catch (IOException exception) {
                showError(getString(R.string.checklist_sync_error));
            }
        });
    }

    private String formatImportResult(ChecklistRepository.ImportResult result) {
        if (result.invalidFormat) return getString(R.string.checklist_import_invalid);
        return getString(
                R.string.checklist_import_summary,
                result.rowsRead,
                result.newNormalTargets,
                result.newShinyTargets,
                result.unmatched,
                result.ambiguous);
    }

    private void applyFilters() {
        if (adapter == null || allEntries.isEmpty()) return;
        String query = ChecklistMatcher.normalize(searchView.getText().toString());
        ChecklistMark selectedMark = selectedMark();
        Boolean selectedShiny = selectedShiny();
        int status = statusFilterView.getSelectedItemPosition();
        String shinyLabel = getString(R.string.checklist_shiny);
        String normalLabel = getString(R.string.checklist_non_shiny);
        List<ChecklistEntry> visible = new ArrayList<>();
        for (ChecklistEntry entry : allEntries) {
            if (selectedMark != null && entry.mark != selectedMark) continue;
            if (selectedShiny != null && entry.shiny != selectedShiny) continue;
            if (status == 1 && entry.owned) continue;
            if (status == 2 && !entry.owned) continue;
            if (!query.isEmpty()) {
                String haystack = ChecklistMatcher.normalize(String.format(Locale.ROOT,
                        "%04d %s %s %s %s", entry.nationalNumber, entry.pokemon,
                        entry.form, entry.mark.label(this), entry.shiny ? shinyLabel : normalLabel));
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
        ChecklistMark mark = selectedMark();
        Boolean shiny = selectedShiny();
        int total = repository.countTotal(mark, shiny);
        int owned = repository.countOwned(mark, shiny);
        int pending = Math.max(0, total - owned);
        int percentage = total == 0 ? 0 : Math.round(owned * 100f / total);
        summaryView.setText(getResources().getQuantityString(
                R.plurals.checklist_summary_format,
                owned, owned, total, pending, percentage));
        completionView.setMax(Math.max(1, total));
        completionView.setProgress(owned);
        updateClearButton();
    }

    private ChecklistMark selectedMark() {
        if (markFilterView == null) return null;
        int position = markFilterView.getSelectedItemPosition();
        ChecklistMark[] marks = ChecklistMark.values();
        return position > 0 && position <= marks.length ? marks[position - 1] : null;
    }

    private Boolean selectedShiny() {
        if (variantFilterView == null) return null;
        int position = variantFilterView.getSelectedItemPosition();
        if (position == 1) return true;
        if (position == 2) return false;
        return null;
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
