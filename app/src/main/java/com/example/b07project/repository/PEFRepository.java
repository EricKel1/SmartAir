package com.example.b07project.repository;

import com.example.b07project.models.PEFReading;
import com.example.b07project.models.PersonalBest;
import com.example.b07project.models.ZoneChangeLog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
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
        android.util.Log.d("PEF_ZoneLogic", "--- Starting savePEFReading for value: " + reading.getValue() + " ---");

        getPersonalBest(reading.getUserId(), new LoadCallback<PersonalBest>() {
            @Override
            public void onSuccess(PersonalBest pb) {
                if (pb == null) {
                    android.util.Log.w("PEF_ZoneLogic", "No PersonalBest. Saving with 'unknown' zone.");
                    reading.setZone("unknown");
                    savePEFReadingToFirestore(reading, callback);
                    return;
                }
                android.util.Log.d("PEF_ZoneLogic", "PersonalBest found: " + pb.getValue());

                // We need the last reading's zone BEFORE we do anything else
                getLastPEFReading(reading.getUserId(), new LoadCallback<PEFReading>() {
                    @Override
                    public void onSuccess(PEFReading lastReading) {
                        String previousDayZone = (lastReading != null) ? lastReading.getZone() : "unknown";
                        android.util.Log.d("PEF_ZoneLogic", "The zone of the last saved entry was: '" + previousDayZone + "'");

                        // Now, get the rest of today's readings to determine the new zone
                        getReadingsForToday(reading.getUserId(), new LoadCallback<List<PEFReading>>() {
                            @Override
                            public void onSuccess(List<PEFReading> todaysReadings) {
                                android.util.Log.d("PEF_ZoneLogic", "Found " + todaysReadings.size() + " previous readings for today.");
                                List<String> todaysZones = new ArrayList<>();

                                // Add the zone from the NEW reading
                                String newReadingZone = PersonalBest.calculateZone(reading.getValue(), pb.getValue());
                                todaysZones.add(newReadingZone);
                                android.util.Log.d("PEF_ZoneLogic", "Current reading (value: " + reading.getValue() + ") has individual zone: " + newReadingZone);

                                // Add zones from all PREVIOUS readings today
                                for (PEFReading r : todaysReadings) {
                                    todaysZones.add(PersonalBest.calculateZone(r.getValue(), pb.getValue()));
                                }

                                // Determine the BEST zone from all zones collected today
                                String bestZoneToday = getBestZone(todaysZones);
                                android.util.Log.i("PEF_ZoneLogic", "FINAL BEST ZONE for today is: '" + bestZoneToday + "'.");

                                // Set the new reading's zone to the day's best zone
                                reading.setZone(bestZoneToday);
                                reading.setPersonalBest(pb.getValue());

                                // Compare the new final zone with the last saved entry's zone
                                if (!bestZoneToday.equals(previousDayZone)) {
                                    android.util.Log.i("PEF_ZoneLogic", "ZONE CHANGE DETECTED: From " + previousDayZone + " to " + bestZoneToday);
                                    logZoneChange(reading.getUserId(), previousDayZone, bestZoneToday,
                                            reading.getValue(), pb.getValue(), null);
                                } else {
                                    android.util.Log.d("PEF_ZoneLogic", "No change in daily zone. Both are '" + bestZoneToday + "'");
                                }

                                savePEFReadingToFirestore(reading, callback);
                            }

                            @Override
                            public void onFailure(String error) {
                                // Fallback logic
                                android.util.Log.e("PEF_ZoneLogic", "Could not get today's readings: " + error);
                                String zone = PersonalBest.calculateZone(reading.getValue(), pb.getValue());
                                reading.setZone(zone);
                                reading.setPersonalBest(pb.getValue());
                                if (!zone.equals(previousDayZone)) {
                                    logZoneChange(reading.getUserId(), previousDayZone, zone, reading.getValue(), pb.getValue(), null);
                                }
                                savePEFReadingToFirestore(reading, callback);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        // This is if getLastPEFReading fails, it's not critical. We just can't log a zone change.
                        android.util.Log.e("PEF_ZoneLogic", "Could not get last reading: " + error + ". Proceeding without zone change check.");
                        // Since we can't check the last zone, we just run the main logic without the check.
                        // This block is now redundant because the logic is nested, but we'll keep it as a safeguard.
                        String zone = PersonalBest.calculateZone(reading.getValue(), pb.getValue());
                        reading.setZone(zone);
                        reading.setPersonalBest(pb.getValue());
                        savePEFReadingToFirestore(reading, callback);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                android.util.Log.e("PEF_ZoneLogic", "CRITICAL: Could not get PersonalBest: " + error);
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

    public void getReadingsForToday(String userId, LoadCallback<List<PEFReading>> callback) {
        // Set up start and end of the current day
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        Date startOfDay = cal.getTime();

        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        Date endOfDay = cal.getTime();

        db.collection(PEF_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .whereLessThanOrEqualTo("timestamp", endOfDay)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<PEFReading> readings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        PEFReading reading = new PEFReading();
                        Long valueLong = doc.getLong("value");
                        reading.setValue(valueLong != null ? valueLong.intValue() : 0);
                        // We only need the value for this logic, but can fill in more if needed
                        readings.add(reading);
                    }
                    callback.onSuccess(readings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void handleZoneChangeAndSave(PEFReading reading, PersonalBest pb, SaveCallback callback) {
        getLastPEFReading(reading.getUserId(), new LoadCallback<PEFReading>() {
            @Override
            public void onSuccess(PEFReading lastReading) {
                if (lastReading != null && lastReading.getZone() != null) {
                    android.util.Log.d("PEF_ZoneLogic", "Comparing new zone '" + reading.getZone() + "' with last reading's zone '" + lastReading.getZone() + "'");
                    if (!reading.getZone().equals(lastReading.getZone())) {
                        android.util.Log.i("PEF_ZoneLogic", "ZONE CHANGE DETECTED: From " + lastReading.getZone() + " to " + reading.getZone());
                        logZoneChange(reading.getUserId(), lastReading.getZone(), reading.getZone(),
                                reading.getValue(), pb.getValue(), null);
                    }
                } else {
                    android.util.Log.d("PEF_ZoneLogic", "No previous reading found to compare zones.");
                }
                savePEFReadingToFirestore(reading, callback);
            }

            @Override
            public void onFailure(String error) {
                android.util.Log.e("PEF_ZoneLogic", "Failed to get last PEF reading: " + error + ". Saving new reading anyway.");
                savePEFReadingToFirestore(reading, callback);
            }
        });
    }


    private String getBestZone(List<String> zones) {
        android.util.Log.d("PEF_ZoneLogic", "Finding best zone from list: " + zones.toString());
        if (zones.contains("green")) {
            android.util.Log.d("PEF_ZoneLogic", "Best zone is 'green'");
            return "green";
        }
        if (zones.contains("yellow")) {
            android.util.Log.d("PEF_ZoneLogic", "Best zone is 'yellow'");
            return "yellow";
        }
        if (zones.contains("red")) {
            android.util.Log.d("PEF_ZoneLogic", "Best zone is 'red'");
            return "red";
        }
        android.util.Log.d("PEF_ZoneLogic", "No best zone found, defaulting to 'unknown'");
        return "unknown"; // Default case
    }



}
