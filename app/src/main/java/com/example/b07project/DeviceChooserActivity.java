package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.b07project.main.WelcomeActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.content.SharedPreferences;

public class DeviceChooserActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private Button btnParent;
    private LinearLayout llChildrenButtons;
    private TextView tvNoChildren;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_chooser);

        progressBar = findViewById(R.id.progressBar);
        btnParent = findViewById(R.id.btnParent);
        llChildrenButtons = findViewById(R.id.llChildrenButtons);
        tvNoChildren = findViewById(R.id.tvNoChildren);

        btnParent.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            navigateToLogin();
            return;
        }

        loadParentInfo(currentUser);
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void savePreference(String type, String childId, String childName, boolean isLocked) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_role", type);
        editor.putBoolean("is_locked", isLocked);
        if (childId != null) {
            editor.putString("last_child_id", childId);
            editor.putString("last_child_name", childName);
        } else {
            editor.remove("last_child_id");
            editor.remove("last_child_name");
        }
        editor.apply();
    }

    private void loadParentInfo(FirebaseUser currentUser) {
        String uid = currentUser.getUid();
        DocumentReference userDocRef = FirebaseFirestore.getInstance().collection("users").document(uid);

        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String displayName = documentSnapshot.getString("displayName");
                if (displayName == null || displayName.isEmpty()) {
                    displayName = documentSnapshot.getString("name");
                }
                if (displayName == null || displayName.isEmpty()) {
                    displayName = currentUser.getEmail();
                }

                btnParent.setText(getString(R.string.im_the_parent, displayName));
                btnParent.setVisibility(View.VISIBLE);
                loadChildren(uid);
            } else {
                Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show();
                logout();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load user data.", Toast.LENGTH_SHORT).show();
            logout();
        });

        btnParent.setOnClickListener(v -> {
            savePreference("parent", null, null, false);
            startActivity(new Intent(DeviceChooserActivity.this, ParentDashboardActivity.class));
            finish();
        });
    }

    private void loadChildren(String parentId) {
        FirebaseFirestore.getInstance().collection("children")
                .whereEqualTo("parentId", parentId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    if (queryDocumentSnapshots.isEmpty()) {
                        tvNoChildren.setVisibility(View.VISIBLE);
                    } else {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String childId = document.getId();
                            String childName = document.getString("name");
                            if (childName != null && !childName.isEmpty()) {
                                addChildButton(childId, childName);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load children.", Toast.LENGTH_SHORT).show();
                });
    }

    private void addChildButton(String childId, String childName) {
        com.google.android.material.button.MaterialButton childButton = 
            (com.google.android.material.button.MaterialButton) getLayoutInflater()
            .inflate(R.layout.item_child_selection, llChildrenButtons, false);
        
        childButton.setText("I'm " + childName + " (Child)");
        childButton.setOnClickListener(v -> {
            showModeSelectionDialog(childId, childName);
        });
        llChildrenButtons.addView(childButton);
    }

    private void showModeSelectionDialog(String childId, String childName) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selection, null);
        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btnAllowSwitching).setOnClickListener(v -> {
            dialog.dismiss();
            proceedToChildMode(childId, childName, false);
        });

        dialogView.findViewById(R.id.btnLockProfile).setOnClickListener(v -> {
            dialog.dismiss();
            proceedToChildMode(childId, childName, true);
        });

        dialog.show();
    }

    private void proceedToChildMode(String childId, String childName, boolean isLocked) {
        savePreference("child", childId, childName, isLocked);
        Intent intent = new Intent(DeviceChooserActivity.this, HomeActivity.class);
        intent.putExtra("EXTRA_CHILD_ID", childId);
        intent.putExtra("EXTRA_CHILD_NAME", childName);
        startActivity(intent);
        finish();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(DeviceChooserActivity.this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
