package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

public class ProviderChildInfoActivity extends AppCompatActivity {

    private String childId;
    private String childName;
    private FirebaseFirestore db;

    private TextView tvTitle;
    private TextView tvViewingChild;
    private TextView tvChildName;
    private LinearLayout containerItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_child_info);

        db = FirebaseFirestore.getInstance();
        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        childName = getIntent().getStringExtra("EXTRA_CHILD_NAME");

        initializeViews();
        tvTitle.setText("Shared Child Data");
        tvViewingChild.setText("Viewing Child:");
        tvChildName.setText(childName != null ? childName : "Unknown");

        loadSharingSettings();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvViewingChild = findViewById(R.id.tvViewingChild);
        tvChildName = findViewById(R.id.tvChildName);
        containerItems = findViewById(R.id.containerItems);
    }

    private void loadSharingSettings() {
        db.collection("children").document(childId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Map<String, Object> sharing = (Map<String, Object>) doc.get("sharingSettings");
                    if (sharing == null) return;

                    addItemIfShared(sharing, "symptoms", "Symptom History", "View historical symptom check-ins", v -> openActivity(SymptomHistoryActivity.class));
                    addItemIfShared(sharing, "medication", "Medicine Logs", "View rescue/controller medicine history", v -> openActivity(RescueInhalerHistoryActivity.class));
                    addItemIfShared(sharing, "pef", "PEF Readings", "View peak flow history", v -> openActivity(PEFHistoryActivity.class));
                    addItemIfShared(sharing, "triage", "Incidents", "View emergency triage incidents", v -> openActivity(IncidentHistoryActivity.class));
                    addItemIfShared(sharing, "patterns", "Trigger Patterns", "View trigger analytics", v -> openActivity(TriggerPatternsActivity.class));
                    addItemIfShared(sharing, "summaryCharts", "Statistics & Reports", "View summary charts & reports", v -> openActivity(StatisticsReportsActivity.class));
                });
    }

    private void addItemIfShared(Map<String, Object> sharing, String key, String title, String desc, View.OnClickListener onClick) {
        boolean shared = Boolean.TRUE.equals(sharing.get(key));
        if (!shared) return;

        View item = getLayoutInflater().inflate(R.layout.item_provider_child_info, containerItems, false);
        TextView tvItemTitle = item.findViewById(R.id.tvItemTitle);
        TextView tvItemDesc = item.findViewById(R.id.tvItemDesc);
        Button btnView = item.findViewById(R.id.btnViewHistory);

        tvItemTitle.setText(title);
        tvItemDesc.setText(desc);
        btnView.setOnClickListener(onClick);

        containerItems.addView(item);
    }

    private void openActivity(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        intent.putExtra("EXTRA_CHILD_ID", childId);
        startActivity(intent);
    }
}