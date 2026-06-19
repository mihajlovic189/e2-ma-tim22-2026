package com.example.slagalicaapp.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.RegionData;

import java.util.ArrayList;
import java.util.List;

public class RegionLeaderboardAdapter extends RecyclerView.Adapter<RegionLeaderboardAdapter.VH> {

    public interface OnItemClickListener {
        void onClick(RegionData region);
    }

    private List<RegionData> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setItems(List<RegionData> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_region_leaderboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RegionData rd = items.get(position);
        h.bind(rd);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(rd);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvRank, tvIcon, tvName, tvStars, tvMine;

        VH(View v) {
            super(v);
            tvRank  = v.findViewById(R.id.tvRank);
            tvIcon  = v.findViewById(R.id.tvRegionIcon);
            tvName  = v.findViewById(R.id.tvRegionName);
            tvStars = v.findViewById(R.id.tvMonthlyStars);
            tvMine  = v.findViewById(R.id.tvMyRegionBadge);
        }

        void bind(RegionData rd) {
            String medal;
            switch (rd.getRank()) {
                case 1: medal = "🥇"; break;
                case 2: medal = "🥈"; break;
                case 3: medal = "🥉"; break;
                default: medal = rd.getRank() + ".";
            }
            tvRank.setText(medal);
            tvIcon.setText(rd.getIconEmoji());
            tvName.setText(rd.getName());
            tvStars.setText(rd.getMonthlyStars() + " ⭐");
            tvMine.setVisibility(rd.isCurrentPlayerRegion() ? View.VISIBLE : View.GONE);

            int bg = rd.isCurrentPlayerRegion() ? Color.parseColor("#1E3A5F") : Color.parseColor("#111827");
            itemView.setBackgroundColor(bg);
        }
    }
}
