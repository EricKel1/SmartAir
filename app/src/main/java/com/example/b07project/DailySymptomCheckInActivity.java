package com.example.b07project;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.b07project.models.SymptomCheckIn;
import com.example.b07project.repository.SymptomCheckInRepository;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.example.b07project.utils.NotificationHelper;

public class DailySymptomCheckInActivity extends AppCompatActivity {

    private TextView tvDate, tvSymptomLevelValue, tvMessage;
    private Slider sliderSymptomLevel;
    private CheckBox cbWheezing, cbCoughing, cbShortnessOfBreath, cbChestTightness, cbNighttimeSymptoms;
    private CheckBox cbTriggerExercise, cbTriggerColdAir, cbTriggerPets, cbTriggerPollen;
    private CheckBox cbTriggerStress, cbTriggerSmoke, cbTriggerWeather, cbTriggerDust;
    private EditText etNotes;
    private Button btnSave;
    private ProgressBar progress;

    private Date todayDate;
    private SymptomCheckInRepository repository;
    private SimpleDateFormat dateFormat;
    private String childId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_symptom_checkin);

        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");

        initializeViews();
        repository = new SymptomCheckInRepository();
        dateFormat = new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault());

        // Set today's date
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        todayDate = calendar.getTime();
        tvDate.setText(dateFormat.format(todayDate));

        setupListeners();
        checkForExistingCheckIn();

        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack5).setOnClickListener(v ->finish());

        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        tvDate = findViewById(R.id.tvDate);
        tvSymptomLevelValue = findViewById(R.id.tvSymptomLevelValue);
        tvMessage = findViewById(R.id.tvMessage);
        sliderSymptomLevel = findViewById(R.id.sliderSymptomLevel);
        cbWheezing = findViewById(R.id.cbWheezing);
        cbCoughing = findViewById(R.id.cbCoughing);
        cbShortnessOfBreath = findViewById(R.id.cbShortnessOfBreath);
        cbChestTightness = findViewById(R.id.cbChestTightness);
        cbNighttimeSymptoms = findViewById(R.id.cbNighttimeSymptoms);
        cbTriggerExercise = findViewById(R.id.cbTriggerExercise);
        cbTriggerColdAir = findViewById(R.id.cbTriggerColdAir);
        cbTriggerPets = findViewById(R.id.cbTriggerPets);
        cbTriggerPollen = findViewById(R.id.cbTriggerPollen);
        cbTriggerStress = findViewById(R.id.cbTriggerStress);
        cbTriggerSmoke = findViewById(R.id.cbTriggerSmoke);
        cbTriggerWeather = findViewById(R.id.cbTriggerWeather);
        cbTriggerDust = findViewById(R.id.cbTriggerDust);
        etNotes = findViewById(R.id.etNotes);
        btnSave = findViewById(R.id.btnSave);
        progress = findViewById(R.id.progress);
    }

    private void setupListeners() {
        sliderSymptomLevel.addOnChangeListener((slider, value, fromUser) -> {
            tvSymptomLevelValue.setText(String.valueOf((int) value));
            updateSymptomLevelColor((int) value);
        });

        btnSave.setOnClickListener(v -> saveCheckIn());
    }

    private void updateSymptomLevelColor(int level) {
        int color;
        switch (level) {
            case 1:
                color = 0xFF4CAF50; // Green
                break;
            case 2:
                color = 0xFF8BC34A; // Light green
                break;
            case 3:
                color = 0xFFFF9800; // Orange
                break;
            case 4:
                color = 0xFFFF5722; // Deep orange
                break;
            case 5:
                color = 0xFFF44336; // Red
                break;
            default:
                color = 0xFFFF9800;
        }
        tvSymptomLevelValue.setTextColor(color);
    }

    private void checkForExistingCheckIn() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String targetUserId = (childId != null) ? childId : currentUser.getUid();

        showLoading(true);
        repository.checkIfCheckInExistsForDate(targetUserId, todayDate,
            new SymptomCheckInRepository.CheckInExistsCallback() {
                @Override
                public void onResult(boolean exists, SymptomCheckIn existingCheckIn) {
                    showLoading(false);
                    if (exists) {
                        loadExistingCheckIn(existingCheckIn);
                        showMessage("You already checked in today. You can update your check-in.", false);
                    }
                }

                @Override
                public void onFailure(String error) {
                    showLoading(false);
                }
            });
    }

    private void loadExistingCheckIn(SymptomCheckIn checkIn) {
        sliderSymptomLevel.setValue(checkIn.getSymptomLevel());
        tvSymptomLevelValue.setText(String.valueOf(checkIn.getSymptomLevel()));
        updateSymptomLevelColor(checkIn.getSymptomLevel());

        if (checkIn.getSymptoms() != null) {
            cbWheezing.setChecked(checkIn.getSymptoms().contains("wheezing"));
            cbCoughing.setChecked(checkIn.getSymptoms().contains("coughing"));
            cbShortnessOfBreath.setChecked(checkIn.getSymptoms().contains("shortness of breath"));
            cbChestTightness.setChecked(checkIn.getSymptoms().contains("chest tightness"));
            cbNighttimeSymptoms.setChecked(checkIn.getSymptoms().contains("nighttime symptoms"));
        }

        if (checkIn.getTriggers() != null) {
            cbTriggerExercise.setChecked(checkIn.getTriggers().contains("exercise"));
            cbTriggerColdAir.setChecked(checkIn.getTriggers().contains("cold air"));
            cbTriggerPets.setChecked(checkIn.getTriggers().contains("pets"));
            cbTriggerPollen.setChecked(checkIn.getTriggers().contains("pollen"));
            cbTriggerStress.setChecked(checkIn.getTriggers().contains("stress"));
            cbTriggerSmoke.setChecked(checkIn.getTriggers().contains("smoke"));
            cbTriggerWeather.setChecked(checkIn.getTriggers().contains("weather change"));
            cbTriggerDust.setChecked(checkIn.getTriggers().contains("dust"));
        }

        if (checkIn.getNotes() != null) {
            etNotes.setText(checkIn.getNotes());
        }
    }

    private void saveCheckIn() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showMessage("Please sign in to save check-in", true);
            return;
        }

        showLoading(true);

        String targetUserId = (childId != null) ? childId : currentUser.getUid();
        android.util.Log.d("childparentlink", "DailySymptomCheckInActivity: Saving check-in for targetUserId: " + targetUserId);

        int symptomLevel = (int) sliderSymptomLevel.getValue();
        List<String> symptoms = getSelectedSymptoms();
        List<String> triggers = getSelectedTriggers();
        String notes = etNotes.getText().toString().trim();
        
        // Determine enteredBy
        String enteredBy = "Child";
        if (!targetUserId.equals(currentUser.getUid())) {
            // Check if we are in "Child Mode" via DeviceChooser (Parent logged in but acting as Child)
            android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            String lastRole = prefs.getString("last_role", "");
            String lastChildId = prefs.getString("last_child_id", "");
            
            if ("child".equals(lastRole) && targetUserId.equals(lastChildId)) {
                enteredBy = "Child";
            } else {
                enteredBy = "Parent";
            }
        }

        SymptomCheckIn checkIn = new SymptomCheckIn(
            targetUserId,
            todayDate,
            symptomLevel,
            symptoms,
            triggers,
            notes.isEmpty() ? null : notes,
            new Date()
        );
        checkIn.setEnteredBy(enteredBy);

        repository.saveCheckIn(checkIn, new SymptomCheckInRepository.SaveCallback() {
            @Override
            public void onSuccess(String documentId) {
                // Check for Triage Escalation
                if (symptomLevel >= 4) {
                    sendAlertWithChildName(targetUserId, "Triage Escalation Alert", "reported severe symptoms (Level " + symptomLevel + "). Please check on them immediately.");
                }

                showLoading(false);
                Toast.makeText(DailySymptomCheckInActivity.this,
                    "Symptom check-in saved successfully!",
                    Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onFailure(String error) {
                showLoading(false);
                showMessage("Failed to save check-in: " + error, true);
            }
        });
    }

    private void sendAlertWithChildName(String targetUserId, String title, String messageSuffix) {
        FirebaseFirestore.getInstance().collection("users").document(targetUserId).get()
            .addOnSuccessListener(documentSnapshot -> {
                String name = documentSnapshot.getString("name");
                if (name == null || name.isEmpty()) {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null && user.getUid().equals(targetUserId) && user.getDisplayName() != null) {
                        name = user.getDisplayName();
                    }
                }
                if (name == null || name.isEmpty()) {
                    name = "Child";
                }
                
                NotificationHelper.sendAlert(DailySymptomCheckInActivity.this, targetUserId, title, name + " " + messageSuffix);
            })
            .addOnFailureListener(e -> {
                NotificationHelper.sendAlert(DailySymptomCheckInActivity.this, targetUserId, title, "Child " + messageSuffix);
            });
    }

    private List<String> getSelectedSymptoms() {
        List<String> symptoms = new ArrayList<>();
        if (cbWheezing.isChecked()) symptoms.add("wheezing");
        if (cbCoughing.isChecked()) symptoms.add("coughing");
        if (cbShortnessOfBreath.isChecked()) symptoms.add("shortness of breath");
        if (cbChestTightness.isChecked()) symptoms.add("chest tightness");
        if (cbNighttimeSymptoms.isChecked()) symptoms.add("nighttime symptoms");
        return symptoms;
    }

    private List<String> getSelectedTriggers() {
        List<String> triggers = new ArrayList<>();
        if (cbTriggerExercise.isChecked()) triggers.add("exercise");
        if (cbTriggerColdAir.isChecked()) triggers.add("cold air");
        if (cbTriggerPets.isChecked()) triggers.add("pets");
        if (cbTriggerPollen.isChecked()) triggers.add("pollen");
        if (cbTriggerStress.isChecked()) triggers.add("stress");
        if (cbTriggerSmoke.isChecked()) triggers.add("smoke");
        if (cbTriggerWeather.isChecked()) triggers.add("weather change");
        if (cbTriggerDust.isChecked()) triggers.add("dust");
        return triggers;
    }

    private void showLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
        sliderSymptomLevel.setEnabled(!show);
    }

    private void showMessage(String message, boolean isError) {
        tvMessage.setText(message);
        tvMessage.setTextColor(getColor(isError ? android.R.color.holo_red_dark : android.R.color.holo_green_dark));
        tvMessage.setVisibility(View.VISIBLE);
    }
}
