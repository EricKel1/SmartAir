package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.repository.PEFRepository;
import com.example.b07project.repository.ScheduleRepository;
import com.example.b07project.repository.ControllerMedicineRepository;
import com.example.b07project.models.MedicationSchedule;
import com.example.b07project.models.ControllerMedicineLog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import androidx.cardview.widget.CardView;

import android.widget.ImageButton;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private Button btnLogRescueInhaler, btnViewHistory, btnDailyCheckIn, btnViewSymptomHistory, btnViewPatterns, btnSignOut, btnSwitchProfile;
    private Button btnEmergencyTriage, btnEnterPEF, btnViewPEFHistory, btnViewIncidents, btnInhalerTechnique, btnMotivation, btnStatisticsReports;
    private Button btnInventory;
    private ImageButton btnNotifications;
    private TextView tvCurrentZone, tvZonePercentage, tvViewingChildNotice, tvMedicationSchedule;
    private CardView cardMedicationSchedule;
    private PEFRepository pefRepository;
    private ScheduleRepository scheduleRepository;
    private ControllerMedicineRepository controllerRepository;
    private String dataOwnerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        pefRepository = new PEFRepository();
        scheduleRepository = new ScheduleRepository();
        controllerRepository = new ControllerMedicineRepository();
        initializeViews();
        setupListeners();

        String childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        String childName = getIntent().getStringExtra("EXTRA_CHILD_NAME");

        if (childId != null) {
            // Child Mode
            dataOwnerId = childId;
            android.util.Log.d("HomeActivity", "onCreate: Child Mode. dataOwnerId=" + dataOwnerId);
            tvViewingChildNotice.setText("Viewing child: " + (childName != null ? childName : "Child"));
            tvViewingChildNotice.setVisibility(View.VISIBLE);
            if (btnSignOut != null) btnSignOut.setVisibility(View.VISIBLE);
            
            android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            boolean isLocked = prefs.getBoolean("is_locked", false);
            
            if (btnSwitchProfile != null) {
                btnSwitchProfile.setVisibility(isLocked ? View.GONE : View.VISIBLE);
            }
        } else {
            // Parent Mode (or default)
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                dataOwnerId = user.getUid();
                android.util.Log.d("HomeActivity", "onCreate: User Mode. dataOwnerId=" + dataOwnerId);
            } else {
                android.util.Log.e("HomeActivity", "onCreate: No user logged in!");
            }
            tvViewingChildNotice.setVisibility(View.GONE);
            if (btnSignOut != null) btnSignOut.setVisibility(View.VISIBLE);
            if (btnSwitchProfile != null) btnSwitchProfile.setVisibility(View.GONE);
        }
        
        // Hide Inventory button if the current user is a child
        // We can check the role from Firestore, or pass it in intent.
        // For now, let's check if we are in "Child Mode" (Provider viewing) or if the logged in user is a child.
        // Actually, the user said "remove manage inventory from the kids page".
        // If I am a child logged in, I see this page.
        // If I am a parent logged in, I see ParentDashboardActivity.
        // If I am a provider, I see ProviderHomeActivity -> HomeActivity (Child Mode).
        
        // So if I am here, I am either a Child (logged in) or a Provider (viewing child).
        // In both cases, "Manage Inventory" should probably be hidden?
        // Requirement: "Inventory (Parent): Track purchase date..."
        // So only Parent should see it.
        // But Parent doesn't use HomeActivity anymore, they use ParentDashboardActivity.
        // So HomeActivity is ONLY for Child (self) or Provider (viewing child).
        // Therefore, Inventory button should ALWAYS be hidden in HomeActivity.
        if (btnInventory != null) {
            btnInventory.setVisibility(View.GONE);
        }

        loadZoneStatus();
        loadMedicationSchedule();
        checkNotificationPermission();
        loadSharingSettings();

    }

    private void loadMedicationSchedule() {
        if (dataOwnerId == null) return;

        scheduleRepository.getSchedule(dataOwnerId, new ScheduleRepository.LoadCallback() {
            @Override
            public void onSuccess(MedicationSchedule schedule) {
                if (schedule != null && schedule.getFrequency() > 0) {
                    calculateRemainingDoses(schedule.getFrequency());
                } else {
                    cardMedicationSchedule.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(String error) {
                cardMedicationSchedule.setVisibility(View.GONE);
            }
        });
    }

    private void calculateRemainingDoses(int frequency) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfDay = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date endOfDay = cal.getTime();

        controllerRepository.getLogsForUserInDateRange(dataOwnerId, startOfDay, endOfDay, new ControllerMedicineRepository.LoadCallback() {
            @Override
            public void onSuccess(List<ControllerMedicineLog> logs) {
                int takenCount = logs.size();
                int remaining = frequency - takenCount;
                
                cardMedicationSchedule.setVisibility(View.VISIBLE);
                if (remaining <= 0) {
                    tvMedicationSchedule.setText("All doses taken today! (" + takenCount + "/" + frequency + ")");
                    tvMedicationSchedule.setTextColor(ContextCompat.getColor(HomeActivity.this, android.R.color.holo_green_dark));
                } else {
                    tvMedicationSchedule.setText("Take " + remaining + " more dose(s) today (" + takenCount + "/" + frequency + ")");
                    // Use a standard color if primary_blue isn't guaranteed, or use the one from xml
                    tvMedicationSchedule.setTextColor(0xFF00838F); // Cyan 800
                }
            }

            @Override
            public void onFailure(String error) {
                // Fallback if logs fail to load
                tvMedicationSchedule.setText("Daily Goal: " + frequency + " doses");
                cardMedicationSchedule.setVisibility(View.VISIBLE);
            }
        });
    }

    private com.google.firebase.firestore.ListenerRegistration sharingSettingsListener;

    private void loadSharingSettings() {
        if (dataOwnerId == null) {
            android.util.Log.e("sharedpermissionsindicator", "loadSharingSettings: dataOwnerId is null");
            return;
        }

        android.util.Log.d("sharedpermissionsindicator", "loadSharingSettings: Fetching settings for " + dataOwnerId);

        if (sharingSettingsListener != null) {
            sharingSettingsListener.remove();
        }

        sharingSettingsListener = FirebaseFirestore.getInstance().collection("children").document(dataOwnerId)
            .addSnapshotListener((documentSnapshot, e) -> {
                if (e != null) {
                    android.util.Log.e("sharedpermissionsindicator", "loadSharingSettings: Listen failed", e);
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    android.util.Log.d("sharedpermissionsindicator", "loadSharingSettings: Document exists");
                    java.util.Map<String, Boolean> sharingSettings = (java.util.Map<String, Boolean>) documentSnapshot.get("sharingSettings");
                    if (sharingSettings != null) {
                        android.util.Log.d("sharedpermissionsindicator", "loadSharingSettings: Settings found: " + sharingSettings.toString());
                        updateBadges(sharingSettings);
                    } else {
                        android.util.Log.d("sharedpermissionsindicator", "loadSharingSettings: sharingSettings field is null");
                    }
                } else {
                    android.util.Log.d("sharedpermissionsindicator", "loadSharingSettings: Document does not exist");
                }
            });

    }

    private void updateBadges(java.util.Map<String, Boolean> settings) {
        // Medication
        boolean shareMedication = Boolean.TRUE.equals(settings.get("medication"));
        android.util.Log.d("sharedpermissionsindicator", "updateBadges: medication=" + shareMedication);
        setViewVisibility(R.id.badgeMedicationSchedule, shareMedication);
        setViewVisibility(R.id.badgeRescueInhaler, shareMedication);

        // Symptoms
        boolean shareSymptoms = Boolean.TRUE.equals(settings.get("symptoms"));
        android.util.Log.d("sharedpermissionsindicator", "updateBadges: symptoms=" + shareSymptoms);
        setViewVisibility(R.id.badgeSymptomCheckIn, shareSymptoms);

        // PEF / Safety
        boolean sharePEF = Boolean.TRUE.equals(settings.get("pef"));
        android.util.Log.d("sharedpermissionsindicator", "updateBadges: pef=" + sharePEF);
        setViewVisibility(R.id.badgeSafetyMonitoring, sharePEF);

        // Patterns
        boolean sharePatterns = Boolean.TRUE.equals(settings.get("patterns"));
        android.util.Log.d("sharedpermissionsindicator", "updateBadges: patterns=" + sharePatterns);
        setViewVisibility(R.id.badgeTriggerPatterns, sharePatterns);

        // Stats
        boolean shareStats = Boolean.TRUE.equals(settings.get("stats"));
        android.util.Log.d("sharedpermissionsindicator", "updateBadges: stats=" + shareStats);
        setViewVisibility(R.id.badgeMotivation, shareStats);
    }

    private void setViewVisibility(int viewId, boolean visible) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void initializeViews() {
        btnLogRescueInhaler = findViewById(R.id.btnLogRescueInhaler);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnDailyCheckIn = findViewById(R.id.btnDailyCheckIn);
        btnViewSymptomHistory = findViewById(R.id.btnViewSymptomHistory);
        btnViewPatterns = findViewById(R.id.btnViewPatterns);
        btnEmergencyTriage = findViewById(R.id.btnEmergencyTriage);
        btnEnterPEF = findViewById(R.id.btnEnterPEF);
        btnViewPEFHistory = findViewById(R.id.btnViewPEFHistory);
        btnViewIncidents = findViewById(R.id.btnViewIncidents);
        btnInhalerTechnique = findViewById(R.id.btnInhalerTechnique);
        btnMotivation = findViewById(R.id.btnMotivation);
        btnStatisticsReports = findViewById(R.id.btnStatisticsReports);
        btnInventory = findViewById(R.id.btnInventory);
        btnSignOut = findViewById(R.id.btnSignOut);
        btnSwitchProfile = findViewById(R.id.btnSwitchProfile);
        btnNotifications = findViewById(R.id.btnNotifications);

        tvCurrentZone = findViewById(R.id.tvCurrentZone);
        tvZonePercentage = findViewById(R.id.tvZonePercentage);
        tvViewingChildNotice = findViewById(R.id.tvViewingChildNotice);
        
        tvMedicationSchedule = findViewById(R.id.tvMedicationSchedule);
        cardMedicationSchedule = findViewById(R.id.cardMedicationSchedule);
    }

    private void setupListeners() {
        btnLogRescueInhaler.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogRescueInhalerActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        if (btnInventory != null) {
            btnInventory.setOnClickListener(v -> {
                startActivity(new Intent(this, InventoryActivity.class));
            });
        }

        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationCenterActivity.class));
            });
        }

        btnViewHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, RescueInhalerHistoryActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnDailyCheckIn.setOnClickListener(v -> {
            Intent intent = new Intent(this, DailySymptomCheckInActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnViewSymptomHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, SymptomHistoryActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnViewPatterns.setOnClickListener(v -> {
            Intent intent = new Intent(this, TriggerPatternsActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnEmergencyTriage.setOnClickListener(v -> {
            Intent intent = new Intent(this, TriageActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnEnterPEF.setOnClickListener(v -> {
            Intent intent = new Intent(this, PEFEntryActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnViewPEFHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, PEFHistoryActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnViewIncidents.setOnClickListener(v -> {
            Intent intent = new Intent(this, IncidentHistoryActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnInhalerTechnique.setOnClickListener(v -> {
            Intent intent = new Intent(this, InhalerTechniqueActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnMotivation.setOnClickListener(v -> {
            Intent intent = new Intent(this, MotivationActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnStatisticsReports.setOnClickListener(v -> {
            Intent intent = new Intent(this, StatisticsReportsActivity.class);
            if (dataOwnerId != null) {
                intent.putExtra("EXTRA_CHILD_ID", dataOwnerId);
            }
            startActivity(intent);
        });

        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        if (btnSwitchProfile != null) {
            btnSwitchProfile.setOnClickListener(v -> {
                // Clear last child preference
                android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.remove("last_child_id");
                editor.remove("last_child_name");
                editor.remove("last_role");
                editor.remove("is_locked");
                editor.apply();

                Intent intent = new Intent(this, DeviceChooserActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void loadZoneStatus() {
        if (dataOwnerId == null) return; // Don't load if no user/child is set

        pefRepository.getLastPEFReading(dataOwnerId, new PEFRepository.LoadCallback<com.example.b07project.models.PEFReading>() {
            @Override
            public void onSuccess(com.example.b07project.models.PEFReading reading) {
                if (reading != null && reading.getZone() != null && !reading.getZone().equals("unknown")) {
                    updateZoneDisplay(reading.getZone(), reading.getPercentageOfPB());
                } else {
                    tvCurrentZone.setText("No data yet");
                    tvZonePercentage.setText("");
                }
            }

            @Override
            public void onFailure(String error) {
                tvCurrentZone.setText("No data yet");
                tvZonePercentage.setText("");
            }
        });
    }

    private void updateZoneDisplay(String zone, int percentage) {
        String zoneLabel = PersonalBest.getZoneLabel(zone);
        tvCurrentZone.setText(zoneLabel);
        tvZonePercentage.setText("(" + percentage + "% of PB)");

        int color = PersonalBest.getZoneColor(zone);
        tvCurrentZone.setTextColor(color);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadZoneStatus(); // Refresh zone when returning to home
        loadSharingSettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sharingSettingsListener != null) {
            sharingSettingsListener.remove();
        }
    }
}
