package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.database.*;
import java.util.ArrayList;
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
    private final java.util.Random random = new java.util.Random();
    private static final long ROUND_DURATION_MS = 60000L;

    public MojBrojManager(String roomId, int myPlayerNumber, MojBrojListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(GameActivity.GAME_MECH).child(roomId);
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
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);
                if (status == null) return;

                if ("forfeit".equals(status)) {
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1Score, p2Score, snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                DataSnapshot gameSnap = snapshot.child("mojBroj");

                String roundStatus = gameSnap.child("roundStatus").getValue(String.class);
                if (roundStatus == null) roundStatus = "";

                int round = toInt(gameSnap.child("currentRound").getValue(Long.class));
                int activePlayer = toInt(gameSnap.child("activePlayer").getValue(Long.class));

                if (round > lastRound && "playing".equals(status)) {
                    lastRound = round;
                    lastStatus = "playing";
                    lastTargetRevealed = false;
                    lastNumbersRevealed = false;
                    lastP1Submitted = false;
                    lastP2Submitted = false;

                    int target = toInt(gameSnap.child("targetNumber").getValue(Long.class));
                    List<Integer> nums = readNumbers(gameSnap);
                    listener.onRoundStarted(activePlayer, round, target, nums);
                }

                boolean targetRevealed = Boolean.TRUE.equals(gameSnap.child("targetRevealed").getValue(Boolean.class));
                if (targetRevealed && !lastTargetRevealed) {
                    lastTargetRevealed = true;
                    int target = toInt(gameSnap.child("targetNumber").getValue(Long.class));
                    listener.onTargetRevealed(target);
                }

                boolean numbersRevealed = Boolean.TRUE.equals(gameSnap.child("numbersRevealed").getValue(Boolean.class));
                if (numbersRevealed && !lastNumbersRevealed) {
                    lastNumbersRevealed = true;
                    int target = toInt(gameSnap.child("targetNumber").getValue(Long.class));
                    List<Integer> nums = readNumbers(gameSnap);
                    listener.onNumbersRevealed(target, nums);
                }

                boolean p1Submitted = Boolean.TRUE.equals(gameSnap.child("submissions").child("player1").child("submitted").getValue(Boolean.class));
                boolean p2Submitted = Boolean.TRUE.equals(gameSnap.child("submissions").child("player2").child("submitted").getValue(Boolean.class));

                if (myPlayerNumber == 1 && p2Submitted && !lastP2Submitted) {
                    lastP2Submitted = true;
                    listener.onOpponentSubmitted();
                }
                if (myPlayerNumber == 2 && p1Submitted && !lastP1Submitted) {
                    lastP1Submitted = true;
                    listener.onOpponentSubmitted();
                }

                if (myPlayerNumber == 1 && p1Submitted && p2Submitted && (!lastP1Submitted || !lastP2Submitted)) {
                    lastP1Submitted = true;
                    lastP2Submitted = true;
                    attemptFinalizeRound(gameSnap, snapshot, round, activePlayer);
                }

                if ("round_finished".equals(roundStatus) && !lastStatus.equals("round_finished")) {
                    lastStatus = "round_finished";
                    int target = toInt(gameSnap.child("targetNumber").getValue(Long.class));
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    String msg = gameSnap.child("lastRoundMessage").getValue(String.class);
                    listener.onRoundResult(p1Score, p2Score, target, msg != null ? msg : "");
                }

                if ("game_finished".equals(roundStatus) && !lastStatus.equals("game_finished")) {
                    lastStatus = "game_finished";
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    String forfeitBy = snapshot.child("forfeitBy").getValue(String.class);
                    listener.onGameFinished(p1Score, p2Score, forfeitBy);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };

        roomRef.addValueEventListener(roomListener);
    }

    public void revealTargetNumber() {
        int target = random.nextInt(999) + 1;
        Map<String, Object> update = new HashMap<>();
        update.put("mojBroj/targetNumber", target);
        update.put("mojBroj/targetRevealed", true);
        update.put("mojBroj/numbersRevealed", false);
        roomRef.updateChildren(update);
    }

    public void revealNumbers() {
        List<Integer> nums = generateNumbers();
        Map<String, Object> update = new HashMap<>();
        for (int i = 0; i < nums.size(); i++) {
            update.put("mojBroj/numbers/n" + i, nums.get(i));
        }
        update.put("mojBroj/numbersRevealed", true);
        update.put("mojBroj/roundEndsAt", System.currentTimeMillis() + ROUND_DURATION_MS);
        update.put("mojBroj/roundStatus", "playing");
        roomRef.updateChildren(update);
    }

    public void revealTargetIfNeeded() {
        roomRef.child("mojBroj/targetRevealed").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    revealTargetNumber();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void revealNumbersIfNeeded() {
        roomRef.child("mojBroj/numbersRevealed").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    revealNumbers();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void submitResult(String expression, int result) {
        String playerKey = "player" + myPlayerNumber;
        Map<String, Object> submission = new HashMap<>();
        submission.put("expression", expression);
        submission.put("result", result);
        submission.put("submitted", true);
        roomRef.child("mojBroj/submissions").child(playerKey).setValue(submission);
    }

    public void finalizeOnTimeout() {
        if (myPlayerNumber == 1) {
            roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    DataSnapshot subsSnap = snapshot.child("mojBroj/submissions");
                    boolean p1Sub = Boolean.TRUE.equals(subsSnap.child("player1").child("submitted").getValue(Boolean.class));
                    boolean p2Sub = Boolean.TRUE.equals(subsSnap.child("player2").child("submitted").getValue(Boolean.class));

                    Map<String, Object> upd = new HashMap<>();
                    if (!p1Sub) {
                        upd.put("mojBroj/submissions/player1/expression", "");
                        upd.put("mojBroj/submissions/player1/result", 0);
                        upd.put("mojBroj/submissions/player1/submitted", true);
                    }
                    if (!p2Sub) {
                        upd.put("mojBroj/submissions/player2/expression", "");
                        upd.put("mojBroj/submissions/player2/result", 0);
                        upd.put("mojBroj/submissions/player2/submitted", true);
                    }
                    if (!upd.isEmpty()) {
                        roomRef.updateChildren(upd);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void attemptFinalizeRound(DataSnapshot gameSnap, DataSnapshot roomSnap, int round, int activePlayer) {
        int target = toInt(gameSnap.child("targetNumber").getValue(Long.class));
        int p1Res = toInt(gameSnap.child("submissions").child("player1").child("result").getValue(Long.class));
        int p2Res = toInt(gameSnap.child("submissions").child("player2").child("result").getValue(Long.class));

        int p1OldScore = toInt(roomSnap.child("scores").child("player1").getValue(Long.class));
        int p2OldScore = toInt(roomSnap.child("scores").child("player2").getValue(Long.class));

        int p1Add = 0, p2Add = 0;
        String message = "";

        int p1Diff = Math.abs(target - p1Res);
        int p2Diff = Math.abs(target - p2Res);

        if (p1Res == target && p2Res == target) {
            p1Add = 10; p2Add = 10;
            message = "Oba igrača su pogodila tačan broj! (+10)";
        } else if (p1Res == target) {
            p1Add = 10;
            message = "Igrač 1 je pogodio tačan broj! (+10)";
        } else if (p2Res == target) {
            p2Add = 10;
            message = "Igrač 2 je pogodio tačan broj! (+10)";
        } else {
            if (p1Res == 0 && p2Res == 0) {
                message = "Niko nije uneo validan rezultat. (+0)";
            } else if (p1Res > 0 && (p2Res == 0 || p1Diff < p2Diff)) {
                p1Add = 5;
                message = "Igrač 1 je bio bliži broju! (+5)";
            } else if (p2Res > 0 && (p1Res == 0 || p2Diff < p1Diff)) {
                p2Add = 5;
                message = "Igrač 2 je bio bliži broju! (+5)";
            } else if (p1Res == p2Res && p1Res > 0) {
                if (activePlayer == 1) {
                    p1Add = 5;
                    message = "Isti rezultat! Bodovi idu Igraču 1 (njegova runda).";
                } else {
                    p2Add = 5;
                    message = "Isti rezultat! Bodovi idu Igraču 2 (njegova runda).";
                }
            }
        }

        Map<String, Object> upd = new HashMap<>();
        upd.put("scores/player1", p1OldScore + p1Add);
        upd.put("scores/player2", p2OldScore + p2Add);
        upd.put("mojBroj/lastRoundMessage", message);
        upd.put("mojBroj/roundStatus", round >= 2 ? "game_finished" : "round_finished");
        roomRef.updateChildren(upd);
    }

    public void startNextRoundIfReady(int nextActivePlayer) {
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                Object roundVal = data.child("mojBroj/currentRound").getValue();
                Object statusVal = data.child("mojBroj/roundStatus").getValue();
                int round = roundVal instanceof Long ? ((Long) roundVal).intValue() : 0;
                String status = statusVal instanceof String ? (String) statusVal : "";

                if (round >= 2 || !"round_finished".equals(status)) {
                    return Transaction.abort();
                }

                data.child("mojBroj/currentRound").setValue(2);
                data.child("mojBroj/activePlayer").setValue(nextActivePlayer);
                data.child("mojBroj/targetNumber").setValue(0);
                data.child("mojBroj/targetRevealed").setValue(false);
                data.child("mojBroj/numbersRevealed").setValue(false);
                data.child("mojBroj/roundStatus").setValue("playing");
                data.child("mojBroj/roundEndsAt").setValue(0);
                data.child("mojBroj/submissions").setValue(null);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    public void stopListening() {
        if (roomListener != null) {
            roomRef.removeEventListener(roomListener);
        }
    }

    private List<Integer> generateNumbers() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) list.add(random.nextInt(9) + 1);
        int[] mediumOpts = {10, 15, 20};
        list.add(mediumOpts[random.nextInt(mediumOpts.length)]);
        int[] largeOpts = {25, 50, 75, 100};
        list.add(largeOpts[random.nextInt(largeOpts.length)]);
        return list;
    }

    private List<Integer> readNumbers(DataSnapshot gameSnap) {
        List<Integer> list = new ArrayList<>();
        DataSnapshot ns = gameSnap.child("numbers");
        for (int i = 0; i < 6; i++) {
            Long v = ns.child("n" + i).getValue(Long.class);
            list.add(v != null ? v.intValue() : 0);
        }
        return list;
    }

    private int toInt(Long v) { return v != null ? v.intValue() : 0; }
}
