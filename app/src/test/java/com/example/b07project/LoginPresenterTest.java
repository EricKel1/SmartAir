package com.example.b07project;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.b07project.auth.AuthRepo;
import com.example.b07project.auth.LoginContract;
import com.example.b07project.auth.LoginPresenter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


//READBEFORE:
//I will be documenting certain parts of the tests so that future testers who want to build on this
//test suite, or make a similar one, can do so with minimal downtime due to learning.
@RunWith(MockitoJUnitRunner.class)
public class LoginPresenterTest {
    @Mock private LoginContract.View mockView;
    @Mock private AuthRepo mockRepo;
    @Mock private FirebaseAuth mockFirebaseAuth;
    @Mock private FirebaseUser mockFirebaseUser;

     private MockedStatic<FirebaseAuth> mockedFirebaseAuth;
     private LoginPresenter realPresenter;
     private LoginPresenter spyPresenter;

    @Before
    public void setUpTests(){
        mockedFirebaseAuth = mockStatic(FirebaseAuth.class);
        mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(mockFirebaseAuth);

        // Create the real presenter first
        realPresenter = new LoginPresenter(mockView, mockRepo);

        //In mockito a spy serves  as a way to  call methods that rely on other classes
        // without using their implementations. Trying to call just the real doesn't work or has pitfalls
        // , like if it requires a FirebaseAuth that doesn't yet exist or works incorrectly.
        // they allow you to replace specific methods that rely on other classes we arent testing,
        // with mocks to pretend those classes did their behaviour correctly.
        spyPresenter = Mockito.spy(realPresenter);
    }

    @After
    public void tearDown() {
        //mockedFirebaseAuth is part of mockStatic so if we don't close it
        //it can stay active for an indeterminate amount of time and can stop our test suite from
        //finishing execution.

        if (mockedFirebaseAuth != null) {
            mockedFirebaseAuth.close();
        }
    }
    

    //Test
    @Test
    public void onLoginWithEmptyEmailShowsError(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);

        //We take pretend inputs "" for email and password respectively.
        //Mockview is a mock of the loginContract so we can pretend these values are
        //taken as input for the actual login.
        when(mockView.getEmailInput()).thenReturn("");
        when(mockView.getPasswordInput()).thenReturn("password123");

        //When we try to login
        spyPresenter.onLoginClicked();

