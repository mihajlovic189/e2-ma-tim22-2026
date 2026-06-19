package com.example.slagalicaapp.data.models;

public class League {
    public final int index;
    public final String name;
    public final String icon;
    public final int starsRequired;
    public final int dailyBonusTokens;

    public League(int index, String name, String icon, int starsRequired, int dailyBonusTokens) {
        this.index = index;
        this.name = name;
        this.icon = icon;
        this.starsRequired = starsRequired;
        this.dailyBonusTokens = dailyBonusTokens;
    }
}
