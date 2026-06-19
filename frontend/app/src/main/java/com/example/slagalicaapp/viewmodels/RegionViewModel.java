package com.example.slagalicaapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalicaapp.data.models.PlayerLeaderboardEntry;
import com.example.slagalicaapp.data.models.PlayerMapDot;
import com.example.slagalicaapp.data.models.RegionData;
import com.example.slagalicaapp.repositories.RegionRepository;

import java.util.List;

public class RegionViewModel extends ViewModel {
    private final RegionRepository repo = new RegionRepository();

    public LiveData<List<RegionData>> getMonthlyLeaderboard() {
        return repo.getMonthlyLeaderboard();
    }

    public LiveData<List<PlayerMapDot>> getPlayerDots() {
        return repo.getPlayerDots();
    }

    public LiveData<RegionData> getRegionStats(String regionName) {
        return repo.getRegionStats(regionName);
    }

    public LiveData<List<PlayerLeaderboardEntry>> getRegionPlayerLeaderboard(String region) {
        return repo.getRegionPlayerLeaderboard(region);
    }

    public void checkAndUpdateCycle() {
        repo.checkAndUpdateCycleForCurrentUser();
    }

    public String getCurrentMonth() {
        return repo.getCurrentMonth();
    }
}
