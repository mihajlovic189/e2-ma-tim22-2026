package com.example.slagalicaapp.data.models;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class ProfileData {
    private String uid;
    private String username;
    private String email;
    private String region;
    private String avatarUri;
    private String leagueName;
    private String qrPayload;
    private int tokenCount;
    private int totalStars;
    private int totalGamesPlayed;
    private int wins;
    private int losses;
    private int previousCycleRank;
    private Map<String, String> averageScoreRanges = new LinkedHashMap<>();
    private Map<String, String> detailedStats = new LinkedHashMap<>();

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAvatarUri() {
        return avatarUri;
    }

    public void setAvatarUri(String avatarUri) {
        this.avatarUri = avatarUri;
    }

    public String getLeagueName() {
        return leagueName;
    }

    public void setLeagueName(String leagueName) {
        this.leagueName = leagueName;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public int getTotalStars() {
        return totalStars;
    }

    public void setTotalStars(int totalStars) {
        this.totalStars = totalStars;
    }

    public int getTotalGamesPlayed() {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(int totalGamesPlayed) {
        this.totalGamesPlayed = totalGamesPlayed;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getPreviousCycleRank() { return previousCycleRank; }
    public void setPreviousCycleRank(int rank) { this.previousCycleRank = rank; }

    public Map<String, String> getAverageScoreRanges() {
        return averageScoreRanges;
    }

    public void setAverageScoreRanges(Map<String, String> averageScoreRanges) {
        this.averageScoreRanges = averageScoreRanges != null ? new LinkedHashMap<>(averageScoreRanges) : new LinkedHashMap<>();
    }

    public Map<String, String> getDetailedStats() {
        return detailedStats;
    }

    public void setDetailedStats(Map<String, String> detailedStats) {
        this.detailedStats = detailedStats != null ? new LinkedHashMap<>(detailedStats) : new LinkedHashMap<>();
    }
}

