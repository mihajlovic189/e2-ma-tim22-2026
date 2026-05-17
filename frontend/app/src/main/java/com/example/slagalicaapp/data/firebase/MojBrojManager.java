package com.example.slagalicaapp.data.firebase;

import com.google.firebase.database.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MojBrojManager {

    public interface MojBrojListener {
        void onRoundStarted(int activePlayer, int round, int targetNumber, List<Integer> numbers);
        void onTargetRevealed(int targetNumber);
        void onNumbersRevealed(int targetNumber, List<Integer> numbers);
        void onOpponentSubmitted();
        void onRoundResult(int p1Score, int p2Score, int correctNumber, String message);
        void onGameFinished(int p1Score, int p2Score, String forfeitBy);
        void onError(String message);
    }

    private final DatabaseReference roomRef;
    private final int myPlayerNumber;
    private final MojBrojListener listener;
    private ValueEventListener roomListener;
    private static final int[] MEDIUM = {10, 15, 20};
    private static final int[] LARGE  = {25, 50, 75, 100};
    private final java.util.Random random = new java.util.Random();
    private static final long ROUND_DURATION_MS = 60000L;

    public MojBrojManager(String roomId, int myPlayerNumber, MojBrojListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child("MOJ_BROJ").child(roomId);
        this.myPlayerNumber = myPlayerNumber;
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            private String lastStatus = "";
            private int lastRound = 0;
            private boolean lastTargetRevealed = false;
            private boolean lastNumbersRevealed = false;
            private boolean lastP1Submitted = false;
            private boolean lastP2Submitted = false;

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if (status == null) return;

                String roundStatus = snapshot.child("roundStatus").getValue(String.class);
                if (roundStatus == null) roundStatus = "";

                int round = toInt(snapshot.child("currentRound").getValue(Long.class));
                int activePlayer = toInt(snapshot.child("activePlayer").getValue(Long.class));

                if (round > lastRound && status.equals("playing")) {
                    lastRound = round;
                    lastStatus = "playing";
                    lastTargetRevealed = false;
                    lastNumbersRevealed = false;
                    lastP1Submitted = false;
                    lastP2Submitted = false;

                    int target = toInt(snapshot.child("targetNumber").getValue(Long.class));
                    List<Integer> nums = readNumbers(snapshot);
                    listener.onRoundStarted(activePlayer, round, target, nums);
                }

                boolean targetRevealed = Boolean.TRUE.equals(
                        snapshot.child("targetRevealed").getValue(Boolean.class));
                if (targetRevealed && !lastTargetRevealed) {
                    lastTargetRevealed = true;
                    int target = toInt(snapshot.child("targetNumber").getValue(Long.class));
                    listener.onTargetRevealed(target);
                }

                boolean numbersRevealed = Boolean.TRUE.equals(
                        snapshot.child("numbersRevealed").getValue(Boolean.class));
                if (numbersRevealed && !lastNumbersRevealed) {
                    lastNumbersRevealed = true;
                    int target = toInt(snapshot.child("targetNumber").getValue(Long.class));
                    List<Integer> nums = readNumbers(snapshot);
                    listener.onNumbersRevealed(target, nums);
                }

                long roundEndsAt = toLong(snapshot.child("roundEndsAt").getValue(Long.class));
                if (numbersRevealed && "playing".equals(roundStatus)
                        && roundEndsAt > 0 && System.currentTimeMillis() >= roundEndsAt) {
                    finalizeOnTimeout();
                }

                boolean p1Submitted = Boolean.TRUE.equals(
                        snapshot.child("submissions").child("player1")
                                .child("submitted").getValue(Boolean.class));
                boolean p2Submitted = Boolean.TRUE.equals(
                        snapshot.child("submissions").child("player2")
                                .child("submitted").getValue(Boolean.class));

                if (myPlayerNumber == 1 && p2Submitted && !lastP2Submitted) {
                    lastP2Submitted = true;
                    listener.onOpponentSubmitted();
                }
                if (myPlayerNumber == 2 && p1Submitted && !lastP1Submitted) {
                    lastP1Submitted = true;
                    listener.onOpponentSubmitted();
                }

                if (p1Submitted && p2Submitted && (!lastP1Submitted || !lastP2Submitted)) {
                    lastP1Submitted = true;
                    lastP2Submitted = true;
                    attemptFinalizeRound();
                }

                if (roundStatus.equals("round_finished") && !lastStatus.equals("round_finished")) {
                    lastStatus = "round_finished";
                    int target = toInt(snapshot.child("targetNumber").getValue(Long.class));
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    String msg = snapshot.child("lastRoundMessage").getValue(String.class);
                    listener.onRoundResult(p1Score, p2Score, target, msg != null ? msg : "");
                }

                if (roundStatus.equals("game_finished") && !lastStatus.equals("game_finished")) {
                    lastStatus = "game_finished";
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    String forfeitBy = snapshot.child("forfeitBy").getValue(String.class);
                    listener.onGameFinished(p1Score, p2Score, forfeitBy);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };

        roomRef.addValueEventListener(roomListener);
    }

    public void revealTargetNumber() {
        int target = random.nextInt(999) + 1; // 1-999

        Map<String, Object> update = new HashMap<>();
        update.put("targetNumber", target);
        update.put("targetRevealed", true);
        update.put("numbersRevealed", false);
        roomRef.updateChildren(update);
    }

    public void revealNumbers() {
        List<Integer> nums = generateNumbers();

        Map<String, Object> update = new HashMap<>();
        for (int i = 0; i < nums.size(); i++) {
            update.put("numbers/n" + i, nums.get(i));
        }
        update.put("numbersRevealed", true);
        update.put("roundEndsAt", System.currentTimeMillis() + ROUND_DURATION_MS);
        roomRef.updateChildren(update);
    }

    public void revealTargetIfNeeded() {
        roomRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Object tr = data.child("targetRevealed").getValue();
                Object nr = data.child("numbersRevealed").getValue();
                boolean targetRevealed = tr instanceof Boolean && (Boolean) tr;
                boolean numbersRevealed = nr instanceof Boolean && (Boolean) nr;
                if (targetRevealed || numbersRevealed) {
                    return Transaction.abort();
                }

                int target = random.nextInt(999) + 1;
                data.child("targetNumber").setValue(target);
                data.child("targetRevealed").setValue(true);
                data.child("numbersRevealed").setValue(false);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    public void revealNumbersIfNeeded() {
        roomRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Object tr = data.child("targetRevealed").getValue();
                Object nr = data.child("numbersRevealed").getValue();
                boolean targetRevealed = tr instanceof Boolean && (Boolean) tr;
                boolean numbersRevealed = nr instanceof Boolean && (Boolean) nr;
                if (!targetRevealed || numbersRevealed) {
                    return Transaction.abort();
                }

                java.util.List<Integer> nums = generateNumbers();
                for (int i = 0; i < nums.size(); i++) {
                    data.child("numbers").child("n" + i).setValue(nums.get(i));
                }
                data.child("numbersRevealed").setValue(true);
                data.child("roundEndsAt").setValue(System.currentTimeMillis() + ROUND_DURATION_MS);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    public void submitResult(String expression, int result) {
        String playerKey = "player" + myPlayerNumber;

        Map<String, Object> submission = new HashMap<>();
        submission.put("expression", expression);
        submission.put("result", result);
        submission.put("submitted", true);

        roomRef.child("submissions").child(playerKey).setValue(submission);
    }

    public void startNextRound(int nextActivePlayer) {
        startNextRoundIfReady(nextActivePlayer);
    }

    public void startNextRoundIfReady(int nextActivePlayer) {
        roomRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Object roundVal = data.child("currentRound").getValue();
                Object statusVal = data.child("roundStatus").getValue();
                int round = roundVal instanceof Long ? ((Long) roundVal).intValue() : 0;
                String status = statusVal instanceof String ? (String) statusVal : "";

                if (round >= 2 || !"round_finished".equals(status)) {
                    return Transaction.abort();
                }

                data.child("currentRound").setValue(2);
                data.child("activePlayer").setValue(nextActivePlayer);
                data.child("targetNumber").setValue(0);
                data.child("targetRevealed").setValue(false);
                data.child("numbersRevealed").setValue(false);
                data.child("roundStatus").setValue("playing");
                data.child("roundEndsAt").setValue(0);
                data.child("submissions").child("player1").setValue(null);
                data.child("submissions").child("player2").setValue(null);
                data.child("numbers").setValue(null);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    public void finalizeOnTimeout() {
        roomRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Object roundStatus = data.child("roundStatus").getValue();
                if (!(roundStatus instanceof String) || !"playing".equals(roundStatus)) {
                    return Transaction.abort();
                }

                ensureSubmissionIfMissing(data.child("submissions").child("player1"));
                ensureSubmissionIfMissing(data.child("submissions").child("player2"));

                data.child("roundStatus").setValue("calculating");
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!committed) return;
                finalizeScoresFromCurrentData();
            }
        });
    }

    private void attemptFinalizeRound() {
        roomRef.child("roundStatus").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Object val = data.getValue();
                if (!(val instanceof String) || !"playing".equals(val)) {
                    return Transaction.abort();
                }
                data.setValue("calculating");
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!committed) return;
                finalizeScoresFromCurrentData();
            }
        });
    }

    private void finalizeScoresFromCurrentData() {
        roomRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Object statusVal = data.child("roundStatus").getValue();
                if (!(statusVal instanceof String) || !"calculating".equals(statusVal)) {
                    return Transaction.abort();
                }

                int target = toIntObject(data.child("targetNumber").getValue());
                int p1Result = toIntObject(data.child("submissions").child("player1").child("result").getValue());
                int p2Result = toIntObject(data.child("submissions").child("player2").child("result").getValue());
                int p1Score = toIntObject(data.child("scores").child("player1").getValue());
                int p2Score = toIntObject(data.child("scores").child("player2").getValue());
                int activePlayer = toIntObject(data.child("activePlayer").getValue());
                int round = toIntObject(data.child("currentRound").getValue());

                int deltaP1 = 0;
                int deltaP2 = 0;
                String message;

                if (p1Result == target && p2Result == target) {
                    if (activePlayer == 1) { deltaP1 += 10; message = "Oba tačno! Igrač 1 dobija 10 bodova."; }
                    else                   { deltaP2 += 10; message = "Oba tačno! Igrač 2 dobija 10 bodova."; }

                } else if (p1Result == target) {
                    deltaP1 += 10;
                    message = "Igrač 1 tačno! +10 bodova.";

                } else if (p2Result == target) {
                    deltaP2 += 10;
                    message = "Igrač 2 tačno! +10 bodova.";

                } else {
                    int diff1 = p1Result != 0 ? Math.abs(target - p1Result) : Integer.MAX_VALUE;
                    int diff2 = p2Result != 0 ? Math.abs(target - p2Result) : Integer.MAX_VALUE;

                    if (p1Result == 0 && p2Result == 0) {
                        message = "Niko nije unio izraz. 0 bodova.";

                    } else if (p1Result == p2Result) {
                        if (activePlayer == 1) { deltaP1 += 5; message = "Isti rezultat! Igrač 1 (aktivni) +5."; }
                        else                   { deltaP2 += 5; message = "Isti rezultat! Igrač 2 (aktivni) +5."; }

                    } else if (diff1 < diff2) {
                        deltaP1 += 5;
                        message = "Igrač 1 bliže! +5 bodova.";

                    } else if (diff2 < diff1) {
                        deltaP2 += 5;
                        message = "Igrač 2 bliže! +5 bodova.";

                    } else {
                        message = "Isti razmak, niko ne dobija bodove.";
                    }
                }

                int finalP1 = p1Score + deltaP1;
                int finalP2 = p2Score + deltaP2;

                data.child("scores").child("player1").setValue(finalP1);
                data.child("scores").child("player2").setValue(finalP2);
                data.child("lastRoundMessage").setValue(message);

                if (round >= 2) {
                    String winner;
                    if (finalP1 > finalP2) {
                        winner = "player1";
                    } else if (finalP2 > finalP1) {
                        winner = "player2";
                    } else {
                        winner = "draw";
                    }
                    data.child("roundStatus").setValue("game_finished");
                    data.child("status").setValue("game_finished");
                    data.child("winner").setValue(winner);
                    data.child("finishedAt").setValue(System.currentTimeMillis());
                } else {
                    data.child("roundStatus").setValue("round_finished");
                }

                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    private int toIntObject(Object val) {
        if (val instanceof Long) return ((Long) val).intValue();
        if (val instanceof Integer) return (Integer) val;
        return 0;
    }

    private int toInt(Long val) { return val != null ? val.intValue() : 0; }

    private long toLong(Long val) { return val != null ? val : 0L; }

    private void ensureSubmissionIfMissing(MutableData submissionNode) {
        Object submittedVal = submissionNode.child("submitted").getValue();
        boolean submitted = submittedVal instanceof Boolean && (Boolean) submittedVal;
        if (submitted) return;
        submissionNode.child("expression").setValue("");
        submissionNode.child("result").setValue(0);
        submissionNode.child("submitted").setValue(true);
    }

    private List<Integer> generateNumbers() {
        List<Integer> list = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) list.add(random.nextInt(9) + 1);
        list.add(MEDIUM[random.nextInt(MEDIUM.length)]);
        list.add(LARGE[random.nextInt(LARGE.length)]);
        return list;
    }

    private List<Integer> readNumbers(DataSnapshot snapshot) {
        List<Integer> list = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Long val = snapshot.child("numbers").child("n" + i).getValue(Long.class);
            list.add(val != null ? val.intValue() : 0);
        }
        return list;
    }

    public void stopListening() {
        if (roomListener != null) roomRef.removeEventListener(roomListener);
    }
}
