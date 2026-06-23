package com.example.slagalicaapp.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalicaapp.model.GameResult;
import com.example.slagalicaapp.repositories.GameResultRepository;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.ui.fragments.KorakPoKorakFragment;
import com.example.slagalicaapp.ui.fragments.MojBrojFragment;
import com.example.slagalicaapp.ui.fragments.AsocijacijeMultiplayerFragment;
import com.example.slagalicaapp.ui.fragments.KoZnaZnaMultiplayerFragment;
import com.example.slagalicaapp.ui.fragments.SkockoMultiplayerFragment;
import com.example.slagalicaapp.ui.fragments.SpojniceMultiplayerFragment;
import com.example.slagalicaapp.utils.StatsCalculator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class GameActivity extends AppCompatActivity {

    private static final String TAG = "GameActivity";

    public static final String EXTRA_ROOM_ID   = "ROOM_ID";
    public static final String EXTRA_PLAYER_NUM = "PLAYER_NUM";
    public static final String EXTRA_PLAYER_NAME = "PLAYER_NAME";

    public static final String GAME_MECH     = "SLAGALICA_MECH";

    public static final String GAME_KORAK    = "KORAK_PO_KORAK";
    public static final String GAME_MOJ_BROJ = "MOJ_BROJ";
    public static final String GAME_SKOCKO      = "SKOCKO";
    public static final String GAME_KO_ZNA_ZNA = "KO_ZNA_ZNA";
    public static final String GAME_SPOJNICE    = "SPOJNICE";
    public static final String GAME_ASOCIJACIJE = "ASOCIJACIJE";

    private String roomId;
    private int playerNumber;
    private String currentSubGame = GAME_KO_ZNA_ZNA;
    private String playerName;
    private boolean hasForfeited = false;
    private boolean finalResultSaved = false;

    private DatabaseReference roomRef;
    private ValueEventListener roomListener;

    private final GameResultRepository gameResultRepository = new GameResultRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        if (savedInstanceState != null) return;

        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        playerNumber = getIntent().getIntExtra(EXTRA_PLAYER_NUM, 1);
        playerName = getIntent().getStringExtra(EXTRA_PLAYER_NAME);

        if (roomId == null) {
            Toast.makeText(this, "Greška: Soba nije prosleđena.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomRef = FirebaseDatabase.getInstance().getReference().child("rooms").child(GAME_MECH).child(roomId);

        getSupportFragmentManager().setFragmentResultListener(
                "GAME_FINISHED", this, (requestKey, result) -> handleSubGameFinished());

        resolvePlayerNumberFromRoom(this::pratiStanjePartije);
    }

    private void pratiStanjePartije() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (!finalResultSaved) {
                        finish();
                    }
                    return;
                }

                String status = snapshot.child("status").getValue(String.class);
                if ("forfeit".equals(status)) {
                    roomRef.removeEventListener(this);
                    persistFinalResultAndFinish(true);
                    return;
                }

                String activeGameInDb = snapshot.child("currentGameType").getValue(String.class);
                if (activeGameInDb != null && !activeGameInDb.equals(currentSubGame)) {
                    currentSubGame = activeGameInDb;
                    showGameFragment(currentSubGame);
                } else if (getSupportFragmentManager().findFragmentById(R.id.game_fragment_container) == null) {
                    showGameFragment(currentSubGame);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Greška pri slušanju sobe: " + error.getMessage());
            }
        };
        roomRef.addValueEventListener(roomListener);
    }

    private void handleSubGameFinished() {
        if (GAME_KO_ZNA_ZNA.equals(currentSubGame)) {
            if (playerNumber == 1) roomRef.child("currentGameType").setValue(GAME_SPOJNICE);

        } else if (GAME_SPOJNICE.equals(currentSubGame)) {
            if (playerNumber == 1) roomRef.child("currentGameType").setValue(GAME_ASOCIJACIJE);

        } else if (GAME_ASOCIJACIJE.equals(currentSubGame)) {
            if (playerNumber == 1) roomRef.child("currentGameType").setValue(GAME_SKOCKO);

        } else if (GAME_SKOCKO.equals(currentSubGame)) {
            if (playerNumber == 1) roomRef.child("currentGameType").setValue(GAME_KORAK);

        } else if (GAME_KORAK.equals(currentSubGame)) {
            if (playerNumber == 1) roomRef.child("currentGameType").setValue(GAME_MOJ_BROJ);

        } else if (GAME_MOJ_BROJ.equals(currentSubGame)) {
            if (roomListener != null) roomRef.removeEventListener(roomListener);
            persistFinalResultAndFinish(false);
        }
    }

    private void resolvePlayerNumberFromRoom(Runnable onReady) {
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                onReady.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onReady.run();
            }
        });
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
        } else if (GAME_KO_ZNA_ZNA.equals(gameType)) {
            fragment = new KoZnaZnaMultiplayerFragment();
        } else if (GAME_SPOJNICE.equals(gameType)) {
            fragment = new SpojniceMultiplayerFragment();
        } else if (GAME_ASOCIJACIJE.equals(gameType)) {
            fragment = new AsocijacijeMultiplayerFragment();
        } else {
            fragment = new KorakPoKorakFragment();
        }

        fragment.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.game_fragment_container, fragment)
                .commitAllowingStateLoss();
    }

    public void forfeitMatch() {
        if (hasForfeited || roomId == null) return;
        hasForfeited = true;

        if (roomListener != null) roomRef.removeEventListener(roomListener);

        String myLeftKey = "player" + playerNumber + "Left"; // npr. player1Left
        String opponentKey = playerNumber == 1 ? "player2" : "player1";

        Map<String, Object> update = new HashMap<>();
        update.put(myLeftKey, true);

        update.put("status", "forfeit");
        update.put("forfeitBy", "player" + playerNumber);
        update.put("winner", opponentKey);

        roomRef.updateChildren(update);

        primenikaznuZbogNapustanja();
    }

    private void primenikaznuZbogNapustanja() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                if (userSnapshot.exists()) {
                    int trenutneZvezde = userSnapshot.child("stars").getValue(Integer.class) != null ?
                            userSnapshot.child("stars").getValue(Integer.class) : 0;

                    int noveZvezde = Math.max(0, trenutneZvezde - 10);
                    userRef.child("stars").setValue(noveZvezde);

                    Toast.makeText(GameActivity.this, "Napustili ste partiju. Izgubili ste 10 zvezda.", Toast.LENGTH_SHORT).show();
                }
                finish();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                finish();
            }
        });
    }

    private void persistFinalResultAndFinish(boolean forfeit) {
        if (finalResultSaved || roomId == null) {
            finish();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Rezultat nije sačuvan: korisnik nije prijavljen.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        finalResultSaved = true;
        long finishedAt = System.currentTimeMillis();

        String firestoreCollection = "slagalica_match_results";

        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    finish();
                    return;
                }

                boolean isFriendly = snapshot.child("isFriendly").getValue(Boolean.class) != null
                        && snapshot.child("isFriendly").getValue(Boolean.class);

                GameResult result = buildGameResult(snapshot, GAME_MECH, finishedAt, forfeit);

                gameResultRepository.saveGameResult(result, firestoreCollection)
                        .addOnSuccessListener(documentReference -> {
                            if (!isFriendly) {
                                primeniEkonomijuZvezdaITokena(result, currentUser.getUid());
                            } else {
                                Toast.makeText(GameActivity.this, "Prijateljska partija završena.", Toast.LENGTH_LONG).show();
                                ocistiSobuIAzurnoZavrsi();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Greška pri čuvanju u Firestore", e);
                            ocistiSobuIAzurnoZavrsi();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                finish();
            }
        });
    }

    private void primeniEkonomijuZvezdaITokena(GameResult result, String currentUid) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                if (!userSnapshot.exists()) {
                    ocistiSobuIAzurnoZavrsi();
                    return;
                }

                int trenutneZvezde = userSnapshot.child("stars").getValue(Integer.class) != null ?
                        userSnapshot.child("stars").getValue(Integer.class) : 0;
                int trenutniTokeni = userSnapshot.child("tokens").getValue(Integer.class) != null ?
                        userSnapshot.child("tokens").getValue(Integer.class) : 0;

                int mojSkor = currentUid.equals(result.player1Uid) ? result.player1Score : result.player2Score;

                boolean samJaPobednik = currentUid.equals(result.winnerUid);
                boolean jeNereseno = (result.winnerUid == null);

                int finalneZvezde;
                int razlikaZvezda;

                int bodovneZvezde = mojSkor / 40;

                if (jeNereseno) {
                    razlikaZvezda = bodovneZvezde;
                    finalneZvezde = Math.max(0, trenutneZvezde + razlikaZvezda);
                } else {
                    Map<String, Integer> epilog = StatsCalculator.obradiKrajPartije(samJaPobednik, mojSkor, trenutneZvezde);
                    if (epilog != null && epilog.containsKey("finalStars") && epilog.containsKey("starDifference")) {
                        finalneZvezde = epilog.get("finalStars");
                        razlikaZvezda = epilog.get("starDifference");
                    } else {
                        if (samJaPobednik) {
                            razlikaZvezda = 10 + bodovneZvezde;
                        } else {
                            razlikaZvezda = -10 + bodovneZvezde;
                        }
                        finalneZvezde = Math.max(0, trenutneZvezde + razlikaZvezda);
                    }
                }

                int stariTokeniIzZvezda = trenutneZvezde / 50;
                int noviTokeniIzZvezda = finalneZvezde / 50;
                int nagradniTokeni = Math.max(0, noviTokeniIzZvezda - stariTokeniIzZvezda);

                Map<String, Object> updates = new HashMap<>();
                updates.put("stars", finalneZvezde);
                updates.put("tokens", trenutniTokeni + nagradniTokeni);

                userRef.updateChildren(updates).addOnSuccessListener(unused -> {
                    String poruka = jeNereseno ? "Nerešeno! " : (samJaPobednik ? "Pobeda! " : "Poraz! ");
                    poruka += (razlikaZvezda >= 0 ? "+" : "") + razlikaZvezda + " zvezda.";

                    if (nagradniTokeni > 0) {
                        poruka += " Osvojili ste " + nagradniTokeni + " token(a) zbog prelaska praga od 50 zvezda!";
                    }
                    Toast.makeText(GameActivity.this, poruka, Toast.LENGTH_LONG).show();

                    ocistiSobuIAzurnoZavrsi();
                }).addOnFailureListener(e -> ocistiSobuIAzurnoZavrsi());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                ocistiSobuIAzurnoZavrsi();
            }
        });
    }

    private void ocistiSobuIAzurnoZavrsi() {
        if (roomId != null) {
            if (hasForfeited || playerNumber == 1) {
                roomRef.removeValue().addOnCompleteListener(t -> finish());
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

    private GameResult buildGameResult(DataSnapshot snapshot, String gameType, long finishedAt, boolean forfeit) {
        String player1Name = snapshot.child("player1").getValue(String.class);
        String player2Name = snapshot.child("player2").getValue(String.class);
        String player1Uid = snapshot.child("player1Uid").getValue(String.class);
        String player2Uid = snapshot.child("player2Uid").getValue(String.class);

        int player1Score = readInt(snapshot.child("scores").child("player1").getValue(), 0);
        int player2Score = readInt(snapshot.child("scores").child("player2").getValue(), 0);
        long startedAt = readLong(snapshot.child("startedAt").getValue(), finishedAt);

        String winnerUid;
        if (forfeit) {
            Boolean p1Left = snapshot.child("player1Left").getValue(Boolean.class);
            if (p1Left != null && p1Left) {
                winnerUid = player2Uid;
            } else {
                winnerUid = player1Uid;
            }
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
                6
        );
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private long readLong(Object value, long fallback) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomRef != null && roomListener != null) {
            roomRef.removeEventListener(roomListener);
        }
    }
}