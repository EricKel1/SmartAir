package com.example.b07project.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.R;
import com.example.b07project.adapters.ReportAdapter;
import com.example.b07project.models.Report;
import com.example.b07project.utils.ReportGenerator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class ReportsFragment extends Fragment {

    private RecyclerView rvReports;
    private View tvEmptyState;
    private FloatingActionButton fabCreateReport;
    private ReportAdapter reportAdapter;
    private FirebaseFirestore db;
    private String userId;
    private boolean readOnly;

    public static ReportsFragment newInstance(String userId, boolean readOnly) {
        ReportsFragment fragment = new ReportsFragment();
        Bundle args = new Bundle();
        args.putString("userId", userId);
        args.putBoolean("readOnly", readOnly);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);

        rvReports = view.findViewById(R.id.rvReports);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        fabCreateReport = view.findViewById(R.id.fabCreateReport);

        db = FirebaseFirestore.getInstance();
        
        if (getArguments() != null && getArguments().getString("userId") != null) {
            userId = getArguments().getString("userId");
        } else {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        if (getArguments() != null) {
            readOnly = getArguments().getBoolean("readOnly", false);
        }

        setupRecyclerView();
        setupListeners();
        if (readOnly) {
            fabCreateReport.setVisibility(View.GONE);
        }
        loadReports();

        return view;
    }

    private void setupRecyclerView() {
        reportAdapter = new ReportAdapter(new ArrayList<>(), new ReportAdapter.OnReportActionListener() {
            @Override
            public void onShareReport(Report report) {
                shareReport(report);
            }

            @Override
            public void onDownloadReport(Report report) {
                downloadReport(report);
            }
        }, this::confirmDeleteReport);
        // Providers are read-only: remove generate/share/delete actions
        reportAdapter.setReadOnly(readOnly);
        rvReports.setLayoutManager(new LinearLayoutManager(getContext()));
        rvReports.setAdapter(reportAdapter);
    }

    private void setupListeners() {
        fabCreateReport.setOnClickListener(v -> {
            if (!readOnly) showCreateReportDialog();
        });
    }

    private void showCreateReportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_report, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }

        Spinner spinner = dialogView.findViewById(R.id.spinnerTimePeriod);
        LinearLayout layoutCustomDates = dialogView.findViewById(R.id.layoutCustomDates);
        android.widget.Button btnStartDate = dialogView.findViewById(R.id.btnStartDate);
        android.widget.Button btnEndDate = dialogView.findViewById(R.id.btnEndDate);
        CheckBox cbRescue = dialogView.findViewById(R.id.cbRescue);
        CheckBox cbController = dialogView.findViewById(R.id.cbController);
        CheckBox cbSymptoms = dialogView.findViewById(R.id.cbSymptoms);
        CheckBox cbDailyLogs = dialogView.findViewById(R.id.cbDailyLogs);
        CheckBox cbTriggerChart = dialogView.findViewById(R.id.cbTriggerChart);
        CheckBox cbZones = dialogView.findViewById(R.id.cbZones);
        CheckBox cbTriage = dialogView.findViewById(R.id.cbTriage);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        android.widget.Button btnGenerate = dialogView.findViewById(R.id.btnGenerate);

        cbDailyLogs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cbTriggerChart.setEnabled(isChecked);
            if (!isChecked) cbTriggerChart.setChecked(false);
        });

        String[] options = {"7 days", "30 days", "90 days (3 months)", "180 days (6 months)", "Custom Range"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        final java.util.Calendar startCal = java.util.Calendar.getInstance();
        startCal.add(java.util.Calendar.DAY_OF_YEAR, -7);
        final java.util.Calendar endCal = java.util.Calendar.getInstance();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault());
        btnStartDate.setText(sdf.format(startCal.getTime()));
        btnEndDate.setText(sdf.format(endCal.getTime()));

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 4) { // Custom Range
                    layoutCustomDates.setVisibility(View.VISIBLE);
                } else {
                    layoutCustomDates.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnStartDate.setOnClickListener(v -> {
            new android.app.DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                startCal.set(year, month, dayOfMonth);
                btnStartDate.setText(sdf.format(startCal.getTime()));
            }, startCal.get(java.util.Calendar.YEAR), startCal.get(java.util.Calendar.MONTH), startCal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        btnEndDate.setOnClickListener(v -> {
            new android.app.DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                endCal.set(year, month, dayOfMonth);
                btnEndDate.setText(sdf.format(endCal.getTime()));
            }, endCal.get(java.util.Calendar.YEAR), endCal.get(java.util.Calendar.MONTH), endCal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnGenerate.setOnClickListener(v -> {
            int position = spinner.getSelectedItemPosition();
            if (position == 4) {
                if (startCal.getTimeInMillis() > endCal.getTimeInMillis()) {
                    Toast.makeText(getContext(), "Start date must be before end date", Toast.LENGTH_SHORT).show();
                    return;
                }
                createReport(startCal.getTime(), endCal.getTime(), cbTriage.isChecked(), cbRescue.isChecked(), cbController.isChecked(), cbSymptoms.isChecked(), cbZones.isChecked(), cbDailyLogs.isChecked(), cbTriggerChart.isChecked());
            } else {
                int days = 7;
                switch (position) {
                    case 0: days = 7; break;
                    case 1: days = 30; break;
                    case 2: days = 90; break;
                    case 3: days = 180; break;
                }
                createReport(days, cbTriage.isChecked(), cbRescue.isChecked(), cbController.isChecked(), cbSymptoms.isChecked(), cbZones.isChecked(), cbDailyLogs.isChecked(), cbTriggerChart.isChecked());
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createReport(java.util.Date startDate, java.util.Date endDate, boolean includeTriage, boolean includeRescue, boolean includeController, boolean includeSymptoms, boolean includeZones, boolean includeDailyLogs, boolean includeTriggerChart) {
        long diff = endDate.getTime() - startDate.getTime();
        int days = (int) (diff / (24 * 60 * 60 * 1000)) + 1;
        Toast.makeText(requireContext(), "Generating " + days + "-day report...", Toast.LENGTH_SHORT).show();

        ReportGenerator generator = new ReportGenerator(requireContext());
        generator.generateReport(userId, startDate, endDate, includeTriage, includeRescue, includeController, includeSymptoms, includeZones, includeDailyLogs, includeTriggerChart, new ReportGenerator.ReportCallback() {
            @Override
            public void onSuccess(Report report) {
                saveReportToFirestore(report);
                Toast.makeText(requireContext(), "Report generated successfully!", Toast.LENGTH_SHORT).show();
                loadReports();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(requireContext(), "Failed to generate report: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createReport(int days, boolean includeTriage, boolean includeRescue, boolean includeController, boolean includeSymptoms, boolean includeZones, boolean includeDailyLogs, boolean includeTriggerChart) {
        Toast.makeText(requireContext(), "Generating " + days + "-day report...", Toast.LENGTH_SHORT).show();
        
        ReportGenerator generator = new ReportGenerator(requireContext());
        generator.generateReport(userId, days, includeTriage, includeRescue, includeController, includeSymptoms, includeZones, includeDailyLogs, includeTriggerChart, new ReportGenerator.ReportCallback() {
            @Override
            public void onSuccess(Report report) {
                saveReportToFirestore(report);
                Toast.makeText(requireContext(), "Report generated successfully!", Toast.LENGTH_SHORT).show();
                loadReports();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(requireContext(), "Failed to generate report: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveReportToFirestore(Report report) {
        db.collection("reports")
                .add(report)
                .addOnSuccessListener(documentReference -> {
                    // Report saved successfully
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to save report", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadReports() {
        android.util.Log.d("RescueServiceChart", "Loading reports for userId: " + userId);
        
        db.collection("reports")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    android.util.Log.d("RescueServiceChart", "Reports query SUCCESS: Found " + queryDocumentSnapshots.size() + " reports");
                    List<Report> reports = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Report report = document.toObject(Report.class);
                        report.setId(document.getId());
                        reports.add(report);
                        android.util.Log.d("RescueServiceChart", "  - Report: " + report.getDays() + " days, generated: " + report.getGeneratedDate());
                    }

                    if (reports.isEmpty()) {
                        android.util.Log.d("RescueServiceChart", "No reports found, showing empty state");
                        tvEmptyState.setVisibility(View.VISIBLE);
                        rvReports.setVisibility(View.GONE);
                    } else {
                        // Sort by generated date descending (newest first)
                        reports.sort((r1, r2) -> Long.compare(r2.getGeneratedDate(), r1.getGeneratedDate()));
                        
                        android.util.Log.d("RescueServiceChart", "Displaying " + reports.size() + " reports");
                        tvEmptyState.setVisibility(View.GONE);
                        rvReports.setVisibility(View.VISIBLE);
                        reportAdapter.updateReports(reports);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("RescueServiceChart", "Reports query FAILED: " + e.getMessage(), e);
                    Toast.makeText(getContext(), "Failed to load reports: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void shareReport(Report report) {
        ReportGenerator generator = new ReportGenerator(getContext());
        generator.shareReport(report);
    }

    private void downloadReport(Report report) {
        ReportGenerator generator = new ReportGenerator(getContext());
        generator.downloadReport(report);
    }

    private void confirmDeleteReport(Report report, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_delete_report, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }

        android.widget.Button btnCancel = dialogView.findViewById(R.id.btnCancelDelete);
        android.widget.Button btnConfirm = dialogView.findViewById(R.id.btnConfirmDelete);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            deleteReport(report, position);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void deleteReport(Report report, int position) {
        db.collection("reports")
                .document(report.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Report deleted successfully", Toast.LENGTH_SHORT).show();
                    loadReports(); // Refresh the list
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to delete report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
