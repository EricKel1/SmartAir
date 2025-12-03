package com.example.b07project.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.b07project.MainActivity;
import com.example.b07project.R;
import com.example.b07project.models.AppNotification;
import com.example.b07project.repository.NotificationRepository;

import com.google.firebase.firestore.FirebaseFirestore;

public class NotificationHelper {
    private static final String CHANNEL_ID = "smart_air_alerts";
    private static final String CHANNEL_NAME = "Smart Air Alerts";
    private static final String CHANNEL_DESC = "Alerts for low medicine and expiry";

    public static void sendAlert(Context context, String userId, String title, String message) {
        android.util.Log.d("NotificationDebug", "sendAlert START. userId=" + userId + ", title=" + title);

        // 1. Send System Notification (Local) - Enabled to ensure immediate feedback
        showLocalNotification(context, title, message);
        
        // 2. Save to Firestore
        // We check the 'users' collection FIRST because it's the most reliable source 
        // for the user's own profile (and their parentId).
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
            android.util.Log.d("NotificationDebug", "Users collection lookup success. Exists: " + doc.exists());
            if (doc.exists()) {
                String parentId = doc.getString("parentId");
                android.util.Log.d("NotificationDebug", "Found parentId in users: " + parentId);
                if (parentId != null && !parentId.isEmpty()) {
                    // This is a child user in users collection with a parentId - send to parent
                    saveToFirestore(parentId, title, message);
                } else {
                    // This is a parent user (no parentId) - check children collection in case 
                    // the userId is actually a child document ID
                    checkChildrenCollection(userId, title, message);
                }
            } else {
                // User not found in users collection - check children collection
                android.util.Log.d("NotificationDebug", "User not in users collection, checking children...");
                checkChildrenCollection(userId, title, message);
            }
        }).addOnFailureListener(e -> {
            android.util.Log.w("NotificationDebug", "Users collection lookup failed: " + e.getMessage());
            // Fallback to children collection
            checkChildrenCollection(userId, title, message);
        });
    }

    private static void checkChildrenCollection(String userId, String title, String message) {
        FirebaseFirestore.getInstance().collection("children").document(userId).get().addOnSuccessListener(doc -> {
            String targetId = userId;
            if (doc.exists()) {
                String parentId = doc.getString("parentId");
                if (parentId != null && !parentId.isEmpty()) {
                    targetId = parentId;
                }
            }
            saveToFirestore(targetId, title, message);
        }).addOnFailureListener(e -> {
            android.util.Log.w("NotificationDebug", "Children collection lookup failed: " + e.getMessage());
            // If both fail, just save to the user ID
            saveToFirestore(userId, title, message);
        });
    }

    private static void saveToFirestore(String targetId, String title, String message) {
        android.util.Log.d("NotificationDebug", "FINAL SAVE: Saving notification for targetId=" + targetId);
        NotificationRepository repo = new NotificationRepository();
        repo.saveNotification(new AppNotification(targetId, title, message));
        
        // Note: The actual push notification will be sent by a Firebase Cloud Function
        // that listens to the 'notifications' collection. This is required for FCM V1 API.
    }

    public static void showLocalNotification(Context context, String title, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(CHANNEL_DESC);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
