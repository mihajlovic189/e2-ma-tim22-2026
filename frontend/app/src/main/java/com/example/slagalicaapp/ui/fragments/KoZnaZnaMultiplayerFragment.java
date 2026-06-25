package com.example.slagalicaapp.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.KoZnaZnaManager;
import com.example.slagalicaapp.model.Question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class KoZnaZnaMultiplayerFragment extends Fragment implements KoZnaZnaManager.KoZnaZnaListener {

    private static final long QUESTION_TIME_MS = 5_000L;
    private static final int  POINTS_CORRECT   = 10;
    private static final int  POINTS_INCORRECT  = -5;

    private View root;
    private TextView tvP1Name, tvP1Score, tvP2Name, tvP2Score;
    private TextView tvQuestionNumber, tvTimer, tvQuestionText;
    private ProgressBar progressTimer;
    private Button[] answerButtons; // A=0, B=1, C=2, D=3

    private String roomId;
    private int myPlayerNumber;
    private boolean isCoordinator; // true for player 1
    private boolean isGameOver = false;

    private KoZnaZnaManager manager;
    private List<Question> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int p1Score = 0;
    private int p2Score = 0;

    // Per-question state (reset on each new question)
    private int  myLocalAnswer   = -1; // what this device's player clicked
    private int  p1TrackedAnswer = -1;
    private int  p2TrackedAnswer = -1;
    private boolean p1Answered = false;
    private boolean p2Answered = false;
    private boolean evaluated  = false;
    private long p1AnswerTime = 0; // ms epoch; set on click for local player, on arrival for remote
    private long p2AnswerTime = 0;

    private CountDownTimer questionTimer;
    private final Handler handler = new Handler();
    private boolean isStandaloneMode = false;
    private int standaloneScore = 0;
    private int cumulativePoints = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvP1Name        = root.findViewById(R.id.player1_username);
        tvP1Score       = root.findViewById(R.id.player1_score);
        tvP2Name        = root.findViewById(R.id.player2_username);
        tvP2Score       = root.findViewById(R.id.player2_score);
        tvQuestionNumber = root.findViewById(R.id.question_number);
        tvTimer         = root.findViewById(R.id.timer);
        tvQuestionText  = root.findViewById(R.id.question_text);
        progressTimer   = root.findViewById(R.id.timer_progress);

        answerButtons = new Button[] {
            root.findViewById(R.id.btnAnswerA),
            root.findViewById(R.id.btnAnswerB),
            root.findViewById(R.id.btnAnswerC),
            root.findViewById(R.id.btnAnswerD)
        };

        progressTimer.setMax(50); // 50 ticks for smooth animation (100ms each)
        setAnswersEnabled(false);

        Bundle args = getArguments();
        if (args != null) {
            roomId         = args.getString("roomId");
            myPlayerNumber = args.getInt("playerNumber", 1);
            cumulativePoints = args.getInt("cumulativePoints", 0);
        }
        isCoordinator = (myPlayerNumber == 1);

        for (int i = 0; i < answerButtons.length; i++) {
            final int answerIndex = i;
            answerButtons[i].setOnClickListener(v -> onAnswerClicked(answerIndex));
        }

        if (roomId == null) {
            isStandaloneMode = true;
            isCoordinator = true;
            setupStandaloneGame();
        } else {
            manager = new KoZnaZnaManager(roomId, myPlayerNumber, this);
            manager.startListening();
            tvQuestionText.setText("Čekamo igrača 2…");
        }
    }

    // ─── KoZnaZnaListener ───────────────────────────────────────────────────

    @Override
    public void onGameReady(List<Question> qs, String p1Name, String p2Name) {
        requireActivity().runOnUiThread(() -> {
            questions = qs;
            tvP1Name.setText(p1Name);
            tvP2Name.setText(p2Name);
            tvP1Score.setText("0");
            tvP2Score.setText("0");
            tvQuestionText.setText("Igra počinje!");
        });
    }

    @Override
    public void onQuestionStarted(int index, long startedAt) {
        requireActivity().runOnUiThread(() -> {
            currentQuestionIndex = index;
            p1TrackedAnswer = -1;
            p2TrackedAnswer = -1;
            p1Answered = false;
            p2Answered = false;
            evaluated  = false;
            myLocalAnswer = -1;
            p1AnswerTime = 0;
            p2AnswerTime = 0;

            resetButtonColors();
            setAnswersEnabled(true);

            Question q = questions.get(index);
            tvQuestionNumber.setText(String.format(Locale.getDefault(), "Pitanje %d/%d", index + 1, questions.size()));
            tvQuestionText.setText(q.getQuestionText());

            List<String> opts = q.getOptions();
            String[] labels = {"A", "B", "C", "D"};
            for (int i = 0; i < answerButtons.length && i < opts.size(); i++) {
                answerButtons[i].setText(labels[i] + ". " + opts.get(i));
            }

            long remaining = startedAt + QUESTION_TIME_MS - System.currentTimeMillis();
            if (remaining > QUESTION_TIME_MS) remaining = QUESTION_TIME_MS;
            if (remaining < 300) remaining = 300;
            startQuestionTimer(remaining);
        });
    }

    @Override
    public void onAnswerSubmitted(int playerNum, int answerIndex, long clientClickTime) {
        requireActivity().runOnUiThread(() -> {
            if (playerNum == 1) {
                p1TrackedAnswer = answerIndex;
                p1Answered = true;
                p1AnswerTime = clientClickTime;
            } else {
                p2TrackedAnswer = answerIndex;
                p2Answered = true;
                p2AnswerTime = clientClickTime;
            }

            if (isCoordinator && p1Answered && p2Answered) {
                evaluateAndPublish();
            }
        });
    }

    @Override
    public void onQuestionFinished(int index, int p1Answer, int p2Answer,
                                   int correctAnswer, int p1Sc, int p2Sc) {
        requireActivity().runOnUiThread(() -> {
            cancelTimer();
            setAnswersEnabled(false);

            p1Score = p1Sc;
            p2Score = p2Sc;
            tvP1Score.setText(String.valueOf(p1Score));
            tvP2Score.setText(String.valueOf(p2Score));

            highlightAnswers(p1Answer, p2Answer, correctAnswer);

            // Show which answer is correct
            List<String> opts = questions.get(index).getOptions();
            String correctText = (correctAnswer >= 0 && correctAnswer < opts.size())
                    ? opts.get(correctAnswer) : "?";
            tvQuestionNumber.setText("Tačan: " + correctText);

            if (isCoordinator) {
                handler.postDelayed(() -> {
                    if (isGameOver || !isAdded()) return;
                    int nextIndex = index + 1;
                    if (nextIndex < questions.size()) {
                        manager.advanceToNextQuestion(nextIndex, System.currentTimeMillis());
                    } else {
                        manager.finishGame(p1Score, p2Score);
                    }
                }, 2000);
            }
        });
    }

    @Override
    public void onGameFinished(int p1Sc, int p2Sc, String forfeitBy) {
        requireActivity().runOnUiThread(() -> {
            if (isGameOver) return;
            isGameOver = true;
            cancelTimer();
            setAnswersEnabled(false);

            String msg;
            if (forfeitBy != null) {
                boolean iWasForfeit = ("player" + myPlayerNumber).equals(forfeitBy);
                msg = iWasForfeit ? "Predao si meč." : "Protivnik je predao!";
            } else if (p1Sc > p2Sc) {
                msg = (myPlayerNumber == 1) ? "Pobedio si! 🎉" : "Izgubio si.";
            } else if (p2Sc > p1Sc) {
                msg = (myPlayerNumber == 2) ? "Pobedio si! 🎉" : "Izgubio si.";
            } else {
                msg = "Nerešeno!";
            }

            tvQuestionText.setText(msg);
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

    // ─── Local answer click ──────────────────────────────────────────────────

    private void onAnswerClicked(int answerIndex) {
        if (evaluated || isGameOver) return;
        setAnswersEnabled(false);
        myLocalAnswer = answerIndex;
        answerButtons[answerIndex].setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#3B82F6")));

        if (isStandaloneMode) {
            standaloneEvaluate(answerIndex);
        } else {
            long kliknutoU = System.currentTimeMillis();
            if (myPlayerNumber == 1) {
                p1AnswerTime = kliknutoU;
                p1TrackedAnswer = answerIndex;
                p1Answered = true;
            } else {
                p2AnswerTime = kliknutoU;
                p2TrackedAnswer = answerIndex;
                p2Answered = true;
            }
            manager.submitAnswer(currentQuestionIndex, answerIndex, kliknutoU);
            if (isCoordinator && p1Answered && p2Answered) {
                evaluateAndPublish();
            }
        }
    }

    // ─── Coordinator: evaluate ───────────────────────────────────────────────

    private void evaluateAndPublish() {
        if (!isCoordinator || evaluated) return;
        evaluated = true;
        cancelTimer();

        Question q = questions.get(currentQuestionIndex);
        int correct = q.getCorrectAnswerIndex();

        int newP1Score = p1Score;
        int newP2Score = p2Score;

        boolean p1Correct = p1Answered && (p1TrackedAnswer == correct);
        boolean p2Correct = p2Answered && (p2TrackedAnswer == correct);

        if (p1Correct && p2Correct) {
            // Savršeno pošteno poređenje mrežnih timestamp-ova
            boolean p1First = (p1AnswerTime <= p2AnswerTime);
            if (p1First) newP1Score += POINTS_CORRECT;
            else         newP2Score += POINTS_CORRECT;
        } else if (p1Correct) {
            newP1Score += POINTS_CORRECT;
        } else if (p2Correct) {
            newP2Score += POINTS_CORRECT;
        }

        if (p1Answered && !p1Correct) newP1Score += POINTS_INCORRECT;
        if (p2Answered && !p2Correct) newP2Score += POINTS_INCORRECT;

        manager.publishQuestionResult(currentQuestionIndex,
                p1Answered ? p1TrackedAnswer : -1,
                p2Answered ? p2TrackedAnswer : -1,
                newP1Score, newP2Score);
    }

    // ─── Timer ───────────────────────────────────────────────────────────────

    private void startQuestionTimer(long durationMs) {
        cancelTimer();
        long ticks = Math.max(1, durationMs / 100L);
        progressTimer.setMax((int) ticks);
        progressTimer.setProgress((int) ticks);

        questionTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long remaining) {
                if (!isAdded()) return;
                int secs = (int) Math.ceil(remaining / 1000.0);
                tvTimer.setText(String.format(Locale.getDefault(), "00:%02d", secs));
                progressTimer.setProgress((int) (remaining / 100L));
            }

            @Override
            public void onFinish() {
                if (!isAdded() || isGameOver) return;
                tvTimer.setText("00:00");
                progressTimer.setProgress(0);
                setAnswersEnabled(false);
                if (isStandaloneMode) standaloneEvaluate(-1);
                else if (isCoordinator) evaluateAndPublish();
            }
        }.start();
    }

    private void cancelTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────

    private void setAnswersEnabled(boolean enabled) {
        for (Button btn : answerButtons) btn.setEnabled(enabled);
    }

    private void resetButtonColors() {
        for (Button btn : answerButtons) btn.setBackgroundTintList(null);
    }

    private void highlightAnswers(int p1Answer, int p2Answer, int correctAnswer) {
        // Correct answer = green
        if (correctAnswer >= 0 && correctAnswer < answerButtons.length) {
            answerButtons[correctAnswer].setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#22C55E")));
        }
        // My wrong answer = red
        int myAnswer = (myPlayerNumber == 1) ? p1Answer : p2Answer;
        if (myAnswer != -1 && myAnswer != correctAnswer && myAnswer < answerButtons.length) {
            answerButtons[myAnswer].setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#EF4444")));
        }
        // Opponent's wrong answer = orange
        int oppAnswer = (myPlayerNumber == 1) ? p2Answer : p1Answer;
        if (oppAnswer != -1 && oppAnswer != correctAnswer
                && oppAnswer != myAnswer && oppAnswer < answerButtons.length) {
            answerButtons[oppAnswer].setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#F97316")));
        }
    }

    private void setupStandaloneGame() {
        questions = Arrays.asList(
            new Question("Koji je glavni grad Srbije?", Arrays.asList("Niš", "Beograd", "Novi Sad", "Kragujevac"), 1),
            new Question("Koliko dana ima u jednoj nedelji?", Arrays.asList("5", "6", "7", "8"), 2),
            new Question("Ko je napisao ep 'Gorski vijenac'?", Arrays.asList("Vuk Stefanović", "Petar P. Njegoš", "Jovan Zmaj", "Ivo Andrić"), 1),
            new Question("Koja planeta je najbliža Suncu?", Arrays.asList("Venera", "Mars", "Zemlja", "Merkur"), 3),
            new Question("Koliko sekundi ima jedan minut?", Arrays.asList("100", "50", "60", "30"), 2)
        );
        tvP1Name.setText("Ti");
        tvP2Name.setText("—");
        tvP1Score.setText(String.valueOf(cumulativePoints));
        tvP2Score.setText("—");
        standaloneAdvanceToQuestion(0);
    }

    private void standaloneAdvanceToQuestion(int index) {
        if (!isAdded()) return;
        if (index >= questions.size()) {
            standaloneFinishGame();
            return;
        }
        currentQuestionIndex = index;
        evaluated = false;
        myLocalAnswer = -1;
        resetButtonColors();
        setAnswersEnabled(true);

        Question q = questions.get(index);
        tvQuestionNumber.setText(String.format(Locale.getDefault(), "Pitanje %d/%d", index + 1, questions.size()));
        tvQuestionText.setText(q.getQuestionText());
        List<String> opts = q.getOptions();
        String[] labels = {"A", "B", "C", "D"};
        for (int i = 0; i < answerButtons.length && i < opts.size(); i++) {
            answerButtons[i].setText(labels[i] + ". " + opts.get(i));
        }
        startQuestionTimer(QUESTION_TIME_MS);
    }

    private void standaloneEvaluate(int chosenIndex) {
        if (evaluated) return;
        evaluated = true;
        cancelTimer();
        setAnswersEnabled(false);

        Question q = questions.get(currentQuestionIndex);
        int correct = q.getCorrectAnswerIndex();
        if (chosenIndex == correct) standaloneScore += POINTS_CORRECT;

        tvP1Score.setText(String.valueOf(cumulativePoints + standaloneScore));
        highlightAnswers(chosenIndex, -1, correct);
        List<String> opts = q.getOptions();
        tvQuestionNumber.setText("Tačan: " + (correct >= 0 && correct < opts.size() ? opts.get(correct) : "?"));

        handler.postDelayed(() -> {
            if (!isAdded()) return;
            standaloneAdvanceToQuestion(currentQuestionIndex + 1);
        }, 2000);
    }

    private void standaloneFinishGame() {
        isGameOver = true;
        setAnswersEnabled(false);
        tvQuestionText.setText("Igra završena! Ukupno: " + standaloneScore + " poena");
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