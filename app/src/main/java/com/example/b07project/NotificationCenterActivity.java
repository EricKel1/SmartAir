package com.example.b07project;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.b07project.main.WelcomeActivity;
import com.example.b07project.models.AppNotification;
import com.example.b07project.repository.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.snackbar.Snackbar;

public class NotificationCenterActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private NotificationRepository repository;
    private NotificationAdapter adapter;
    BackToParent bh = new BackToParent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        repository = new NotificationRepository();
        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvEmpty);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        rvNotifications.setAdapter(adapter);

        findViewById(R.id.btnBack2).setOnClickListener(v -> finish());

        setupSwipeToDelete();
        loadNotifications();
        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTop();
    }

    private void setupSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                AppNotification notification = adapter.getNotification(position);
                
                // Optimistically remove from UI
                adapter.removeItem(position);
                if (adapter.getItemCount() == 0) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvNotifications.setVisibility(View.GONE);
                }

                repository.deleteNotification(notification.getId(), new NotificationRepository.DeleteCallback() {
                    @Override
                    public void onSuccess() {
                        Snackbar.make(rvNotifications, "Notification deleted", Snackbar.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String error) {
                        // Re-add item if deletion fails
                        adapter.addItem(position, notification);
                        tvEmpty.setVisibility(View.GONE);
                        rvNotifications.setVisibility(View.VISIBLE);
                        Toast.makeText(NotificationCenterActivity.this, "Failed to delete: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).attachToRecyclerView(rvNotifications);
    }

    private void loadNotifications() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repository.getNotifications(userId, new NotificationRepository.LoadCallback() {
            @Override
            public void onSuccess(List<AppNotification> notifications) {
                if (notifications.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvNotifications.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvNotifications.setVisibility(View.VISIBLE);
                    adapter.setNotifications(notifications);
                }
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(NotificationCenterActivity.this, "Error loading notifications: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
        private List<AppNotification> notifications = new ArrayList<>();
        private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

        public void setNotifications(List<AppNotification> notifications) {
            this.notifications = notifications;
            notifyDataSetChanged();
        }

        public AppNotification getNotification(int position) {
            return notifications.get(position);
        }

        public void removeItem(int position) {
            notifications.remove(position);
            notifyItemRemoved(position);
        }

        public void addItem(int position, AppNotification notification) {
            notifications.add(position, notification);
            notifyItemInserted(position);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppNotification notif = notifications.get(position);
            holder.tvTitle.setText(notif.getTitle());
            holder.tvMessage.setText(notif.getMessage());
            holder.tvDate.setText(sdf.format(notif.getTimestamp()));
            
            // Mark as read if not already
            if (!notif.isRead()) {
                repository.markAsRead(notif.getId());
            }
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMessage, tvDate;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvDate = itemView.findViewById(R.id.tvDate);
            }
        }
    }
}
