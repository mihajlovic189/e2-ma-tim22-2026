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

    private final DatabaseReference roomRef;
    private final AsocijacijeListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver    = false;
    private boolean gameReadyFired = false;

    public AsocijacijeManager(String roomId, AsocijacijeListener listener) {
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

                if ("game_finished".equals(status)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                if (!"playing".equals(status)) return;

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

    // KOREKCIJA: Atomska transakcija za obradu pogađanja kolone prema zvaničnim pravilima Slagalice
    public void submitColumnGuessAtomic(int playerNum, String colLetter, String guess, String correctSolution, long guessDurationMs) {
        if (isGameOver) return;
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                // Umesto .exists() koristimo proveru na null vrednost
                if (data.child("columnsSolved").child(colLetter).getValue() != null) {
                    return Transaction.abort(); // Već rešeno
                }

                boolean isCorrect = guess.trim().equalsIgnoreCase(correctSolution.trim());
                long now = System.currentTimeMillis();

                if (isCorrect) {
                    // Računanje neotvorenih polja u toj koloni
                    int unopenCount = 4;
                    for (int i = 1; i <= 4; i++) {
                        if (data.child("openedFields").child(colLetter + i).getValue() != null) {
                            unopenCount--;
                        }
                    }
                    // Formula: 2 boda + 1 bod za svako neotvoreno polje
                    int points = 2 + unopenCount;

                    data.child("columnsSolved").child(colLetter).setValue(playerNum);

                    Long currentScore = data.child("scores/player" + playerNum).getValue(Long.class);
                    data.child("scores/player" + playerNum).setValue((currentScore != null ? currentScore : 0) + points);

                    data.child("turnPhase").setValue("guessing");
                    data.child("turnEndsAt").setValue(now + guessDurationMs);
                } else {
                    int nextPlayer = 3 - playerNum;
                    data.child("activePlayer").setValue(nextPlayer);

                    boolean allOpen = checkAllFieldsOpen(data);
                    if (allOpen || data.child("columnsSolved").getChildrenCount() >= 4) {
                        data.child("turnPhase").setValue("guessing");
                        data.child("turnEndsAt").setValue(now + guessDurationMs);
                    } else {
                        data.child("turnPhase").setValue("opening");
                        data.child("turnEndsAt").setValue(0L);
                    }
                }
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    // KOREKCIJA: Atomska transakcija za obradu konačnog rešenja sa kaskadnim bodovanjem neotvorenih kolona
    public void submitFinalGuessAtomic(int playerNum, String guess, String correctSolution, long guessDurationMs) {
        if (isGameOver) return;
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                if (data.child("finalSolvedBy").getValue(Long.class) != null &&
                        data.child("finalSolvedBy").getValue(Long.class) > 0) {
                    return Transaction.abort();
                }

                boolean isCorrect = guess.trim().equalsIgnoreCase(correctSolution.trim());
                long now = System.currentTimeMillis();

                if (isCorrect) {
                    int finalPoints = 7;

                    String[] cols = {"A", "B", "C", "D"};
                    for (String col : cols) {
                        if (data.child("columnsSolved").child(col).getValue() == null) {
                            // Kolona nije rešena pre konačnog
                            finalPoints += 6;

                            int unopenInCol = 4;
                            for (int i = 1; i <= 4; i++) {
                                if (data.child("openedFields").child(col + i).getValue() != null) {
                                    unopenInCol--;
                                }
                            }
                            finalPoints += (2 + unopenInCol);
                            data.child("columnsSolved").child(col).setValue(playerNum);
                        }
                    }

                    data.child("finalSolvedBy").setValue(playerNum);
                    Long currentScore = data.child("scores/player" + playerNum).getValue(Long.class);
                    int totalNewScore = (currentScore != null ? currentScore.intValue() : 0) + finalPoints;
                    data.child("scores/player" + playerNum).setValue(totalNewScore);

                    int p1 = 0, p2 = 0;
                    if (playerNum == 1) {
                        p1 = totalNewScore;
                        Long p2L = data.child("scores/player2").getValue(Long.class);
                        p2 = p2L != null ? p2L.intValue() : 0;
                    } else {
                        p2 = totalNewScore;
                        Long p1L = data.child("scores/player1").getValue(Long.class);
                        p1 = p1L != null ? p1L.intValue() : 0;
                    }

                    String winner = p1 > p2 ? "player1" : (p2 > p1 ? "player2" : "draw");
                    data.child("winner").setValue(winner);
                    data.child("status").setValue("game_finished");
                    data.child("finishedAt").setValue(now);

                } else {
                    int nextPlayer = 3 - playerNum;
                    data.child("activePlayer").setValue(nextPlayer);

                    boolean allOpen = checkAllFieldsOpen(data);
                    if (allOpen || data.child("columnsSolved").getChildrenCount() >= 4) {
                        data.child("turnPhase").setValue("guessing");
                        data.child("turnEndsAt").setValue(now + guessDurationMs);
                    } else {
                        data.child("turnPhase").setValue("opening");
                        data.child("turnEndsAt").setValue(0L);
                    }
                }
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    private boolean checkAllFieldsOpen(MutableData data) {
        Set<String> totalRevealed = new HashSet<>();
        for (MutableData f : data.child("openedFields").getChildren()) {
            totalRevealed.add(f.getKey());
        }
        String[] cols = {"A", "B", "C", "D"};
        for (String colLetter : cols) {
            if (data.child("columnsSolved").child(colLetter).getValue() != null) {
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