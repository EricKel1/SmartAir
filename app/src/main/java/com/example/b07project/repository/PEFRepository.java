package com.example.b07project.repository;

import com.example.b07project.models.PEFReading;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.models.ZoneChangeLog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PEFRepository {
    private final FirebaseFirestore db;
    private static final String PEF_COLLECTION = "pef_readings";
    private static final String PB_COLLECTION = "personal_bests";
    private static final String ZONE_LOG_COLLECTION = "zone_change_logs";

    public PEFRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public interface SaveCallback {
        void onSuccess(String documentId);
        void onFailure(String error);
    }

    public interface LoadCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }

    // PEF Reading operations
    public void savePEFReading(PEFReading reading, SaveCallback callback) {
        // Get personal best first to calculate zone
        getPersonalBest(reading.getUserId(), new LoadCallback<PersonalBest>() {
            @Override
            public void onSuccess(PersonalBest pb) {
                if (pb != null) {
                    String zone = PersonalBest.calculateZone(reading.getValue(), pb.getValue());
                    reading.setZone(zone);
                    reading.setPersonalBest(pb.getValue());
                    
                    // Check if zone changed from last reading
                    getLastPEFReading(reading.getUserId(), new LoadCallback<PEFReading>() {
                        @Override
                        public void onSuccess(PEFReading lastReading) {
                            if (lastReading != null && !zone.equals(lastReading.getZone())) {
                                // Log zone change
                                logZoneChange(reading.getUserId(), lastReading.getZone(), zone, 
                                    reading.getValue(), pb.getValue(), null);
                            }
                            savePEFReadingToFirestore(reading, callback);
                        }

                        @Override
                        public void onFailure(String error) {
                            // Still save reading even if we couldn't get last reading
                            savePEFReadingToFirestore(reading, callback);
                        }
                    });
                } else {
                    reading.setZone("unknown");
                    savePEFReadingToFirestore(reading, callback);
                }
            }

            @Override
            public void onFailure(String error) {
                reading.setZone("unknown");
                savePEFReadingToFirestore(reading, callback);
            }
        });
    }

    private void savePEFReadingToFirestore(PEFReading reading, SaveCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", reading.getUserId());
        data.put("timestamp", reading.getTimestamp());
        data.put("value", reading.getValue());
        data.put("isPreMedication", reading.isPreMedication());
        data.put("isPostMedication", reading.isPostMedication());
        data.put("notes", reading.getNotes());
        data.put("zone", reading.getZone());
        data.put("personalBest", reading.getPersonalBest());

        db.collection(PEF_COLLECTION)
            .add(data)
            .addOnSuccessListener(documentReference -> {
                if (callback != null) {
                    callback.onSuccess(documentReference.getId());
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            });
    }

    public void getPEFReadingsForUser(String userId, LoadCallback<List<PEFReading>> callback) {
        db.collection(PEF_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snapshots -> {
                List<PEFReading> readings = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    PEFReading reading = new PEFReading();
                    reading.setId(doc.getId());
                    reading.setUserId(doc.getString("userId"));
                    reading.setTimestamp(doc.getDate("timestamp"));
                    Long valueLong = doc.getLong("value");
                    reading.setValue(valueLong != null ? valueLong.intValue() : 0);
                    Boolean preMed = doc.getBoolean("isPreMedication");
                    reading.setPreMedication(preMed != null && preMed);
                    Boolean postMed = doc.getBoolean("isPostMedication");
                    reading.setPostMedication(postMed != null && postMed);
                    reading.setNotes(doc.getString("notes"));
                    reading.setZone(doc.getString("zone"));
                    Long pbLong = doc.getLong("personalBest");
                    reading.setPersonalBest(pbLong != null ? pbLong.intValue() : 0);
                    readings.add(reading);
                }
                if (callback != null) {
                    callback.onSuccess(readings);
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            });
    }

    public void getLastPEFReading(String userId, LoadCallback<PEFReading> callback) {
        android.util.Log.d("childparentlink", "Repo: getLastPEFReading for user: " + userId);
        db.collection(PEF_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener(snapshots -> {
                if (!snapshots.isEmpty()) {
                    android.util.Log.d("childparentlink", "Repo: Found PEF reading for user: " + userId);
                    QueryDocumentSnapshot doc = (QueryDocumentSnapshot) snapshots.getDocuments().get(0);
                    PEFReading reading = new PEFReading();
                    reading.setId(doc.getId());
                    reading.setUserId(doc.getString("userId"));
                    reading.setTimestamp(doc.getDate("timestamp"));
                    Long valueLong = doc.getLong("value");
                    reading.setValue(valueLong != null ? valueLong.intValue() : 0);
                    reading.setZone(doc.getString("zone"));
                    Long pbLong = doc.getLong("personalBest");
                    reading.setPersonalBest(pbLong != null ? pbLong.intValue() : 0);
                    if (callback != null) {
                        callback.onSuccess(reading);
                    }
                } else {
                    android.util.Log.d("childparentlink", "Repo: No PEF reading found for user: " + userId);
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("childparentlink", "Repo: Error fetching PEF reading", e);
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            });
    }

    // Personal Best operations
    public void setPersonalBest(PersonalBest pb, SaveCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", pb.getUserId());
        data.put("value", pb.getValue());
        data.put("setByUserId", pb.getSetByUserId());
        data.put("dateSet", pb.getDateSet());
        data.put("notes", pb.getNotes());

        // Delete old PB for this user first
        db.collection(PB_COLLECTION)
            .whereEqualTo("userId", pb.getUserId())
            .get()
            .addOnSuccessListener(snapshots -> {
                for (QueryDocumentSnapshot doc : snapshots) {
                    doc.getReference().delete();
                }
                
                // Save new PB
                db.collection(PB_COLLECTION)
                    .add(data)
                    .addOnSuccessListener(documentReference -> {
                        if (callback != null) {
                            callback.onSuccess(documentReference.getId());
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (callback != null) {
                            callback.onFailure(e.getMessage());
                        }
                    });
            });
    }

    public void getPersonalBest(String userId, LoadCallback<PersonalBest> callback) {
        db.collection(PB_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("dateSet", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener(snapshots -> {
                if (!snapshots.isEmpty()) {
                    QueryDocumentSnapshot doc = (QueryDocumentSnapshot) snapshots.getDocuments().get(0);
                    PersonalBest pb = new PersonalBest();
                    pb.setId(doc.getId());
                    pb.setUserId(doc.getString("userId"));
                    Long valueLong = doc.getLong("value");
                    pb.setValue(valueLong != null ? valueLong.intValue() : 0);
                    pb.setSetByUserId(doc.getString("setByUserId"));
                    pb.setDateSet(doc.getDate("dateSet"));
                    pb.setNotes(doc.getString("notes"));
                    if (callback != null) {
                        callback.onSuccess(pb);
                    }
                } else {
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            });
    }

    // Zone change logging
    private void logZoneChange(String userId, String previousZone, String newZone, 
                               int pefValue, int personalBest, SaveCallback callback) {
        ZoneChangeLog log = new ZoneChangeLog(userId, previousZone, newZone, pefValue, personalBest);
        
        Map<String, Object> data = new HashMap<>();
        data.put("userId", log.getUserId());
        data.put("timestamp", log.getTimestamp());
        data.put("previousZone", log.getPreviousZone());
        data.put("newZone", log.getNewZone());
        data.put("pefValue", log.getPefValue());
        data.put("personalBest", log.getPersonalBest());
        data.put("percentage", log.getPercentage());

        db.collection(ZONE_LOG_COLLECTION)
            .add(data)
            .addOnSuccessListener(documentReference -> {
                if (callback != null) {
                    callback.onSuccess(documentReference.getId());
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            });
    }

    public void getZoneChangeLogs(String userId, LoadCallback<List<ZoneChangeLog>> callback) {
        db.collection(ZONE_LOG_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snapshots -> {
                List<ZoneChangeLog> logs = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    ZoneChangeLog log = new ZoneChangeLog();
                    log.setId(doc.getId());
                    log.setUserId(doc.getString("userId"));
                    log.setTimestamp(doc.getDate("timestamp"));
                    log.setPreviousZone(doc.getString("previousZone"));
                    log.setNewZone(doc.getString("newZone"));
                    Long pefLong = doc.getLong("pefValue");
                    log.setPefValue(pefLong != null ? pefLong.intValue() : 0);
                    Long pbLong = doc.getLong("personalBest");
                    log.setPersonalBest(pbLong != null ? pbLong.intValue() : 0);
                    Long pctLong = doc.getLong("percentage");
                    log.setPercentage(pctLong != null ? pctLong.intValue() : 0);
                    logs.add(log);
                }
                if (callback != null) {
                    callback.onSuccess(logs);
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            });
    }
}
