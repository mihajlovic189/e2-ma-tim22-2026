package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.database.*;

import java.util.*;

public class SpojniceManager {

    public interface SpojniceListener {
        void onGameReady(String p1Name, String p2Name);

        void onTurnStarted(int round, int totalRounds, int activePlayer, long turnEndsAt,
                           List<String> leftItems, List<String> shuffledRights, List<String> correctRights,
                           String description, Map<Integer, Integer> resolvedThisRound,
                           Set<Integer> failedByP1, Set<Integer> lostByP2,
                           int p1Score, int p2Score);

        void onGameFinished(int p1Score, int p2Score, String forfeitBy);
        void onError(String message);
    }

    private final DatabaseReference roomRef;
    private final int myPlayerNumber;
    private final SpojniceListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver = false;
    private boolean gameReadyFired = false;

    private int totalRounds = 0;
    private final List<List<String>> leftItemsPerRound      = new ArrayList<>();
    private final List<List<String>> shuffledRightsPerRound = new ArrayList<>();
    private final List<List<String>> correctRightsPerRound  = new ArrayList<>();
    private final List<String>       descriptionsPerRound   = new ArrayList<>();

    private int lastRound        = -1;
    private int lastActivePlayer = -1;
    private Map<Integer, Integer> lastAttemptedSize = new HashMap<>();

    public SpojniceManager(String roomId, int myPlayerNumber, SpojniceListener listener) {
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

                DataSnapshot gameSnap = snapshot.child("spojnice");

                if ("forfeit".equals(status)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                if ("game_finished".equals(gameSnap.child("status").getValue(String.class))) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, null);
                    return;
                }

                if (!"playing".equals(status)) return;

                if (!gameReadyFired) {
                    if (myPlayerNumber == 1) {
                        Long existingEndsAt = gameSnap.child("turnEndsAt").getValue(Long.class);
                        if (existingEndsAt == null || existingEndsAt <= System.currentTimeMillis()) {
                            roomRef.child("spojnice/turnEndsAt").setValue(System.currentTimeMillis() + 30_000L);
                            return;
                        }
                    }
                    Long endsAtCheck = gameSnap.child("turnEndsAt").getValue(Long.class);
                    if (endsAtCheck == null || endsAtCheck <= System.currentTimeMillis()) return;

                    gameReadyFired = true;
                    parseGameData(gameSnap);
                    String p1 = snapshot.child("player1").getValue(String.class);
                    String p2 = snapshot.child("player2").getValue(String.class);
                    listener.onGameReady(p1 != null ? p1 : "Igrač 1", p2 != null ? p2 : "Igrač 2");
                }

                Long roundL  = gameSnap.child("currentRound").getValue(Long.class);
                Long playerL = gameSnap.child("currentPlayer").getValue(Long.class);
                if (roundL == null || playerL == null) return;

                int round        = roundL.intValue();
                int activePlayer = playerL.intValue();

                Map<Integer, Integer> resolvedThisRound = new HashMap<>();
                for (DataSnapshot entry : gameSnap.child("resolved").child(String.valueOf(round)).getChildren()) {
                    try {
                        int leftIdx = Integer.parseInt(entry.getKey());
                        Long pNum   = entry.getValue(Long.class);
                        if (pNum != null) resolvedThisRound.put(leftIdx, pNum.intValue());
                    } catch (NumberFormatException ignored) {}
                }

                Set<Integer> failedThisRound = new HashSet<>();
                for (DataSnapshot entry : gameSnap.child("failed").child(String.valueOf(round)).getChildren()) {
                    try { failedThisRound.add(Integer.parseInt(entry.getKey())); }
                    catch (NumberFormatException ignored) {}
                }

                Set<Integer> lostThisRound = new HashSet<>();
                for (DataSnapshot entry : gameSnap.child("lost").child(String.valueOf(round)).getChildren()) {
                    try { lostThisRound.add(Integer.parseInt(entry.getKey())); }
                    catch (NumberFormatException ignored) {}
                }

                // Trigger UI update whenever any of the three sets change size
                int currentAttemptedSize = resolvedThisRound.size() + failedThisRound.size() + lostThisRound.size();
                Integer lastSize = lastAttemptedSize.get(round);

