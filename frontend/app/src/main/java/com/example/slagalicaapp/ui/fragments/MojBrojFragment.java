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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.databinding.FragmentMojBrojBinding;
import com.example.slagalicaapp.data.firebase.MojBrojManager;
import com.example.slagalicaapp.ui.activities.GameActivity;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    private MojBrojManager firebaseManager;
    private String roomId;
    private int myPlayerNumber;
    private boolean iAmActivePlayer;
    private boolean numbersAlreadyRevealed = false;
    private boolean targetAlreadyRevealed = false;
    private boolean hasSubmitted = false;

    private FragmentMojBrojBinding binding;
    private int targetNumber = 0;
    private List<Integer> availableNumbers = new ArrayList<>();
    private int stopCount = 0;

    private int player1Points = 0;
    private int cumulativePoints = 0;
    private int player2Points = 0;

    private SensorManager sensorManager;
    private float acceleration = 0f;
    private float currentAcceleration = 0f;
    private float lastAcceleration = 0f;
    private CountDownTimer gameTimer;
    private Handler autoStopHandler = new Handler();
    private Runnable autoTargetRunnable;
    private Runnable autoNumbersRunnable;

    private List<View> inputHistory = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMojBrojBinding.inflate(inflater, container, false);

        Bundle args = getArguments();
        if (args != null) {
            roomId           = args.getString("roomId");
            myPlayerNumber   = args.getInt("playerNumber", 1);
            cumulativePoints = args.getInt("cumulativePoints", 0);
        }

        setupShakeSensor();
        setupClickListeners();
        setupFirebase();

        return binding.getRoot();
    }

    private void setupFirebase() {
        if (roomId == null) {
            setupStandaloneGame();
            return;
        }

        if (binding != null && binding.gameStatus.btnForfeit != null) {
            binding.gameStatus.btnForfeit.setOnClickListener(v -> showForfeitDialog());
        }

        firebaseManager = new MojBrojManager(roomId, myPlayerNumber,
                new MojBrojManager.MojBrojListener() {

                    @Override
                    public void onRoundStarted(int activePlayer, int round,
                                               int targetNumber, List<Integer> numbers) {
                        requireActivity().runOnUiThread(() -> {
                            iAmActivePlayer = (activePlayer == myPlayerNumber);
                            numbersAlreadyRevealed = false;
                            targetAlreadyRevealed = false;
                            hasSubmitted = false;
                            stopCount = 0;
                            inputHistory.clear();

                            binding.tvTargetNumber.setText("?");
                            binding.btnStop.setVisibility(View.VISIBLE);
                            binding.btnStop.setEnabled(iAmActivePlayer);
                            binding.btnSubmit.setVisibility(View.GONE);
                            binding.tvExpression.setText("");
                            setInputEnabled(false);

                            for (int i = 0; i < 6; i++) {
                                TextView tv = (TextView) binding.numbersContainer.getChildAt(i);
                                tv.setText("?");
                                tv.setEnabled(false);
                                tv.setAlpha(0.5f);
                            }

                            Toast.makeText(getContext(),
                                    "Runda " + round + " — " +
                                            (iAmActivePlayer ? "Ti biraš!" : "Protivnik bira brojeve"),
                                    Toast.LENGTH_SHORT).show();

                            if (iAmActivePlayer) startAutoTargetTimer();
                        });
                    }

                    @Override
                    public void onTargetRevealed(int targetNumber) {
                        requireActivity().runOnUiThread(() -> {
                            if (targetAlreadyRevealed) return;
                            targetAlreadyRevealed = true;
                            MojBrojFragment.this.targetNumber = targetNumber;
                            binding.tvTargetNumber.setText(String.valueOf(targetNumber));
                            stopCount = 1;
                            if (iAmActivePlayer) startAutoNumbersTimer();
                        });
                    }

                    @Override
                    public void onNumbersRevealed(int target, List<Integer> numbers) {
                        requireActivity().runOnUiThread(() -> {
                            if (numbersAlreadyRevealed) return;
                            numbersAlreadyRevealed = true;
                            targetAlreadyRevealed = true;
                            autoStopHandler.removeCallbacksAndMessages(null);

                            MojBrojFragment.this.targetNumber = target;
                            availableNumbers.clear();
                            availableNumbers.addAll(numbers);

                            binding.tvTargetNumber.setText(String.valueOf(target));
                            for (int i = 0; i < 6; i++) {
                                TextView tv = (TextView) binding.numbersContainer.getChildAt(i);
                                tv.setText(String.valueOf(numbers.get(i)));
                                tv.setEnabled(true);
                                tv.setAlpha(1.0f);
                            }

                            binding.btnStop.setVisibility(View.GONE);
                            binding.btnSubmit.setVisibility(View.VISIBLE);
                            binding.btnSubmit.setEnabled(false);
                            setInputEnabled(true);

                            stopCount = 2;
                            startGameTimer();

                            autoStopHandler.postDelayed(() -> {
                                if (binding != null) binding.btnSubmit.setEnabled(true);
                            }, 5000);
                        });
                    }

                    @Override
                    public void onOpponentSubmitted() {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(),
                                        "Protivnik je predao odgovor!", Toast.LENGTH_SHORT).show()
                        );
                    }

                    @Override
                    public void onRoundResult(int p1Score, int p2Score,
                                              int correctNumber, String message) {
                        requireActivity().runOnUiThread(() -> {
                            if (gameTimer != null) gameTimer.cancel();
                            player1Points = p1Score;
                            player2Points = p2Score;
                            updateScoreUI();
                            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();

                            autoStopHandler.postDelayed(() -> {
                                if (firebaseManager != null) {
                                    firebaseManager.startNextRoundIfReady(2);
                                }
                            }, 5000);
                        });
                    }

                    @Override
                    public void onGameFinished(int p1Score, int p2Score, String forfeitBy) {
                        requireActivity().runOnUiThread(() -> {
                            if (gameTimer != null) gameTimer.cancel();
                            player1Points = p1Score;
                            player2Points = p2Score;
                            updateScoreUI();
                            binding.btnStop.setEnabled(false);
                            binding.btnSubmit.setEnabled(false);
                            setInputEnabled(false);

                            String winner;
                            if (forfeitBy != null && !forfeitBy.isEmpty()) {
                                boolean iForfeited = forfeitBy.equals("player" + myPlayerNumber);
                                winner = iForfeited ? "Odustao si." : "Protivnik je odustao.";
                            } else if (p1Score > p2Score) {
                                winner = "Igrač 1 pobijedio!";
                            } else if (p2Score > p1Score) {
                                winner = "Igrač 2 pobijedio!";
                            } else {
                                winner = "Neriješeno!";
                            }

                            Toast.makeText(getContext(),
                                    "Kraj igre! " + winner + " (" + p1Score + ":" + p2Score + ")",
                                    Toast.LENGTH_LONG).show();
                            autoStopHandler.postDelayed(() -> { if (isAdded()) notifyGameFinished(); }, 5000);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show()
                        );
                    }
                });

        firebaseManager.startListening();
        loadPlayerNamesAndScores();
    }

    private void loadPlayerNamesAndScores() {
        com.google.firebase.database.DatabaseReference roomRef =
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference()
                        .child("rooms").child(GameActivity.GAME_MECH).child(roomId);
        roomRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                if (!isAdded() || !snapshot.exists()) return;
                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);
                Long s1 = snapshot.child("scores").child("player1").getValue(Long.class);
                Long s2 = snapshot.child("scores").child("player2").getValue(Long.class);

                if (p1 != null) binding.gameStatus.tvPlayer1Name.setText(p1);
                if (p2 != null) binding.gameStatus.tvPlayer2Name.setText(p2);
                if (s1 != null) player1Points = s1.intValue();
                if (s2 != null) player2Points = s2.intValue();
                updateScoreUI();
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        });
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
        if (!iAmActivePlayer || numbersAlreadyRevealed) return;

        autoStopHandler.removeCallbacksAndMessages(null);

        if (stopCount == 0) {
            stopCount = 1;
            if (firebaseManager != null && iAmActivePlayer) {
                firebaseManager.revealTargetNumber();
            }

        } else if (stopCount == 1) {
            stopCount = 2;
            if (firebaseManager != null && iAmActivePlayer) {
                firebaseManager.revealNumbers();
            }
        }
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
        if (hasSubmitted) return;

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

        if (firebaseManager != null) {
            firebaseManager.submitResult(expressionStr, currentResult);
            hasSubmitted = true;
            binding.btnSubmit.setEnabled(false);
            setInputEnabled(false);
            Toast.makeText(getContext(),
                    "Predano! Tvoj rezultat: " + currentResult, Toast.LENGTH_SHORT).show();
        } else {
            hasSubmitted = true;
            binding.btnSubmit.setEnabled(false);
            setInputEnabled(false);
            if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }

            int diff = Math.abs(targetNumber - currentResult);
            int score = diff == 0 ? 20 : diff <= 10 ? 10 : diff <= 20 ? 5 : 0;
            player1Points = cumulativePoints + score;
            updateScoreUI();
            Toast.makeText(getContext(), "Rezultat: " + currentResult + " (razlika: " + diff + ")", Toast.LENGTH_SHORT).show();
            autoStopHandler.postDelayed(() -> { if (isAdded()) notifyStandaloneFinished(score); }, 2000);
        }
    }

    private void updateScoreUI() {
        binding.gameStatus.tvPlayer1Score.setText(String.valueOf(player1Points));
        binding.gameStatus.tvPlayer2Score.setText(String.valueOf(player2Points));
    }

    private void setupStandaloneGame() {
        iAmActivePlayer = true;
        hasSubmitted = false;
        stopCount = 2;

        Random rand = new Random();
        targetNumber = 100 + rand.nextInt(900);
        availableNumbers.clear();
        for (int i = 0; i < 4; i++) availableNumbers.add(1 + rand.nextInt(9)); // single-digit
        int[] mediums = {10, 15, 20};
        availableNumbers.add(mediums[rand.nextInt(mediums.length)]);
        int[] larges = {25, 50, 75, 100};
        availableNumbers.add(larges[rand.nextInt(larges.length)]);
        Collections.shuffle(availableNumbers, rand);

        binding.tvTargetNumber.setText(String.valueOf(targetNumber));
        for (int i = 0; i < 6; i++) {
            TextView tv = (TextView) binding.numbersContainer.getChildAt(i);
            tv.setText(String.valueOf(availableNumbers.get(i)));
            tv.setEnabled(true);
            tv.setAlpha(1.0f);
        }
        binding.btnStop.setVisibility(View.GONE);
        binding.btnSubmit.setVisibility(View.VISIBLE);
        binding.btnSubmit.setEnabled(true);
        setInputEnabled(true);
        if (binding.gameStatus.tvPlayer1Name != null) binding.gameStatus.tvPlayer1Name.setText("Ti");
        if (binding.gameStatus.tvPlayer2Name != null) binding.gameStatus.tvPlayer2Name.setText("—");
        player1Points = cumulativePoints;
        updateScoreUI();
        startGameTimer();

        Toast.makeText(getContext(), "Cilj: " + targetNumber + " — imate 60 sekundi!", Toast.LENGTH_LONG).show();
    }

    private void notifyStandaloneFinished(int score) {
        if (!isAdded()) return;
        Bundle result = new Bundle();
        result.putInt("points", score);
        result.putString("game", "MOJ_BROJ");
        getParentFragmentManager().setFragmentResult("GAME_FINISHED", result);
    }

    private void notifyGameFinished() {
        if (!isAdded()) return;
        Bundle result = new Bundle();
        result.putString("game", "MOJ_BROJ");
        getParentFragmentManager().setFragmentResult("GAME_FINISHED", result);
    }

    private void showForfeitDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Odustajanje")
                .setMessage("Ako odustaneš, smatra se da si izgubio. Da li želiš da odustaneš?")
                .setPositiveButton("Odustani", (dialog, which) -> requestForfeit())
                .setNegativeButton("Nastavi", null)
                .show();
    }

    private void requestForfeit() {
        if (getActivity() instanceof GameActivity) {
            ((GameActivity) getActivity()).forfeitMatch();
        }
    }

    private void startAutoTargetTimer() {
        if (autoTargetRunnable != null) autoStopHandler.removeCallbacks(autoTargetRunnable);
        autoTargetRunnable = () -> {
            if (firebaseManager != null) {
                firebaseManager.revealTargetIfNeeded();
            }
        };
        autoStopHandler.postDelayed(autoTargetRunnable, 5000);
    }

    private void startAutoNumbersTimer() {
        if (autoNumbersRunnable != null) autoStopHandler.removeCallbacks(autoNumbersRunnable);
        autoNumbersRunnable = () -> {
            if (firebaseManager != null) {
                firebaseManager.revealNumbersIfNeeded();
            }
        };
        autoStopHandler.postDelayed(autoNumbersRunnable, 5000);
    }

    private void startGameTimer() {
        gameTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.gameStatus.tvGameTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }
            @Override
            public void onFinish() {
                if (!hasSubmitted) {
                    evaluateResult();
                }
                if (firebaseManager != null) {
                    firebaseManager.finalizeOnTimeout();
                }
            }
        }.start();
    }

    private void setInputEnabled(boolean enabled) {
        for (int i = 0; i < binding.operandsGrid.getChildCount(); i++) {
            View v = binding.operandsGrid.getChildAt(i);
            v.setEnabled(enabled);
        }
        for (int i = 0; i < binding.numbersContainer.getChildCount(); i++) {
            View v = binding.numbersContainer.getChildAt(i);
            v.setEnabled(enabled);
            v.setAlpha(enabled ? 1.0f : 0.5f);
        }
        binding.btnDelete.setEnabled(enabled);
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
        if (sensorManager != null) sensorManager.unregisterListener(this);
        autoStopHandler.removeCallbacksAndMessages(null);
        if (firebaseManager != null) firebaseManager.stopListening();
        if (gameTimer != null) gameTimer.cancel();
        binding = null;
    }
}

