package com.example.slagalicaapp.ui.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.databinding.FragmentAsocijacijeBinding;
import com.example.slagalicaapp.game.asocijacije.Asocijacija;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeBoard;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeGuessResult;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeRepository;
import com.example.slagalicaapp.ui.header.GameHeaderController;

import java.util.HashMap;
import java.util.Map;

public class AsocijacijeFragment extends Fragment {

    private static final String TAG = "AsocijacijeFragment";

    /** Trajanje runde za Asocijacije po specifikaciji - 2 minuta. */
    private static final long ROUND_DURATION_MS = 120_000L;

    private FragmentAsocijacijeBinding binding;

    /** Kontroler za deljeni gornji GUI (Igrac 1 | Tajmer | Igrac 2). */
    private GameHeaderController headerController;

    /** Poslovna logika tabele (4 kolone × 4 polja + rešenja). */
    private AsocijacijeBoard board;

    /** Mapiranje ID dugmeta polja → [col, row] u board-u. */
    private Map<Integer, int[]> buttonToCoord;

    public AsocijacijeFragment() {}

    public static AsocijacijeFragment newInstance() {
        return new AsocijacijeFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAsocijacijeBinding.inflate(inflater, container, false);
        setupGameData();
        setupButtonCoordMapping();
        setupButtonClickListeners();
        setupSolutionInputs();
        setupGameHeader();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (headerController != null) {
            headerController.release();
            headerController = null;
        }
        binding = null;
    }

    // ----------------- DATA (hardkodovano za 1. KT) -----------------

    private void setupGameData() {
        Asocijacija asocijacija = AsocijacijeRepository.getNasumicnaAsocijacija();

        // Kolone i polja: [col][row]
        String[][] fields = new String[][] {
                /* A */ { asocijacija.polja.get("A1"), asocijacija.polja.get("A2"), asocijacija.polja.get("A3"), asocijacija.polja.get("A4") },
                /* B */ { asocijacija.polja.get("B1"), asocijacija.polja.get("B2"), asocijacija.polja.get("B3"), asocijacija.polja.get("B4") },
                /* C */ { asocijacija.polja.get("C1"), asocijacija.polja.get("C2"), asocijacija.polja.get("C3"), asocijacija.polja.get("C4") },
                /* D */ { asocijacija.polja.get("D1"), asocijacija.polja.get("D2"), asocijacija.polja.get("D3"), asocijacija.polja.get("D4") },
        };
        String[] columnSolutions = new String[] {
                asocijacija.resenjaKolona.get("A"),
                asocijacija.resenjaKolona.get("B"),
                asocijacija.resenjaKolona.get("C"),
                asocijacija.resenjaKolona.get("D")
        };
        String finalSolution = asocijacija.konacnoResenje;

        board = new AsocijacijeBoard(fields, columnSolutions, finalSolution);
        Log.d(TAG, "Asocijacije: tabela inicijalizovana iz repozitorijuma. Konačno rešenje: "
                + finalSolution);
    }

    private void setupButtonCoordMapping() {
        buttonToCoord = new HashMap<>();
        // Kolona A (col=0)
        buttonToCoord.put(R.id.btn_a1, new int[]{0, 0});
        buttonToCoord.put(R.id.btn_a2, new int[]{0, 1});
        buttonToCoord.put(R.id.btn_a3, new int[]{0, 2});
        buttonToCoord.put(R.id.btn_a4, new int[]{0, 3});
        // Kolona B (col=1)
        buttonToCoord.put(R.id.btn_b1, new int[]{1, 0});
        buttonToCoord.put(R.id.btn_b2, new int[]{1, 1});
        buttonToCoord.put(R.id.btn_b3, new int[]{1, 2});
        buttonToCoord.put(R.id.btn_b4, new int[]{1, 3});
        // Kolona C (col=2)
        buttonToCoord.put(R.id.btn_c1, new int[]{2, 0});
        buttonToCoord.put(R.id.btn_c2, new int[]{2, 1});
        buttonToCoord.put(R.id.btn_c3, new int[]{2, 2});
        buttonToCoord.put(R.id.btn_c4, new int[]{2, 3});
        // Kolona D (col=3)
        buttonToCoord.put(R.id.btn_d1, new int[]{3, 0});
        buttonToCoord.put(R.id.btn_d2, new int[]{3, 1});
        buttonToCoord.put(R.id.btn_d3, new int[]{3, 2});
        buttonToCoord.put(R.id.btn_d4, new int[]{3, 3});
    }

    // ----------------- BUTTONS (otvaranje polja) -----------------

