package com.example.b07project;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.models.ControllerMedicineLog;
import com.example.b07project.models.MedicationSchedule;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.repository.ControllerMedicineRepository;
import com.example.b07project.repository.PEFRepository;
import com.example.b07project.repository.ScheduleRepository;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
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

        loadAdherenceData();

        showOnboarding();
    }

    private void showOnboarding() {
        SharedPreferences prefs = getSharedPreferences("onboarding", MODE_PRIVATE);
        boolean hasBeenShown = prefs.getBoolean("has_shown_child_dashboard_onboarding_v2", false);

        if (!hasBeenShown) {
            new TapTargetSequence(this)
                .targets(
                    TapTarget.forView(findViewById(R.id.cardLogMedicine), "Quick Actions", "Use these cards to quickly log events like medication, symptoms, and Peak Flow.").id(1),
                    TapTarget.forView(findViewById(R.id.cardAdherence), "Medication Adherence", "Set up the expected medication schedule for adherence tracking here.").id(2),
                    TapTarget.forView(findViewById(R.id.cardStats), "Statistics and Reports", "Tap here to see detailed charts and statistics of your child's data over time.").id(3),
                    TapTarget.forView(findViewById(R.id.cardPatterns), "Trigger Patterns", "Discover what might be triggering your child's asthma symptoms.").id(4),
                    TapTarget.forView(findViewById(R.id.cardHistoryMedicine), "History Logs", "Review past entries for medicine, symptoms, Peak Flow, and more.").id(5))
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        prefs.edit().putBoolean("has_shown_child_dashboard_onboarding_v2", true).apply();
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                        final NestedScrollView nestedScrollView = findViewById(R.id.nested_scroll_view);
                        if (nestedScrollView == null) return;

                        // After step 4, scroll down to see the lower cards
                        if (lastTarget.id() == 4) {
                            nestedScrollView.post(() -> nestedScrollView.fullScroll(View.FOCUS_DOWN));
                        }
                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {}
                }).start();
        }
    }

    private void initializeViews() {
        tvChildNameHeader = findViewById(R.id.tvChildNameHeader);
        tvCurrentZoneHeader = findViewById(R.id.tvCurrentZoneHeader);

        tvAdherenceDetails = findViewById(R.id.tvAdherenceDetails);
        rvAdherenceCalendar = findViewById(R.id.rvAdherenceCalendar);
        rvAdherenceCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        rvAdherenceCalendar.setNestedScrollingEnabled(false);

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
        cal.add(Calendar.DAY_OF_YEAR, -29);
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

                Date scheduleStart = schedule.getStartDate();
                if (scheduleStart == null) {
                    scheduleStart = startDate;
                }

                for (int i = 0; i < 30; i++) {
                    Date currentDate = iteratorCal.getTime();
                    String dayText = dayFormat.format(currentDate);
                    int color;

                    if (currentDate.before(scheduleStart) && !isSameDay(currentDate, scheduleStart)) {
                        color = Color.parseColor("#E0E0E0");
                    } else if (iteratorCal.after(today)) {
                         color = Color.parseColor("#E0E0E0");
                    } else {
                        int logsCount = 0;
                        for (ControllerMedicineLog log : logs) {
                            if (isSameDay(log.getTimestamp(), currentDate)) {
                                logsCount++;
                            }
                        }

                        if (logsCount >= schedule.getFrequency()) {
                            color = Color.parseColor("#4CAF50");
                        } else if (logsCount > 0) {
                            color = Color.parseColor("#FFC107");
                        } else {
                            color = Color.parseColor("#F44336");
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
        boolean shareMedication = Boolean.TRUE.equals(settings.get("medication"));
        setViewVisibility(R.id.badgeLogMedicine, shareMedication);
        setViewVisibility(R.id.badgeHistoryMedicine, shareMedication);

        boolean shareSymptoms = Boolean.TRUE.equals(settings.get("symptoms"));
        setViewVisibility(R.id.badgeDailyCheckIn, shareSymptoms);
        setViewVisibility(R.id.badgeHistoryCheckIn, shareSymptoms);

        boolean sharePEF = Boolean.TRUE.equals(settings.get("pef"));
        setViewVisibility(R.id.badgeEnterPEF, sharePEF);
        setViewVisibility(R.id.badgeHistoryPEF, sharePEF);
        setViewVisibility(R.id.badgeHistoryIncidents, sharePEF);

        boolean sharePatterns = Boolean.TRUE.equals(settings.get("patterns"));
        setViewVisibility(R.id.badgePatterns, sharePatterns);

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
        loadChildData();
        loadSharingSettings();
        loadAdherenceData();
    }
}
