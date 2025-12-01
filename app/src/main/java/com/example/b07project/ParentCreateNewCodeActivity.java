package com.example.b07project;

import android.app.Activity;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import com.google.firebase.Timestamp;

public class ParentCreateNewCodeActivity extends AppCompatActivity {
    private Button btnToProviderPermissions;
    private Button btnBackToLogs, codeCopy;
    private TextView sevenDayCodePlaceholder;

    private BackToParent bh = new BackToParent();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //always load screen first
        setContentView(R.layout.activity_parent_create_new_code);

        Intent intent = getIntent();
        String providerCode = intent.getStringExtra("providerCode");
        initializeViews();
        setupListeners(providerCode);

        //get providerCode from previous screen
        sevenDayCodePlaceholder.setText(providerCode);
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();


    }
    private void initializeViews(){
        btnToProviderPermissions = findViewById(R.id.btnToProviderPermissions);
        btnBackToLogs = findViewById(R.id.btnBackToLogs);
        sevenDayCodePlaceholder = findViewById(R.id.sevenDayCodePlaceholder);
        codeCopy = findViewById(R.id.codeCopy);
    }

    private void setupListeners(String code){
        btnToProviderPermissions.setOnClickListener(v -> {
            //Will link to the page that lets you modify provider permissions.

        });
        btnBackToLogs.setOnClickListener((v -> bh.backTo(this)));
        codeCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Provider Code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_LONG).show();
         });
    }
}
