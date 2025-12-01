package com.example.b07project;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.models.MedicineInventory;
import com.example.b07project.repository.InventoryRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView rvInventory;
    private FloatingActionButton fabAdd;
    private ProgressBar progressBar;
    private InventoryRepository repository;
    private InventoryAdapter adapter;
    private String userId;
    private Map<String, String> childrenMap = new HashMap<>(); // ID -> Name

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repository = new InventoryRepository(this);

        rvInventory = findViewById(R.id.rvInventory);
        fabAdd = findViewById(R.id.fabAdd);
        progressBar = findViewById(R.id.progressBar);

        rvInventory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryAdapter();
        rvInventory.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showMedicineDialog(null));

        fetchChildren();
        loadInventory();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed

        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack).setOnClickListener(v -> bh.backTo(this, ParentDashboardActivity.class));
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void fetchChildren() {
        FirebaseFirestore.getInstance().collection("children")
                .whereEqualTo("parentId", userId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    childrenMap.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        childrenMap.put(doc.getId(), doc.getString("name"));
                    }
                });
    }

    private void loadInventory() {
        progressBar.setVisibility(View.VISIBLE);
        repository.getMedicines(userId, new InventoryRepository.LoadCallback() {
            @Override
            public void onSuccess(List<MedicineInventory> medicines) {
                progressBar.setVisibility(View.GONE);
                adapter.setMedicines(medicines);
            }

            @Override
            public void onFailure(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(InventoryActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMedicineDialog(MedicineInventory medicine) {
        boolean isEditing = medicine != null;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_medicine, null);
        builder.setView(view);
        builder.setTitle(isEditing ? "Edit Medicine" : "Add Medicine");

        TextInputEditText etName = view.findViewById(R.id.etName);
        Spinner spChild = view.findViewById(R.id.spChild);
        Spinner spType = view.findViewById(R.id.spType);
        TextInputEditText etTotalDoses = view.findViewById(R.id.etTotalDoses);
        TextInputLayout tilRemainingDoses = view.findViewById(R.id.tilRemainingDoses);
        TextInputEditText etRemainingDoses = view.findViewById(R.id.etRemainingDoses);
        TextInputEditText etExpiry = view.findViewById(R.id.etExpiry);
        Button btnDelete = view.findViewById(R.id.btnDelete);
        
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

        // Pre-fill data if editing
        if (isEditing) {
            etName.setText(medicine.getName());
            etTotalDoses.setText(String.valueOf(medicine.getTotalDoses()));
            
            tilRemainingDoses.setVisibility(View.VISIBLE);
            etRemainingDoses.setText(String.valueOf(medicine.getRemainingDoses()));
            
            if (medicine.getExpiryDate() != null) {
                expiryDate[0] = medicine.getExpiryDate();
                calendar.setTime(expiryDate[0]);
                etExpiry.setText(sdf.format(expiryDate[0]));
            }
            
            // Set spinners
            if (medicine.getChildId() != null) {
                int index = childIds.indexOf(medicine.getChildId());
                if (index >= 0) spChild.setSelection(index);
            }
            
            if (medicine.getType() != null) {
                int index = typeAdapter.getPosition(medicine.getType());
                if (index >= 0) spType.setSelection(index);
            }
            
            // Setup Delete Button
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Delete Medicine")
                    .setMessage("Are you sure you want to delete " + medicine.getName() + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        repository.deleteMedicine(medicine.getId(), new InventoryRepository.SaveCallback() {
                            @Override
                            public void onSuccess() {
                                loadInventory();
                                dialog.dismiss();
                                Toast.makeText(InventoryActivity.this, "Medicine deleted", Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onFailure(String error) {
                                Toast.makeText(InventoryActivity.this, "Failed to delete: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, isEditing ? "Save" : "Add", (d, which) -> {
            // This listener is overridden below to prevent auto-dismiss on validation error
        });
        
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (d, which) -> dialog.dismiss());

        dialog.show();
        
        // Override positive button to handle validation
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
            int remainingDoses = totalDoses;
            
            if (isEditing) {
                String remainingStr = etRemainingDoses.getText().toString().trim();
                if (!remainingStr.isEmpty()) {
                    remainingDoses = Integer.parseInt(remainingStr);
                }
            }

            MedicineInventory newMedicine = isEditing ? medicine : new MedicineInventory();
            newMedicine.setUserId(userId);
            newMedicine.setName(name);
            newMedicine.setType(type);
            newMedicine.setTotalDoses(totalDoses);
            newMedicine.setRemainingDoses(remainingDoses);
            newMedicine.setExpiryDate(expiryDate[0]);
            newMedicine.setChildId(selectedChildId);
            newMedicine.setChildName(selectedChildName);
            if (!isEditing) newMedicine.setPurchaseDate(new Date());

            InventoryRepository.SaveCallback callback = new InventoryRepository.SaveCallback() {
                @Override
                public void onSuccess() {
                    loadInventory();
                    dialog.dismiss();
                    Toast.makeText(InventoryActivity.this, isEditing ? "Medicine updated" : "Medicine added", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(InventoryActivity.this, "Failed: " + error, Toast.LENGTH_SHORT).show();
                }
            };

            if (isEditing) {
                repository.updateMedicine(newMedicine, callback);
            } else {
                repository.addMedicine(newMedicine, callback);
            }
        });
    }

    private class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {
        private List<MedicineInventory> medicines = new ArrayList<>();
        private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

        public void setMedicines(List<MedicineInventory> medicines) {
            this.medicines = medicines;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MedicineInventory med = medicines.get(position);
            String owner = med.getChildName() != null ? med.getChildName() : "Parent";
            holder.tvName.setText(med.getName() + " (" + owner + ")");
            holder.tvType.setText(med.getType());
            holder.tvExpiry.setText("Exp: " + sdf.format(med.getExpiryDate()));
            
            holder.pbDoses.setMax(med.getTotalDoses());
            holder.pbDoses.setProgress(med.getRemainingDoses());
            holder.tvDosesLeft.setText(med.getRemainingDoses() + "/" + med.getTotalDoses() + " doses left");

            if (med.isLow()) {
                holder.tvAlert.setVisibility(View.VISIBLE);
                holder.tvAlert.setText("LOW!");
            } else if (med.isExpired()) {
                holder.tvAlert.setVisibility(View.VISIBLE);
                holder.tvAlert.setText("EXPIRED!");
            } else {
                holder.tvAlert.setVisibility(View.GONE);
            }
            
            holder.itemView.setOnClickListener(v -> showMedicineDialog(med));
        }

        @Override
        public int getItemCount() {
            return medicines.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvType, tvExpiry, tvDosesLeft, tvAlert;
            ProgressBar pbDoses;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvType = itemView.findViewById(R.id.tvType);
                tvExpiry = itemView.findViewById(R.id.tvExpiry);
                tvDosesLeft = itemView.findViewById(R.id.tvDosesLeft);
                tvAlert = itemView.findViewById(R.id.tvAlert);
                pbDoses = itemView.findViewById(R.id.pbDoses);
            }
        }
    }
}
