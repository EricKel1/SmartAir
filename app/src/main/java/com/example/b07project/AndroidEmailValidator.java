package com.example.b07project;

public class AndroidEmailValidator extends EmailValidator {

    public boolean isEmailValid(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
}

