package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.model.Question;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaManager {

    public interface KoZnaZnaListener {
        void onGameReady(List<Question> questions, String p1Name, String p2Name);
        void onQuestionStarted(int index, long startedAt);
        // PROMENJENO: listener sada prosleđuje i timestamp kada je igrač stvarno kliknuo
        void onAnswerSubmitted(int playerNum, int answerIndex, long clientClickTime);
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
    private int lastAnswerBitmask = 0;

    public KoZnaZnaManager(String roomId, int myPlayerNumber, KoZnaZnaListener listener) {
        this.roomRef = FirebaseDatabase.getInstance().getReference()
                .child("rooms").child(GameActivity.GAME_MECH).child(roomId);
        this.myPlayerNumber = myPlayerNumber;
        this.listener = listener;
    }

    public void startListening() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isGameOver || !snapshot.exists()) return;

                String roomStatus = snapshot.child("status").getValue(String.class);
                if (roomStatus == null) return;

                if (!gameReadyFired && "playing".equals(roomStatus)) {
                    gameReadyFired = true;
                    String p1 = snapshot.child("player1").getValue(String.class);
                    String p2 = snapshot.child("player2").getValue(String.class);

                    List<Question> qs = readQuestions(snapshot);
                    listener.onGameReady(
                            qs,
                            p1 != null ? p1 : "Igrač 1",
                            p2 != null ? p2 : "Igrač 2");

                    if (myPlayerNumber == 1 && !snapshot.child("koZnaZna").hasChild("currentQuestionIndex")) {
                        Map<String, Object> startUpd = new HashMap<>();
                        startUpd.put("koZnaZna/currentQuestionIndex", 0);
                        startUpd.put("koZnaZna/questionStatus", "playing");
                        startUpd.put("koZnaZna/questionStartedAt", System.currentTimeMillis());
                        roomRef.updateChildren(startUpd);
                    }
                }

                if ("forfeit".equals(roomStatus)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, snapshot.child("forfeitBy").getValue(String.class));
                    return;
                }

                DataSnapshot kznSnap = snapshot.child("koZnaZna");
                String kznStatus = kznSnap.child("status").getValue(String.class);
                if ("game_finished".equals(kznStatus)) {
                    isGameOver = true;
                    stopListening();
                    int p1 = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2 = toInt(snapshot.child("scores").child("player2").getValue(Long.class));
                    listener.onGameFinished(p1, p2, null);
                    return;
                }

                Long qIdxLong = kznSnap.child("currentQuestionIndex").getValue(Long.class);
                String qStatus = kznSnap.child("questionStatus").getValue(String.class);
                if (qIdxLong == null || qStatus == null) return;
                int qIdx = qIdxLong.intValue();

                if (qIdx != lastQuestionIndex && "playing".equals(qStatus)) {
                    lastQuestionIndex = qIdx;
                    lastQuestionStatus = qStatus;
                    lastAnswerBitmask = 0;
                    Long startedAt = kznSnap.child("questionStartedAt").getValue(Long.class);
                    listener.onQuestionStarted(qIdx, startedAt != null ? startedAt : System.currentTimeMillis());
                }

                DataSnapshot ansSnap = kznSnap.child("answers").child(String.valueOf(qIdx));
                DataSnapshot p1Snap = ansSnap.child("player1");
                DataSnapshot p2Snap = ansSnap.child("player2");

                if (p1Snap.exists() && (lastAnswerBitmask & 1) == 0) {
                    lastAnswerBitmask |= 1;
                    Long ans = p1Snap.child("index").getValue(Long.class);
                    Long time = p1Snap.child("clickTime").getValue(Long.class);
                    listener.onAnswerSubmitted(1, ans != null ? ans.intValue() : -1, time != null ? time : 0L);
                }
                if (p2Snap.exists() && (lastAnswerBitmask & 2) == 0) {
                    lastAnswerBitmask |= 2;
                    Long ans = p2Snap.child("index").getValue(Long.class);
                    Long time = p2Snap.child("clickTime").getValue(Long.class);
                    listener.onAnswerSubmitted(2, ans != null ? ans.intValue() : -1, time != null ? time : 0L);
                }

                if ("result".equals(qStatus) && !"result".equals(lastQuestionStatus)) {
                    lastQuestionStatus = qStatus;
                    Long correctL = kznSnap.child("questions").child(String.valueOf(qIdx))
                            .child("correctAnswerIndex").getValue(Long.class);
                    int p1Score = toInt(snapshot.child("scores").child("player1").getValue(Long.class));
                    int p2Score = toInt(snapshot.child("scores").child("player2").getValue(Long.class));

                    Long p1AnsVal = p1Snap.child("index").getValue(Long.class);
                    Long p2AnsVal = p2Snap.child("index").getValue(Long.class);

                    listener.onQuestionFinished(
                            qIdx,
                            p1AnsVal != null ? p1AnsVal.intValue() : -1,
                            p2AnsVal != null ? p2AnsVal.intValue() : -1,
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

    public void submitAnswer(int questionIndex, int answerIndex, long clickTime) {
        if (isGameOver) return;
        Map<String, Object> ansMap = new HashMap<>();
        ansMap.put("index", answerIndex);
        ansMap.put("clickTime", clickTime);
        roomRef.child("koZnaZna").child("answers").child(String.valueOf(questionIndex))
                .child("player" + myPlayerNumber).setValue(ansMap);
    }

    public void publishQuestionResult(int questionIndex, int p1Answer, int p2Answer, int p1Score, int p2Score) {
        if (isGameOver) return;
        Map<String, Object> upd = new HashMap<>();
        upd.put("koZnaZna/answers/" + questionIndex + "/player1/index", p1Answer);
        upd.put("koZnaZna/answers/" + questionIndex + "/player2/index", p2Answer);
        upd.put("scores/player1", p1Score);
        upd.put("scores/player2", p2Score);
        upd.put("koZnaZna/questionStatus", "result");
        roomRef.updateChildren(upd);
    }

    public void advanceToNextQuestion(int nextIndex, long startedAt) {
        if (isGameOver) return;
        Map<String, Object> upd = new HashMap<>();
        upd.put("koZnaZna/currentQuestionIndex", nextIndex);
        upd.put("koZnaZna/questionStartedAt", startedAt);
        upd.put("koZnaZna/questionStatus", "playing");
        roomRef.updateChildren(upd);
    }

    public void finishGame(int p1Score, int p2Score) {
        if (isGameOver) return;
        Map<String, Object> upd = new HashMap<>();
        upd.put("koZnaZna/status", "game_finished");
        upd.put("scores/player1", p1Score);
        upd.put("scores/player2", p2Score);
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
        for (DataSnapshot qSnap : snapshot.child("koZnaZna").child("questions").getChildren()) {
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