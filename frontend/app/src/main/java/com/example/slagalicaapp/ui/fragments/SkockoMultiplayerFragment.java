package com.example.slagalicaapp.ui.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.SkockoManager;
import com.example.slagalicaapp.game.skocko.GuessFeedback;
import com.example.slagalicaapp.game.skocko.SkockoRound;
import com.example.slagalicaapp.game.skocko.SkockoSymbol;
import com.example.slagalicaapp.ui.header.GameHeaderController;

import java.util.HashMap;
import java.util.Map;

public class SkockoMultiplayerFragment extends Fragment {

    private static final int MAX_ROWS = 6;
    private static final int MAX_COLS = 4;
    private static final long MAIN_DURATION_MS  = 30_000L;
    private static final long STEAL_DURATION_MS = 10_000L;

    // Ordinal index → button ID (SKOCKO=0, KVADRAT=1, KRUG=2, SRCE=3, TROUGAO=4, ZVEZDA=5)
    private static final int[] ORDINAL_TO_BTN = {
            R.id.btn_karo, R.id.btn_tref, R.id.btn_pik,
            R.id.btn_srce, R.id.btn_sova, R.id.btn_zvezda
    };

    private View rootView;
    private Button btnConfirmRow;
    private GameHeaderController headerController;
    private TextView timerView;

    private String roomId;
    private int myPlayerNumber;
    private boolean isGameOver = false;

    private SkockoManager skockoManager;

    // Phase state
    private String currentPhase = "";
    private boolean iAmActive   = false;
    private boolean isStealPhase = false;

    // Secrets parsed from Firebase
    private int[] secret1Ordinals = new int[0];
    private int[] secret2Ordinals = new int[0];

    // Grid input state
    private int currentRow = 1;
    private int currentCol = 1;

    // Local round for active-player evaluation (null for passive player)
    private SkockoRound localRound;

    // Scores (kept in sync with Firebase)
    private int p1Score = 0;
    private int p2Score = 0;

