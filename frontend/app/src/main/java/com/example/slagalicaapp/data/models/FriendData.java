package com.example.slagalicaapp.data.models;

public class FriendData {
    private String uid;
    private String username;
    private String avatarUri;
    private String leagueName;
    private int totalStars;
    private int monthlyStars;
    private boolean online;
    private boolean inGame;
    private boolean alreadyFriend;

    public FriendData() {}

    public FriendData(String uid, String username, String avatarUri) {
        this.uid = uid;
        this.username = username;
        this.avatarUri = avatarUri;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUri() { return avatarUri; }
    public void setAvatarUri(String avatarUri) { this.avatarUri = avatarUri; }

    public String getLeagueName() { return leagueName; }
    public void setLeagueName(String leagueName) { this.leagueName = leagueName; }

    public int getTotalStars() { return totalStars; }
    public void setTotalStars(int totalStars) { this.totalStars = totalStars; }

    public int getMonthlyStars() { return monthlyStars; }
    public void setMonthlyStars(int monthlyStars) { this.monthlyStars = monthlyStars; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public boolean isInGame() { return inGame; }
    public void setInGame(boolean inGame) { this.inGame = inGame; }

    public boolean isAlreadyFriend() { return alreadyFriend; }
    public void setAlreadyFriend(boolean alreadyFriend) { this.alreadyFriend = alreadyFriend; }

    public boolean canInvite() { return online && !inGame; }
}
