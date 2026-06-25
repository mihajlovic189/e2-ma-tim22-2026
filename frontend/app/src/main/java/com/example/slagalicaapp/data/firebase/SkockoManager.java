package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.database.*;
import java.util.HashMap;
import java.util.Map;

public class SkockoManager {

    public interface SkockoListener {
        void onRoomReady(String p1Name, String p2Name, String secret1, String secret2, int p1Score, int p2Score);
        void onPhaseChanged(String phase, long phaseEndsAt, int p1Score, int p2Score);
        void onGuessAdded(String roundKey, int guessIndex, int[] symbols, int reds, int yellows);
        void onGameFinished(int p1Score, int p2Score, String forfeitBy);
        void onError(String message);
    }

    private static final long MAIN_SKOCKO_DURATION_MS = 30_000L;

    private final DatabaseReference roomRef;
    private final int myPlayerNumber;
    private final SkockoListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver = false;

    private boolean roomReadyFired = false;
    private String lastPhase = "";
    private int lastRound1Count = 0;
    private int lastRound2Count = 0;

    public SkockoManager(String roomId, int myPlayerNumber, SkockoListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(GameActivity.GAME_MECH).child(roomId);
        this.myPlayerNumber = myPlayerNumber;
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isGameOver || !snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);
                if (status == null) return;

                DataSnapshot gameSnap = snapshot.child("skocko");

                if ("forfeit".equals(status)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                if (!roomReadyFired && "playing".equals(status)) {
                    // Coordinator sets phaseEndsAt when Skocko actually starts (not at matchmaking time)
                    long existingEndsAt = gameSnap.child("phaseEndsAt").getValue(Long.class) != null
                            ? gameSnap.child("phaseEndsAt").getValue(Long.class) : 0L;
                    if (existingEndsAt <= System.currentTimeMillis()) {
                        if (myPlayerNumber == 1) {
                            roomRef.child("skocko/phaseEndsAt").setValue(
                                    System.currentTimeMillis() + MAIN_SKOCKO_DURATION_MS);
                        }
                        return;
                    }

                    roomReadyFired = true;
                    String p1  = snapshot.child("player1").getValue(String.class);
                    String p2  = snapshot.child("player2").getValue(String.class);
                    String s1  = gameSnap.child("secret1").getValue(String.class);
                    String s2  = gameSnap.child("secret2").getValue(String.class);
                    int ps1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int ps2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onRoomReady(
                            p1 != null ? p1 : "Igrač 1",
                            p2 != null ? p2 : "Igrač 2",
                            s1 != null ? s1 : "",
                            s2 != null ? s2 : "",
                            ps1, ps2);
                    // Fire onPhaseChanged immediately with correct endsAt
                    String phase = gameSnap.child("phase").getValue(String.class);
                    if (phase != null) {
                        lastPhase = phase;
                        listener.onPhaseChanged(phase, existingEndsAt, ps1, ps2);
                    }
                }

                String phase = gameSnap.child("phase").getValue(String.class);
                if (phase != null && !phase.equals(lastPhase)) {
                    lastPhase = phase;
                    Long endsAt = gameSnap.child("phaseEndsAt").getValue(Long.class);
                    int sc1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int sc2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onPhaseChanged(phase, endsAt != null ? endsAt : 0L, sc1, sc2);
                }

                processRoundGuesses(gameSnap, "round1");
                processRoundGuesses(gameSnap, "round2");

                if ("game_finished".equals(gameSnap.child("status").getValue(String.class))) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        roomRef.addValueEventListener(roomListener);
    }

    private void processRoundGuesses(DataSnapshot snapshot, String roundKey) {
        int lastCount = "round1".equals(roundKey) ? lastRound1Count : lastRound2Count;
        DataSnapshot roundSnap = snapshot.child(roundKey);

        int count = (int) roundSnap.getChildrenCount();

        if ("round1".equals(roundKey)) lastRound1Count = count;
        else lastRound2Count = count;

        if (count <= lastCount) return;

        int idx = 0;
        for (DataSnapshot guessSnap : roundSnap.getChildren()) {
            if (idx >= lastCount) {
                String sym = guessSnap.child("symbols").getValue(String.class);
                int reds    = toInt(guessSnap.child("reds").getValue(Long.class));
                int yellows = toInt(guessSnap.child("yellows").getValue(Long.class));
                listener.onGuessAdded(roundKey, idx, parseSymbols(sym), reds, yellows);
            }
            idx++;
        }
    }

    public void writeGuessAndAdvance(String roundKey, int guessIndex, int[] symbols,
                                     int reds, int yellows, Map<String, Object> extra) {
        if (isGameOver) return;
        Map<String, Object> update = new HashMap<>();
        for (Map.Entry<String, Object> e : extra.entrySet()) {
            String k = e.getKey();
            // extra keys that are skocko-specific get prefixed; score keys stay at root
            if (k.startsWith("scores/")) update.put(k, e.getValue());
            else update.put("skocko/" + k, e.getValue());
        }
        update.put("skocko/" + roundKey + "/" + guessIndex + "/symbols", encodeSymbols(symbols));
        update.put("skocko/" + roundKey + "/" + guessIndex + "/reds", reds);
        update.put("skocko/" + roundKey + "/" + guessIndex + "/yellows", yellows);
        roomRef.updateChildren(update);
    }

    // KOREKCIJA: Koristimo transakciju za prelazak faza na tajmaut da sprečimo dupliranje/trkanje niti
    public void advancePhaseAtomic(String expectedPhase, String nextPhase, long nextEndsAt, int currentP1, int currentP2) {
        if (isGameOver) return;
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                String currentPhaseInDb = data.child("skocko/phase").getValue(String.class);
                if (currentPhaseInDb == null || !currentPhaseInDb.equals(expectedPhase)) {
                    return Transaction.abort();
                }

                data.child("skocko/phase").setValue(nextPhase);
                data.child("skocko/phaseEndsAt").setValue(nextEndsAt);
                data.child("scores/player1").setValue(currentP1);
                data.child("scores/player2").setValue(currentP2);

                if ("FINISHED".equals(nextPhase)) {
                    data.child("skocko/status").setValue("game_finished");
                }

                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    public void stopListening() {
        if (roomListener != null) {
            roomRef.removeEventListener(roomListener);
            roomListener = null;
        }
    }

    private int[] parseSymbols(String csv) {
        if (csv == null || csv.isEmpty()) return new int[4];
        String[] parts = csv.split(",");
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { arr[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException ignored) {}
        }
        return arr;
    }

    private String encodeSymbols(int[] s) {
        if (s == null || s.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(s[i]);
        }
        return sb.toString();
    }

    private int toInt(Long val) { return val != null ? val.intValue() : 0; }
}