        //Make sure it shows this error
        verify(mockView).showError("Enter a valid email/username and a 6+ character password.");
        //verify signIn was never called in AuthRepo; i.e. we never tried to sign in.
        verify(mockRepo, never()).signIn(anyString(), anyString(), any());
        //also verify never did the showLoading during the test.
        verify(mockView, never()).showLoading(true);
    }

    //Test
    @Test
    public void onLoginWithNullEmailShowsError(){
        //If the email input is null
        when(mockView.getEmailInput()).thenReturn(null);
        //If our password is 123456789
        when(mockView.getPasswordInput()).thenReturn("123456789");//valid password

        spyPresenter.onLoginClicked();
        //same as above test.
        verify(mockView).showError("Enter a valid email/username and a 6+ character password.");
        verify(mockRepo, never()).signIn(anyString(), anyString(), any());
        verify(mockView, never()).showLoading(true);
    }

    //Test
    @Test
    public void onLoginWithNullPasswordShowsError(){
        //Test for when the password input is null; we don't want to crash
        when(mockView.getEmailInput()).thenReturn("123@example.com");//valid email
        when(mockView.getPasswordInput()).thenReturn(null);

        spyPresenter.onLoginClicked();
        verify(mockView).showError("Enter a valid email/username and a 6+ character password.");
        verify(mockRepo, never()).signIn(anyString(), anyString(), any());
        verify(mockView, never()).showLoading(true);
    }

    //Test
    @Test
    public void onLoginWithShortPasswordShowsError(){
        when(mockView.getEmailInput()).thenReturn("123@example.com");//valid email
        when(mockView.getPasswordInput()).thenReturn("12345");

        spyPresenter.onLoginClicked();
        verify(mockView).showError("Enter a valid email/username and a 6+ character password.");
        verify(mockRepo, never()).signIn(anyString(), anyString(), any());
        verify(mockView, never()).showLoading(true);
    }

    //Test
    @Test
    public void onLoginWithValidEmailAndPasswordSignsIn(){
        // Creates spy and mock the email validation to return true
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        //This line "overrides" isValidEmailFormat to not execute implementation and
        //to just return true.
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");

        spyPresenter.onLoginClicked();
        //Verifies that signIn was called with those values and only once.
        verify(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));
        verify(mockView).showLoading(true);
    }

    //Test
    @Test
    public void onLoginWithUserNameConvertsToEmail() {
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(false).when(spyPresenter).isValidEmailFormat("123user");

        when(mockView.getEmailInput()).thenReturn("123user");
        when(mockView.getPasswordInput()).thenReturn("123456789");

        spyPresenter.onLoginClicked();

        verify(mockRepo).signIn(eq("123user@b07project.local"), eq("123456789"), any(AuthRepo.Callback.class));
        verify(mockView).showLoading(true);
    }

    //Test
    @Test
    public void onLoginWithSignInSuccessWithVerifiedEmailFetchRole(){
        // Create spy and mock the email validation to return true
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");//valid email
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(true);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        // Mock the signIn to capture callback and trigger onSuccess.
        //Basically it says to perform the code in the doAnswer when we call signIn on mockRepo.
        //It is mocking that our sign in succeeeded.
        doAnswer(invocation -> {
            //get the callback in index two of the when below.
            AuthRepo.Callback callback = invocation.getArgument(2);
            //signal success in the mock
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();

        verify(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));
        verify(mockView).showLoading(true);
    }

    //Test
    @Test
    public void onLoginWithSignInSuccessWIthVerificationBypass(){
        when(mockView.getEmailInput()).thenReturn("123@test.com");//valid email
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(false);
        when(mockFirebaseUser.getEmail()).thenReturn("123@test.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        //Assume 123@test.com is a valid email.
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@test.com");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@test.com"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();
        //verify getUserRole was called with these arguments once.
        verify(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));
        verify(mockView).showLoading(true);
        // Verify showEmailNotVerifiedDialog is not called (verification bypassed)
        verify(mockView, never()).showEmailNotVerifiedDialog();
    }
    @Test
    public void onLoginWithSignInSuccessWithChildUserWithVerificationBypass(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("child@b07project.local");

        when(mockView.getEmailInput()).thenReturn("child@b07project.local");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(false);
        when(mockFirebaseUser.getEmail()).thenReturn("child@b07project.local");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("child@b07project.local"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();

        verify(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));
        verify(mockView, never()).showEmailNotVerifiedDialog();

    }

    @Test
    public void onLoginWithSignInSuccessWithUnverifiedEmail_showsDialog(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(false);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();

        verify(mockView).showEmailNotVerifiedDialog();
    }

    @Test
    public void onLoginWithSignInSuccessWithNullUserDoesNotCrash(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(null);

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));
        //If this throws an exception or fails the test automatically fails.
        spyPresenter.onLoginClicked();

        verify(mockRepo, never()).getUserRole(anyString(), any(AuthRepo.RoleCallback.class));
    }

    @Test
    public void onLoginWithSignInErrorShowsError(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onError(new Exception("Authentication failed"));
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();

        verify(mockView).showError("Authentication failed");
    }

    @Test
    public void onLoginWithSignInErrorWithNullMessageShowDefaultError(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onError(new Exception());
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();

        verify(mockView).showError("Login failed");
    }

    @Test
    public void fetchUserRoleWithParentRoleNavigatesToDeviceChooser(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(true);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        doAnswer(invocation -> {
            AuthRepo.RoleCallback callback = invocation.getArgument(1);
            //pretend it returned "parent"
            callback.onRole("parent");
            return null;
        }).when(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));

        spyPresenter.onLoginClicked();

        verify(mockView).navigateToDeviceChooser();
    }

    @Test
    public void fetchUserRoleWithProviderRoleNavigatesToProviderHome(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(true);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        doAnswer(invocation -> {
            AuthRepo.RoleCallback callback = invocation.getArgument(1);
            callback.onRole("provider");
            return null;
        }).when(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));

        spyPresenter.onLoginClicked();

        verify(mockView).navigateToProviderHome();
    }

    @Test
    public void fetchUserRoleWithOtherRoleNavigatesToHome(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(true);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        doAnswer(invocation -> {
            AuthRepo.RoleCallback callback = invocation.getArgument(1);
            callback.onRole("child");
            return null;
        }).when(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));

        spyPresenter.onLoginClicked();
        //Home is the child screen.
        verify(mockView).navigateToHome();
    }

    @Test
    public void fetchUserRoleErrorShowsError(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(true);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        //Pretend an exception was thrown when we tried to getUserRole
        doAnswer(invocation -> {
            AuthRepo.RoleCallback callback = invocation.getArgument(1);
            callback.onError(new Exception("Database error"));
            return null;
        }).when(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));

        spyPresenter.onLoginClicked();

        verify(mockView).showError("Failed to get user role: Database error");
    }

    @Test
    public void onDestroySetViewToNull(){
        realPresenter.onDestroy();
        realPresenter.onLoginClicked();

        verify(mockView, never()).getEmailInput();
    }

    @Test
    public void signInCallbackSuccessDoesntCrashAfterDestroy(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
//        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);

        doAnswer(invocation -> {
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();
        spyPresenter.onDestroy();
    }

    @Test
    public void signInCallbackErrorDoesntCrashAfterDestroy(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");

        doAnswer(invocation -> {
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        spyPresenter.onLoginClicked();
        spyPresenter.onDestroy();
    }

    @Test
    public void roleCallbackDoesNotCrashOnRoleAfterDestroy(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(true);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        doAnswer(invocation -> {
            return null;
        }).when(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));

        spyPresenter.onLoginClicked();
        spyPresenter.onDestroy();
    }

    @Test
    public void roleCallbackDoesNotCrashOnErrorAfterDestroy(){
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("123@example.com");

        when(mockView.getEmailInput()).thenReturn("123@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");
        when(mockFirebaseAuth.getCurrentUser()).thenReturn(mockFirebaseUser);
        when(mockFirebaseUser.isEmailVerified()).thenReturn(true);
        when(mockFirebaseUser.getEmail()).thenReturn("123@example.com");
        when(mockFirebaseUser.getUid()).thenReturn("user123");

        doAnswer(invocation -> {
            AuthRepo.Callback callback = invocation.getArgument(2);
            callback.onSuccess();
            return null;
        }).when(mockRepo).signIn(eq("123@example.com"), eq("123456789"), any(AuthRepo.Callback.class));

        doAnswer(invocation -> {
            return null;
        }).when(mockRepo).getUserRole(eq("user123"), any(AuthRepo.RoleCallback.class));

        spyPresenter.onLoginClicked();
        spyPresenter.onDestroy();
    }


    @Test
    public void onLoginWithNullViewDoesNothing(){
        realPresenter.onDestroy();
        realPresenter.onLoginClicked();

        verify(mockView, never()).getEmailInput();
    }

    @Test
    public void onDestroySetsViewToNull() {
        LoginPresenter spyPresenter = Mockito.spy(realPresenter);
        doReturn(true).when(spyPresenter).isValidEmailFormat("test@example.com");

        when(mockView.getEmailInput()).thenReturn("test@example.com");
        when(mockView.getPasswordInput()).thenReturn("123456789");

        spyPresenter.onLoginClicked();
        verify(mockView).getEmailInput();
        verify(mockView).getPasswordInput();
        spyPresenter.onDestroy();

        reset(mockView);
        spyPresenter.onLoginClicked();
        verify(mockView, never()).getEmailInput();
        verify(mockView, never()).getPasswordInput();
        verify(mockView, never()).showError(anyString());
        verify(mockView, never()).showLoading(anyBoolean());
    }

}