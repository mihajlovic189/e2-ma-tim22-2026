package com.example.slagalicaapp.data.models;

public class PlayerMapDot {
    private final float mapX;
    private final float mapY;
    private final String region;

    public PlayerMapDot(float mapX, float mapY, String region) {
        this.mapX = mapX;
        this.mapY = mapY;
        this.region = region;
    }

    public float getMapX() { return mapX; }
    public float getMapY() { return mapY; }
    public String getRegion() { return region; }
}
