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

    private final DatabaseReference roomRef;
    private final SkockoListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver = false;

    private boolean roomReadyFired = false;
    private String lastPhase = "";
    private int lastRound1Count = 0;
    private int lastRound2Count = 0;

    public SkockoManager(String roomId, SkockoListener listener) {
        // KOREKCIJA: Prilagođeno krovnom čvoru sesije meča
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(GameActivity.GAME_MECH).child(roomId);
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isGameOver || !snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);
                if (status == null) return;

                if (!roomReadyFired && "playing".equals(status)) {
                    roomReadyFired = true;
                    String p1  = snapshot.child("player1").getValue(String.class);
                    String p2  = snapshot.child("player2").getValue(String.class);
                    String s1  = snapshot.child("secret1").getValue(String.class);
                    String s2  = snapshot.child("secret2").getValue(String.class);
                    int ps1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int ps2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onRoomReady(
                            p1 != null ? p1 : "Igrač 1",
                            p2 != null ? p2 : "Igrač 2",
                            s1 != null ? s1 : "",
                            s2 != null ? s2 : "",
                            ps1, ps2);
                }

                String phase = snapshot.child("phase").getValue(String.class);
                if (phase != null && !phase.equals(lastPhase)) {
                    lastPhase = phase;
                    Long endsAt = snapshot.child("phaseEndsAt").getValue(Long.class);
                    int sc1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int sc2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onPhaseChanged(phase, endsAt != null ? endsAt : 0L, sc1, sc2);
                }

                processRoundGuesses(snapshot, "round1");
                processRoundGuesses(snapshot, "round2");

                if ("game_finished".equals(status)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    String forfeit = snapshot.child("forfeitBy").getValue(String.class);
                    listener.onGameFinished(p1, p2, forfeit);
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
        Map<String, Object> update = new HashMap<>(extra);
        update.put(roundKey + "/" + guessIndex + "/symbols", encodeSymbols(symbols));
        update.put(roundKey + "/" + guessIndex + "/reds", reds);
        update.put(roundKey + "/" + guessIndex + "/yellows", yellows);
        roomRef.updateChildren(update);
    }

    // KOREKCIJA: Koristimo transakciju za prelazak faza na tajmaut da sprečimo dupliranje/trkanje niti
    public void advancePhaseAtomic(String expectedPhase, String nextPhase, long nextEndsAt, int currentP1, int currentP2) {
        if (isGameOver) return;
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                String currentPhaseInDb = data.child("phase").getValue(String.class);
                if (currentPhaseInDb == null || !currentPhaseInDb.equals(expectedPhase)) {
                    return Transaction.abort(); // Neko je već promenio fazu
                }

                data.child("phase").setValue(nextPhase);
                data.child("phaseEndsAt").setValue(nextEndsAt);
                data.child("scores/player1").setValue(currentP1);
                data.child("scores/player2").setValue(currentP2);

                if ("FINISHED".equals(nextPhase)) {
                    int p1 = 0, p2 = 0;
                    try {
                        p1 = ((Long) data.child("scores/player1").getValue()).intValue();
                        p2 = ((Long) data.child("scores/player2").getValue()).intValue();
                    } catch (Exception ignored) {}

                    String winner = p1 > p2 ? "player1" : (p2 > p1 ? "player2" : "draw");
                    data.child("winner").setValue(winner);
                    data.child("status").setValue("game_finished");
                    data.child("finishedAt").setValue(System.currentTimeMillis());
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