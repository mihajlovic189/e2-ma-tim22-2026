package com.example.slagalicaapp.data.firebase;

import com.google.firebase.database.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import androidx.annotation.NonNull;

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

                // Detekcija nove runde
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

                // Otkrivanje ciljnog broja
                boolean targetRevealed = Boolean.TRUE.equals(snapshot.child("targetRevealed").getValue(Boolean.class));
                if (targetRevealed && !lastTargetRevealed) {
                    lastTargetRevealed = true;
                    int target = toInt(snapshot.child("targetNumber").getValue(Long.class));
                    listener.onTargetRevealed(target);
                }

                // Otkrivanje ponuđenih brojeva
                boolean numbersRevealed = Boolean.TRUE.equals(snapshot.child("numbersRevealed").getValue(Boolean.class));
                if (numbersRevealed && !lastNumbersRevealed) {
                    lastNumbersRevealed = true;
                    int target = toInt(snapshot.child("targetNumber").getValue(Long.class));
                    List<Integer> nums = readNumbers(snapshot);
                    listener.onNumbersRevealed(target, nums);
                }

                // Praćenje statusa slanja odgovora
                boolean p1Submitted = Boolean.TRUE.equals(snapshot.child("submissions").child("player1").child("submitted").getValue(Boolean.class));
                boolean p2Submitted = Boolean.TRUE.equals(snapshot.child("submissions").child("player2").child("submitted").getValue(Boolean.class));

                if (myPlayerNumber == 1 && p2Submitted && !lastP2Submitted) {
                    lastP2Submitted = true;
                    listener.onOpponentSubmitted();
                }
                if (myPlayerNumber == 2 && p1Submitted && !lastP1Submitted) {
                    lastP1Submitted = true;
                    listener.onOpponentSubmitted();
                }

                // Kada su oba unosa spremna, isključivo Player 1 pokreće evaluaciju bodova
                if (p1Submitted && p2Submitted && (!lastP1Submitted || !lastP2Submitted)) {
                    lastP1Submitted = true;
                    lastP2Submitted = true;
                    if (myPlayerNumber == 1) {
                        attemptFinalizeRound();
                    }
                }

                // Kraj runde i prikaz rezultata
                if (roundStatus.equals("round_finished") && !lastStatus.equals("round_finished")) {
                    lastStatus = "round_finished";
                    int target = toInt(snapshot.child("targetNumber").getValue(Long.class));
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    String msg = snapshot.child("lastRoundMessage").getValue(String.class);
                    listener.onRoundResult(p1Score, p2Score, target, msg != null ? msg : "");
                }

                // Kraj kompletne igre
                if ((roundStatus.equals("game_finished") || status.equals("game_finished")) && !lastStatus.equals("game_finished")) {
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

    // Samo Player 1 (ili aktivni igrač) poziva generisanje brojeva kroz transakciju
    public void revealTargetAndNumbersIfNeeded() {
        if (myPlayerNumber != 1) return; // Sigurnosni osigurač: samo host piše zajedničke brojeve

        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                Object tr = data.child("targetRevealed").getValue();
                boolean targetRevealed = tr instanceof Boolean && (Boolean) tr;

                if (targetRevealed) {
                    return Transaction.abort(); // Već su generisani brojevi za ovu rundu
                }

                // Generiši ciljni broj
                int target = random.nextInt(999) + 1;
                data.child("targetNumber").setValue(target);
                data.child("targetRevealed").setValue(true);

                // Generiši niz brojeva za računanje
                List<Integer> nums = generateNumbers();
                for (int i = 0; i < nums.size(); i++) {
                    data.child("numbers").child("n" + i).setValue(nums.get(i));
                }
                data.child("numbersRevealed").setValue(true);
                data.child("roundStatus").setValue("playing");

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

    // Kada klijentski tajmer (npr. CountDownTimer u UI-ju) dođe do nule, klijent šalje prazan unos
    public void submitTimeoutFallback() {
        String playerKey = "player" + myPlayerNumber;
        roomRef.child("submissions").child(playerKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean submitted = snapshot.child("submitted").getValue(Boolean.class);
                if (submitted == null || !submitted) {
                    submitResult("", 0);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void startNextRound(int nextActivePlayer) {
        if (myPlayerNumber != 1) return; // Isključivo host prebacuje u novu rundu

        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
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
                data.child("submissions").child("player1").setValue(null);
                data.child("submissions").child("player2").setValue(null);
                data.child("numbers").setValue(null);

                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    private void attemptFinalizeRound() {
        roomRef.child("roundStatus").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
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
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
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
                    String winner = "draw";
                    if (finalP1 > finalP2) winner = "player1";
                    else if (finalP2 > finalP1) winner = "player2";

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