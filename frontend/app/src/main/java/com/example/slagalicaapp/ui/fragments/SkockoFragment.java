package com.example.slagalicaapp.ui.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.game.skocko.GuessFeedback;
import com.example.slagalicaapp.game.skocko.SkockoRound;
import com.example.slagalicaapp.game.skocko.SkockoSymbol;
import com.example.slagalicaapp.model.GameResult;
import com.example.slagalicaapp.repositories.GameResultRepository;
import com.example.slagalicaapp.ui.header.GameHeaderController;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class SkockoFragment extends Fragment {

    private static final String GAME_TYPE = "SKOCKO";

    private final GameResultRepository gameResultRepository = new GameResultRepository();
    private long gameStartedAtMs = 0L;
    private boolean resultPersisted = false;
    private int player1Points = 0;
    private int player2Points = 0;
    private static final String TAG = "SkockoFragment";

    private int currentRow = 1;
    private int currentCol = 1;

    private static final int MAX_ROWS = 6;
    private static final int MAX_COLS = 4;

    private Button btnConfirmRow;
    private View rootView;

    /** Kontroler za deljeni gornji GUI (Igrac 1 | Tajmer | Igrac 2). */
    private GameHeaderController headerController;

    /** Poslovna logika jedne runde (6 pokušaja, nasumična kombinacija). */
    private SkockoRound round;

    /** Bijektivno mapiranje: ID dugmeta simbola → SkockoSymbol enum vrednost. */
    private Map<Integer, SkockoSymbol> buttonToSymbol;

    public SkockoFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_skocko, container, false);

        btnConfirmRow = rootView.findViewById(R.id.btn_confirm_row);

        setupSymbolMapping();
        setupGameLogic();
        setupButtons(rootView);
        setupConfirmButton();
        setupCellClickRemoving(rootView);
        setupGameHeader();

        return rootView;
    }

    /**
     * Mapira ID svakog dugmeta simbola na jedinstvenu SkockoSymbol enum vrednost.
     * Ovo mapiranje je bijektivno (1-na-1) i SAMO služi internoj logici za
     * poređenje pokušaja sa dobitnom kombinacijom. Stvarne ikonice koje
     * korisnik vidi vode ImageButton.setDrawable(...) - nezavisno od enum imena.
     */
    private void setupSymbolMapping() {
        buttonToSymbol = new HashMap<>();
        buttonToSymbol.put(R.id.btn_karo,   SkockoSymbol.SKOCKO);
        buttonToSymbol.put(R.id.btn_tref,   SkockoSymbol.KVADRAT);
        buttonToSymbol.put(R.id.btn_pik,    SkockoSymbol.KRUG);
        buttonToSymbol.put(R.id.btn_srce,   SkockoSymbol.SRCE);
        buttonToSymbol.put(R.id.btn_sova,   SkockoSymbol.TROUGAO);
        buttonToSymbol.put(R.id.btn_zvezda, SkockoSymbol.ZVEZDA);
    }

    private void setupGameLogic() {
        round = new SkockoRound();
        Log.d(TAG, "Skocko: nova runda. Dobitna kombinacija: "
                + java.util.Arrays.toString(round.revealSecret()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        persistAsocijacijeResult();
        // Vazno: zaustaviti CountDownTimer da ne pravi memory leak
        if (headerController != null) {
            headerController.release();
            headerController = null;
        }
    }

    // ----------------- HEADER + TAJMER (1. KT) -----------------

    private void setupGameHeader() {
        headerController = new GameHeaderController(rootView);
        headerController.setPlayerNames("IGRAČ 1", "IGRAČ 2");
        headerController.setOnTimerFinishedListener(this::onTimerExpired);
        headerController.start();
        gameStartedAtMs = System.currentTimeMillis();
        Log.d(TAG, "Skocko: tajmer pokrenut na 60s.");
    }

    /** Poziva se kad istekne vreme za rundu. */
    private void onTimerExpired() {
        Log.d(TAG, "Skocko: vreme isteklo.");
        if (getContext() != null) {
            Toast.makeText(getContext(),
                    "Vreme je isteklo!", Toast.LENGTH_SHORT).show();
        }
        // Onemoguci sva dugmad sa simbolima i potvrdu reda
        disableAllInput();
        persistAsocijacijeResult();
    }

    private void persistAsocijacijeResult() {
        if (resultPersisted) return;
        resultPersisted = true;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot save Asocijacije result: no authenticated user.");
            return;
        }

        String player1Uid = currentUser.getUid();
        String player1Name = currentUser.getDisplayName();
        if (player1Name == null || player1Name.trim().isEmpty()) {
            player1Name = "Igrac 1";
        }

        long finishedAt = System.currentTimeMillis();
        long durationMs = gameStartedAtMs > 0
                ? Math.max(0L, finishedAt - gameStartedAtMs)
                : 0L;

        GameResult result = new GameResult(
                GAME_TYPE,
                player1Uid,
                player1Name,
                player1Points,
                null,       // player2Uid
                null,       // player2Name
                player2Points,
                player1Uid, // winnerId
                finishedAt,
                durationMs,
                0           // askedQuestions — nema smisla za Asocijacije, stavi 0 ili broj otvorenih polja
        );

        gameResultRepository.saveGameResult(result, "skocko_results")
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "Asocijacije result saved: " + ref.getId());
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Rezultat sacuvan.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save Asocijacije result", e);
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Neuspesno cuvanje rezultata.", Toast.LENGTH_SHORT).show();
                });
    }

    private void disableAllInput() {
        if (rootView == null) return;
        int[] symbolButtons = {
                R.id.btn_karo, R.id.btn_srce, R.id.btn_tref,
                R.id.btn_pik, R.id.btn_sova, R.id.btn_zvezda
        };
        for (int id : symbolButtons) {
            ImageButton b = rootView.findViewById(id);
            if (b != null) b.setEnabled(false);
        }
        if (btnConfirmRow != null) btnConfirmRow.setEnabled(false);
    }

    private void setupButtons(View view) {
        int[] buttons = {
                R.id.btn_karo,
                R.id.btn_srce,
                R.id.btn_tref,
                R.id.btn_pik,
                R.id.btn_sova,
                R.id.btn_zvezda
        };

        for (int id : buttons) {
            ImageButton btn = view.findViewById(id);

            btn.setOnClickListener(v -> {
                if (currentRow > MAX_ROWS) return;
                if (currentCol > MAX_COLS) return;
                if (round != null && round.isFinished()) return;

                ImageView cell = getCellView(view, currentRow, currentCol);

                if (cell != null) {
                    cell.setImageDrawable(btn.getDrawable());
                    cell.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
                    // Sačuvaj koji simbol je u ćeliji - bitno za submitGuess()
                    cell.setTag(buttonToSymbol.get(btn.getId()));

                    currentCol++;

                    if (currentCol > MAX_COLS) {
                        btnConfirmRow.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    private void setupCellClickRemoving(View view) {
        for (int row = 1; row <= MAX_ROWS; row++) {
            for (int col = 1; col <= MAX_COLS; col++) {
                final int clickedRow = row;
                final int clickedCol = col;

                ImageView cell = getCellView(view, clickedRow, clickedCol);

                if (cell != null) {
                    cell.setOnClickListener(v -> {
                        if (clickedRow != currentRow) return;
                        if (clickedCol >= currentCol) return;
                        if (round != null && round.isFinished()) return;

                        ImageView clickedCell = (ImageView) v;

                        clickedCell.setImageDrawable(null);
                        clickedCell.setBackgroundTintList(
                                ColorStateList.valueOf(0xFFE3F2FD)
                        );
                        clickedCell.setTag(null);

                        currentCol = clickedCol;
                        btnConfirmRow.setVisibility(View.GONE);

                        clearCellsAfter(clickedCol);
                    });
                }
            }
        }
    }

    private void clearCellsAfter(int clickedCol) {
        for (int col = clickedCol + 1; col <= MAX_COLS; col++) {
            ImageView cell = getCellView(rootView, currentRow, col);

            if (cell != null) {
                cell.setImageDrawable(null);
                cell.setBackgroundTintList(
                        ColorStateList.valueOf(0xFFE3F2FD)
                );
                cell.setTag(null);
            }
        }
    }

    private void setupConfirmButton() {
        btnConfirmRow.setOnClickListener(v -> {
            // 1) Skupi 4 simbola iz tekućeg reda (čuvamo ih kroz setTag).
            SkockoSymbol[] guess = collectGuessFromCurrentRow();
            if (guess == null) {
                // Defenziva - confirm dugme se prikazuje tek kad je red pun,
                // pa ovde ne bi trebalo nikad da dođemo.
                Log.w(TAG, "POTVRDI pritisnut ali red nije popunjen.");
                return;
            }

            // 2) Predaj logici i dobij feedback (reds + yellows).
            GuessFeedback fb;
            try {
                fb = round.submitGuess(guess);
            } catch (IllegalStateException ex) {
                Log.w(TAG, "submitGuess odbijen: " + ex.getMessage());
                return;
            }
            Log.d(TAG, "Red " + currentRow + ": guess="
                    + java.util.Arrays.toString(guess)
                    + " → " + fb);

            // 3) Prikaži i oboji odgovarajući fbN view sa desne strane.
            renderFeedbackForRow(currentRow, fb.getReds(), fb.getYellows());

            // 4) Sakri "POTVRDI UNOS" do popunjavanja sledećeg reda.
            btnConfirmRow.setVisibility(View.GONE);

            // 5) Obradi stanje na nivou runde (pobeda/poraz/u toku).
            if (round.isWon()) {
                onRoundWon();
            } else if (round.isFinished()) {
                onRoundLost();
            } else {
                // Idemo na sledeći red.
                currentRow++;
                currentCol = 1;
            }
        });
    }

    /**
     * Čita simbole iz ćelija tekućeg reda preko {@code getTag()}. Vraća null
     * ako neka ćelija nema simbol (npr. nije popunjen ceo red).
     */
    private SkockoSymbol[] collectGuessFromCurrentRow() {
        SkockoSymbol[] arr = new SkockoSymbol[MAX_COLS];
        for (int col = 1; col <= MAX_COLS; col++) {
            ImageView cell = getCellView(rootView, currentRow, col);
            if (cell == null) return null;
            Object tag = cell.getTag();
            if (!(tag instanceof SkockoSymbol)) return null;
            arr[col - 1] = (SkockoSymbol) tag;
        }
        return arr;
    }

    /**
     * Učini fbN view vidljivim i oboji 4 tačke prema reds/yellows.
     * Tačke se obojavaju redom: prvo N zelenih (na mestu), zatim M žutih
     * (van mesta), pa preostale plave (nije u kombinaciji).
     */
    private void renderFeedbackForRow(int row, int reds, int yellows) {
        int[] feedbackIds = {
                R.id.fb1, R.id.fb2, R.id.fb3,
                R.id.fb4, R.id.fb5, R.id.fb6
        };
        if (row < 1 || row > MAX_ROWS) return;
        View fbRoot = rootView.findViewById(feedbackIds[row - 1]);
        if (fbRoot == null) return;

        fbRoot.setVisibility(View.VISIBLE);
        paintFeedbackDots(fbRoot, reds, yellows);
    }

    /**
     * Pretpostavlja strukturu iz item_skocko_feedback.xml:
     * outer LinearLayout (vertikalni) → 2 inner LinearLayout-a (horizontalni),
     * svaki sa 2 View-a (tačke). Tačno 4 tačke ukupno.
     *
     * Boja:
     *   zelena (circle_feedback_green)  → pogodjen i na tačnom mestu (red)
     *   žuta   (circle_feedback_yellow) → pogodjen ali na pogrešnom mestu
     *   plava  (circle_feedback_blue)   → nije u dobitnoj kombinaciji
     */
    private void paintFeedbackDots(View fbRoot, int reds, int yellows) {
        if (!(fbRoot instanceof ViewGroup)) return;
        ViewGroup outer = (ViewGroup) fbRoot;
        if (outer.getChildCount() < 2) return;

        View topRow = outer.getChildAt(0);
        View bottomRow = outer.getChildAt(1);
        if (!(topRow instanceof ViewGroup) || !(bottomRow instanceof ViewGroup)) return;

        ViewGroup top = (ViewGroup) topRow;
        ViewGroup bot = (ViewGroup) bottomRow;
        if (top.getChildCount() < 2 || bot.getChildCount() < 2) return;

        View[] dots = new View[] {
                top.getChildAt(0),
                top.getChildAt(1),
                bot.getChildAt(0),
                bot.getChildAt(1)
        };

        // Sigurnosni clamp - zbir nikad ne sme preći 4.
        int redsClamped = Math.max(0, Math.min(reds, dots.length));
        int yellowsClamped = Math.max(0, Math.min(yellows, dots.length - redsClamped));

        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == null) continue;
            int drawableRes;
            if (i < redsClamped) {
                drawableRes = R.drawable.circle_feedback_green;
            } else if (i < redsClamped + yellowsClamped) {
                drawableRes = R.drawable.circle_feedback_yellow;
            } else {
                drawableRes = R.drawable.circle_feedback_blue;
            }
            dots[i].setBackgroundResource(drawableRes);
        }
    }

    private void onRoundWon() {
        int points = round.getEarnedPoints();
        Log.d(TAG, "Skocko: POBEDA u " + round.getWinningAttempt()
                + ". pokušaju, +" + points + " bodova.");
        if (headerController != null) {
            // Pretpostavka: trenutno igra Igrač 1 (1. KT - jedna runda).
            // Kad budeš dodavao multi-round/steal logiku, koristi
            // SkockoMatch.getActivePlayer() da odrediš kome dodaješ bodove.
            headerController.addPointsToPlayer1(points);
            headerController.stop();
        }
        if (getContext() != null) {
            Toast.makeText(getContext(),
                    "Bravo! +" + points + " bodova", Toast.LENGTH_LONG).show();
        }
        disableAllInput();
    }

    private void onRoundLost() {
        Log.d(TAG, "Skocko: PORAZ. Tačna kombinacija: "
                + java.util.Arrays.toString(round.revealSecret()));
        if (headerController != null) {
            headerController.stop();
        }
        if (getContext() != null) {
            Toast.makeText(getContext(),
                    "Iskorišćeni svi pokušaji.", Toast.LENGTH_LONG).show();
        }
        disableAllInput();
    }

    private ImageView getCellView(View view, int row, int col) {
        int[][] cellIds = {
                {R.id.r1c1, R.id.r1c2, R.id.r1c3, R.id.r1c4},
                {R.id.r2c1, R.id.r2c2, R.id.r2c3, R.id.r2c4},
                {R.id.r3c1, R.id.r3c2, R.id.r3c3, R.id.r3c4},
                {R.id.r4c1, R.id.r4c2, R.id.r4c3, R.id.r4c4},
                {R.id.r5c1, R.id.r5c2, R.id.r5c3, R.id.r5c4},
                {R.id.r6c1, R.id.r6c2, R.id.r6c3, R.id.r6c4}
        };

        if (row < 1 || row > MAX_ROWS || col < 1 || col > MAX_COLS) return null;

        return view.findViewById(cellIds[row - 1][col - 1]);
    }
}