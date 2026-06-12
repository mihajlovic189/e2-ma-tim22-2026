package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.google.firebase.database.*;

import java.util.*;

public class AsocijacijeManager {

    public interface AsocijacijeListener {
        /** Fires once when status becomes "playing". Includes static board data. */
        void onGameReady(String p1Name, String p2Name,
                         String[][] fields,
                         String[] columnSolutions,
                         String finalSolution,
                         long gameEndsAt);

        /** Fires on every meaningful board-state change. */
        void onStateChanged(int activePlayer,
                            String turnPhase,
                            long turnEndsAt,
                            Set<String> openedFields,
                            Map<String, Integer> columnsSolvedBy,
                            int finalSolvedBy,
                            int finalGuessAttempts,
                            int p1Score, int p2Score);

        void onGameFinished(int p1Score, int p2Score, String forfeitBy);
        void onError(String message);
    }

    private final DatabaseReference roomRef;
    private final AsocijacijeListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver    = false;
    private boolean gameReadyFired = false;

    public AsocijacijeManager(String roomId, AsocijacijeListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child("ASOCIJACIJE").child(roomId);
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

                // Parse board data on first snapshot
                if (!gameReadyFired) {
                    gameReadyFired = true;
                    String[][] fields = parseFields(snapshot);
                    String[] colSols  = parseColumnSolutions(snapshot);
                    String finalSol   = snapshot.child("finalSolution").getValue(String.class);
                    Long geAt         = snapshot.child("gameEndsAt").getValue(Long.class);
                    String p1 = snapshot.child("player1").getValue(String.class);
                    String p2 = snapshot.child("player2").getValue(String.class);
                    listener.onGameReady(
                            p1 != null ? p1 : "Igrač 1",
                            p2 != null ? p2 : "Igrač 2",
                            fields, colSols,
                            finalSol != null ? finalSol : "",
                            geAt != null ? geAt : System.currentTimeMillis() + 120_000L);
                }

                // Current dynamic state
                Long apL = snapshot.child("activePlayer").getValue(Long.class);
                int activePlayer = apL != null ? apL.intValue() : 1;

                String turnPhase = snapshot.child("turnPhase").getValue(String.class);
                if (turnPhase == null) turnPhase = "opening";

                Long teL = snapshot.child("turnEndsAt").getValue(Long.class);
                long turnEndsAt = teL != null ? teL : 0L;

                Set<String> openedFields = new HashSet<>();
                for (DataSnapshot f : snapshot.child("openedFields").getChildren()) {
                    openedFields.add(f.getKey());
                }

                Map<String, Integer> columnsSolvedBy = new HashMap<>();
                for (DataSnapshot c : snapshot.child("columnsSolved").getChildren()) {
                    Long pNum = c.getValue(Long.class);
                    if (pNum != null) columnsSolvedBy.put(c.getKey(), pNum.intValue());
                }

                Long fsL = snapshot.child("finalSolvedBy").getValue(Long.class);
                int finalSolvedBy = fsL != null ? fsL.intValue() : 0;

                int finalGuessAttempts = toInt(snapshot.child("finalGuessAttempts").getValue(Long.class));

                int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));

                listener.onStateChanged(activePlayer, turnPhase, turnEndsAt,
                        openedFields, columnsSolvedBy, finalSolvedBy, finalGuessAttempts, p1Score, p2Score);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        roomRef.addValueEventListener(roomListener);
    }

    public void commitAction(Map<String, Object> updates) {
        if (isGameOver) return;
        roomRef.updateChildren(updates);
    }

    public void stopListening() {
        if (roomListener != null) {
            roomRef.removeEventListener(roomListener);
            roomListener = null;
        }
    }

    // ─── Parsing helpers ────────────────────────────────────────────────────

    /** Returns fields[col][row], col=A..D (0..3), row=1..4 (0..3). */
    private String[][] parseFields(DataSnapshot snapshot) {
        String[][] f = new String[4][4];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                String key = String.valueOf((char)('A' + c)) + (r + 1);
                String val = snapshot.child("fields").child(key).getValue(String.class);
                f[c][r] = val != null ? val : "?";
            }
        }
        return f;
    }

    private String[] parseColumnSolutions(DataSnapshot snapshot) {
        String[] s = new String[4];
        for (int c = 0; c < 4; c++) {
            String key = String.valueOf((char)('A' + c));
            String val = snapshot.child("columnSolutions").child(key).getValue(String.class);
            s[c] = val != null ? val : "";
        }
        return s;
    }

    private int toInt(Long v) { return v != null ? v.intValue() : 0; }
}
