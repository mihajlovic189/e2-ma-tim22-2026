package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import com.example.slagalicaapp.data.models.ChatMessage;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.List;

public class ChatManager {

    public interface ChatListener {
        void onMessagesUpdated(List<ChatMessage> messages);
        void onError(String error);
    }

    private final DatabaseReference chatRef;
    private ValueEventListener chatListener;
    private final ChatListener listener;

    public ChatManager(String regionId, ChatListener listener) {
        this.chatRef = FirebaseDatabase.getInstance().getReference()
                .child("regional_chats").child(regionId);
        this.listener = listener;
    }

    public void startListening() {
        chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> messages = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    ChatMessage msg = data.getValue(ChatMessage.class);
                    if (msg != null) {
                        messages.add(msg);
                    }
                }
                listener.onMessagesUpdated(messages);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        chatRef.limitToLast(50).addValueEventListener(chatListener);
    }

    public void sendMessage(String senderId, String senderName, String text) {
        String msgId = chatRef.push().getKey();
        if (msgId == null) return;

        ChatMessage message = new ChatMessage(senderId, senderName, text, System.currentTimeMillis());
        chatRef.child(msgId).setValue(message);
    }

    public void stopListening() {
        if (chatListener != null) {
            chatRef.removeEventListener(chatListener);
            chatListener = null;
        }
    }
}