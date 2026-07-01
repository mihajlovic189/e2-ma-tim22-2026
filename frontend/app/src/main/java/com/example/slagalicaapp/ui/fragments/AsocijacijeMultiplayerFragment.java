package com.example.slagalicaapp.ui.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.AsocijacijeManager;
import com.example.slagalicaapp.databinding.FragmentAsocijacijeBinding;
import com.example.slagalicaapp.game.asocijacije.Asocijacija;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeBoard;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeGuessResult;
import com.example.slagalicaapp.game.asocijacije.AsocijacijeRepository;
import com.example.slagalicaapp.ui.header.GameHeaderController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AsocijacijeMultiplayerFragment extends Fragment implements AsocijacijeManager.AsocijacijeListener {

    private static final long GAME_DURATION_MS  = 120_000L;
    private static final long GUESS_DURATION_MS =  20_000L;

    private FragmentAsocijacijeBinding binding;
    private GameHeaderController headerController;
    private AsocijacijeManager manager;

    // Static board data (set once on onGameReady)
    private String[][] fields;
    private String[] columnSolutions;
    private String   finalSolution;

    // [col][row] → Button
    private Button[][] fieldButtons;

    // Game args
    private String roomId;
    private int myPlayerNumber;
    private boolean isCoordinator;
    private boolean isGameOver = false;

    // Live board state (from RTDB)
    private Set<String>          openedFieldKeys = new HashSet<>();
    private Map<String, Integer> columnsSolvedBy = new HashMap<>();
    private int  finalSolvedBy = 0;
    private int  p1Score = 0;
    private int  p2Score = 0;
    private boolean iAmActive    = false;
    private boolean inputFrozen  = false;

    // Turn phase state
    private String currentTurnPhase  = "opening"; // "opening" | "guessing"
    private long   currentTurnEndsAt = 0;

    // Player names (needed for GameHeaderController recreation)
    private String p1PlayerName = "Igrač 1";
    private String p2PlayerName = "Igrač 2";
    private long   gameEndsAt   = 0;

    private boolean isStandaloneMode = false;
    private int standaloneScore = 0;
    private int cumulativePoints = 0;

    // Timers
    private CountDownTimer guessTimer;
    private boolean mainTimerPaused = false;
    private TextView headerTimerView; // direct reference for guess-phase display

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAsocijacijeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            roomId           = args.getString("roomId");
            myPlayerNumber   = args.getInt("playerNumber", 1);
            cumulativePoints = args.getInt("cumulativePoints", 0);
        }
        isCoordinator = (myPlayerNumber == 1);

        headerTimerView = binding.getRoot().findViewById(R.id.header_timer);

        buildFieldButtonsArray();
        setupFieldButtonListeners();
        setupColumnInputListeners();
        setupFinalInputListener();

        disableAllInput();

        if (roomId == null) {
            isStandaloneMode = true;
            setupStandaloneGame();
        } else {
            manager = new AsocijacijeManager(roomId, myPlayerNumber, this);
            manager.startListening();
        }
    }

    // ─── AsocijacijeListener ────────────────────────────────────────────────

    @Override
    public void onGameReady(String p1Name, String p2Name,
                            String[][] boardFields, String[] colSols, String finalSol,
                            long gameEndsAtParam) {
        requireActivity().runOnUiThread(() -> {
            // Reset per-round board state for a clean slate each round
            openedFieldKeys = new HashSet<>();
            columnsSolvedBy = new HashMap<>();
            finalSolvedBy   = 0;
            clearEditTextsForNewRound();

            fields          = boardFields;
            columnSolutions = colSols;
            finalSolution   = finalSol;
            p1PlayerName    = p1Name;
            p2PlayerName    = p2Name;
            gameEndsAt      = gameEndsAtParam;

            long remaining = gameEndsAtParam - System.currentTimeMillis();
            if (remaining < 1_000) remaining = 1_000;
            if (remaining > GAME_DURATION_MS) remaining = GAME_DURATION_MS;

            if (headerController != null) headerController.release();
            headerController = new GameHeaderController(binding.getRoot(), remaining);
            headerController.setPlayerNames(p1Name, p2Name);
            headerController.setScores(isStandaloneMode ? cumulativePoints : 0, 0);
            headerController.setOnTimerFinishedListener(this::onGameTimerExpired);
            headerController.start();
        });
    }

    @Override
    public void onStateChanged(int activePlayer,
                               String turnPhase,
                               long turnEndsAt,
                               Set<String> newOpened,
                               Map<String, Integer> newColsSolved,
                               int newFinalSolvedBy,
                               int newFinalGuessAttempts,
                               int newP1Score, int newP2Score) {
        requireActivity().runOnUiThread(() -> {
            if (isGameOver) return;
            currentTurnPhase  = turnPhase;
            currentTurnEndsAt = turnEndsAt;
            iAmActive         = (activePlayer == myPlayerNumber);
            openedFieldKeys   = new HashSet<>(newOpened);
            columnsSolvedBy   = new HashMap<>(newColsSolved);
            finalSolvedBy     = newFinalSolvedBy;
            p1Score           = newP1Score;
            p2Score           = newP2Score;
            inputFrozen       = false;

            if (headerController != null) headerController.setScores(p1Score, p2Score);
            renderBoardState();
            updateInputEnabled();
            updateGuessTimer();
        });
    }

    @Override
    public void onGameFinished(int p1Sc, int p2Sc, String forfeitBy) {
        requireActivity().runOnUiThread(() -> {
            if (isGameOver) return;
            isGameOver = true;
            cancelGuessTimer();
            disableAllInput();
            if (headerController != null) {
                headerController.stop();
                headerController.setScores(p1Sc, p2Sc);
            }

            String msg;
            if (forfeitBy != null) {
                msg = ("player" + myPlayerNumber).equals(forfeitBy)
                        ? "Predao si." : "Protivnik je predao!";
            } else if (p1Sc > p2Sc) {
                msg = (myPlayerNumber == 1) ? "Pobedio si! 🎉" : "Izgubio si.";
            } else if (p2Sc > p1Sc) {
                msg = (myPlayerNumber == 2) ? "Pobedio si! 🎉" : "Izgubio si.";
            } else {
                msg = "Nerešeno!";
            }
            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();

            Bundle result = new Bundle();
            result.putInt("points", isStandaloneMode ? standaloneScore : 0);
            result.putBoolean("finished", true);
            getParentFragmentManager().setFragmentResult("GAME_FINISHED", result);
        });
    }

    @Override
    public void onError(String message) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show());
    }

    // ─── Game timer expiry (120s) ────────────────────────────────────────────

    private void onGameTimerExpired() {
        if (isGameOver) return;
        disableAllInput();
        if (isStandaloneMode) {
            onGameFinished(standaloneScore, 0, null);
        } else {
            String winner = p1Score > p2Score ? "player1"
                          : p2Score > p1Score ? "player2" : "draw";
            manager.finishRoundByTimeout(winner);
        }
    }

    // ─── Guess timer (20s per turn) ──────────────────────────────────────────

    private void updateGuessTimer() {
        if ("guessing".equals(currentTurnPhase) && currentTurnEndsAt > 0) {
            long remaining = currentTurnEndsAt - System.currentTimeMillis();
            if (remaining > 0) {
                startGuessTimer(remaining);
            } else {
                cancelGuessTimer();
                if (mainTimerPaused) resumeMainTimer();
                if (iAmActive && !isGameOver) onGuessTimerExpired();
            }
        } else {
            cancelGuessTimer();
            if (mainTimerPaused) resumeMainTimer();
        }
    }

    private void startGuessTimer(long durationMs) {
        if (guessTimer != null) { guessTimer.cancel(); guessTimer = null; }

        long mainTimeRemaining = gameEndsAt - System.currentTimeMillis();
        if (mainTimeRemaining <= 0) {
            onGameTimerExpired();
            return;
        }

        if (!mainTimerPaused) {
            mainTimerPaused = true;
            if (headerController != null) headerController.stop();
        }
        if (headerTimerView != null)
            headerTimerView.setText(String.valueOf((int) Math.ceil(durationMs / 1000.0)));

        guessTimer = new CountDownTimer(durationMs, 200) {
            @Override
            public void onTick(long ms) {
                if (!isAdded()) return;
                if (headerTimerView != null)
                    headerTimerView.setText(String.valueOf((int) Math.ceil(ms / 1000.0)));
            }
            @Override
            public void onFinish() {
                guessTimer = null;
                if (isAdded()) resumeMainTimer();
                if (iAmActive && !isGameOver) onGuessTimerExpired();
            }
        }.start();
    }

    private void cancelGuessTimer() {
        if (guessTimer != null) { guessTimer.cancel(); guessTimer = null; }
    }

    private void resumeMainTimer() {
        mainTimerPaused = false;
        if (binding == null || !isAdded()) return;
        if (headerController != null) headerController.release();
        long remaining = Math.max(0L, gameEndsAt - System.currentTimeMillis());
        headerController = new GameHeaderController(binding.getRoot(), remaining);
        headerController.setPlayerNames(p1PlayerName, p2PlayerName);
        headerController.setScores(p1Score, p2Score);
        headerController.setOnTimerFinishedListener(this::onGameTimerExpired);
        headerController.start();
    }

    private void onGuessTimerExpired() {
        if (!iAmActive || isGameOver) return;
        freezeInput();
        if (isStandaloneMode) {
            currentTurnPhase = "opening";
            iAmActive = true;
            inputFrozen = false;
            if (mainTimerPaused) resumeMainTimer();
            updateInputEnabled();
            return;
        }
        Map<String, Object> upd = new HashMap<>();
        upd.put("activePlayer", 3 - myPlayerNumber);

        boolean allFieldsAreOpen = areAllFieldsOpen();
        if (allFieldsAreOpen || columnsSolvedBy.size() >= 4) {
            upd.put("turnPhase",   "guessing");
            upd.put("turnEndsAt",  System.currentTimeMillis() + GUESS_DURATION_MS);
        } else {
            upd.put("turnPhase",   "opening");
            upd.put("turnEndsAt",  0L);
        }
        manager.commitAction(upd);
    }

    // ─── Field buttons ───────────────────────────────────────────────────────

    private void onFieldClicked(int col, int row) {
        if (!iAmActive || isGameOver || inputFrozen) return;
        if (!"opening".equals(currentTurnPhase)) return; // only allowed before first opening

        String key = fieldKey(col, row);
        if (openedFieldKeys.contains(key)) return;
        if (columnsSolvedBy.containsKey(colLetter(col))) return;

        freezeInput();
        revealFieldButton(fieldButtons[col][row], fields[col][row]);
        openedFieldKeys.add(key);

        if (isStandaloneMode) {
            currentTurnPhase = "guessing";
            currentTurnEndsAt = System.currentTimeMillis() + GUESS_DURATION_MS;
            iAmActive = true;
            inputFrozen = false;
            updateInputEnabled();
            startGuessTimer(GUESS_DURATION_MS);
        } else {
            Map<String, Object> upd = new HashMap<>();
            upd.put("openedFields/" + key, true);
            upd.put("turnPhase",  "guessing");
            upd.put("turnEndsAt", System.currentTimeMillis() + GUESS_DURATION_MS);
            manager.commitAction(upd);
        }
    }

    // ─── Column guess ────────────────────────────────────────────────────────

    private void handleColumnGuess(int col, String attempt) {
        if (!iAmActive || isGameOver || inputFrozen) return;
        if (!"guessing".equals(currentTurnPhase)) return;
        if (TextUtils.isEmpty(attempt)) return;

        String colLetter = colLetter(col);
        if (columnsSolvedBy.containsKey(colLetter)) return;

        freezeInput();
        getEditTextForColumn(col).setText("");

        if (isStandaloneMode) {
            boolean correct = attempt.trim().equalsIgnoreCase(columnSolutions[col].trim());
            if (correct) {
                columnsSolvedBy.put(colLetter, 1);
                standaloneScore += 10;
                for (int row = 0; row < 4; row++) openedFieldKeys.add(fieldKey(col, row));
                onColumnSolvedUI(col);
                if (headerController != null) headerController.setScores(cumulativePoints + standaloneScore, 0);
            }
            cancelGuessTimer();
            if (mainTimerPaused) resumeMainTimer();
            currentTurnPhase = "opening";
            iAmActive = true;
            inputFrozen = false;
            updateInputEnabled();
        } else {
            manager.submitColumnGuessAtomic(myPlayerNumber, colLetter, attempt, columnSolutions[col], GUESS_DURATION_MS);
        }
    }

    // ─── Final guess ─────────────────────────────────────────────────────────

    private void handleFinalGuess(String attempt) {
        if (!iAmActive || isGameOver || inputFrozen) return;
        if (!"guessing".equals(currentTurnPhase)) return;
        if (TextUtils.isEmpty(attempt)) return;
        if (finalSolvedBy > 0) return;

        freezeInput();
        binding.etFinalSolution.setText("");

        if (isStandaloneMode) {
            boolean correct = attempt.trim().equalsIgnoreCase(finalSolution.trim());
            if (correct) {
                standaloneScore += 20;
                finalSolvedBy = 1;
                binding.etFinalSolution.setText(finalSolution);
                if (headerController != null) headerController.setScores(cumulativePoints + standaloneScore, 0);
                onGameFinished(standaloneScore, 0, null);
            } else {
                iAmActive = true;
                inputFrozen = false;
                updateInputEnabled();
            }
        } else {
            manager.submitFinalGuessAtomic(myPlayerNumber, attempt, finalSolution, GUESS_DURATION_MS);
        }
    }

    // ─── Board reconstruction for local evaluation ───────────────────────────

    private boolean areAllFieldsOpen() {
        Set<String> totalRevealed = new HashSet<>(openedFieldKeys);
        for (String colLetter : columnsSolvedBy.keySet()) {
            for (int i = 1; i <= 4; i++) {
                totalRevealed.add(colLetter + i);
            }
        }
        return totalRevealed.size() >= 16;
    }

    private AsocijacijeBoard reconstructBoard() {
        AsocijacijeBoard b = new AsocijacijeBoard(fields, columnSolutions, finalSolution);
        for (String key : openedFieldKeys) {
            b.openField(colIndex(key.charAt(0)), key.charAt(1) - '1');
        }
        for (String colL : columnsSolvedBy.keySet()) {
            b.guessColumn(colIndex(colL.charAt(0)), columnSolutions[colIndex(colL.charAt(0))]);
        }
        return b;
    }

    // ─── UI rendering ────────────────────────────────────────────────────────

    private void clearEditTextsForNewRound() {
        if (binding == null) return;
        for (int col = 0; col < 4; col++) {
            EditText et = getEditTextForColumn(col);
            et.setText("");
            et.setBackgroundTintList(ColorStateList.valueOf(0xFFF0F0F0));
        }
        binding.etFinalSolution.setText("");
        binding.etFinalSolution.setBackgroundTintList(ColorStateList.valueOf(0xFF81D4FA));
    }

    private void renderBoardState() {
        if (fields == null) return;

        // Reset all field buttons to closed state before re-revealing.
        // Safe to do every render: Android batches view updates within one frame,
        // so the intermediate "all closed" state is never drawn on screen.
        for (int col = 0; col < 4; col++) {
            String letter = String.valueOf((char)('A' + col));
            for (int row = 0; row < 4; row++) {
                Button btn = fieldButtons[col][row];
                if (btn != null) {
                    btn.setText(letter + (row + 1));
                    btn.setTextColor(0xFFFFFFFF);
                    btn.setBackgroundTintList(null);
                }
            }
        }

        for (String key : openedFieldKeys) {
            int col = colIndex(key.charAt(0));
            int row = key.charAt(1) - '1';
            if (col < 0 || row < 0 || col >= 4 || row >= 4) continue;
            if (fieldButtons[col][row] != null)
                revealFieldButton(fieldButtons[col][row], fields[col][row]);
        }

        for (String colL : columnsSolvedBy.keySet()) {
            int col = colIndex(colL.charAt(0));
            if (col < 0 || col >= 4) continue;
            onColumnSolvedUI(col);
        }

        if (finalSolvedBy > 0) {
            binding.etFinalSolution.setText(finalSolution);
            binding.etFinalSolution.setEnabled(false);
            binding.etFinalSolution.setBackgroundTintList(ColorStateList.valueOf(0xFF81D4FA));
        }
    }

    private void onColumnSolvedUI(int col) {
        EditText et = getEditTextForColumn(col);
        et.setText(columnSolutions[col]);
        et.setEnabled(false);
        et.setBackgroundTintList(ColorStateList.valueOf(0xFF81D4FA));
        for (int row = 0; row < 4; row++) {
            if (fieldButtons[col][row] != null)
                revealFieldButton(fieldButtons[col][row], fields[col][row]);
        }
    }

    private void updateInputEnabled() {
        if (fields == null) return;
        boolean canAct = iAmActive && !isGameOver && !inputFrozen;
        boolean inGuessingPhase = "guessing".equals(currentTurnPhase);

        for (int col = 0; col < 4; col++) {
            boolean colSolved = columnsSolvedBy.containsKey(colLetter(col));
            for (int row = 0; row < 4; row++) {
                if (fieldButtons[col][row] == null) continue;
                String key = fieldKey(col, row);
                boolean alreadyOpen = openedFieldKeys.contains(key) || colSolved;
                // Field buttons: only clickable in opening phase (before first field opened)
                fieldButtons[col][row].setEnabled(canAct && !inGuessingPhase && !alreadyOpen);
            }
            // Column inputs: only during guessing phase
            getEditTextForColumn(col).setEnabled(canAct && inGuessingPhase && !colSolved);
        }
        // Final input: only during guessing phase
        binding.etFinalSolution.setEnabled(canAct && inGuessingPhase && finalSolvedBy == 0);
    }

    private void freezeInput() {
        inputFrozen = true;
        iAmActive   = false;
        updateInputEnabled();
    }

    private void disableAllInput() {
        cancelGuessTimer();
        mainTimerPaused = false;
        if (binding == null) return;
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                if (fieldButtons != null && fieldButtons[col][row] != null)
                    fieldButtons[col][row].setEnabled(false);
            }
            getEditTextForColumn(col).setEnabled(false);
        }
        binding.etFinalSolution.setEnabled(false);
        if (headerController != null) headerController.stop();
    }

    private void revealFieldButton(Button btn, String word) {
        btn.setText(word);
        btn.setEnabled(false);
        btn.setTextColor(0xFF2196F3);
        btn.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private void buildFieldButtonsArray() {
        fieldButtons = new Button[4][4];
        fieldButtons[0][0] = binding.btnA1; fieldButtons[0][1] = binding.btnA2;
        fieldButtons[0][2] = binding.btnA3; fieldButtons[0][3] = binding.btnA4;
        fieldButtons[1][0] = binding.btnB1; fieldButtons[1][1] = binding.btnB2;
        fieldButtons[1][2] = binding.btnB3; fieldButtons[1][3] = binding.btnB4;
        fieldButtons[2][0] = binding.btnC1; fieldButtons[2][1] = binding.btnC2;
        fieldButtons[2][2] = binding.btnC3; fieldButtons[2][3] = binding.btnC4;
        fieldButtons[3][0] = binding.btnD1; fieldButtons[3][1] = binding.btnD2;
        fieldButtons[3][2] = binding.btnD3; fieldButtons[3][3] = binding.btnD4;
    }

    private void setupFieldButtonListeners() {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                final int fc = col, fr = row;
                if (fieldButtons[col][row] != null)
                    fieldButtons[col][row].setOnClickListener(v -> onFieldClicked(fc, fr));
            }
        }
    }

    private void setupColumnInputListeners() {
        wireColumnInput(binding.etASolution, 0);
        wireColumnInput(binding.etBSolution, 1);
        wireColumnInput(binding.etCSolution, 2);
        wireColumnInput(binding.etDSolution, 3);
    }

    private void wireColumnInput(EditText et, int col) {
        et.setOnEditorActionListener((v, actionId, event) -> {
            handleColumnGuess(col, v.getText().toString());
            return true;
        });
    }

    private void setupFinalInputListener() {
        binding.etFinalSolution.setOnEditorActionListener((v, actionId, event) -> {
            handleFinalGuess(v.getText().toString());
            return true;
        });
    }

    // ─── Key helpers ─────────────────────────────────────────────────────────

    private static String fieldKey(int col, int row) {
        return String.valueOf((char)('A' + col)) + (row + 1);
    }

    private static String colLetter(int col) {
        return String.valueOf((char)('A' + col));
    }

    private static int colIndex(char letter) {
        return letter - 'A';
    }

    private int myScore() {
        return myPlayerNumber == 1 ? p1Score : p2Score;
    }

    private EditText getEditTextForColumn(int col) {
        switch (col) {
            case 0: return binding.etASolution;
            case 1: return binding.etBSolution;
            case 2: return binding.etCSolution;
            default: return binding.etDSolution;
        }
    }

    private void showToast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void setupStandaloneGame() {
        Asocijacija a = AsocijacijeRepository.getNasumicnaAsocijacija();
        String[][] boardFields = new String[4][4];
        String[] colSols = new String[4];
        String[] cols = {"A", "B", "C", "D"};
        for (int c = 0; c < 4; c++) {
            colSols[c] = a.resenjaKolona.get(cols[c]);
            for (int r = 0; r < 4; r++) {
                boardFields[c][r] = a.polja.get(cols[c] + (r + 1));
            }
        }
        String finalSol = a.konacnoResenje;
        long endsAt = System.currentTimeMillis() + GAME_DURATION_MS;

        myPlayerNumber = 1;
        isCoordinator = true;
        iAmActive = true;
        currentTurnPhase = "opening";

        onGameReady("Ti", "—", boardFields, colSols, finalSol, endsAt);
        if (headerController != null) {
            headerController.setOnTimerFinishedListener(this::onGameTimerExpired);
            headerController.start();
        }
        updateInputEnabled();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelGuessTimer();
        if (headerController != null) { headerController.release(); headerController = null; }
        if (manager != null) manager.stopListening();
        binding = null;
    }
}
