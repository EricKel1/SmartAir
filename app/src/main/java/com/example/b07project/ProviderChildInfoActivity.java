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
    private TextView tvEmptyShared;
    private View layoutEmptyShared;
    private View scrollContent;
    private Button btnSignOutEmpty;
    private LinearLayout containerItems;
    private Button btnSignOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_child_info);

        db = FirebaseFirestore.getInstance();
        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        childName = getIntent().getStringExtra("EXTRA_CHILD_NAME");

        initializeViews();
        String nameForTitle = (childName != null && !childName.trim().isEmpty()) ? childName.trim() : "Child";
        tvTitle.setText("Viewing " + nameForTitle + "'s Info");
        tvViewingChild.setText("Shared By Parent");
        tvChildName.setText("");
        tvChildName.setVisibility(View.GONE);

        loadSharingSettings();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvViewingChild = findViewById(R.id.tvViewingChild);
        tvChildName = findViewById(R.id.tvChildName);
        tvEmptyShared = findViewById(R.id.tvEmptyShared);
        layoutEmptyShared = findViewById(R.id.layoutEmptyShared);
        scrollContent = findViewById(R.id.scrollContent);
        containerItems = findViewById(R.id.containerItems);
        btnSignOut = findViewById(R.id.btnSignOut);
        btnSignOutEmpty = findViewById(R.id.btnSignOutEmpty);

        btnSignOut.setOnClickListener(v -> {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
        });
        if (btnSignOutEmpty != null) {
            btnSignOutEmpty.setOnClickListener(v -> {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
        }
    }

    private void loadSharingSettings() {
        db.collection("children").document(childId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        showEmptyState();
                        return;
                    }
                    Map<String, Object> sharing = (Map<String, Object>) doc.get("sharingSettings");
                    if (sharing == null) {
                        showEmptyState();
                        return;
                    }

                    addItemIfShared(sharing, "symptoms", "Symptom History", "View historical symptom check-ins", v -> openActivity(SymptomHistoryActivity.class));
                    addItemIfShared(sharing, "medication", "Medicine Logs", "View rescue/controller medicine history", v -> openActivity(RescueInhalerHistoryActivity.class));
                    addItemIfShared(sharing, "pef", "PEF Readings", "View peak flow history", v -> openActivity(PEFHistoryActivity.class));
                    addItemIfShared(sharing, "pef", "Incidents", "View emergency triage incidents", v -> openActivity(IncidentHistoryActivity.class));
                    addItemIfShared(sharing, "patterns", "Trigger Patterns", "View trigger analytics", v -> openActivity(TriggerPatternsActivity.class));
                    // Show Stats & Reports when parent enabled 'stats' sharing
                    boolean shareStats = Boolean.TRUE.equals(sharing.get("stats"));
                    if (shareStats) {
                        View.OnClickListener openStats = v -> {
                            Intent intent = new Intent(this, StatisticsReportsActivity.class);
                            intent.putExtra("EXTRA_CHILD_ID", childId);
                            intent.putExtra("EXTRA_READ_ONLY", true);
                            startActivity(intent);
                        };
                        addItemIfShared(java.util.Collections.singletonMap("stats", true), "stats",
                                "Statistics & Reports", "View summary charts & reports", openStats);
                    }

                    if (containerItems.getChildCount() == 0) {
                        showEmptyState();
                    } else {
                        hideEmptyState();
                    }
                });
    }

    private void showEmptyState() {
        if (layoutEmptyShared != null) layoutEmptyShared.setVisibility(View.VISIBLE);
        if (tvEmptyShared != null) tvEmptyShared.setVisibility(View.VISIBLE);
        if (scrollContent != null) scrollContent.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        if (layoutEmptyShared != null) layoutEmptyShared.setVisibility(View.GONE);
        if (tvEmptyShared != null) tvEmptyShared.setVisibility(View.GONE);
        if (scrollContent != null) scrollContent.setVisibility(View.VISIBLE);
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