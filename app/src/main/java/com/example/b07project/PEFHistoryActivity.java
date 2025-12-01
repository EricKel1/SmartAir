package com.example.b07project;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.adapters.PEFHistoryAdapter;
import com.example.b07project.models.PEFReading;
import com.example.b07project.repository.PEFRepository;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;

public class PEFHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PEFRepository pefRepository;
    private String childId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pef_history);

        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        if (childId == null) {
            childId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        pefRepository = new PEFRepository();
        loadHistory();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void loadHistory() {
        pefRepository.getPEFReadingsForUser(childId, new PEFRepository.LoadCallback<List<PEFReading>>() {
            @Override
            public void onSuccess(List<PEFReading> readings) {
                if (readings != null && !readings.isEmpty()) {
                    PEFHistoryAdapter adapter = new PEFHistoryAdapter(readings);
                    recyclerView.setAdapter(adapter);
                } else {
                    // Show empty state if needed
                }
            }

            @Override
            public void onFailure(String error) {
                // Handle error
            }
        });
    }
}
