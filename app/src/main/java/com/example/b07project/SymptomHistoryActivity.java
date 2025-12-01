package com.example.b07project;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.b07project.adapters.SymptomCheckInAdapter;
import com.example.b07project.models.SymptomCheckIn;
import com.example.b07project.repository.SymptomCheckInRepository;
import com.example.b07project.repository.TriggerAnalyticsRepository;
import com.example.b07project.utils.ReportGenerator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SymptomHistoryActivity extends AppCompatActivity {

    private Button btnFilter;
    private Button btnDownloadCsv;
    private Button btnDownloadPdf;
    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView tvEmptyMessage;

    private SymptomCheckInAdapter adapter;
    private SymptomCheckInRepository repository;
    
    private List<SymptomCheckIn> allCheckIns = new ArrayList<>();
    private List<SymptomCheckIn> filteredCheckIns = new ArrayList<>();
    private List<String> selectedTriggers = new ArrayList<>();
    private List<String> selectedSymptoms = new ArrayList<>();
    private Date startDate;
    private Date endDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_symptom_history);

        initializeViews();
        setupRecyclerView();

        repository = new SymptomCheckInRepository();
        
        btnFilter.setOnClickListener(v -> showFilterDialog());
        btnDownloadCsv.setOnClickListener(v -> exportToCsv());
        btnDownloadPdf.setOnClickListener(v -> exportToPdf());
        
        // Default to all time
        Calendar cal = Calendar.getInstance();
        endDate = cal.getTime();
        cal.add(Calendar.YEAR, -10);
        startDate = cal.getTime();
        
        loadCheckIns();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        btnFilter = findViewById(R.id.btnFilter);
        btnDownloadCsv = findViewById(R.id.btnDownloadCsv);
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf);
        recyclerView = findViewById(R.id.recyclerView);
        progress = findViewById(R.id.progress);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
    }

    private void setupRecyclerView() {
        adapter = new SymptomCheckInAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadCheckIns() {
        String userId;
        if (getIntent().hasExtra("EXTRA_CHILD_ID")) {
            userId = getIntent().getStringExtra("EXTRA_CHILD_ID");
            android.util.Log.d("childparentlink", "SymptomHistoryActivity: Using EXTRA_CHILD_ID: " + userId);
        } else {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Please sign in to view history", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            userId = currentUser.getUid();
            android.util.Log.d("childparentlink", "SymptomHistoryActivity: Using current user ID: " + userId);
        }

        showLoading(true);

        repository.getCheckInsForUser(userId, new SymptomCheckInRepository.LoadCallback() {
            @Override
            public void onSuccess(List<SymptomCheckIn> checkIns) {
                showLoading(false);
                allCheckIns = checkIns;
                applyFilters();
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                Toast.makeText(SymptomHistoryActivity.this,
                    "Failed to load history: " + error,
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyMessage(boolean show) {
        tvEmptyMessage.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void applyFilters() {
        filteredCheckIns.clear();
        
        for (SymptomCheckIn checkIn : allCheckIns) {
            // Date filter
            if (checkIn.getDate() != null) {
                if (checkIn.getDate().before(startDate) || checkIn.getDate().after(endDate)) {
                    continue;
                }
            }
            
            // Symptom filter
            if (!selectedSymptoms.isEmpty()) {
                if (checkIn.getSymptoms() == null || checkIn.getSymptoms().isEmpty()) {
                    continue;
                }
                boolean hasMatchingSymptom = false;
                for (String symptom : selectedSymptoms) {
                    if (checkIn.getSymptoms().contains(symptom)) {
                        hasMatchingSymptom = true;
                        break;
                    }
                }
                if (!hasMatchingSymptom) {
                    continue;
                }
            }
            
            // Trigger filter
            if (!selectedTriggers.isEmpty()) {
                if (checkIn.getTriggers() == null || checkIn.getTriggers().isEmpty()) {
                    continue;
                }
                boolean hasMatchingTrigger = false;
                for (String trigger : selectedTriggers) {
                    if (checkIn.getTriggers().contains(trigger)) {
                        hasMatchingTrigger = true;
                        break;
                    }
                }
                if (!hasMatchingTrigger) {
                    continue;
                }
            }
            
            filteredCheckIns.add(checkIn);
        }
        
        if (filteredCheckIns.isEmpty()) {
            showEmptyMessage(true);
        } else {
            showEmptyMessage(false);
            adapter.setCheckIns(filteredCheckIns);
        }
    }
    
    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter, null);
        
        // Trigger checkboxes
        CheckBox cbExercise = dialogView.findViewById(R.id.cbFilterExercise);
        CheckBox cbColdAir = dialogView.findViewById(R.id.cbFilterColdAir);
        CheckBox cbPets = dialogView.findViewById(R.id.cbFilterPets);
        CheckBox cbPollen = dialogView.findViewById(R.id.cbFilterPollen);
        CheckBox cbStress = dialogView.findViewById(R.id.cbFilterStress);
        CheckBox cbSmoke = dialogView.findViewById(R.id.cbFilterSmoke);
        CheckBox cbWeather = dialogView.findViewById(R.id.cbFilterWeather);
        CheckBox cbDust = dialogView.findViewById(R.id.cbFilterDust);
        
        // Symptom checkboxes (show them for symptom history)
        TextView tvSymptomLabel = dialogView.findViewById(R.id.tvSymptomFilterLabel);
        CheckBox cbWheezing = dialogView.findViewById(R.id.cbFilterWheezing);
        CheckBox cbCoughing = dialogView.findViewById(R.id.cbFilterCoughing);
        CheckBox cbShortnessBreath = dialogView.findViewById(R.id.cbFilterShortnessBreath);
        CheckBox cbChestTightness = dialogView.findViewById(R.id.cbFilterChestTightness);
        CheckBox cbNighttime = dialogView.findViewById(R.id.cbFilterNighttime);
        View divider2 = dialogView.findViewById(R.id.divider2);
        
        tvSymptomLabel.setVisibility(View.VISIBLE);
        cbWheezing.setVisibility(View.VISIBLE);
        cbCoughing.setVisibility(View.VISIBLE);
        cbShortnessBreath.setVisibility(View.VISIBLE);
        cbChestTightness.setVisibility(View.VISIBLE);
        cbNighttime.setVisibility(View.VISIBLE);
        divider2.setVisibility(View.VISIBLE);
        
        RadioGroup rgDateRange = dialogView.findViewById(R.id.rgDateRange);
        
        // Set current selections
        cbExercise.setChecked(selectedTriggers.contains("exercise"));
        cbColdAir.setChecked(selectedTriggers.contains("cold air"));
        cbPets.setChecked(selectedTriggers.contains("pets"));
        cbPollen.setChecked(selectedTriggers.contains("pollen"));
        cbStress.setChecked(selectedTriggers.contains("stress"));
        cbSmoke.setChecked(selectedTriggers.contains("smoke"));
        cbWeather.setChecked(selectedTriggers.contains("weather change"));
        cbDust.setChecked(selectedTriggers.contains("dust"));
        
        cbWheezing.setChecked(selectedSymptoms.contains("wheezing"));
        cbCoughing.setChecked(selectedSymptoms.contains("coughing"));
        cbShortnessBreath.setChecked(selectedSymptoms.contains("shortness of breath"));
        cbChestTightness.setChecked(selectedSymptoms.contains("chest tightness"));
        cbNighttime.setChecked(selectedSymptoms.contains("nighttime symptoms"));
        
        new AlertDialog.Builder(this)
            .setTitle("Filter Check-Ins")
            .setView(dialogView)
            .setPositiveButton("Apply", (dialog, which) -> {
                selectedTriggers.clear();
                if (cbExercise.isChecked()) selectedTriggers.add("exercise");
                if (cbColdAir.isChecked()) selectedTriggers.add("cold air");
                if (cbPets.isChecked()) selectedTriggers.add("pets");
                if (cbPollen.isChecked()) selectedTriggers.add("pollen");
                if (cbStress.isChecked()) selectedTriggers.add("stress");
                if (cbSmoke.isChecked()) selectedTriggers.add("smoke");
                if (cbWeather.isChecked()) selectedTriggers.add("weather change");
                if (cbDust.isChecked()) selectedTriggers.add("dust");
                
                selectedSymptoms.clear();
                if (cbWheezing.isChecked()) selectedSymptoms.add("wheezing");
                if (cbCoughing.isChecked()) selectedSymptoms.add("coughing");
                if (cbShortnessBreath.isChecked()) selectedSymptoms.add("shortness of breath");
                if (cbChestTightness.isChecked()) selectedSymptoms.add("chest tightness");
                if (cbNighttime.isChecked()) selectedSymptoms.add("nighttime symptoms");
                
                // Update date range
                Calendar cal = Calendar.getInstance();
                endDate = cal.getTime();
                
                int selectedId = rgDateRange.getCheckedRadioButtonId();
                if (selectedId == R.id.rbThisWeek) {
                    startDate = TriggerAnalyticsRepository.getStartOfWeek();
                } else if (selectedId == R.id.rbThisMonth) {
                    startDate = TriggerAnalyticsRepository.getStartOfMonth();
                } else {
                    cal.add(Calendar.YEAR, -10);
                    startDate = cal.getTime();
                }
                
                applyFilters();
            })
            .setNegativeButton("Clear", (dialog, which) -> {
                selectedTriggers.clear();
                selectedSymptoms.clear();
                Calendar cal = Calendar.getInstance();
                endDate = cal.getTime();
                cal.add(Calendar.YEAR, -10);
                startDate = cal.getTime();
                applyFilters();
            })
            .setNeutralButton("Cancel", null)
            .show();
    }
    
    private void exportToCsv() {
        if (filteredCheckIns.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Date,Time,Symptom Level,Symptoms,Triggers,Notes\n");
        
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (SymptomCheckIn checkIn : filteredCheckIns) {
            if (checkIn.getDate() == null) continue;
            
            csv.append(dateFmt.format(checkIn.getDate())).append(",");
            csv.append(timeFmt.format(checkIn.getDate())).append(",");
            csv.append(checkIn.getSymptomLevel()).append(",");
            
            String symptoms = checkIn.getSymptoms() != null ? TextUtils.join(";", checkIn.getSymptoms()) : "";
            csv.append("\"").append(symptoms.replace("\"", "\"\"")).append("\",");
            
            String triggers = checkIn.getTriggers() != null ? TextUtils.join(";", checkIn.getTriggers()) : "";
            csv.append("\"").append(triggers.replace("\"", "\"\"")).append("\",");
            
            String notes = checkIn.getNotes() != null ? checkIn.getNotes() : "";
            csv.append("\"").append(notes.replace("\"", "\"\"")).append("\"\n");
        }

        saveCsvFile(csv.toString());
    }

    private void saveCsvFile(String csvContent) {
        String fileName = "SymptomHistory_" + System.currentTimeMillis() + ".csv";
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        out.write(csvContent.getBytes());
                    }
                    Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
                }
            } else {
                java.io.File path = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File file = new java.io.File(path, fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(csvContent.getBytes());
                }
                Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error saving CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    private void exportToPdf() {
        if (filteredCheckIns.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Generating PDF...", Toast.LENGTH_SHORT).show();
        ReportGenerator generator = new ReportGenerator(this);
        generator.generateSymptomLogPdf(filteredCheckIns);
    }
}
