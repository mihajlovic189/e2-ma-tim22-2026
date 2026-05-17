package com.example.slagalicaapp.data.firebase;

import com.google.firebase.database.*;
import java.util.HashMap;
import java.util.Map;

public class KorakPoKorakManager {

    public interface KorakListener {
        void onRoundStarted(int activePlayer, int round, String solution, java.util.List<String> steps);
        void onStepRevealed(int stepIndex);
        void onOpponentAnswering();
        void onRoundFinished(int p1Score, int p2Score, boolean hasNextRound, boolean solved);
        void onGameFinished(int p1Score, int p2Score, String forfeitBy, boolean solved);
    }

    private final DatabaseReference roomRef;
    private final int myPlayerNumber;
    private final KorakListener listener;
    private ValueEventListener roomListener;

    public KorakPoKorakManager(String roomId, int myPlayerNumber, KorakListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child("KORAK_PO_KORAK").child(roomId);
        this.myPlayerNumber = myPlayerNumber;
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            private int lastStep = -1;
            private String lastRoundStatus = "";
            private int lastRound = 0;

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if (!"playing".equals(status)) return;

                int round = toInt(snapshot.child("currentRound").getValue(Long.class));
                int activePlayer = toInt(snapshot.child("activePlayer").getValue(Long.class));
                int step = toInt(snapshot.child("currentStep").getValue(Long.class));
                String roundStatus = snapshot.child("roundStatus").getValue(String.class);
                if (roundStatus == null) roundStatus = "";
                Boolean solvedFlag = snapshot.child("lastRoundSolved").getValue(Boolean.class);
                boolean solved = solvedFlag != null && solvedFlag;

                if (round > lastRound) {
                    String solution = snapshot.child("rounds")
                            .child(String.valueOf(round)).child("solution")
                            .getValue(String.class);
                    java.util.List<String> steps = readSteps(snapshot, round);
                    if (!isRoundReady(solution, steps)) return;

                    lastRound = round;
                    lastStep = -1;
                    lastRoundStatus = "";
                    listener.onRoundStarted(activePlayer, round, solution, steps);
                }

                if (step > lastStep) {
                    lastStep = step;
                    listener.onStepRevealed(step);
                }

                if ("opponent_chance".equals(roundStatus) && !lastRoundStatus.equals("opponent_chance")) {
                    lastRoundStatus = roundStatus;
                    listener.onOpponentAnswering();
                }

                Long s1 = snapshot.child("scores").child("player1").getValue(Long.class);
                Long s2 = snapshot.child("scores").child("player2").getValue(Long.class);
                int p1 = s1 != null ? s1.intValue() : 0;
                int p2 = s2 != null ? s2.intValue() : 0;

                if ("round_finished".equals(roundStatus) && !lastRoundStatus.equals("round_finished")) {
                    lastRoundStatus = roundStatus;
                    boolean hasNext = round < 2;
                    listener.onRoundFinished(p1, p2, hasNext, solved);
                }

                if ("game_finished".equals(roundStatus) && !lastRoundStatus.equals("game_finished")) {
                    lastRoundStatus = roundStatus;
                    String forfeitBy = snapshot.child("forfeitBy").getValue(String.class);
                    listener.onGameFinished(p1, p2, forfeitBy, solved);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        roomRef.addValueEventListener(roomListener);
    }

    public void revealNextStep(int stepIndex) {
        roomRef.child("currentStep").setValue(stepIndex);
    }

    public void advanceStepIfNeeded(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= 7) return;
        roomRef.child("currentStep").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Long current = data.getValue(Long.class);
                long currentVal = current != null ? current : -1L;
                if (stepIndex > currentVal) {
                    data.setValue(stepIndex);
                }
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    public void submitAnswer(String answer, int currentStep, boolean isOpponentChance,
                             String correctAnswer) {
        boolean correct = answer.trim().equalsIgnoreCase(correctAnswer.trim());

        if (!correct) {
            Map<String, Object> update = new HashMap<>();
            update.put("lastWrongAnswer", answer);
            update.put("lastWrongBy", "player" + myPlayerNumber);
            roomRef.updateChildren(update);
            if (isOpponentChance) {
                endRoundWithoutAnswer();
            }
            return;
        }

        Map<String, Object> solvedUpdate = new HashMap<>();
        solvedUpdate.put("lastRoundSolved", true);
        solvedUpdate.put("lastSolvedBy", "player" + myPlayerNumber);
        roomRef.updateChildren(solvedUpdate);

        int points;
        if (!isOpponentChance) {
            points = Math.max(0, 20 - (currentStep * 2));
        } else {
            points = 5;
        }

        String scorePath;
        if (!isOpponentChance) {
            scorePath = "player" + myPlayerNumber;
        } else {
            scorePath = "player" + myPlayerNumber;
        }

        final int finalPoints = points;
        final String finalScorePath = scorePath;

        roomRef.child("scores").child(finalScorePath)
                .runTransaction(new Transaction.Handler() {
                    @Override
                    public Transaction.Result doTransaction(MutableData data) {
                        Long current = data.getValue(Long.class);
                        data.setValue((current != null ? current : 0) + finalPoints);
                        return Transaction.success(data);
                    }

                    @Override
                    public void onComplete(DatabaseError e, boolean committed, DataSnapshot s) {
                        if (!committed) return;

                        roomRef.child("currentRound").addListenerForSingleValueEvent(
                                new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot snap) {
                                        Long round = snap.getValue(Long.class);
                                        if (round != null && round >= 2) {
                                            roomRef.child("roundStatus").setValue("game_finished");
                                        } else {
                                            roomRef.child("roundStatus").setValue("round_finished");
                                        }
                                    }
                                    @Override public void onCancelled(DatabaseError e) {}
                                }
                        );
                    }
                });
    }

    public void onTimerExpired(boolean alreadyOpponentChance) {
        if (!alreadyOpponentChance) {
            roomRef.child("roundStatus").setValue("opponent_chance");
        } else {
            endRoundWithoutAnswer();
        }
    }

    public void startNextRound(int nextActivePlayer) {
        Map<String, Object> update = new HashMap<>();
        update.put("currentRound", 2);
        update.put("activePlayer", nextActivePlayer);
        update.put("currentStep", 0);
        update.put("roundStatus", "playing");
        update.put("lastWrongAnswer", null);
        roomRef.updateChildren(update);
    }

    public void startNextRoundIfReady(int nextActivePlayer) {
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Long roundVal = snapshot.child("currentRound").getValue(Long.class);
                String roundStatus = snapshot.child("roundStatus").getValue(String.class);
                if (roundVal == null || roundVal >= 2) return;
                if (!"round_finished".equals(roundStatus)) return;

                Map<String, Object> update = new HashMap<>();
                update.put("currentRound", 2);
                update.put("activePlayer", nextActivePlayer);
                update.put("currentStep", 0);
                update.put("roundStatus", "playing");
                update.put("lastWrongAnswer", null);
                update.put("lastRoundSolved", null);
                update.put("lastSolvedBy", null);
                roomRef.updateChildren(update);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void endRoundWithoutAnswer() {
        Map<String, Object> update = new HashMap<>();
        update.put("lastRoundSolved", false);
        update.put("lastSolvedBy", null);
        roomRef.updateChildren(update);

        roomRef.child("currentRound").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Long round = snapshot.getValue(Long.class);
                if (round != null && round >= 2) {
                    roomRef.child("roundStatus").setValue("game_finished");
                } else {
                    roomRef.child("roundStatus").setValue("round_finished");
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    public void stopListening() {
        if (roomListener != null) roomRef.removeEventListener(roomListener);
    }

    private int toInt(Long val) { return val != null ? val.intValue() : 0; }

    private java.util.List<String> readSteps(DataSnapshot snapshot, int round) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String val = snapshot.child("rounds").child(String.valueOf(round))
                    .child("steps").child(String.valueOf(i))
                    .getValue(String.class);
            list.add(val != null ? val : "");
        }
        return list;
    }

    private boolean isRoundReady(String solution, java.util.List<String> steps) {
        if (solution == null || solution.trim().isEmpty()) return false;
        if (steps == null || steps.size() < 7) return false;
        for (String step : steps) {
            if (step == null || step.trim().isEmpty()) return false;
        }
        return true;
    }
}