package com.example.slagalicaapp.ui.adapters;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.ChatMessage;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String currentUserId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault());

    public ChatAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setMessages(List<ChatMessage> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (messages.get(position).getSenderId().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_sent, parent, false);
            return new SentMessageViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_received, parent, false);
            return new ReceivedMessageViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        String timeStr = timeFormat.format(new Date(msg.getTimestamp()));

        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).tvText.setText(msg.getText());
            ((SentMessageViewHolder) holder).tvTime.setText(timeStr);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).tvName.setText(msg.getSenderName());
            ((ReceivedMessageViewHolder) holder).tvText.setText(msg.getText());
            ((ReceivedMessageViewHolder) holder).tvTime.setText(timeStr);
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvText, tvTime;
        SentMessageViewHolder(View v) {
            super(v);
            tvText = v.findViewById(R.id.tv_message_text);
            tvTime = v.findViewById(R.id.tv_message_time);
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvText, tvTime;
        ReceivedMessageViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_sender_name);
            tvText = v.findViewById(R.id.tv_message_text);
            tvTime = v.findViewById(R.id.tv_message_time);
        }
    }
}