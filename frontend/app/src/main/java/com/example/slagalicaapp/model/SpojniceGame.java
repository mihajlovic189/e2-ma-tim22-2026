package com.example.slagalicaapp.model;

import java.util.List;
import java.util.Map;

public class SpojniceGame {
    private String description;
    private Map<String, String> pairs;

    public SpojniceGame(String description, Map<String, String> pairs) {
        this.description = description;
        this.pairs = pairs;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getPairs() {
        return pairs;
    }
}

