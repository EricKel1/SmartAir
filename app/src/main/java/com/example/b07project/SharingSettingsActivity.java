package com.example.b07project;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public class SharingSettingsActivity extends AppCompatActivity {

    private String childId;
    private FirebaseFirestore db;
    
    private SwitchMaterial switchMedication;
    private SwitchMaterial switchDailyCheckIn;
    private SwitchMaterial switchSafetyMonitoring;
    private SwitchMaterial switchTriggerPatterns;
    private SwitchMaterial switchStatisticsReports;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sharing_settings);

        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        if (childId == null) {
            Toast.makeText(this, "Error: No child ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        switchMedication = findViewById(R.id.switchMedication);
        switchDailyCheckIn = findViewById(R.id.switchDailyCheckIn);
        switchSafetyMonitoring = findViewById(R.id.switchSafetyMonitoring);
        switchTriggerPatterns = findViewById(R.id.switchTriggerPatterns);
        switchStatisticsReports = findViewById(R.id.switchStatisticsReports);
        btnSave = findViewById(R.id.btnSave);

        loadSettings();

        btnSave.setOnClickListener(v -> saveSettings());
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void loadSettings() {
        db.collection("children").document(childId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> sharing = (Map<String, Object>) documentSnapshot.get("sharingSettings");
                        if (sharing != null) {
                            switchMedication.setChecked(Boolean.TRUE.equals(sharing.get("medication")));
                            switchDailyCheckIn.setChecked(Boolean.TRUE.equals(sharing.get("symptoms")));
                            switchSafetyMonitoring.setChecked(Boolean.TRUE.equals(sharing.get("pef")));
                            switchTriggerPatterns.setChecked(Boolean.TRUE.equals(sharing.get("patterns")));
                            switchStatisticsReports.setChecked(Boolean.TRUE.equals(sharing.get("stats")));
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load settings", Toast.LENGTH_SHORT).show());
    }

    private void saveSettings() {
        Map<String, Object> sharing = new HashMap<>();
        sharing.put("medication", switchMedication.isChecked());
        sharing.put("symptoms", switchDailyCheckIn.isChecked());
        sharing.put("pef", switchSafetyMonitoring.isChecked());
        sharing.put("patterns", switchTriggerPatterns.isChecked());
        sharing.put("stats", switchStatisticsReports.isChecked());

        Map<String, Object> update = new HashMap<>();
        update.put("sharingSettings", sharing);

        db.collection("children").document(childId)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save settings", Toast.LENGTH_SHORT).show());
    }
}
