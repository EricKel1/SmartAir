package com.example.b07project;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.models.ControllerMedicineLog;
import com.example.b07project.models.MedicationSchedule;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.repository.ControllerMedicineRepository;
import com.example.b07project.repository.PEFRepository;
import com.example.b07project.repository.ScheduleRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ParentChildDashboardActivity extends AppCompatActivity {

    private String childId;
    private String childName;
    private TextView tvChildNameHeader, tvCurrentZoneHeader;
    private TextView tvAdherenceDetails;
    private RecyclerView rvAdherenceCalendar;
    private ImageView btnConfigureSchedule;

    private PEFRepository pefRepository;
    private ScheduleRepository scheduleRepository;
    private ControllerMedicineRepository controllerRepository;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_child_dashboard);

        db = FirebaseFirestore.getInstance();
        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        childName = getIntent().getStringExtra("EXTRA_CHILD_NAME");
        
        pefRepository = new PEFRepository();
        scheduleRepository = new ScheduleRepository();
        controllerRepository = new ControllerMedicineRepository();

        initializeViews();
        setupListeners();
        loadChildData();
        loadSharingSettings();
        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack4).setOnClickListener(v -> bh.backTo(this, ParentDashboardActivity.class));

        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
//        TopMover mover = new TopMover(this);
//        mover.adjustTop();
        loadAdherenceData();
    }

    private void initializeViews() {
        tvChildNameHeader = findViewById(R.id.tvChildNameHeader);
        tvCurrentZoneHeader = findViewById(R.id.tvCurrentZoneHeader);
        
        tvAdherenceDetails = findViewById(R.id.tvAdherenceDetails);
        rvAdherenceCalendar = findViewById(R.id.rvAdherenceCalendar);
        rvAdherenceCalendar.setLayoutManager(new GridLayoutManager(this, 7)); // 7 days a week

        btnConfigureSchedule = findViewById(R.id.btnConfigureSchedule);

        if (childName != null) {
            tvChildNameHeader.setText(childName);
        }
    }

    private void setupListeners() {
        // Quick Actions
        setupCardListener(R.id.cardLogMedicine, LogRescueInhalerActivity.class);
        setupCardListener(R.id.cardDailyCheckIn, DailySymptomCheckInActivity.class);
        setupCardListener(R.id.cardEnterPEF, PEFEntryActivity.class);

        // Analytics
        setupCardListener(R.id.cardStats, StatisticsReportsActivity.class);
        setupCardListener(R.id.cardPatterns, TriggerPatternsActivity.class);

        // History
        setupCardListener(R.id.cardHistoryMedicine, RescueInhalerHistoryActivity.class);
        setupCardListener(R.id.cardHistoryCheckIn, SymptomHistoryActivity.class);
        setupCardListener(R.id.cardHistoryPEF, PEFHistoryActivity.class);
        setupCardListener(R.id.cardHistoryIncidents, IncidentHistoryActivity.class);

        // Schedule Config
        View.OnClickListener configListener = v -> {
            Intent intent = new Intent(this, ConfigureScheduleActivity.class);
            intent.putExtra("EXTRA_CHILD_ID", childId);
            startActivity(intent);
        };

        btnConfigureSchedule.setOnClickListener(configListener);
        findViewById(R.id.cardAdherence).setOnClickListener(configListener);
    }

    private void loadAdherenceData() {
        if (childId == null) return;

        scheduleRepository.getSchedule(childId, new ScheduleRepository.LoadCallback() {
            @Override
            public void onSuccess(MedicationSchedule schedule) {
                if (schedule == null) {
                    tvAdherenceDetails.setText("No schedule configured");
                    rvAdherenceCalendar.setAdapter(null);
                    return;
                }

                calculateAdherence(schedule);
            }

            @Override
            public void onFailure(String error) {
                tvAdherenceDetails.setText("Error loading schedule: " + error);
            }
        });
    }

    private void calculateAdherence(MedicationSchedule schedule) {
        // Calculate for last 30 days
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -29); // Go back 29 days to include today (30 days total)
        // Reset to start of day
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date startDate = cal.getTime();

        controllerRepository.getLogsSince(childId, startDate, new ControllerMedicineRepository.LoadCallback() {
            @Override
            public void onSuccess(List<ControllerMedicineLog> logs) {
                List<AdherenceAdapter.DayStatus> dayStatuses = new ArrayList<>();
                SimpleDateFormat dayFormat = new SimpleDateFormat("d", Locale.getDefault());

                Calendar iteratorCal = (Calendar) cal.clone();
                Calendar today = Calendar.getInstance();

                // Normalize schedule start date
                Date scheduleStart = schedule.getStartDate();
                if (scheduleStart == null) {
                    // If no start date, assume it started 30 days ago (so we see data)
                    scheduleStart = startDate;
                }

                // Iterate through the last 30 days
                for (int i = 0; i < 30; i++) {
                    Date currentDate = iteratorCal.getTime();
                    String dayText = dayFormat.format(currentDate);
                    int color;

                    // Check if this day is before the schedule started
                    if (currentDate.before(scheduleStart) && !isSameDay(currentDate, scheduleStart)) {
                        color = Color.parseColor("#E0E0E0"); // Grey
                    } else if (iteratorCal.after(today)) {
                         color = Color.parseColor("#E0E0E0"); // Future (shouldn't happen with logic but safe)
                    } else {
                        // Count logs for this day
                        int logsCount = 0;
                        for (ControllerMedicineLog log : logs) {
                            if (isSameDay(log.getTimestamp(), currentDate)) {
                                logsCount++;
                            }
                        }

                        if (logsCount >= schedule.getFrequency()) {
                            color = Color.parseColor("#4CAF50"); // Green
                        } else if (logsCount > 0) {
                            color = Color.parseColor("#FFC107"); // Yellow
                        } else {
                            color = Color.parseColor("#F44336"); // Red
                        }
                    }

                    dayStatuses.add(new AdherenceAdapter.DayStatus(dayText, color));
                    iteratorCal.add(Calendar.DAY_OF_YEAR, 1);
                }

                AdherenceAdapter adapter = new AdherenceAdapter(ParentChildDashboardActivity.this, dayStatuses);
                rvAdherenceCalendar.setAdapter(adapter);

                tvAdherenceDetails.setText("Last 30 Days History");
            }

            @Override
            public void onFailure(String error) {
                tvAdherenceDetails.setText("Error calculating adherence");
            }
        });
    }

    private boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) return false;
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void setupCardListener(int cardId, Class<?> activityClass) {
        CardView card = findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, activityClass);
                intent.putExtra("EXTRA_CHILD_ID", childId);
                startActivity(intent);
            });
        }
    }

    private void loadChildData() {
        if (childId == null) return;

        pefRepository.getLastPEFReading(childId, new PEFRepository.LoadCallback<com.example.b07project.models.PEFReading>() {
            @Override
            public void onSuccess(com.example.b07project.models.PEFReading reading) {
                if (reading != null && reading.getZone() != null && !reading.getZone().equals("unknown")) {
                    updateZoneDisplay(reading.getZone());
                } else {
                    tvCurrentZoneHeader.setText("Current Zone: No data");
                }
            }

            @Override
            public void onFailure(String error) {
                tvCurrentZoneHeader.setText("Current Zone: Unavailable");
            }
        });
    }

    private void updateZoneDisplay(String zone) {
        String zoneLabel = PersonalBest.getZoneLabel(zone);
        tvCurrentZoneHeader.setText("Current Zone: " + zoneLabel);

        int color = PersonalBest.getZoneColor(zone);
        tvCurrentZoneHeader.setTextColor(color);
    }

    private void loadSharingSettings() {
        if (childId == null) return;

        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Map<String, Boolean> sharingSettings = (Map<String, Boolean>) documentSnapshot.get("sharingSettings");
                    if (sharingSettings != null) {
                        updateBadges(sharingSettings);
                    }
                }
            })
            .addOnFailureListener(e -> {
                // Handle error or just leave badges hidden
            });
    }

    private void updateBadges(Map<String, Boolean> settings) {
        // Medication
        boolean shareMedication = Boolean.TRUE.equals(settings.get("medication"));
        setViewVisibility(R.id.badgeLogMedicine, shareMedication);
        setViewVisibility(R.id.badgeHistoryMedicine, shareMedication);

        // Symptoms
        boolean shareSymptoms = Boolean.TRUE.equals(settings.get("symptoms"));
        setViewVisibility(R.id.badgeDailyCheckIn, shareSymptoms);
        setViewVisibility(R.id.badgeHistoryCheckIn, shareSymptoms);

        // PEF / Safety
        boolean sharePEF = Boolean.TRUE.equals(settings.get("pef"));
        setViewVisibility(R.id.badgeEnterPEF, sharePEF);
        setViewVisibility(R.id.badgeHistoryPEF, sharePEF);
        setViewVisibility(R.id.badgeHistoryIncidents, sharePEF);

        // Patterns
        boolean sharePatterns = Boolean.TRUE.equals(settings.get("patterns"));
        setViewVisibility(R.id.badgePatterns, sharePatterns);

        // Stats
        boolean shareStats = Boolean.TRUE.equals(settings.get("stats"));
        setViewVisibility(R.id.badgeStats, shareStats);
    }

    private void setViewVisibility(int viewId, boolean visible) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChildData(); // Refresh zone on return
        loadSharingSettings();
        loadAdherenceData(); // Refresh adherence
    }
}