    private void setupButtonClickListeners() {
        for (Map.Entry<Integer, int[]> e : buttonToCoord.entrySet()) {
            Button button = getButtonById(e.getKey());
            int[] coord = e.getValue();
            if (button == null) continue;

            button.setOnClickListener(v -> {
                if (board.isGameOver()) return;
                String word = board.openField(coord[0], coord[1]);
                if (word == null) return; // već otvoreno / kolona rešena / game over
                revealFieldButton(button, word);
                Log.d(TAG, "Polje otvoreno: col=" + coord[0]
                        + " row=" + coord[1] + " word=" + word);
            });
        }
    }

    /** Vizuelno označi dugme polja kao "otkriveno" i ispiši reč. */
    private void revealFieldButton(Button button, String word) {
        button.setText(word);
        button.setEnabled(false);
        button.setTextColor(0xFF2196F3);
        button.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
    }

    // ----------------- INPUTS (pogađanje rešenja) -----------------

    private void setupSolutionInputs() {
        // Rešenja kolona
        wireColumnInput(binding.etASolution, 0);
        wireColumnInput(binding.etBSolution, 1);
        wireColumnInput(binding.etCSolution, 2);
        wireColumnInput(binding.etDSolution, 3);

        // Konačno rešenje
        binding.etFinalSolution.setOnEditorActionListener((v, actionId, event) -> {
            // Bilo koja "akcija" (Done, Go, Enter...) okida proveru.
            handleFinalGuess(v.getText().toString());
            return true;
        });
    }

    private void wireColumnInput(EditText input, int colIndex) {
        input.setOnEditorActionListener((v, actionId, event) -> {
            handleColumnGuess(colIndex, v.getText().toString());
            return true;
        });
    }

    private void handleColumnGuess(int col, String attempt) {
        if (board == null || board.isGameOver()) return;
        if (TextUtils.isEmpty(attempt)) return;

        AsocijacijeGuessResult res = board.guessColumn(col, attempt);
        Log.d(TAG, "Pogađanje kolone " + col + " '" + attempt + "' → " + res);

        if (res.isCorrect()) {
            awardPoints(res.getPointsAwarded());
            onColumnSolved(col, board.getColumnSolution(col));
            showToast("Bravo! +" + res.getPointsAwarded() + " bodova");
        } else {
            showToast("Pogrešno");
            // očisti polje da igrač može lakše ponovo da pokuša
            getEditTextForColumn(col).setText("");
        }
    }

    private void handleFinalGuess(String attempt) {
        if (board == null || board.isGameOver()) return;
        if (TextUtils.isEmpty(attempt)) return;

        AsocijacijeGuessResult res = board.guessFinal(attempt);
        Log.d(TAG, "Pogađanje konačnog rešenja '" + attempt + "' → " + res);

        if (res.isCorrect()) {
            awardPoints(res.getPointsAwarded());
            // Otkrij sve ostalo radi vizuelne potvrde.
            revealEverythingForDisplay();
            binding.etFinalSolution.setText(board.getFinalSolution());
            binding.etFinalSolution.setEnabled(false);
            showToast("POBEDA! +" + res.getPointsAwarded() + " bodova");
            disableAllInput();
        } else {
            showToast("Pogrešno konačno rešenje");
            binding.etFinalSolution.setText("");
        }
    }

    /** Pozove se kad je rešenje kolone tačno pogođeno. */
    private void onColumnSolved(int col, String solutionText) {
        // 1) Ispiši rešenje u odgovarajući "et_X_solution" i disable.
        EditText sol = getEditTextForColumn(col);
        sol.setText(solutionText);
        sol.setEnabled(false);
        sol.setBackgroundTintList(ColorStateList.valueOf(0xFF81D4FA));

        // 2) Otkrij sva preostala (zatvorena) polja u toj koloni - sad su poznata.
        int[] buttonIdsByRow = getButtonIdsForColumn(col);
        for (int row = 0; row < AsocijacijeBoard.FIELDS_PER_COLUMN; row++) {
            Button btn = getButtonById(buttonIdsByRow[row]);
            if (btn == null) continue;
            // Otkrij reč preko getFieldText (ignoriše opened flag)
            String word = board.getFieldText(col, row);
            if (word != null) {
                revealFieldButton(btn, word);
            }
        }
    }

