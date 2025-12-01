package com.example.b07project;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.b07project.models.MedicationSchedule;
import com.example.b07project.models.MedicineInventory;
import com.example.b07project.repository.InventoryRepository;
import com.example.b07project.repository.ScheduleRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfigureScheduleActivity extends AppCompatActivity {

    private Spinner spinnerMedication;
    private EditText etDosage;
    private Spinner spinnerFrequency;
    private LinearLayout layoutTimePickers;
    private Button btnSaveSchedule;
    private ScheduleRepository scheduleRepository;
    private InventoryRepository inventoryRepository;
    private String childId;
    private List<String> selectedTimes = new ArrayList<>();
    private List<String> medicineNames = new ArrayList<>();
    private Map<String, String> childrenMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure_schedule);

        childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        scheduleRepository = new ScheduleRepository();
        inventoryRepository = new InventoryRepository(this);

        initializeViews();
        setupFrequencySpinner();
        fetchChildren();
        loadInventoryMedicines(null);
        loadExistingSchedule();

        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBackCS).setOnClickListener(v -> finish());
        TopMover mover = new TopMover(this);
        mover.adjustTop();
        btnSaveSchedule.setOnClickListener(v -> saveSchedule());
    }

    private void initializeViews() {
        spinnerMedication = findViewById(R.id.spinnerMedication);
        etDosage = findViewById(R.id.etDosage);
        spinnerFrequency = findViewById(R.id.spinnerFrequency);
        layoutTimePickers = findViewById(R.id.layoutTimePickers);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
    }

    private void fetchChildren() {
        FirebaseFirestore.getInstance().collection("children")
                .whereEqualTo("parentId", FirebaseAuth.getInstance().getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(snapshots -> {
                    childrenMap.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        childrenMap.put(doc.getId(), doc.getString("name"));
                    }
                });
    }

    private void loadInventoryMedicines(String preSelectName) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        inventoryRepository.getMedicines(userId, new InventoryRepository.LoadCallback() {
            @Override
            public void onSuccess(List<MedicineInventory> medicines) {
                medicineNames.clear();
                for (MedicineInventory med : medicines) {
                    if (!medicineNames.contains(med.getName())) {
                        medicineNames.add(med.getName());
                    }
                }
                medicineNames.add("Add New Medicine...");

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    ConfigureScheduleActivity.this,
                    android.R.layout.simple_spinner_item,
                    medicineNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerMedication.setAdapter(adapter);

                spinnerMedication.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String selected = medicineNames.get(position);
                        if (selected.equals("Add New Medicine...")) {
                            showMedicineDialog();
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                if (preSelectName != null) {
                    int index = medicineNames.indexOf(preSelectName);
                    if (index >= 0) {
                        spinnerMedication.setSelection(index);
                    }
                }
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(ConfigureScheduleActivity.this, "Error loading medicines", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMedicineDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_medicine, null);
        builder.setView(view);
        builder.setTitle("Add Medicine");

        TextInputEditText etName = view.findViewById(R.id.etName);
        Spinner spChild = view.findViewById(R.id.spChild);
        Spinner spType = view.findViewById(R.id.spType);
        TextInputEditText etTotalDoses = view.findViewById(R.id.etTotalDoses);
        TextInputEditText etExpiry = view.findViewById(R.id.etExpiry);
        
        // Setup Child Spinner
        List<String> childNames = new ArrayList<>();
        List<String> childIds = new ArrayList<>();
        childNames.add("Assign to: Me (Parent)");
        childIds.add(null);
        
        for (Map.Entry<String, String> entry : childrenMap.entrySet()) {
            childNames.add("Assign to: " + entry.getValue());
            childIds.add(entry.getKey());
        }
        
        ArrayAdapter<String> childAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, childNames);
        childAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spChild.setAdapter(childAdapter);

        // Pre-select current child if possible
        if (childId != null) {
            int index = childIds.indexOf(childId);
            if (index >= 0) spChild.setSelection(index);
        }

        // Setup Type Spinner
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Rescue", "Controller"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);

        final Calendar calendar = Calendar.getInstance();
        final Date[] expiryDate = {null};
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

        etExpiry.setOnClickListener(v -> {
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                expiryDate[0] = calendar.getTime();
                etExpiry.setText(sdf.format(expiryDate[0]));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = builder.create();
        
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add", (d, which) -> {});
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (d, which) -> {
            dialog.dismiss();
            // Reset spinner to first item if cancelled
            if (!medicineNames.isEmpty()) spinnerMedication.setSelection(0);
        });

        dialog.show();
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String type = spType.getSelectedItem().toString();
            String dosesStr = etTotalDoses.getText().toString().trim();
            int selectedChildIndex = spChild.getSelectedItemPosition();
            String selectedChildId = childIds.get(selectedChildIndex);
            String selectedChildName = selectedChildIndex == 0 ? "Parent" : childrenMap.get(selectedChildId);

            if (name.isEmpty() || dosesStr.isEmpty() || expiryDate[0] == null) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int totalDoses = Integer.parseInt(dosesStr);

            MedicineInventory newMedicine = new MedicineInventory();
            newMedicine.setUserId(FirebaseAuth.getInstance().getCurrentUser().getUid());
            newMedicine.setName(name);
            newMedicine.setType(type);
            newMedicine.setTotalDoses(totalDoses);
            newMedicine.setRemainingDoses(totalDoses);
            newMedicine.setExpiryDate(expiryDate[0]);
            newMedicine.setChildId(selectedChildId);
            newMedicine.setChildName(selectedChildName);
            newMedicine.setPurchaseDate(new Date());

            inventoryRepository.addMedicine(newMedicine, new InventoryRepository.SaveCallback() {
                @Override
                public void onSuccess() {
                    dialog.dismiss();
                    Toast.makeText(ConfigureScheduleActivity.this, "Medicine added", Toast.LENGTH_SHORT).show();
                    loadInventoryMedicines(name); // Reload and select new medicine
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(ConfigureScheduleActivity.this, "Failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupFrequencySpinner() {
        String[] frequencies = {"1 time per day", "2 times per day", "3 times per day", "4 times per day"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, frequencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(adapter);

        spinnerFrequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTimePickers(position + 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateTimePickers(int count) {
        layoutTimePickers.removeAllViews();
        List<String> oldTimes = new ArrayList<>(selectedTimes);
        selectedTimes.clear();
        
        // Preserve existing times if possible, otherwise default
        for (int i = 0; i < count; i++) {
            if (i < oldTimes.size()) {
                selectedTimes.add(oldTimes.get(i));
            } else {
                selectedTimes.add("08:00"); // Default
            }
            addTimePickerRow(i);
        }
    }

    private void addTimePickerRow(int index) {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        TextView tvTime = view.findViewById(android.R.id.text1);
        
        tvTime.setText("Dose " + (index + 1) + ": " + selectedTimes.get(index));
        tvTime.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_recent_history, 0, 0, 0);
        tvTime.setPadding(16, 24, 16, 24);
        
        tvTime.setOnClickListener(v -> {
            String[] parts = selectedTimes.get(index).split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            new TimePickerDialog(this, (timePicker, h, m) -> {
                String time = String.format(Locale.getDefault(), "%02d:%02d", h, m);
                selectedTimes.set(index, time);
                tvTime.setText("Dose " + (index + 1) + ": " + time);
            }, hour, minute, true).show();
        });

        layoutTimePickers.addView(view);
    }

    private void loadExistingSchedule() {
        scheduleRepository.getSchedule(childId, new ScheduleRepository.LoadCallback() {
            @Override
            public void onSuccess(MedicationSchedule schedule) {
                if (schedule != null) {
                    // Pre-select medicine in spinner if available
                    String medName = schedule.getMedicationName();
                    if (medName != null && !medicineNames.isEmpty()) {
                        int index = medicineNames.indexOf(medName);
                        if (index >= 0) {
                            spinnerMedication.setSelection(index);
                        }
                    }
                    
                    etDosage.setText(String.valueOf(schedule.getDosagePerIntake()));
                    
                    int freqIndex = schedule.getFrequency() - 1;
                    if (freqIndex >= 0 && freqIndex < 4) {
                        spinnerFrequency.setSelection(freqIndex);
                    }
                    
                    // Wait for spinner to update pickers, then fill times
                    layoutTimePickers.post(() -> {
                        if (schedule.getScheduledTimes() != null && schedule.getScheduledTimes().size() == schedule.getFrequency()) {
                            selectedTimes = new ArrayList<>(schedule.getScheduledTimes());
                            // Refresh UI
                            layoutTimePickers.removeAllViews();
                            for (int i = 0; i < selectedTimes.size(); i++) {
                                addTimePickerRow(i);
                            }
                        }
                    });
                }
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(ConfigureScheduleActivity.this, "Error loading schedule", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveSchedule() {
        String name = "";
        if (spinnerMedication.getSelectedItem() != null) {
            name = spinnerMedication.getSelectedItem().toString();
        }
        
        if (name.equals("Add New Medicine...") || name.isEmpty()) {
            Toast.makeText(this, "Please select a valid medicine", Toast.LENGTH_SHORT).show();
            return;
        }

        String dosageStr = etDosage.getText().toString().trim();
        
        if (dosageStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int dosage = Integer.parseInt(dosageStr);
        int frequency = spinnerFrequency.getSelectedItemPosition() + 1;

        MedicationSchedule schedule = new MedicationSchedule(childId, name, dosage, frequency, selectedTimes);

        scheduleRepository.saveSchedule(childId, schedule, new ScheduleRepository.SaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ConfigureScheduleActivity.this, "Schedule saved!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(ConfigureScheduleActivity.this, "Error saving: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
