package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.repositories.DailyMissionsRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyMissionsFragment extends Fragment {

    private DailyMissionsRepository repo;
    private TextView tvDate, tvProgress;
    private TextView tvWinMatchStatus, tvSendChatStatus, tvFriendlyMatchStatus, tvWinTournamentStatus;
    private TextView tvAllRewardStatus, tvAllRewardDesc;
    private View cardAllReward;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_missions, container, false);

        repo = new DailyMissionsRepository();

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        tvDate = view.findViewById(R.id.tvDate);
        tvProgress = view.findViewById(R.id.tvProgress);
        tvWinMatchStatus = view.findViewById(R.id.tvWinMatchStatus);
        tvSendChatStatus = view.findViewById(R.id.tvSendChatStatus);
        tvFriendlyMatchStatus = view.findViewById(R.id.tvFriendlyMatchStatus);
        tvWinTournamentStatus = view.findViewById(R.id.tvWinTournamentStatus);
        tvAllRewardStatus = view.findViewById(R.id.tvAllRewardStatus);
        tvAllRewardDesc = view.findViewById(R.id.tvAllRewardDesc);
        cardAllReward = view.findViewById(R.id.cardAllReward);

        tvDate.setText("Danas: " + new SimpleDateFormat("dd. MMMM yyyy.", Locale.getDefault()).format(new Date()));

        loadMissions();

        return view;
    }

    private void loadMissions() {
        repo.getMissions().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;

            int completed = data.completedCount();
            tvProgress.setText("Napredak: " + completed + " / 4");

            setMissionStatus(tvWinMatchStatus, data.winMatch);
            setMissionStatus(tvSendChatStatus, data.sendChat);
            setMissionStatus(tvFriendlyMatchStatus, data.friendlyMatch);
            setMissionStatus(tvWinTournamentStatus, data.winTournament);

            if (data.allCompleted()) {
                cardAllReward.setVisibility(View.VISIBLE);
                if (data.rewardsClaimed) {
                    tvAllRewardStatus.setText("✅");
                    tvAllRewardDesc.setText("Nagrada već preuzeta! (+15⭐, +2 tokena)");
                } else {
                    tvAllRewardStatus.setText("⏳");
                    tvAllRewardDesc.setText("Završi sve misije za bonus nagradu!");
                }
            } else {
                cardAllReward.setVisibility(View.GONE);
            }
        });
    }

    private void setMissionStatus(TextView tv, boolean completed) {
        tv.setText(completed ? "✅" : "❌");
    }

    @Override
    public void onResume() {
        super.onResume();
        repo.refreshMissions();
    }
}