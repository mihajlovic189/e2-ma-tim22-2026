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

    private List<String> leftItems    = new ArrayList<>();
    private List<String> shuffledRights = new ArrayList<>();
    private List<String> correctRights  = new ArrayList<>();
    private Map<Integer, Integer> resolvedThisRound = new HashMap<>();

    private List<Button> leftButtons  = new ArrayList<>();
    private List<Button> rightButtons = new ArrayList<>();

    private int selectedLeftIdx = -1;
    private final Set<Integer> usedRightPositions = new HashSet<>();

    private int currentRound  = 0;
    private int totalRounds   = 0;
    private int activePlayer  = 1;
    private int p1Score       = 0;
    private int p2Score       = 0;
    private boolean iAmActive = false;
    private Set<Integer> failedByP1 = new HashSet<>();
    private Set<Integer> lostByP2   = new HashSet<>();

    private CountDownTimer countDownTimer;
    private final Handler handler = new Handler();
    private boolean isStandaloneMode = false;
    private int standaloneScore = 0;
    private int standaloneRoundIndex = 0;
    private int cumulativePoints = 0;
    private final Set<Integer> failedLeftIndices = new HashSet<>();

    private static final String[][] STANDALONE_LEFTS = {
        {"Beograd", "Pariz", "Rim", "Atina"},
        {"Fudbal", "Košarka", "Tenis", "Plivanje"}
    };
    private static final String[][] STANDALONE_RIGHTS = {
        {"Srbija", "Francuska", "Italija", "Grčka"},
        {"Gol i lopta", "Koš i lopta", "Reketi", "Bazen"}
    };
    private static final String[] STANDALONE_DESC = {
        "Povežite gradove sa državama",
        "Povežite sport sa pojmom"
    };

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

        progressTimer.setMax(300);
        tvCurrentPlayer.setText("Čekamo protivnika…");

        Bundle args = getArguments();
        if (args != null) {
            roomId         = args.getString("roomId");
            myPlayerNumber = args.getInt("playerNumber", 1);
            cumulativePoints = args.getInt("cumulativePoints", 0);
        }

        if (roomId == null) {
            isStandaloneMode = true;
            iAmActive = true;
            setupStandaloneRound(0);
        } else {
            manager = new SpojniceManager(roomId, myPlayerNumber, this);
            manager.startListening();
        }
    }

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
                              Set<Integer> failedP1, Set<Integer> lostP2,
                              int p1Sc, int p2Sc) {
        requireActivity().runOnUiThread(() -> {
            if (isGameOver) return;

            currentRound  = round;
            totalRounds   = total;
            activePlayer  = activePl;
            p1Score       = p1Sc;
            p2Score       = p2Sc;
            iAmActive     = (myPlayerNumber == activePl);
            selectedLeftIdx = -1;
            usedRightPositions.clear();

            leftItems       = new ArrayList<>(lefts);
            shuffledRights  = new ArrayList<>(shuffled);
            correctRights   = new ArrayList<>(correct);
            resolvedThisRound = new HashMap<>(resolved);
            failedByP1      = new HashSet<>(failedP1);
            lostByP2        = new HashSet<>(lostP2);

            tvP1Score.setText(String.valueOf(p1Score));
            tvP2Score.setText(String.valueOf(p2Score));
            tvRoundInfo.setText("Runda " + (round + 1) + " / " + total);
            tvDescription.setText(description);

            String p1n = tvP1Name.getText().toString();
            String p2n = tvP2Name.getText().toString();
            String activeName = (activePl == 1) ? p1n : p2n;
            tvCurrentPlayer.setText(iAmActive ? "Tvoj red je! Poveži pojmove." : "Na potezu je: " + activeName);

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
        requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show());
    }

    private void buildBoard() {
        while (connectionsContainer.getChildCount() > STATIC_CHILDREN) {
            connectionsContainer.removeViewAt(STATIC_CHILDREN);
        }
        leftButtons.clear();
        rightButtons.clear();

        for (Integer leftIdx : resolvedThisRound.keySet()) {
            String cr  = correctRights.get(leftIdx);
            int rPos   = shuffledRights.indexOf(cr);
            if (rPos >= 0) usedRightPositions.add(rPos);
        }

        for (int i = 0; i < leftItems.size(); i++) {
            View pairView = getLayoutInflater().inflate(R.layout.item_spojnice_pair, connectionsContainer, false);
            Button leftBtn  = pairView.findViewById(R.id.left_button);
            Button rightBtn = pairView.findViewById(R.id.right_button);

            leftBtn.setText(leftItems.get(i));
            rightBtn.setText(shuffledRights.get(i));

            final int leftIdx  = i;
            final int rightPos = i;
            leftBtn.setOnClickListener(v -> onLeftClicked(leftIdx));
            rightBtn.setOnClickListener(v -> onRightClicked(rightPos));

            boolean leftResolved = resolvedThisRound.containsKey(i);
            boolean leftFailed   = failedByP1.contains(i);
            boolean leftLost     = lostByP2.contains(i);
            boolean rightUsed    = usedRightPositions.contains(i);

            if (leftResolved || leftLost) {
                // Zelena = tačno spojeno | Crvena = oba igrača pogrešila
                applyTint(leftBtn, leftResolved ? "#22C55E" : "#EF4444");
                leftBtn.setEnabled(false);
            } else if (leftFailed) {
                // Narandžasta: igrač1 pogrešio — igrač2 može da pokuša, igrač1 ne može ponovo
                applyTint(leftBtn, "#F97316");
                leftBtn.setEnabled(iAmActive);
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

    private void onLeftClicked(int leftIdx) {
        if (!iAmActive || isGameOver) return;
        if (resolvedThisRound.containsKey(leftIdx)) return;

        if (selectedLeftIdx >= 0 && selectedLeftIdx < leftButtons.size()) {
            leftButtons.get(selectedLeftIdx).setBackgroundTintList(null);
        }
        selectedLeftIdx = leftIdx;
        applyTint(leftButtons.get(leftIdx), "#3B82F6"); // Plava boja za selekciju
    }

    private void onRightClicked(int rightPos) {
        if (!iAmActive || isGameOver || selectedLeftIdx == -1) return;
        if (usedRightPositions.contains(rightPos)) return;

        int leftIdx = selectedLeftIdx;
        selectedLeftIdx = -1;
        leftButtons.get(leftIdx).setBackgroundTintList(null);

        boolean isCorrect = correctRights.get(leftIdx).equals(shuffledRights.get(rightPos));

        if (isStandaloneMode) {
            if (isCorrect) {
                resolvedThisRound.put(leftIdx, rightPos);
                usedRightPositions.add(rightPos);
                standaloneScore += 10;
                applyTint(leftButtons.get(leftIdx), "#22C55E");
                applyTint(rightButtons.get(rightPos), "#22C55E");
                leftButtons.get(leftIdx).setEnabled(false);
                rightButtons.get(rightPos).setEnabled(false);
            } else {
                // Permanently red — this left item is lost
                failedLeftIndices.add(leftIdx);
                applyTint(leftButtons.get(leftIdx), "#EF4444");
                leftButtons.get(leftIdx).setEnabled(false);
            }
            tvP1Score.setText(String.valueOf(cumulativePoints + standaloneScore));
            int done = resolvedThisRound.size() + failedLeftIndices.size();
            if (done == leftItems.size()) {
                cancelTimer();
                handler.postDelayed(this::standaloneAdvanceRound, 1000);
            }
        } else {
            disableAllButtons();
            manager.submitMatchAtomic(myPlayerNumber, currentRound, leftIdx, isCorrect, leftItems.size());
        }
    }

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
                if (isStandaloneMode) {
                    standaloneAdvanceRound();
                } else if (iAmActive) {
                    disableAllButtons();
                    if (manager != null) {
                        manager.handleTimeoutLocal(currentRound, myPlayerNumber, leftItems.size());
                    }
                }
            }
        }.start();
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void disableAllButtons() {
        for (Button b : leftButtons)  b.setEnabled(false);
        for (Button b : rightButtons) b.setEnabled(false);
    }

    private void applyTint(Button btn, String hex) {
        btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
    }

    private void setupStandaloneRound(int roundIdx) {
        standaloneRoundIndex = roundIdx;
        leftItems = new ArrayList<>(Arrays.asList(STANDALONE_LEFTS[roundIdx]));
        correctRights = new ArrayList<>(Arrays.asList(STANDALONE_RIGHTS[roundIdx]));
        shuffledRights = new ArrayList<>(correctRights);
        Collections.shuffle(shuffledRights);
        resolvedThisRound = new HashMap<>();
        usedRightPositions.clear();
        failedLeftIndices.clear();
        selectedLeftIdx = -1;
        iAmActive = true;

        tvRoundInfo.setText("Runda " + (roundIdx + 1) + " / " + STANDALONE_LEFTS.length);
        tvDescription.setText(STANDALONE_DESC[roundIdx]);
        tvCurrentPlayer.setText("Tvoj red je! Poveži pojmove.");
        tvP1Score.setText(String.valueOf(cumulativePoints + standaloneScore));

        buildBoard();
        startTimer(TURN_TIME_MS);
    }

    private void standaloneAdvanceRound() {
        if (!isAdded() || isGameOver) return;
        int next = standaloneRoundIndex + 1;
        if (next >= STANDALONE_LEFTS.length) {
            standaloneFinishGame();
        } else {
            setupStandaloneRound(next);
        }
    }

    private void standaloneFinishGame() {
        isGameOver = true;
        cancelTimer();
        disableAllButtons();
        tvCurrentPlayer.setText("Igra završena! Ukupno: " + standaloneScore + " poena");
        Bundle result = new Bundle();
        result.putInt("points", standaloneScore);
        getParentFragmentManager().setFragmentResult("GAME_FINISHED", result);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
        handler.removeCallbacksAndMessages(null);
        if (manager != null) manager.stopListening();
    }
}