package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.slagalicaapp.game.asocijacije.Asocijacija;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeRepository;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

public class MatchmakingManager {

    public interface MatchmakingListener {
        void onMatchFound(String roomId, int playerNumber);
        void onWaiting();
        void onError(String message);
    }

    private interface SeedCallback {
        void onSeeded();
    }

    private static final long MAIN_SKOCKO_DURATION_MS = 30_000L;
    private static final int QUEUE_SCAN_LIMIT = 20;

    private final DatabaseReference db;
    private final MatchmakingListener listener;
    private final String gameType; // Primamo "SLAGALICA_MECH" sa HomeFragment-a
    private final String playerName;
    private final String playerUid;
    private ValueEventListener queueListener;
    private String myQueueKey;
    private final Random random = new Random();

    public MatchmakingManager(String gameType, String playerName, String playerUid, MatchmakingListener listener) {
        this.db = FirebaseDatabase.getInstance().getReference();
        this.gameType = gameType;
        this.playerName = playerName;
        this.playerUid = playerUid;
        this.listener = listener;
    }

    public void findMatch() {
        DatabaseReference queueRef = db.child("queue").child(gameType);

        if (playerUid != null && !playerUid.isEmpty()) {
            queueRef.orderByChild("playerUid").equalTo(playerUid)
                    .limitToFirst(1)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                DataSnapshot existing = snapshot.getChildren().iterator().next();
                                String existingRoomId = existing.child("roomId").getValue(String.class);
                                String existingStatus = existing.child("status").getValue(String.class);
                                String existingKey = existing.getKey();

                                if ("matched".equals(existingStatus)) {
                                    if (existingKey != null) {
                                        queueRef.child(existingKey).removeValue();
                                    }
                                    searchForMatch(queueRef);
                                    return;
                                }

                                myQueueKey = existingKey;
                                if (existingKey != null) {
                                    attachQueueListener(queueRef.child(existingKey), existingRoomId);
                                }
                                listener.onWaiting();
                                return;
                            }
                            searchForMatch(queueRef);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            listener.onError(error.getMessage());
                        }
                    });
        } else {
            searchForMatch(queueRef);
        }
    }

    private void searchForMatch(DatabaseReference queueRef) {
        queueRef.orderByChild("status").equalTo("waiting")
                .limitToFirst(QUEUE_SCAN_LIMIT)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        DataSnapshot waitingEntry = null;
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String otherUid = child.child("playerUid").getValue(String.class);
                            if (playerUid != null && playerUid.equals(otherUid)) {
                                continue;
                            }
                            waitingEntry = child;
                            break;
                        }

                        if (waitingEntry != null) {
                            attemptToClaim(queueRef, waitingEntry.getKey());
                        } else {
                            String roomId = db.child("rooms").child(gameType).push().getKey();
                            joinQueue(queueRef, roomId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        listener.onError(error.getMessage());
                    }
                });
    }

    private void attemptToClaim(DatabaseReference queueRef, String opponentKey) {
        if (opponentKey == null) {
            searchForMatch(queueRef);
            return;
        }
        DatabaseReference statusRef = queueRef.child(opponentKey).child("status");
        statusRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String current = currentData.getValue(String.class);
                if (!"waiting".equals(current)) {
                    return Transaction.abort();
                }
                currentData.setValue("matched");
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                if (error != null) {
                    listener.onError(error.getMessage());
                    return;
                }
                if (!committed) {
                    searchForMatch(queueRef);
                    return;
                }
                queueRef.child(opponentKey).child("roomId").addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot roomIdSnap) {
                                String roomId = roomIdSnap.getValue(String.class);
                                if (roomId == null) {
                                    listener.onError("Soba protivnika nije pronađena.");
                                    return;
                                }
                                createRoom(roomId, 2, false, () -> listener.onMatchFound(roomId, 2));
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                listener.onError(error.getMessage());
                            }
                        });
            }
        });
    }

    private void joinQueue(DatabaseReference queueRef, String roomId) {
        createRoom(roomId, 1, false, () -> {
            DatabaseReference myEntry = queueRef.push();
            myQueueKey = myEntry.getKey();

            Map<String, Object> entry = new HashMap<>();
            entry.put("roomId", roomId);
            entry.put("player", playerName);
            entry.put("playerUid", playerUid);
            entry.put("status", "waiting");
            myEntry.setValue(entry);

            myEntry.onDisconnect().removeValue();

            attachQueueListener(myEntry, roomId);
            listener.onWaiting();
        });
    }

    private void attachQueueListener(DatabaseReference entryRef, String roomId) {
        queueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if ("matched".equals(status)) {
                    stopListening();
                    entryRef.onDisconnect().cancel();
                    if (myQueueKey != null) {
                        db.child("queue").child(gameType).child(myQueueKey).removeValue();
                        myQueueKey = null;
                    }
                    if (roomId != null) {
                        listener.onMatchFound(roomId, 1);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        entryRef.addValueEventListener(queueListener);
    }

    private void createRoom(String roomId, int playerNumber, boolean isFriendly, @Nullable SeedCallback onReady) {
        DatabaseReference roomRef = db.child("rooms").child(gameType).child(roomId);
        long startedAt = System.currentTimeMillis();

        if (playerNumber == 1) {
            roomRef.child("status").setValue("seeding");
            roomRef.child("isFriendly").setValue(isFriendly);
            roomRef.child("player1").setValue(playerName);
            roomRef.child("player1Uid").setValue(playerUid);
            roomRef.child("startedAt").setValue(startedAt);

            roomRef.child("scores").child("player1").setValue(0);
            roomRef.child("scores").child("player2").setValue(0);

            roomRef.child("currentGameType").setValue("KO_ZNA_ZNA");
            roomRef.child("roundStatus").setValue("playing");

            Map<String, Object> baseStates = new HashMap<>();

            baseStates.put("korakPoKorak/activePlayer", 1);
            baseStates.put("korakPoKorak/currentStep", -1);
            baseStates.put("korakPoKorak/currentRound", 1);

            baseStates.put("mojBroj/activePlayer", 1);
            baseStates.put("mojBroj/targetRevealed", false);
            baseStates.put("mojBroj/numbersRevealed", false);
            baseStates.put("mojBroj/roundEndsAt", 0L);

            baseStates.put("skocko/secret1", generateSkockoSecret());
            baseStates.put("skocko/secret2", generateSkockoSecret());
            baseStates.put("skocko/phase", "ROUND_1_PLAYING");
            baseStates.put("skocko/phaseEndsAt", startedAt + MAIN_SKOCKO_DURATION_MS);

            baseStates.put("asocijacije/activePlayer", 1);
            baseStates.put("asocijacije/turnPhase", "opening");
            baseStates.put("asocijacije/turnEndsAt", 0L);

            baseStates.put("spojnice/currentRound", 0);
            baseStates.put("spojnice/currentPlayer", 1);

            roomRef.updateChildren(baseStates);

            seedKorakRoundsFromPool(roomRef, () -> {
                seedKoZnaZnaQuestions(roomRef, () -> {
                    seedSpojniceGames(roomRef, () -> {
                        seedAsocijacije(roomRef, () -> {
                            roomRef.child("status").setValue("waiting");
                            if (onReady != null) onReady.onSeeded();
                        });
                    });
                });
            });

        } else {
            roomRef.child("player2").setValue(playerName);
            roomRef.child("player2Uid").setValue(playerUid);

            long now = System.currentTimeMillis();
            Map<String, Object> joinUpdates = new HashMap<>();
            joinUpdates.put("status", "playing");
            joinUpdates.put("skocko/phaseEndsAt", now + MAIN_SKOCKO_DURATION_MS);
            joinUpdates.put("koZnaZna/currentQuestionIndex", 0);
            joinUpdates.put("koZnaZna/questionStatus", "playing");
            joinUpdates.put("koZnaZna/questionStartedAt", now + 2000L);
            joinUpdates.put("spojnice/turnEndsAt", now + 32_000L);
            joinUpdates.put("asocijacije/gameEndsAt", now + 122_000L);
            joinUpdates.put("mojBroj/roundEndsAt", 0L);

            roomRef.updateChildren(joinUpdates);

            if (onReady != null) onReady.onSeeded();
        }
    }

    private void seedAsocijacije(DatabaseReference roomRef, Runnable onDone) {
        Asocijacija a = AsocijacijeRepository.getNasumicnaAsocijacija();
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<String, String> e : a.polja.entrySet()) {
            data.put("asocijacije/fields/" + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : a.resenjaKolona.entrySet()) {
            data.put("asocijacije/columnSolutions/" + e.getKey(), e.getValue());
        }
        data.put("asocijacije/finalSolution", a.konacnoResenje);
        roomRef.updateChildren(data, (error, ref) -> {
            if (error != null) listener.onError(error.getMessage());
            onDone.run();
        });
    }

    private void seedSpojniceGames(DatabaseReference roomRef, Runnable onDone) {
        db.child("Spojnice").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> all = new ArrayList<>();
                for (DataSnapshot g : snapshot.getChildren()) all.add(g);
                if (all.isEmpty()) {
                    onDone.run();
                    return;
                }
                Collections.shuffle(all, random);
                int count = Math.min(2, all.size());

                Map<String, Object> gamesMap = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    DataSnapshot src  = all.get(i);
                    String desc       = src.child("description").getValue(String.class);
                    gamesMap.put("spojnice/games/" + i + "/description", desc != null ? desc : "");

                    List<String> leftKeys   = new ArrayList<>();
                    List<String> rightVals  = new ArrayList<>();
                    for (DataSnapshot pair : src.child("pairs").getChildren()) {
                        leftKeys.add(pair.getKey());
                        String v = pair.getValue(String.class);
                        rightVals.add(v != null ? v : "");
                    }

                    List<String> shuffledRights = new ArrayList<>(rightVals);
                    Collections.shuffle(shuffledRights, random);

                    for (int j = 0; j < leftKeys.size(); j++) {
                        gamesMap.put("spojnice/games/" + i + "/leftItems/"      + j, leftKeys.get(j));
                        gamesMap.put("spojnice/games/" + i + "/correctRights/"  + j, rightVals.get(j));
                        gamesMap.put("spojnice/games/" + i + "/shuffledRights/" + j, shuffledRights.get(j));
                    }
                    gamesMap.put("spojnice/games/" + i + "/pairCount", leftKeys.size());
                }
                gamesMap.put("spojnice/roundCount", count);
                roomRef.updateChildren(gamesMap, (error, ref) -> {
                    if (error != null) listener.onError(error.getMessage());
                    onDone.run();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onDone.run();
            }
        });
    }

    private void seedKoZnaZnaQuestions(DatabaseReference roomRef, Runnable onDone) {
        db.child("KoZnaZna").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> all = new ArrayList<>();
                for (DataSnapshot q : snapshot.getChildren()) all.add(q);
                if (all.isEmpty()) {
                    onDone.run();
                    return;
                }
                Collections.shuffle(all, random);
                int count = Math.min(5, all.size());

                Map<String, Object> questionsMap = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    DataSnapshot src = all.get(i);
                    String key = String.valueOf(i);
                    questionsMap.put("koZnaZna/questions/" + key + "/questionText",
                            src.child("questionText").getValue(String.class));
                    Long correctL = src.child("correctAnswerIndex").getValue(Long.class);
                    questionsMap.put("koZnaZna/questions/" + key + "/correctAnswerIndex",
                            correctL != null ? correctL.intValue() : 0);
                    int optIdx = 0;
                    for (DataSnapshot opt : src.child("options").getChildren()) {
                        questionsMap.put("koZnaZna/questions/" + key + "/options/" + optIdx,
                                opt.getValue(String.class));
                        optIdx++;
                    }
                }
                questionsMap.put("koZnaZna/questionCount", count);
                roomRef.updateChildren(questionsMap, (error, ref) -> {
                    if (error != null) listener.onError(error.getMessage());
                    onDone.run();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onDone.run();
            }
        });
    }

    private void seedKorakRoundsFromPool(DatabaseReference roomRef, Runnable onDone) {
        DatabaseReference poolRef = db.child("korak_pool");
        poolRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> items = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    items.add(child);
                }
                if (items.isEmpty()) {
                    onDone.run();
                    return;
                }

                Collections.shuffle(items, random);
                DataSnapshot r1 = items.get(0);
                DataSnapshot r2 = items.size() > 1 ? items.get(1) : items.get(0);

                Map<String, Object> roundsMap = new HashMap<>();
                putKorakRound(roundsMap, 1, r1);
                putKorakRound(roundsMap, 2, r2);

                roomRef.child("korakPoKorak/rounds").updateChildren(roundsMap, (error, ref) -> {
                    if (error != null) listener.onError(error.getMessage());
                    onDone.run();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onDone.run();
            }
        });
    }

    private void putKorakRound(Map<String, Object> roundsMap, int round, DataSnapshot src) {
        String solution = src.child("solution").getValue(String.class);
        if (solution != null) {
            roundsMap.put(round + "/solution", solution);
        }
        for (int i = 0; i < 7; i++) {
            String step = src.child("steps").child(String.valueOf(i)).getValue(String.class);
            if (step != null) {
                roundsMap.put(round + "/steps/" + i, step);
            }
        }
    }

    private String generateSkockoSecret() {
        return random.nextInt(6) + "," + random.nextInt(6) + ","
                + random.nextInt(6) + "," + random.nextInt(6);
    }

    public String createDirectRoom(@Nullable Runnable onRoomReady) {
        String roomId = db.child("rooms").child(gameType).push().getKey();
        if (roomId != null) {
            createRoom(roomId, 1, true, onRoomReady == null ? null : onRoomReady::run);
        }
        return roomId;
    }

    public void joinDirectRoom(String roomId) {
        createRoom(roomId, 2, true, () -> listener.onMatchFound(roomId, 2));
    }

    public void stopListening() {
        if (queueListener != null && myQueueKey != null) {
            db.child("queue").child(gameType).child(myQueueKey)
                    .removeEventListener(queueListener);
        }
    }

    public void cancelSearch() {
        stopListening();
        if (myQueueKey != null) {
            db.child("queue").child(gameType).child(myQueueKey).onDisconnect().cancel();
            db.child("queue").child(gameType).child(myQueueKey).removeValue();
            myQueueKey = null;
        }
    }
}