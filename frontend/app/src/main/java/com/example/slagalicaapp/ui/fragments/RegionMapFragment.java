package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.RegionData;
import com.example.slagalicaapp.databinding.FragmentRegionMapBinding;
import com.example.slagalicaapp.ui.adapters.PlayerLeaderboardAdapter;
import com.example.slagalicaapp.ui.adapters.RegionLeaderboardAdapter;
import com.example.slagalicaapp.viewmodels.RegionViewModel;

public class RegionMapFragment extends Fragment {

    private enum ViewState { MAP, REGION_LEADERBOARD, PLAYER_LEADERBOARD }

    private FragmentRegionMapBinding binding;
    private RegionViewModel viewModel;
    private RegionLeaderboardAdapter regionAdapter;
    private PlayerLeaderboardAdapter playerAdapter;
    private ViewState currentState = ViewState.MAP;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegionMapBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(RegionViewModel.class);

        setupRegionRecyclerView();
        setupPlayerRecyclerView();
        setupTabs();
        setupMapClick();

        binding.btnBack.setOnClickListener(v -> handleBack());

        viewModel.checkAndUpdateCycle();
        loadData();

        return binding.getRoot();
    }

    private void setupRegionRecyclerView() {
        regionAdapter = new RegionLeaderboardAdapter();
        binding.rvLeaderboard.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLeaderboard.addItemDecoration(
                new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        regionAdapter.setOnItemClickListener(rd -> openPlayerLeaderboard(rd));
        binding.rvLeaderboard.setAdapter(regionAdapter);
    }

    private void setupPlayerRecyclerView() {
        playerAdapter = new PlayerLeaderboardAdapter();
        binding.rvPlayerLeaderboard.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlayerLeaderboard.addItemDecoration(
                new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        binding.rvPlayerLeaderboard.setAdapter(playerAdapter);
    }

    private void setupTabs() {
        binding.btnTabMap.setOnClickListener(v -> switchToMap());
        binding.btnTabLeaderboard.setOnClickListener(v -> switchToRegionLeaderboard());
    }

    private void setupMapClick() {
        binding.serbiaMapView.setOnRegionClickListener(regionName -> {
            binding.serbiaMapView.setSelectedRegion(regionName);
        });
    }

    private void loadData() {
        binding.progressBar.setVisibility(View.VISIBLE);

        viewModel.getPlayerDots().observe(getViewLifecycleOwner(), dots -> {
            binding.serbiaMapView.setPlayerDots(dots);
            binding.progressBar.setVisibility(View.GONE);
        });

        viewModel.getMonthlyLeaderboard().observe(getViewLifecycleOwner(), regions -> {
            regionAdapter.setItems(regions);
            binding.progressBar.setVisibility(View.GONE);
        });
    }

    private void openPlayerLeaderboard(RegionData rd) {
        String month = viewModel.getCurrentMonth();
        String title = rd.getIconEmoji() + "  " + rd.getName()
                + "  —  Mesečna rang lista (" + month + ")";
        binding.tvPlayerLeaderboardTitle.setText(title);

        binding.progressBar.setVisibility(View.VISIBLE);
        viewModel.getRegionPlayerLeaderboard(rd.getName()).observe(getViewLifecycleOwner(), players -> {
            binding.progressBar.setVisibility(View.GONE);
            playerAdapter.setItems(players);
        });

        applyState(ViewState.PLAYER_LEADERBOARD);
    }

    private void handleBack() {
        if (currentState == ViewState.PLAYER_LEADERBOARD) {
            applyState(ViewState.REGION_LEADERBOARD);
        } else {
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private void switchToMap() {
        applyState(ViewState.MAP);
    }

    private void switchToRegionLeaderboard() {
        applyState(ViewState.REGION_LEADERBOARD);
    }

    private void applyState(ViewState state) {
        currentState = state;

        binding.serbiaMapView.setVisibility(state == ViewState.MAP ? View.VISIBLE : View.GONE);
        binding.rvLeaderboard.setVisibility(state == ViewState.REGION_LEADERBOARD ? View.VISIBLE : View.GONE);
        binding.layoutPlayerLeaderboard.setVisibility(state == ViewState.PLAYER_LEADERBOARD ? View.VISIBLE : View.GONE);

        boolean mapActive = state == ViewState.MAP;
        binding.btnTabMap.setBackgroundTintList(
                requireContext().getColorStateList(mapActive ? R.color.primary : R.color.button_bg));
        binding.btnTabLeaderboard.setBackgroundTintList(
                requireContext().getColorStateList(mapActive ? R.color.button_bg : R.color.primary));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