    /**
     * Kad je konačno rešenje pogođeno - vizuelno otkrij sva ne-otkrivena polja
     * i sve nepogađene kolone (samo radi UI prikaza, bez bodovanja).
     */
    private void revealEverythingForDisplay() {
        for (int col = 0; col < AsocijacijeBoard.NUM_COLUMNS; col++) {
            int[] btnIds = getButtonIdsForColumn(col);
            for (int row = 0; row < AsocijacijeBoard.FIELDS_PER_COLUMN; row++) {
                Button btn = getButtonById(btnIds[row]);
                if (btn != null) {
                    String w = board.getFieldText(col, row);
                    if (w != null) revealFieldButton(btn, w);
                }
            }
            if (!board.isColumnSolved(col)) {
                EditText sol = getEditTextForColumn(col);
                sol.setText(board.getColumnSolution(col));
                sol.setEnabled(false);
                sol.setBackgroundTintList(ColorStateList.valueOf(0xFFB0BEC5));
            }
        }
    }

    // ----------------- HEADER + TAJMER (120s za Asocijacije) -----------------

    private void setupGameHeader() {
        headerController = new GameHeaderController(binding.getRoot(), ROUND_DURATION_MS);
        headerController.setPlayerNames("IGRAČ 1", "IGRAČ 2");
        headerController.setOnTimerFinishedListener(this::onTimerExpired);
        headerController.start();
        Log.d(TAG, "Asocijacije: tajmer pokrenut na "
                + (ROUND_DURATION_MS / 1000) + "s.");
    }

    private void onTimerExpired() {
        Log.d(TAG, "Asocijacije: vreme isteklo.");
        if (board != null) {
            board.markTimeExpired();
        }
        showToast("Vreme je isteklo!");
        revealEverythingForDisplay();
        disableAllInput();
    }

    // ----------------- HELPERS -----------------

    private void awardPoints(int points) {
        if (headerController != null && points > 0) {
            headerController.addPointsToPlayer1(points);
        }
    }

    private void disableAllInput() {
        if (binding == null) return;
        // Polja (dugmad)
        for (Integer buttonId : buttonToCoord.keySet()) {
            Button b = getButtonById(buttonId);
            if (b != null) b.setEnabled(false);
        }
        // EditText-ovi
        binding.etASolution.setEnabled(false);
        binding.etBSolution.setEnabled(false);
        binding.etCSolution.setEnabled(false);
        binding.etDSolution.setEnabled(false);
        binding.etFinalSolution.setEnabled(false);
        if (headerController != null) {
            headerController.stop();
        }
    }

    private void showToast(String text) {
        if (getContext() != null) {
            Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private EditText getEditTextForColumn(int col) {
        switch (col) {
            case 0: return binding.etASolution;
            case 1: return binding.etBSolution;
            case 2: return binding.etCSolution;
            case 3: return binding.etDSolution;
            default: throw new IllegalArgumentException("Nepoznata kolona: " + col);
        }
    }

    private int[] getButtonIdsForColumn(int col) {
        switch (col) {
            case 0: return new int[]{ R.id.btn_a1, R.id.btn_a2, R.id.btn_a3, R.id.btn_a4 };
            case 1: return new int[]{ R.id.btn_b1, R.id.btn_b2, R.id.btn_b3, R.id.btn_b4 };
            case 2: return new int[]{ R.id.btn_c1, R.id.btn_c2, R.id.btn_c3, R.id.btn_c4 };
            case 3: return new int[]{ R.id.btn_d1, R.id.btn_d2, R.id.btn_d3, R.id.btn_d4 };
            default: throw new IllegalArgumentException("Nepoznata kolona: " + col);
        }
    }

    private Button getButtonById(Integer id) {
        if (id == R.id.btn_a1) return binding.btnA1;
        if (id == R.id.btn_a2) return binding.btnA2;
        if (id == R.id.btn_a3) return binding.btnA3;
        if (id == R.id.btn_a4) return binding.btnA4;

        if (id == R.id.btn_b1) return binding.btnB1;
        if (id == R.id.btn_b2) return binding.btnB2;
        if (id == R.id.btn_b3) return binding.btnB3;
        if (id == R.id.btn_b4) return binding.btnB4;

        if (id == R.id.btn_c1) return binding.btnC1;
        if (id == R.id.btn_c2) return binding.btnC2;
        if (id == R.id.btn_c3) return binding.btnC3;
        if (id == R.id.btn_c4) return binding.btnC4;

        if (id == R.id.btn_d1) return binding.btnD1;
        if (id == R.id.btn_d2) return binding.btnD2;
        if (id == R.id.btn_d3) return binding.btnD3;
        if (id == R.id.btn_d4) return binding.btnD4;

        return null;
    }
}
