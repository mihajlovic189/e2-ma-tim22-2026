package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.google.firebase.database.*;

import java.util.*;

public class SpojniceManager {

    public interface SpojniceListener {
        void onGameReady(String p1Name, String p2Name);

        /** Fires whenever the active player or round changes. */
        void onTurnStarted(int round, int totalRounds, int activePlayer, long turnEndsAt,
                           List<String> leftItems, List<String> shuffledRights, List<String> correctRights,
                           String description, Map<Integer, Integer> resolvedThisRound,
                           int p1Score, int p2Score);

        void onGameFinished(int p1Score, int p2Score, String forfeitBy);
        void onError(String message);
    }

    private final DatabaseReference roomRef;
    private final SpojniceListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver = false;
    private boolean gameReadyFired = false;

    // Cached game data (populated once, on first "playing" snapshot)
    private int totalRounds = 0;
    private final List<List<String>> leftItemsPerRound    = new ArrayList<>();
    private final List<List<String>> shuffledRightsPerRound = new ArrayList<>();
    private final List<List<String>> correctRightsPerRound  = new ArrayList<>();
    private final List<String>       descriptionsPerRound   = new ArrayList<>();

    // Change tracking
    private int lastRound        = -1;
    private int lastActivePlayer = -1;

    public SpojniceManager(String roomId, SpojniceListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child("SPOJNICE").child(roomId);
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isGameOver) return;

                String status = snapshot.child("status").getValue(String.class);
                if (status == null) return;

                if ("game_finished".equals(status)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2,
                            snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                if (!"playing".equals(status)) return;

                // Parse and cache game data on first "playing" snapshot
                if (!gameReadyFired) {
                    gameReadyFired = true;
                    parseGameData(snapshot);
                    String p1 = snapshot.child("player1").getValue(String.class);
                    String p2 = snapshot.child("player2").getValue(String.class);
                    listener.onGameReady(
                            p1 != null ? p1 : "Igrač 1",
                            p2 != null ? p2 : "Igrač 2");
                }

                Long roundL  = snapshot.child("currentRound").getValue(Long.class);
                Long playerL = snapshot.child("currentPlayer").getValue(Long.class);
                if (roundL == null || playerL == null) return;

                int round        = roundL.intValue();
                int activePlayer = playerL.intValue();
                if (round == lastRound && activePlayer == lastActivePlayer) return;

                lastRound        = round;
                lastActivePlayer = activePlayer;

                Long turnEndsAt = snapshot.child("turnEndsAt").getValue(Long.class);
                int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));

                // Read resolved pairs for the current round
                Map<Integer, Integer> resolvedThisRound = new HashMap<>();
                for (DataSnapshot entry : snapshot.child("resolved")
                        .child(String.valueOf(round)).getChildren()) {
                    try {
                        int leftIdx = Integer.parseInt(entry.getKey());
                        Long pNum   = entry.getValue(Long.class);
                        if (pNum != null) resolvedThisRound.put(leftIdx, pNum.intValue());
                    } catch (NumberFormatException ignored) { /* skip malformed key */ }
                }

                if (round < leftItemsPerRound.size()) {
                    listener.onTurnStarted(
                            round, totalRounds, activePlayer,
                            turnEndsAt != null ? turnEndsAt : System.currentTimeMillis() + 30_000L,
                            leftItemsPerRound.get(round),
                            shuffledRightsPerRound.get(round),
                            correctRightsPerRound.get(round),
                            descriptionsPerRound.get(round),
                            resolvedThisRound, p1Score, p2Score);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        roomRef.addValueEventListener(roomListener);
    }

    public void commitTurn(Map<String, Object> updates) {
        if (isGameOver) return;
        roomRef.updateChildren(updates);
    }

    public void stopListening() {
        if (roomListener != null) {
            roomRef.removeEventListener(roomListener);
            roomListener = null;
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void parseGameData(DataSnapshot snapshot) {
        Long rcL = snapshot.child("roundCount").getValue(Long.class);
        totalRounds = rcL != null ? rcL.intValue() : 2;

        leftItemsPerRound.clear();
        shuffledRightsPerRound.clear();
        correctRightsPerRound.clear();
        descriptionsPerRound.clear();

        for (int i = 0; i < totalRounds; i++) {
            DataSnapshot g    = snapshot.child("games").child(String.valueOf(i));
            String desc       = g.child("description").getValue(String.class);
            Long pairCountL   = g.child("pairCount").getValue(Long.class);
            int pairCount     = pairCountL != null ? pairCountL.intValue() : 0;

            List<String> lefts    = new ArrayList<>();
            List<String> shuffled = new ArrayList<>();
            List<String> correct  = new ArrayList<>();

            for (int j = 0; j < pairCount; j++) {
                String idx = String.valueOf(j);
                lefts   .add(str(g.child("leftItems")    .child(idx).getValue(String.class)));
                correct .add(str(g.child("correctRights").child(idx).getValue(String.class)));
                shuffled.add(str(g.child("shuffledRights").child(idx).getValue(String.class)));
            }

            descriptionsPerRound  .add(desc != null ? desc : "");
            leftItemsPerRound     .add(lefts);
            shuffledRightsPerRound.add(shuffled);
            correctRightsPerRound .add(correct);
        }
    }

    private int    toInt(Long v)     { return v != null ? v.intValue() : 0; }
    private String str (String v)    { return v != null ? v : ""; }
}