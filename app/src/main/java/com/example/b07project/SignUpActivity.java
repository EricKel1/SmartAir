package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

public class SignUpActivity extends AppCompatActivity {
    
    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button btnSignUp;
    private ProgressBar progress;
    private TextView tvError, tvSignIn;
    
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        initializeViews();
        setupListeners();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }
    
    private void initializeViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        progress = findViewById(R.id.progress);
        tvError = findViewById(R.id.tvError);
        tvSignIn = findViewById(R.id.tvSignIn);
    }
    
    private void setupListeners() {
        btnSignUp.setOnClickListener(v -> attemptSignUp());
        
        tvSignIn.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
    
    private void attemptSignUp() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();
        
        if (!validateInput(name, email, password, confirmPassword)) {
            return;
        }
        
        showLoading(true);
        
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        // Store account creation timestamp
                        long creationTime = System.currentTimeMillis();
                        java.util.Map<String, Object> userData = new java.util.HashMap<>();
                        userData.put("userId", user.getUid());
                        userData.put("email", email);
                        userData.put("name", name);
                        userData.put("role", "child");
                        userData.put("accountCreatedAt", creationTime);
                        
                        db.collection("users").document(user.getUid())
                            .set(userData)
                            .addOnSuccessListener(aVoid -> {
                                android.util.Log.d("SignUpActivity", "User data saved with creation time: " + creationTime);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.e("SignUpActivity", "Failed to save user data", e);
                            });
                        
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();
                        
                        user.updateProfile(profileUpdates)
                            .addOnCompleteListener(profileTask -> {
                                if (profileTask.isSuccessful()) {
                                    sendVerificationEmail(user);
                                } else {
                                    showLoading(false);
                                    Toast.makeText(SignUpActivity.this, 
                                        "Account created, but profile update failed", 
                                        Toast.LENGTH_SHORT).show();
                                    navigateToHome();
                                }
                            });
                    }
                   } else {
                    showLoading(false);
                    String errorMessage = "Sign up failed";
                    if (task.getException() != null) {
                        String exceptionMessage = task.getException().getMessage();
                        if (exceptionMessage != null) {
                            if (exceptionMessage.contains("email address is already in use")) {
                                errorMessage = "This email is already registered";
                            } else if (exceptionMessage.contains("network error")) {
                                errorMessage = "Network error. Please check your connection";
                            } else {
                                errorMessage = exceptionMessage;
                            }
                        }
                    }
                    showError(errorMessage);
                }
            });
    }
    
    private void sendVerificationEmail(FirebaseUser user) {
        if (user.getEmail() != null && user.getEmail().endsWith("@test.com")) {
            showLoading(false);
            Toast.makeText(SignUpActivity.this, "Test account created!", Toast.LENGTH_SHORT).show();
            navigateToHome();
            return;
        }

        user.sendEmailVerification()
            .addOnCompleteListener(task -> {
                showLoading(false);
                if (task.isSuccessful()) {
                    android.util.Log.d("SignUpActivity", "Verification email sent to " + user.getEmail());
                    showSuccessDialog(user.getEmail());
                } else {
                    android.util.Log.e("SignUpActivity", "Failed to send verification email", task.getException());
                    Toast.makeText(SignUpActivity.this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private boolean validateInput(String name, String email, String password, String confirmPassword) {
        if (TextUtils.isEmpty(name)) {
            showError("Please enter your name");
            etName.requestFocus();
            return false;
        }
        
        if (TextUtils.isEmpty(email)) {
            showError("Please enter your email");
            etEmail.requestFocus();
            return false;
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address");
            etEmail.requestFocus();
            return false;
        }
        
        if (TextUtils.isEmpty(password)) {
            showError("Please enter a password");
            etPassword.requestFocus();
            return false;
        }
        
        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }
        
        if (TextUtils.isEmpty(confirmPassword)) {
            showError("Please confirm your password");
            etConfirmPassword.requestFocus();
            return false;
        }
        
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private void showLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSignUp.setEnabled(!show);
        etName.setEnabled(!show);
        etEmail.setEnabled(!show);
        etPassword.setEnabled(!show);
        etConfirmPassword.setEnabled(!show);
    }
    
    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
    
    private void showSuccessDialog(String email) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_verification_sent, null);
        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.setCancelable(false);

        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        tvMessage.setText("We've sent a verification link to " + email + ". Please check your inbox and verify your email before logging in.");

        dialogView.findViewById(R.id.btnDialogLogin).setOnClickListener(v -> {
            dialog.dismiss();
            auth.signOut();
            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
