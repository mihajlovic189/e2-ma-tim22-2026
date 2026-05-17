package com.example.slagalicaapp.data.firebase;

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
                        public void onDataChange(DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                DataSnapshot existing = snapshot.getChildren().iterator().next();
                                String roomId = existing.child("roomId").getValue(String.class);
                                String status = existing.child("status").getValue(String.class);
                                String key = existing.getKey();
                                myQueueKey = key;
                                if ("matched".equals(status) && roomId != null) {
                                    listener.onMatchFound(roomId, 1);
                                    return;
                                }
                                attachQueueListener(queueRef.child(key), roomId, 1);
                                listener.onWaiting();
                                return;
                            }
                            searchForMatch(queueRef);
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
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
                    public void onDataChange(DataSnapshot snapshot) {
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
                            queueRef.child(opponentKey).child("status").setValue("matched");
                            createRoom(roomId, 2);
                        } else {
                            String roomId = db.child("rooms").child(gameType).push().getKey();
                            joinQueue(queueRef, roomId);
                            listener.onWaiting();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
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

        attachQueueListener(myEntry, roomId, 1);
    }

    private void attachQueueListener(DatabaseReference entryRef, String roomId, int playerNumber) {
        queueListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if ("matched".equals(status)) {
                    stopListening();
                    if (roomId != null) {
                        listener.onMatchFound(roomId, playerNumber);
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        entryRef.addValueEventListener(queueListener);
    }

    private void createRoom(String roomId, int playerNumber) {
        DatabaseReference roomRef = db.child("rooms").child(gameType).child(roomId);

        if (playerNumber == 1) {
            roomRef.child("status").setValue("waiting");
            roomRef.child("player1").setValue(playerName);
            roomRef.child("player1Uid").setValue(playerUid);
            roomRef.child("currentRound").setValue(1);
            roomRef.child("scores").child("player1").setValue(0);
            roomRef.child("scores").child("player2").setValue(0);

            if (gameType.equals("KORAK_PO_KORAK")) {
                roomRef.child("activePlayer").setValue(1);
                roomRef.child("currentStep").setValue(-1);
                roomRef.child("roundStatus").setValue("playing");
                seedKorakRoundsFromPool(roomRef);
                seedMojBrojRoom(roomId, playerName, null, playerUid, null);
            } else if (gameType.equals("MOJ_BROJ")) {
                roomRef.child("activePlayer").setValue(1);
                roomRef.child("roundStatus").setValue("playing");
                roomRef.child("targetRevealed").setValue(false);
                roomRef.child("numbersRevealed").setValue(false);
            }
        } else {
            roomRef.child("player2").setValue(playerName);
            roomRef.child("player2Uid").setValue(playerUid);
            roomRef.child("status").setValue("playing");
            if (gameType.equals("KORAK_PO_KORAK")) {
                seedMojBrojRoom(roomId, null, playerName, null, playerUid);
            }
            listener.onMatchFound(roomId, 2);
        }
    }

    private void seedKorakRoundsFromPool(DatabaseReference roomRef) {
        DatabaseReference poolRef = db.child("korak_pool");
        poolRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
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
            public void onCancelled(DatabaseError error) {}
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

    private void seedMojBrojRoom(String roomId, String p1, String p2, String p1Uid, String p2Uid) {
        DatabaseReference mbRef = db.child("rooms").child("MOJ_BROJ").child(roomId);
        if (p1 != null) mbRef.child("player1").setValue(p1);
        if (p2 != null) mbRef.child("player2").setValue(p2);
        if (p1Uid != null) mbRef.child("player1Uid").setValue(p1Uid);
        if (p2Uid != null) mbRef.child("player2Uid").setValue(p2Uid);
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

    public void cancelSearch() {
        stopListening();
        if (myQueueKey != null) {
            db.child("queue").child(gameType).child(myQueueKey).removeValue();
        }
    }
}