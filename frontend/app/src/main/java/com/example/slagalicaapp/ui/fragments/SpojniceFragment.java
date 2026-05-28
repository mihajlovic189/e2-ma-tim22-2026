package com.example.slagalicaapp.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.model.SpojniceGame;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceFragment extends Fragment {

    private static final int ROUND_TIME = 30000;
    private static final int POINTS_PER_PAIR = 2;
    private static final int NUMBER_OF_GAMES = 2;

    private TextView roundInfo, player1Username, player1Score, player2Username, player2Score, currentPlayerInfo, connectionDescription;
    private ProgressBar timerProgress;
    private LinearLayout connectionsContainer;

    private CountDownTimer timer;
    private DatabaseReference mDatabase;
    private List<SpojniceGame> games;
    private int currentRound = 0;
    private int player1Points = 0;
    private int player2Points = 0;
    private int currentPlayer = 1; // Player 1 starts the first round

    private Button selectedLeft = null;
    private Button selectedRight = null;
    private Map<Button, Button> connections = new HashMap<>();
    private List<Button> leftButtons = new ArrayList<>();
    private List<Button> rightButtons = new ArrayList<>();

    public SpojniceFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        mDatabase = FirebaseDatabase.getInstance("https://slagalica-mobilna-aplikacija-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
        prepareGames();
    }

    private void initializeViews(View view) {
        roundInfo = view.findViewById(R.id.round_info);
        player1Username = view.findViewById(R.id.player1_username);
        player1Score = view.findViewById(R.id.player1_score);
        player2Username = view.findViewById(R.id.player2_username);
        player2Score = view.findViewById(R.id.player2_score);
        currentPlayerInfo = view.findViewById(R.id.current_player_info);
        timerProgress = view.findViewById(R.id.timer_progress);
        connectionsContainer = view.findViewById(R.id.connections_container);
        connectionDescription = view.findViewById(R.id.connection_description);
    }

    private void prepareGames() {
        games = new ArrayList<>();
        mDatabase.child("Spojnice").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<SpojniceGame> allGames = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    SpojniceGame game = snapshot.getValue(SpojniceGame.class);
                    allGames.add(game);
                }
                Collections.shuffle(allGames);
                games = allGames.subList(0, Math.min(NUMBER_OF_GAMES, allGames.size()));
                startRound();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    private void startRound() {
        if (currentRound < games.size()) {
            roundInfo.setText("Runda " + (currentRound + 1) + " / " + games.size());
            currentPlayer = (currentRound % 2) + 1; // Alternate starting player
            setupBoard();
            startTurn();
        } else {
            endGame();
        }
    }

    private void setupBoard() {
        connectionsContainer.removeAllViews();
        leftButtons.clear();
        rightButtons.clear();
        connections.clear();
        selectedLeft = null;
        selectedRight = null;

        SpojniceGame currentGame = games.get(currentRound);
        connectionDescription.setText(currentGame.getDescription());

        List<String> leftItems = new ArrayList<>(currentGame.getPairs().keySet());
        List<String> rightItems = new ArrayList<>(currentGame.getPairs().values());
        Collections.shuffle(rightItems);

        for (int i = 0; i < leftItems.size(); i++) {
            View pairView = getLayoutInflater().inflate(R.layout.item_spojnice_pair, connectionsContainer, false);
            Button leftButton = pairView.findViewById(R.id.left_button);
            Button rightButton = pairView.findViewById(R.id.right_button);

            leftButton.setText(leftItems.get(i));
            rightButton.setText(rightItems.get(i));

            leftButton.setOnClickListener(v -> selectButton((Button) v, true));
            rightButton.setOnClickListener(v -> selectButton((Button) v, false));

            leftButtons.add(leftButton);
            rightButtons.add(rightButton);
            connectionsContainer.addView(pairView);
        }
    }

    private void selectButton(Button button, boolean isLeft) {
        if (isLeft) {
            if (selectedLeft != null) {
                selectedLeft.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_bg));
            }
            selectedLeft = button;
            selectedLeft.setBackgroundColor(Color.CYAN);
        } else {
            if (selectedRight != null) {
                selectedRight.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_bg));
            }
            selectedRight = button;
            selectedRight.setBackgroundColor(Color.CYAN);
        }

        if (selectedLeft != null && selectedRight != null) {
            connect(selectedLeft, selectedRight);
            selectedLeft.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_bg));
            selectedRight.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_bg));
            selectedLeft = null;
            selectedRight = null;

            // Check if all buttons are disabled (i.e., all pairs have been attempted)
            boolean allAttempted = true;
            for (Button btn : leftButtons) {
                if (btn.isEnabled()) {
                    allAttempted = false;
                    break;
                }
            }
            if (allAttempted) {
                finishTurn();
            }
        }
    }

    private void connect(Button left, Button right) {
        connections.put(left, right);
        left.setEnabled(false);
        right.setEnabled(false);
        left.setBackgroundColor(Color.GREEN);
        right.setBackgroundColor(Color.GREEN);
    }

    private void startTurn() {
        currentPlayerInfo.setText("Na potezu je: Igrač " + currentPlayer);
        setButtonsEnabled(true);

        timer = new CountDownTimer(ROUND_TIME, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerProgress.setProgress((int) (millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                finishTurn();
            }
        }.start();
    }

    private void finishTurn() {
        if (timer != null) {
            timer.cancel();
        }
        evaluatePairs();

        // Check if all pairs on the board are now correctly connected and disabled
        boolean allPairsResolved = true;
        for (Button btn : leftButtons) {
            if (btn.isEnabled()) {
                allPairsResolved = false;
                break;
            }
        }

        // If all pairs are resolved, move to the next round immediately
        if (allPairsResolved) {
            new android.os.Handler().postDelayed(() -> {
                currentRound++;
                startRound();
            }, 1000); // Short delay to show the result
            return;
        }

        int startingPlayer = (currentRound % 2) + 1;

        // If the current player is the one who started the round, switch to the other player
        if (currentPlayer == startingPlayer) {
            currentPlayer = (startingPlayer == 1) ? 2 : 1;
            startTurn();
        } else {
            // If the second player just finished their turn, the round is over
            new android.os.Handler().postDelayed(() -> {
                currentRound++;
                startRound();
            }, 1000); // Short delay
        }
    }

    private void evaluatePairs() {
        SpojniceGame currentGame = games.get(currentRound);
        List<Button> incorrectLeft = new ArrayList<>();
        List<Button> incorrectRight = new ArrayList<>();

        for (Map.Entry<Button, Button> entry : connections.entrySet()) {
            Button left = entry.getKey();
            Button right = entry.getValue();
            String leftText = left.getText().toString();
            String rightText = right.getText().toString();

            if (currentGame.getPairs().get(leftText).equals(rightText)) {
                if (currentPlayer == 1) {
                    player1Points += POINTS_PER_PAIR;
                } else {
                    player2Points += POINTS_PER_PAIR;
                }
                left.setBackgroundColor(Color.GREEN);
                right.setBackgroundColor(Color.GREEN);
                left.setEnabled(false);
                right.setEnabled(false);
            } else {
                left.setBackgroundColor(Color.RED);
                right.setBackgroundColor(Color.RED);
                incorrectLeft.add(left);
                incorrectRight.add(right);
            }
        }
        connections.clear();

        // Reset incorrectly connected pairs for the next player
        for(Button btn : incorrectLeft) {
            btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_bg));
            btn.setEnabled(true);
        }
        for(Button btn : incorrectRight) {
            btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_bg));
            btn.setEnabled(true);
        }

        updateScores();
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button btn : leftButtons) {
            if (!connections.containsKey(btn) && btn.isEnabled()) {
                btn.setEnabled(enabled);
            }
        }
        for (Button btn : rightButtons) {
            boolean isConnected = false;
            for (Button connectedRight : connections.values()) {
                if (connectedRight == btn) {
                    isConnected = true;
                    break;
                }
            }
            if (!isConnected && btn.isEnabled()) {
                btn.setEnabled(enabled);
            }
        }
    }

    private void updateScores() {
        player1Score.setText(String.valueOf(player1Points));
        player2Score.setText(String.valueOf(player2Points));
    }

    private void endGame() {
        if (timer != null) {
            timer.cancel();
        }
        currentPlayerInfo.setText("Kraj igre!");
        setButtonsEnabled(false); // Disable all connection buttons
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) {
            timer.cancel();
        }
    }
}
