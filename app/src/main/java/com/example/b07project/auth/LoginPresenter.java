package com.example.b07project.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginPresenter implements LoginContract.Presenter {
    private LoginContract.View view;
    private final AuthRepo repo;

    public LoginPresenter(LoginContract.View view, AuthRepo repo) {
        this.view = view;
        this.repo = repo;
    }
    //make public for testing
    public boolean isValidEmailFormat(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
    @Override
    public void onLoginClicked() {
        if (view == null) return;
        String input = view.getEmailInput();
        String pass  = view.getPasswordInput();

        if (input == null || input.isEmpty() || pass == null || pass.length() < 6) {
            view.showError("Enter a valid email/username and a 6+ character password.");
            return;
        }

        String email = input;
//        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
//            // Assume it's a username
//            email = input + "@b07project.local";
//        }
        if (!isValidEmailFormat(input)) {  // Use the extracted method
            // Assume it's a username
            email = input + "@b07project.local";
        }
        view.showLoading(true);
        repo.signIn(email, pass, new AuthRepo.Callback() {
            @Override public void onSuccess() {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    // Bypass verification check for test accounts and child accounts (username login)
                    boolean isTestUser = user.getEmail() != null && user.getEmail().endsWith("@test.com");
                    boolean isChildUser = user.getEmail() != null && user.getEmail().endsWith("@b07project.local");

                    if (user.isEmailVerified() || isTestUser || isChildUser) {
                        // Email is verified or is a test/child user, proceed with role check
                        fetchUserRole(user.getUid());
                    } else {
                        // Email not verified, show dialog and sign out
                        if (view != null) {
                            view.showLoading(false);
                            view.showEmailNotVerifiedDialog();
                        }
                    }
                }
            }
            @Override public void onError(Exception e) {
                if (view != null) {
                    view.showLoading(false);
                    view.showError(e.getMessage() != null ? e.getMessage() : "Login failed");
                }
            }
        });
    }

    private void fetchUserRole(String uid) {
        repo.getUserRole(uid, new AuthRepo.RoleCallback() {
            @Override
            public void onRole(String role) {
                if (view != null) {
                    view.showLoading(false);
                    if ("parent".equals(role)) {
                        view.navigateToDeviceChooser();
                    } else if ("provider".equals(role)) {
                        view.navigateToProviderHome();
                    } else {
                        view.navigateToHome();
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (view != null) {
                    view.showLoading(false);
                    view.showError("Failed to get user role: " + e.getMessage());
                }
            }
        });
    }

    @Override public void onDestroy() { view = null; }
}
