package com.example.b07project;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.adapters.MedicineLogAdapter;
import com.example.b07project.models.ControllerMedicineLog;
import com.example.b07project.models.MedicineLog;
import com.example.b07project.models.RescueInhalerLog;
import com.example.b07project.repository.ControllerMedicineRepository;
import com.example.b07project.repository.RescueInhalerRepository;
import com.example.b07project.repository.TriggerAnalyticsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class RescueInhalerHistoryActivity extends AppCompatActivity {
    
    private RadioGroup rgHistoryType;
    private RadioButton rbRescueHistory, rbControllerHistory;
    private Button btnFilter;
    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView tvEmptyMessage;
    
    private MedicineLogAdapter adapter;
    private RescueInhalerRepository rescueRepository;
    private ControllerMedicineRepository controllerRepository;
    
    private List<? extends MedicineLog> allLogs = new ArrayList<>();
    private List<String> selectedTriggers = new ArrayList<>();
    private Date startDate;
    private Date endDate;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescue_inhaler_history);
        
        initializeViews();
        setupRecyclerView();
        
        rescueRepository = new RescueInhalerRepository();
        controllerRepository = new ControllerMedicineRepository();
        
        rgHistoryType.setOnCheckedChangeListener((group, checkedId) -> loadLogs());
        btnFilter.setOnClickListener(v -> showFilterDialog());
        
        // Default to all time
        Calendar cal = Calendar.getInstance();
        endDate = cal.getTime();
        cal.add(Calendar.YEAR, -10);
        startDate = cal.getTime();
        
        loadLogs();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }
    
    private void initializeViews() {
        rgHistoryType = findViewById(R.id.rgHistoryType);
        rbRescueHistory = findViewById(R.id.rbRescueHistory);
        rbControllerHistory = findViewById(R.id.rbControllerHistory);
        btnFilter = findViewById(R.id.btnFilter);
        recyclerView = findViewById(R.id.recyclerView);
        progress = findViewById(R.id.progress);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
    }
    
    private void setupRecyclerView() {
        adapter = new MedicineLogAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
    
    private void loadLogs() {
        String userId;
        if (getIntent().hasExtra("EXTRA_CHILD_ID")) {
            userId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        } else {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Please sign in to view logs", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            userId = currentUser.getUid();
        }
        
        android.util.Log.d("childparentdatalink", "RescueInhalerHistoryActivity: Loading logs for userId: " + userId);
        
        showLoading(true);
        
        boolean isController = rbControllerHistory.isChecked();
        
        if (isController) {
            controllerRepository.getLogsForUser(userId, new ControllerMedicineRepository.LoadCallback() {
                @Override
                public void onSuccess(List<ControllerMedicineLog> logs) {
                    showLoading(false);
                    allLogs = logs;
                    applyFilters();
                }
                
                @Override
                public void onFailure(String error) {
                    showLoading(false);
                    Toast.makeText(RescueInhalerHistoryActivity.this, 
                        "Failed to load logs: " + error, 
                        Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            rescueRepository.getLogsForUser(userId, new RescueInhalerRepository.LoadCallback() {
                @Override
                public void onSuccess(List<RescueInhalerLog> logs) {
                    showLoading(false);
                    allLogs = logs;
                    applyFilters();
                }
                
                @Override
                public void onFailure(String error) {
                    showLoading(false);
                    Toast.makeText(RescueInhalerHistoryActivity.this, 
                        "Failed to load logs: " + error, 
                        Toast.LENGTH_SHORT).show();
                }
            });
        }
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
        List<MedicineLog> filteredLogs = new ArrayList<>();
        boolean isController = rbControllerHistory.isChecked();
        
        for (MedicineLog log : (List<MedicineLog>) allLogs) {
            // Date filter
            if (log.getTimestamp() != null) {
                if (log.getTimestamp().before(startDate) || log.getTimestamp().after(endDate)) {
                    continue;
                }
            }
            
            // Trigger filter
            if (!selectedTriggers.isEmpty()) {
                if (log.getTriggers() == null || log.getTriggers().isEmpty()) {
                    continue;
                }
                boolean hasMatchingTrigger = false;
                for (String trigger : selectedTriggers) {
                    if (log.getTriggers().contains(trigger)) {
                        hasMatchingTrigger = true;
                        break;
                    }
                }
                if (!hasMatchingTrigger) {
                    continue;
                }
            }
            
            filteredLogs.add(log);
        }
        
        if (filteredLogs.isEmpty()) {
            showEmptyMessage(true);
        } else {
            showEmptyMessage(false);
            adapter.setLogs(filteredLogs, isController);
        }
    }
    
    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter, null);
        
        CheckBox cbExercise = dialogView.findViewById(R.id.cbFilterExercise);
        CheckBox cbColdAir = dialogView.findViewById(R.id.cbFilterColdAir);
        CheckBox cbPets = dialogView.findViewById(R.id.cbFilterPets);
        CheckBox cbPollen = dialogView.findViewById(R.id.cbFilterPollen);
        CheckBox cbStress = dialogView.findViewById(R.id.cbFilterStress);
        CheckBox cbSmoke = dialogView.findViewById(R.id.cbFilterSmoke);
        CheckBox cbWeather = dialogView.findViewById(R.id.cbFilterWeather);
        CheckBox cbDust = dialogView.findViewById(R.id.cbFilterDust);
        
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
        
        new AlertDialog.Builder(this)
            .setTitle("Filter Logs")
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
                Calendar cal = Calendar.getInstance();
                endDate = cal.getTime();
                cal.add(Calendar.YEAR, -10);
                startDate = cal.getTime();
                applyFilters();
            })
            .setNeutralButton("Cancel", null)
            .show();
    }
}
