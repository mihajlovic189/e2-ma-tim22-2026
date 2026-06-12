package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.model.Question;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaManager {

    public interface KoZnaZnaListener {
        void onGameReady(List<Question> questions, String p1Name, String p2Name);
        void onQuestionStarted(int index, long startedAt);
        void onAnswerSubmitted(int playerNum, int answerIndex);
        void onQuestionFinished(int index, int p1Answer, int p2Answer,
                                int correctAnswer, int p1Score, int p2Score);
        void onGameFinished(int p1Score, int p2Score, String forfeitBy);
        void onError(String message);
    }

    private final DatabaseReference roomRef;
    private final int myPlayerNumber;
    private final KoZnaZnaListener listener;
    private ValueEventListener roomListener;
    private boolean isGameOver = false;

    private boolean gameReadyFired = false;
    private int lastQuestionIndex = -1;
    private String lastQuestionStatus = "";
    private int lastAnswerBitmask = 0; // bit 0 = P1 answered, bit 1 = P2 answered

    public KoZnaZnaManager(String roomId, int myPlayerNumber, KoZnaZnaListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child("KO_ZNA_ZNA").child(roomId);
        this.myPlayerNumber = myPlayerNumber;
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isGameOver) return;

                String status = snapshot.child("status").getValue(String.class);
                if (status == null) return;

                if (!gameReadyFired && "playing".equals(status)) {
                    gameReadyFired = true;
                    String p1 = snapshot.child("player1").getValue(String.class);
                    String p2 = snapshot.child("player2").getValue(String.class);
                    listener.onGameReady(
                            readQuestions(snapshot),
                            p1 != null ? p1 : "Igrac 1",
                            p2 != null ? p2 : "Igrac 2");
                }

                if ("game_finished".equals(status)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2,
                            snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                Long qIdxLong = snapshot.child("currentQuestionIndex").getValue(Long.class);
                String qStatus = snapshot.child("questionStatus").getValue(String.class);
                if (qIdxLong == null || qStatus == null) return;
                int qIdx = qIdxLong.intValue();

                // New question started
                if (qIdx != lastQuestionIndex && "playing".equals(qStatus)) {
                    lastQuestionIndex = qIdx;
                    lastQuestionStatus = qStatus;
                    lastAnswerBitmask = 0;
                    Long startedAt = snapshot.child("questionStartedAt").getValue(Long.class);
                    listener.onQuestionStarted(qIdx, startedAt != null ? startedAt : System.currentTimeMillis());
                }

                // Check for new answers
                DataSnapshot ansSnap = snapshot.child("answers").child(String.valueOf(qIdx));
                Long p1AnsL = ansSnap.child("player1").getValue(Long.class);
                Long p2AnsL = ansSnap.child("player2").getValue(Long.class);
                if (p1AnsL != null && (lastAnswerBitmask & 1) == 0) {
                    lastAnswerBitmask |= 1;
                    listener.onAnswerSubmitted(1, p1AnsL.intValue());
                }
                if (p2AnsL != null && (lastAnswerBitmask & 2) == 0) {
                    lastAnswerBitmask |= 2;
                    listener.onAnswerSubmitted(2, p2AnsL.intValue());
                }

                // Question result phase
                if ("result".equals(qStatus) && !"result".equals(lastQuestionStatus)) {
                    lastQuestionStatus = qStatus;
                    Long correctL = snapshot.child("questions").child(String.valueOf(qIdx))
                            .child("correctAnswerIndex").getValue(Long.class);
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onQuestionFinished(
                            qIdx,
                            p1AnsL != null ? p1AnsL.intValue() : -1,
                            p2AnsL != null ? p2AnsL.intValue() : -1,
                            correctL != null ? correctL.intValue() : 0,
                            p1Score, p2Score);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        roomRef.addValueEventListener(roomListener);
    }

    public void submitAnswer(int questionIndex, int answerIndex) {
        if (isGameOver) return;
        roomRef.child("answers").child(String.valueOf(questionIndex))
                .child("player" + myPlayerNumber).setValue(answerIndex);
    }

    /** P1 only: write both answers, new scores, set questionStatus = "result". */
    public void publishQuestionResult(int questionIndex, int p1Answer, int p2Answer,
                                       int p1Score, int p2Score) {
        if (isGameOver) return;
        Map<String, Object> upd = new HashMap<>();
        upd.put("answers/" + questionIndex + "/player1", p1Answer);
        upd.put("answers/" + questionIndex + "/player2", p2Answer);
        upd.put("scores/player1", p1Score);
        upd.put("scores/player2", p2Score);
        upd.put("questionStatus", "result");
        roomRef.updateChildren(upd);
    }

    /** P1 only: advance to next question. */
    public void advanceToNextQuestion(int nextIndex, long startedAt) {
        if (isGameOver) return;
        Map<String, Object> upd = new HashMap<>();
        upd.put("currentQuestionIndex", nextIndex);
        upd.put("questionStartedAt", startedAt);
        upd.put("questionStatus", "playing");
        roomRef.updateChildren(upd);
    }

    /** P1 only: mark game as finished. */
    public void finishGame(int p1Score, int p2Score) {
        if (isGameOver) return;
        String winner = p1Score > p2Score ? "player1" : p2Score > p1Score ? "player2" : "draw";
        Map<String, Object> upd = new HashMap<>();
        upd.put("status", "game_finished");
        upd.put("winner", winner);
        upd.put("scores/player1", p1Score);
        upd.put("scores/player2", p2Score);
        upd.put("finishedAt", System.currentTimeMillis());
        roomRef.updateChildren(upd);
    }

    public void stopListening() {
        if (roomListener != null) {
            roomRef.removeEventListener(roomListener);
            roomListener = null;
        }
    }

    private List<Question> readQuestions(DataSnapshot snapshot) {
        List<Question> list = new ArrayList<>();
        for (DataSnapshot qSnap : snapshot.child("questions").getChildren()) {
            String text = qSnap.child("questionText").getValue(String.class);
            Long correctL = qSnap.child("correctAnswerIndex").getValue(Long.class);
            List<String> opts = new ArrayList<>();
            for (DataSnapshot opt : qSnap.child("options").getChildren()) {
                String v = opt.getValue(String.class);
                opts.add(v != null ? v : "");
            }
            if (text != null && correctL != null && opts.size() == 4) {
                list.add(new Question(text, opts, correctL.intValue()));
            }
        }
        return list;
    }

    private int toInt(Long v) { return v != null ? v.intValue() : 0; }
}