package com.example.slagalicaapp.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.model.NotificationModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface NotificationListener {
        void onMarkRead(NotificationModel n);
        void onAction(NotificationModel n);
    }

    private List<NotificationModel> notifications;
    private final NotificationListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public NotificationAdapter(List<NotificationModel> notifications, NotificationListener listener) {
        this.notifications = notifications;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationModel n = notifications.get(position);

        holder.title.setText(n.getTitle());
        holder.body.setText(n.getBody());
        holder.timestamp.setText(sdf.format(new Date(n.getTimestamp())));

        // Read / unread visual
        if (n.isRead()) {
            holder.card.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
            holder.title.setAlpha(0.55f);
            holder.body.setAlpha(0.55f);
            holder.unreadDot.setVisibility(View.INVISIBLE);
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE);
            holder.title.setAlpha(1.0f);
            holder.body.setAlpha(1.0f);
            holder.unreadDot.setVisibility(View.VISIBLE);
        }

        // Type icon
        switch (n.getType()) {
            case "chat":
                holder.typeIcon.setImageResource(android.R.drawable.ic_menu_send);
                break;
            case "ranking":
                holder.typeIcon.setImageResource(R.drawable.ic_zvezda);
                break;
            case "rewards":
                holder.typeIcon.setImageResource(android.R.drawable.ic_popup_reminder);
                break;
            default:
                holder.typeIcon.setImageResource(android.R.drawable.ic_dialog_info);
                break;
        }

        // Action button label
        switch (n.getType()) {
            case "chat":    holder.btnAction.setText("Otvori čet");   break;
            case "ranking": holder.btnAction.setText("Rang lista");   break;
            case "rewards": holder.btnAction.setText("Preuzmi");      break;
            default:        holder.btnAction.setText("Detalji");      break;
        }

        // Click on whole card → mark as read
        holder.itemView.setOnClickListener(v -> {
            if (!n.isRead()) listener.onMarkRead(n);
        });

        // Action button click
        holder.btnAction.setOnClickListener(v -> listener.onAction(n));
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void updateData(List<NotificationModel> newNotifications) {
        this.notifications = newNotifications;
        notifyDataSetChanged();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        View unreadDot;
        ImageView typeIcon;
        TextView title;
        TextView body;
        TextView timestamp;
        Button btnAction;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (CardView) itemView;
            unreadDot = itemView.findViewById(R.id.unreadDot);
            typeIcon = itemView.findViewById(R.id.ivIcon);
            title = itemView.findViewById(R.id.tvNaslov);
            body = itemView.findViewById(R.id.tvOpis);
            timestamp = itemView.findViewById(R.id.tvVrijeme);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}
