package com.example.b07project;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.models.TriageSession;
import com.example.b07project.repository.PEFRepository;
import com.example.b07project.repository.TriageRepository;
import com.example.b07project.utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TriageActivity extends AppCompatActivity {

    private CheckBox cbCantSpeak, cbChestRetractions, cbBlueLips;
    private EditText etRescueAttempts, etCurrentPEF;
    private Button btnGetGuidance, btnCancel, btnCall911;
    private Button btnFeelingBetter, btnNotBetter, btnEmergencyNow;
    private CardView cardDecision;
    private TextView tvDecisionTitle, tvDecisionGuidance, tvActionSteps;
    private LinearLayout layoutTimer, layoutResponseButtons;
    private TextView tvTimerDisplay, tvBreathingInstruction;
    private View viewBreathingPacer;

    private TriageRepository triageRepository;
    private PEFRepository pefRepository;
    private TriageSession currentSession;
    private CountDownTimer countDownTimer;
    private ObjectAnimator breathingAnimator;
    private static final long TIMER_DURATION_MS = 10 * 60 * 1000; // 10 minutes
    private String childId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_triage);

        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");

        triageRepository = new TriageRepository();
        pefRepository = new PEFRepository();
        
        initializeViews();
        setupListeners();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        cbCantSpeak = findViewById(R.id.cbCantSpeak);
        cbChestRetractions = findViewById(R.id.cbChestRetractions);
        cbBlueLips = findViewById(R.id.cbBlueLips);
        etRescueAttempts = findViewById(R.id.etRescueAttempts);
        etCurrentPEF = findViewById(R.id.etCurrentPEF);
        btnGetGuidance = findViewById(R.id.btnGetGuidance);
        btnCancel = findViewById(R.id.btnCancel);
        btnCall911 = findViewById(R.id.btnCall911);
        cardDecision = findViewById(R.id.cardDecision);
        tvDecisionTitle = findViewById(R.id.tvDecisionTitle);
        tvDecisionGuidance = findViewById(R.id.tvDecisionGuidance);
        tvActionSteps = findViewById(R.id.tvActionSteps);
        layoutTimer = findViewById(R.id.layoutTimer);
        layoutResponseButtons = findViewById(R.id.layoutResponseButtons);
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay);
        viewBreathingPacer = findViewById(R.id.viewBreathingPacer);
        tvBreathingInstruction = findViewById(R.id.tvBreathingInstruction);
        btnFeelingBetter = findViewById(R.id.btnFeelingBetter);
        btnNotBetter = findViewById(R.id.btnNotBetter);
        btnEmergencyNow = findViewById(R.id.btnEmergencyNow);
    }

    private void setupListeners() {
        btnGetGuidance.setOnClickListener(v -> performTriage());
        btnCancel.setOnClickListener(v -> finish());
        btnCall911.setOnClickListener(v -> callEmergency());
        
        btnFeelingBetter.setOnClickListener(v -> handleUserImproved());
        btnNotBetter.setOnClickListener(v -> handleStillTrouble());
        btnEmergencyNow.setOnClickListener(v -> escalateToEmergency());
    }

    private void performTriage() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String targetUserId = (childId != null) ? childId : userId;
        
        // Gather data
        boolean cantSpeak = cbCantSpeak.isChecked();
        boolean chestRetractions = cbChestRetractions.isChecked();
        boolean blueLips = cbBlueLips.isChecked();
        
        String rescueStr = etRescueAttempts.getText().toString().trim();
        int rescueAttempts = rescueStr.isEmpty() ? 0 : Integer.parseInt(rescueStr);
        
        String pefStr = etCurrentPEF.getText().toString().trim();
        Integer currentPEF = pefStr.isEmpty() ? null : Integer.parseInt(pefStr);
        
        // Create session
        currentSession = new TriageSession();
        currentSession.setUserId(targetUserId);
        currentSession.setStartTime(new Date());
        currentSession.setCantSpeakFullSentences(cantSpeak);
        currentSession.setChestPullingInRetractions(chestRetractions);
        currentSession.setBlueGrayLipsNails(blueLips);
        currentSession.setRescueAttemptsLast3Hours(rescueAttempts);
        currentSession.setCurrentPEF(currentPEF);
        
        // Get zone if PEF provided
        if (currentPEF != null) {
            pefRepository.getPersonalBest(targetUserId, new PEFRepository.LoadCallback<PersonalBest>() {
                @Override
                public void onSuccess(PersonalBest pb) {
                    if (pb != null) {
                        String zone = PersonalBest.calculateZone(currentPEF, pb.getValue());
                        currentSession.setCurrentZone(zone);
                    }
                    makeTriageDecision();
                }

                @Override
                public void onFailure(String error) {
                    makeTriageDecision();
                }
            });
        } else {
            makeTriageDecision();
        }
    }

    private void makeTriageDecision() {
        // Check for critical red flags
        boolean hasCriticalFlags = currentSession.hasCriticalFlags();
        
        if (hasCriticalFlags) {
            // EMERGENCY: Any red flag = immediate emergency
            showEmergencyDecision();
        } else {
            // MONITOR: All non-emergency cases get monitored with timer
            showHomeMonitoringDecision();
        }
        
        // Save session
        triageRepository.saveTriageSession(currentSession, new TriageRepository.SaveCallback() {
            @Override
            public void onSuccess(String documentId) {
                currentSession.setId(documentId);
                
                // Send Parent Alert
                String alertTitle = "Triage Started";
                String alertMessageSuffix = "started a triage session. Status: " + currentSession.getDecision().replace("_", " ").toUpperCase();
                sendAlertWithChildName(currentSession.getUserId(), alertTitle, alertMessageSuffix);
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(TriageActivity.this, "Error saving session: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmergencyDecision() {
        currentSession.setDecision("emergency");
        
        List<String> steps = new ArrayList<>();
        steps.add("1. Call 911 immediately");
        steps.add("2. Take your rescue inhaler while waiting");
        steps.add("3. Sit upright and try to stay calm");
        steps.add("4. If you have oxygen, use it");
        
        currentSession.setGuidanceShown("Call 911 or go to the emergency room immediately.");
        currentSession.setActionSteps(steps);
        
        tvDecisionTitle.setText("🚨 EMERGENCY");
        tvDecisionTitle.setTextColor(getColor(android.R.color.holo_red_dark));
        tvDecisionGuidance.setText("You have emergency warning signs. Call 911 or go to the emergency room NOW.");
        
        StringBuilder stepsText = new StringBuilder();
        for (String step : steps) {
            stepsText.append(step).append("\n");
        }
        tvActionSteps.setText(stepsText.toString().trim());
        
        layoutTimer.setVisibility(View.GONE);
        layoutResponseButtons.setVisibility(View.GONE);
        btnCall911.setVisibility(View.VISIBLE);
        
        cardDecision.setVisibility(View.VISIBLE);
        btnGetGuidance.setVisibility(View.GONE);
        
        stopBreathingPacer();
    }

    private void showHomeMonitoringDecision() {
        currentSession.setDecision("home_steps");
        
        List<String> steps = new ArrayList<>();
        steps.add("1. Take your rescue inhaler (2-4 puffs)");
        steps.add("2. Sit upright and breathe slowly");
        steps.add("3. We'll check in after 10 minutes");
        steps.add("4. If you feel worse, call 911");
        
        currentSession.setGuidanceShown("Use your rescue inhaler and monitor symptoms closely.");
        currentSession.setActionSteps(steps);
        currentSession.setTimerStarted(true);
        currentSession.setTimerEndTime(new Date(System.currentTimeMillis() + TIMER_DURATION_MS));
        
        tvDecisionTitle.setText("⚠️ MONITOR AT HOME");
        tvDecisionTitle.setTextColor(getColor(android.R.color.holo_orange_dark));
        tvDecisionGuidance.setText("Your symptoms need close monitoring. Follow these steps:");
        
        StringBuilder stepsText = new StringBuilder();
        for (String step : steps) {
            stepsText.append(step).append("\n");
        }
        tvActionSteps.setText(stepsText.toString().trim());
        
        layoutTimer.setVisibility(View.VISIBLE);
        layoutResponseButtons.setVisibility(View.VISIBLE);
        btnCall911.setVisibility(View.GONE);
        startCountdownTimer();
        startBreathingPacer();
        
        cardDecision.setVisibility(View.VISIBLE);
        btnGetGuidance.setVisibility(View.GONE);
    }

    private void startCountdownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        countDownTimer = new CountDownTimer(TIMER_DURATION_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / 60000;
                long seconds = (millisUntilFinished % 60000) / 1000;
                tvTimerDisplay.setText(String.format("%02d:%02d", minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvTimerDisplay.setText("00:00");
                stopBreathingPacer();
                Toast.makeText(TriageActivity.this, "Time's up! How are you feeling?", Toast.LENGTH_LONG).show();
            }
        };
        
        countDownTimer.start();
    }

    private void startBreathingPacer() {
        if (breathingAnimator != null) {
            breathingAnimator.cancel();
        }
        
        // Reset view state
        viewBreathingPacer.setScaleX(1.0f);
        viewBreathingPacer.setScaleY(1.0f);

        // 4-7-8 Breathing Technique (approximate for visual simplicity: 4s in, 4s out)
        // Scale from 1.0 to 1.5
        breathingAnimator = ObjectAnimator.ofPropertyValuesHolder(
                viewBreathingPacer,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.5f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.5f)
        );
        
        breathingAnimator.setDuration(4000); // 4 seconds per phase
        breathingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathingAnimator.setRepeatMode(ValueAnimator.REVERSE);
        breathingAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        
        breathingAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean isBreatheIn = true;

            @Override
            public void onAnimationStart(android.animation.Animator animation) {
                isBreatheIn = true;
                tvBreathingInstruction.setText("Breathe In");
            }

            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
                isBreatheIn = !isBreatheIn;
                tvBreathingInstruction.setText(isBreatheIn ? "Breathe In" : "Breathe Out");
            }
        });
        
        breathingAnimator.start();
    }

    private void stopBreathingPacer() {
        if (breathingAnimator != null) {
            breathingAnimator.cancel();
            viewBreathingPacer.setScaleX(1f);
            viewBreathingPacer.setScaleY(1f);
        }
    }

    private void callEmergency() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:911"));
        startActivity(intent);
    }

    private void handleUserImproved() {
        currentSession.setUserImproved(true);
        currentSession.setUserResponse("improved");
        currentSession.setResponseTime(new Date());
        
        triageRepository.saveTriageSession(currentSession, null);
        
        Toast.makeText(this, "Great! Continue monitoring your symptoms.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void handleStillTrouble() {
        currentSession.setUserImproved(false);
        currentSession.setUserResponse("still_trouble");
        currentSession.setResponseTime(new Date());
        
        escalateToEmergency();
    }

    private void escalateToEmergency() {
        currentSession.setEscalated(true);
        currentSession.setEscalationReason("User reported continued difficulty or requested emergency help");
        currentSession.setDecision("emergency");
        
        triageRepository.saveTriageSession(currentSession, null);
        
        // Send Parent Alert
        sendAlertWithChildName(currentSession.getUserId(), "Triage Escalation", "requested emergency assistance during triage.");
        
        // Show emergency guidance
        showEmergencyDecision();
        
        Toast.makeText(this, "Escalating to emergency guidance", Toast.LENGTH_SHORT).show();
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
                
                NotificationHelper.sendAlert(TriageActivity.this, targetUserId, title, name + " " + messageSuffix);
            })
            .addOnFailureListener(e -> {
                NotificationHelper.sendAlert(TriageActivity.this, targetUserId, title, "Child " + messageSuffix);
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