    // Timers
    private CountDownTimer phaseTimer;
    private final Handler gameFinishHandler = new Handler();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_skocko, container, false);
        btnConfirmRow = rootView.findViewById(R.id.btn_confirm_row);
        timerView     = rootView.findViewById(R.id.header_timer);

        Bundle args = getArguments();
        if (args != null) {
            roomId         = args.getString("roomId");
            myPlayerNumber = args.getInt("playerNumber", 1);
        }

        headerController = new GameHeaderController(rootView);

        setupSymbolButtons();
        setupCellClickRemoving();
        setupConfirmButton();
        connectToFirebase();

        return rootView;
    }

    // ─────────────── Firebase ───────────────

    private void connectToFirebase() {
        if (roomId == null) return;

        skockoManager = new SkockoManager(roomId, new SkockoManager.SkockoListener() {

            @Override
            public void onRoomReady(String p1Name, String p2Name,
                                    String s1, String s2, int p1, int p2) {
                requireActivity().runOnUiThread(() -> {
                    headerController.setPlayerNames(p1Name, p2Name);
                    p1Score = p1;
                    p2Score = p2;
                    headerController.setScores(p1Score, p2Score);
                    secret1Ordinals = parseSecret(s1);
                    secret2Ordinals = parseSecret(s2);
                });
            }

            @Override
            public void onPhaseChanged(String phase, long phaseEndsAt, int p1Sc, int p2Sc) {
                requireActivity().runOnUiThread(() -> {
                    p1Score = p1Sc;
                    p2Score = p2Sc;
                    if (headerController != null) headerController.setScores(p1Score, p2Score);
                    handlePhaseChange(phase, phaseEndsAt);
                });
            }

            @Override
            public void onGuessAdded(String roundKey, int guessIndex,
                                     int[] symbols, int reds, int yellows) {
                requireActivity().runOnUiThread(() -> {
                    // Only render guesses for the current phase's round
                    String expected = currentPhase.startsWith("ROUND_1") ? "round1" : "round2";
                    if (!roundKey.equals(expected)) return;
                    renderGuess(guessIndex, symbols, reds, yellows);
                });
            }

            @Override
            public void onGameFinished(int p1, int p2, String forfeitBy) {
                requireActivity().runOnUiThread(() -> finishGame(p1, p2, forfeitBy));
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show());
            }
        });

        skockoManager.startListening();
    }

    // ─────────────── Phase logic ───────────────

    private void handlePhaseChange(String phase, long phaseEndsAt) {
        if (isGameOver) return;
        currentPhase = phase;

        if ("FINISHED".equals(phase)) {
            disableAllInput();
            return;
        }

        isStealPhase = phase.contains("STEAL");
        int activePlayer;
        int[] secret;
        switch (phase) {
            case "ROUND_1_PLAYING": activePlayer = 1; secret = secret1Ordinals; break;
            case "ROUND_1_STEAL":   activePlayer = 2; secret = secret1Ordinals; break;
            case "ROUND_2_PLAYING": activePlayer = 2; secret = secret2Ordinals; break;
            case "ROUND_2_STEAL":   activePlayer = 1; secret = secret2Ordinals; break;
            default: return;
        }

        iAmActive = (activePlayer == myPlayerNumber);
        localRound = iAmActive ? new SkockoRound(toSymbolArray(secret)) : null;

        clearGrid();
        currentRow = 1;
        currentCol = 1;
        btnConfirmRow.setVisibility(View.GONE);

        cancelPhaseTimer();
        long remaining = phaseEndsAt - System.currentTimeMillis();
        if (remaining > 0) startPhaseTimer(remaining);

        setSymbolButtonsEnabled(iAmActive);
        Toast.makeText(getContext(), phaseLabel(phase), Toast.LENGTH_LONG).show();
    }

    private void renderGuess(int guessIndex, int[] symbols, int reds, int yellows) {
        int row = guessIndex + 1;
        if (row < 1 || row > MAX_ROWS) return;

        for (int col = 1; col <= MAX_COLS; col++) {
            ImageView cell = getCellView(row, col);
            if (cell == null) continue;
            int ord = (col - 1 < symbols.length) ? symbols[col - 1] : 0;
            setCell(cell, ord);
        }

        renderFeedbackForRow(row, reds, yellows);

        // Advance cursor for input
        currentRow = guessIndex + 2;
        currentCol = 1;
        btnConfirmRow.setVisibility(View.GONE);

        // Re-enable input for active player (overridden by handlePhaseChange if phase changes)
        if (iAmActive && !isGameOver) {
            setSymbolButtonsEnabled(true);
        }
    }

    // ─────────────── Input setup ───────────────

    private void setupSymbolButtons() {
        for (int i = 0; i < ORDINAL_TO_BTN.length; i++) {
            final int ordinal = i;
            ImageButton btn = rootView.findViewById(ORDINAL_TO_BTN[i]);
            if (btn == null) continue;
            btn.setOnClickListener(v -> {
                if (!iAmActive || isGameOver) return;
                if (currentRow > MAX_ROWS) return;
                if (isStealPhase && currentRow > 1) return;
                if (currentCol > MAX_COLS) return;

                ImageView cell = getCellView(currentRow, currentCol);
                if (cell == null) return;
                setCell(cell, ordinal);
                cell.setTag(ordinal);
                currentCol++;

                if (currentCol > MAX_COLS) btnConfirmRow.setVisibility(View.VISIBLE);
            });
        }
    }

    private void setupCellClickRemoving() {
        for (int row = 1; row <= MAX_ROWS; row++) {
            for (int col = 1; col <= MAX_COLS; col++) {
                final int r = row, c = col;
                ImageView cell = getCellView(r, c);
                if (cell == null) continue;
                cell.setOnClickListener(v -> {
                    if (!iAmActive || isGameOver) return;
                    if (r != currentRow || c >= currentCol) return;
                    clearCell(cell);
                    currentCol = c;
                    btnConfirmRow.setVisibility(View.GONE);
                    for (int ac = c + 1; ac <= MAX_COLS; ac++) {
                        ImageView ac_cell = getCellView(currentRow, ac);
                        if (ac_cell != null) clearCell(ac_cell);
                    }
                });
            }
        }
    }

    private void setupConfirmButton() {
        btnConfirmRow.setOnClickListener(v -> {
            if (!iAmActive || isGameOver || localRound == null) return;

            int[] ordinals = collectOrdinals(currentRow);
            if (ordinals == null) return;

            GuessFeedback fb;
            try {
                fb = localRound.submitGuess(toSymbolArray(ordinals));
            } catch (IllegalStateException ex) {
                return;
            }

            int guessIndex = currentRow - 1;
            String roundKey = currentPhase.startsWith("ROUND_1") ? "round1" : "round2";
            Map<String, Object> phaseUpdate = buildPhaseUpdate(fb, guessIndex);

            skockoManager.writeGuessAndAdvance(roundKey, guessIndex, ordinals,
                    fb.getReds(), fb.getYellows(), phaseUpdate);

            btnConfirmRow.setVisibility(View.GONE);
            setSymbolButtonsEnabled(false); // Wait for Firebase echo
        });
    }

    // ─────────────── Phase-update builder ───────────────

    private Map<String, Object> buildPhaseUpdate(GuessFeedback fb, int guessIndex) {
        Map<String, Object> upd = new HashMap<>();
        boolean won = fb.isWin();
        boolean lastAttempt = isStealPhase || (guessIndex >= MAX_ROWS - 1);

        if (!won && !lastAttempt) return upd; // Intermediate guess — no phase change

        // Determine points and next phase
        int points = won ? (isStealPhase ? 10 : SkockoRound.scoreForAttempt(guessIndex + 1)) : 0;
        String nextPhase;

        switch (currentPhase) {
            case "ROUND_1_PLAYING":
                if (won) { p1Score += points; nextPhase = "ROUND_2_PLAYING"; }
                else     { nextPhase = "ROUND_1_STEAL"; }
                break;
            case "ROUND_1_STEAL":
                if (won) p2Score += points;
                nextPhase = "ROUND_2_PLAYING";
                break;
            case "ROUND_2_PLAYING":
                if (won) { p2Score += points; nextPhase = "FINISHED"; }
                else     { nextPhase = "ROUND_2_STEAL"; }
                break;
            case "ROUND_2_STEAL":
                if (won) p1Score += points;
                nextPhase = "FINISHED";
                break;
            default:
                return upd;
        }

        long nextEndsAt = computeNextEndsAt(nextPhase);
        upd.put("phase",       nextPhase);
        upd.put("phaseEndsAt", nextEndsAt);
        upd.put("scores/player1", p1Score);
        upd.put("scores/player2", p2Score);

        if ("FINISHED".equals(nextPhase)) {
            upd.put("winner", winner());
            upd.put("status", "game_finished");
            upd.put("finishedAt", System.currentTimeMillis());
        }
        return upd;
    }

    // ─────────────── Timer ───────────────

    private void startPhaseTimer(long durationMs) {
        phaseTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long ms) {
                if (timerView != null) timerView.setText(String.valueOf(ms / 1000));
            }
            @Override
            public void onFinish() {
                if (timerView != null) timerView.setText("0");
                if (iAmActive && !isGameOver) handleTimerExpired();
            }
        }.start();
    }

    private void handleTimerExpired() {
        if (isGameOver) return;
        Map<String, Object> upd = new HashMap<>();
        String nextPhase;

        switch (currentPhase) {
            case "ROUND_1_PLAYING": nextPhase = "ROUND_1_STEAL";  break;
            case "ROUND_1_STEAL":   nextPhase = "ROUND_2_PLAYING"; break;
            case "ROUND_2_PLAYING": nextPhase = "ROUND_2_STEAL";  break;
            case "ROUND_2_STEAL":   nextPhase = "FINISHED";        break;
            default: return;
        }

        long nextEndsAt = computeNextEndsAt(nextPhase);
        upd.put("phase",       nextPhase);
        upd.put("phaseEndsAt", nextEndsAt);
        upd.put("scores/player1", p1Score);
        upd.put("scores/player2", p2Score);

        if ("FINISHED".equals(nextPhase)) {
            upd.put("winner", winner());
            upd.put("status", "game_finished");
            upd.put("finishedAt", System.currentTimeMillis());
        }

        skockoManager.advancePhase(upd);
    }

    private long computeNextEndsAt(String phase) {
        if ("FINISHED".equals(phase)) return 0L;
        long dur = (phase.contains("STEAL")) ? STEAL_DURATION_MS : MAIN_DURATION_MS;
        return System.currentTimeMillis() + dur;
    }

    private void cancelPhaseTimer() {
        if (phaseTimer != null) { phaseTimer.cancel(); phaseTimer = null; }
    }

    // ─────────────── Game finished ───────────────

    private void finishGame(int p1, int p2, String forfeitBy) {
        isGameOver = true;
        p1Score = p1; p2Score = p2;
        headerController.setScores(p1Score, p2Score);
        cancelPhaseTimer();
        disableAllInput();

        String msg;
        if (forfeitBy != null && !forfeitBy.isEmpty()) {
            boolean iForfeited = ("player" + myPlayerNumber).equals(forfeitBy);
            msg = iForfeited ? "Odustao si. Poraz." : "Protivnik je odustao. Pobjeda!";
        } else if (p1 > p2) {
            msg = "Pobijedio Igrac 1! (" + p1 + ":" + p2 + ")";
        } else if (p2 > p1) {
            msg = "Pobijedio Igrac 2! (" + p1 + ":" + p2 + ")";
        } else {
            msg = "Nerijeseno! (" + p1 + ":" + p2 + ")";
        }

        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        gameFinishHandler.postDelayed(() -> { if (isAdded()) notifyGameFinished(); }, 3000);
    }

    private void notifyGameFinished() {
        if (!isAdded()) return;
        Bundle result = new Bundle();
        result.putString("game", "SKOCKO");
        getParentFragmentManager().setFragmentResult("GAME_FINISHED", result);
    }

    // ─────────────── Grid helpers ───────────────

    private void clearGrid() {
        for (int row = 1; row <= MAX_ROWS; row++) {
            for (int col = 1; col <= MAX_COLS; col++) {
                ImageView cell = getCellView(row, col);
                if (cell != null) clearCell(cell);
            }
        }
        int[] fbIds = { R.id.fb1, R.id.fb2, R.id.fb3, R.id.fb4, R.id.fb5, R.id.fb6 };
        for (int id : fbIds) {
            View fb = rootView.findViewById(id);
            if (fb != null) fb.setVisibility(View.INVISIBLE);
        }
    }

    private void setCell(ImageView cell, int ordinal) {
        if (ordinal >= 0 && ordinal < ORDINAL_TO_BTN.length) {
            ImageButton btn = rootView.findViewById(ORDINAL_TO_BTN[ordinal]);
            if (btn != null) cell.setImageDrawable(btn.getDrawable());
        }
        cell.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
        cell.setTag(ordinal);
    }

    private void clearCell(ImageView cell) {
        cell.setImageDrawable(null);
        cell.setBackgroundTintList(ColorStateList.valueOf(0xFFE3F2FD));
        cell.setTag(null);
    }

    private int[] collectOrdinals(int row) {
        int[] arr = new int[MAX_COLS];
        for (int col = 1; col <= MAX_COLS; col++) {
            ImageView cell = getCellView(row, col);
            if (cell == null) return null;
            Object tag = cell.getTag();
            if (!(tag instanceof Integer)) return null;
            arr[col - 1] = (Integer) tag;
        }
        return arr;
    }

    private ImageView getCellView(int row, int col) {
        int[][] ids = {
                {R.id.r1c1, R.id.r1c2, R.id.r1c3, R.id.r1c4},
                {R.id.r2c1, R.id.r2c2, R.id.r2c3, R.id.r2c4},
                {R.id.r3c1, R.id.r3c2, R.id.r3c3, R.id.r3c4},
                {R.id.r4c1, R.id.r4c2, R.id.r4c3, R.id.r4c4},
                {R.id.r5c1, R.id.r5c2, R.id.r5c3, R.id.r5c4},
                {R.id.r6c1, R.id.r6c2, R.id.r6c3, R.id.r6c4}
        };
        if (row < 1 || row > MAX_ROWS || col < 1 || col > MAX_COLS) return null;
        return rootView.findViewById(ids[row - 1][col - 1]);
    }

    private void renderFeedbackForRow(int row, int reds, int yellows) {
        int[] fbIds = { R.id.fb1, R.id.fb2, R.id.fb3, R.id.fb4, R.id.fb5, R.id.fb6 };
        if (row < 1 || row > MAX_ROWS) return;
        View fbRoot = rootView.findViewById(fbIds[row - 1]);
        if (fbRoot == null) return;
        fbRoot.setVisibility(View.VISIBLE);
        paintFeedbackDots(fbRoot, reds, yellows);
    }

    private void paintFeedbackDots(View fbRoot, int reds, int yellows) {
        if (!(fbRoot instanceof ViewGroup)) return;
        ViewGroup outer = (ViewGroup) fbRoot;
        if (outer.getChildCount() < 2) return;
        ViewGroup top = (ViewGroup) outer.getChildAt(0);
        ViewGroup bot = (ViewGroup) outer.getChildAt(1);
        if (top.getChildCount() < 2 || bot.getChildCount() < 2) return;
        View[] dots = { top.getChildAt(0), top.getChildAt(1),
                bot.getChildAt(0), bot.getChildAt(1) };
        int r = Math.max(0, Math.min(reds, 4));
        int y = Math.max(0, Math.min(yellows, 4 - r));
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == null) continue;
            int res = i < r ? R.drawable.circle_feedback_green
                    : i < r + y ? R.drawable.circle_feedback_yellow
                    : R.drawable.circle_feedback_blue;
            dots[i].setBackgroundResource(res);
        }
    }

    // ─────────────── Misc helpers ───────────────

    private void setSymbolButtonsEnabled(boolean enabled) {
        for (int id : ORDINAL_TO_BTN) {
            View v = rootView.findViewById(id);
            if (v != null) v.setEnabled(enabled);
        }
    }

    private void disableAllInput() {
        setSymbolButtonsEnabled(false);
        if (btnConfirmRow != null) btnConfirmRow.setEnabled(false);
    }

    private int[] parseSecret(String csv) {
        if (csv == null || csv.isEmpty()) return new int[4];
        String[] parts = csv.split(",");
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { arr[i] = Integer.parseInt(parts[i].trim()); }
            catch (NumberFormatException ignored) {}
        }
        return arr;
    }

    private SkockoSymbol[] toSymbolArray(int[] ordinals) {
        SkockoSymbol[] values = SkockoSymbol.values();
        SkockoSymbol[] result = new SkockoSymbol[ordinals.length];
        for (int i = 0; i < ordinals.length; i++) {
            int ord = ordinals[i];
            result[i] = (ord >= 0 && ord < values.length) ? values[ord] : values[0];
        }
        return result;
    }

    private String winner() {
        if (p1Score > p2Score) return "player1";
        if (p2Score > p1Score) return "player2";
        return "draw";
    }

    private String phaseLabel(String phase) {
        switch (phase) {
            case "ROUND_1_PLAYING": return "Runda 1 - Igrac 1 pogada (30s)";
            case "ROUND_1_STEAL":   return "Kradzba - Igrac 2 ima 1 pokusaj (10s)";
            case "ROUND_2_PLAYING": return "Runda 2 - Igrac 2 pogada (30s)";
            case "ROUND_2_STEAL":   return "Kradzba - Igrac 1 ima 1 pokusaj (10s)";
            default:                return "";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelPhaseTimer();
        gameFinishHandler.removeCallbacksAndMessages(null);
        if (skockoManager != null) { skockoManager.stopListening(); skockoManager = null; }
        if (headerController != null) { headerController.release(); headerController = null; }
        rootView = null;
    }
}