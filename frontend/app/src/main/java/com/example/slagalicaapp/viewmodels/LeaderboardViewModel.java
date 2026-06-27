package com.example.slagalicaapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalicaapp.data.models.PlayerLeaderboardEntry;
import com.example.slagalicaapp.repositories.LeaderboardRepository;

import java.util.List;
import java.util.Map;

public class LeaderboardViewModel extends ViewModel {

    private final LeaderboardRepository repo = new LeaderboardRepository();

    public LiveData<List<PlayerLeaderboardEntry>> getWeeklyLeaderboard() {
        return repo.getWeeklyLeaderboard();
    }

    public LiveData<List<PlayerLeaderboardEntry>> getMonthlyLeaderboard() {
        return repo.getMonthlyLeaderboard();
    }

    public String getWeekDateRange() {
        return repo.getWeekDateRange();
    }

    public String getMonthDateRange() {
        return repo.getMonthDateRange();
    }

    public void checkAndDistributeRewards(Runnable onDone) {
        repo.checkAndDistributeRewards(onDone);
    }

    public LiveData<Map<String, Object>> getPendingReward() {
        return repo.getPendingReward();
    }

    public void markRewardNotifRead(String notifId) {
        repo.markRewardNotifRead(notifId);
    }
}