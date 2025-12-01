package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.adapters.BadgeAdapter;
import com.example.b07project.models.Badge;
import com.example.b07project.models.Streak;
import com.example.b07project.services.MotivationService;
import java.util.ArrayList;
import java.util.List;

public class MotivationActivity extends AppCompatActivity {

    private TextView tvControllerStreak, tvControllerLongest;
    private TextView tvTechniqueStreak, tvTechniqueLongest;
    private RecyclerView rvBadges;
    private Button btnSettings, btnClose;
    private BadgeAdapter badgeAdapter;
    private MotivationService motivationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motivation);

        android.util.Log.d("RescueInhalerService", "=== MOTIVATION ACTIVITY CREATED ===");

        motivationService = new MotivationService(this);
        
        // Check for child ID passed from HomeActivity
        String childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        if (childId != null && !childId.isEmpty()) {
            android.util.Log.d("MotivationActivity", "Using child ID: " + childId);
            motivationService.setTargetUserId(childId);
            // Hide settings button if viewing as parent (optional, but good practice)
            // btnSettings.setVisibility(View.GONE); 
        } else {
            android.util.Log.d("MotivationActivity", "Using current user ID");
        }

        initializeViews();
        setupRecyclerView();
        setupListeners();
        
        android.util.Log.d("RescueInhalerService", "Starting cleanup and data load...");
        // Clean up any duplicate badges before loading
        motivationService.cleanupDuplicateBadges(() -> {
            android.util.Log.d("RescueInhalerService", "Cleanup complete, loading data...");
            loadData();
        });
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        tvControllerStreak = findViewById(R.id.tvControllerStreak);
        tvControllerLongest = findViewById(R.id.tvControllerLongest);
        tvTechniqueStreak = findViewById(R.id.tvTechniqueStreak);
        tvTechniqueLongest = findViewById(R.id.tvTechniqueLongest);
        rvBadges = findViewById(R.id.rvBadges);
        btnSettings = findViewById(R.id.btnSettings);
        btnClose = findViewById(R.id.btnClose);
    }

    private void setupRecyclerView() {
        badgeAdapter = new BadgeAdapter(new ArrayList<>());
        rvBadges.setLayoutManager(new LinearLayoutManager(this));
        rvBadges.setAdapter(badgeAdapter);
    }

    private void setupListeners() {
        btnSettings.setOnClickListener(v -> openSettings());
        btnClose.setOnClickListener(v -> finish());
    }

    private void loadData() {
        android.util.Log.d("RescueInhalerService", "loadData() called");
        
        // Update badge progress before loading
        motivationService.checkLowRescueBadge(() -> {
            loadStreaks();
            loadBadges();
        });
    }

    private void loadStreaks() {
        android.util.Log.d("RescueInhalerService", "Loading streaks...");
        // Load controller streak
        motivationService.getStreak("controller", new MotivationService.StreakCallback() {
            @Override
            public void onSuccess(Streak streak) {
                android.util.Log.d("RescueInhalerService", "Controller streak loaded: current=" + 
                    streak.getCurrentStreak() + ", longest=" + streak.getLongestStreak());
                tvControllerStreak.setText(streak.getCurrentStreak() + " days");
                tvControllerLongest.setText("Longest: " + streak.getLongestStreak() + " days");
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.e("RescueInhalerService", "Failed to load controller streak", e);
                Toast.makeText(MotivationActivity.this, "Failed to load controller streak", Toast.LENGTH_SHORT).show();
            }
        });

        // Load technique streak
        motivationService.getStreak("technique", new MotivationService.StreakCallback() {
            @Override
            public void onSuccess(Streak streak) {
                android.util.Log.d("RescueInhalerService", "Technique streak loaded: current=" + 
                    streak.getCurrentStreak() + ", longest=" + streak.getLongestStreak());
                tvTechniqueStreak.setText(streak.getCurrentStreak() + " days");
                tvTechniqueLongest.setText("Longest: " + streak.getLongestStreak() + " days");
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.e("RescueInhalerService", "Failed to load technique streak", e);
                Toast.makeText(MotivationActivity.this, "Failed to load technique streak", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadBadges() {
        android.util.Log.d("RescueInhalerService", "Loading badges from Firestore...");
        motivationService.getAllBadges(new MotivationService.BadgeListCallback() {
            @Override
            public void onSuccess(List<Badge> badges) {
                android.util.Log.d("RescueInhalerService", "Badges loaded successfully: count=" + badges.size());
                for (Badge badge : badges) {
                    android.util.Log.d("RescueInhalerService", "  - Badge: type=" + badge.getType() + 
                        ", name=" + badge.getName() + ", progress=" + badge.getProgress() + 
                        "/" + badge.getRequirement() + ", earned=" + badge.isEarned());
                }
                badgeAdapter.updateBadges(badges);
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.e("RescueInhalerService", "Failed to load badges", e);
                Toast.makeText(MotivationActivity.this, "Failed to load badges", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openSettings() {
        Intent intent = new Intent(this, MotivationSettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("RescueInhalerService", "=== MOTIVATION ACTIVITY RESUMED ===");
        // Refresh data when returning from settings
        loadData();
    }
}
