package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.PlayerLeaderboardEntry;
import com.example.slagalicaapp.ui.adapters.PlayerLeaderboardAdapter;
import com.example.slagalicaapp.viewmodels.LeaderboardViewModel;

import java.util.List;
import java.util.Map;

public class LeaderboardFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 120_000L;

    private enum Tab { WEEKLY, MONTHLY }

    private LeaderboardViewModel viewModel;
    private PlayerLeaderboardAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView, tvCycleRange;
    private Button btnTabWeekly, btnTabMonthly;
    private Tab currentTab = Tab.WEEKLY;
    private CountDownTimer refreshTimer;
    private LiveData<List<PlayerLeaderboardEntry>> currentLiveData;
    private Observer<List<PlayerLeaderboardEntry>> currentObserver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        viewModel = new ViewModelProvider(this).get(LeaderboardViewModel.class);

        recyclerView = view.findViewById(R.id.rvLeaderboard);
        emptyView = view.findViewById(R.id.emptyView);
        tvCycleRange = view.findViewById(R.id.tvCycleRange);
        btnTabWeekly = view.findViewById(R.id.btnTabWeekly);
        btnTabMonthly = view.findViewById(R.id.btnTabMonthly);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PlayerLeaderboardAdapter();
        recyclerView.setAdapter(adapter);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        btnTabWeekly.setOnClickListener(v -> switchTab(Tab.WEEKLY));
        btnTabMonthly.setOnClickListener(v -> switchTab(Tab.MONTHLY));

        viewModel.checkAndDistributeRewards(() -> {
            if (isAdded()) {
                loadData();
                checkForRewardDialog();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    private void switchTab(Tab tab) {
        currentTab = tab;
        updateTabVisuals();
        loadData();
    }

    private void loadData() {
        if (currentLiveData != null && currentObserver != null) {
            currentLiveData.removeObserver(currentObserver);
        }

        tvCycleRange.setText(currentTab == Tab.WEEKLY
                ? "Nedeljni ciklus: " + viewModel.getWeekDateRange()
                : "Mesečni ciklus: " + viewModel.getMonthDateRange());

        currentLiveData = currentTab == Tab.WEEKLY
                ? viewModel.getWeeklyLeaderboard()
                : viewModel.getMonthlyLeaderboard();

        currentObserver = entries -> {
            if (entries == null || entries.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                adapter.setItems(entries);
            }
        };

        currentLiveData.observe(getViewLifecycleOwner(), currentObserver);
    }

    private void updateTabVisuals() {
        boolean weekly = currentTab == Tab.WEEKLY;
        btnTabWeekly.setBackgroundTintList(
                requireContext().getColorStateList(weekly ? R.color.primary : R.color.button_bg));
        btnTabMonthly.setBackgroundTintList(
                requireContext().getColorStateList(weekly ? R.color.button_bg : R.color.primary));
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        refreshTimer = new CountDownTimer(REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (isAdded()) {
                    loadData();
                    startAutoRefresh();
                }
            }
        }.start();
    }

    private void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
    }

    private void checkForRewardDialog() {
        viewModel.getPendingReward().observe(getViewLifecycleOwner(), reward -> {
            if (reward != null && isAdded() && getContext() != null) {
                showRewardDialog(reward);
            }
        });
    }

    private void showRewardDialog(Map<String, Object> reward) {
        if (!isAdded() || getContext() == null) return;

        String title = (String) reward.get("title");
        String body = (String) reward.get("body");
        String notifId = (String) reward.get("id");

        if (title == null) title = "Nagrada za plasman!";
        if (body == null) body = "Osvojili ste nagradu na rang listi!";

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("🎉 " + title)
                .setMessage(body + "\n\nČestitamo na odličnom rezultatu!")
                .setCancelable(false)
                .setPositiveButton("Super! 🎊", (d, w) -> {
                    if (notifId != null) viewModel.markRewardNotifRead(notifId);
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAutoRefresh();
    }
}