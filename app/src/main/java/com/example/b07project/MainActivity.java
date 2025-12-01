package com.example.b07project;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            Log.d("FirebaseCheck", "Firebase initialized: " + app.getName()); // expect [DEFAULT]
        } catch (IllegalStateException e) {
            Log.e("FirebaseCheck", "Firebase NOT initialized: " + e.getMessage());
        }
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }
}