package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.data.models.KorakPoKorakIgra;
import com.example.slagalicaapp.databinding.FragmentKorakPoKorakBinding;

import java.util.ArrayList;
import java.util.List;

public class KorakPoKorakFragment extends Fragment {
    private FragmentKorakPoKorakBinding binding;

    private List<KorakPoKorakIgra> runde = new ArrayList<>();
    private int currentRoundIndex = 0;
    private int currentStepIndex = -1;
    private boolean isPlayer1Turn = true;
    private boolean isOpponentChance = false;

    private int player1TotalPoints = 0;
    private int player2TotalPoints = 0;

    private CountDownTimer mainGameTimer;
    private final long ROUND_DURATION = 70000;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentKorakPoKorakBinding.inflate(inflater, container, false);

        initData();
        setupGameUI();
        startNewRound();

        binding.btnPotvrdi.setOnClickListener(v -> checkAnswer());

        return binding.getRoot();
    }

    private void initData() {
        runde.add(new KorakPoKorakIgra("TELEFON",
                "Izumeo ga Alexander Graham Bell", "Ima brojčanik ili ekran",
                "Služi za komunikaciju", "Može biti mobilni",
                "Halo", "Poziv", "Uređaj za razgovor"));

        runde.add(new KorakPoKorakIgra("SRBIJA",
                "Nalazi se na Balkanu", "Glavni grad je Beograd",
                "Zastava je trobojka", "Nemanjići",
                "Šljiva", "Sarma", "Zemlja u Evropi"));
    }

    private void setupGameUI() {
        binding.gameStatus.tvPlayer1Name.setText("Mika_92");
        binding.gameStatus.tvPlayer2Name.setText("Gost_a1b2");
        updateScores();
    }

    private void startNewRound() {
        isOpponentChance = false;
        currentStepIndex = -1;
        binding.etKrajnjeResenje.setText("");
        binding.etKrajnjeResenje.setEnabled(true);
        binding.btnPotvrdi.setEnabled(true);

        for (int i = 0; i < binding.stepsContainer.getChildCount(); i++) {
            TextView tv = (TextView) binding.stepsContainer.getChildAt(i);
            tv.setText("KORAK " + (i + 1));
            tv.setAlpha(0.5f);
        }

        startGlobalTimer();
    }

    private void startGlobalTimer() {
        if (mainGameTimer != null) mainGameTimer.cancel();

        mainGameTimer = new CountDownTimer(ROUND_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                binding.gameStatus.tvGameTimer.setText(String.valueOf(secondsRemaining));

                int stepToOpen = (69 - secondsRemaining) / 10;

                if (stepToOpen > currentStepIndex && stepToOpen < 7) {
                    currentStepIndex = stepToOpen;
                    revealStep(currentStepIndex);
                }
            }

            @Override
            public void onFinish() {
                binding.gameStatus.tvGameTimer.setText("0");
                handleFailedRound();
            }
        }.start();
    }

    private void revealStep(int index) {
        if (index >= 0 && index < 7) {
            TextView tv = (TextView) binding.stepsContainer.getChildAt(index);
            tv.setText(runde.get(currentRoundIndex).getKoraci()[index]);
            tv.setAlpha(1.0f);
        }
    }

    private void checkAnswer() {
        String answer = binding.etKrajnjeResenje.getText().toString().trim().toUpperCase();
        String correct = runde.get(currentRoundIndex).getKonacnoResenje();

        if (answer.equalsIgnoreCase(correct)) {
            handleRoundEnd(true);
        } else {
            Toast.makeText(getContext(), "Netačno!", Toast.LENGTH_SHORT).show();
            binding.etKrajnjeResenje.setText("");
        }
    }

    private void handleFailedRound() {
        if (!isOpponentChance) {
            isOpponentChance = true;
            Toast.makeText(getContext(), "Protivnik ima 10s!", Toast.LENGTH_LONG).show();

            if (mainGameTimer != null) mainGameTimer.cancel();
            mainGameTimer = new CountDownTimer(10000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    binding.gameStatus.tvGameTimer.setText(String.valueOf(millisUntilFinished / 1000));
                }
                @Override
                public void onFinish() {
                    handleRoundEnd(false);
                }
            }.start();
        } else {
            handleRoundEnd(false);
        }
    }

    private void handleRoundEnd(boolean guessed) {
        if (mainGameTimer != null) mainGameTimer.cancel();

        if (guessed) {
            int points;
            if (!isOpponentChance) {
                points = 20 - (currentStepIndex * 2);
                if (isPlayer1Turn) player1TotalPoints += points;
                else player2TotalPoints += points;
            } else {
                points = 5;
                if (isPlayer1Turn) player2TotalPoints += points;
                else player1TotalPoints += points;
            }
            Toast.makeText(getContext(), "POGODAK! Osvojeno: " + points, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Kraj runde! Rešenje: " + runde.get(currentRoundIndex).getKonacnoResenje(), Toast.LENGTH_LONG).show();
        }

        updateScores();

        new Handler().postDelayed(() -> {
            if (currentRoundIndex == 0) {
                currentRoundIndex = 1;
                isPlayer1Turn = false;
                startNewRound();
            } else {
                Toast.makeText(getContext(), "Kraj igre!", Toast.LENGTH_LONG).show();
                binding.btnPotvrdi.setEnabled(false);
                binding.etKrajnjeResenje.setEnabled(false);
            }
        }, 3000);
    }

    private void updateScores() {
        binding.gameStatus.tvPlayer1Score.setText(String.valueOf(player1TotalPoints));
        binding.gameStatus.tvPlayer2Score.setText(String.valueOf(player2TotalPoints));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainGameTimer != null) mainGameTimer.cancel();
        binding = null;
    }
}