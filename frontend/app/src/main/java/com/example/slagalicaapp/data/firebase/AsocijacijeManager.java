package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.database.*;

import java.util.*;

public class AsocijacijeManager {

    public interface AsocijacijeListener {
        void onGameReady(String p1Name, String p2Name, String[][] fields, String[] columnSolutions, String finalSolution, long gameEndsAt);
        void onStateChanged(int activePlayer, String turnPhase, long turnEndsAt, Set<String> openedFields, Map<String, Integer> columnsSolvedBy, int finalSolvedBy, int finalGuessAttempts, int p1Score, int p2Score);
        void onGameFinished(int p1Score, int p2Score, String forfeitBy);
        void onError(String message);
    }

    private static final long ROUND_DURATION_MS = 120_000L;

    private final DatabaseReference roomRef;
    private final int myPlayerNumber;
    private final AsocijacijeListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver = false;
    private boolean gameReadyFired = false;
    private int currentRound = 0;

    public AsocijacijeManager(String roomId, int myPlayerNumber, AsocijacijeListener listener) {
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

                DataSnapshot gameSnap = snapshot.child("asocijacije");

                if ("forfeit".equals(status)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                if (!"playing".equals(status)) return;

                int newRound = toInt(gameSnap.child("currentRound").getValue(Long.class));
                if (newRound < 1) return;

                if (newRound != currentRound) {
                    currentRound = newRound;
                    gameReadyFired = false;
                }

                DataSnapshot roundSnap = gameSnap.child("round" + currentRound);

                String roundStatus = roundSnap.child("status").getValue(String.class);
                if ("game_finished".equals(roundStatus)) {
                    if (currentRound == 1) {
                        if (myPlayerNumber == 1) initRound2();
                        return;
                    } else {
                        isGameOver = true;
                        stopListening();
                        int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                        int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                        listener.onGameFinished(p1, p2, null);
                        return;
                    }
                }

                if (!gameReadyFired) {
                    if (myPlayerNumber == 1 && !roundSnap.hasChild("gameEndsAt")) {
                        Map<String, Object> init = new HashMap<>();
                        init.put("asocijacije/round" + currentRound + "/gameEndsAt",
                                System.currentTimeMillis() + ROUND_DURATION_MS);
                        roomRef.updateChildren(init);
                        return;
                    }
                    Long geAt = roundSnap.child("gameEndsAt").getValue(Long.class);
                    if (geAt == null || geAt == 0) return;

                    gameReadyFired = true;
                    String[][] fields = parseFields(roundSnap);
                    String[] colSols = parseColumnSolutions(roundSnap);
                    String finalSol = roundSnap.child("finalSolution").getValue(String.class);
                    String p1Name = snapshot.child("player1").getValue(String.class);
                    String p2Name = snapshot.child("player2").getValue(String.class);
                    listener.onGameReady(
                            p1Name != null ? p1Name : "Igrač 1",
                            p2Name != null ? p2Name : "Igrač 2",
                            fields, colSols, finalSol != null ? finalSol : "", geAt);
                }

                Long apL = roundSnap.child("activePlayer").getValue(Long.class);
                int activePlayer = apL != null ? apL.intValue() : 1;

                String turnPhase = roundSnap.child("turnPhase").getValue(String.class);
                if (turnPhase == null) turnPhase = "opening";

                Long teL = roundSnap.child("turnEndsAt").getValue(Long.class);
                long turnEndsAt = teL != null ? teL : 0L;

                Set<String> openedFields = new HashSet<>();
                for (DataSnapshot f : roundSnap.child("openedFields").getChildren()) {
                    openedFields.add(f.getKey());
                }

                Map<String, Integer> columnsSolvedBy = new HashMap<>();
                for (DataSnapshot c : roundSnap.child("columnsSolved").getChildren()) {
                    Long pNum = c.getValue(Long.class);
                    if (pNum != null) columnsSolvedBy.put(c.getKey(), pNum.intValue());
                }

                Long fsL = roundSnap.child("finalSolvedBy").getValue(Long.class);
                int finalSolvedBy = fsL != null ? fsL.intValue() : 0;

                int finalGuessAttempts = toInt(roundSnap.child("finalGuessAttempts").getValue(Long.class));

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

    private void initRound2() {
        Map<String, Object> upd = new HashMap<>();
        upd.put("asocijacije/currentRound", 2);
        upd.put("asocijacije/round2/activePlayer", 2);
        upd.put("asocijacije/round2/turnPhase", "opening");
        upd.put("asocijacije/round2/turnEndsAt", 0L);
        upd.put("asocijacije/round2/gameEndsAt", System.currentTimeMillis() + ROUND_DURATION_MS);
        roomRef.updateChildren(upd);
    }

    public void commitAction(Map<String, Object> updates) {
        if (isGameOver) return;
        Map<String, Object> prefixed = new HashMap<>();
        for (Map.Entry<String, Object> e : updates.entrySet()) {
            prefixed.put("asocijacije/round" + currentRound + "/" + e.getKey(), e.getValue());
        }
        roomRef.updateChildren(prefixed);
    }

    public void submitColumnGuessAtomic(int playerNum, String colLetter, String guess, String correctSolution, long guessDurationMs) {
        if (isGameOver) return;
        final int round = currentRound;
        final String rp = "asocijacije/round" + round + "/";
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                if (data.child(rp + "columnsSolved").child(colLetter).getValue() != null) {
                    return Transaction.abort();
                }

                boolean isCorrect = guess.trim().equalsIgnoreCase(correctSolution.trim());
                long now = System.currentTimeMillis();

                if (isCorrect) {
                    int unopenCount = 4;
                    for (int i = 1; i <= 4; i++) {
                        if (data.child(rp + "openedFields").child(colLetter + i).getValue() != null) {
                            unopenCount--;
                        }
                    }
                    int points = 2 + unopenCount;

                    data.child(rp + "columnsSolved").child(colLetter).setValue(playerNum);
                    Long currentScore = data.child("scores/player" + playerNum).getValue(Long.class);
                    data.child("scores/player" + playerNum).setValue((currentScore != null ? currentScore : 0) + points);
                    data.child(rp + "turnPhase").setValue("guessing");
                    data.child(rp + "turnEndsAt").setValue(now + guessDurationMs);
                } else {
                    int nextPlayer = 3 - playerNum;
                    data.child(rp + "activePlayer").setValue(nextPlayer);

                    boolean allOpen = checkAllFieldsOpen(data, round);
                    if (allOpen || data.child(rp + "columnsSolved").getChildrenCount() >= 4) {
                        data.child(rp + "turnPhase").setValue("guessing");
                        data.child(rp + "turnEndsAt").setValue(now + guessDurationMs);
                    } else {
                        data.child(rp + "turnPhase").setValue("opening");
                        data.child(rp + "turnEndsAt").setValue(0L);
                    }
                }
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    public void submitFinalGuessAtomic(int playerNum, String guess, String correctSolution, long guessDurationMs) {
        if (isGameOver) return;
        final int round = currentRound;
        final String rp = "asocijacije/round" + round + "/";
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                Long fsbL = data.child(rp + "finalSolvedBy").getValue(Long.class);
                if (fsbL != null && fsbL > 0) {
                    return Transaction.abort();
                }

                boolean isCorrect = guess.trim().equalsIgnoreCase(correctSolution.trim());
                long now = System.currentTimeMillis();

                if (isCorrect) {
                    int finalPoints = 7;
                    String[] cols = {"A", "B", "C", "D"};
                    for (String col : cols) {
                        if (data.child(rp + "columnsSolved").child(col).getValue() == null) {
                            finalPoints += 6;
                            int unopenInCol = 4;
                            for (int i = 1; i <= 4; i++) {
                                if (data.child(rp + "openedFields").child(col + i).getValue() != null) {
                                    unopenInCol--;
                                }
                            }
                            finalPoints += (2 + unopenInCol);
                            data.child(rp + "columnsSolved").child(col).setValue(playerNum);
                        }
                    }

                    data.child(rp + "finalSolvedBy").setValue(playerNum);
                    Long currentScore = data.child("scores/player" + playerNum).getValue(Long.class);
                    data.child("scores/player" + playerNum).setValue(
                            (currentScore != null ? currentScore.intValue() : 0) + finalPoints);
                    data.child(rp + "status").setValue("game_finished");
                } else {
                    int nextPlayer = 3 - playerNum;
                    data.child(rp + "activePlayer").setValue(nextPlayer);

                    boolean allOpen = checkAllFieldsOpen(data, round);
                    if (allOpen || data.child(rp + "columnsSolved").getChildrenCount() >= 4) {
                        data.child(rp + "turnPhase").setValue("guessing");
                        data.child(rp + "turnEndsAt").setValue(now + guessDurationMs);
                    } else {
                        data.child(rp + "turnPhase").setValue("opening");
                        data.child(rp + "turnEndsAt").setValue(0L);
                    }
                }
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    private boolean checkAllFieldsOpen(MutableData data, int round) {
        String rp = "asocijacije/round" + round + "/";
        Set<String> totalRevealed = new HashSet<>();
        for (MutableData f : data.child(rp + "openedFields").getChildren()) {
            totalRevealed.add(f.getKey());
        }
        String[] cols = {"A", "B", "C", "D"};
        for (String colLetter : cols) {
            if (data.child(rp + "columnsSolved").child(colLetter).getValue() != null) {
                for (int i = 1; i <= 4; i++) {
                    totalRevealed.add(colLetter + i);
                }
            }
        }
        return totalRevealed.size() >= 16;
    }

    public void stopListening() {
        if (roomListener != null) {
            roomRef.removeEventListener(roomListener);
            roomListener = null;
        }
    }

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
