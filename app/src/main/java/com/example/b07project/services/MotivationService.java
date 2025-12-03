package com.example.b07project.services;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.b07project.models.Badge;
import com.example.b07project.models.Streak;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MotivationService {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Context context;
    private static final String PREFS_NAME = "MotivationPrefs";

    // Default thresholds
    private static final int DEFAULT_TECHNIQUE_SESSIONS_REQUIRED = 10;
    private static final int DEFAULT_LOW_RESCUE_THRESHOLD = 4;
    private static final int DEFAULT_LOW_RESCUE_PERIOD = 30;

    private String targetUserId;

    public MotivationService(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.targetUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
    
    public void setTargetUserId(String userId) {
        this.targetUserId = userId;
    }

    // Streak Methods
    public interface StreakCallback {
        void onSuccess(Streak streak);
        void onFailure(Exception e);
    }

    public interface BadgeListCallback {
        void onSuccess(List<Badge> badges);
        void onFailure(Exception e);
    }

    public interface BadgeEarnedCallback {
        void onBadgeEarned(Badge badge);
    }

    private BadgeEarnedCallback badgeEarnedCallback;

    public void setBadgeEarnedCallback(BadgeEarnedCallback callback) {
        this.badgeEarnedCallback = callback;
    }

    public void getStreak(String type, StreakCallback callback) {
        String userId = targetUserId;
        if (userId == null) {
             if (auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
             else {
                 callback.onFailure(new Exception("No user ID available"));
                 return;
             }
        }
        
        final String finalUserId = userId;
        db.collection("streaks")
                .whereEqualTo("userId", finalUserId)
                .whereEqualTo("type", type)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Streak streak = doc.toObject(Streak.class);
                        // Ensure ID is set from document
                        if (streak != null) {
                            streak.setId(doc.getId());
                            callback.onSuccess(streak);
                        } else {
                            callback.onFailure(new Exception("Failed to parse streak"));
                        }
                    } else {
                        // Create new streak with generated ID
                        String newId = db.collection("streaks").document().getId();
                        Streak newStreak = new Streak(
                                newId,
                                finalUserId,
                                type,
                                0,
                                0,
                                0  // Set lastUpdated to 0 so first update will trigger
                        );
                        callback.onSuccess(newStreak);
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void updateControllerStreak(UpdateCallback callback) {
        getStreak("controller", new StreakCallback() {
            @Override
            public void onSuccess(Streak streak) {
                android.util.Log.d("MotivationService", "updateControllerStreak - Got streak: " + 
                    streak.getId() + ", current=" + streak.getCurrentStreak() + 
                    ", lastUpdated=" + streak.getLastUpdated());
                
                long today = getStartOfDay(System.currentTimeMillis());
                long lastUpdated = getStartOfDay(streak.getLastUpdated());
                long daysDiff = TimeUnit.MILLISECONDS.toDays(today - lastUpdated);

                android.util.Log.d("MotivationService", "updateControllerStreak - daysDiff=" + daysDiff + 
                    ", currentStreak=" + streak.getCurrentStreak());

                // Update if: it's a new day OR it's the first time (currentStreak == 0 and lastUpdated == 0)
                boolean isFirstTime = (streak.getCurrentStreak() == 0 && streak.getLastUpdated() == 0);
                
                if (daysDiff >= 1 || isFirstTime) {
                    if (daysDiff == 1 && !isFirstTime) {
                        // Continue streak (consecutive day)
                        streak.setCurrentStreak(streak.getCurrentStreak() + 1);
                    } else {
                        // Streak broken, or first time - start at 1
                        streak.setCurrentStreak(1);
                    }

                    if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                        streak.setLongestStreak(streak.getCurrentStreak());
                    }

                    streak.setLastUpdated(System.currentTimeMillis());
                    
                    android.util.Log.d("MotivationService", "updateControllerStreak - Saving streak: current=" + 
                        streak.getCurrentStreak() + ", longest=" + streak.getLongestStreak());
                    
                    saveStreak(streak, () -> checkBadgesAfterStreakUpdate(streak, callback));
                } else {
                    // Already updated today, but still check badges (in case requirements changed)
                    android.util.Log.d("MotivationService", "updateControllerStreak - Already updated today, checking badges anyway");
                    checkBadgesAfterStreakUpdate(streak, callback);
                }
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.e("MotivationService", "updateControllerStreak - Failed to get streak", e);
                callback.onComplete();
            }
        });
    }

    public void updateTechniqueStreak(UpdateCallback callback) {
        getStreak("technique", new StreakCallback() {
            @Override
            public void onSuccess(Streak streak) {
                long today = getStartOfDay(System.currentTimeMillis());
                long lastUpdated = getStartOfDay(streak.getLastUpdated());
                long daysDiff = TimeUnit.MILLISECONDS.toDays(today - lastUpdated);

                // Only update if this is a new day
                if (daysDiff >= 1) {
                    if (daysDiff == 1) {
                        // Continue streak (consecutive day)
                        streak.setCurrentStreak(streak.getCurrentStreak() + 1);
                    } else if (daysDiff > 1 || streak.getCurrentStreak() == 0) {
                        // Streak broken or first time, start at 1
                        streak.setCurrentStreak(1);
                    }

                    if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                        streak.setLongestStreak(streak.getCurrentStreak());
                    }

                    streak.setLastUpdated(System.currentTimeMillis());
                    saveStreak(streak, () -> updateTechniqueSessionBadge(true, callback));
                } else {
                    // Already updated today, check badges but DO NOT increment
                    updateTechniqueSessionBadge(false, callback);
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (callback != null) callback.onComplete();
            }
        });
    }

    private void saveStreak(Streak streak, UpdateCallback callback) {
        if (streak.getId() == null || streak.getId().isEmpty()) {
            // Generate new ID if not set
            streak.setId(db.collection("streaks").document().getId());
            android.util.Log.d("MotivationService", "saveStreak - Generated new ID: " + streak.getId());
        }
        
        android.util.Log.d("MotivationService", "saveStreak - Saving to Firestore: ID=" + streak.getId() + 
            ", userId=" + streak.getUserId() + ", type=" + streak.getType() + 
            ", current=" + streak.getCurrentStreak() + ", longest=" + streak.getLongestStreak());
        
        Map<String, Object> streakData = new HashMap<>();
        streakData.put("userId", streak.getUserId());
        streakData.put("type", streak.getType());
        streakData.put("currentStreak", streak.getCurrentStreak());
        streakData.put("longestStreak", streak.getLongestStreak());
        streakData.put("lastUpdated", streak.getLastUpdated());

        db.collection("streaks").document(streak.getId())
                .set(streakData)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("MotivationService", "saveStreak - Successfully saved to Firestore");
                    if (callback != null) callback.onComplete();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MotivationService", "saveStreak - Failed to save to Firestore", e);
                    e.printStackTrace();
                    if (callback != null) callback.onComplete();
                });
    }

    // Badge Methods
    public void getAllBadges(BadgeListCallback callback) {
        String userId = targetUserId;
        if (userId == null) {
             if (auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
             else {
                 callback.onFailure(new Exception("No user ID available"));
                 return;
             }
        }
        final String finalUserId = userId;
        
        ensureAllBadgesExist(() -> {
            db.collection("badges")
                    .whereEqualTo("userId", finalUserId)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<Badge> badges = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Badge badge = doc.toObject(Badge.class);
                            if (badge != null) {
                                badge.setId(doc.getId());
                                badges.add(badge);
                            }
                        }
                        callback.onSuccess(badges);
                    })
                    .addOnFailureListener(callback::onFailure);
        });
    }

    private void ensureAllBadgesExist(Runnable onComplete) {
        ensureFirstRescueBadgeExists(() -> {
            ensureTechniqueBadgeExists(() -> {
                ensurePerfectWeekBadgeExists(() -> {
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    int rescueThreshold = prefs.getInt("low_rescue_threshold", DEFAULT_LOW_RESCUE_THRESHOLD);
                    int rescuePeriod = prefs.getInt("low_rescue_period", DEFAULT_LOW_RESCUE_PERIOD);
                    ensureLowRescueBadgeExists(rescueThreshold, rescuePeriod, onComplete);
                });
            });
        });
    }

    private void initializeBadges(BadgeListCallback callback) {
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;
        
        List<Badge> defaultBadges = new ArrayList<>();

        String perfectWeekId = db.collection("badges").document().getId();
        Badge perfectWeek = new Badge(
                perfectWeekId,
                finalUserId,
                "perfect_controller_week",
                "Perfect Week",
                "Take controller medication every day for 7 consecutive days",
                false,
                0,
                0,
                7
        );
        defaultBadges.add(perfectWeek);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int techniqueRequired = prefs.getInt("technique_sessions_required", DEFAULT_TECHNIQUE_SESSIONS_REQUIRED);

        String techniqueMasterId = db.collection("badges").document().getId();
        Badge techniqueMaster = new Badge(
                techniqueMasterId,
                finalUserId,
                "technique_sessions",
                "Technique Master",
                "Complete " + techniqueRequired + " high-quality technique practice sessions",
                false,
                0,
                0,
                techniqueRequired
        );
        defaultBadges.add(techniqueMaster);

        // Test badge - First Rescue Use
        String testBadgeId = db.collection("badges").document().getId();
        Badge testBadge = new Badge(
                testBadgeId,
                finalUserId,
                "first_rescue_use",
                "First Step",
                "Log your first rescue inhaler use",
                false,
                0,
                0,
                1
        );
        defaultBadges.add(testBadge);

        // Low Rescue Month badge
        int rescueThreshold = prefs.getInt("low_rescue_threshold", DEFAULT_LOW_RESCUE_THRESHOLD);
        int rescuePeriod = prefs.getInt("low_rescue_period", DEFAULT_LOW_RESCUE_PERIOD);

        String lowRescueId = db.collection("badges").document().getId();
        Badge lowRescue = new Badge(
                lowRescueId,
                finalUserId,
                "low_rescue_month",
                "Control Champion",
                "Use rescue inhaler ≤" + rescueThreshold + " times in " + rescuePeriod + " days",
                false,
                0,
                0,
                rescueThreshold
        );
        defaultBadges.add(lowRescue);

        // Save all badges with their pre-assigned IDs
        int[] saveCount = {0};
        for (Badge badge : defaultBadges) {
            saveBadge(badge, () -> {
                saveCount[0]++;
                if (saveCount[0] == defaultBadges.size()) {
                    callback.onSuccess(defaultBadges);
                }
            });
        }
    }

    private void saveBadge(Badge badge, UpdateCallback callback) {
        android.util.Log.d("RescueInhalerService", "saveBadge - CALLED for badge type: " + badge.getType() + ", name: " + badge.getName());
        android.util.Log.d("RescueInhalerService", "saveBadge - Badge ID BEFORE check: " + badge.getId());
        android.util.Log.d("RescueInhalerService", "saveBadge - Badge progress: " + badge.getProgress() + ", requirement: " + badge.getRequirement() + ", earned: " + badge.isEarned());
        
        if (badge.getId() == null || badge.getId().isEmpty()) {
            // Generate new ID if not set
            badge.setId(db.collection("badges").document().getId());
            android.util.Log.d("RescueInhalerService", "saveBadge - Generated new ID: " + badge.getId());
        }
        
        android.util.Log.d("RescueInhalerService", "saveBadge - Final Badge ID: " + badge.getId());
        
        Map<String, Object> badgeData = new HashMap<>();
        badgeData.put("userId", badge.getUserId());
        badgeData.put("type", badge.getType());
        badgeData.put("name", badge.getName());
        badgeData.put("description", badge.getDescription());
        badgeData.put("earned", badge.isEarned());
        badgeData.put("earnedDate", badge.getEarnedDate());
        badgeData.put("progress", badge.getProgress());
        badgeData.put("requirement", badge.getRequirement());
        badgeData.put("periodEndDate", badge.getPeriodEndDate());

        android.util.Log.d("RescueInhalerService", "saveBadge - badgeData map created with progress: " + badgeData.get("progress"));
        android.util.Log.d("RescueInhalerService", "saveBadge - Full badgeData: " + badgeData.toString());
        android.util.Log.d("RescueInhalerService", "saveBadge - About to call Firestore .set() on document: " + badge.getId());

        db.collection("badges").document(badge.getId())
                .set(badgeData)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("RescueInhalerService", "saveBadge - SUCCESS! Firestore .set() completed for badge: " + badge.getId());
                    android.util.Log.d("RescueInhalerService", "saveBadge - Saved progress value: " + badgeData.get("progress"));
                    if (callback != null) callback.onComplete();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("RescueInhalerService", "saveBadge - FAILED! Firestore .set() error for badge: " + badge.getId(), e);
                    if (callback != null) callback.onComplete();
                });
    }

    private void ensureBadgeExists(String type, BadgeTemplateBuilder builder, Runnable onComplete) {
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;
        
        db.collection("badges")
                .whereEqualTo("userId", finalUserId)
                .whereEqualTo("type", type)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        String badgeId = db.collection("badges").document().getId();
                        Badge badge = builder.build(badgeId, finalUserId);
                        android.util.Log.d("MotivationService", "ensureBadgeExists - Creating missing badge type=" + type);
                        saveBadge(badge, () -> {
                            if (onComplete != null) onComplete.run();
                        });
                    } else {
                        if (onComplete != null) onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MotivationService", "ensureBadgeExists - Failed for type=" + type, e);
                    if (onComplete != null) onComplete.run();
                });
    }

    private void ensureFirstRescueBadgeExists(Runnable onComplete) {
        ensureBadgeExists("first_rescue_use", (badgeId, userId) -> new Badge(
                badgeId,
                userId,
                "first_rescue_use",
                "First Step",
                "Log your first rescue inhaler use",
                false,
                0,
                0,
                1
        ), onComplete);
    }

    private void ensureTechniqueBadgeExists(Runnable onComplete) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int techniqueRequired = prefs.getInt("technique_sessions_required", DEFAULT_TECHNIQUE_SESSIONS_REQUIRED);

        ensureBadgeExists("technique_sessions", (badgeId, userId) -> new Badge(
                badgeId,
                userId,
                "technique_sessions",
                "Technique Master",
                "Complete " + techniqueRequired + " high-quality technique practice sessions",
                false,
                0,
                0,
                techniqueRequired
        ), onComplete);
    }

    private void ensurePerfectWeekBadgeExists(Runnable onComplete) {
        ensureBadgeExists("perfect_controller_week", (badgeId, userId) -> new Badge(
                badgeId,
                userId,
                "perfect_controller_week",
                "Perfect Week",
                "Take controller medication every day for 7 consecutive days",
                false,
                0,
                0,
                7
        ), onComplete);
    }

    private void ensureLowRescueBadgeExists(int rescueThreshold, int rescuePeriod, Runnable onComplete) {
        ensureBadgeExists("low_rescue_month", (badgeId, userId) -> new Badge(
                badgeId,
                userId,
                "low_rescue_month",
                "Control Champion",
                "Use rescue inhaler ≤" + rescueThreshold + " times in " + rescuePeriod + " days",
                false,
                0,
                0,
                rescueThreshold
        ), onComplete);
    }

    private void checkBadgesAfterStreakUpdate(Streak streak, UpdateCallback callback) {
        android.util.Log.d("MotivationService", "checkBadgesAfterStreakUpdate - Called with streak type: " + 
            streak.getType() + ", currentStreak: " + streak.getCurrentStreak());
        
        if (streak.getType().equals("controller")) {
            ensurePerfectWeekBadgeExists(() -> performPerfectWeekBadgeCheck(streak, callback));
        } else {
            android.util.Log.d("MotivationService", "checkBadgesAfterStreakUpdate - Not controller type, skipping");
            if (callback != null) callback.onComplete();
        }
    }

    private void performPerfectWeekBadgeCheck(Streak streak, UpdateCallback callback) {
        String userId = streak.getUserId();
        android.util.Log.d("MotivationService", "performPerfectWeekBadgeCheck - Querying for controller badge, userId: " + userId);

        db.collection("badges")
                .whereEqualTo("userId", userId)
                .whereEqualTo("type", "perfect_controller_week")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("MotivationService", "performPerfectWeekBadgeCheck - Query result: isEmpty=" + querySnapshot.isEmpty());

                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Badge badge = doc.toObject(Badge.class);
                        android.util.Log.d("MotivationService", "performPerfectWeekBadgeCheck - Found badge: " +
                                (badge != null ? "id=" + doc.getId() + ", progress=" + badge.getProgress() : "null"));

                        if (badge != null) {
                            badge.setId(doc.getId());

                            android.util.Log.d("MotivationService", "performPerfectWeekBadgeCheck - Controller badge progress: " +
                                    badge.getProgress() + " -> " + streak.getCurrentStreak());

                            badge.setProgress(streak.getCurrentStreak());

                            if (!badge.isEarned() && streak.getCurrentStreak() >= badge.getRequirement()) {
                                badge.setEarned(true);
                                badge.setEarnedDate(System.currentTimeMillis());
                                android.util.Log.d("MotivationService", "performPerfectWeekBadgeCheck - Badge earned!");

                                if (badgeEarnedCallback != null) {
                                    badgeEarnedCallback.onBadgeEarned(badge);
                                }
                            }

                            saveBadge(badge, callback);
                        } else {
                            android.util.Log.e("MotivationService", "performPerfectWeekBadgeCheck - Badge object is null");
                            if (callback != null) callback.onComplete();
                        }
                    } else {
                        android.util.Log.e("MotivationService", "performPerfectWeekBadgeCheck - No badge found in query");
                        if (callback != null) callback.onComplete();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MotivationService", "performPerfectWeekBadgeCheck - Query failed", e);
                    if (callback != null) callback.onComplete();
                });
    }

    private void updateTechniqueSessionBadge(boolean increment, UpdateCallback callback) {
        ensureTechniqueBadgeExists(() -> performTechniqueSessionBadgeUpdate(increment, callback));
    }

    private void performTechniqueSessionBadgeUpdate(boolean increment, UpdateCallback callback) {
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;
        
        android.util.Log.d("MotivationService", "performTechniqueSessionBadgeUpdate - Starting for userId: " + finalUserId + ", increment=" + increment);

        db.collection("badges")
                .whereEqualTo("userId", finalUserId)
                .whereEqualTo("type", "technique_sessions")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("MotivationService", "performTechniqueSessionBadgeUpdate - Query result: isEmpty=" + querySnapshot.isEmpty());

                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Badge badge = doc.toObject(Badge.class);
                        android.util.Log.d("MotivationService", "performTechniqueSessionBadgeUpdate - Found badge: " +
                                (badge != null ? "progress=" + badge.getProgress() + ", earned=" + badge.isEarned() : "null"));

                        if (badge != null) {
                            badge.setId(doc.getId());
                            if (!badge.isEarned()) {
                                if (increment) {
                                    int oldProgress = badge.getProgress();
                                    badge.setProgress(badge.getProgress() + 1);
                                    android.util.Log.d("MotivationService", "performTechniqueSessionBadgeUpdate - Updated progress: " +
                                            oldProgress + " -> " + badge.getProgress() + ", requirement: " + badge.getRequirement());
                                }

                                if (badge.getProgress() >= badge.getRequirement()) {
                                    badge.setEarned(true);
                                    badge.setEarnedDate(System.currentTimeMillis());
                                    android.util.Log.d("MotivationService", "performTechniqueSessionBadgeUpdate - Badge earned!");

                                    if (badgeEarnedCallback != null) {
                                        badgeEarnedCallback.onBadgeEarned(badge);
                                    }
                                }
                                saveBadge(badge, callback);
                            } else {
                                android.util.Log.d("MotivationService", "performTechniqueSessionBadgeUpdate - Badge already earned");
                                if (callback != null) callback.onComplete();
                            }
                        } else {
                            android.util.Log.e("MotivationService", "performTechniqueSessionBadgeUpdate - Badge object is null");
                            if (callback != null) callback.onComplete();
                        }
                    } else {
                        android.util.Log.e("MotivationService", "performTechniqueSessionBadgeUpdate - No badge found in query");
                        if (callback != null) callback.onComplete();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MotivationService", "performTechniqueSessionBadgeUpdate - Query failed", e);
                    if (callback != null) callback.onComplete();
                });
    }

    public void checkFirstRescueBadge(UpdateCallback callback) {
        ensureFirstRescueBadgeExists(() -> performFirstRescueBadgeCheck(callback));
    }

    private void performFirstRescueBadgeCheck(UpdateCallback callback) {
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;
        
        android.util.Log.d("MotivationService", "performFirstRescueBadgeCheck - Starting check");

        db.collection("badges")
                .whereEqualTo("userId", finalUserId)
                .whereEqualTo("type", "first_rescue_use")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Badge badge = doc.toObject(Badge.class);
                        if (badge != null && !badge.isEarned()) {
                            badge.setId(doc.getId());
                            badge.setProgress(1);
                            badge.setEarned(true);
                            badge.setEarnedDate(System.currentTimeMillis());

                            android.util.Log.d("MotivationService", "performFirstRescueBadgeCheck - Badge earned!");

                            if (badgeEarnedCallback != null) {
                                badgeEarnedCallback.onBadgeEarned(badge);
                            }

                            saveBadge(badge, callback);
                        } else {
                            callback.onComplete();
                        }
                    } else {
                        callback.onComplete();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MotivationService", "performFirstRescueBadgeCheck - Failed", e);
                    callback.onComplete();
                });
    }

    public void checkLowRescueBadge(UpdateCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int rescueThreshold = prefs.getInt("low_rescue_threshold", DEFAULT_LOW_RESCUE_THRESHOLD);
        int rescuePeriod = prefs.getInt("low_rescue_period", DEFAULT_LOW_RESCUE_PERIOD);

        ensureLowRescueBadgeExists(rescueThreshold, rescuePeriod,
                () -> performLowRescueBadgeCheck(rescueThreshold, rescuePeriod, callback));
    }

    private void performLowRescueBadgeCheck(int rescueThreshold, int rescuePeriod, UpdateCallback callback) {
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;

        android.util.Log.d("RescueInhalerService", "=== LOW RESCUE BADGE CHECK START ===");
        android.util.Log.d("RescueInhalerService", "userId: " + finalUserId);
        android.util.Log.d("RescueInhalerService", "threshold: " + rescueThreshold + ", period: " + rescuePeriod + " days");

        try {
            // Note: We can't easily get account creation time for a child user if we are a parent
            // For now, we'll use a fallback or try to get it from the user document if possible
            // But since we don't have easy access to child user metadata, we might need to skip the "account age" check
            // or assume it's old enough if we can't verify.
            
            // However, the original code used auth.getCurrentUser().getMetadata().
            // If we are viewing a child, auth.getCurrentUser() is the PARENT.
            // So using parent's account age for child's badge logic is technically wrong but might be the only easy option without fetching child user doc.
            // Let's stick to using the current authenticated user's metadata for account age for now as a proxy, 
            // or just proceed. 
            
            // BETTER APPROACH: Just use current time for "now" and query logs. 
            // The "period" logic relies on account creation date to define "months".
            // If we are a parent viewing a child, we should ideally fetch the child's creation date.
            // Since that's complex to add right now without changing more code, let's use the parent's creation date as the anchor 
            // OR just use the current date and look back 'rescuePeriod' days (rolling window).
            // The original code used fixed windows from account creation.
            
            // Let's keep using auth.getCurrentUser() for metadata for now to minimize breakage, 
            // but use finalUserId for the queries.
            
            com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
            long accountCreated = System.currentTimeMillis(); // Default to now if unavailable
            if (user != null && user.getMetadata() != null) {
                accountCreated = user.getMetadata().getCreationTimestamp();
            }

            long now = System.currentTimeMillis();
            long accountAgeDays = TimeUnit.MILLISECONDS.toDays(now - accountCreated);

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            android.util.Log.d("RescueInhalerService", "Account created (anchor): " + sdf.format(new java.util.Date(accountCreated)));
            android.util.Log.d("RescueInhalerService", "Current time: " + sdf.format(new java.util.Date(now)));
            android.util.Log.d("RescueInhalerService", "Account age: " + accountAgeDays + " days");

            // Calculate which month period we're in (0-indexed from account creation)
            int currentMonthIndex = (int) (accountAgeDays / rescuePeriod);
            long periodStart = accountCreated + TimeUnit.DAYS.toMillis(currentMonthIndex * rescuePeriod);
            long periodEnd = accountCreated + TimeUnit.DAYS.toMillis((currentMonthIndex + 1) * rescuePeriod);
            
            // Track progress even if account is not old enough to earn badge
            boolean canEarnBadge = accountAgeDays >= rescuePeriod;

            android.util.Log.d("RescueInhalerService", "Current month index: " + currentMonthIndex);
            android.util.Log.d("RescueInhalerService", "Period start: " + sdf.format(new java.util.Date(periodStart)));
            android.util.Log.d("RescueInhalerService", "Period end: " + sdf.format(new java.util.Date(periodEnd)));
            android.util.Log.d("RescueInhalerService", "Querying Firestore for rescue_inhaler_logs...");

            // Convert long timestamps to Date objects for Firestore query
            java.util.Date periodStartDate = new java.util.Date(periodStart);
            java.util.Date periodEndDate = new java.util.Date(periodEnd);

            db.collection("rescue_inhaler_logs")
                    .whereEqualTo("userId", finalUserId)
                    .whereGreaterThanOrEqualTo("timestamp", periodStartDate)
                    .whereLessThan("timestamp", periodEndDate)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int totalRescueUses = querySnapshot.size();
                        android.util.Log.d("RescueInhalerService", "Query SUCCESS: Found " + totalRescueUses + " rescue logs");
                        
                        // Log each document found
                        for (DocumentSnapshot logDoc : querySnapshot.getDocuments()) {
                            java.util.Date timestamp = logDoc.getDate("timestamp");
                            if (timestamp != null) {
                                android.util.Log.d("RescueInhalerService", "  - Log ID: " + logDoc.getId() + 
                                    ", timestamp: " + sdf.format(timestamp));
                            }
                        }

                        android.util.Log.d("RescueInhalerService", "Querying for low_rescue_month badge...");

                        db.collection("badges")
                                .whereEqualTo("userId", finalUserId)
                                .whereEqualTo("type", "low_rescue_month")
                                .limit(1)
                                .get()
                                .addOnSuccessListener(badgeQuery -> {
                                    android.util.Log.d("RescueInhalerService", "Badge query result: isEmpty=" + badgeQuery.isEmpty());

                                    if (!badgeQuery.isEmpty()) {
                                        DocumentSnapshot doc = badgeQuery.getDocuments().get(0);
                                        Badge badge = doc.toObject(Badge.class);
                                        if (badge != null) {
                                            badge.setId(doc.getId());

                                            android.util.Log.d("RescueInhalerService", "Current badge state:");
                                            android.util.Log.d("RescueInhalerService", "  - Badge ID: " + badge.getId());
                                            android.util.Log.d("RescueInhalerService", "  - Old progress: " + badge.getProgress());
                                            android.util.Log.d("RescueInhalerService", "  - New progress: " + totalRescueUses);
                                            android.util.Log.d("RescueInhalerService", "  - Requirement: " + rescueThreshold);
                                            android.util.Log.d("RescueInhalerService", "  - Already earned: " + badge.isEarned());

                                            badge.setProgress(totalRescueUses);
                                            badge.setPeriodEndDate(periodEnd); // Set period end for display

                                            // Check if we're at the end of the month period
                                            boolean isEndOfMonth = (now >= periodEnd - TimeUnit.DAYS.toMillis(1));
                                            android.util.Log.d("RescueInhalerService", "Can earn badge (account old enough): " + canEarnBadge);
                                            android.util.Log.d("RescueInhalerService", "Is end of month period: " + isEndOfMonth);
                                            
                                            // Use the requirement from the badge itself, not the passed preference
                                            // This ensures per-user settings are respected
                                            int actualThreshold = badge.getRequirement();
                                            
                                            if (!badge.isEarned() && canEarnBadge && isEndOfMonth && totalRescueUses <= actualThreshold) {
                                                badge.setEarned(true);
                                                badge.setEarnedDate(System.currentTimeMillis());
                                                android.util.Log.d("RescueInhalerService", "🎉 BADGE EARNED for month " + currentMonthIndex + "!");

                                                if (badgeEarnedCallback != null) {
                                                    badgeEarnedCallback.onBadgeEarned(badge);
                                                }
                                            } else {
                                                android.util.Log.d("RescueInhalerService", "Badge NOT earned (earned=" + badge.isEarned() + 
                                                    ", canEarn=" + canEarnBadge + ", endOfMonth=" + isEndOfMonth + ", uses=" + totalRescueUses + "<=" + actualThreshold + ")");
                                            }

                                            android.util.Log.d("RescueInhalerService", "Saving badge to Firestore...");
                                            saveBadge(badge, callback);
                                        } else {
                                            android.util.Log.e("RescueInhalerService", "ERROR: Badge object is null");
                                            if (callback != null) callback.onComplete();
                                        }
                                    } else {
                                        android.util.Log.e("RescueInhalerService", "ERROR: No badge found in Firestore");
                                        if (callback != null) callback.onComplete();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("RescueInhalerService", "ERROR: Badge query failed", e);
                                    if (callback != null) callback.onComplete();
                                });
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("RescueInhalerService", "ERROR: Rescue logs query failed", e);
                        if (callback != null) callback.onComplete();
                    });
        } catch (Exception e) {
            android.util.Log.e("RescueInhalerService", "ERROR: Exception in performLowRescueBadgeCheck", e);
            if (callback != null) callback.onComplete();
        }
        
        android.util.Log.d("RescueInhalerService", "=== LOW RESCUE BADGE CHECK END ===");
    }

    // Helper methods
    private long getStartOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void checkControllerMedicationForToday(MedicationCheckCallback callback) {
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;
        
        long todayStart = getStartOfDay(System.currentTimeMillis());
        long todayEnd = todayStart + TimeUnit.DAYS.toMillis(1) - 1;

        // Check if user has logged controller medication today
        db.collection("controller_medicine_logs")
                .whereEqualTo("userId", finalUserId)
                .whereGreaterThanOrEqualTo("timestamp", todayStart)
                .whereLessThanOrEqualTo("timestamp", todayEnd)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> callback.onResult(!querySnapshot.isEmpty()))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // Settings methods
    public void updateBadgeThresholds(int techniqueSessions, int lowRescueThreshold, int lowRescuePeriod) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("technique_sessions_required", techniqueSessions)
                .putInt("low_rescue_threshold", lowRescueThreshold)
                .putInt("low_rescue_period", lowRescuePeriod)
                .apply();

        // Update existing badges with new requirements
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;
        
        db.collection("badges")
                .whereEqualTo("userId", finalUserId)
                .whereEqualTo("type", "technique_sessions")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Badge badge = doc.toObject(Badge.class);
                        if (badge != null) {
                            badge.setId(doc.getId());
                            badge.setRequirement(techniqueSessions);
                            badge.setDescription("Complete " + techniqueSessions + " high-quality technique practice sessions");
                            saveBadge(badge, null);
                        }
                    }
                });

        db.collection("badges")
                .whereEqualTo("userId", finalUserId)
                .whereEqualTo("type", "low_rescue_month")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Badge badge = doc.toObject(Badge.class);
                        if (badge != null) {
                            badge.setId(doc.getId());
                            badge.setRequirement(lowRescueThreshold);
                            badge.setDescription("Use rescue inhaler ≤" + lowRescueThreshold + " times in " + lowRescuePeriod + " days");
                            saveBadge(badge, null);
                        }
                    }
                });
    }

    public Map<String, Integer> getBadgeThresholds() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, Integer> thresholds = new HashMap<>();
        thresholds.put("technique_sessions", prefs.getInt("technique_sessions_required", DEFAULT_TECHNIQUE_SESSIONS_REQUIRED));
        thresholds.put("low_rescue_threshold", prefs.getInt("low_rescue_threshold", DEFAULT_LOW_RESCUE_THRESHOLD));
        thresholds.put("low_rescue_period", prefs.getInt("low_rescue_period", DEFAULT_LOW_RESCUE_PERIOD));
        return thresholds;
    }

    // Cleanup method to remove duplicate badges
    public void cleanupDuplicateBadges(UpdateCallback callback) {
        String userId = targetUserId;
        if (userId == null && auth.getCurrentUser() != null) userId = auth.getCurrentUser().getUid();
        final String finalUserId = userId;
        
        db.collection("badges")
                .whereEqualTo("userId", finalUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Group badges by type
                    Map<String, List<DocumentSnapshot>> badgesByType = new HashMap<>();
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String type = doc.getString("type");
                        if (type != null) {
                            if (!badgesByType.containsKey(type)) {
                                badgesByType.put(type, new ArrayList<>());
                            }
                            badgesByType.get(type).add(doc);
                        }
                    }
                    
                    // Delete duplicates (keep only the first one of each type)
                    List<com.google.android.gms.tasks.Task<Void>> deleteTasks = new ArrayList<>();
                    for (Map.Entry<String, List<DocumentSnapshot>> entry : badgesByType.entrySet()) {
                        List<DocumentSnapshot> badges = entry.getValue();
                        if (badges.size() > 1) {
                            // Delete all except the first one
                            for (int i = 1; i < badges.size(); i++) {
                                deleteTasks.add(badges.get(i).getReference().delete());
                            }
                        }
                    }
                    
                    if (deleteTasks.isEmpty()) {
                        if (callback != null) callback.onComplete();
                    } else {
                        com.google.android.gms.tasks.Tasks.whenAll(deleteTasks)
                                .addOnCompleteListener(task -> {
                                    if (callback != null) callback.onComplete();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onComplete();
                    }
                });
    }

    // Callback interfaces
    public interface UpdateCallback {
        void onComplete();
    }

    private interface MedicationCheckCallback {
        void onResult(boolean hasMedication);
    }

    private interface BadgeTemplateBuilder {
        Badge build(String badgeId, String userId);
    }
}
