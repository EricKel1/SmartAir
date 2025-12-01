package com.example.b07project;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.b07project.adapters.ChildAdapter;
import com.example.b07project.main.WelcomeActivity;
import com.example.b07project.models.PEFReading;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.models.RescueInhalerLog;
import com.example.b07project.repository.PEFRepository;
import com.example.b07project.repository.RescueInhalerRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.EditText;
import android.text.InputType;

import android.widget.ImageButton;

import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentChange;
import com.example.b07project.utils.NotificationHelper;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.util.concurrent.TimeUnit;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;

public class ParentDashboardActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 101;
    private RecyclerView rvChildren;
    private ProgressBar progressBar;
    private ChildAdapter adapter;
    private List<Map<String, String>> childrenList;
    private PEFRepository pefRepository;
    private RescueInhalerRepository rescueRepository;
    private Button btnAddChild;
    private ImageButton btnNotifications;
    private Button btnSwitchProfile;
    private ListenerRegistration notificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_dashboard);

        // Request Notification Permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }

        // Register FCM Token
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    android.util.Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                    return;
                }
                String token = task.getResult();
                android.util.Log.d("FCM", "FCM Token: " + token);
                saveFCMToken(token);
            });

        rvChildren = findViewById(R.id.rvChildren);
        progressBar = findViewById(R.id.progressBar);
        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnInventory = findViewById(R.id.btnInventory);
        btnAddChild = findViewById(R.id.btnAddChild);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnSwitchProfile = findViewById(R.id.btnSwitchProfile);

        pefRepository = new PEFRepository();
        rescueRepository = new RescueInhalerRepository();

        childrenList = new ArrayList<>();
        adapter = new ChildAdapter(childrenList, new ChildAdapter.OnChildActionListener() {
            @Override
            public void onViewChildDashboard(String childName, String childId) {
                String targetId = getTargetId(childId);
                Intent intent = new Intent(ParentDashboardActivity.this, ParentChildDashboardActivity.class);
                intent.putExtra("EXTRA_CHILD_ID", targetId);
                intent.putExtra("EXTRA_CHILD_NAME", childName);
                startActivity(intent);
            }

            @Override
            public void onGenerateCode(String childName, String childId) {
                onGenerateCodeClicked(childName, childId);
            }

            @Override
            public void onViewReports(String childName, String childId) {
                String targetId = getTargetId(childId);
                Intent intent = new Intent(ParentDashboardActivity.this, StatisticsReportsActivity.class);
                intent.putExtra("EXTRA_CHILD_ID", targetId);
                startActivity(intent);
            }

            @Override
            public void onSharingSettings(String childName, String childId) {
                onSharingSettingsClicked(childName, childId);
            }

            @Override
            public void onRangeChanged(String childId, int days) {
                for (int i = 0; i < childrenList.size(); i++) {
                    if (childrenList.get(i).get("id").equals(childId)) {
                        fetchChildStats(childrenList.get(i), i, days);
                        break;
                    }
                }
            }

            @Override
            public void onEditProfile(String childName, String childId) {
                showEditProfileDialog(childName, childId);
            }

            @Override
            public void onSetPersonalBest(String childName, String childId) {
                String targetId = getTargetId(childId);
                showSetPersonalBestDialog(childName, targetId);
            }

            @Override
            public void onSetMedicationSchedule(String childName, String childId) {
                // Deprecated: Schedule is now managed in ParentChildDashboardActivity
                // showSetMedicationScheduleDialog(childName, childId);
            }

            @Override
            public void onRemoveChild(String childName, String childId) {
                new MaterialAlertDialogBuilder(ParentDashboardActivity.this)
                        .setTitle("Remove Child")
                        .setMessage("Are you sure you want to remove " + childName + " from your dashboard? This cannot be undone.")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            FirebaseFirestore.getInstance().collection("children").document(childId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(ParentDashboardActivity.this, "Child removed", Toast.LENGTH_SHORT).show();
                                        loadChildren();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(ParentDashboardActivity.this, "Error removing child", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        rvChildren.setLayoutManager(new LinearLayoutManager(this));
        rvChildren.setAdapter(adapter);

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ParentDashboardActivity.this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        btnInventory.setOnClickListener(v -> {
            Intent intent = new Intent(ParentDashboardActivity.this, InventoryActivity.class);
            startActivity(intent);
        });

        btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(ParentDashboardActivity.this, NotificationCenterActivity.class);
            startActivity(intent);
        });

        btnSwitchProfile.setOnClickListener(v -> {
            Intent intent = new Intent(ParentDashboardActivity.this, DeviceChooserActivity.class);
            startActivity(intent);
            finish();
        });

        btnAddChild.setOnClickListener(v -> showAddChildDialog());
        
        // Removed scheduleNotificationWorker() as we are switching to FCM
        
        // Temporary Debug: Print logs for a specific child ID if known
        // debugPrintLogsForChild("ioeu7bHKq4a5otHN2DursmyuQnT2");

        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void debugPrintLogsForChild(String childId) {
        FirebaseFirestore.getInstance().collection("rescue_inhaler_logs")
                .whereEqualTo("userId", childId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(snapshots -> {
                    android.util.Log.d("childparentdatalink", "DEBUG: Dumping last 5 logs for " + childId + ":");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                        android.util.Log.d("childparentdatalink", "Log ID: " + doc.getId() + 
                                " | userId: " + doc.getString("userId") + 
                                " | time: " + doc.getDate("timestamp"));
                    }
                    if (snapshots.isEmpty()) {
                        android.util.Log.d("childparentdatalink", "DEBUG: No logs found for " + childId);
                    }
                })
                .addOnFailureListener(e -> android.util.Log.e("childparentdatalink", "DEBUG: Failed to fetch logs", e));
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupNotificationListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (notificationListener != null) {
            notificationListener.remove();
        }
    }

    private void setupNotificationListener() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        android.util.Log.d("NotificationDebug", "ParentDashboard listener setup for user: " + user.getUid());

        SharedPreferences prefs = getSharedPreferences("NotificationWorkerPrefs", MODE_PRIVATE);
        // If key doesn't exist, initialize it to now so we don't show old history on first run
        if (!prefs.contains("last_check_timestamp")) {
             prefs.edit().putLong("last_check_timestamp", System.currentTimeMillis()).apply();
        }

        notificationListener = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("userId", user.getUid())
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        android.util.Log.e("NotificationDebug", "ParentDashboard listener FAILED: " + e.getMessage());
                        return;
                    }

                    if (snapshots != null) {
                        android.util.Log.d("NotificationDebug", "ParentDashboard listener update. Count: " + snapshots.size());
                        
                        long lastCheck = prefs.getLong("last_check_timestamp", System.currentTimeMillis());
                        long maxTimestamp = lastCheck;
                        boolean updated = false;

                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                String title = dc.getDocument().getString("title");
                                String message = dc.getDocument().getString("message");
                                Date timestamp = dc.getDocument().getDate("timestamp");

                                if (timestamp != null && timestamp.getTime() > lastCheck) {
                                    android.util.Log.d("NotificationDebug", "New notification: " + title);
                                    // NotificationHelper.showLocalNotification(this, title, message); // Disabled to avoid duplicates with FCM
                                    
                                    if (timestamp.getTime() > maxTimestamp) {
                                        maxTimestamp = timestamp.getTime();
                                        updated = true;
                                    }
                                } else {
                                    android.util.Log.d("NotificationDebug", "Skipping old/duplicate notification: " + title);
                                }
                            }
                        }
                        
                        if (updated) {
                            prefs.edit().putLong("last_check_timestamp", maxTimestamp).apply();
                        }
                    } else {
                        android.util.Log.d("NotificationDebug", "ParentDashboard listener update. Snapshots is null.");
                    }
                });
    }

    private void updateLastCheckTime(Date timestamp) {
        SharedPreferences prefs = getSharedPreferences("NotificationWorkerPrefs", MODE_PRIVATE);
        long currentLast = prefs.getLong("last_check_timestamp", 0);
        if (timestamp.getTime() > currentLast) {
            prefs.edit().putLong("last_check_timestamp", timestamp.getTime()).apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChildren();
    }

    private void showAddChildDialog() {
        Intent intent = new Intent(ParentDashboardActivity.this, ParentAddNewChildActivity.class);
        startActivity(intent);
    }

    private void showCreateNewChildDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Create New Profile");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Child Name");
        
        // Add padding
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 50;
        params.rightMargin = 50;
        input.setLayoutParams(params);
        container.addView(input);
        
        builder.setView(container);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String childName = input.getText().toString().trim();
            if (!childName.isEmpty()) {
                addChild(childName);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void addChild(String name) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> child = new HashMap<>();
        child.put("name", name);
        child.put("parentId", user.getUid());
        // Add default sharing settings
        Map<String, Boolean> sharing = new HashMap<>();
        sharing.put("rescueLogs", false);
        sharing.put("controllerAdherence", false);
        sharing.put("symptoms", false);
        sharing.put("triggers", false);
        sharing.put("pef", false);
        sharing.put("triage", false);
        sharing.put("summaryCharts", false);
        child.put("sharingSettings", sharing);

        FirebaseFirestore.getInstance().collection("children")
                .add(child)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Child added", Toast.LENGTH_SHORT).show();
                    loadChildren();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error adding child", Toast.LENGTH_SHORT).show());
    }

    private void loadChildren() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        android.util.Log.d("childparentlink", "loadChildren called for parent: " + user.getUid());

        progressBar.setVisibility(View.VISIBLE);
        FirebaseFirestore.getInstance().collection("children")
                .whereEqualTo("parentId", user.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    android.util.Log.d("childparentlink", "Found " + queryDocumentSnapshots.size() + " children");
                    childrenList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Map<String, String> child = new HashMap<>();
                        child.put("id", document.getId());
                        child.put("name", document.getString("name"));
                        if (document.contains("uid")) {
                            String uid = document.getString("uid");
                            child.put("uid", uid);
                            android.util.Log.d("childparentlink", "Child: " + document.getString("name") + " has linked UID: " + uid);
                        } else {
                            android.util.Log.d("childparentlink", "Child: " + document.getString("name") + " has NO linked UID (using docId: " + document.getId() + ")");
                        }
                        child.put("zone", "Loading...");
                        child.put("lastRescue", "Loading...");
                        child.put("weeklyRescues", "Loading...");
                        childrenList.add(child);
                    }
                    adapter.notifyDataSetChanged();
                    
                    // Now fetch stats for each child
                    for (int i = 0; i < childrenList.size(); i++) {
                        fetchChildStats(childrenList.get(i), i, 7); // Default 7 days
                    }
                    
                    if (childrenList.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("childparentlink", "Error loading children", e);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load children: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchChildStats(Map<String, String> child, int position, int days) {
        String childId = child.get("id");
        if (child.containsKey("uid")) {
            childId = child.get("uid");
        }
        
        android.util.Log.d("childparentlink", "Fetching stats for " + child.get("name") + " using ID: " + childId);
        
        // 1. Fetch Zone (PEF)
        String finalChildId = childId;
        pefRepository.getLastPEFReading(childId, new PEFRepository.LoadCallback<PEFReading>() {
            @Override
            public void onSuccess(PEFReading reading) {
                if (reading != null && reading.getZone() != null) {
                    child.put("zone", PersonalBest.getZoneLabel(reading.getZone()));
                } else {
                    // Try fetching personal best to see if it's set
                    pefRepository.getPersonalBest(finalChildId, new PEFRepository.LoadCallback<PersonalBest>() {
                        @Override
                        public void onSuccess(PersonalBest pb) {
                            if (pb == null) {
                                child.put("zone", "Set PB");
                            } else {
                                child.put("zone", "No Data");
                            }
                            adapter.notifyItemChanged(position);
                        }
                        @Override
                        public void onFailure(String error) {
                            child.put("zone", "Unknown");
                            adapter.notifyItemChanged(position);
                        }
                    });
                    return; // Return early as we handle notify in inner callback
                }
                adapter.notifyItemChanged(position);
                checkIfAllLoaded();
            }

            @Override
            public void onFailure(String error) {
                child.put("zone", "Unknown");
                adapter.notifyItemChanged(position);
                checkIfAllLoaded();
            }
        });

        // 2. Fetch Rescue Stats
        Calendar calendar = Calendar.getInstance();
        Date endDate = calendar.getTime();
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        Date startDate = calendar.getTime();

        rescueRepository.getLogsForUserInDateRange(childId, startDate, endDate, new RescueInhalerRepository.LoadCallback() {
            @Override
            public void onSuccess(List<RescueInhalerLog> logs) {
                int count = 0;
                Date lastRescueTime = null;
                
                for (RescueInhalerLog log : logs) {
                    count += log.getDoseCount(); // Count doses, not just events
                    if (log.getTimestamp() != null) {
                        if (lastRescueTime == null || log.getTimestamp().after(lastRescueTime)) {
                            lastRescueTime = log.getTimestamp();
                        }
                    }
                }
                
                child.put("weeklyRescues", String.valueOf(count));
                
                if (lastRescueTime != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
                    child.put("lastRescue", sdf.format(lastRescueTime));
                } else {
                    child.put("lastRescue", "None");
                }
                
                adapter.notifyItemChanged(position);
                checkIfAllLoaded();
            }

            @Override
            public void onFailure(String error) {
                child.put("weeklyRescues", "Error");
                child.put("lastRescue", "Error");
                adapter.notifyItemChanged(position);
                checkIfAllLoaded();
            }
        });
    }

    private String getTargetId(String childId) {
        for (Map<String, String> child : childrenList) {
            if (child.get("id").equals(childId)) {
                if (child.containsKey("uid")) {
                    String uid = child.get("uid");
                    android.util.Log.d("childparentdatalink", "getTargetId: Resolved " + childId + " to UID " + uid);
                    return uid;
                }
                break;
            }
        }
        android.util.Log.d("childparentdatalink", "getTargetId: Could not resolve " + childId + " to UID, using original ID");
        return childId;
    }

    private int loadedCount = 0;
    private void checkIfAllLoaded() {
        // This is a rough approximation since we have 2 async calls per child
        // Ideally we'd track exact requests, but for hiding the progress bar this is okay-ish
        // or we just hide it after the initial list load and let the items update individually.
        // I'll hide it immediately after list load in loadChildren actually.
        progressBar.setVisibility(View.GONE);
    }

    private void onSharingSettingsClicked(String childName, String childId) {
        Intent intent = new Intent(this, SharingSettingsActivity.class);
        intent.putExtra("EXTRA_CHILD_ID", childId);
        startActivity(intent);
    }

    private void onGenerateCodeClicked(String childName, String childId) {
        Intent intent = new Intent(this, ParentChildLogsActivity.class);
        intent.putExtra("childName", childName);
        startActivity(intent);
    }

    private void onViewReportsClicked(String childName, String childId) {
        Intent intent = new Intent(this, StatisticsReportsActivity.class);
        intent.putExtra("EXTRA_CHILD_ID", childId);
        startActivity(intent);
    }

    private void showEditProfileDialog(String currentName, String childId) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Edit Child Profile");

        // Create layout programmatically
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        final EditText etName = new EditText(this);
        etName.setHint("Child Name");
        etName.setText(currentName);
        layout.addView(etName);

        final EditText etDOB = new EditText(this);
        etDOB.setHint("Date of Birth (YYYY-MM-DD)");
        etDOB.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        ((android.widget.LinearLayout.LayoutParams) etDOB.getLayoutParams()).topMargin = 20;
        layout.addView(etDOB);

        final EditText etNotes = new EditText(this);
        etNotes.setHint("Medical Notes (Optional)");
        etNotes.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        ((android.widget.LinearLayout.LayoutParams) etNotes.getLayoutParams()).topMargin = 20;
        layout.addView(etNotes);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = etName.getText().toString().trim();
            String dob = etDOB.getText().toString().trim();
            String notes = etNotes.getText().toString().trim();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", newName);
            if (!dob.isEmpty()) updates.put("dateOfBirth", dob);
            if (!notes.isEmpty()) updates.put("notes", notes);

            FirebaseFirestore.getInstance().collection("children").document(childId)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                        loadChildren(); // Refresh list
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show());
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSetPersonalBestDialog(String childName, String childId) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Set Personal Best (PEF)");
        builder.setMessage("Enter the highest Peak Flow value " + childName + " can achieve when healthy.");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 350");
        
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 60;
        params.rightMargin = 60;
        input.setLayoutParams(params);
        container.addView(input);
        
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String valueStr = input.getText().toString().trim();
            if (!valueStr.isEmpty()) {
                int value = Integer.parseInt(valueStr);
                PersonalBest pb = new PersonalBest(childId, value, FirebaseAuth.getInstance().getCurrentUser().getUid());
                pefRepository.setPersonalBest(pb, new PEFRepository.SaveCallback() {
                    @Override
                    public void onSuccess(String documentId) {
                        Toast.makeText(ParentDashboardActivity.this, "Personal Best updated", Toast.LENGTH_SHORT).show();
                        loadChildren(); // Refresh to update zone calculation if needed
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(ParentDashboardActivity.this, "Failed to update PB", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSetMedicationScheduleDialog(String childName, String childId) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Controller Schedule");
        builder.setMessage("How many controller doses should " + childName + " take per day?");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 2");
        
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 60;
        params.rightMargin = 60;
        input.setLayoutParams(params);
        container.addView(input);
        
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String valueStr = input.getText().toString().trim();
            if (!valueStr.isEmpty()) {
                int doses = Integer.parseInt(valueStr);
                Map<String, Object> updates = new HashMap<>();
                updates.put("plannedDosesPerDay", doses);

                FirebaseFirestore.getInstance().collection("children").document(childId)
                        .set(updates, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> Toast.makeText(this, "Schedule updated", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveFCMToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("fcmToken", token);
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> android.util.Log.d("FCM", "Token saved to Firestore"))
                .addOnFailureListener(e -> android.util.Log.e("FCM", "Error saving token", e));
        }
    }
}
