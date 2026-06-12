package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.game.asocijacije.Asocijacija;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeRepository;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MatchmakingManager {

    public interface MatchmakingListener {
        void onMatchFound(String roomId, int playerNumber);
        void onWaiting();
        void onError(String message);
    }

    private final DatabaseReference db;
    private final MatchmakingListener listener;
    private final String gameType;
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

                                // Stale "matched" entry from a previous game — delete and search fresh
                                if ("matched".equals(existingStatus)) {
                                    if (existingKey != null) {
                                        queueRef.child(existingKey).removeValue();
                                    }
                                    searchForMatch(queueRef);
                                    return;
                                }

                                // Still "waiting" — reuse the existing slot
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
                            String roomId = waitingEntry.child("roomId").getValue(String.class);
                            String opponentKey = waitingEntry.getKey();
                            if (opponentKey != null) {
                                queueRef.child(opponentKey).child("status").setValue("matched");
                                createRoom(roomId, 2);
                            }
                        } else {
                            String roomId = db.child("rooms").child(gameType).push().getKey();
                            joinQueue(queueRef, roomId);
                            listener.onWaiting();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        listener.onError(error.getMessage());
                    }
                });
    }

    private void joinQueue(DatabaseReference queueRef, String roomId) {
        DatabaseReference myEntry = queueRef.push();
        myQueueKey = myEntry.getKey();

        myEntry.child("roomId").setValue(roomId);
        myEntry.child("player").setValue(playerName);
        myEntry.child("playerUid").setValue(playerUid);
        myEntry.child("status").setValue("waiting");

        createRoom(roomId, 1);

        attachQueueListener(myEntry, roomId);
    }

    private void attachQueueListener(DatabaseReference entryRef, String roomId) {
        queueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if ("matched".equals(status)) {
                    stopListening();
                    // Delete queue entry so future matchmaking doesn't pick up this stale slot
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

    private void createRoom(String roomId, int playerNumber) {
        DatabaseReference roomRef = db.child("rooms").child(gameType).child(roomId);
        long startedAt = System.currentTimeMillis();

        if (playerNumber == 1) {
            roomRef.child("status").setValue("waiting");
            roomRef.child("player1").setValue(playerName);
            roomRef.child("player1Uid").setValue(playerUid);
            roomRef.child("currentRound").setValue(1);
            roomRef.child("startedAt").setValue(startedAt);
            roomRef.child("scores").child("player1").setValue(0);
            roomRef.child("scores").child("player2").setValue(0);

            if (gameType.equals("KORAK_PO_KORAK")) {
                roomRef.child("activePlayer").setValue(1);
                roomRef.child("currentStep").setValue(-1);
                roomRef.child("roundStatus").setValue("playing");
                seedKorakRoundsFromPool(roomRef);
                seedMojBrojRoom(roomId, playerName, null, playerUid, null, startedAt);
            } else if (gameType.equals("MOJ_BROJ")) {
                roomRef.child("activePlayer").setValue(1);
                roomRef.child("roundStatus").setValue("playing");
                roomRef.child("targetRevealed").setValue(false);
                roomRef.child("numbersRevealed").setValue(false);
            } else if (gameType.equals("SKOCKO")) {
                roomRef.child("secret1").setValue(generateSkockoSecret());
                roomRef.child("secret2").setValue(generateSkockoSecret());
                roomRef.child("phase").setValue("ROUND_1_PLAYING");
                roomRef.child("phaseEndsAt").setValue(startedAt + MAIN_SKOCKO_DURATION_MS);
            } else if (gameType.equals("KO_ZNA_ZNA")) {
                seedKoZnaZnaQuestions(roomRef);
            } else if (gameType.equals("SPOJNICE")) {
                roomRef.child("currentRound").setValue(0);
                roomRef.child("currentPlayer").setValue(1);
                seedSpojniceGames(roomRef);
            } else if (gameType.equals("ASOCIJACIJE")) {
                seedAsocijacije(roomRef);
                roomRef.child("activePlayer").setValue(1);
                roomRef.child("turnPhase").setValue("opening");
                roomRef.child("turnEndsAt").setValue(0L);
            }
        } else {
            roomRef.child("player2").setValue(playerName);
            roomRef.child("player2Uid").setValue(playerUid);
            roomRef.child("status").setValue("playing");
            if (gameType.equals("KORAK_PO_KORAK")) {
                seedMojBrojRoom(roomId, null, playerName, null, playerUid, null);
            } else if (gameType.equals("SKOCKO")) {
                // Reset phaseEndsAt to now so both players get a fresh countdown
                roomRef.child("phaseEndsAt")
                        .setValue(System.currentTimeMillis() + MAIN_SKOCKO_DURATION_MS);
            } else if (gameType.equals("KO_ZNA_ZNA")) {
                long now = System.currentTimeMillis();
                roomRef.child("currentQuestionIndex").setValue(0);
                roomRef.child("questionStatus").setValue("playing");
                roomRef.child("questionStartedAt").setValue(now + 2000L);
            } else if (gameType.equals("SPOJNICE")) {
                roomRef.child("turnEndsAt").setValue(System.currentTimeMillis() + 32_000L);
            } else if (gameType.equals("ASOCIJACIJE")) {
                roomRef.child("gameEndsAt").setValue(System.currentTimeMillis() + 122_000L);
            }
            listener.onMatchFound(roomId, 2);
        }
    }

    private static final long MAIN_SKOCKO_DURATION_MS = 30_000L;

    private void seedAsocijacije(DatabaseReference roomRef) {
        Asocijacija a = AsocijacijeRepository.getNasumicnaAsocijacija();
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> e : a.polja.entrySet()) {
            data.put("fields/" + e.getKey(), e.getValue());
        }
        for (java.util.Map.Entry<String, String> e : a.resenjaKolona.entrySet()) {
            data.put("columnSolutions/" + e.getKey(), e.getValue());
        }
        data.put("finalSolution", a.konacnoResenje);
        roomRef.updateChildren(data);
    }

    private void seedSpojniceGames(DatabaseReference roomRef) {
        db.child("Spojnice").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> all = new ArrayList<>();
                for (DataSnapshot g : snapshot.getChildren()) all.add(g);
                if (all.isEmpty()) return;
                Collections.shuffle(all, random);
                int count = Math.min(2, all.size());

                java.util.Map<String, Object> gamesMap = new java.util.HashMap<>();
                for (int i = 0; i < count; i++) {
                    DataSnapshot src  = all.get(i);
                    String desc       = src.child("description").getValue(String.class);
                    gamesMap.put("games/" + i + "/description", desc != null ? desc : "");

                    List<String> leftKeys   = new ArrayList<>();
                    List<String> rightVals  = new ArrayList<>();
                    for (DataSnapshot pair : src.child("pairs").getChildren()) {
                        leftKeys .add(pair.getKey());
                        rightVals.add(pair.getValue(String.class) != null
                                ? pair.getValue(String.class) : "");
                    }

                    // Shuffle the right column so both players see the same shuffled board
                    List<String> shuffledRights = new ArrayList<>(rightVals);
                    Collections.shuffle(shuffledRights, random);

                    for (int j = 0; j < leftKeys.size(); j++) {
                        gamesMap.put("games/" + i + "/leftItems/"     + j, leftKeys.get(j));
                        gamesMap.put("games/" + i + "/correctRights/"  + j, rightVals.get(j));
                        gamesMap.put("games/" + i + "/shuffledRights/" + j, shuffledRights.get(j));
                    }
                    gamesMap.put("games/" + i + "/pairCount", leftKeys.size());
                }
                gamesMap.put("roundCount", count);
                roomRef.updateChildren(gamesMap);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void seedKoZnaZnaQuestions(DatabaseReference roomRef) {
        db.child("KoZnaZna").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> all = new ArrayList<>();
                for (DataSnapshot q : snapshot.getChildren()) all.add(q);
                if (all.isEmpty()) return;
                Collections.shuffle(all, random);
                int count = Math.min(5, all.size());

                java.util.Map<String, Object> questionsMap = new java.util.HashMap<>();
                for (int i = 0; i < count; i++) {
                    DataSnapshot src = all.get(i);
                    String key = String.valueOf(i);
                    questionsMap.put("questions/" + key + "/questionText",
                            src.child("questionText").getValue(String.class));
                    Long correctL = src.child("correctAnswerIndex").getValue(Long.class);
                    questionsMap.put("questions/" + key + "/correctAnswerIndex",
                            correctL != null ? correctL.intValue() : 0);
                    int optIdx = 0;
                    for (DataSnapshot opt : src.child("options").getChildren()) {
                        questionsMap.put("questions/" + key + "/options/" + optIdx,
                                opt.getValue(String.class));
                        optIdx++;
                    }
                }
                questionsMap.put("questionCount", count);
                roomRef.updateChildren(questionsMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String generateSkockoSecret() {
        return random.nextInt(6) + "," + random.nextInt(6) + ","
                + random.nextInt(6) + "," + random.nextInt(6);
    }

    private void seedKorakRoundsFromPool(DatabaseReference roomRef) {
        DatabaseReference poolRef = db.child("korak_pool");
        poolRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> items = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    items.add(child);
                }
                if (items.isEmpty()) return;

                Collections.shuffle(items, new Random());
                DataSnapshot r1 = items.get(0);
                DataSnapshot r2 = items.size() > 1 ? items.get(1) : items.get(0);

                writeKorakRoundFromSnapshot(roomRef, 1, r1);
                writeKorakRoundFromSnapshot(roomRef, 2, r2);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void writeKorakRoundFromSnapshot(DatabaseReference roomRef, int round, DataSnapshot src) {
        String solution = src.child("solution").getValue(String.class);
        if (solution != null) {
            roomRef.child("rounds").child(String.valueOf(round)).child("solution")
                    .setValue(solution);
        }
        for (int i = 0; i < 7; i++) {
            String step = src.child("steps").child(String.valueOf(i)).getValue(String.class);
            if (step != null) {
                roomRef.child("rounds").child(String.valueOf(round))
                        .child("steps").child(String.valueOf(i)).setValue(step);
            }
        }
    }

    private void seedMojBrojRoom(String roomId, String p1, String p2, String p1Uid, String p2Uid, Long startedAt) {
        DatabaseReference mbRef = db.child("rooms").child("MOJ_BROJ").child(roomId);
        if (p1 != null) mbRef.child("player1").setValue(p1);
        if (p2 != null) mbRef.child("player2").setValue(p2);
        if (p1Uid != null) mbRef.child("player1Uid").setValue(p1Uid);
        if (p2Uid != null) mbRef.child("player2Uid").setValue(p2Uid);
        if (startedAt != null) mbRef.child("startedAt").setValue(startedAt);
        mbRef.child("status").setValue(p2 != null ? "playing" : "waiting");
        mbRef.child("currentRound").setValue(1);
        mbRef.child("activePlayer").setValue(1);
        mbRef.child("scores").child("player1").setValue(0);
        mbRef.child("scores").child("player2").setValue(0);
        mbRef.child("roundStatus").setValue("playing");
        mbRef.child("targetRevealed").setValue(false);
        mbRef.child("numbersRevealed").setValue(false);
        mbRef.child("roundEndsAt").setValue(0);
    }


    public void stopListening() {
        if (queueListener != null && myQueueKey != null) {
            db.child("queue").child(gameType).child(myQueueKey)
                    .removeEventListener(queueListener);
        }
    }

    @SuppressWarnings("unused")
    public void cancelSearch() {
        stopListening();
        if (myQueueKey != null) {
            db.child("queue").child(gameType).child(myQueueKey).removeValue();
        }
    }
}