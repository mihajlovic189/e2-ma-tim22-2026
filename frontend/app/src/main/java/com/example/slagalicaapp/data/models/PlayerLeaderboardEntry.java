package com.example.slagalicaapp.data.models;

public class PlayerLeaderboardEntry {
    private final String username;
    private final int monthlyStars;
    private final boolean currentUser;
    private int rank;

    public PlayerLeaderboardEntry(String username, int monthlyStars, boolean currentUser) {
        this.username = username;
        this.monthlyStars = monthlyStars;
        this.currentUser = currentUser;
    }

    public String getUsername() { return username; }
    public int getMonthlyStars() { return monthlyStars; }
    public boolean isCurrentUser() { return currentUser; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
}
