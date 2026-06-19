package com.example.slagalicaapp.data.models;

public class RegionData {
    private final String name;
    private final String iconEmoji;
    private int monthlyStars;
    private int firstPlaces;
    private int secondPlaces;
    private int thirdPlaces;
    private int activePlayerCount;
    private int totalPlayerCount;
    private int rank;
    private boolean currentPlayerRegion;

    public RegionData(String name, String iconEmoji) {
        this.name = name;
        this.iconEmoji = iconEmoji;
    }

    public String getName() { return name; }
    public String getIconEmoji() { return iconEmoji; }
    public int getMonthlyStars() { return monthlyStars; }
    public void setMonthlyStars(int monthlyStars) { this.monthlyStars = monthlyStars; }
    public int getFirstPlaces() { return firstPlaces; }
    public void setFirstPlaces(int firstPlaces) { this.firstPlaces = firstPlaces; }
    public int getSecondPlaces() { return secondPlaces; }
    public void setSecondPlaces(int secondPlaces) { this.secondPlaces = secondPlaces; }
    public int getThirdPlaces() { return thirdPlaces; }
    public void setThirdPlaces(int thirdPlaces) { this.thirdPlaces = thirdPlaces; }
    public int getActivePlayerCount() { return activePlayerCount; }
    public void setActivePlayerCount(int count) { this.activePlayerCount = count; }
    public int getTotalPlayerCount() { return totalPlayerCount; }
    public void setTotalPlayerCount(int count) { this.totalPlayerCount = count; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public boolean isCurrentPlayerRegion() { return currentPlayerRegion; }
    public void setCurrentPlayerRegion(boolean b) { this.currentPlayerRegion = b; }
}
