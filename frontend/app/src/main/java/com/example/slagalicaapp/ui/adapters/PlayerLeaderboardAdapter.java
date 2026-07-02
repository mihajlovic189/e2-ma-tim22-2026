package com.example.slagalicaapp.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.PlayerLeaderboardEntry;

import java.util.ArrayList;
import java.util.List;

public class PlayerLeaderboardAdapter extends RecyclerView.Adapter<PlayerLeaderboardAdapter.VH> {

    private List<PlayerLeaderboardEntry> items = new ArrayList<>();

    public void setItems(List<PlayerLeaderboardEntry> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player_leaderboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvRank, tvUsername, tvStars, tvMe;
        final ImageView ivLeagueIcon;

        VH(View v) {
            super(v);
            tvRank       = v.findViewById(R.id.tvPlayerRank);
            tvUsername   = v.findViewById(R.id.tvPlayerUsername);
            tvStars      = v.findViewById(R.id.tvPlayerStars);
            tvMe         = v.findViewById(R.id.tvMeBadge);
            ivLeagueIcon = v.findViewById(R.id.ivLeagueIcon);
        }

        void bind(PlayerLeaderboardEntry e) {
            String medal;
            switch (e.getRank()) {
                case 1: medal = "🥇"; break;
                case 2: medal = "🥈"; break;
                case 3: medal = "🥉"; break;
                default: medal = e.getRank() + ".";
            }
            tvRank.setText(medal);
            tvUsername.setText(e.getUsername());
            tvStars.setText(e.getMonthlyStars() + " ⭐");
            tvMe.setVisibility(e.isCurrentUser() ? View.VISIBLE : View.GONE);
            ivLeagueIcon.setImageResource(e.getLeagueIconRes());

            int bg = e.isCurrentUser() ? Color.parseColor("#1E3A5F") : Color.parseColor("#111827");
            itemView.setBackgroundColor(bg);
        }
    }
}
