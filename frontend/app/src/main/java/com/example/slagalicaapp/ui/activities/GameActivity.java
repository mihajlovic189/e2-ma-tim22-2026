package com.example.slagalicaapp.ui.activities;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalicaapp.model.GameResult;
import com.example.slagalicaapp.repositories.GameResultRepository;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.ui.fragments.KorakPoKorakFragment;
import com.example.slagalicaapp.ui.fragments.MojBrojFragment;
import com.example.slagalicaapp.ui.fragments.SkockoMultiplayerFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class GameActivity extends AppCompatActivity {

    private static final String TAG = "GameActivity";

    public static final String EXTRA_GAME_TYPE = "GAME_TYPE";
    public static final String EXTRA_ROOM_ID   = "ROOM_ID";
    public static final String EXTRA_PLAYER_NUM = "PLAYER_NUM";
    public static final String EXTRA_PLAYER_NAME = "PLAYER_NAME";

    public static final String GAME_KORAK    = "KORAK_PO_KORAK";
    public static final String GAME_MOJ_BROJ = "MOJ_BROJ";
    public static final String GAME_SKOCKO   = "SKOCKO";

    private String roomId;
    private int playerNumber;
    private String currentGameType;
    private String playerName;
    private boolean hasForfeited = false;
    private boolean finalResultSaved = false;

    private final GameResultRepository gameResultRepository = new GameResultRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        if (savedInstanceState != null) return;

        currentGameType = getIntent().getStringExtra(EXTRA_GAME_TYPE);
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        playerNumber = getIntent().getIntExtra(EXTRA_PLAYER_NUM, 1);
        playerName = getIntent().getStringExtra(EXTRA_PLAYER_NAME);

        if (currentGameType == null) currentGameType = GAME_KORAK;

        getSupportFragmentManager().setFragmentResultListener(
                "GAME_FINISHED", this, (requestKey, result) -> {
                    if (GAME_KORAK.equals(currentGameType)) {
                        currentGameType = GAME_MOJ_BROJ;
                        new Handler().postDelayed(() ->
                                syncMojBrojRoomFromKorak(() -> showGameFragment(currentGameType)), 5000);
                    } else {
                        persistFinalResultAndFinish(false);
                    }
                });

        if (roomId != null && playerName != null) {
            resolvePlayerNumberFromRoom(() -> showGameFragment(currentGameType));
        } else {
            showGameFragment(currentGameType);
        }
    }

    private void resolvePlayerNumberFromRoom(Runnable onReady) {
        DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(currentGameType).child(roomId);
        roomRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);
                String p1Uid = snapshot.child("player1Uid").getValue(String.class);
                String p2Uid = snapshot.child("player2Uid").getValue(String.class);

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                String uid = user != null ? user.getUid() : null;

                if (uid != null) {
                    if (uid.equals(p1Uid)) {
                        playerNumber = 1;
                        onReady.run();
                        return;
                    }
                    if (uid.equals(p2Uid)) {
                        playerNumber = 2;
                        onReady.run();
                        return;
                    }
                }

                String normalized = normalizeName(playerName);
                if (normalized != null) {
                    if (normalized.equals(normalizeName(p1))) {
                        playerNumber = 1;
                    } else if (normalized.equals(normalizeName(p2))) {
                        playerNumber = 2;
                    }
                }
                onReady.run();
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                onReady.run();
            }
        });
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private void showGameFragment(String gameType) {
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        args.putInt("playerNumber", playerNumber);

        androidx.fragment.app.Fragment fragment;

        if (GAME_MOJ_BROJ.equals(gameType)) {
            fragment = new MojBrojFragment();
        } else if (GAME_SKOCKO.equals(gameType)) {
            fragment = new SkockoMultiplayerFragment();
        } else {
            fragment = new KorakPoKorakFragment();
        }

        fragment.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.game_fragment_container, fragment)
                .commit();
    }

    public void forfeitMatch() {
        if (hasForfeited || roomId == null) return;
        hasForfeited = true;

        String playerKey = "player" + playerNumber;
        String winnerKey = playerNumber == 1 ? "player2" : "player1";
        long finishedAt = System.currentTimeMillis();

        if (GAME_SKOCKO.equals(currentGameType)) {
            updateForfeitRoom(GAME_SKOCKO, playerKey, winnerKey, finishedAt);
        } else {
            updateForfeitRoom(GAME_KORAK, playerKey, winnerKey, finishedAt);
            updateForfeitRoom(GAME_MOJ_BROJ, playerKey, winnerKey, finishedAt);
        }
        persistFinalResultAndFinish(true);
    }

    private void updateForfeitRoom(String gameType, String playerKey, String winnerKey, long finishedAt) {
        DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(gameType).child(roomId);

        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put("roundStatus", "game_finished");
        update.put("status", "forfeit");
        update.put("forfeitBy", playerKey);
        update.put("winner", winnerKey);
        update.put("finishedAt", finishedAt);
        roomRef.updateChildren(update);
    }

    private void persistFinalResultAndFinish(boolean forfeit) {
        if (finalResultSaved || roomId == null) {
            finish();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot save game result: no authenticated user is available.");
            Toast.makeText(this, "Rezultat nije sačuvan: korisnik nije prijavljen.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        finalResultSaved = true;
        String resultGameType = currentGameType != null ? currentGameType : GAME_KORAK;
        long finishedAt = System.currentTimeMillis();

        DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(resultGameType).child(roomId);

        boolean isSkocko = GAME_SKOCKO.equals(resultGameType);
        String firestoreCollection = isSkocko ? "skocko_results" : "ko_zna_zna_results";

        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameResult result = buildGameResult(snapshot, resultGameType, finishedAt, forfeit);
                Log.d(TAG, "Saving game result to Firestore collection " + firestoreCollection + ": " + result.gameType);
                gameResultRepository.saveGameResult(result, firestoreCollection)
                        .addOnSuccessListener(documentReference -> {
                            Toast.makeText(GameActivity.this, "Rezultat sačuvan.", Toast.LENGTH_SHORT).show();
                            if (isSkocko) {
                                // Clean up RTDB room after Firestore write succeeds
                                FirebaseDatabase.getInstance().getReference()
                                        .child("rooms").child(GAME_SKOCKO).child(roomId)
                                        .removeValue()
                                        .addOnCompleteListener(t -> finish());
                            } else {
                                finish();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to save game result to Firestore", e);
                            Toast.makeText(GameActivity.this, "Rezultat nije sačuvan. Proveri Firestore rules.", Toast.LENGTH_LONG).show();
                            finish();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                finish();
            }
        });
    }

    private GameResult buildGameResult(DataSnapshot snapshot, String gameType, long finishedAt, boolean forfeit) {
        String player1Name = snapshot.child("player1").getValue(String.class);
        String player2Name = snapshot.child("player2").getValue(String.class);
        String player1Uid = snapshot.child("player1Uid").getValue(String.class);
        String player2Uid = snapshot.child("player2Uid").getValue(String.class);
        int player1Score = readInt(snapshot.child("scores").child("player1").getValue(), 0);
        int player2Score = readInt(snapshot.child("scores").child("player2").getValue(), 0);
        long startedAt = readLong(snapshot.child("startedAt").getValue(), finishedAt);
        int questionsCount = readInt(snapshot.child("currentRound").getValue(), 0);

        String winnerUid;
        if (forfeit) {
            winnerUid = playerNumber == 1 ? player2Uid : player1Uid;
        } else {
            String winner = snapshot.child("winner").getValue(String.class);
            if ("player1".equalsIgnoreCase(winner)) {
                winnerUid = player1Uid;
            } else if ("player2".equalsIgnoreCase(winner)) {
                winnerUid = player2Uid;
            } else if (player1Score > player2Score) {
                winnerUid = player1Uid;
            } else if (player2Score > player1Score) {
                winnerUid = player2Uid;
            } else {
                winnerUid = null;
            }
        }

        long durationMs = Math.max(0L, finishedAt - startedAt);

        return new GameResult(
                gameType,
                player1Uid,
                player1Name,
                player1Score,
                player2Uid,
                player2Name,
                player2Score,
                winnerUid,
                finishedAt,
                durationMs,
                questionsCount
        );
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private long readLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void syncMojBrojRoomFromKorak(Runnable onDone) {
        if (roomId == null) {
            onDone.run();
            return;
        }

        DatabaseReference korakRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(GAME_KORAK).child(roomId);
        korakRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);
                String p1Uid = snapshot.child("player1Uid").getValue(String.class);
                String p2Uid = snapshot.child("player2Uid").getValue(String.class);
                Long s1 = snapshot.child("scores").child("player1").getValue(Long.class);
                Long s2 = snapshot.child("scores").child("player2").getValue(Long.class);

                DatabaseReference mbRef = FirebaseDatabase.getInstance().getReference()
                        .child("rooms").child(GAME_MOJ_BROJ).child(roomId);

                java.util.Map<String, Object> update = new java.util.HashMap<>();
                if (p1 != null) update.put("player1", p1);
                if (p2 != null) update.put("player2", p2);
                if (p1Uid != null) update.put("player1Uid", p1Uid);
                if (p2Uid != null) update.put("player2Uid", p2Uid);
                if (s1 != null) update.put("scores/player1", s1);
                if (s2 != null) update.put("scores/player2", s2);

                if (update.isEmpty()) {
                    onDone.run();
                    return;
                }

                mbRef.updateChildren(update).addOnCompleteListener(task -> onDone.run());
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                onDone.run();
            }
        });
    }
}