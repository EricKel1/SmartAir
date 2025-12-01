package com.example.b07project.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.b07project.R;

import java.util.List;
import java.util.Map;

public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {

    private final List<Map<String, Object>> patients;
    private final OnPatientClickListener listener;

    public interface OnPatientClickListener {
        void onPatientClick(Map<String, Object> patient);
    }

    public PatientAdapter(List<Map<String, Object>> patients, OnPatientClickListener listener) {
        this.patients = patients;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_patient, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        Map<String, Object> patient = patients.get(position);

        String name = (String) patient.get("name");
        String dob  = (String) patient.get("dob");

        holder.tvChildName.setText(name != null ? name : "Unknown");
            String dobText = (dob != null && !dob.trim().isEmpty()) ? "DOB: " + dob.trim() : "DOB: N/A";
            holder.tvChildDob.setText(dobText);
        holder.tvChildSubtitle.setText("Tap to view shared information");

        boolean safetyEnabled = Boolean.TRUE.equals(patient.get("safetyMonitoringEnabled"));

        //  PEF circle
        boolean hasPEFData = Boolean.TRUE.equals(patient.get("hasPEFData"));
        if (safetyEnabled && hasPEFData) {
            holder.layoutPefContainer.setVisibility(View.VISIBLE);

            // value + zone
            Long pefValueObj = patient.get("pefValue") instanceof Long
                    ? (Long) patient.get("pefValue")
                    : patient.get("pefValue") instanceof Integer
                    ? ((Integer) patient.get("pefValue")).longValue()
                    : null;

            String zone = (String) patient.get("pefZone");

            String valueText = pefValueObj != null
                    ? pefValueObj + " L/min"
                    : "-- L/min";
            holder.tvPefValue.setText(valueText);

            if (zone != null) {
                String lower = zone.toLowerCase();
                if (lower.equals("green")) {
                    holder.flPefCircle.setBackgroundResource(R.drawable.bg_pef_green);
                } else if (lower.equals("yellow")) {
                    holder.flPefCircle.setBackgroundResource(R.drawable.bg_pef_yellow);
                } else if (lower.equals("red")) {
                    holder.flPefCircle.setBackgroundResource(R.drawable.bg_pef_red);
                } else {
                    holder.flPefCircle.setBackgroundResource(R.drawable.bg_pef_green);
                }
            }
        } else {
            holder.layoutPefContainer.setVisibility(View.GONE);
        }

        //  triage badge
        boolean hasRecentTriage = Boolean.TRUE.equals(patient.get("hasRecentTriage"));
        if (safetyEnabled && hasRecentTriage) {
            holder.tvTriageBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvTriageBadge.setVisibility(View.GONE);
        }

        // click on whole card
        holder.itemView.setOnClickListener(v -> listener.onPatientClick(patient));
    }

    @Override
    public int getItemCount() {
        return patients.size();
    }

    static class PatientViewHolder extends RecyclerView.ViewHolder {

        TextView tvChildName;
        TextView tvChildDob;
        TextView tvChildSubtitle;
        TextView tvTriageBadge;
        View     layoutPefContainer;
        FrameLayout flPefCircle;
        TextView tvPefValue;

        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);

            tvChildName       = itemView.findViewById(R.id.tvChildName);
            tvChildDob        = itemView.findViewById(R.id.tvChildDob);
            tvChildSubtitle   = itemView.findViewById(R.id.tvChildSubtitle);
            tvTriageBadge     = itemView.findViewById(R.id.tvTriageBadge);
            layoutPefContainer= itemView.findViewById(R.id.layoutPefContainer);
            flPefCircle       = itemView.findViewById(R.id.flPefCircle);
            tvPefValue        = itemView.findViewById(R.id.tvPefValue);
        }
    }
}
