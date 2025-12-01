package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.b07project.main.WelcomeActivity;
import com.example.b07project.repository.SignupRepository;

public class ProviderSignupActivity extends AppCompatActivity {
    BackToParent bh = new BackToParent();
    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button btnSignUp;
    private ProgressBar progressBar;
    private SignupRepository signupRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_signup);

        signupRepository = new SignupRepository();

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnBack).setOnClickListener(v -> bh.backTo(this, WelcomeActivity.class));

        btnSignUp.setOnClickListener(v -> attemptSignup());
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void attemptSignup() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Valid email is required");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        showLoading(true);

        signupRepository.createProviderAccount(email, password, name, new SignupRepository.OnSignupCompleteListener() {
            @Override
            public void onSuccess(boolean verificationBypassed) {
                showLoading(false);
                if (verificationBypassed) {
                    Toast.makeText(ProviderSignupActivity.this, "Test account created!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ProviderSignupActivity.this, ProviderHomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    showSuccessDialog(email);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                showLoading(false);
                Toast.makeText(ProviderSignupActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
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

        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        tvMessage.setText("We've sent a verification link to " + email + ". Please check your inbox and verify your email before logging in.");

        dialogView.findViewById(R.id.btnDialogLogin).setOnClickListener(v -> {
            dialog.dismiss();
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ProviderSignupActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnSignUp.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnSignUp.setEnabled(true);
        }
    }
}
