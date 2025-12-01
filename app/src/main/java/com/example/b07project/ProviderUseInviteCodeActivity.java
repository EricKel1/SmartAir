package com.example.b07project;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.util.Date;

public class ProviderUseInviteCodeActivity extends AppCompatActivity {

    private Button UICUseCode, UICGoBack;
    private EditText IcInviteCode;
    private TextView UICError;
    private ProgressBar UICprogress;
    private BackToParent bh = new BackToParent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_use_invite_code);
        initializeViews();
        setupListeners();
    }

    private void initializeViews() {
        UICUseCode = findViewById(R.id.UICUseCode);
        UICGoBack = findViewById(R.id.UICGoBack);
        IcInviteCode = findViewById(R.id.IcInviteCode);
        UICError = findViewById(R.id.UICError);
        UICprogress = findViewById(R.id.UICprogress);
    }

    private void setupListeners() {
        UICUseCode.setOnClickListener(e -> attemptCodeRead());
        UICGoBack.setOnClickListener(e -> finish());
    }

    private void attemptCodeRead() {
        String code = IcInviteCode.getText().toString().trim().toUpperCase();
        if (code.isEmpty()) {
            showError("Please enter an invite code");
            return;
        }

        showLoading(true);
        UICError.setVisibility(View.GONE);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser == null) {
            showLoading(false);
            showError("You must be logged in");
            return;
        }
        
        String providerId = currentUser.getUid();

        ///Check if code exists
        db.collection("invite_codes").document(code).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        showLoading(false);
                        showError("Invalid invite code");
                        return;
                    }

                    // 2. Check expiry
                    Timestamp expiresAt = documentSnapshot.getTimestamp("expiresAt");
                    if (expiresAt != null && expiresAt.toDate().before(new Date())) {
                        showLoading(false);
                        showError("Invite code has expired");
                        documentSnapshot.getReference().delete(); // Cleanup expired code
                        return;
                    }

                    /// Get child info
                    String childName = documentSnapshot.getString("child");
                    String parentUid = documentSnapshot.getString("uid");

                    if (childName == null || parentUid == null) {
                        showLoading(false);
                        showError("Invalid code data");
                        return;
                    }

                    ///Find the child document
                    db.collection("children")
                            .whereEqualTo("name", childName)
                            .whereEqualTo("parentId", parentUid)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (querySnapshot.isEmpty()) {
                                    showLoading(false);
                                    showError("Child profile not found");
                                    return;
                                }

                                DocumentSnapshot childDoc = querySnapshot.getDocuments().get(0);
                                String childId = childDoc.getId();

                                ///Add child to provider's list
                                db.collection("users").document(providerId)
                                        .update("childIds", FieldValue.arrayUnion(childId))
                                        .addOnSuccessListener(aVoid -> {
                                            // 6. Delete the used code
                                            documentSnapshot.getReference().delete();
                                            showLoading(false);
                                            Toast.makeText(this, "Child added successfully!", Toast.LENGTH_SHORT).show();
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            // If update fails, maybe the document doesn't exist?
                                            // Try setting it if it doesn't exist (though provider should exist)
                                            showLoading(false);
                                            showError("Failed to link child: " + e.getMessage());
                                        });

                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                showError("Error finding child: " + e.getMessage());
                            });

                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Error checking code: " + e.getMessage());
                });
    }

    private void showLoading(boolean show) {
        UICprogress.setVisibility(show ? View.VISIBLE : View.GONE);
        UICUseCode.setEnabled(!show);
        UICGoBack.setEnabled(!show);
        IcInviteCode.setEnabled(!show);
    }

    private void showError(String message) {
        UICError.setText(message);
        UICError.setVisibility(View.VISIBLE);
    }
}
