package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalicaapp.data.models.ProfileData;
import com.example.slagalicaapp.data.models.User;
import com.example.slagalicaapp.repositories.RegionRepository;
import com.example.slagalicaapp.model.GameResult;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.database.Query;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Firestore kolekcije sa rezultatima
    private static final String[] RESULT_COLLECTIONS = {
            "asocijacije_results",
            "ko_zna_zna_results",
            "skocko_results",
            "spojnice_results"
    };

    // ──────────────────────────────────────────────
    //  Registracija
    // ──────────────────────────────────────────────

    public LiveData<String> registerUser(User user) {
        MutableLiveData<String> result = new MutableLiveData<>();
        db.collection("users")
                .whereEqualTo("username", user.getUsername())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        result.setValue("Greška: Korisničko ime je već zauzeto.");
                    } else if (task.isSuccessful()) {
                        proceedWithAuthRegistration(user, result);
                    } else {
                        result.setValue("Greška pri proveri baze.");
                    }
                });
        return result;
    }

    public LiveData<String> registerGuestUser(User user) {
        MutableLiveData<String> result = new MutableLiveData<>();
        mAuth.signInAnonymously().addOnCompleteListener(authTask -> {
            if (authTask.isSuccessful() && mAuth.getCurrentUser() != null) {
                String uid = mAuth.getCurrentUser().getUid();
                db.collection("users").document(uid).get().addOnCompleteListener(dbTask -> {
                    if (dbTask.isSuccessful() && dbTask.getResult().exists()) {
                        result.setValue("GUEST_SUCCESS");
                    } else {
                        checkUsernameAndCreateGuest(user, uid, result);
                    }
                });
            } else {
                result.setValue("Greška pri anonimnoj prijavi.");
            }
        });
        return result;
    }

    private void checkUsernameAndCreateGuest(User user, String uid, MutableLiveData<String> result) {
        db.collection("users")
                .whereEqualTo("username", user.getUsername())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String newName = user.getUsername() + (int) (Math.random() * 100);
                        user.setUsername(newName);
                    }
                    saveUserToFirestore(user, uid, true, result);
                });
    }

    private void saveUserToFirestore(User user, String uid, boolean isGuest, MutableLiveData<String> result) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("username", user.getUsername());
        userData.put("region", user.getRegion());
        userData.put("isGuest", isGuest);
        String email = isGuest ? ("guest_" + uid + "@slagalica.app") : user.getEmail();
        userData.put("email", email);
        userData.put("avatarUri", "");
        userData.put("tokenCount", 0);
        userData.put("totalStars", 0);
        userData.put("totalGamesPlayed", 0);
        userData.put("wins", 0);
        userData.put("losses", 0);
        userData.put("qrPayload", buildQrPayload(uid, user.getUsername()));
        userData.put("averageScoreRanges", buildDefaultAverageScoreRanges());
        userData.put("detailedStats", buildDefaultDetailedStats());
        userData.put("leagueName", isGuest ? "Nema lige" : "Bronzana liga");

        // Region map position — random point within the player's region
        float[] mapCoords = RegionRepository.generateRandomMapCoords(user.getRegion());
        userData.put("mapX", (double) mapCoords[0]);
        userData.put("mapY", (double) mapCoords[1]);

        // Monthly cycle tracking
        userData.put("monthlyStars", 0L);
        userData.put("cycleMonth", "");
        userData.put("previousCycleRank", 0L);
        userData.put("previousMonthlyStars", 0L);
        userData.put("previousCycleMonth", "");

        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> result.setValue(isGuest ? "GUEST_SUCCESS" : "Registracija uspešna. Potvrdite mejl!"))
                .addOnFailureListener(e -> result.setValue("Greška pri upisu u bazu."));
    }

    private void proceedWithAuthRegistration(User user, MutableLiveData<String> result) {
        mAuth.createUserWithEmailAndPassword(user.getEmail(), user.getPassword())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser fUser = mAuth.getCurrentUser();
                        if (fUser != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(user.getUsername())
                                    .build();
                            fUser.updateProfile(profileUpdates).addOnCompleteListener(updateTask -> {
                                if (updateTask.isSuccessful()) {
                                    fUser.sendEmailVerification();
                                    saveUserToFirestore(user, fUser.getUid(), false, result);
                                } else {
                                    result.setValue("Greška pri ažuriranju profila.");
                                }
                            });
                        }
                    } else {
                        result.setValue("Greška: " + task.getException().getMessage());
                    }
                });
    }

    // ──────────────────────────────────────────────
    //  Login
    // ──────────────────────────────────────────────

    public LiveData<FirebaseUser> login(String identity, String password) {
        MutableLiveData<FirebaseUser> userLiveData = new MutableLiveData<>();
        if (identity.contains("@")) {
            performFirebaseLogin(identity, password, identity, userLiveData);
        } else {
            db.collection("users")
                    .whereEqualTo("username", identity)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            String email = task.getResult().getDocuments().get(0).getString("email");
                            performFirebaseLogin(email, password, identity, userLiveData);
                        } else {
                            userLiveData.setValue(null);
                        }
                    });
        }
        return userLiveData;
    }

    public LiveData<String> loginGuest(String guestName, String region) {
        User guestUser = new User();
        guestUser.setUsername(guestName);
        guestUser.setRegion(region);
        guestUser.setGuest(true);
        return registerGuestUser(guestUser);
    }

    private void performFirebaseLogin(String email, String password, String identity,
                                      MutableLiveData<FirebaseUser> liveData) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            if (user.getDisplayName() == null || user.getDisplayName().isEmpty()) {
                                String username = identity.contains("@") ? "" : identity;
                                if (!username.isEmpty()) {
                                    updateDisplayName(user, username, liveData);
                                } else {
                                    fetchUsernameFromDbAndSetDisplayName(user, liveData);
                                }
                            } else {
                                liveData.setValue(user);
                            }
                        } else {
                            mAuth.signOut();
                            liveData.setValue(null);
                        }
                    } else {
                        liveData.setValue(null);
                    }
                });
    }

    private void updateDisplayName(FirebaseUser user, String username,
                                   MutableLiveData<FirebaseUser> liveData) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build();
        user.updateProfile(profileUpdates).addOnCompleteListener(t -> liveData.setValue(user));
    }

    private void fetchUsernameFromDbAndSetDisplayName(FirebaseUser user,
                                                      MutableLiveData<FirebaseUser> liveData) {
        db.collection("users").document(user.getUid()).get().addOnCompleteListener(docTask -> {
            if (docTask.isSuccessful() && docTask.getResult() != null) {
                String username = docTask.getResult().getString("username");
                if (username != null && !username.isEmpty()) {
                    updateDisplayName(user, username, liveData);
                } else {
                    liveData.setValue(user);
                }
            } else {
                liveData.setValue(user);
            }
        });
    }

    // ──────────────────────────────────────────────
    //  Profil — učitavanje + računanje statistika
    // ──────────────────────────────────────────────

    public LiveData<ProfileData> loadCurrentUserProfile() {
        MutableLiveData<ProfileData> result = new MutableLiveData<>();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            result.setValue(null);
            return result;
        }

        String uid = currentUser.getUid();

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    ProfileData profileData = mapProfile(currentUser, documentSnapshot);
                    fetchAndComputeStats(uid, profileData, result);
                })
                .addOnFailureListener(e -> result.setValue(null));

        return result;
    }

    /**
     * Paralelno fetchuje Firestore kolekcije i RTDB sobe,
     * računa statistike i emituje finalni ProfileData.
     */
    private void fetchAndComputeStats(String uid, ProfileData profileData,
                                      MutableLiveData<ProfileData> result) {

        // ── Firestore: 4 kolekcije × 2 querija (player1 + player2) ──
        List<Task<QuerySnapshot>> firestoreTasks = new ArrayList<>();
        for (String collection : RESULT_COLLECTIONS) {
            firestoreTasks.add(db.collection(collection).whereEqualTo("player1Uid", uid).get());
            firestoreTasks.add(db.collection(collection).whereEqualTo("player2Uid", uid).get());
        }

        Tasks.whenAllSuccess(firestoreTasks).addOnSuccessListener(firestoreResults -> {

            // Grupišemo po gameType (lowercase)
            Map<String, List<GameResult>> byType = new HashMap<>();
            for (Object obj : firestoreResults) {
                QuerySnapshot snapshot = (QuerySnapshot) obj;
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    GameResult gr = doc.toObject(GameResult.class);
                    if (gr == null) continue;
                    String type = gr.gameType != null
                            ? gr.gameType.toLowerCase().trim() : "ostalo";
                    byType.computeIfAbsent(type, k -> new ArrayList<>()).add(gr);
                }
            }

            // ── RTDB: KORAK_PO_KORAK i MOJ_BROJ ──
            DatabaseReference rtdb = FirebaseDatabase.getInstance().getReference();

            Task<DataSnapshot> korakP1   = rtdbQuery(rtdb.child("rooms").child("KORAK_PO_KORAK")
                    .orderByChild("player1Uid").equalTo(uid));
            Task<DataSnapshot> korakP2   = rtdbQuery(rtdb.child("rooms").child("KORAK_PO_KORAK")
                    .orderByChild("player2Uid").equalTo(uid));
            Task<DataSnapshot> mojBrojP1 = rtdbQuery(rtdb.child("rooms").child("MOJ_BROJ")
                    .orderByChild("player1Uid").equalTo(uid));
            Task<DataSnapshot> mojBrojP2 = rtdbQuery(rtdb.child("rooms").child("MOJ_BROJ")
                    .orderByChild("player2Uid").equalTo(uid));

            Tasks.whenAllSuccess(korakP1, korakP2, mojBrojP1, mojBrojP2)
                    .addOnSuccessListener(rtdbResults -> {

                        for (int i = 0; i < rtdbResults.size(); i++) {
                            DataSnapshot snap = (DataSnapshot) rtdbResults.get(i);
                            String label = i == 0 ? "korakP1" : i == 1 ? "korakP2"
                                                                : i == 2 ? "mojBrojP1" : "mojBrojP2";
                            android.util.Log.d("STATS_DEBUG",
                                    label + " → count: " + snap.getChildrenCount());
                            for (DataSnapshot room : snap.getChildren()) {
                                android.util.Log.d("STATS_DEBUG",
                                        label + " room: " + room.getKey()
                                                + " | status: " + room.child("status").getValue(String.class)
                                                + " | p1Uid: " + room.child("player1Uid").getValue(String.class)
                                                + " | p2Uid: " + room.child("player2Uid").getValue(String.class));
                            }
                        }

                        List<GameResult> korakGames   = new ArrayList<>();
                        List<GameResult> mojBrojGames = new ArrayList<>();

                        for (int i = 0; i < rtdbResults.size(); i++) {
                            DataSnapshot snap     = (DataSnapshot) rtdbResults.get(i);
                            String gameType       = i < 2 ? "KORAK_PO_KORAK" : "MOJ_BROJ";
                            List<GameResult> target = i < 2 ? korakGames : mojBrojGames;

                            for (DataSnapshot room : snap.getChildren()) {
                                GameResult gr = parseRtdbRoom(room, gameType);
                                if (gr != null) target.add(gr);
                            }
                        }

                        byType.put("korak_po_korak", korakGames);
                        byType.put("moj_broj", mojBrojGames);

                        finalizeStats(uid, profileData, byType, result);

                    })
                    .addOnFailureListener(e -> {
                        // RTDB nije uspio — nastavljamo samo sa Firestore podacima
                        finalizeStats(uid, profileData, byType, result);
                    });

        }).addOnFailureListener(e -> result.setValue(profileData));
    }

    private Task<DataSnapshot> rtdbQuery(Query query) {
        com.google.android.gms.tasks.TaskCompletionSource<DataSnapshot> tcs =
                new com.google.android.gms.tasks.TaskCompletionSource<>();
        query.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tcs.setResult(snapshot);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tcs.setException(error.toException());
            }
        });
        return tcs.getTask();
    }

    /**
     * Parsira jedan RTDB room node u GameResult.
     * Prihvata završene sobe: status == "finished" | "forfeit" | winner != null.
     */
    private GameResult parseRtdbRoom(DataSnapshot room, String gameType) {
        String status = room.child("status").getValue(String.class);
        String winner = room.child("winner").getValue(String.class);

        //boolean stillActive = "waiting".equals(status) || "playing".equals(status);
        //if (stillActive) return null;

        String player1Uid  = room.child("player1Uid").getValue(String.class);
        String player2Uid  = room.child("player2Uid").getValue(String.class);
        String player1Name = room.child("player1").getValue(String.class);
        String player2Name = room.child("player2").getValue(String.class);

        int p1Score = readRtdbInt(room.child("scores").child("player1").getValue());
        int p2Score = readRtdbInt(room.child("scores").child("player2").getValue());

        Long startedAt  = room.child("startedAt").getValue(Long.class);
        Long finishedAt = room.child("finishedAt").getValue(Long.class);
        long now        = System.currentTimeMillis();
        long duration   = (startedAt != null && finishedAt != null)
                ? Math.max(0, finishedAt - startedAt) : 0L;

        String winnerUid;
        if ("player1".equalsIgnoreCase(winner)) {
            winnerUid = player1Uid;
        } else if ("player2".equalsIgnoreCase(winner)) {
            winnerUid = player2Uid;
        } else if (p1Score > p2Score) {
            winnerUid = player1Uid;
        } else if (p2Score > p1Score) {
            winnerUid = player2Uid;
        } else {
            winnerUid = null;
        }

        int rounds = readRtdbInt(room.child("currentRound").getValue());

        return new GameResult(
                gameType,
                player1Uid, player1Name, p1Score,
                player2Uid, player2Name, p2Score,
                winnerUid,
                finishedAt != null ? finishedAt : now,
                duration,
                rounds
        );
    }

    /**
     * Završava računanje: popunjava ProfileData i emituje rezultat.
     */
    private void finalizeStats(String uid, ProfileData profileData,
                               Map<String, List<GameResult>> byType,
                               MutableLiveData<ProfileData> result) {

        profileData.setAverageScoreRanges(computeAverageScoreRanges(uid, byType));
        profileData.setDetailedStats(computeDetailedStats(uid, byType));

        int totalGames = 0, wins = 0, losses = 0;
        for (List<GameResult> games : byType.values()) {
            for (GameResult gr : games) {
                totalGames++;
                if (uid.equals(gr.winnerUid)) wins++;
                else losses++;
            }
        }

        profileData.setTotalGamesPlayed(totalGames);
        profileData.setWins(wins);
        profileData.setLosses(losses);

        persistComputedStats(uid, profileData);
        result.setValue(profileData);
    }

    // ──────────────────────────────────────────────
    //  Računanje statistika
    // ──────────────────────────────────────────────

    private Map<String, String> computeAverageScoreRanges(String uid,
                                                          Map<String, List<GameResult>> byType) {
        Map<String, String> ranges = new LinkedHashMap<>();

        String[] displayNames = {
                "Ko zna zna", "Asocijacije", "Skočko", "Spojnice", "Korak po korak", "Moj broj"
        };
        String[] typeKeys = {
                "ko_zna_zna", "asocijacije", "skocko", "spojnice", "korak_po_korak", "moj_broj"
        };

        for (int i = 0; i < typeKeys.length; i++) {
            List<GameResult> games = byType.get(typeKeys[i]);

            if (games == null || games.isEmpty()) {
                ranges.put(displayNames[i], "Nema podataka");
                continue;
            }

            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0;
            for (GameResult gr : games) {
                int score = uid.equals(gr.player1Uid) ? gr.player1Score : gr.player2Score;
                if (score < min) min = score;
                if (score > max) max = score;
                sum += score;
            }

            int avg = sum / games.size();
            ranges.put(displayNames[i],
                    "Avg: " + avg + " (min " + min + " – max " + max + ")");
        }

        return ranges;
    }

    private Map<String, String> computeDetailedStats(String uid,
                                                     Map<String, List<GameResult>> byType) {
        Map<String, String> stats = new LinkedHashMap<>();

        // ── Ko zna zna ──
        List<GameResult> kzz = byType.getOrDefault("ko_zna_zna", new ArrayList<>());
        if (!kzz.isEmpty()) {
            int totalGames = kzz.size();
            int totalScore = 0, maxPossibleScore = 0;
            for (GameResult gr : kzz) {
                int myScore  = uid.equals(gr.player1Uid) ? gr.player1Score : gr.player2Score;
                int oppScore = uid.equals(gr.player1Uid) ? gr.player2Score : gr.player1Score;
                totalScore += myScore;
                // Maksimalan mogući skor u partiji = zbir oba skora (ukupno dostupnih poena)
                maxPossibleScore += (myScore + oppScore);
            }
            int hitPct = maxPossibleScore > 0 ? (totalScore * 100 / maxPossibleScore) : 0;
            stats.put("Ko zna zna",
                    "Osvojeno " + hitPct + "% / protivnik " + (100 - hitPct) + "%");
        } else {
            stats.put("Ko zna zna", "Nema podataka");
        }

        // ── Asocijacije ──
        List<GameResult> asoc = byType.getOrDefault("asocijacije", new ArrayList<>());
        if (!asoc.isEmpty()) {
            int solved = 0;
            for (GameResult gr : asoc) {
                int score = uid.equals(gr.player1Uid) ? gr.player1Score : gr.player2Score;
                if (score > 0) solved++;
            }
            int pct = solved * 100 / asoc.size();
            stats.put("Asocijacije",
                    "Rešene " + pct + "% / nerešene " + (100 - pct) + "%");
        } else {
            stats.put("Asocijacije", "Nema podataka");
        }

        // ── Skočko ──
        List<GameResult> skocko = byType.getOrDefault("skocko", new ArrayList<>());
        if (!skocko.isEmpty()) {
            int solved = 0;
            for (GameResult gr : skocko) {
                int score = uid.equals(gr.player1Uid) ? gr.player1Score : gr.player2Score;
                if (score > 0) solved++;
            }
            int pct = solved * 100 / skocko.size();
            stats.put("Skočko", "Kombinacija pogođena u " + pct + "% pokušaja");
        } else {
            stats.put("Skočko", "Nema podataka");
        }

        // ── Spojnice ──
        List<GameResult> spoj = byType.getOrDefault("spojnice", new ArrayList<>());
        if (!spoj.isEmpty()) {
            int totalQ = 0, connected = 0;
            for (GameResult gr : spoj) {
                totalQ += 5; //5 spojnica po partiji
                int score = uid.equals(gr.player1Uid) ? gr.player1Score : gr.player2Score;
                connected += score / 2; // prilagodi prema poenu po spojnici
            }
            int pct = totalQ > 0 ? (connected * 100 / totalQ) : 0;
            stats.put("Spojnice", "Povezano " + pct + "% pojmova");
        } else {
            stats.put("Spojnice", "Nema podataka");
        }

        // ── Korak po korak ──
        List<GameResult> korak = byType.getOrDefault("korak_po_korak", new ArrayList<>());
        if (!korak.isEmpty()) {
            int solved = 0, totalScore = 0;
            for (GameResult gr : korak) {
                int score = uid.equals(gr.player1Uid) ? gr.player1Score : gr.player2Score;
                totalScore += score;
                if (score > 0) solved++;
            }
            int pct    = solved * 100 / korak.size();
            int avg    = totalScore / korak.size();
            stats.put("Korak po korak",
                    "Riješeno " + pct + "% rundi, avg skor: " + avg);
        } else {
            stats.put("Korak po korak", "Nema podataka");
        }

        // ── Moj broj ──
        List<GameResult> mojBroj = byType.getOrDefault("moj_broj", new ArrayList<>());
        if (!mojBroj.isEmpty()) {
            int exact = 0, totalScore = 0;
            for (GameResult gr : mojBroj) {
                int score = uid.equals(gr.player1Uid) ? gr.player1Score : gr.player2Score;
                totalScore += score;
                if (score > 0) exact++;
            }
            int pct = exact * 100 / mojBroj.size();
            int avg = totalScore / mojBroj.size();
            stats.put("Moj broj",
                    "Tačan broj pronađen u " + pct + "% partija, avg: " + avg);
        } else {
            stats.put("Moj broj", "Nema podataka");
        }

        // ── Opšte statistike ──
        int totalGames = byType.values().stream().mapToInt(List::size).sum();
        int wins = 0;
        for (List<GameResult> games : byType.values()) {
            for (GameResult gr : games) {
                if (uid.equals(gr.winnerUid)) wins++;
            }
        }

        stats.put("Ukupno odigranih partija", String.valueOf(totalGames));
        if (totalGames > 0) {
            int winPct  = wins * 100 / totalGames;
            stats.put("Pobeđene / izgubljene partije",
                    winPct + "% / " + (100 - winPct) + "%");
        } else {
            stats.put("Pobeđene / izgubljene partije", "0% / 0%");
        }

        return stats;
    }

    /**
     * Snima izračunate statistike nazad u Firestore (fire-and-forget).
     */
    private void persistComputedStats(String uid, ProfileData profileData) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("averageScoreRanges", profileData.getAverageScoreRanges());
        updates.put("detailedStats", profileData.getDetailedStats());
        updates.put("totalGamesPlayed", profileData.getTotalGamesPlayed());
        updates.put("wins", profileData.getWins());
        updates.put("losses", profileData.getLosses());

        db.collection("users").document(uid).update(updates);
    }

    // ──────────────────────────────────────────────
    //  Avatar i lozinka
    // ──────────────────────────────────────────────

    public LiveData<String> updateAvatarUri(String avatarUri) {
        MutableLiveData<String> result = new MutableLiveData<>();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            result.setValue("Niste prijavljeni.");
            return result;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("avatarUri", avatarUri);

        db.collection("users").document(currentUser.getUid())
                .update(update)
                .addOnSuccessListener(unused -> result.setValue("Avatar je uspešno ažuriran."))
                .addOnFailureListener(e -> result.setValue("Neuspešno ažuriranje avatara."));

        return result;
    }

    public void logout() {
        mAuth.signOut();
    }

    public LiveData<String> changePassword(String oldPass, String newPass) {
        MutableLiveData<String> result = new MutableLiveData<>();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null && user.getEmail() != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPass);
            user.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
                if (reAuthTask.isSuccessful()) {
                    user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                        if (updateTask.isSuccessful()) {
                            result.setValue("Lozinka uspešno promenjena.");
                        } else {
                            result.setValue("Greška pri ažuriranju.");
                        }
                    });
                } else {
                    result.setValue("Stara lozinka nije ispravna.");
                }
            });
        }
        return result;
    }

    // ──────────────────────────────────────────────
    //  Pomoćni metodi
    // ──────────────────────────────────────────────

    private ProfileData mapProfile(FirebaseUser currentUser, DocumentSnapshot doc) {
        ProfileData profileData = new ProfileData();
        profileData.setUid(currentUser.getUid());
        profileData.setUsername(stringValue(doc.getString("username"), "Igrač"));
        profileData.setEmail(stringValue(doc.getString("email"), currentUser.getEmail()));
        profileData.setRegion(stringValue(doc.getString("region"), "Nepoznat region"));
        profileData.setAvatarUri(stringValue(doc.getString("avatarUri"), ""));
        profileData.setLeagueName(stringValue(doc.getString("leagueName"), "Bronzana liga"));
        profileData.setQrPayload(stringValue(doc.getString("qrPayload"),
                buildQrPayload(currentUser.getUid(), profileData.getUsername())));
        profileData.setTokenCount(readInt(doc.get("tokenCount"), 0));
        profileData.setTotalStars(readInt(doc.get("totalStars"), 0));
        profileData.setTotalGamesPlayed(readInt(doc.get("totalGamesPlayed"), 0));
        profileData.setWins(readInt(doc.get("wins"), 0));
        profileData.setLosses(readInt(doc.get("losses"), 0));
        profileData.setPreviousCycleRank(readInt(doc.get("previousCycleRank"), 0));
        profileData.setAverageScoreRanges(
                readMap(doc.get("averageScoreRanges"), buildDefaultAverageScoreRanges()));
        profileData.setDetailedStats(
                readMap(doc.get("detailedStats"), buildDefaultDetailedStats()));
        return profileData;
    }

    private String stringValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }

    private int readRtdbInt(Object value) {
        if (value instanceof Long)    return ((Long) value).intValue();
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private Map<String, String> readMap(Object value, Map<String, String> fallback) {
        if (value instanceof Map<?, ?>) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return result.isEmpty() ? new LinkedHashMap<>(fallback) : result;
        }
        return new LinkedHashMap<>(fallback);
    }

    private Map<String, String> buildDefaultAverageScoreRanges() {
        Map<String, String> ranges = new LinkedHashMap<>();
        ranges.put("Ko zna zna",      "20–50 poena");
        ranges.put("Moj broj",        "10–30 poena");
        ranges.put("Korak po korak",  "15–40 poena");
        ranges.put("Asocijacije",     "25–60 poena");
        ranges.put("Skočko",          "10–25 poena");
        ranges.put("Spojnice",        "15–35 poena");
        return ranges;
    }

    private Map<String, String> buildDefaultDetailedStats() {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Ko zna zna",                    "Pogođeno 68% / promašeno 32%");
        stats.put("Moj broj",                      "Tačan broj pronađen u 54% partija");
        stats.put("Korak po korak",                "Riješeno 0% rundi, avg skor: 0");
        stats.put("Asocijacije",                   "Rešene 46% / nerešene 54%");
        stats.put("Skočko",                        "Kombinacija pogođena u 38% pokušaja");
        stats.put("Spojnice",                      "Povezano 72% pojmova");
        stats.put("Ukupno odigranih partija",      "0");
        stats.put("Pobeđene / izgubljene partije", "0% / 0%");
        return stats;
    }

    private String buildQrPayload(String uid, String username) {
        return "slagalica://invite?uid=" + uid + "&username=" + username;
    }
}