package com.example.slagalicaapp.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.SpojniceManager;

import java.util.*;

public class SpojniceMultiplayerFragment extends Fragment implements SpojniceManager.SpojniceListener {

    private static final long TURN_TIME_MS  = 30_000L;
    private static final int  POINTS_PER_PAIR = 2;
    // Number of static children in connections_container (connection_round_title + connection_description)
    private static final int  STATIC_CHILDREN = 2;

    private View root;
    private TextView tvP1Name, tvP1Score, tvP2Name, tvP2Score;
    private TextView tvRoundInfo, tvCurrentPlayer, tvDescription;
    private ProgressBar progressTimer;
    private LinearLayout connectionsContainer;

    private String roomId;
    private int myPlayerNumber;
    private boolean isGameOver = false;

    private SpojniceManager manager;

    // Current turn state
    private List<String> leftItems    = new ArrayList<>();
    private List<String> shuffledRights = new ArrayList<>();
    private List<String> correctRights  = new ArrayList<>();
    private Map<Integer, Integer> resolvedThisRound = new HashMap<>(); // leftIdx → playerNum
    private List<Button> leftButtons  = new ArrayList<>();
    private List<Button> rightButtons = new ArrayList<>();

    private int selectedLeftIdx = -1;
    private final Set<Integer> myCorrectLeftIndices = new HashSet<>();
    private final Set<Integer> myWrongLeftIndices   = new HashSet<>(); // permanently locked after one wrong attempt
    private final Set<Integer> usedRightPositions   = new HashSet<>();

    private int currentRound  = 0;
    private int totalRounds   = 0;
    private int activePlayer  = 1;
    private int p1Score       = 0;
    private int p2Score       = 0;
    private boolean iAmActive = false;
    private boolean turnEnded = false;

    private CountDownTimer countDownTimer;
    private final Handler handler = new Handler();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_spojnice, container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvP1Name          = root.findViewById(R.id.player1_username);
        tvP1Score         = root.findViewById(R.id.player1_score);
        tvP2Name          = root.findViewById(R.id.player2_username);
        tvP2Score         = root.findViewById(R.id.player2_score);
        tvRoundInfo       = root.findViewById(R.id.round_info);
        tvCurrentPlayer   = root.findViewById(R.id.current_player_info);
        tvDescription     = root.findViewById(R.id.connection_description);
        progressTimer     = root.findViewById(R.id.timer_progress);
        connectionsContainer = root.findViewById(R.id.connections_container);

        progressTimer.setMax(300); // 300 × 100ms = 30s
        tvCurrentPlayer.setText("Čekamo protivnika…");

        Bundle args = getArguments();
        if (args != null) {
            roomId       = args.getString("roomId");
            myPlayerNumber = args.getInt("playerNumber", 1);
        }

