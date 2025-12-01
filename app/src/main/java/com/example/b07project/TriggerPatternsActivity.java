package com.example.b07project;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.b07project.adapters.TriggerStatsAdapter;
import com.example.b07project.repository.TriggerAnalyticsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TriggerPatternsActivity extends AppCompatActivity {

    private Button btnWeek, btnMonth, btnAll;
    private ProgressBar progress;
    private TextView tvNoData;
    private CardView cardTopTriggers, cardSeverityCorrelation;
    private RecyclerView rvTopTriggers, rvSeverityCorrelation;

    private TriggerAnalyticsRepository repository;
    private TriggerStatsAdapter topTriggersAdapter;
    private TriggerStatsAdapter severityAdapter;

    private Date startDate;
    private Date endDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trigger_patterns);

        initializeViews();
        repository = new TriggerAnalyticsRepository();

        setupRecyclerViews();
        setupListeners();

        // Default to this month
        setTimeRange(TimeRange.MONTH);
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        com.example.b07project.TopMover mover = new com.example.b07project.TopMover(this);
        mover.adjustTop();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());


    }

    private void initializeViews() {
        btnWeek = findViewById(R.id.btnWeek);
        btnMonth = findViewById(R.id.btnMonth);
        btnAll = findViewById(R.id.btnAll);
        progress = findViewById(R.id.progress);
        tvNoData = findViewById(R.id.tvNoData);
        cardTopTriggers = findViewById(R.id.cardTopTriggers);
        cardSeverityCorrelation = findViewById(R.id.cardSeverityCorrelation);
        rvTopTriggers = findViewById(R.id.rvTopTriggers);
        rvSeverityCorrelation = findViewById(R.id.rvSeverityCorrelation);
    }

    private void setupRecyclerViews() {
        topTriggersAdapter = new TriggerStatsAdapter();
        rvTopTriggers.setLayoutManager(new LinearLayoutManager(this));
        rvTopTriggers.setAdapter(topTriggersAdapter);

        severityAdapter = new TriggerStatsAdapter();
        rvSeverityCorrelation.setLayoutManager(new LinearLayoutManager(this));
        rvSeverityCorrelation.setAdapter(severityAdapter);
    }

    private void setupListeners() {
        btnWeek.setOnClickListener(v -> setTimeRange(TimeRange.WEEK));
        btnMonth.setOnClickListener(v -> setTimeRange(TimeRange.MONTH));
        btnAll.setOnClickListener(v -> setTimeRange(TimeRange.ALL));
    }

    private enum TimeRange {
        WEEK, MONTH, ALL
    }

    private void setTimeRange(TimeRange range) {
        // Update button states
        btnWeek.setBackgroundColor(range == TimeRange.WEEK ? 
            getColor(R.color.primary_blue) : getColor(android.R.color.transparent));
        btnMonth.setBackgroundColor(range == TimeRange.MONTH ? 
            getColor(R.color.primary_blue) : getColor(android.R.color.transparent));
        btnAll.setBackgroundColor(range == TimeRange.ALL ? 
            getColor(R.color.primary_blue) : getColor(android.R.color.transparent));

        btnWeek.setTextColor(range == TimeRange.WEEK ? 
            getColor(android.R.color.white) : getColor(R.color.primary_blue));
        btnMonth.setTextColor(range == TimeRange.MONTH ? 
            getColor(android.R.color.white) : getColor(R.color.primary_blue));
        btnAll.setTextColor(range == TimeRange.ALL ? 
            getColor(android.R.color.white) : getColor(R.color.primary_blue));

        // Calculate date range
        Calendar cal = Calendar.getInstance();
        endDate = cal.getTime();

        switch (range) {
            case WEEK:
                startDate = TriggerAnalyticsRepository.getStartOfWeek();
                break;
            case MONTH:
                startDate = TriggerAnalyticsRepository.getStartOfMonth();
                break;
            case ALL:
                cal.add(Calendar.YEAR, -10); // Go back 10 years
                startDate = cal.getTime();
                break;
        }

        loadData();
    }

    private void loadData() {
        String userId;
        if (getIntent().hasExtra("EXTRA_CHILD_ID")) {
            userId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        } else {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                tvNoData.setText("Please sign in to view trigger patterns");
                tvNoData.setVisibility(View.VISIBLE);
                return;
            }
            userId = currentUser.getUid();
        }

        showLoading(true);

        repository.getTriggerStatistics(userId, startDate, endDate,
            new TriggerAnalyticsRepository.TriggerStatsCallback() {
                @Override
                public void onSuccess(Map<String, TriggerAnalyticsRepository.TriggerStats> triggerStats) {
                    showLoading(false);

                    // Convert to list and filter out zero counts
                    List<TriggerAnalyticsRepository.TriggerStats> statsList = new ArrayList<>();
                    for (TriggerAnalyticsRepository.TriggerStats stats : triggerStats.values()) {
                        if (stats.totalCount > 0) {
                            statsList.add(stats);
                        }
                    }

                    if (statsList.isEmpty()) {
                        showNoData();
                        return;
                    }

                    // Sort by total count (descending)
                    Collections.sort(statsList, new Comparator<TriggerAnalyticsRepository.TriggerStats>() {
                        @Override
                        public int compare(TriggerAnalyticsRepository.TriggerStats a, TriggerAnalyticsRepository.TriggerStats b) {
                            return Integer.compare(b.totalCount, a.totalCount);
                        }
                    });

                    topTriggersAdapter.setStats(statsList);
                    cardTopTriggers.setVisibility(View.VISIBLE);

                    // Filter for severity correlation (only items with symptom data)
                    List<TriggerAnalyticsRepository.TriggerStats> severityList = new ArrayList<>();
                    for (TriggerAnalyticsRepository.TriggerStats stats : statsList) {
                        if (stats.symptomCheckInCount > 0) {
                            severityList.add(stats);
                        }
                    }

                    if (!severityList.isEmpty()) {
                        // Sort by average severity (descending)
                        Collections.sort(severityList, new Comparator<TriggerAnalyticsRepository.TriggerStats>() {
                            @Override
                            public int compare(TriggerAnalyticsRepository.TriggerStats a, TriggerAnalyticsRepository.TriggerStats b) {
                                return Double.compare(b.averageSeverity, a.averageSeverity);
                            }
                        });

                        severityAdapter.setStats(severityList);
                        cardSeverityCorrelation.setVisibility(View.VISIBLE);
                    }

                    tvNoData.setVisibility(View.GONE);
                }

                @Override
                public void onFailure(String error) {
                    showLoading(false);
                    tvNoData.setText("Failed to load trigger patterns: " + error);
                    tvNoData.setVisibility(View.VISIBLE);
                }
            });
    }

    private void showLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
        cardTopTriggers.setVisibility(View.GONE);
        cardSeverityCorrelation.setVisibility(View.GONE);
    }

    private void showNoData() {
        tvNoData.setText("No trigger data available yet.\nStart logging your symptoms and medicine use!");
        tvNoData.setVisibility(View.VISIBLE);
        cardTopTriggers.setVisibility(View.GONE);
        cardSeverityCorrelation.setVisibility(View.GONE);
    }

    public static class TopMover {
        private Activity activity;

        public TopMover(Activity activity) {
            this.activity = activity;
        }

        public void adjustForStatusBar() {
            // Use the activity reference
            View rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);

            int statusBarHeight = 0;
            int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
            }

            // Apply top padding to prevent UI from overlapping the status bar
            rootView.setPadding(0, statusBarHeight, 0, 0);
        }
    }
}
