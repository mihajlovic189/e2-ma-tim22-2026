package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalicaapp.data.models.PlayerLeaderboardEntry;
import com.example.slagalicaapp.data.models.PlayerMapDot;
import com.example.slagalicaapp.data.models.RegionData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegionRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    public static final String[] ALL_REGIONS = {
            "Beograd", "Vojvodina", "Centralna Srbija", "Južna Srbija"
    };

    // Icon for each region
    private static final Map<String, String> REGION_ICONS = new HashMap<>();
    static {
        REGION_ICONS.put("Beograd", "🏙");
        REGION_ICONS.put("Vojvodina", "🌾");
        REGION_ICONS.put("Centralna Srbija", "⛰");
        REGION_ICONS.put("Južna Srbija", "🌊");
    }

    // Random dot placement bounds per region [minX, maxX, minY, maxY] in internal 100×120 space
    private static final Map<String, float[]> REGION_DOT_BOUNDS = new HashMap<>();
    static {
        REGION_DOT_BOUNDS.put("Vojvodina",        new float[]{5f, 70f,  2f, 27f});
        REGION_DOT_BOUNDS.put("Beograd",          new float[]{40f, 53f, 37f, 44f});
        REGION_DOT_BOUNDS.put("Centralna Srbija", new float[]{5f, 88f, 32f, 62f});
        REGION_DOT_BOUNDS.put("Južna Srbija",     new float[]{10f, 85f, 66f, 98f});
    }

    public static String getIconForRegion(String region) {
        return REGION_ICONS.getOrDefault(region, "⭐");
    }

    public String getCurrentMonth() {
        return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
    }

    private String getPreviousMonth() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.getTime());
    }

    // ── Monthly leaderboard ──────────────────────────────────────────────────

    public LiveData<List<RegionData>> getMonthlyLeaderboard() {
        MutableLiveData<List<RegionData>> result = new MutableLiveData<>();
        String currentMonth = getCurrentMonth();
        FirebaseUser user = mAuth.getCurrentUser();
        String currentUid = user != null ? user.getUid() : "";

        db.collection("users").get().addOnSuccessListener(snapshots -> {
            Map<String, Integer> starsMap = initRegionIntMap();
            Map<String, Integer> totalMap  = initRegionIntMap();
            Map<String, Integer> activeMap = initRegionIntMap();
            String[] userRegion = {""};

            for (QueryDocumentSnapshot doc : snapshots) {
                String region = doc.getString("region");
                if (region == null || !starsMap.containsKey(region)) continue;

                if (doc.getId().equals(currentUid)) userRegion[0] = region;

                totalMap.put(region, totalMap.get(region) + 1);

                Long games = doc.getLong("totalGamesPlayed");
                if (games != null && games > 0) {
                    activeMap.put(region, activeMap.get(region) + 1);
                }

                String cycleMonth = doc.getString("cycleMonth");
                if (currentMonth.equals(cycleMonth)) {
                    Long ms = doc.getLong("monthlyStars");
                    int stars = ms != null ? ms.intValue() : 0;
                    starsMap.put(region, starsMap.get(region) + stars);
                }
            }

            List<RegionData> list = new ArrayList<>();
            for (String r : ALL_REGIONS) {
                RegionData rd = new RegionData(r, getIconForRegion(r));
                rd.setMonthlyStars(starsMap.getOrDefault(r, 0));
                rd.setTotalPlayerCount(totalMap.getOrDefault(r, 0));
                rd.setActivePlayerCount(activeMap.getOrDefault(r, 0));
                rd.setCurrentPlayerRegion(r.equals(userRegion[0]));
                list.add(rd);
            }

            list.sort((a, b) -> b.getMonthlyStars() - a.getMonthlyStars());
            for (int i = 0; i < list.size(); i++) list.get(i).setRank(i + 1);

            loadPlaceCounts(list, result);
        }).addOnFailureListener(e -> result.setValue(new ArrayList<>()));

        return result;
    }

    private void loadPlaceCounts(List<RegionData> list, MutableLiveData<List<RegionData>> result) {
        int[] remaining = {list.size()};
        for (RegionData rd : list) {
            db.collection("region_stats").document(rd.getName()).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            Long f1 = task.getResult().getLong("firstPlaces");
                            Long f2 = task.getResult().getLong("secondPlaces");
                            Long f3 = task.getResult().getLong("thirdPlaces");
                            rd.setFirstPlaces(f1 != null ? f1.intValue() : 0);
                            rd.setSecondPlaces(f2 != null ? f2.intValue() : 0);
                            rd.setThirdPlaces(f3 != null ? f3.intValue() : 0);
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) result.setValue(list);
                    });
        }
    }

    // ── Region stats (for click dialog) ─────────────────────────────────────

    public LiveData<RegionData> getRegionStats(String regionName) {
        MutableLiveData<RegionData> result = new MutableLiveData<>();
        RegionData rd = new RegionData(regionName, getIconForRegion(regionName));

        db.collection("users").whereEqualTo("region", regionName).get()
                .addOnSuccessListener(snapshots -> {
                    int total = 0, active = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        total++;
                        Long games = doc.getLong("totalGamesPlayed");
                        if (games != null && games > 0) active++;
                    }
                    rd.setTotalPlayerCount(total);
                    rd.setActivePlayerCount(active);

                    db.collection("region_stats").document(regionName).get()
                            .addOnSuccessListener(statsDoc -> {
                                if (statsDoc.exists()) {
                                    Long f1 = statsDoc.getLong("firstPlaces");
                                    Long f2 = statsDoc.getLong("secondPlaces");
                                    Long f3 = statsDoc.getLong("thirdPlaces");
                                    rd.setFirstPlaces(f1 != null ? f1.intValue() : 0);
                                    rd.setSecondPlaces(f2 != null ? f2.intValue() : 0);
                                    rd.setThirdPlaces(f3 != null ? f3.intValue() : 0);
                                }
                                result.setValue(rd);
                            })
                            .addOnFailureListener(e -> result.setValue(rd));
                })
                .addOnFailureListener(e -> result.setValue(rd));

        return result;
    }

    // ── Per-region player leaderboard ───────────────────────────────────────

    public LiveData<List<PlayerLeaderboardEntry>> getRegionPlayerLeaderboard(String region) {
        MutableLiveData<List<PlayerLeaderboardEntry>> result = new MutableLiveData<>();
        String currentMonth = getCurrentMonth();
        FirebaseUser user = mAuth.getCurrentUser();
        String currentUid = user != null ? user.getUid() : "";

        db.collection("users").whereEqualTo("region", region).get()
                .addOnSuccessListener(snapshots -> {
                    List<PlayerLeaderboardEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String username = doc.getString("username");
                        if (username == null) continue;

                        int stars = 0;
                        String cycleMonth = doc.getString("cycleMonth");
                        if (currentMonth.equals(cycleMonth)) {
                            Long ms = doc.getLong("monthlyStars");
                            stars = ms != null ? ms.intValue() : 0;
                        }

                        boolean isCurrentUser = doc.getId().equals(currentUid);
                        entries.add(new PlayerLeaderboardEntry(username, stars, isCurrentUser));
                    }
                    entries.sort((a, b) -> b.getMonthlyStars() - a.getMonthlyStars());
                    for (int i = 0; i < entries.size(); i++) entries.get(i).setRank(i + 1);
                    result.setValue(entries);
                })
                .addOnFailureListener(e -> result.setValue(new ArrayList<>()));

        return result;
    }

    // ── Player dots for map ──────────────────────────────────────────────────

    public LiveData<List<PlayerMapDot>> getPlayerDots() {
        MutableLiveData<List<PlayerMapDot>> result = new MutableLiveData<>();

        db.collection("users").get().addOnSuccessListener(snapshots -> {
            List<PlayerMapDot> dots = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshots) {
                String region = doc.getString("region");
                Double mapX = doc.getDouble("mapX");
                Double mapY = doc.getDouble("mapY");
                if (region != null && mapX != null && mapY != null) {
                    dots.add(new PlayerMapDot(mapX.floatValue(), mapY.floatValue(), region));
                }
            }
            result.setValue(dots);
        }).addOnFailureListener(e -> result.setValue(new ArrayList<>()));

        return result;
    }

    // ── Cycle management ─────────────────────────────────────────────────────

    /**
     * Checks if the current user is in a new monthly cycle and resets their stars,
     * saving the previous cycle's rank.
     */
    public void checkAndUpdateCycleForCurrentUser() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentMonth = getCurrentMonth();

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String cycleMonth = doc.getString("cycleMonth");
                    if (cycleMonth == null || cycleMonth.isEmpty()) {
                        // First time — initialise
                        Map<String, Object> init = new HashMap<>();
                        init.put("monthlyStars", 0L);
                        init.put("cycleMonth", currentMonth);
                        init.put("previousCycleRank", 0L);
                        init.put("previousMonthlyStars", 0L);
                        init.put("previousCycleMonth", "");
                        db.collection("users").document(user.getUid()).update(init);
                    } else if (!cycleMonth.equals(currentMonth)) {
                        // New cycle started: save previous data, then reset
                        String oldMonth = cycleMonth;
                        Long oldStars = doc.getLong("monthlyStars");
                        String userRegion = doc.getString("region");

                        Map<String, Object> rollover = new HashMap<>();
                        rollover.put("previousMonthlyStars", oldStars != null ? oldStars : 0L);
                        rollover.put("previousCycleMonth", oldMonth);
                        rollover.put("monthlyStars", 0L);
                        rollover.put("cycleMonth", currentMonth);

                        db.collection("users").document(user.getUid()).update(rollover)
                                .addOnSuccessListener(unused ->
                                        computeAndSaveRank(oldMonth, user.getUid(), userRegion));
                    }
                });
    }

    private void computeAndSaveRank(String cycleMonth, String uid, String userRegion) {
        db.collection("users").get().addOnSuccessListener(snapshots -> {
            Map<String, Integer> starsMap = initRegionIntMap();

            for (QueryDocumentSnapshot doc : snapshots) {
                String region = doc.getString("region");
                if (region == null || !starsMap.containsKey(region)) continue;
                // Use previousMonthlyStars where previousCycleMonth == cycleMonth
                String prevMonth = doc.getString("previousCycleMonth");
                if (cycleMonth.equals(prevMonth)) {
                    Long ps = doc.getLong("previousMonthlyStars");
                    int s = ps != null ? ps.intValue() : 0;
                    starsMap.put(region, starsMap.get(region) + s);
                }
            }

            List<String> sorted = new ArrayList<>(starsMap.keySet());
            sorted.sort((a, b) -> starsMap.get(b) - starsMap.get(a));

            int rank = 0;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).equals(userRegion)) { rank = i + 1; break; }
            }

            db.collection("users").document(uid).update("previousCycleRank", rank);

            // Update cumulative place counts for top 3 regions
            String[] placeFields = {"firstPlaces", "secondPlaces", "thirdPlaces"};
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                String region = sorted.get(i);
                String field = placeFields[i];
                db.collection("region_stats").document(region).get()
                        .addOnSuccessListener(doc -> {
                            long cur = doc.exists() && doc.getLong(field) != null
                                    ? doc.getLong(field) : 0L;
                            Map<String, Object> upd = new HashMap<>();
                            upd.put(field, cur + 1);
                            db.collection("region_stats").document(region)
                                    .set(upd, SetOptions.merge());
                        });
            }
        });
    }

    // ── Generate random map coordinates ─────────────────────────────────────

    /** Returns [x, y] in the internal 100×120 map space. */
    public static float[] generateRandomMapCoords(String region) {
        float[] b = REGION_DOT_BOUNDS.get(region);
        if (b == null) b = new float[]{10f, 90f, 10f, 110f};
        float x = b[0] + (float) Math.random() * (b[1] - b[0]);
        float y = b[2] + (float) Math.random() * (b[3] - b[2]);
        return new float[]{x, y};
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Map<String, Integer> initRegionIntMap() {
        Map<String, Integer> map = new HashMap<>();
        for (String r : ALL_REGIONS) map.put(r, 0);
        return map;
    }
}
