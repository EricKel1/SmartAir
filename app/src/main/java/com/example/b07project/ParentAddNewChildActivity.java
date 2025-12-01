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

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ParentAddNewChildActivity extends AppCompatActivity {
    //This class serves to serve the screen to
    //add a new child to a parent's account,
    //specifically with the underlying logic of the adding.

    private EditText childName, childDateOfBirth, childUsername, childPassword;
    private com.google.android.material.textfield.TextInputLayout tilChildUsername, tilChildPassword;
    private com.google.android.material.switchmaterial.SwitchMaterial switchEnableLogin;

    private Button btnAddChild, btnCancelAddChild;
    private TextView ncError2;
    private ProgressBar progress3;
    private FirebaseAuth auth;

    private BackToParent bh = new BackToParent();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_add_new_child);

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
        childName = findViewById(R.id.childName);
        childDateOfBirth = findViewById(R.id.childDateOfBirth);
        childUsername = findViewById(R.id.childUsername);
        childPassword = findViewById(R.id.childPassword);
        tilChildUsername = findViewById(R.id.tilChildUsername);
        tilChildPassword = findViewById(R.id.tilChildPassword);
        switchEnableLogin = findViewById(R.id.switchEnableLogin);
        btnAddChild = findViewById(R.id.btnAddChild);
        btnCancelAddChild = findViewById(R.id.btnCancelAddChild);
        ncError2 = findViewById(R.id.ncError2);
        progress3 = findViewById(R.id.progress3);
    }

    private void setupListeners(){
        btnAddChild.setOnClickListener(v -> attemptAddChild());
        btnCancelAddChild.setOnClickListener((v -> bh.backTo(this)));

        CalendarPicker cp = new CalendarPicker();
        childDateOfBirth.setOnClickListener(v -> cp.openCalendar(this, childDateOfBirth));
        
        switchEnableLogin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tilChildUsername.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            tilChildPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }
    private void attemptAddChild(){
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String name = childName.getText().toString().trim();
        String age = childDateOfBirth.getText().toString().trim();
        
        boolean createLogin = switchEnableLogin.isChecked();
        String username = createLogin ? childUsername.getText().toString().trim() : null;
        String password = createLogin ? childPassword.getText().toString().trim() : null;


        if(!validateInput(name, age, username, password, createLogin)) {
            return;
        }

        showLoading(true);

        if (createLogin) {
            // Create secondary app to create user without signing out parent
            String secondaryAppName = "SecondaryApp";
            com.google.firebase.FirebaseApp secondaryApp = null;
            try {
                secondaryApp = com.google.firebase.FirebaseApp.getInstance(secondaryAppName);
            } catch (IllegalStateException e) {
                com.google.firebase.FirebaseOptions options = com.google.firebase.FirebaseApp.getInstance().getOptions();
                secondaryApp = com.google.firebase.FirebaseApp.initializeApp(this, options, secondaryAppName);
            }

            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
            String email = username + "@b07project.local"; // Construct email from username

            com.google.firebase.FirebaseApp finalSecondaryApp = secondaryApp;
            secondaryAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        String childUid = authResult.getUser().getUid();
                        String parentId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                        // 1. Create User Document (in 'users' collection)
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("name", name);
                        userMap.put("email", email);
                        userMap.put("username", username);
                        userMap.put("role", "child");
                        userMap.put("parentId", parentId);
                        userMap.put("hasParent", true);
                        userMap.put("accountCreatedAt", System.currentTimeMillis());

                        // Use secondary db instance (as the child) to write to users collection
                        FirebaseFirestore secondaryDb = FirebaseFirestore.getInstance(finalSecondaryApp);
                        
                        secondaryDb.collection("users").document(childUid).set(userMap)
                                .addOnSuccessListener(aVoid -> {
                                    // 2. Create Child Document (in 'children' collection) - Parent writes this
                                    Map<String, Object> childMap = new HashMap<>();
                                    childMap.put("name", name);
                                    childMap.put("dateOfBirth", age);
                                    childMap.put("parentId", parentId);
                                    childMap.put("uid", childUid); // Link to Auth UID
                                    childMap.put("username", username);
                                    childMap.put("createdAt", System.currentTimeMillis());
                                    
                                    // Add default sharing settings
                                    Map<String, Boolean> sharing = new HashMap<>();
                                    sharing.put("rescueLogs", false);
                                    sharing.put("controllerAdherence", false);
                                    sharing.put("symptoms", false);
                                    sharing.put("triggers", false);
                                    sharing.put("pef", false);
                                    sharing.put("triage", false);
                                    sharing.put("summaryCharts", false);
                                    childMap.put("sharingSettings", sharing);

                                    // Use main db instance (as parent) to write to children collection
                                    db.collection("children").document(childUid).set(childMap)
                                            .addOnSuccessListener(docRef -> {
                                                secondaryAuth.signOut();
                                                showLoading(false);
                                                Toast.makeText(this, "Child account created successfully", Toast.LENGTH_SHORT).show();
                                                bh.backTo(this);
                                            })
                                            .addOnFailureListener(e -> {
                                                secondaryAuth.signOut();
                                                showLoading(false);
                                                showError("Failed to save child data: " + e.getMessage());
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    secondaryAuth.signOut();
                                    showLoading(false);
                                    showError("Failed to create user profile: " + e.getMessage());
                                });
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        if (e.getMessage() != null && e.getMessage().contains("email address is already in use")) {
                            showError("Username is already taken");
                        } else {
                            showError("Error creating account: " + e.getMessage());
                        }
                    });
        } else {
            // No login credentials
            Map<String, Object> childMap = new HashMap<>();
            childMap.put("name", name);
            childMap.put("dateOfBirth", age);
            childMap.put("parentId", FirebaseAuth.getInstance().getCurrentUser().getUid());
            childMap.put("createdAt", System.currentTimeMillis());
            
            // Add default sharing settings
            Map<String, Boolean> sharing = new HashMap<>();
            sharing.put("rescueLogs", false);
            sharing.put("controllerAdherence", false);
            sharing.put("symptoms", false);
            sharing.put("triggers", false);
            sharing.put("pef", false);
            sharing.put("triage", false);
            sharing.put("summaryCharts", false);
            childMap.put("sharingSettings", sharing);

            db.collection("children").add(childMap)
                    .addOnSuccessListener(docRef -> {
                        showLoading(false);
                        Toast.makeText(this, "Child profile created", Toast.LENGTH_SHORT).show();
                        bh.backTo(this);
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        showError("Error: " + e.getMessage());
                    });
        }
    }

    private boolean validateInput(String name, String age, String username, String password, boolean checkLogin){
        if(Objects.equals(name, "") || name == null){
            showError("Please enter your child's name");
            ncError2.requestFocus();
            return false;
        }
        if(age == null || age.equals("")){
            showError("Please enter your child's date of birth");
            ncError2.requestFocus();
            return false;
        }
        
        if (checkLogin) {
            if(username == null || username.equals("")){
                showError("Please enter a username");
                ncError2.requestFocus();
                return false;
            }
            if(password == null || password.length() < 6){
                showError("Password must be at least 6 characters");
                ncError2.requestFocus();
                return false;
            }
        }
        return true;
    }
    //showLoading
    private void showLoading(boolean show) {
        progress3.setVisibility(show ? View.VISIBLE : View.GONE);
        btnAddChild.setEnabled(!show);
        btnCancelAddChild.setEnabled(!show);
        childName.setEnabled(!show);
        childDateOfBirth.setEnabled(!show);
        childUsername.setEnabled(!show);
        childPassword.setEnabled(!show);
    }

    //showError
    private void showError(String message) {
        ncError2.setText(message);
        ncError2.setVisibility(View.VISIBLE);
    }


}
