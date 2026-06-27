package com.example.slagalicaapp.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalicaapp.model.GameResult;
import com.example.slagalicaapp.repositories.DailyMissionsRepository;
import com.example.slagalicaapp.repositories.GameResultRepository;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.ChallengeManager; // DODATO
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GameActivity extends AppCompatActivity {

    private static final String TAG = "GameActivity";

    public static final String EXTRA_ROOM_ID   = "ROOM_ID";
    public static final String EXTRA_CHALLENGE_ID = "CHALLENGE_ID"; // DODATO
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
    private String challengeId; // DODATO
    private int playerNumber;
    private String currentSubGame = GAME_KO_ZNA_ZNA;
    private String playerName;
    private boolean hasForfeited = false;
    private boolean finalResultSaved = false;

    // Lokalni brojač poena za samostalno igranje u Izazovu
    private int ukupniPoeniIzazova = 0;

    private DatabaseReference roomRef;
    private ValueEventListener roomListener;

    private final GameResultRepository gameResultRepository = new GameResultRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        if (savedInstanceState != null) return;

        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        challengeId = getIntent().getStringExtra(EXTRA_CHALLENGE_ID); // DODATO
        playerNumber = getIntent().getIntExtra(EXTRA_PLAYER_NUM, 1);
        playerName = getIntent().getStringExtra(EXTRA_PLAYER_NAME);

        // PRILAGOĐENO: Soba može biti null ako je u pitanju asinhroni izazov
        if (roomId == null && challengeId == null) {
            Toast.makeText(this, "Greška: Identifikator partije nije prosleđen.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        getSupportFragmentManager().setFragmentResultListener(
                "GAME_FINISHED", this, (requestKey, result) -> {
                    // Ako fragment vraća sakupljene poene, dodajemo ih u zbir za izazov
                    if (result.containsKey("points")) {
                        ukupniPoeniIzazova += result.getInt("points");
                    }
                    handleSubGameFinished(result);
                });

        if (challengeId != null) {
            // Ako je u pitanju IZAZOV, igrač igra samostalno i odmah otvaramo prvu igru
            showGameFragment(currentSubGame);
        } else {
            // Standardni 1v1 multiplayer preko soba
            roomRef = FirebaseDatabase.getInstance().getReference().child("rooms").child(GAME_MECH).child(roomId);
            resolvePlayerNumberFromRoom(this::pratiStanjePartije);
        }
    }

    private void pratiStanjePartije() {
        if (roomRef == null) return;
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

    private void handleSubGameFinished(android.os.Bundle result) {
        // GRANANJE LOGIKE: Ako je izazov, igrač samostalno menja igre lokalno
        if (challengeId != null) {
            switch (currentSubGame) {
                case GAME_KO_ZNA_ZNA:
                    currentSubGame = GAME_SPOJNICE;
                    break;
                case GAME_SPOJNICE:
                    currentSubGame = GAME_ASOCIJACIJE;
                    break;
                case GAME_ASOCIJACIJE:
                    currentSubGame = GAME_SKOCKO;
                    break;
                case GAME_SKOCKO:
                    currentSubGame = GAME_KORAK;
                    break;
                case GAME_KORAK:
                    currentSubGame = GAME_MOJ_BROJ;
                    break;
                case GAME_MOJ_BROJ:
                    završiIzazovIUpisiRezultat();
                    return;
            }
            showGameFragment(currentSubGame);
        } else {
            // Standardna 1v1 multiplayer logika (sinhronizovana preko baze)
            if (GAME_MOJ_BROJ.equals(currentSubGame)) {
                if (roomListener != null) roomRef.removeEventListener(roomListener);
                if (result.getBoolean("iWon", false)) {
                    new DailyMissionsRepository().completeMission("winMatch", null);
                }
                persistFinalResultAndFinish(false);
                return;
            }
            if (playerNumber == 1) {
                String nextGame;
                if (GAME_KO_ZNA_ZNA.equals(currentSubGame))   nextGame = GAME_SPOJNICE;
                else if (GAME_SPOJNICE.equals(currentSubGame)) nextGame = GAME_ASOCIJACIJE;
                else if (GAME_ASOCIJACIJE.equals(currentSubGame)) nextGame = GAME_SKOCKO;
                else if (GAME_SKOCKO.equals(currentSubGame))   nextGame = GAME_KORAK;
                else nextGame = GAME_MOJ_BROJ;

                Map<String, Object> transition = new HashMap<>();
                transition.put("currentGameType", nextGame);
                transition.put("status", "playing");
                roomRef.updateChildren(transition);
            }
        }
    }

    /**
     * DODATO: Slanje finalnog rezultata za asinhroni Mini-Turnir Izazov
     */
    private void završiIzazovIUpisiRezultat() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && challengeId != null) {
            ChallengeManager manager = new ChallengeManager();
            manager.submitFinalScore(challengeId, user.getUid(), ukupniPoeniIzazova);
            Toast.makeText(this, "Izazov uspešno odigran! Ukupno poena: " + ukupniPoeniIzazova, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void resolvePlayerNumberFromRoom(Runnable onReady) {
        if (roomRef == null) {
            onReady.run();
            return;
        }
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String p1Uid = snapshot.child("player1Uid").getValue(String.class);
                String p2Uid = snapshot.child("player2Uid").getValue(String.class);

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                String uid = user != null ? user.getUid() : null;

                Boolean isFriendly = snapshot.child("isFriendly").getValue(Boolean.class);
                if (Boolean.TRUE.equals(isFriendly) && uid != null) {
                    new DailyMissionsRepository().completeMission("friendlyMatch", null);
                }

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
        args.putString("challengeId", challengeId);
        args.putInt("playerNumber", playerNumber);
        args.putInt("cumulativePoints", ukupniPoeniIzazova);

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
        if (challengeId != null) {
            završiIzazovIUpisiRezultat();
            return;
        }

        if (hasForfeited || roomId == null) return;
        hasForfeited = true;

        if (roomListener != null) roomRef.removeEventListener(roomListener);

        String myLeftKey = "player" + playerNumber + "Left";
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

        FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    long trenutneZvezde = doc.getLong("totalStars") != null ? doc.getLong("totalStars") : 0;
                    long noveZvezde = Math.max(0, trenutneZvezde - 10);
                    FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid())
                            .update("totalStars", noveZvezde);
                    Toast.makeText(GameActivity.this, "Napustili ste partiju. Izgubili ste 10 zvezda.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> finish());
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
                            DailyMissionsRepository missionsRepo = new DailyMissionsRepository();
                            if (!isFriendly) {
                                if (currentUser.getUid().equals(result.winnerUid)) {
                                    missionsRepo.completeMission("winMatch", new DailyMissionsRepository.MissionCallback() {
                                        @Override public void onSuccess(boolean newlyCompleted) {
                                            primeniEkonomijuZvezdaITokena(result, currentUser.getUid());
                                        }
                                        @Override public void onError(String error) {
                                            primeniEkonomijuZvezdaITokena(result, currentUser.getUid());
                                        }
                                    });
                                } else {
                                    primeniEkonomijuZvezdaITokena(result, currentUser.getUid());
                                }
                            } else {
                                missionsRepo.completeMission("friendlyMatch", null);
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
        FirebaseFirestore.getInstance().collection("users").document(currentUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        ocistiSobuIAzurnoZavrsi();
                        return;
                    }

                    int trenutneZvezde = doc.getLong("totalStars") != null ? doc.getLong("totalStars").intValue() : 0;
                    int trenutniTokeni = doc.getLong("tokenCount") != null ? doc.getLong("tokenCount").intValue() : 0;

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
                    updates.put("totalStars", finalneZvezde);
                    updates.put("tokenCount", trenutniTokeni + nagradniTokeni);

                    String currentWeek = getCurrentWeekId();
                    String currentMonth = getCurrentMonthId();
                    updates.put("cycleWeek", currentWeek);
                    updates.put("cycleMonth", currentMonth);
                    Long curWeekly = doc.getLong("weeklyStars");
                    Long curMonthly = doc.getLong("monthlyStars");
                    String docCycleWeek = doc.getString("cycleWeek");
                    String docCycleMonth = doc.getString("cycleMonth");
                    int baseWeekly = currentWeek.equals(docCycleWeek) && curWeekly != null ? curWeekly.intValue() : 0;
                    int baseMonthly = currentMonth.equals(docCycleMonth) && curMonthly != null ? curMonthly.intValue() : 0;
                    int newWeekly = Math.max(0, baseWeekly + razlikaZvezda);
                    int newMonthly = Math.max(0, baseMonthly + razlikaZvezda);
                    updates.put("weeklyStars", (long) newWeekly);
                    updates.put("monthlyStars", (long) newMonthly);

                    long baseWeeklyGames = currentWeek.equals(docCycleWeek) && doc.getLong("weeklyGamesPlayed") != null
                            ? doc.getLong("weeklyGamesPlayed") : 0L;
                    long baseMonthlyGames = currentMonth.equals(docCycleMonth) && doc.getLong("monthlyGamesPlayed") != null
                            ? doc.getLong("monthlyGamesPlayed") : 0L;
                    updates.put("weeklyGamesPlayed", baseWeeklyGames + 1);
                    updates.put("monthlyGamesPlayed", baseMonthlyGames + 1);
                    Long curGames = doc.getLong("totalGamesPlayed");
                    updates.put("totalGamesPlayed", (curGames != null ? curGames : 0) + 1);

                    if (samJaPobednik) {
                        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());
                        updates.put("dailyMissions.date", today);
                        updates.put("dailyMissions.winMatch", true);
                    }

                    final int finalRazlika = razlikaZvezda;
                    final int finalNagrada = nagradniTokeni;
                    final boolean finalPobeda = samJaPobednik;
                    final boolean finalNeres = jeNereseno;

                    FirebaseFirestore.getInstance().collection("users").document(currentUid)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                String poruka = finalNeres ? "Nerešeno! " : (finalPobeda ? "Pobeda! " : "Poraz! ");
                                poruka += (finalRazlika >= 0 ? "+" : "") + finalRazlika + " zvezda.";
                                if (finalNagrada > 0) {
                                    poruka += " Osvojili ste " + finalNagrada + " token(a) zbog prelaska praga od 50 zvezda!";
                                }
                                Toast.makeText(GameActivity.this, poruka, Toast.LENGTH_LONG).show();
                                ocistiSobuIAzurnoZavrsi();
                            })
                            .addOnFailureListener(e -> ocistiSobuIAzurnoZavrsi());
                })
                .addOnFailureListener(e -> ocistiSobuIAzurnoZavrsi());
    }

    private String getCurrentWeekId() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        int year = cal.get(Calendar.YEAR);
        return String.format(Locale.US, "%d-W%02d", year, week);
    }

    private String getCurrentMonthId() {
        return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new java.util.Date());
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