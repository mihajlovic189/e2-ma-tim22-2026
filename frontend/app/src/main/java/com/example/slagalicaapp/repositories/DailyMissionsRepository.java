package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            missionsLiveData.setValue(new DailyMissionsData());
            return;
        }
        String today = getToday();
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    DailyMissionsData data = parseMissions(doc, today);
                    missionsLiveData.setValue(data);
                })
                .addOnFailureListener(e -> {});
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
                    db.collection("users").document(user.getUid())
                            .update(updates)
                            .addOnSuccessListener(unused -> {
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

    private boolean bool(Object val) {
        return val instanceof Boolean && (Boolean) val;
    }
}
