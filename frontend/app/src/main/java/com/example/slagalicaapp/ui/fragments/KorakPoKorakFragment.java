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
import androidx.appcompat.app.AlertDialog;

import com.example.slagalicaapp.data.firebase.KorakPoKorakManager;
import com.example.slagalicaapp.databinding.FragmentKorakPoKorakBinding;
import com.example.slagalicaapp.ui.activities.GameActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class KorakPoKorakFragment extends Fragment {
    private KorakPoKorakManager firebaseManager;
    private String roomId;
    private int myPlayerNumber;
    private boolean iAmActivePlayer;
    private FragmentKorakPoKorakBinding binding;

    private List<String> currentSteps = new ArrayList<>();
    private String currentSolution = "";
    private int activePlayerNumber = 1;
    private int currentStepIndex = -1;
    private boolean isOpponentChance = false;

    private int player1TotalPoints = 0;
    private int player2TotalPoints = 0;

    private CountDownTimer mainGameTimer;
    private CountDownTimer opponentTimer;
    private final long ROUND_DURATION = 70000;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentKorakPoKorakBinding.inflate(inflater, container, false);

        Bundle args = getArguments();
        if (args != null) {
            roomId = args.getString("roomId");
            myPlayerNumber = args.getInt("playerNumber", 1);
        }

        setupGameUI();
        setupFirebase();

        binding.btnPotvrdi.setOnClickListener(v -> checkAnswer());

        return binding.getRoot();
    }

    private void setupFirebase() {
        if (roomId == null) return;

        if (binding != null && binding.gameStatus.btnForfeit != null) {
            binding.gameStatus.btnForfeit.setOnClickListener(v -> showForfeitDialog());
        }

        firebaseManager = new KorakPoKorakManager(roomId, myPlayerNumber,
                new KorakPoKorakManager.KorakListener() {

                    @Override
                    public void onRoundStarted(int activePlayer, int round, String solution, List<String> steps) {
                        requireActivity().runOnUiThread(() -> {
                            activePlayerNumber = activePlayer;
                            iAmActivePlayer = (activePlayer == myPlayerNumber);
                            isOpponentChance = false;
                            currentSolution = solution;
                            currentSteps.clear();
                            currentSteps.addAll(steps);
                            startNewRound();

                            if (iAmActivePlayer && currentStepIndex < 0 && firebaseManager != null) {
                                currentStepIndex = 0;
                                firebaseManager.revealNextStep(0);
                                revealStep(0);
                            }

                            binding.etKrajnjeResenje.setEnabled(iAmActivePlayer);
                            binding.btnPotvrdi.setEnabled(iAmActivePlayer);
                        });
                    }

                    @Override
                    public void onStepRevealed(int stepIndex) {
                        requireActivity().runOnUiThread(() -> revealStep(stepIndex));
                    }

                    @Override
                    public void onOpponentAnswering() {
                        requireActivity().runOnUiThread(() -> {
                            isOpponentChance = true;
                            if (mainGameTimer != null) mainGameTimer.cancel();
                            boolean iAmOpponent = !iAmActivePlayer;
                            binding.etKrajnjeResenje.setEnabled(iAmOpponent);
                            binding.btnPotvrdi.setEnabled(iAmOpponent);
                            Toast.makeText(getContext(),
                                    iAmOpponent ? "Tvoja šansa! 10 sekundi!" : "Protivnik pokušava...",
                                    Toast.LENGTH_SHORT).show();
                            startOpponentTimer(iAmOpponent);
                        });
                    }

                    @Override
                    public void onRoundFinished(int p1Score, int p2Score, boolean hasNextRound, boolean solved) {
                        requireActivity().runOnUiThread(() -> {
                            player1TotalPoints = p1Score;
                            player2TotalPoints = p2Score;
                            updateScores();
                            binding.etKrajnjeResenje.setEnabled(false);
                            binding.btnPotvrdi.setEnabled(false);
                            if (!solved && currentSolution != null && !currentSolution.isEmpty()) {
                                Toast.makeText(getContext(), "Pojam je bio: " + currentSolution, Toast.LENGTH_LONG).show();
                            }
                            Toast.makeText(getContext(),
                                    hasNextRound ? "Runda gotova! Spremi se za rundu 2..." : "Kraj igre!",
                                    Toast.LENGTH_LONG).show();
                            if (hasNextRound && firebaseManager != null) {
                                int nextActive = activePlayerNumber == 1 ? 2 : 1;
                                new Handler().postDelayed(
                                        () -> firebaseManager.startNextRoundIfReady(nextActive), 5000);
                            }
                        });
                    }

                    @Override
                    public void onGameFinished(int p1Score, int p2Score, String forfeitBy, boolean solved) {
                        requireActivity().runOnUiThread(() -> {
                            player1TotalPoints = p1Score;
                            player2TotalPoints = p2Score;
                            updateScores();
                            binding.btnPotvrdi.setEnabled(false);
                            binding.etKrajnjeResenje.setEnabled(false);
                            if (!solved && currentSolution != null && !currentSolution.isEmpty()) {
                                Toast.makeText(getContext(), "Pojam je bio: " + currentSolution, Toast.LENGTH_LONG).show();
                            }
                            if (forfeitBy != null && !forfeitBy.isEmpty()) {
                                boolean iForfeited = forfeitBy.equals("player" + myPlayerNumber);
                                Toast.makeText(getContext(),
                                        iForfeited ? "Odustao si. Poraz." : "Protivnik je odustao. Pobjeda!",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "Kraj igre!", Toast.LENGTH_LONG).show();
                            }
                            notifyGameFinished();
                        });
                    }
                });

        firebaseManager.startListening();
        loadPlayerNames();
    }

    private void setupGameUI() {
        updateScores();
    }

    private void startNewRound() {
        isOpponentChance = false;
        currentStepIndex = -1;
        if (opponentTimer != null) opponentTimer.cancel();
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

                int elapsed = (int) ((ROUND_DURATION - millisUntilFinished) / 1000);
                int stepToOpen = elapsed / 10;

                if (stepToOpen > currentStepIndex && stepToOpen < 7) {
                    currentStepIndex = stepToOpen;
                    if (firebaseManager != null) {
                        firebaseManager.advanceStepIfNeeded(stepToOpen);
                    }
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
        if (index >= 0 && index < 7 && index < currentSteps.size()) {
            TextView tv = (TextView) binding.stepsContainer.getChildAt(index);
            tv.setText(currentSteps.get(index));
            tv.setAlpha(1.0f);
        }
    }

    private void checkAnswer() {
        String answer = binding.etKrajnjeResenje.getText().toString().trim().toUpperCase();
        String correct = currentSolution;

        if (firebaseManager != null) {
            firebaseManager.submitAnswer(answer, currentStepIndex, isOpponentChance, correct);
            if (!answer.equalsIgnoreCase(correct)) {
                Toast.makeText(getContext(), "Netačno!", Toast.LENGTH_SHORT).show();
                binding.etKrajnjeResenje.setText("");
            }
        }
    }

    private void handleFailedRound() {
        if (firebaseManager != null) {
            firebaseManager.onTimerExpired(isOpponentChance);
        }
    }

    private void updateScores() {
        binding.gameStatus.tvPlayer1Score.setText(String.valueOf(player1TotalPoints));
        binding.gameStatus.tvPlayer2Score.setText(String.valueOf(player2TotalPoints));
    }

    private void startOpponentTimer(boolean iAmOpponent) {
        if (opponentTimer != null) opponentTimer.cancel();
        opponentTimer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.gameStatus.tvGameTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                if (firebaseManager != null) {
                    firebaseManager.onTimerExpired(true);
                }
            }
        }.start();
    }

    private void loadPlayerNames() {
        DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child("KORAK_PO_KORAK").child(roomId);
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);
                if (p1 != null) binding.gameStatus.tvPlayer1Name.setText(p1);
                if (p2 != null) binding.gameStatus.tvPlayer2Name.setText(p2);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void notifyGameFinished() {
        Bundle result = new Bundle();
        result.putString("game", "KORAK_PO_KORAK");
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (firebaseManager != null) firebaseManager.stopListening();
        if (mainGameTimer != null) mainGameTimer.cancel();
        if (opponentTimer != null) opponentTimer.cancel();
        binding = null;
    }
}