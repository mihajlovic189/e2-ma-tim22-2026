package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.model.Question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class KoZnaZnaFragment extends Fragment {

    private static final int QUESTION_TIME = 5000;
    private static final int POINTS_CORRECT = 10;
    private static final int POINTS_INCORRECT = -5;

    private TextView player1Username, player1Score, player2Username, player2Score;
    private TextView questionNumber, timer, questionText;
    private ProgressBar timerProgress;
    private Button btnAnswerA, btnAnswerB, btnAnswerC, btnAnswerD;

    private CountDownTimer questionTimer;

    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private int player1Points = 0;
    private int player2Points = 0;

    private boolean player1Answered = false;
    private boolean player2Answered = false;
    private long player1AnswerTime = -1;
    private long player2AnswerTime = -1;
    private int player1AnswerIndex = -1;
    private int player2AnswerIndex = -1;


    public KoZnaZnaFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        prepareQuestions();
        startGame();
    }

    private void initializeViews(View view) {
        player1Username = view.findViewById(R.id.player1_username);
        player1Score = view.findViewById(R.id.player1_score);
        player2Username = view.findViewById(R.id.player2_username);
        player2Score = view.findViewById(R.id.player2_score);
        questionNumber = view.findViewById(R.id.question_number);
        timer = view.findViewById(R.id.timer);
        questionText = view.findViewById(R.id.question_text);
        timerProgress = view.findViewById(R.id.timer_progress);
        btnAnswerA = view.findViewById(R.id.btnAnswerA);
        btnAnswerB = view.findViewById(R.id.btnAnswerB);
        btnAnswerC = view.findViewById(R.id.btnAnswerC);
        btnAnswerD = view.findViewById(R.id.btnAnswerD);

        btnAnswerA.setOnClickListener(v -> handleAnswer(0));
        btnAnswerB.setOnClickListener(v -> handleAnswer(1));
        btnAnswerC.setOnClickListener(v -> handleAnswer(2));
        btnAnswerD.setOnClickListener(v -> handleAnswer(3));

        timerProgress.setMax(QUESTION_TIME / 1000);
    }

    private void prepareQuestions() {
        questions = new ArrayList<>();
        questions.add(new Question("Koji je glavni grad Srbije?", Arrays.asList("Beograd", "Novi Sad", "Niš", "Kragujevac"), 0));
        questions.add(new Question("Koja je najduža reka na svetu?", Arrays.asList("Nil", "Amazon", "Jangce", "Misisipi"), 1));
        questions.add(new Question("Ko je napisao 'Na Drini ćuprija'?", Arrays.asList("Meša Selimović", "Danilo Kiš", "Ivo Andrić", "Miloš Crnjanski"), 2));
        questions.add(new Question("Koja planeta je najbliža Suncu?", Arrays.asList("Venera", "Mars", "Merkur", "Zemlja"), 2));
        questions.add(new Question("U kom gradu se nalazi Krivi toranj?", Arrays.asList("Rim", "Piza", "Firenca", "Venecija"), 1));
    }

    private void startGame() {
        player1Username.setText("Igrac 1");
        player2Username.setText("Igrac 2");
        showQuestion();
    }

    private void showQuestion() {
        if (currentQuestionIndex < questions.size()) {
            resetQuestionState();
            Question q = questions.get(currentQuestionIndex);
            questionText.setText(q.getQuestionText());
            questionNumber.setText(String.format(Locale.getDefault(), "Pitanje %d/%d", currentQuestionIndex + 1, questions.size()));
            List<String> options = q.getOptions();
            btnAnswerA.setText("A. " + options.get(0));
            btnAnswerB.setText("B. " + options.get(1));
            btnAnswerC.setText("C. " + options.get(2));
            btnAnswerD.setText("D. " + options.get(3));
            startQuestionTimer();
        } else {
            endGame();
        }
    }

    private void startQuestionTimer() {
        questionTimer = new CountDownTimer(QUESTION_TIME, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                timer.setText(String.format(Locale.getDefault(), "00:%02d", seconds));
                timerProgress.setProgress(seconds);
            }

            @Override
            public void onFinish() {
                timer.setText("00:00");
                timerProgress.setProgress(0);
                evaluateAnswers();
            }
        }.start();
    }

    private void handleAnswer(int answerIndex) {
        // This device handles answers for Player 1.
        // In a real multiplayer scenario, you'd have a way to identify the current user.
        if (!player1Answered) {
            player1Answered = true;
            player1AnswerTime = System.currentTimeMillis();
            player1AnswerIndex = answerIndex;
            // Disable buttons after answering to prevent changing the answer
            setAnswerButtonsEnabled(false);
            questionTimer.cancel(); // Stop the timer since answer is submitted
            evaluateAnswers(); // Evaluate immediately
        }
    }

    private void evaluateAnswers() {
        if(questionTimer != null) {
            questionTimer.cancel();
        }

        Question q = questions.get(currentQuestionIndex);
        int correctAnswerIndex = q.getCorrectAnswerIndex();

        boolean p1Correct = player1Answered && player1AnswerIndex == correctAnswerIndex;
        // We are not evaluating player 2 here anymore, as their answers would come from the network.
        // boolean p2Correct = player2Answered && player2AnswerIndex == correctAnswerIndex;

        // The logic for scoring will need to be updated once network logic is in place.
        // For now, we only score player 1.
        if (p1Correct) {
            player1Points += POINTS_CORRECT;
        } else if (player1Answered) { // Incorrect answer
            player1Points += POINTS_INCORRECT;
        }

        // Example of how scoring might work with a second player's data
        /*
        if (p1Correct && p2Correct) {
            if (player1AnswerTime < player2AnswerTime) {
                player1Points += POINTS_CORRECT;
            } else {
                player2Points += POINTS_CORRECT;
            }
        } else if (p1Correct) {
            player1Points += POINTS_CORRECT;
        } else if (p2Correct) {
            player2Points += POINTS_CORRECT;
        }

        if (player1Answered && !p1Correct) {
            player1Points += POINTS_INCORRECT;
        }
        if (player2Answered && !p2Correct) {
            player2Points += POINTS_INCORRECT;
        }
        */

        updateScores();
        currentQuestionIndex++;
        // Delay before showing next question
        new android.os.Handler().postDelayed(this::showQuestion, 2000); // Increased delay to see result
    }

    private void resetQuestionState() {
        player1Answered = false;
        player2Answered = false;
        player1AnswerTime = -1;
        player2AnswerTime = -1;
        player1AnswerIndex = -1;
        player2AnswerIndex = -1;
        // Re-enable buttons for the new question
        setAnswerButtonsEnabled(true);
    }

    private void updateScores() {
        player1Score.setText(String.valueOf(player1Points));
        player2Score.setText(String.valueOf(player2Points));
    }

    private void setAnswerButtonsEnabled(boolean enabled) {
        btnAnswerA.setEnabled(enabled);
        btnAnswerB.setEnabled(enabled);
        btnAnswerC.setEnabled(enabled);
        btnAnswerD.setEnabled(enabled);
    }

    private void endGame() {
        if (questionTimer != null) {
            questionTimer.cancel();
        }
        // Show final scores or navigate away
        setAnswerButtonsEnabled(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (questionTimer != null) {
            questionTimer.cancel();
        }
    }
}