        manager = new SpojniceManager(roomId, this);
        manager.startListening();
    }

    // ─── SpojniceListener ───────────────────────────────────────────────────

    @Override
    public void onGameReady(String p1Name, String p2Name) {
        requireActivity().runOnUiThread(() -> {
            tvP1Name.setText(p1Name);
            tvP2Name.setText(p2Name);
            tvCurrentPlayer.setText("Igra počinje!");
        });
    }

    @Override
    public void onTurnStarted(int round, int total, int activePl, long turnEndsAt,
                               List<String> lefts, List<String> shuffled, List<String> correct,
                               String description, Map<Integer, Integer> resolved,
                               int p1Sc, int p2Sc) {
        requireActivity().runOnUiThread(() -> {
            currentRound  = round;
            totalRounds   = total;
            activePlayer  = activePl;
            p1Score       = p1Sc;
            p2Score       = p2Sc;
            iAmActive     = (myPlayerNumber == activePl);
            turnEnded     = false;
            selectedLeftIdx = -1;
            myCorrectLeftIndices.clear();
            myWrongLeftIndices.clear();
            usedRightPositions.clear();

            leftItems       = new ArrayList<>(lefts);
            shuffledRights  = new ArrayList<>(shuffled);
            correctRights   = new ArrayList<>(correct);
            resolvedThisRound = new HashMap<>(resolved);

            tvP1Score.setText(String.valueOf(p1Score));
            tvP2Score.setText(String.valueOf(p2Score));
            tvRoundInfo.setText("Runda " + (round + 1) + " / " + total);
            tvDescription.setText(description);

            String p1n = tvP1Name.getText().toString();
            String p2n = tvP2Name.getText().toString();
            String activeName = (activePl == 1) ? p1n : p2n;
            tvCurrentPlayer.setText(iAmActive
                    ? "Tvoj red je! Poveži pojmove."
                    : "Na potezu je: " + activeName);

            buildBoard();

            long remaining = turnEndsAt - System.currentTimeMillis();
            if (remaining < 300)       remaining = 300;
            if (remaining > TURN_TIME_MS) remaining = TURN_TIME_MS;
            startTimer(remaining);
        });
    }

    @Override
    public void onGameFinished(int p1Sc, int p2Sc, String forfeitBy) {
        requireActivity().runOnUiThread(() -> {
            if (isGameOver) return;
            isGameOver = true;
            cancelTimer();
            disableAllButtons();

            String msg;
            if (forfeitBy != null) {
                boolean iForfeited = ("player" + myPlayerNumber).equals(forfeitBy);
                msg = iForfeited ? "Predao si." : "Protivnik je predao!";
            } else if (p1Sc > p2Sc) {
                msg = (myPlayerNumber == 1) ? "Pobedio si! 🎉" : "Izgubio si.";
            } else if (p2Sc > p1Sc) {
                msg = (myPlayerNumber == 2) ? "Pobedio si! 🎉" : "Izgubio si.";
            } else {
                msg = "Nerešeno!";
            }
            tvCurrentPlayer.setText("Kraj igre! " + msg);
            tvP1Score.setText(String.valueOf(p1Sc));
            tvP2Score.setText(String.valueOf(p2Sc));
            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();

            Bundle result = new Bundle();
            result.putBoolean("finished", true);
            getParentFragmentManager().setFragmentResult("GAME_FINISHED", result);
        });
    }

    @Override
    public void onError(String message) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show());
    }

    // ─── Board setup ─────────────────────────────────────────────────────────

    private void buildBoard() {
        // Remove only dynamically added pair views; keep the 2 static TextViews
        while (connectionsContainer.getChildCount() > STATIC_CHILDREN) {
            connectionsContainer.removeViewAt(STATIC_CHILDREN);
        }
        leftButtons.clear();
        rightButtons.clear();

        // Determine which right positions are already consumed by resolved pairs
        for (Integer leftIdx : resolvedThisRound.keySet()) {
            String cr  = correctRights.get(leftIdx);
            int rPos   = shuffledRights.indexOf(cr);
            if (rPos >= 0) usedRightPositions.add(rPos);
        }

        for (int i = 0; i < leftItems.size(); i++) {
            View pairView = getLayoutInflater().inflate(
                    R.layout.item_spojnice_pair, connectionsContainer, false);
            Button leftBtn  = pairView.findViewById(R.id.left_button);
            Button rightBtn = pairView.findViewById(R.id.right_button);

            leftBtn.setText(leftItems.get(i));
            rightBtn.setText(shuffledRights.get(i));

            final int leftIdx  = i;
            final int rightPos = i;
            leftBtn.setOnClickListener(v -> onLeftClicked(leftIdx));
            rightBtn.setOnClickListener(v -> onRightClicked(rightPos));

            boolean leftResolved = resolvedThisRound.containsKey(i);
            boolean rightUsed    = usedRightPositions.contains(i);

            if (leftResolved) {
                applyTint(leftBtn,  "#22C55E");
                leftBtn.setEnabled(false);
            } else {
                leftBtn.setBackgroundTintList(null);
                leftBtn.setEnabled(iAmActive);
            }

            if (rightUsed) {
                applyTint(rightBtn, "#22C55E");
                rightBtn.setEnabled(false);
            } else {
                rightBtn.setBackgroundTintList(null);
                rightBtn.setEnabled(iAmActive);
            }

            leftButtons.add(leftBtn);
            rightButtons.add(rightBtn);
            connectionsContainer.addView(pairView);
        }
    }

    // ─── Button interaction ───────────────────────────────────────────────────

    private void onLeftClicked(int leftIdx) {
        if (!iAmActive || turnEnded) return;
        if (resolvedThisRound.containsKey(leftIdx)
                || myCorrectLeftIndices.contains(leftIdx)
                || myWrongLeftIndices.contains(leftIdx)) return;

        // Deselect previous
        if (selectedLeftIdx >= 0 && selectedLeftIdx < leftButtons.size()) {
            leftButtons.get(selectedLeftIdx).setBackgroundTintList(null);
        }
        selectedLeftIdx = leftIdx;
        applyTint(leftButtons.get(leftIdx), "#3B82F6");
    }

    private void onRightClicked(int rightPos) {
        if (!iAmActive || turnEnded || selectedLeftIdx == -1) return;
        if (usedRightPositions.contains(rightPos)) return;

        int leftIdx = selectedLeftIdx;
        selectedLeftIdx = -1;
        leftButtons.get(leftIdx).setBackgroundTintList(null);

        boolean correct = correctRights.get(leftIdx).equals(shuffledRights.get(rightPos));
        if (correct) {
            applyTint(leftButtons.get(leftIdx),  "#22C55E");
            applyTint(rightButtons.get(rightPos), "#22C55E");
            leftButtons.get(leftIdx).setEnabled(false);
            rightButtons.get(rightPos).setEnabled(false);
            myCorrectLeftIndices.add(leftIdx);
            usedRightPositions.add(rightPos);

            if (allPairsHandled()) {
                handler.postDelayed(this::endTurn, 400);
            }
        } else {
            // Left button permanently locked red; right button freed for other left items
            applyTint(leftButtons.get(leftIdx), "#EF4444");
            leftButtons.get(leftIdx).setEnabled(false);
            rightButtons.get(rightPos).setBackgroundTintList(null);
            myWrongLeftIndices.add(leftIdx);

            if (allPairsHandled()) {
                handler.postDelayed(this::endTurn, 400);
            }
        }
    }

    private boolean allPairsHandled() {
        for (int i = 0; i < leftItems.size(); i++) {
            if (!resolvedThisRound.containsKey(i)
                    && !myCorrectLeftIndices.contains(i)
                    && !myWrongLeftIndices.contains(i)) return false;
        }
        return true;
    }

    // ─── End turn ────────────────────────────────────────────────────────────

    private void endTurn() {
        if (!iAmActive || turnEnded || isGameOver) return;
        turnEnded = true;
        iAmActive = false;
        cancelTimer();
        disableAllButtons();

        int gained   = myCorrectLeftIndices.size() * POINTS_PER_PAIR;
        int newP1Score = (myPlayerNumber == 1) ? p1Score + gained : p1Score;
        int newP2Score = (myPlayerNumber == 2) ? p2Score + gained : p2Score;

        int totalResolved = resolvedThisRound.size() + myCorrectLeftIndices.size();
        boolean roundComplete = totalResolved >= leftItems.size();

        Map<String, Object> updates = new HashMap<>();
        for (int leftIdx : myCorrectLeftIndices) {
            updates.put("resolved/" + currentRound + "/" + leftIdx, myPlayerNumber);
        }
        updates.put("scores/player1", newP1Score);
        updates.put("scores/player2", newP2Score);

        // Round 0 starts with P1; round 1 starts with P2
        int startingPlayerForRound = (currentRound % 2) + 1;
        boolean iAmStartingPlayer  = (myPlayerNumber == startingPlayerForRound);

        if (roundComplete || !iAmStartingPlayer) {
            // All pairs done OR second player just finished → advance round or end game
            int nextRound = currentRound + 1;
            if (nextRound < totalRounds) {
                int nextPlayer = (nextRound % 2) + 1;
                updates.put("currentRound", nextRound);
                updates.put("currentPlayer", nextPlayer);
                updates.put("turnEndsAt", System.currentTimeMillis() + 30_000L);
            } else {
                String winner = newP1Score > newP2Score ? "player1"
                              : newP2Score > newP1Score ? "player2" : "draw";
                updates.put("status", "game_finished");
                updates.put("winner", winner);
                updates.put("finishedAt", System.currentTimeMillis());
            }
        } else {
            // First player's turn ended but pairs remain → give other player a turn
            int nextPlayer = (myPlayerNumber == 1) ? 2 : 1;
            updates.put("currentPlayer", nextPlayer);
            updates.put("turnEndsAt", System.currentTimeMillis() + 30_000L);
        }

        manager.commitTurn(updates);
    }

    // ─── Timer ───────────────────────────────────────────────────────────────

    private void startTimer(long durationMs) {
        cancelTimer();
        long ticks = Math.max(1, durationMs / 100L);
        progressTimer.setMax((int) ticks);
        progressTimer.setProgress((int) ticks);

        countDownTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long remaining) {
                if (!isAdded()) return;
                progressTimer.setProgress((int) (remaining / 100L));
            }
            @Override
            public void onFinish() {
                if (!isAdded() || isGameOver) return;
                progressTimer.setProgress(0);
                if (iAmActive) endTurn();
            }
        }.start();
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void disableAllButtons() {
        for (Button b : leftButtons)  b.setEnabled(false);
        for (Button b : rightButtons) b.setEnabled(false);
    }

    private void applyTint(Button btn, String hex) {
        btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
        handler.removeCallbacksAndMessages(null);
        if (manager != null) manager.stopListening();
    }
}