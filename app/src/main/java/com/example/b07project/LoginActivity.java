package com.example.b07project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.b07project.auth.AuthRepo;
import com.example.b07project.auth.FirebaseAuthRepo;
import com.example.b07project.auth.LoginContract;
import com.example.b07project.auth.LoginPresenter;
import com.example.b07project.main.WelcomeActivity;
import com.google.firebase.auth.FirebaseAuth;



//AuthRepo/FirebaseAuthRepo is the model;
//handles business logic with the database.
//LoginActivity is the view; its the UI layer displaying info and getting input.
//look into loginContract.java for clearer idea.
//LoginPresenter is the presenter.
public class LoginActivity extends AppCompatActivity implements LoginContract.View {

    private EditText etEmail, etPassword;
    private ProgressBar progress;
    private TextView tvError, tvSignUp, tvForgotPassword;
    private LoginContract.Presenter presenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        progress = findViewById(R.id.progress);
        tvError = findViewById(R.id.tvError);
        tvSignUp = findViewById(R.id.tvSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        presenter = new LoginPresenter(this, new FirebaseAuthRepo());

        btnLogin.setOnClickListener(v -> presenter.onLoginClicked());

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(this, WelcomeActivity.class));
        });

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        EditText etEmail = view.findViewById(R.id.etEmail);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSend = view.findViewById(R.id.btnSend);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSend.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter your email", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            sendPasswordResetEmail(email);
        });

        dialog.show();
    }

    private void sendPasswordResetEmail(String email) {
        showLoading(true);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        showEmailSentDialog(email);
                    } else {
                        String error = "Failed to send reset email.";
                        if (task.getException() != null) {
                            error = task.getException().getMessage();
                        }
                        showError(error);
                    }
                });
    }

    private void showEmailSentDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_email_sent, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView tvMessage = view.findViewById(R.id.tvSentMessage);
        tvMessage.setText("We have sent a password reset link to " + email + ". Please check your inbox.");

        Button btnOk = view.findViewById(R.id.btnOk);
        btnOk.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    public void showLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    @Override
    public void navigateToHome() {
        tvError.setVisibility(View.GONE);
        startActivity(new Intent(this, HomeActivity.class));
        finish();

    }

    @Override
    public void navigateToProviderHome() {
        tvError.setVisibility(View.GONE);
        startActivity(new Intent(this, ProviderHomeActivity.class));
        finish();
    }

    @Override
    public void navigateToDeviceChooser() {
        tvError.setVisibility(View.GONE);
        startActivity(new Intent(this, DeviceChooserActivity.class));
        finish();

    }

    @Override
    public void showEmailNotVerifiedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Email not verified")
                .setMessage("Please verify your email using the link we sent before logging in.")
                .setPositiveButton("OK", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public String getEmailInput() {
        return etEmail.getText().toString().trim();
    }

    @Override
    public String getPasswordInput() {
        return etPassword.getText().toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.onDestroy();
    }
}
