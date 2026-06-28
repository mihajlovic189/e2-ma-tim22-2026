package com.example.slagalicaapp.repositories;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalicaapp.SlagalicaApp;
import com.example.slagalicaapp.data.models.PlayerLeaderboardEntry;
import com.example.slagalicaapp.notifications.AppNotificationManager;
import com.example.slagalicaapp.utils.LeagueManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LeaderboardRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    public String getCurrentWeekId() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        int year = cal.get(Calendar.YEAR);
        return String.format(Locale.US, "%d-W%02d", year, week);
    }

    public String getCurrentMonthId() {
        return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
    }

    public String getWeekDateRange() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.", Locale.getDefault());
        String start = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_WEEK, 6);
        String end = sdf.format(cal.getTime());
        return start + " - " + end;
    }

    public String getMonthDateRange() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        return sdf.format(new Date());
    }

    public LiveData<List<PlayerLeaderboardEntry>> getWeeklyLeaderboard() {
        MutableLiveData<List<PlayerLeaderboardEntry>> result = new MutableLiveData<>();
        FirebaseUser user = mAuth.getCurrentUser();
        String currentUid = user != null ? user.getUid() : "";
        String currentWeek = getCurrentWeekId();

        db.collection("users").whereEqualTo("cycleWeek", currentWeek).get()
                .addOnSuccessListener(snapshots -> {
                    List<PlayerLeaderboardEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Long gamesPlayed = doc.getLong("weeklyGamesPlayed");
                        if (gamesPlayed == null || gamesPlayed == 0) continue; // spec: must play at least one game
                        String username = doc.getString("username");
                        if (username == null) continue;
                        Long ws = doc.getLong("weeklyStars");
                        int stars = ws != null ? ws.intValue() : 0;
                        boolean isCurrentUser = doc.getId().equals(currentUid);
                        PlayerLeaderboardEntry entry = new PlayerLeaderboardEntry(username, stars, isCurrentUser);
                        String leagueName = doc.getString("leagueName");
                        entry.setLeagueIcon(LeagueManager.iconForName(leagueName));
                        entries.add(entry);
                    }
                    entries.sort((a, b) -> b.getMonthlyStars() - a.getMonthlyStars());
                    for (int i = 0; i < entries.size(); i++) entries.get(i).setRank(i + 1);
                    result.setValue(entries);
                })
                .addOnFailureListener(e -> result.setValue(new ArrayList<>()));

        return result;
    }

    public LiveData<List<PlayerLeaderboardEntry>> getMonthlyLeaderboard() {
        MutableLiveData<List<PlayerLeaderboardEntry>> result = new MutableLiveData<>();
        FirebaseUser user = mAuth.getCurrentUser();
        String currentUid = user != null ? user.getUid() : "";
        String currentMonth = getCurrentMonthId();

        db.collection("users").whereEqualTo("cycleMonth", currentMonth).get()
                .addOnSuccessListener(snapshots -> {
                    List<PlayerLeaderboardEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Long gamesPlayed = doc.getLong("monthlyGamesPlayed");
                        if (gamesPlayed == null || gamesPlayed == 0) continue; // spec: must play at least one game
                        String username = doc.getString("username");
                        if (username == null) continue;
                        Long ms = doc.getLong("monthlyStars");
                        int stars = ms != null ? ms.intValue() : 0;
                        boolean isCurrentUser = doc.getId().equals(currentUid);
                        PlayerLeaderboardEntry entry = new PlayerLeaderboardEntry(username, stars, isCurrentUser);
                        String leagueName = doc.getString("leagueName");
                        entry.setLeagueIcon(LeagueManager.iconForName(leagueName));
                        entries.add(entry);
                    }
                    entries.sort((a, b) -> b.getMonthlyStars() - a.getMonthlyStars());
                    for (int i = 0; i < entries.size(); i++) entries.get(i).setRank(i + 1);
                    result.setValue(entries);
                })
                .addOnFailureListener(e -> result.setValue(new ArrayList<>()));

        return result;
    }

    public void checkAndDistributeRewards(Runnable onRewardReady) {
        checkAndDistributeRewards(null, onRewardReady);
    }

    public void checkAndDistributeRewards(Context context, Runnable onRewardReady) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { if (onRewardReady != null) onRewardReady.run(); return; }
        String uid = user.getUid();

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) { if (onRewardReady != null) onRewardReady.run(); return; }

            String currentWeek = getCurrentWeekId();
            String currentMonth = getCurrentMonthId();
            String storedWeek = doc.getString("cycleWeek");
            String storedMonth = doc.getString("cycleMonth");

            boolean weekChanged = storedWeek != null && !storedWeek.equals(currentWeek);
            boolean monthChanged = storedMonth != null && !storedMonth.equals(currentMonth);

            if (!weekChanged && !monthChanged) {
                if (onRewardReady != null) onRewardReady.run();
                return;
            }

            processCycleEnd(uid, doc, currentWeek, currentMonth,
                    storedWeek, storedMonth, 0, 0, context, onRewardReady);
        }).addOnFailureListener(e -> { if (onRewardReady != null) onRewardReady.run(); });
    }

    private void processCycleEnd(String uid, com.google.firebase.firestore.DocumentSnapshot doc,
                                  String currentWeek, String currentMonth,
                                  String storedWeek, String storedMonth,
                                  int weekRank, int monthRank, Context context, Runnable onRewardReady) {
        Map<String, Object> updates = new HashMap<>();
        boolean needWeekQuery = false;
        boolean needMonthQuery = false;

        if (storedWeek != null && !storedWeek.equals(currentWeek)) {
            Long ws = doc.getLong("weeklyStars");
            int weeklyStars = ws != null ? ws.intValue() : 0;
            if (weeklyStars > 0) needWeekQuery = true;
            updates.put("weeklyStars", 0L);
            updates.put("weeklyGamesPlayed", 0L);
            updates.put("cycleWeek", currentWeek);
        }

        if (storedMonth != null && !storedMonth.equals(currentMonth)) {
            Long ms = doc.getLong("monthlyStars");
            int monthlyStars = ms != null ? ms.intValue() : 0;
            if (monthlyStars > 0) needMonthQuery = true;
            updates.put("monthlyStars", 0L);
            updates.put("monthlyGamesPlayed", 0L);
            updates.put("cycleMonth", currentMonth);
        }

        if (!needWeekQuery && !needMonthQuery) {
            applyUpdates(uid, updates, onRewardReady);
            return;
        }

        applyCycleUpdates(uid, doc, updates, currentWeek, currentMonth,
                storedWeek, storedMonth, needWeekQuery, needMonthQuery,
                weekRank, monthRank, context, onRewardReady);
    }

    private void applyCycleUpdates(String uid, com.google.firebase.firestore.DocumentSnapshot doc,
                                    Map<String, Object> updates,
                                    String currentWeek, String currentMonth,
                                    String storedWeek, String storedMonth,
                                    boolean needWeekQuery, boolean needMonthQuery,
                                    int weekRank, int monthRank, Context context, Runnable onRewardReady) {
        Runnable applyAndFinish = () -> applyUpdates(uid, updates, onRewardReady);

        if (needWeekQuery) {
            String starsField = "weeklyStars";
            db.collection("users").whereEqualTo("cycleWeek", storedWeek).get()
                    .addOnSuccessListener(snapshots -> {
                        int rank = computeUserRankFromSnapshots(snapshots, starsField, uid);
                        Long ws = doc.getLong("weeklyStars");
                        int weeklyStars = ws != null ? ws.intValue() : 0;
                        int tokens = getWeeklyRewardTokens(rank);
                        if (tokens > 0 && rank > 0) {
                            Long tc = doc.getLong("tokenCount");
                            updates.put("tokenCount", (tc != null ? tc : 0) + tokens);
                            saveRewardNotification(uid, "nedeljnoj", rank, tokens, context);
                        }
                        if (needMonthQuery) {
                            db.collection("users").whereEqualTo("cycleMonth", storedMonth).get()
                                    .addOnSuccessListener(snapshots2 -> {
                                        int rank2 = computeUserRankFromSnapshots(snapshots2, "monthlyStars", uid);
                                        int tokens2 = getMonthlyRewardTokens(rank2);
                                        if (tokens2 > 0 && rank2 > 0) {
                                            long base = updates.containsKey("tokenCount")
                                                    ? ((Number) updates.get("tokenCount")).longValue()
                                                    : (doc.getLong("tokenCount") != null ? doc.getLong("tokenCount") : 0L);
                                            updates.put("tokenCount", base + tokens2);
                                            saveRewardNotification(uid, "mesečnoj", rank2, tokens2, context);
                                        }
                                        if (rank2 == 0 || rank2 > 3) {
                                            Long ts = doc.getLong("totalStars");
                                            int totalStars = ts != null ? ts.intValue() : 0;
                                            int penalised = LeagueManager.applyMonthlyPenalty(totalStars);
                                            updates.put("totalStars", (long) penalised);
                                        }
                                        applyAndFinish.run();
                                    })
                                    .addOnFailureListener(e -> applyAndFinish.run());
                        } else {
                            applyAndFinish.run();
                        }
                    })
                    .addOnFailureListener(e -> applyAndFinish.run());
        } else if (needMonthQuery) {
            db.collection("users").whereEqualTo("cycleMonth", storedMonth).get()
                    .addOnSuccessListener(snapshots -> {
                        int rank = computeUserRankFromSnapshots(snapshots, "monthlyStars", uid);
                        int tokens = getMonthlyRewardTokens(rank);
                        if (tokens > 0 && rank > 0) {
                            Long tc = doc.getLong("tokenCount");
                            updates.put("tokenCount", (tc != null ? tc : 0) + tokens);
                            saveRewardNotification(uid, "mesečnoj", rank, tokens, context);
                        }
                        if (rank == 0 || rank > 3) {
                            Long ts = doc.getLong("totalStars");
                            int totalStars = ts != null ? ts.intValue() : 0;
                            int penalised = LeagueManager.applyMonthlyPenalty(totalStars);
                            updates.put("totalStars", (long) penalised);
                        }
                        applyAndFinish.run();
                    })
                    .addOnFailureListener(e -> applyAndFinish.run());
        } else {
            applyAndFinish.run();
        }
    }

    private void applyUpdates(String uid, Map<String, Object> updates, Runnable onRewardReady) {
        if (!updates.isEmpty()) {
            db.collection("users").document(uid).update(updates)
                    .addOnSuccessListener(unused -> { if (onRewardReady != null) onRewardReady.run(); })
                    .addOnFailureListener(e -> { if (onRewardReady != null) onRewardReady.run(); });
        } else {
            if (onRewardReady != null) onRewardReady.run();
        }
    }

    private int computeUserRankFromSnapshots(com.google.firebase.firestore.QuerySnapshot snapshots, String starsField, String uid) {
        if (snapshots == null || snapshots.isEmpty()) return 0;
        List<Map.Entry<String, Integer>> scores = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshots) {
            Long val = doc.getLong(starsField);
            int s = val != null ? val.intValue() : 0;
            scores.add(new java.util.AbstractMap.SimpleEntry<>(doc.getId(), s));
        }
        scores.sort((a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i).getKey().equals(uid)) return i + 1;
        }
        return 0;
    }

    private int getWeeklyRewardTokens(int rank) {
        if (rank == 1) return 5;
        if (rank == 2) return 3;
        if (rank == 3) return 2;
        if (rank >= 4 && rank <= 10) return 1;
        return 0;
    }

    private int getMonthlyRewardTokens(int rank) {
        if (rank == 1) return 10;
        if (rank == 2) return 6;
        if (rank == 3) return 4;
        if (rank >= 4 && rank <= 10) return 2;
        return 0;
    }

    private void saveRewardNotification(String uid, String cycleType, int rank, int tokens, Context context) {
        String title = "Nagrada za plasman!";
        String body = "Osvojili ste " + rank + ". mesto na " + cycleType + " rang listi! Dobili ste " + tokens + " tokena!";
        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "rewards");
        notif.put("title", title);
        notif.put("body", body);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read", false);
        notif.put("rewardRank", rank);
        notif.put("rewardTokens", tokens);
        notif.put("cycleType", cycleType);
        db.collection("users").document(uid).collection("notifications").add(notif);
        if (context != null) {
            AppNotificationManager.show(context, SlagalicaApp.CHANNEL_RANKING, title, body);
        }
    }

    public LiveData<Map<String, Object>> getPendingReward() {
        MutableLiveData<Map<String, Object>> result = new MutableLiveData<>();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { result.setValue(null); return result; }

        db.collection("users").document(user.getUid()).collection("notifications")
                .whereEqualTo("type", "rewards")
                .whereEqualTo("read", false)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        var doc = snapshots.getDocuments().get(0);
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", doc.getId());
                        data.put("title", doc.getString("title"));
                        data.put("body", doc.getString("body"));
                        data.put("rank", doc.getLong("rewardRank"));
                        data.put("tokens", doc.getLong("rewardTokens"));
                        data.put("cycleType", doc.getString("cycleType"));
                        result.setValue(data);
                    } else {
                        result.setValue(null);
                    }
                })
                .addOnFailureListener(e -> result.setValue(null));

        return result;
    }

    public void markRewardNotifRead(String notifId) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || notifId == null) return;
        db.collection("users").document(user.getUid()).collection("notifications")
                .document(notifId).update("read", true);
    }
}