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

    public SpojniceManager(String roomId, SpojniceListener listener) {
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
                    parseGameData(snapshot);
                    String p1 = snapshot.child("player1").getValue(String.class);
                    String p2 = snapshot.child("player2").getValue(String.class);
                    listener.onGameReady(p1 != null ? p1 : "Igrač 1", p2 != null ? p2 : "Igrač 2");
                }

                Long roundL  = snapshot.child("currentRound").getValue(Long.class);
                Long playerL = snapshot.child("currentPlayer").getValue(Long.class);
                if (roundL == null || playerL == null) return;

                int round        = roundL.intValue();
                int activePlayer = playerL.intValue();

                Map<Integer, Integer> resolvedThisRound = new HashMap<>();
                for (DataSnapshot entry : snapshot.child("resolved").child(String.valueOf(round)).getChildren()) {
                    try {
                        int leftIdx = Integer.parseInt(entry.getKey());
                        Long pNum   = entry.getValue(Long.class);
                        if (pNum != null) resolvedThisRound.put(leftIdx, pNum.intValue());
                    } catch (NumberFormatException ignored) {}
                }

                // Okidamo promenu ako se promenila runda, aktivni igrač ILI ako je dodat novi rešeni par
                int currentResSize = resolvedThisRound.size();
                Integer lastSize = lastResolvedSize.get(round);

                if (round != lastRound || activePlayer != lastActivePlayer || lastSize == null || currentResSize != lastSize) {
                    lastRound = round;
                    lastActivePlayer = activePlayer;
                    lastResolvedSize.put(round, currentResSize);

                    Long turnEndsAt = snapshot.child("turnEndsAt").getValue(Long.class);
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
                // Ako je par već rešen od strane nekoga, ignoriši
                if (data.child("resolved").child(String.valueOf(round)).child(String.valueOf(leftIdx)).getValue() != null) {
                    return Transaction.abort();
                }

                long now = System.currentTimeMillis();

                if (isCorrect) {
                    // Upisujemo pogodak u bazu odmah
                    data.child("resolved").child(String.valueOf(round)).child(String.valueOf(leftIdx)).setValue(playerNum);

                    // Dodaj bodove (2 boda po paru)
                    Long currentScore = data.child("scores/player" + playerNum).getValue(Long.class);
                    data.child("scores/player" + playerNum).setValue((currentScore != null ? currentScore : 0) + 2);

                    // Provera da li su svi parovi u ovoj rundi rešeni
                    long resolvedCount = data.child("resolved").child(String.valueOf(round)).getChildrenCount();
                    if (resolvedCount >= totalPairCount) {
                        advanceRoundOrFinish(data, round, now);
                    }
                    // Ako je tačno, a ima još polja, activePlayer ostaje ISTI, tajmer se NE resetuje (igrač troši svojih 30s)
                } else {
                    // NETAČNO: Igrač gubi potez!
                    handleTurnTimeoutOrWrong(data, round, playerNum, totalPairCount, now);
                }

                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {}
        });
    }

    // KOREKCIJA: Upravljanje istekom vremena ili greškom (Prelazak na drugog igrača ili sledeću rundu)
    public void handleTurnTimeoutOrWrong(MutableData data, int round, int playerNum, int totalPairCount, long now) {
        int startingPlayerForRound = (round % 2) + 1;
        boolean isStartingPlayer = (playerNum == startingPlayerForRound);

        long resolvedCount = data.child("resolved").child(String.valueOf(round)).getChildrenCount();

        if (resolvedCount >= totalPairCount) {
            advanceRoundOrFinish(data, round, now);
            return;
        }

        if (isStartingPlayer) {
            // Prvi igrač je pogrešio/istekao -> prepusti preostale pojmove drugom igraču (dobija novih 30s)
            int nextPlayer = 3 - playerNum;
            data.child("currentPlayer").setValue(nextPlayer);
            data.child("turnEndsAt").setValue(now + 30_000L);
        } else {
            // Drugi igrač je pogrešio/istekao -> Kraj ove runde, idemo na sledeću
            advanceRoundOrFinish(data, round, now);
        }
    }

    private void advanceRoundOrFinish(MutableData data, int round, long now) {
        int nextRound = round + 1;
        if (nextRound < totalRounds) {
            int nextPlayer = (nextRound % 2) + 1; // Runda 1 (indeks 0) počinje P1, Runda 2 (indeks 1) počinje P2
            data.child("currentRound").setValue(nextRound);
            data.child("currentPlayer").setValue(nextPlayer);
            data.child("turnEndsAt").setValue(now + 30_000L);
        } else {
            // Sve runde su gotove -> Kraj igre
            Long p1 = data.child("scores/player1").getValue(Long.class);
            Long p2 = data.child("scores/player2").getValue(Long.class);
            int p1Sc = p1 != null ? p1.intValue() : 0;
            int p2Sc = p2 != null ? p2.intValue() : 0;

            String winner = p1Sc > p2Sc ? "player1" : (p2Sc > p1Sc ? "player2" : "draw");
            data.child("status").setValue("game_finished");
            data.child("winner").setValue(winner);
            data.child("finishedAt").setValue(now);
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