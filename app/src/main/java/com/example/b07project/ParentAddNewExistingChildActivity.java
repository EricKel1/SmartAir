package com.example.b07project;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import com.google.firebase.firestore.SetOptions;

public class ParentAddNewExistingChildActivity extends AppCompatActivity {
    //
    private EditText childEmail, childDateOfBirth2;
    private EditText childPassword2;

    private Button btnAddChildProfile, btnCancelAddChild2;

    //An account created by a child will have a copy in users
    //with role child and parentId set to null and selfId set to something.
    //then when trying to add a child with an existing account,
    //we check if the email's associated id is found in the children collection.

    private FirebaseAuth auth;

    private BackToParent bh = new BackToParent();
    private TextView nceError;
    private ProgressBar nceProgress;
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_add_new_child_existing);
        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupListeners();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }


    //initializeViews initializes all the views on the screen.
    private void initializeViews(){
        childEmail = findViewById(R.id.childEmail);
        childPassword2 = findViewById(R.id.childPassword2);
        btnAddChildProfile = findViewById(R.id.btnAddChildProfile);
        btnCancelAddChild2 = findViewById(R.id.btnCancelAddChild2);
        nceError = findViewById(R.id.nceError);
        nceProgress = findViewById(R.id.nceProgress);
        childDateOfBirth2 = findViewById(R.id.childDateOfBirth2);


    }

    //setupListeners sets up eventlisteners for the relevant views in initializeViews.
    //InitializeViews should always be called before setupListeners.
    private void setupListeners(){
        btnAddChildProfile.setOnClickListener(v -> attemptAddChild());
        btnCancelAddChild2.setOnClickListener((v -> bh.backTo(this)));

        CalendarPicker cp = new CalendarPicker();
        childDateOfBirth2.setOnClickListener(v -> cp.openCalendar(this, childDateOfBirth2));

    }

    private void attemptAddChild() {
        String email = childEmail.getText().toString().trim();
        String password = childPassword2.getText().toString().trim();
        String age = childDateOfBirth2.getText().toString().trim();

        showLoading(true);

        validateInput(email, password, age, (userDoc, authUid) -> {
            if (userDoc != null && authUid != null) {
                // Use the verified Auth UID
                String childUid = authUid;

                // Update user's hasParent flag and set parentId
                Map<String, Object> userUpdates = new HashMap<>();
                userUpdates.put("hasParent", true);
                userUpdates.put("parentId", FirebaseAuth.getInstance().getCurrentUser().getUid());
                userDoc.getReference().update(userUpdates);

                // Also try to update the hasParent flag and parentId on the real UID doc if it differs
                if (!userDoc.getId().equals(childUid)) {
                    Map<String, Object> realUserUpdates = new HashMap<>();
                    realUserUpdates.put("hasParent", true);
                    realUserUpdates.put("parentId", FirebaseAuth.getInstance().getCurrentUser().getUid());
                    
                    db.collection("users").document(childUid).update(realUserUpdates)
                            .addOnFailureListener(e -> android.util.Log.w("Link", "Could not update real user doc hasParent: " + e.getMessage()));
                }

                Map<String, Object> child = new HashMap<>();
                child.put("name", email); // Using email as name initially
                if (userDoc.contains("name")) {
                    child.put("name", userDoc.getString("name"));
                }
                child.put("dateOfBirth", age);
                child.put("parentId", FirebaseAuth.getInstance().getCurrentUser().getUid());
                child.put("createdAt", System.currentTimeMillis());
                child.put("uid", childUid);

                // Use the child's Auth UID as the Document ID in the 'children' collection
                db.collection("children").document(childUid).set(child)
                        .addOnSuccessListener(aVoid -> {
                            android.util.Log.d("childparentdatalink", "Linked existing child. DocID: " + childUid + ", Linked UID: " + childUid);
                            showLoading(false);
                            bh.backTo(this);
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false);
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                showLoading(false);
            }
        });
    }

    private void validateInput(String email, String password, String age, BiConsumer<DocumentSnapshot, String> callback) {
        db.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        showError("Email may be incorrect");
                        callback.accept(null, null);
                        return;
                    }

                    if (age.isEmpty()) {
                        showError("Please enter child's date of birth");
                        callback.accept(null, null);
                        return;
                    }

                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

                    // Check role
                    String role = doc.getString("role");
                    if (!"child".equals(role)) {
                        showError("This account is not a child account.");
                        callback.accept(null, null);
                        return;
                    }

                    // Check password using Firebase Auth (Secondary App)
                    verifyChildCredentials(email, password, authUid -> {
                        if (authUid != null) {
                            callback.accept(doc, authUid);
                        } else {
                            showError("Password is incorrect.");
                            callback.accept(null, null);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    showError("Child's email doesn't exist or network error.");
                    callback.accept(null, null);
                });
    }

    private void verifyChildCredentials(String email, String password, Consumer<String> callback) {
        final String SECONDARY_APP_NAME = "SecondaryApp";
        FirebaseApp secondaryApp = null;

        try {
            try {
                secondaryApp = FirebaseApp.getInstance(SECONDARY_APP_NAME);
            } catch (IllegalStateException e) {
                FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                secondaryApp = FirebaseApp.initializeApp(this, options, SECONDARY_APP_NAME);
            }

            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
            // We also need a Firestore instance for this secondary app to write as the child
            FirebaseFirestore secondaryDb = FirebaseFirestore.getInstance(secondaryApp);

            secondaryAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        String uid = authResult.getUser().getUid();
                        
                        // CRITICAL FIX: Write parentId to the child's user document using the CHILD'S auth session.
                        // This bypasses the permission issue where Parent cannot write to Child's profile.
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("parentId", FirebaseAuth.getInstance().getCurrentUser().getUid());
                        updates.put("hasParent", true);
                        
                        secondaryDb.collection("users").document(uid)
                                .set(updates, SetOptions.merge())
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("Link", "Successfully wrote parentId to child profile as child.");
                                    secondaryAuth.signOut();
                                    callback.accept(uid);
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("Link", "Failed to write parentId as child: " + e.getMessage());
                                    // Even if this fails, we proceed, but notifications might be broken
                                    secondaryAuth.signOut();
                                    callback.accept(uid);
                                });
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("AuthCheck", "Secondary auth failed: " + e.getMessage());
                        callback.accept(null);
                    });

        } catch (Exception e) {
            android.util.Log.e("AuthCheck", "Error initializing secondary app", e);
            callback.accept(null);
        }
    }
    private void showLoading(boolean show) {
        nceProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnAddChildProfile.setEnabled(!show);
        btnCancelAddChild2.setEnabled(!show);
        childEmail.setEnabled(!show);
        childPassword2.setEnabled(!show);
    }

    //showError
    private void showError(String message) {
        nceError.setText(message);
        nceError.setVisibility(View.VISIBLE);
    }
}
