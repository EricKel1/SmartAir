package com.example.b07project;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.example.b07project.utils.NotificationHelper;
import com.example.b07project.repository.PEFRepository;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.models.PEFReading;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class PEFEntryActivity extends AppCompatActivity {

    private EditText etPEFValue, etNotes;
    private RadioGroup rgMedicationContext;
    private RadioButton rbNoContext, rbPreMedication, rbPostMedication;
    private CardView cardZoneDisplay, cardPreMed, cardPostMed;
    private TextView tvZoneResult, tvZoneGuidance, tvZoneEmoji, tvPBWarning;
    private Button btnSave, btnCancel;
    
    private PEFRepository pefRepository;
    private PersonalBest userPersonalBest;
    private String childId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pef_entry);

        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");

        pefRepository = new PEFRepository();
        initializeViews();
        loadPersonalBest();
        setupListeners();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        etPEFValue = findViewById(R.id.etPEFValue);
        etNotes = findViewById(R.id.etNotes);
        
        // Hidden RadioGroup for logic compatibility
        rgMedicationContext = findViewById(R.id.rgMedicationContext);
        rbNoContext = findViewById(R.id.rbNoContext);
        rbPreMedication = findViewById(R.id.rbPreMedication);
        rbPostMedication = findViewById(R.id.rbPostMedication);
        
        // New UI Elements
        cardZoneDisplay = findViewById(R.id.cardZoneDisplay);
        cardPreMed = findViewById(R.id.cardPreMed);
        cardPostMed = findViewById(R.id.cardPostMed);
        
        tvZoneResult = findViewById(R.id.tvZoneResult);
        tvZoneGuidance = findViewById(R.id.tvZoneGuidance);
        tvZoneEmoji = findViewById(R.id.tvZoneEmoji);
        tvPBWarning = findViewById(R.id.tvPBWarning);
        
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
    }

    private void loadPersonalBest() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String targetUserId = (childId != null) ? childId : userId;
        
        pefRepository.getPersonalBest(targetUserId, new PEFRepository.LoadCallback<PersonalBest>() {
            @Override
            public void onSuccess(PersonalBest pb) {
                userPersonalBest = pb;
                if (pb == null) {
                    tvPBWarning.setVisibility(View.VISIBLE);
                } else {
                    tvPBWarning.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(String error) {
                userPersonalBest = null;
                tvPBWarning.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupListeners() {
        // Real-time zone calculation as user types
        etPEFValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateZonePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Custom Context Selection Logic
        cardPreMed.setOnClickListener(v -> setContext(true));
        cardPostMed.setOnClickListener(v -> setContext(false));

        btnSave.setOnClickListener(v -> savePEFReading());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void setContext(boolean isPre) {
        // Reset both first
        cardPreMed.setCardBackgroundColor(android.graphics.Color.WHITE);
        cardPostMed.setCardBackgroundColor(android.graphics.Color.WHITE);
        cardPreMed.setCardElevation(2f);
        cardPostMed.setCardElevation(2f);

        if (isPre) {
            // Select Pre
            if (rbPreMedication.isChecked()) {
                // Toggle off if already checked
                rbNoContext.setChecked(true);
            } else {
                rbPreMedication.setChecked(true);
                cardPreMed.setCardBackgroundColor(android.graphics.Color.parseColor("#E3F2FD")); // Light Blue
                cardPreMed.setCardElevation(8f);
            }
        } else {
            // Select Post
            if (rbPostMedication.isChecked()) {
                // Toggle off if already checked
                rbNoContext.setChecked(true);
            } else {
                rbPostMedication.setChecked(true);
                cardPostMed.setCardBackgroundColor(android.graphics.Color.parseColor("#E3F2FD")); // Light Blue
                cardPostMed.setCardElevation(8f);
            }
        }
    }

    private void updateZonePreview() {
        String valueStr = etPEFValue.getText().toString().trim();
        
        if (valueStr.isEmpty()) {
            cardZoneDisplay.setVisibility(View.GONE);
            return;
        }

        try {
            int pefValue = Integer.parseInt(valueStr);
            
            if (pefValue <= 0 || pefValue > 800) {
                cardZoneDisplay.setVisibility(View.GONE);
                return;
            }

            if (userPersonalBest != null && userPersonalBest.getValue() > 0) {
                String zone = PersonalBest.calculateZone(pefValue, userPersonalBest.getValue());
                displayZone(zone);
                cardZoneDisplay.setVisibility(View.VISIBLE);
            } else {
                cardZoneDisplay.setVisibility(View.GONE);
            }
        } catch (NumberFormatException e) {
            cardZoneDisplay.setVisibility(View.GONE);
        }
    }

    private void displayZone(String zone) {
        String zoneLabel = PersonalBest.getZoneLabel(zone);
        int zoneColor = PersonalBest.getZoneColor(zone);
        
        tvZoneResult.setText(zoneLabel);
        tvZoneResult.setTextColor(zoneColor);
        
        // Set guidance and emoji based on zone
        String guidance;
        String emoji;
        int cardColor;

        switch (zone) {
            case "green":
                guidance = "Great job! Your asthma is well controlled.";
                emoji = "🟢";
                cardColor = android.graphics.Color.parseColor("#E8F5E9"); // Light Green
                break;
            case "yellow":
                guidance = "Caution: Check your action plan.";
                emoji = "🟡";
                cardColor = android.graphics.Color.parseColor("#FFFDE7"); // Light Yellow
                break;
            case "red":
                guidance = "Medical Alert: Follow your emergency plan!";
                emoji = "🔴";
                cardColor = android.graphics.Color.parseColor("#FFEBEE"); // Light Red
                break;
            default:
                guidance = "";
                emoji = "⚪";
                cardColor = android.graphics.Color.WHITE;
        }
        
        tvZoneGuidance.setText(guidance);
        tvZoneEmoji.setText(emoji);
        cardZoneDisplay.setCardBackgroundColor(cardColor);
    }

    private void savePEFReading() {
        String valueStr = etPEFValue.getText().toString().trim();
        
        if (valueStr.isEmpty()) {
            Toast.makeText(this, "Please enter a peak flow value", Toast.LENGTH_SHORT).show();
            return;
        }

        int pefValue;
        try {
            pefValue = Integer.parseInt(valueStr);
            
            if (pefValue <= 0 || pefValue > 800) {
                Toast.makeText(this, "Please enter a valid peak flow value (1-800 L/min)", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String targetUserId = (childId != null) ? childId : userId;
        String notes = etNotes.getText().toString().trim();
        
        boolean isPreMed = rbPreMedication.isChecked();
        boolean isPostMed = rbPostMedication.isChecked();

        PEFReading reading = new PEFReading(targetUserId, pefValue, isPreMed, isPostMed, notes);

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        pefRepository.savePEFReading(reading, new PEFRepository.SaveCallback() {
            @Override
            public void onSuccess(String documentId) {
                // Check for Red Zone Alert
                if (userPersonalBest != null && userPersonalBest.getValue() > 0) {
                    String zone = PersonalBest.calculateZone(pefValue, userPersonalBest.getValue());
                    if ("red".equalsIgnoreCase(zone)) {
                        sendAlertWithChildName(targetUserId, "Red Zone Alert", "recorded a Red Zone PEF reading (" + pefValue + ").");
                    }
                }
                
                Toast.makeText(getApplicationContext(), "Peak flow reading saved", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
                btnSave.setText("Save Reading");
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
                
                NotificationHelper.sendAlert(getApplicationContext(), targetUserId, title, name + " " + messageSuffix);
            })
            .addOnFailureListener(e -> {
                NotificationHelper.sendAlert(getApplicationContext(), targetUserId, title, "Child " + messageSuffix);
            });
    }
}
