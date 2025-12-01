package com.example.b07project.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.b07project.R;
import com.example.b07project.models.Report;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Report> reports;
    private OnReportActionListener listener;
    private OnReportDeleteListener deleteListener;
    private SimpleDateFormat dateFormat;
    private boolean readOnly;

    public interface OnReportActionListener {
        void onShareReport(Report report);
        void onDownloadReport(Report report);
    }

    public interface OnReportDeleteListener {
        void onReportDelete(Report report, int position);
    }

    public ReportAdapter(List<Report> reports, OnReportActionListener listener, OnReportDeleteListener deleteListener) {
        this.reports = reports;
        this.listener = listener;
        this.deleteListener = deleteListener;
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reports.get(position);
        
        holder.tvReportTitle.setText("Usage report - " + report.getDays() + " days");
        holder.tvReportDate.setText("Generated on " + dateFormat.format(new Date(report.getGeneratedDate())));

        if (readOnly) {
            holder.btnShareReport.setVisibility(View.GONE);
            holder.btnDeleteReport.setVisibility(View.GONE);
            holder.btnShareReport.setOnClickListener(null);
            holder.btnDeleteReport.setOnClickListener(null);
        } else {
            holder.btnShareReport.setVisibility(View.VISIBLE);
            holder.btnDeleteReport.setVisibility(View.VISIBLE);
            holder.btnShareReport.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareReport(report);
                }
            });
            holder.btnDeleteReport.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onReportDelete(report, holder.getAdapterPosition());
                }
            });
        }

        holder.btnDownloadReport.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownloadReport(report);
            }
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    public void updateReports(List<Report> newReports) {
        this.reports = newReports;
        notifyDataSetChanged();
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView tvReportTitle;
        TextView tvReportDate;
        Button btnShareReport;
        Button btnDownloadReport;
        Button btnDeleteReport;

        ReportViewHolder(View itemView) {
            super(itemView);
            tvReportTitle = itemView.findViewById(R.id.tvReportTitle);
            tvReportDate = itemView.findViewById(R.id.tvReportDate);
            btnShareReport = itemView.findViewById(R.id.btnShareReport);
            btnDownloadReport = itemView.findViewById(R.id.btnDownloadReport);
            btnDeleteReport = itemView.findViewById(R.id.btnDeleteReport);
        }
    }
}
