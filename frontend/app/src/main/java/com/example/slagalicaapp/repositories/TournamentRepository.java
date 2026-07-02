package com.example.slagalicaapp.repositories;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalicaapp.data.firebase.MatchmakingManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TournamentRepository {

    private static final String TAG = "TournamentRepo";

    public interface JoinCallback {
        void onWaiting(int playerCount);
        void onStarted(String tournamentId, String mySlot);
        void onError(String message);
    }

    public interface TournamentListener {
        void onUpdate(DataSnapshot snapshot);
        void onError(String message);
    }

    public interface ResultCallback {
        void onDone();
        void onError(String message);
    }

    private final DatabaseReference db;
    private final FirebaseFirestore firestore;

    private ValueEventListener lobbyCountListener;
    private DatabaseReference lobbyCountRef;
    private ValueEventListener waitingListener;
    private DatabaseReference waitingRef;
    private ValueEventListener tournamentStateListener;
    private DatabaseReference tournamentRef;

    public TournamentRepository() {
        this.db = FirebaseDatabase.getInstance().getReference();
        this.firestore = FirebaseFirestore.getInstance();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void joinTournament(String uid, String username, String avatarUri,
                                String leagueIcon, JoinCallback callback) {
        firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap =
                    transaction.get(firestore.collection("users").document(uid));
            Long tokens = snap.getLong("tokenCount");
            if (tokens == null || tokens < 3) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        "Nemate dovoljno tokena! Potrebna su 3 tokena za turnir.",
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED);
            }
            transaction.update(firestore.collection("users").document(uid),
                    "tokenCount", tokens - 3);
            return null;
        }).addOnSuccessListener(unused ->
                addToLobby(uid, username, avatarUri, leagueIcon, callback)
        ).addOnFailureListener(e ->
                callback.onError(e.getMessage() != null ? e.getMessage()
                        : "Greška pri plaćanju ulaznice.")
        );
    }

    public void cancelJoin(String uid) {
        stopLobbyListeners();
        db.child("activeTournament").child("players").child(uid)
                .onDisconnect().cancel();
        db.child("activeTournament").child("players").child(uid)
                .removeValue()
                .addOnSuccessListener(unused -> refundTokens(uid, 3));
        if (waitingRef != null && waitingListener != null) {
            waitingRef.removeEventListener(waitingListener);
            waitingRef.removeValue();
            waitingListener = null;
            waitingRef = null;
        }
    }

    public void listenToTournament(String tournamentId, TournamentListener listener) {
        stopTournamentListener();
        tournamentRef = db.child("tournaments").child(tournamentId);
        tournamentStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onUpdate(snapshot);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        tournamentRef.addValueEventListener(tournamentStateListener);
    }

    public void stopTournamentListener() {
        if (tournamentRef != null && tournamentStateListener != null) {
            tournamentRef.removeEventListener(tournamentStateListener);
            tournamentStateListener = null;
            tournamentRef = null;
        }
    }

    // Called from GameActivity after a tournament match ends
    public void reportMatchResult(String tournamentId, String round, String winnerUid,
                                   String myUid, int myScore, boolean iWon,
                                   ResultCallback callback) {
        if ("final".equals(round)) {
            db.child("tournaments").child(tournamentId).child("finalWinnerUid")
                    .setValue(winnerUid, (error, ref) -> {
                        db.child("tournaments").child(tournamentId).child("status")
                                .setValue("done");
                        applyFinalRewards(myUid, myScore, iWon, callback);
                    });
            return;
        }

        String winnerField = "semi1".equals(round) ? "semi1WinnerUid" : "semi2WinnerUid";
        db.child("tournaments").child(tournamentId).child(winnerField)
                .setValue(winnerUid, (error, ref) ->
                        applySemiRewards(myUid, myScore, iWon, () ->
                                checkAndCreateFinal(tournamentId, callback)));
    }

    // ── Lobby ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void addToLobby(String uid, String username, String avatarUri,
                             String leagueIcon, JoinCallback callback) {
        DatabaseReference lobbyRef = db.child("activeTournament");

        Map<String, Object> myData = new HashMap<>();
        myData.put("username", username != null ? username : "Igrač");
        myData.put("avatarUri", avatarUri != null ? avatarUri : "");
        myData.put("leagueIcon", leagueIcon != null ? leagueIcon : "");

        lobbyRef.runTransaction(new Transaction.Handler() {
            @Override
            @NonNull
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Map<String, Object> lobby = (Map<String, Object>) currentData.getValue();

                String tournamentId;
                Map<String, Object> players;

                if (lobby == null) {
                    tournamentId = db.child("tournaments").push().getKey();
                    players = new HashMap<>();
                } else {
                    tournamentId = (String) lobby.get("tournamentId");
                    Object raw = lobby.get("players");
                    players = raw instanceof Map
                            ? new HashMap<>((Map<String, Object>) raw)
                            : new HashMap<>();
                    if (players.size() >= 4) return Transaction.abort();
                    if (players.containsKey(uid)) return Transaction.abort();
                }

                players.put(uid, myData);

                Map<String, Object> newLobby = new HashMap<>();
                newLobby.put("tournamentId", tournamentId);
                newLobby.put("players", players);
                currentData.setValue(newLobby);
                return Transaction.success(currentData);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (!committed || error != null || snapshot == null) {
                    refundTokens(uid, 3);
                    callback.onError("Nije moguće pridružiti se. Pokušajte ponovo.");
                    return;
                }

                Map<String, Object> lobby = (Map<String, Object>) snapshot.getValue();
                if (lobby == null) {
                    refundTokens(uid, 3);
                    callback.onError("Greška lobby-a.");
                    return;
                }

                Map<String, Object> players = (Map<String, Object>) lobby.get("players");
                String tournamentId = (String) lobby.get("tournamentId");
                int count = players != null ? players.size() : 0;

                // Remove player slot if this device disconnects while in the lobby
                db.child("activeTournament").child("players").child(uid)
                        .onDisconnect().removeValue();

                if (count == 4 && players != null) {
                    organizeTournament(tournamentId, players, uid, callback);
                } else {
                    callback.onWaiting(count);
                    watchLobbyCount(callback);
                    watchForStart(uid, callback);
                }
            }
        });
    }

    private void watchLobbyCount(JoinCallback callback) {
        lobbyCountRef = db.child("activeTournament").child("players");
        lobbyCountListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (count > 0) callback.onWaiting((int) count);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        lobbyCountRef.addValueEventListener(lobbyCountListener);
    }

    private void watchForStart(String uid, JoinCallback callback) {
        waitingRef = db.child("tournamentWaiting").child(uid);
        waitingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String tid = snapshot.child("tournamentId").getValue(String.class);
                String slot = snapshot.child("slot").getValue(String.class);
                if (tid == null || slot == null) return;

                snapshot.getRef().removeValue();
                stopLobbyListeners();
                db.child("activeTournament").child("players").child(uid)
                        .onDisconnect().cancel();
                callback.onStarted(tid, slot);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };
        waitingRef.addValueEventListener(waitingListener);
    }

    // ── Tournament organisation ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void organizeTournament(String tournamentId, Map<String, Object> rawPlayers,
                                    String organizerUid, JoinCallback callback) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>(rawPlayers.entrySet());
        Collections.shuffle(entries);

        String[] slotKeys = {"p1", "p2", "p3", "p4"};
        Map<String, Map<String, Object>> slotted = new HashMap<>();
        String mySlot = "p4";

        for (int i = 0; i < 4 && i < entries.size(); i++) {
            String entryUid = entries.get(i).getKey();
            Map<String, Object> data = new HashMap<>();
            Object raw = entries.get(i).getValue();
            if (raw instanceof Map) data.putAll((Map<String, Object>) raw);
            data.put("uid", entryUid);
            slotted.put(slotKeys[i], data);
            if (entryUid.equals(organizerUid)) mySlot = slotKeys[i];
        }

        String semi1RoomId = db.child("rooms").child("SLAGALICA_MECH").push().getKey();
        String semi2RoomId = db.child("rooms").child("SLAGALICA_MECH").push().getKey();

        Map<String, Object> tournament = new HashMap<>();
        tournament.put("status", "semi");
        tournament.put("createdAt", System.currentTimeMillis());
        for (String s : slotKeys) tournament.put(s, slotted.get(s));
        tournament.put("semi1RoomId", semi1RoomId);
        tournament.put("semi2RoomId", semi2RoomId);

        final String finalMySlot = mySlot;

        db.child("tournaments").child(tournamentId).setValue(tournament)
                .addOnSuccessListener(unused -> {
                    Map<String, Object> p1 = slotted.get("p1");
                    Map<String, Object> p2 = slotted.get("p2");
                    Map<String, Object> p3 = slotted.get("p3");
                    Map<String, Object> p4 = slotted.get("p4");

                    createTournamentRoom(semi1RoomId,
                            (String) p1.get("uid"), (String) p1.get("username"),
                            (String) p2.get("uid"), (String) p2.get("username"),
                            () -> createTournamentRoom(semi2RoomId,
                                    (String) p3.get("uid"), (String) p3.get("username"),
                                    (String) p4.get("uid"), (String) p4.get("username"),
                                    () -> {
                                        for (int i = 0; i < 4; i++) {
                                            Map<String, Object> sp = slotted.get(slotKeys[i]);
                                            if (sp == null) continue;
                                            String pUid = (String) sp.get("uid");
                                            Map<String, Object> w = new HashMap<>();
                                            w.put("tournamentId", tournamentId);
                                            w.put("slot", slotKeys[i]);
                                            db.child("tournamentWaiting").child(pUid).setValue(w);
                                        }
                                        db.child("activeTournament").removeValue();
                                    }));
                })
                .addOnFailureListener(e -> {
                    refundTokens(organizerUid, 3);
                    callback.onError("Greška pri kreiranju turnira.");
                });

        // Organizer gets onStarted through their own tournamentWaiting entry
        stopLobbyListeners();
        db.child("activeTournament").child("players").child(organizerUid)
                .onDisconnect().cancel();
        watchForStart(organizerUid, callback);
    }

    private void createTournamentRoom(String roomId, String p1Uid, String p1Name,
                                       String p2Uid, String p2Name, Runnable onComplete) {
        MatchmakingManager mm = new MatchmakingManager(
                "SLAGALICA_MECH",
                p1Name != null ? p1Name : "Igrač",
                p1Uid,
                new MatchmakingManager.MatchmakingListener() {
                    @Override public void onMatchFound(String id, int num) {}
                    @Override public void onWaiting() {}
                    @Override public void onError(String msg) {
                        Log.e(TAG, "Room seed error: " + msg);
                        if (onComplete != null) onComplete.run();
                    }
                });
        mm.createTournamentRoom(roomId, p2Uid, p2Name != null ? p2Name : "Igrač", onComplete);
    }

    // ── Post-match ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void checkAndCreateFinal(String tournamentId, ResultCallback callback) {
        DatabaseReference ref = db.child("tournaments").child(tournamentId);
        ref.runTransaction(new Transaction.Handler() {
            @Override
            @NonNull
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Map<String, Object> t = (Map<String, Object>) currentData.getValue();
                if (t == null) return Transaction.abort();
                if (!"semi".equals(t.get("status"))) return Transaction.abort();
                if (t.get("semi1WinnerUid") == null || t.get("semi2WinnerUid") == null)
                    return Transaction.abort();
                if (t.containsKey("finalRoomId") && t.get("finalRoomId") != null)
                    return Transaction.abort();

                String finalRoomId = db.child("rooms").child("SLAGALICA_MECH").push().getKey();
                t.put("finalRoomId", finalRoomId);
                t.put("status", "final");
                currentData.setValue(t);
                return Transaction.success(currentData);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (!committed) {
                    callback.onDone();
                    return;
                }
                if (snapshot == null) { callback.onDone(); return; }

                Map<String, Object> t = (Map<String, Object>) snapshot.getValue();
                if (t == null) { callback.onDone(); return; }

                String finalRoomId = (String) t.get("finalRoomId");
                String s1Winner = (String) t.get("semi1WinnerUid");
                String s2Winner = (String) t.get("semi2WinnerUid");

                Map<String, Object> p1Data = findPlayerByUid(t, s1Winner);
                Map<String, Object> p2Data = findPlayerByUid(t, s2Winner);
                String p1Name = p1Data != null ? (String) p1Data.get("username") : "Finalist 1";
                String p2Name = p2Data != null ? (String) p2Data.get("username") : "Finalist 2";

                createTournamentRoom(finalRoomId, s1Winner, p1Name, s2Winner, p2Name,
                        callback::onDone);
            }
        });
    }

    // ── Rewards ───────────────────────────────────────────────────────────────

    private void applySemiRewards(String uid, int myScore, boolean iWon, Runnable onDone) {
        if (!iWon) { onDone.run(); return; }
        // Semi winner: +2 tokens, floor(score/40)+10 stars
        applyRewards(uid, myScore / 40 + 10, 2, onDone);
    }

    private void applyFinalRewards(String uid, int myScore, boolean iWon, ResultCallback callback) {
        if (iWon) {
            // Final winner: +3 tokens, floor(score/40)+20 stars (10 regular win + 10 bonus)
            applyRewards(uid, myScore / 40 + 20, 3, callback::onDone);
        } else {
            // Final loser: regular loss formula, no tokens
            applyRewards(uid, myScore / 40 - 10, 0, callback::onDone);
        }
    }

    private void applyRewards(String uid, int starsDelta, int tokensDelta, Runnable onDone) {
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { onDone.run(); return; }

                    long curStars = doc.getLong("totalStars") != null ? doc.getLong("totalStars") : 0;
                    long curTokens = doc.getLong("tokenCount") != null ? doc.getLong("tokenCount") : 0;
                    long newStars = Math.max(0, curStars + starsDelta);
                    long newTokens = curTokens + tokensDelta;

                    String weekId = getCurrentWeekId();
                    String monthId = getCurrentMonthId();
                    long weeklyStars = weekId.equals(doc.getString("cycleWeek"))
                            && doc.getLong("weeklyStars") != null ? doc.getLong("weeklyStars") : 0;
                    long monthlyStars = monthId.equals(doc.getString("cycleMonth"))
                            && doc.getLong("monthlyStars") != null ? doc.getLong("monthlyStars") : 0;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("totalStars", newStars);
                    updates.put("tokenCount", newTokens);
                    updates.put("weeklyStars", Math.max(0, weeklyStars + starsDelta));
                    updates.put("monthlyStars", Math.max(0, monthlyStars + starsDelta));
                    updates.put("cycleWeek", weekId);
                    updates.put("cycleMonth", monthId);

                    firestore.collection("users").document(uid).update(updates)
                            .addOnSuccessListener(v -> onDone.run())
                            .addOnFailureListener(e -> onDone.run());
                })
                .addOnFailureListener(e -> onDone.run());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> findPlayerByUid(Map<String, Object> tournament, String uid) {
        for (String key : new String[]{"p1", "p2", "p3", "p4"}) {
            Object val = tournament.get(key);
            if (val instanceof Map) {
                Map<String, Object> p = (Map<String, Object>) val;
                if (uid != null && uid.equals(p.get("uid"))) return p;
            }
        }
        return null;
    }

    private void refundTokens(String uid, int count) {
        firestore.collection("users").document(uid)
                .update("tokenCount", FieldValue.increment(count))
                .addOnFailureListener(e -> Log.e(TAG, "Token refund failed: " + e.getMessage()));
    }

    private void stopLobbyListeners() {
        if (lobbyCountRef != null && lobbyCountListener != null) {
            lobbyCountRef.removeEventListener(lobbyCountListener);
            lobbyCountListener = null;
            lobbyCountRef = null;
        }
    }

    private String getCurrentWeekId() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        return String.format(Locale.US, "%d-W%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR));
    }

    private String getCurrentMonthId() {
        return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
    }
}
