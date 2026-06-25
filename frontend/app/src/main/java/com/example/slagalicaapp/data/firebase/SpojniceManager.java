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
    private final List<List<String>> leftItemsPerRound    = new ArrayList<>();
    private final List<List<String>> shuffledRightsPerRound = new ArrayList<>();
    private final List<List<String>> correctRightsPerRound  = new ArrayList<>();
    private final List<String>       descriptionsPerRound   = new ArrayList<>();

    private int lastRound        = -1;
    private int lastActivePlayer = -1;
    private Map<Integer, Integer> lastResolvedSize = new HashMap<>();

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
                    // Coordinator sets turnEndsAt when Spojnice actually starts (not at matchmaking time)
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

                int currentResSize = resolvedThisRound.size();
                Integer lastSize = lastResolvedSize.get(round);

                if (round != lastRound || activePlayer != lastActivePlayer || lastSize == null || currentResSize != lastSize) {
                    lastRound = round;
                    lastActivePlayer = activePlayer;
                    lastResolvedSize.put(round, currentResSize);

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
                                resolvedThisRound, p1Score, p2Score);
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

    // KOREKCIJA: Atomska verifikacija spajanja u realnom vremenu na Firebase-u
    public void submitMatchAtomic(final int playerNum, final int round, final int leftIdx, final boolean isCorrect, final int totalPairCount) {
        if (isGameOver) return;
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                if (data.child("spojnice/resolved").child(String.valueOf(round)).child(String.valueOf(leftIdx)).getValue() != null) {
                    return Transaction.abort();
                }

                long now = System.currentTimeMillis();

                if (isCorrect) {
                    data.child("spojnice/resolved").child(String.valueOf(round)).child(String.valueOf(leftIdx)).setValue(playerNum);

                    Long currentScore = data.child("scores/player" + playerNum).getValue(Long.class);
                    data.child("scores/player" + playerNum).setValue((currentScore != null ? currentScore : 0) + 2);

                    long resolvedCount = data.child("spojnice/resolved").child(String.valueOf(round)).getChildrenCount();
                    if (resolvedCount >= totalPairCount) {
                        advanceRoundOrFinish(data, round, now);
                    }
                } else {
                    handleTurnTimeoutOrWrong(data, round, playerNum, totalPairCount, now);
                }

                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    public void handleTurnTimeoutOrWrong(MutableData data, int round, int playerNum, int totalPairCount, long now) {
        int startingPlayerForRound = (round % 2) + 1;
        boolean isStartingPlayer = (playerNum == startingPlayerForRound);

        long resolvedCount = data.child("spojnice/resolved").child(String.valueOf(round)).getChildrenCount();

        if (resolvedCount >= totalPairCount) {
            advanceRoundOrFinish(data, round, now);
            return;
        }

        if (isStartingPlayer) {
            int nextPlayer = 3 - playerNum;
            data.child("spojnice/currentPlayer").setValue(nextPlayer);
            data.child("spojnice/turnEndsAt").setValue(now + 30_000L);
        } else {
            advanceRoundOrFinish(data, round, now);
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
            DataSnapshot g    = snapshot.child("games").child(String.valueOf(i));
            String desc       = g.child("description").getValue(String.class);
            Long pairCountL   = g.child("pairCount").getValue(Long.class);
            int pairCount     = pairCountL != null ? pairCountL.intValue() : 0;

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
                handleTurnTimeoutOrWrong(data, round, playerNum, totalPairCount, now);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    private int    toInt(Long v)     { return v != null ? v.intValue() : 0; }
    private String str (String v)    { return v != null ? v : ""; }
}