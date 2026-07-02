package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DailyMissionsRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    private MutableLiveData<DailyMissionsData> missionsLiveData;

    public interface MissionCallback {
        void onSuccess(boolean newlyCompleted);
        void onError(String error);
    }

    public static class DailyMissionsData {
        public String date;
        public boolean winMatch;
        public boolean sendChat;
        public boolean friendlyMatch;
        public boolean winTournament;
        public boolean rewardsClaimed;

        public int completedCount() {
            int c = 0;
            if (winMatch) c++;
            if (sendChat) c++;
            if (friendlyMatch) c++;
            if (winTournament) c++;
            return c;
        }

        public boolean allCompleted() {
            return completedCount() >= 4;
        }
    }

    public LiveData<DailyMissionsData> getMissions() {
        if (missionsLiveData == null) {
            missionsLiveData = new MutableLiveData<>();
        }
        refreshMissions();
        return missionsLiveData;
    }

    public void refreshMissions() {
        if (missionsLiveData == null) {
            missionsLiveData = new MutableLiveData<>();
        }
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            missionsLiveData.setValue(new DailyMissionsData());
            return;
        }
        String today = getToday();
        db.collection("users").document(user.getUid())
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    DailyMissionsData data = parseMissions(doc, today);
                    if (data.winMatch) {
                        if (missionsLiveData != null) missionsLiveData.setValue(data);
                    } else {
                        checkWonMatchToday(user.getUid(), today, data);
                    }
                })
                .addOnFailureListener(e -> {
                    db.collection("users").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                DailyMissionsData data = parseMissions(doc, today);
                                if (data.winMatch) {
                                    if (missionsLiveData != null) missionsLiveData.setValue(data);
                                } else {
                                    checkWonMatchToday(user.getUid(), today, data);
                                }
                            });
                });
    }

    private void checkWonMatchToday(String uid, String today, DailyMissionsData data) {
        long todayStartMs = getTodayStartMs();
        db.collection("slagalica_match_results")
                .whereEqualTo("winnerUid", uid)
                .whereGreaterThanOrEqualTo("timestamp", todayStartMs)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        data.winMatch = true;
                        Map<String, Object> update = new HashMap<>();
                        update.put("dailyMissions.date", today);
                        update.put("dailyMissions.winMatch", true);
                        // Reset other fields so yesterday's progress doesn't carry over.
                        update.put("dailyMissions.sendChat", false);
                        update.put("dailyMissions.friendlyMatch", false);
                        update.put("dailyMissions.winTournament", false);
                        update.put("dailyMissions.rewardsClaimed", false);
                        db.collection("users").document(uid).update(update);
                    }
                    if (missionsLiveData != null) missionsLiveData.setValue(data);
                })
                .addOnFailureListener(e -> {
                    if (missionsLiveData != null) missionsLiveData.setValue(data);
                });
    }

    private long getTodayStartMs() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private DailyMissionsData parseMissions(DocumentSnapshot doc, String today) {
        DailyMissionsData data = new DailyMissionsData();
        data.date = today;
        if (doc.contains("dailyMissions")) {
            Map<String, Object> missions = (Map<String, Object>) doc.get("dailyMissions");
            if (missions != null) {
                String storedDate = (String) missions.get("date");
                if (today.equals(storedDate)) {
                    data.winMatch = bool(missions.get("winMatch"));
                    data.sendChat = bool(missions.get("sendChat"));
                    data.friendlyMatch = bool(missions.get("friendlyMatch"));
                    data.winTournament = bool(missions.get("winTournament"));
                    data.rewardsClaimed = bool(missions.get("rewardsClaimed"));
                }
            }
        }
        return data;
    }

    public void completeMission(String missionKey, MissionCallback callback) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { if (callback != null) callback.onError("Nije ulogovan"); return; }

        String today = getToday();
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    DailyMissionsData data = new DailyMissionsData();
                    data.date = today;

                    Map<String, Object> missions = null;
                    if (doc.exists() && doc.contains("dailyMissions")) {
                        missions = (Map<String, Object>) doc.get("dailyMissions");
                        if (missions != null && today.equals(missions.get("date"))) {
                            data.winMatch = bool(missions.get("winMatch"));
                            data.sendChat = bool(missions.get("sendChat"));
                            data.friendlyMatch = bool(missions.get("friendlyMatch"));
                            data.winTournament = bool(missions.get("winTournament"));
                            data.rewardsClaimed = bool(missions.get("rewardsClaimed"));
                        }
                    }
                    // Track whether this is the first mission written for today so we
                    // can explicitly reset stale fields left over from previous days.
                    boolean isNewDay = (missions == null || !today.equals(missions.get("date")));

                    boolean wasAllBefore = data.allCompleted();
                    boolean wasAlreadyCompleted;
                    switch (missionKey) {
                        case "winMatch":      wasAlreadyCompleted = data.winMatch;      data.winMatch = true;      break;
                        case "sendChat":      wasAlreadyCompleted = data.sendChat;      data.sendChat = true;      break;
                        case "friendlyMatch": wasAlreadyCompleted = data.friendlyMatch; data.friendlyMatch = true; break;
                        case "winTournament": wasAlreadyCompleted = data.winTournament; data.winTournament = true; break;
                        default:              wasAlreadyCompleted = false;
                    }

                    int starsAwarded = 0;
                    int tokensAwarded = 0;

                    if (!wasAllBefore && data.allCompleted() && !data.rewardsClaimed) {
                        data.rewardsClaimed = true;
                        starsAwarded = 6; // 3 for the mission itself + 3 bonus for completing all 4
                        tokensAwarded = 2;
                    } else if (!wasAlreadyCompleted) {
                        starsAwarded = 3;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("dailyMissions.date", data.date);
                    if (isNewDay) {
                        // Explicitly reset all fields so yesterday's progress doesn't
                        // bleed into today when only one mission is written.
                        updates.put("dailyMissions.winMatch", false);
                        updates.put("dailyMissions.sendChat", false);
                        updates.put("dailyMissions.friendlyMatch", false);
                        updates.put("dailyMissions.winTournament", false);
                        updates.put("dailyMissions.rewardsClaimed", false);
                    }
                    updates.put("dailyMissions." + missionKey, true);
                    if (data.rewardsClaimed) {
                        updates.put("dailyMissions.rewardsClaimed", true);
                    }

                    if (starsAwarded > 0) {
                        Long curStars = doc.getLong("totalStars");
                        updates.put("totalStars", (curStars != null ? curStars : 0) + starsAwarded);
                    }
                    if (tokensAwarded > 0) {
                        Long curTokens = doc.getLong("tokenCount");
                        updates.put("tokenCount", (curTokens != null ? curTokens : 0) + tokensAwarded);
                    }

                    final boolean newlyCompleted = !wasAlreadyCompleted;
                    final int finalStarsAwarded = starsAwarded;
                    final int finalTokensAwarded = tokensAwarded;
                    db.collection("users").document(user.getUid())
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                if (newlyCompleted) {
                                    String name = missionLabel(missionKey);
                                    String body = (finalStarsAwarded == 6)
                                            ? "'" + name + "' završena i sve dnevne misije su ispunjene! Dobili ste " + finalStarsAwarded + " zvezdi i " + finalTokensAwarded + " tokena."
                                            : "'" + name + "' završena. Dobili ste " + finalStarsAwarded + " zvezdi.";
                                    NotificationRepository.saveForUser(user.getUid(),
                                            "Dnevna misija završena!", body, "rewards");
                                }
                                if (callback != null) callback.onSuccess(newlyCompleted);
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onError(e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    private String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String missionLabel(String key) {
        switch (key) {
            case "winMatch":      return "Pobedi u mecu";
            case "sendChat":      return "Posalji poruku";
            case "friendlyMatch": return "Odigraj prijateljski mec";
            case "winTournament": return "Pobedi na turniru";
            default:              return key;
        }
    }

    private boolean bool(Object val) {
        return val instanceof Boolean && (Boolean) val;
    }
}
