package com.example.slagalicaapp.data.models;

import java.util.HashMap;
import java.util.Map;

public class Challenge {
    public static final String STATUS_WAITING = "WAITING_FOR_PLAYERS";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_FINISHED = "FINISHED";

    private String challengeId;
    private String creatorId;
    private String creatorName;
    private String region;
    private int starsWager;
    private int tokensWager;
    private String status;
    private Map<String, ChallengePlayer> joinedPlayers = new HashMap<>();

    public Challenge() {}

    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getStarsWager() { return starsWager; }
    public void setStarsWager(int starsWager) { this.starsWager = starsWager; }
    public int getTokensWager() { return tokensWager; }
    public void setTokensWager(int tokensWager) { this.tokensWager = tokensWager; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, ChallengePlayer> getJoinedPlayers() { return joinedPlayers; }
    public void setJoinedPlayers(Map<String, ChallengePlayer> joinedPlayers) { this.joinedPlayers = joinedPlayers; }

    public static class ChallengePlayer {
        private String name;
        private int score;
        private boolean finished;

        public ChallengePlayer() {}
        public ChallengePlayer(String name) {
            this.name = name;
            this.score = 0;
            this.finished = false;
        }

        public String getName() { return name; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }
    }
}