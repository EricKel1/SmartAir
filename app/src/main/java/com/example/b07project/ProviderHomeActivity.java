package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.b07project.adapters.PatientAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProviderHomeActivity extends AppCompatActivity {

    private RecyclerView rvPatients;
    private ProgressBar progressBar;
    private TextView tvNoPatients;
    private PatientAdapter adapter;
    private List<Map<String, Object>> patientsList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_home);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupRecyclerView();
        setupListeners();

    }

    private void initializeViews() {
        rvPatients = findViewById(R.id.rvPatients);
        progressBar = findViewById(R.id.progressBar);
        tvNoPatients = findViewById(R.id.tvNoPatients);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        tvWelcome.setText("Welcome, Provider");
    }

    private void setupRecyclerView() {
        patientsList = new ArrayList<>();
        adapter = new PatientAdapter(patientsList, this::onPatientClick);
        rvPatients.setLayoutManager(new LinearLayoutManager(this));
        rvPatients.setAdapter(adapter);
    }

    private void setupListeners() {
        Button btnAddPatient = findViewById(R.id.btnAddPatient);
        btnAddPatient.setOnClickListener(v ->
                startActivity(new Intent(this, ProviderUseInviteCodeActivity.class)));

        Button btnSignOut = findViewById(R.id.btnSignOut);
        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void loadPatients() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        progressBar.setVisibility(View.VISIBLE);
        tvNoPatients.setVisibility(View.GONE);

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> childIds = (List<String>) documentSnapshot.get("childIds");
                        if (childIds != null && !childIds.isEmpty()) {
                            fetchChildrenDetails(childIds);
                        } else {
                            progressBar.setVisibility(View.GONE);
                            tvNoPatients.setVisibility(View.VISIBLE);
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        tvNoPatients.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchChildrenDetails(List<String> childIds) {
        patientsList.clear();
        adapter.notifyDataSetChanged();

        final int[] completedCount = {0};
        final int total = childIds.size();

        for (String childId : childIds) {
            db.collection("children").document(childId).get()
                    .addOnSuccessListener(childDoc -> {
                        if (childDoc.exists()) {
                            Map<String, Object> patient = new HashMap<>();

                            String id = childDoc.getId();
                            patient.put("id", id);
                            patient.put("name", childDoc.getString("name"));
                            // Firestore stores DOB under "dateOfBirth"; map to adapter's "dob" key
                            patient.put("dob", childDoc.getString("dateOfBirth"));

                            // sharingSettings.pef = “Safety & Monitoring” switch
                            Map<String, Object> sharing =
                                    (Map<String, Object>) childDoc.get("sharingSettings");
                            boolean safetyEnabled = sharing != null &&
                                    Boolean.TRUE.equals(sharing.get("pef"));
                            patient.put("safetyMonitoringEnabled", safetyEnabled);

                            // default badge state
                            patient.put("hasPEFData", false);
                            patient.put("pefValue", null);
                            patient.put("pefZone", null);
                            patient.put("hasRecentTriage", false);

                            patientsList.add(patient);

                            // Only load badge data if parent has enabled Safety & Monitoring
                            if (safetyEnabled) {
                                loadLatestPEFForChild(id, patient);
                                loadLatestTriageForChild(id, patient);
                            }
                        }
                        checkLoadComplete(++completedCount[0], total);
                    })
                    .addOnFailureListener(e -> {
                        checkLoadComplete(++completedCount[0], total);
                    });
        }
    }

    private void checkLoadComplete(int current, int total) {
        if (current == total) {
            progressBar.setVisibility(View.GONE);
            if (patientsList.isEmpty()) {
                tvNoPatients.setVisibility(View.VISIBLE);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }

    /** Get most recent PEF reading for this child */
    private void loadLatestPEFForChild(String childId, Map<String, Object> patient) {
        db.collection("pef_readings")
                .whereEqualTo("userId", childId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        DocumentSnapshot doc = qs.getDocuments().get(0);
                        Long value = doc.getLong("value");
                        String zone = doc.getString("zone");

                        patient.put("hasPEFData", true);
                        patient.put("pefValue", value);
                        patient.put("pefZone", zone);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    /** Get latest triage session; show badge if latest decision was “emergency” */
    private void loadLatestTriageForChild(String childId, Map<String, Object> patient) {
        db.collection("triage_sessions")
                .whereEqualTo("userId", childId)
                .orderBy("startTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        DocumentSnapshot doc = qs.getDocuments().get(0);
                        String decision = doc.getString("decision");
                        boolean hasIncident = decision != null &&
                                decision.equalsIgnoreCase("emergency");

                        if (hasIncident) {
                            patient.put("hasRecentTriage", true);
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
    }

    private void onPatientClick(Map<String, Object> patient) {
        String childId = (String) patient.get("id");
        String childName = (String) patient.get("name");

        // Later you’ll change this to your “Shared Data” screen
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra("EXTRA_CHILD_ID", childId);
        intent.putExtra("EXTRA_CHILD_NAME", childName);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPatients();
    }
}

