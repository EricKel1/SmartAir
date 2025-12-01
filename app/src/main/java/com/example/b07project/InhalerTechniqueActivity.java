package com.example.b07project;

import android.os.Bundle;
import android.view.ViewGroup;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.b07project.services.MotivationService;
import com.example.b07project.models.Badge;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

public class InhalerTechniqueActivity extends AppCompatActivity {

    private YouTubePlayerView youtubePlayerView;
    private Button btnStartTimer, btnClose;
    private TextView tvTimerDisplay;
    private CountDownTimer practiceTimer;
    private MotivationService motivationService;
    private static final String VIDEO_ID = "2i9_DelNqs4";
    private nl.dionsegijn.konfetti.xml.KonfettiView konfettiView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inhaler_technique);

        motivationService = new MotivationService(this);
        
        String childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        if (childId != null && !childId.isEmpty()) {
            motivationService.setTargetUserId(childId);
        }

        // Register badge earned callback to show celebration
        motivationService.setBadgeEarnedCallback(badge -> runOnUiThread(() -> showBadgeEarnedNotification(badge)));
        initializeViews();
        setupYouTubePlayer();
        setupListeners();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void showBadgeEarnedNotification(Badge badge) {
        // Add Konfetti view over the activity root
        ViewGroup rootView = findViewById(android.R.id.content);
        konfettiView = new nl.dionsegijn.konfetti.xml.KonfettiView(this);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        konfettiView.setLayoutParams(params);
        konfettiView.setZ(1000f);
        konfettiView.setElevation(1000f);
        konfettiView.bringToFront();
        rootView.addView(konfettiView);

        nl.dionsegijn.konfetti.core.emitter.EmitterConfig emitterConfig = new nl.dionsegijn.konfetti.core.emitter.Emitter(5L, java.util.concurrent.TimeUnit.SECONDS).max(200);
        nl.dionsegijn.konfetti.core.Party party = new nl.dionsegijn.konfetti.core.PartyFactory(emitterConfig)
            .angle(90)
            .spread(45)
            .timeToLive(5000L)
            .fadeOutEnabled(true)
            .setDamping(0.97f)
            .shapes(nl.dionsegijn.konfetti.core.models.Shape.Circle.INSTANCE,
                    new nl.dionsegijn.konfetti.core.models.Shape.Rectangle(0.5f),
                    nl.dionsegijn.konfetti.core.models.Shape.Square.INSTANCE)
            .sizes(new nl.dionsegijn.konfetti.core.models.Size(30, 100f, 0.1f))
            .position(0.0, 0.0, 1.0, 0.0)
            .build();
        konfettiView.start(party);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("🎉 Congratulations!")
            .setMessage("You earned a new badge!\n\n" + badge.getName() + "\n" + badge.getDescription())
            .setPositiveButton("Awesome!", (d, which) -> {
                if (konfettiView != null && konfettiView.getParent() != null) {
                    ((ViewGroup) konfettiView.getParent()).removeView(konfettiView);
                }
            })
            .setOnDismissListener(d -> {
                if (konfettiView != null && konfettiView.getParent() != null) {
                    ((ViewGroup) konfettiView.getParent()).removeView(konfettiView);
                }
            })
            .setCancelable(false)
            .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.primary_blue));
    }

    private void initializeViews() {
        youtubePlayerView = findViewById(R.id.youtubePlayerView);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        btnClose = findViewById(R.id.btnClose);
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay);
    }

    private void setupYouTubePlayer() {
        getLifecycle().addObserver(youtubePlayerView);
        
        // Configure IFrame player options
        IFramePlayerOptions options = new IFramePlayerOptions.Builder(this)
                .controls(1)  // Show player controls
                .rel(0)       // Show related videos from same channel only
                .ivLoadPolicy(3)  // Hide video annotations
                .ccLoadPolicy(1)  // Hide captions by default
                .build();
        
        youtubePlayerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                // Use cueVideo to load without autoplay
                youTubePlayer.cueVideo(VIDEO_ID, 0);
            }
            
            @Override
            public void onError(@NonNull YouTubePlayer youTubePlayer, @NonNull PlayerConstants.PlayerError error) {
                String errorMessage = "YouTube Player Error: " + error.name();
                Toast.makeText(InhalerTechniqueActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        }, options);
    }

    private void setupListeners() {
        btnStartTimer.setOnClickListener(v -> startPracticeTimer());
        btnClose.setOnClickListener(v -> finish());
    }

    private void startPracticeTimer() {
        if (practiceTimer != null) {
            practiceTimer.cancel();
        }

        btnStartTimer.setEnabled(false);
        btnStartTimer.setText("Hold your breath...");

        practiceTimer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                tvTimerDisplay.setText(String.valueOf(secondsRemaining));
            }

            @Override
            public void onFinish() {
                tvTimerDisplay.setText("✓");
                
                // Update technique streak after completing practice
                motivationService.updateTechniqueStreak(() -> {
                    Toast.makeText(InhalerTechniqueActivity.this, 
                        "Great job! You can breathe out now.", 
                        Toast.LENGTH_LONG).show();
                });
                
                // Reset after 2 seconds
                tvTimerDisplay.postDelayed(() -> {
                    tvTimerDisplay.setText("10");
                    btnStartTimer.setEnabled(true);
                    btnStartTimer.setText("Start 10-Second Timer");
                }, 2000);
            }
        };

        practiceTimer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (practiceTimer != null) {
            practiceTimer.cancel();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        youtubePlayerView.release();
        if (practiceTimer != null) {
            practiceTimer.cancel();
        }
    }
}