                if (round != lastRound || activePlayer != lastActivePlayer || lastSize == null || currentAttemptedSize != lastSize) {
                    lastRound = round;
                    lastActivePlayer = activePlayer;
                    lastAttemptedSize.put(round, currentAttemptedSize);

                    Long turnEndsAt = gameSnap.child("turnEndsAt").getValue(Long.class);
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));

                    if (round < leftItemsPerRound.size()) {
                        listener.onTurnStarted(
                                round, totalRounds, activePlayer,
                                turnEndsAt != null ? turnEndsAt : System.currentTimeMillis() + 30_000L,
                                leftItemsPerRound.get(round),
                                shuffledRightsPerRound.get(round),
                                correctRightsPerRound.get(round),
                                descriptionsPerRound.get(round),
                                resolvedThisRound,
                                failedThisRound,
                                lostThisRound,
                                p1Score, p2Score);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        roomRef.addValueEventListener(roomListener);
    }

    public void submitMatchAtomic(final int playerNum, final int round, final int leftIdx, final boolean isCorrect, final int totalPairCount) {
        if (isGameOver) return;
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                // Abort if already resolved or permanently lost
                if (data.child("spojnice/resolved").child(String.valueOf(round)).child(String.valueOf(leftIdx)).getValue() != null ||
                    data.child("spojnice/lost").child(String.valueOf(round)).child(String.valueOf(leftIdx)).getValue() != null) {
                    return Transaction.abort();
                }

                long now = System.currentTimeMillis();

                if (isCorrect) {
                    data.child("spojnice/resolved").child(String.valueOf(round)).child(String.valueOf(leftIdx)).setValue(playerNum);

                    Long currentScore = data.child("scores/player" + playerNum).getValue(Long.class);
                    data.child("scores/player" + playerNum).setValue((currentScore != null ? currentScore : 0) + 2);

                    long resolvedCount = data.child("spojnice/resolved").child(String.valueOf(round)).getChildrenCount();
                    long lostCount     = data.child("spojnice/lost").child(String.valueOf(round)).getChildrenCount();
                    if (resolvedCount + lostCount >= totalPairCount) {
                        advanceRoundOrFinish(data, round, now);
                    }
                } else {
                    handleTurnTimeoutOrWrong(data, round, playerNum, leftIdx, totalPairCount, now);
                }

                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    public void handleTurnTimeoutOrWrong(MutableData data, int round, int playerNum, int leftIdx, int totalPairCount, long now) {
        int startingPlayerForRound = (round % 2) + 1;
        boolean isStartingPlayer   = (playerNum == startingPlayerForRound);

        long resolvedCount = data.child("spojnice/resolved").child(String.valueOf(round)).getChildrenCount();
        long lostCount     = data.child("spojnice/lost").child(String.valueOf(round)).getChildrenCount();

        if (resolvedCount + lostCount >= totalPairCount) {
            advanceRoundOrFinish(data, round, now);
            return;
        }

        if (isStartingPlayer) {
            // Player 1 wrong / timeout — mark failed, keep Player 1 active
            if (leftIdx >= 0) {
                if (data.child("spojnice/resolved").child(String.valueOf(round)).child(String.valueOf(leftIdx)).getValue() == null) {
                    data.child("spojnice/failed").child(String.valueOf(round)).child(String.valueOf(leftIdx)).setValue(true);
                }
            } else {
                // Timeout: mark every unattempted item as failed
                Set<String> alreadyHandled = new HashSet<>();
                for (MutableData s : data.child("spojnice/resolved").child(String.valueOf(round)).getChildren()) {
                    alreadyHandled.add(s.getKey());
                }
                for (MutableData s : data.child("spojnice/failed").child(String.valueOf(round)).getChildren()) {
                    alreadyHandled.add(s.getKey());
                }
                for (int i = 0; i < totalPairCount; i++) {
                    if (!alreadyHandled.contains(String.valueOf(i))) {
                        data.child("spojnice/failed").child(String.valueOf(round)).child(String.valueOf(i)).setValue(true);
                    }
                }
            }

            long failedCount = data.child("spojnice/failed").child(String.valueOf(round)).getChildrenCount();

            if (resolvedCount + failedCount >= totalPairCount) {
                // Player 1 attempted everything
                if (failedCount > 0) {
                    int nextPlayer = 3 - playerNum;
                    data.child("spojnice/currentPlayer").setValue(nextPlayer);
                    data.child("spojnice/turnEndsAt").setValue(now + 30_000L);
                } else {
                    advanceRoundOrFinish(data, round, now);
                }
            }
            // else: Player 1 still has unattempted items — leave currentPlayer unchanged

        } else {
            // Player 2 wrong / timeout — mark lost, Player 2 continues with remaining items
            if (leftIdx >= 0) {
                data.child("spojnice/lost").child(String.valueOf(round)).child(String.valueOf(leftIdx)).setValue(true);

                long newLostCount = data.child("spojnice/lost").child(String.valueOf(round)).getChildrenCount();
                if (resolvedCount + newLostCount >= totalPairCount) {
                    advanceRoundOrFinish(data, round, now);
                }
                // else: Player 2 still has more failed items to try

            } else {
                // Timeout for Player 2: mark every remaining failed-but-unhandled item as lost
                Set<String> alreadyDone = new HashSet<>();
                for (MutableData s : data.child("spojnice/resolved").child(String.valueOf(round)).getChildren()) {
                    alreadyDone.add(s.getKey());
                }
                for (MutableData s : data.child("spojnice/lost").child(String.valueOf(round)).getChildren()) {
                    alreadyDone.add(s.getKey());
                }
                for (MutableData s : data.child("spojnice/failed").child(String.valueOf(round)).getChildren()) {
                    String key = s.getKey();
                    if (!alreadyDone.contains(key)) {
                        data.child("spojnice/lost").child(String.valueOf(round)).child(key).setValue(true);
                    }
                }
                advanceRoundOrFinish(data, round, now);
            }
        }
    }

    private void advanceRoundOrFinish(MutableData data, int round, long now) {
        int nextRound = round + 1;
        if (nextRound < totalRounds) {
            int nextPlayer = (nextRound % 2) + 1;
            data.child("spojnice/currentRound").setValue(nextRound);
            data.child("spojnice/currentPlayer").setValue(nextPlayer);
            data.child("spojnice/turnEndsAt").setValue(now + 30_000L);
        } else {
            data.child("spojnice/status").setValue("game_finished");
        }
    }

    public void stopListening() {
        if (roomListener != null) {
            roomRef.removeEventListener(roomListener);
            roomListener = null;
        }
    }

    private void parseGameData(DataSnapshot snapshot) {
        Long rcL = snapshot.child("roundCount").getValue(Long.class);
        totalRounds = rcL != null ? rcL.intValue() : 2;

        leftItemsPerRound.clear();
        shuffledRightsPerRound.clear();
        correctRightsPerRound.clear();
        descriptionsPerRound.clear();

        for (int i = 0; i < totalRounds; i++) {
            DataSnapshot g  = snapshot.child("games").child(String.valueOf(i));
            String desc     = g.child("description").getValue(String.class);
            Long pairCountL = g.child("pairCount").getValue(Long.class);
            int pairCount   = pairCountL != null ? pairCountL.intValue() : 0;

            List<String> lefts    = new ArrayList<>();
            List<String> shuffled = new ArrayList<>();
            List<String> correct  = new ArrayList<>();

            for (int j = 0; j < pairCount; j++) {
                String idx = String.valueOf(j);
                lefts   .add(str(g.child("leftItems").child(idx).getValue(String.class)));
                correct .add(str(g.child("correctRights").child(idx).getValue(String.class)));
                shuffled.add(str(g.child("shuffledRights").child(idx).getValue(String.class)));
            }

            descriptionsPerRound  .add(desc != null ? desc : "");
            leftItemsPerRound     .add(lefts);
            shuffledRightsPerRound.add(shuffled);
            correctRightsPerRound .add(correct);
        }
    }

    public void handleTimeoutLocal(int round, int playerNum, int totalPairCount) {
        long now = System.currentTimeMillis();
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                handleTurnTimeoutOrWrong(data, round, playerNum, -1, totalPairCount, now);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    private int    toInt(Long v)  { return v != null ? v.intValue() : 0; }
    private String str (String v) { return v != null ? v : ""; }
}
