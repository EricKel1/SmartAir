package com.example.b07project;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.adapters.IncidentAdapter;
import com.example.b07project.models.TriageSession;
import com.example.b07project.repository.TriageRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class IncidentHistoryActivity extends AppCompatActivity {

    private RecyclerView rvIncidents;
    private TextView tvNoIncidents;
    private ProgressBar progressBar;
    private IncidentAdapter adapter;
    private TriageRepository triageRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incident_history);

        triageRepository = new TriageRepository();
        
        initializeViews();
        loadIncidents();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        rvIncidents = findViewById(R.id.rvIncidents);
        tvNoIncidents = findViewById(R.id.tvNoIncidents);
        progressBar = findViewById(R.id.progressBar);

        rvIncidents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new IncidentAdapter(new ArrayList<>());
        rvIncidents.setAdapter(adapter);
    }

    private void loadIncidents() {
        progressBar.setVisibility(View.VISIBLE);
        rvIncidents.setVisibility(View.GONE);
        tvNoIncidents.setVisibility(View.GONE);

        String userId;
        if (getIntent().hasExtra("EXTRA_CHILD_ID")) {
            userId = getIntent().getStringExtra("EXTRA_CHILD_ID");
            android.util.Log.d("childparentlink", "IncidentHistoryActivity: Using EXTRA_CHILD_ID: " + userId);
        } else {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                tvNoIncidents.setVisibility(View.VISIBLE);
                tvNoIncidents.setText("Please sign in to view incidents");
                return;
            }
            userId = currentUser.getUid();
            android.util.Log.d("childparentlink", "IncidentHistoryActivity: Using current user ID: " + userId);
        }

        triageRepository.getTriageSessions(userId, new TriageRepository.LoadCallback<List<TriageSession>>() {
            @Override
            public void onSuccess(List<TriageSession> sessions) {
                progressBar.setVisibility(View.GONE);

                if (sessions.isEmpty()) {
                    tvNoIncidents.setVisibility(View.VISIBLE);
                    rvIncidents.setVisibility(View.GONE);
                } else {
                    tvNoIncidents.setVisibility(View.GONE);
                    rvIncidents.setVisibility(View.VISIBLE);
                    adapter.updateData(sessions);
                }
            }

            @Override
            public void onFailure(String error) {
                progressBar.setVisibility(View.GONE);
                tvNoIncidents.setVisibility(View.VISIBLE);
                tvNoIncidents.setText("Error loading incidents: " + error);
                Toast.makeText(IncidentHistoryActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
