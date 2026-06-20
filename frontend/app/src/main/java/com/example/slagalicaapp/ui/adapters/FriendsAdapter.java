package com.example.slagalicaapp.ui.adapters;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.FriendData;

import java.util.ArrayList;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.VH> {

    public enum Mode { FRIENDS, SEARCH }

    public interface Callbacks {
        void onInviteClick(FriendData friend);
        void onAddClick(FriendData friend);
        void onRemoveClick(FriendData friend);
    }

    private final Mode      mode;
    private final Callbacks cb;
    private List<FriendData> items = new ArrayList<>();

    public FriendsAdapter(Mode mode, Callbacks cb) {
        this.mode = mode;
        this.cb   = cb;
    }

    public void setItems(List<FriendData> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FriendData fd = items.get(position);

        h.tvUsername.setText(fd.getUsername());
        h.tvLeague.setText(fd.getLeagueName() != null ? fd.getLeagueName() : "");
        h.tvStars.setText("⭐ " + fd.getTotalStars());
        h.tvMonthly.setText("Mesečno: " + fd.getMonthlyStars());

        // Avatar
        if (fd.getAvatarUri() != null && !fd.getAvatarUri().isEmpty()) {
            try { h.ivAvatar.setImageURI(Uri.parse(fd.getAvatarUri())); }
            catch (Exception e) { h.ivAvatar.setImageResource(android.R.drawable.sym_def_app_icon); }
        } else {
            h.ivAvatar.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // Online status dot
        if (fd.isInGame()) {
            h.tvStatus.setText("🟡 U igri");
        } else if (fd.isOnline()) {
            h.tvStatus.setText("🟢 Online");
        } else {
            h.tvStatus.setText("🔴 Offline");
        }

        // Buttons
        if (mode == Mode.FRIENDS) {
            h.btnAdd.setVisibility(View.GONE);
            h.btnInvite.setVisibility(fd.canInvite() ? View.VISIBLE : View.GONE);
            h.btnRemove.setVisibility(View.VISIBLE);
            h.btnInvite.setOnClickListener(v -> cb.onInviteClick(fd));
            h.btnRemove.setOnClickListener(v -> cb.onRemoveClick(fd));
        } else { // SEARCH mode
            h.btnInvite.setVisibility(View.GONE);
            if (fd.isAlreadyFriend()) {
                h.btnAdd.setVisibility(View.GONE);
                h.btnRemove.setVisibility(View.VISIBLE);
                h.btnRemove.setOnClickListener(v -> cb.onRemoveClick(fd));
            } else {
                h.btnRemove.setVisibility(View.GONE);
                h.btnAdd.setVisibility(View.VISIBLE);
                h.btnAdd.setText("Dodaj");
                h.btnAdd.setEnabled(true);
                h.btnAdd.setOnClickListener(v -> cb.onAddClick(fd));
            }
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView  tvUsername, tvLeague, tvStars, tvMonthly, tvStatus;
        Button    btnInvite, btnAdd, btnRemove;

        VH(View v) {
            super(v);
            ivAvatar   = v.findViewById(R.id.ivFriendAvatar);
            tvUsername = v.findViewById(R.id.tvFriendUsername);
            tvLeague   = v.findViewById(R.id.tvFriendLeague);
            tvStars    = v.findViewById(R.id.tvFriendStars);
            tvMonthly  = v.findViewById(R.id.tvFriendMonthly);
            tvStatus   = v.findViewById(R.id.tvFriendStatus);
            btnInvite  = v.findViewById(R.id.btnFriendInvite);
            btnAdd     = v.findViewById(R.id.btnFriendAdd);
            btnRemove  = v.findViewById(R.id.btnFriendRemove);
        }
    }
}
