package com.example.slagalicaapp.ui.fragments;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.databinding.FragmentMojBrojBinding;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    private FragmentMojBrojBinding binding;
    private Random random = new Random();

    private int targetNumber = 0;
    private List<Integer> availableNumbers = new ArrayList<>();
    private int stopCount = 0;

    private int player1Points = 0;
    private int player2Points = 0;
    private Integer player1Result = null;
    private int currentRound = 1;
    private boolean isPlayer1Turn = true;

    private SensorManager sensorManager;
    private float acceleration = 0f;
    private float currentAcceleration = 0f;
    private float lastAcceleration = 0f;
    private CountDownTimer gameTimer;
    private Handler autoStopHandler = new Handler();

    private List<View> inputHistory = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMojBrojBinding.inflate(inflater, container, false);

        setupShakeSensor();
        setupClickListeners();
        startAutoStopTimer();

        return binding.getRoot();
    }

    private void setupClickListeners() {
        binding.btnStop.setOnClickListener(v -> handleStopAction());
        binding.btnDelete.setOnClickListener(v -> deleteLastEntry());
        binding.btnSubmit.setOnClickListener(v -> evaluateResult());

        for (int i = 0; i < binding.operandsGrid.getChildCount(); i++) {
            View v = binding.operandsGrid.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                b.setOnClickListener(view -> {
                    binding.tvExpression.append(b.getText().toString());
                    inputHistory.add(b);
                });
            }
        }

        for (int i = 0; i < binding.numbersContainer.getChildCount(); i++) {
            TextView tv = (TextView) binding.numbersContainer.getChildAt(i);
            tv.setOnClickListener(view -> {
                String val = tv.getText().toString();
                if (!val.contains("?")) {
                    binding.tvExpression.append(val);
                    tv.setEnabled(false);
                    tv.setAlpha(0.5f);
                    inputHistory.add(tv);
                }
            });
        }
    }

    private void handleStopAction() {
        autoStopHandler.removeCallbacksAndMessages(null);

        if (stopCount == 0) {
            targetNumber = random.nextInt(999) + 1;
            binding.tvTargetNumber.setText(String.valueOf(targetNumber));
            stopCount = 1;
            startAutoStopTimer();
        }
        else if (stopCount == 1) {
            generateAvailableNumbers();

            for (int i = 0; i < 6; i++) {
                TextView tv = (TextView) binding.numbersContainer.getChildAt(i);
                tv.setText(String.valueOf(availableNumbers.get(i)));
            }

            stopCount = 2;
            binding.btnStop.setVisibility(View.GONE);
            binding.btnSubmit.setVisibility(View.VISIBLE);
            binding.btnSubmit.setEnabled(false);

            startGameTimer();

            new Handler().postDelayed(() -> {
                if (binding != null) binding.btnSubmit.setEnabled(true);
            }, 5000);
        }
    }

    private void generateAvailableNumbers() {
        availableNumbers.clear();
        for (int i = 0; i < 4; i++) availableNumbers.add(random.nextInt(9) + 1);
        int[] med = {10, 15, 20};
        availableNumbers.add(med[random.nextInt(3)]);
        int[] large = {25, 50, 75, 100};
        availableNumbers.add(large[random.nextInt(4)]);
    }

    private void deleteLastEntry() {
        String currentExp = binding.tvExpression.getText().toString();
        if (currentExp.isEmpty() || inputHistory.isEmpty()) return;

        View lastView = inputHistory.remove(inputHistory.size() - 1);

        if (lastView instanceof TextView && !(lastView instanceof Button)) {
            lastView.setEnabled(true);
            lastView.setAlpha(1.0f);
            String val = ((TextView) lastView).getText().toString();
            currentExp = currentExp.substring(0, currentExp.length() - val.length());
        } else {
            currentExp = currentExp.substring(0, currentExp.length() - 1);
        }
        binding.tvExpression.setText(currentExp);
    }

    private void evaluateResult() {
        int currentResult = 0;
        String expressionStr = binding.tvExpression.getText().toString();

        try {
            if (!expressionStr.isEmpty()) {
                Expression e = new ExpressionBuilder(expressionStr).build();
                currentResult = (int) e.evaluate();
            }
        } catch (Exception e) {
            currentResult = 0;
            Toast.makeText(getContext(), "Neispravan izraz!", Toast.LENGTH_SHORT).show();
        }

        if (player1Result == null) {
            player1Result = currentResult;
            Toast.makeText(getContext(), "Igrač 1 rezultat: " + player1Result, Toast.LENGTH_SHORT).show();
            calculateFinalScores(player1Result, 0);
        }
    }

    private void calculateFinalScores(int p1Res, int p2Res) {
        int p1Diff = Math.abs(targetNumber - p1Res);
        int p2Diff = Math.abs(targetNumber - p2Res);

        if (p1Res == targetNumber) {
            player1Points += 10;
        } else if (p2Res == targetNumber) {
            player2Points += 10;
        }
        else if (p1Res != 0 || p2Res != 0) {
            if (p1Res != 0 && (p2Res == 0 || p1Diff < p2Diff)) {
                player1Points += 5;
            } else if (p2Res != 0 && (p1Res == 0 || p2Diff < p1Diff)) {
                player2Points += 5;
            }
            else if (p1Res == p2Res) {
                if (isPlayer1Turn) player1Points += 5; else player2Points += 5;
            }
        }

        updateScoreUI();

    }

    private void updateScoreUI() {
        binding.gameStatus.tvPlayer1Score.setText(String.valueOf(player1Points));
        binding.gameStatus.tvPlayer2Score.setText(String.valueOf(player2Points));
    }

    private void startAutoStopTimer() {
        autoStopHandler.postDelayed(this::handleStopAction, 5000);
    }

    private void startGameTimer() {
        gameTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.gameStatus.tvGameTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }
            @Override
            public void onFinish() {
                evaluateResult();
            }
        }.start();
    }

    private void setupShakeSensor() {
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        acceleration = 10f;
        currentAcceleration = SensorManager.GRAVITY_EARTH;
        lastAcceleration = SensorManager.GRAVITY_EARTH;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        lastAcceleration = currentAcceleration;
        currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z);
        float delta = currentAcceleration - lastAcceleration;
        acceleration = acceleration * 0.9f + delta;

        if (acceleration > 12) {
            if (stopCount < 2) {
                handleStopAction();
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        sensorManager.unregisterListener(this);
        autoStopHandler.removeCallbacksAndMessages(null);
        if (gameTimer != null) gameTimer.cancel();
        binding = null;
    }
}