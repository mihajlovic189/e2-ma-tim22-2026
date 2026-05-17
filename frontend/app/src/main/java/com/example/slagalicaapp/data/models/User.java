package com.example.slagalicaapp.data.models;

public class User {
    private String email;
    private String username;
    private String region;
    private String password;
    private String token;
    private Boolean isGuest;

    public User(){}

    public User(String email, String username, String region, String password, Boolean isGuest) {
        this.email = email;
        this.username = username;
        this.region = region;
        this.password = password;
        this.isGuest = isGuest;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getRegion() {
        return region;
    }

    public String getPassword() {
        return password;
    }

    public Boolean getGuest() { return isGuest; }

    public void setGuest(Boolean guest) { isGuest = guest; }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    public String getToken() { return token; }
}
