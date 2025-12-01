package com.example.b07project;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.b07project.services.MotivationService;
import java.util.Map;

public class MotivationSettingsActivity extends AppCompatActivity {

    private EditText etTechniqueSessions;
    private EditText etLowRescueThreshold;
    private EditText etLowRescuePeriod;
    private Button btnSave, btnCancel;
    private MotivationService motivationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motivation_settings);

        motivationService = new MotivationService(this);

        initializeViews();
        loadCurrentSettings();
        setupListeners();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        etTechniqueSessions = findViewById(R.id.etTechniqueSessions);
        etLowRescueThreshold = findViewById(R.id.etLowRescueThreshold);
        etLowRescuePeriod = findViewById(R.id.etLowRescuePeriod);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
    }

    private void loadCurrentSettings() {
        Map<String, Integer> thresholds = motivationService.getBadgeThresholds();
        etTechniqueSessions.setText(String.valueOf(thresholds.get("technique_sessions")));
        etLowRescueThreshold.setText(String.valueOf(thresholds.get("low_rescue_threshold")));
        etLowRescuePeriod.setText(String.valueOf(thresholds.get("low_rescue_period")));
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveSettings());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void saveSettings() {
        try {
            int techniqueSessions = Integer.parseInt(etTechniqueSessions.getText().toString());
            int lowRescueThreshold = Integer.parseInt(etLowRescueThreshold.getText().toString());
            int lowRescuePeriod = Integer.parseInt(etLowRescuePeriod.getText().toString());

            // Validate inputs
            if (techniqueSessions < 1 || techniqueSessions > 100) {
                Toast.makeText(this, "Technique sessions must be between 1 and 100", Toast.LENGTH_SHORT).show();
                return;
            }

            if (lowRescueThreshold < 0 || lowRescueThreshold > lowRescuePeriod) {
                Toast.makeText(this, "Rescue threshold must be between 0 and the period length", Toast.LENGTH_SHORT).show();
                return;
            }

            if (lowRescuePeriod < 1 || lowRescuePeriod > 365) {
                Toast.makeText(this, "Period must be between 1 and 365 days", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save settings
            motivationService.updateBadgeThresholds(techniqueSessions, lowRescueThreshold, lowRescuePeriod);
            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
            finish();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
        }
    }
}
