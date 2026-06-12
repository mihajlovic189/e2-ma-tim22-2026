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
import com.example.slagalicaapp.model.GameResult;
import com.example.slagalicaapp.repositories.GameResultRepository;
import com.example.slagalicaapp.ui.header.GameHeaderController;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class AsocijacijeFragment extends Fragment {

    private static final String GAME_TYPE = "ASOCIJACIJE";

    private final GameResultRepository gameResultRepository = new GameResultRepository();
    private long gameStartedAtMs = 0L;
    private boolean resultPersisted = false;
    private int player1Points = 0;
    private int player2Points = 0;
    private static final String TAG = "AsocijacijeFragment";

    private static final long ROUND_DURATION_MS = 120_000L;

    private FragmentAsocijacijeBinding binding;
    private GameHeaderController headerController;
    private AsocijacijeBoard board;
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
        persistAsocijacijeResult();
        if (headerController != null) {
            headerController.release();
            headerController = null;
        }
        binding = null;
    }

    private void setupGameData() {
        Asocijacija asocijacija = AsocijacijeRepository.getNasumicnaAsocijacija();
        String[][] fields = new String[][] {
                { asocijacija.polja.get("A1"), asocijacija.polja.get("A2"), asocijacija.polja.get("A3"), asocijacija.polja.get("A4") },
                { asocijacija.polja.get("B1"), asocijacija.polja.get("B2"), asocijacija.polja.get("B3"), asocijacija.polja.get("B4") },
                { asocijacija.polja.get("C1"), asocijacija.polja.get("C2"), asocijacija.polja.get("C3"), asocijacija.polja.get("C4") },
                { asocijacija.polja.get("D1"), asocijacija.polja.get("D2"), asocijacija.polja.get("D3"), asocijacija.polja.get("D4") },
        };
        String[] columnSolutions = new String[] {
                asocijacija.resenjaKolona.get("A"),
                asocijacija.resenjaKolona.get("B"),
                asocijacija.resenjaKolona.get("C"),
                asocijacija.resenjaKolona.get("D")
        };
        String finalSolution = asocijacija.konacnoResenje;
        board = new AsocijacijeBoard(fields, columnSolutions, finalSolution);
        Log.d(TAG, "Asocijacije: tabela inicijalizovana. Konacno resenje: " + finalSolution);
    }

    private void setupButtonCoordMapping() {
        buttonToCoord = new HashMap<>();
        buttonToCoord.put(R.id.btn_a1, new int[]{0, 0});
        buttonToCoord.put(R.id.btn_a2, new int[]{0, 1});
        buttonToCoord.put(R.id.btn_a3, new int[]{0, 2});
        buttonToCoord.put(R.id.btn_a4, new int[]{0, 3});
        buttonToCoord.put(R.id.btn_b1, new int[]{1, 0});
        buttonToCoord.put(R.id.btn_b2, new int[]{1, 1});
        buttonToCoord.put(R.id.btn_b3, new int[]{1, 2});
        buttonToCoord.put(R.id.btn_b4, new int[]{1, 3});
        buttonToCoord.put(R.id.btn_c1, new int[]{2, 0});
        buttonToCoord.put(R.id.btn_c2, new int[]{2, 1});
        buttonToCoord.put(R.id.btn_c3, new int[]{2, 2});
        buttonToCoord.put(R.id.btn_c4, new int[]{2, 3});
        buttonToCoord.put(R.id.btn_d1, new int[]{3, 0});
        buttonToCoord.put(R.id.btn_d2, new int[]{3, 1});
        buttonToCoord.put(R.id.btn_d3, new int[]{3, 2});
        buttonToCoord.put(R.id.btn_d4, new int[]{3, 3});
    }

    private void setupButtonClickListeners() {
        for (Map.Entry<Integer, int[]> e : buttonToCoord.entrySet()) {
            Button button = getButtonById(e.getKey());
            int[] coord = e.getValue();
            if (button == null) continue;
            button.setOnClickListener(v -> {
                if (board.isGameOver()) return;
                String word = board.openField(coord[0], coord[1]);
                if (word == null) return;
                revealFieldButton(button, word);
            });
        }
    }

    private void revealFieldButton(Button button, String word) {
        button.setText(word);
        button.setEnabled(false);
        button.setTextColor(0xFF2196F3);
        button.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
    }

    private void setupSolutionInputs() {
        wireColumnInput(binding.etASolution, 0);
        wireColumnInput(binding.etBSolution, 1);
        wireColumnInput(binding.etCSolution, 2);
        wireColumnInput(binding.etDSolution, 3);
        binding.etFinalSolution.setOnEditorActionListener((v, actionId, event) -> {
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
        if (res.isCorrect()) {
            awardPoints(res.getPointsAwarded());
            onColumnSolved(col, board.getColumnSolution(col));
            showToast("Bravo! +" + res.getPointsAwarded() + " bodova");
        } else {
            showToast("Pogresno");
            getEditTextForColumn(col).setText("");
        }
    }

    private void handleFinalGuess(String attempt) {
        if (board == null || board.isGameOver()) return;
        if (TextUtils.isEmpty(attempt)) return;
        AsocijacijeGuessResult res = board.guessFinal(attempt);
        if (res.isCorrect()) {
            awardPoints(res.getPointsAwarded());
            revealEverythingForDisplay();
            binding.etFinalSolution.setText(board.getFinalSolution());
            binding.etFinalSolution.setEnabled(false);
            showToast("POBEDA! +" + res.getPointsAwarded() + " bodova");
            disableAllInput();
        } else {
            showToast("Pogresno konacno resenje");
            binding.etFinalSolution.setText("");
        }
    }

    private void onColumnSolved(int col, String solutionText) {
        EditText sol = getEditTextForColumn(col);
        sol.setText(solutionText);
        sol.setEnabled(false);
        sol.setBackgroundTintList(ColorStateList.valueOf(0xFF81D4FA));
        int[] buttonIdsByRow = getButtonIdsForColumn(col);
        for (int row = 0; row < AsocijacijeBoard.FIELDS_PER_COLUMN; row++) {
            Button btn = getButtonById(buttonIdsByRow[row]);
            if (btn == null) continue;
            String word = board.getFieldText(col, row);
            if (word != null) revealFieldButton(btn, word);
        }
    }

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
        if (!board.isFinalSolved()) {
            binding.etFinalSolution.setText(board.getFinalSolution());
            binding.etFinalSolution.setEnabled(false);
            binding.etFinalSolution.setBackgroundTintList(ColorStateList.valueOf(0xFFB0BEC5));
        }
    }

    private void setupGameHeader() {
        headerController = new GameHeaderController(binding.getRoot(), ROUND_DURATION_MS);
        headerController.setPlayerNames("IGRAC 1", "IGRAC 2");
        headerController.setOnTimerFinishedListener(this::onTimerExpired);
        headerController.start();
        gameStartedAtMs = System.currentTimeMillis();
        Log.d(TAG, "Asocijacije: tajmer pokrenut na " + (ROUND_DURATION_MS / 1000) + "s.");
    }

    private void onTimerExpired() {
        Log.d(TAG, "Asocijacije: vreme isteklo.");
        if (board != null) board.markTimeExpired();
        showToast("Vreme je isteklo!");
        revealEverythingForDisplay();
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
        if (player1Name == null || player1Name.trim().isEmpty()) player1Name = "Igrac 1";

        long finishedAt = System.currentTimeMillis();
        long durationMs = gameStartedAtMs > 0 ? Math.max(0L, finishedAt - gameStartedAtMs) : 0L;

        GameResult result = new GameResult(
                GAME_TYPE, player1Uid, player1Name, player1Points,
                null, null, player2Points,
                player1Uid, finishedAt, durationMs, 0
        );

        gameResultRepository.saveGameResult(result, "asocijacije_results")
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

    private void awardPoints(int points) {
        if (headerController != null && points > 0) {
            headerController.addPointsToPlayer1(points);
        }
    }

    private void disableAllInput() {
        if (binding == null) return;
        for (Integer buttonId : buttonToCoord.keySet()) {
            Button b = getButtonById(buttonId);
            if (b != null) b.setEnabled(false);
        }
        binding.etASolution.setEnabled(false);
        binding.etBSolution.setEnabled(false);
        binding.etCSolution.setEnabled(false);
        binding.etDSolution.setEnabled(false);
        binding.etFinalSolution.setEnabled(false);
        if (headerController != null) headerController.stop();
    }

    private void showToast(String text) {
        if (getContext() != null) Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
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
