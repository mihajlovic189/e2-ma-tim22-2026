package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import java.util.HashMap;
import java.util.Map;

public class SkockoManager {

    public interface SkockoListener {
        void onRoomReady(String p1Name, String p2Name, String secret1, String secret2, int p1Score, int p2Score);
        void onPhaseChanged(String phase, long phaseEndsAt);
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
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child("SKOCKO").child(roomId);
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isGameOver) return;

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
                            p1 != null ? p1 : "Igrac 1",
                            p2 != null ? p2 : "Igrac 2",
                            s1 != null ? s1 : "",
                            s2 != null ? s2 : "",
                            ps1, ps2);
                }

                String phase = snapshot.child("phase").getValue(String.class);
                if (phase != null && !phase.equals(lastPhase)) {
                    lastPhase = phase;
                    Long endsAt = snapshot.child("phaseEndsAt").getValue(Long.class);
                    listener.onPhaseChanged(phase, endsAt != null ? endsAt : 0L);
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

        int count = 0;
        for (DataSnapshot ignored : roundSnap.getChildren()) count++;

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

    /** Atomically writes a guess and any accompanying phase transition. */
    public void writeGuessAndAdvance(String roundKey, int guessIndex, int[] symbols,
                                      int reds, int yellows, Map<String, Object> extra) {
        if (isGameOver) return;
        Map<String, Object> update = new HashMap<>(extra);
        update.put(roundKey + "/" + guessIndex + "/symbols", encodeSymbols(symbols));
        update.put(roundKey + "/" + guessIndex + "/reds", reds);
        update.put(roundKey + "/" + guessIndex + "/yellows", yellows);
        roomRef.updateChildren(update);
    }

    /** Advances phase without a guess (called by active player on timer expiry). */
    public void advancePhase(Map<String, Object> phaseUpdate) {
        if (isGameOver) return;
        roomRef.updateChildren(phaseUpdate);
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