package com.example.b07project;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ParentChildLogsActivity extends AppCompatActivity {

    private RecyclerView rvInviteCodes;
    private TextView tvEmptyState;
    private TextView tvChildName;
    private ExtendedFloatingActionButton btnCreateCode;
    private InviteCodeAdapter adapter;
    private List<DocumentSnapshot> codeList;
    private String childName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_child_logs);

        childName = getIntent().getStringExtra("childName");

        initializeViews();
        setupRecyclerView();
        setupListeners();
        fetchInviteCodes();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void initializeViews() {
        rvInviteCodes = findViewById(R.id.rvInviteCodes);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvChildName = findViewById(R.id.tvChildName);
        btnCreateCode = findViewById(R.id.btnCreateCode);

        if (childName != null) {
            tvChildName.setText("Child: " + childName);
        }
    }

    private void setupRecyclerView() {
        codeList = new ArrayList<>();
        adapter = new InviteCodeAdapter(codeList);
        rvInviteCodes.setLayoutManager(new LinearLayoutManager(this));
        rvInviteCodes.setAdapter(adapter);
    }

    private void setupListeners() {
        btnCreateCode.setOnClickListener(v -> attemptCreateCode());
        
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void fetchInviteCodes() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("invite_codes")
                .whereEqualTo("uid", user.getUid())
                .whereEqualTo("child", childName)
                .orderBy("expiresAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    codeList.clear();
                    codeList.addAll(queryDocumentSnapshots.getDocuments());
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading codes: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateEmptyState() {
        if (codeList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            rvInviteCodes.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            rvInviteCodes.setVisibility(View.VISIBLE);
        }
    }

    private void attemptCreateCode() {
        String code = generateInviteCode();
        makeCodeUnique(code);
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void makeCodeUnique(String code) {
        FirebaseFirestore.getInstance().collection("invite_codes").document(code).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        makeCodeUnique(generateInviteCode());
                    } else {
                        saveCodeToDb(code);
                    }
                });
    }

    private void saveCodeToDb(String code) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        long expireTime = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000;
        Map<String, Object> codeData = new HashMap<>();
        codeData.put("code", code);
        codeData.put("expiresAt", new Timestamp(new Date(expireTime)));
        codeData.put("email", user.getEmail());
        codeData.put("uid", user.getUid());
        codeData.put("child", childName);

        FirebaseFirestore.getInstance().collection("invite_codes").document(code).set(codeData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Code generated!", Toast.LENGTH_SHORT).show();
                    fetchInviteCodes(); // Refresh list
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error creating code", Toast.LENGTH_SHORT).show());
    }

    private void revokeCode(String code) {
        FirebaseFirestore.getInstance().collection("invite_codes").document(code)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Code revoked", Toast.LENGTH_SHORT).show();
                    fetchInviteCodes();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error revoking code", Toast.LENGTH_SHORT).show());
    }

    private class InviteCodeAdapter extends RecyclerView.Adapter<InviteCodeAdapter.ViewHolder> {
        private List<DocumentSnapshot> codes;

        public InviteCodeAdapter(List<DocumentSnapshot> codes) {
            this.codes = codes;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_invite_code, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = codes.get(position);
            String code = doc.getString("code");
            Timestamp expiresAt = doc.getTimestamp("expiresAt");

            holder.tvCode.setText(code);
            if (expiresAt != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                holder.tvExpires.setText("Expires: " + sdf.format(expiresAt.toDate()));
            }

            holder.btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Invite Code", code);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ParentChildLogsActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            });

            holder.btnRevoke.setOnClickListener(v -> revokeCode(code));
        }

        @Override
        public int getItemCount() {
            return codes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvCode, tvExpires;
            ImageButton btnCopy, btnRevoke;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCode = itemView.findViewById(R.id.tvCode);
                tvExpires = itemView.findViewById(R.id.tvExpires);
                btnCopy = itemView.findViewById(R.id.btnCopy);
                btnRevoke = itemView.findViewById(R.id.btnRevoke);
            }
        }
    }
}



