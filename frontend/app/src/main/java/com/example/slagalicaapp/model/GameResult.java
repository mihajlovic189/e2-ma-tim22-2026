package com.example.slagalicaapp.model;

import java.util.Map;

public class GameResult {
    public String gameType;
    public String player1Uid;
    public String player1Name;
    public int player1Score;
    public String player2Uid;
    public String player2Name;
    public int player2Score;
    public String winnerUid;
    public long timestamp;
    public long durationMs;
    public int questionsCount;

    // Only populated for SLAGALICA_MECH matches: per-sub-game score breakdown,
    // keyed by sub-game type (e.g. "KO_ZNA_ZNA"), each holding "player1"/"player2" deltas.
    public Map<String, Map<String, Long>> subGameScores;

    public GameResult() {
        // empty constructor required for Firebase
    }

    public GameResult(String gameType, String player1Uid, String player1Name, int player1Score,
                      String player2Uid, String player2Name, int player2Score,
                      String winnerUid, long timestamp, long durationMs, int questionsCount) {
        this.gameType = gameType;
        this.player1Uid = player1Uid;
        this.player1Name = player1Name;
        this.player1Score = player1Score;
        this.player2Uid = player2Uid;
        this.player2Name = player2Name;
        this.player2Score = player2Score;
        this.winnerUid = winnerUid;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.questionsCount = questionsCount;
    }

    // getters and setters can be added if needed
}

