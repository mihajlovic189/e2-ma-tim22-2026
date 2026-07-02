package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.slagalicaapp.data.firebase.ChatManager;
import com.example.slagalicaapp.data.models.ChatMessage;
import com.example.slagalicaapp.databinding.FragmentRegionalChatBinding;
import com.example.slagalicaapp.repositories.DailyMissionsRepository;
import com.example.slagalicaapp.ui.adapters.ChatAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class RegionalChatFragment extends Fragment implements ChatManager.ChatListener {

    private FragmentRegionalChatBinding binding;
    private ChatManager chatManager;
    private ChatAdapter adapter;

    private String currentUserId;
    private String currentUserName = "Igrač";
    private String userRegion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRegionalChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), "Korisnik nije ulogovan!", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUserId = currentUser.getUid();

        setupRecyclerView();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || documentSnapshot == null) return;

                    if (documentSnapshot.exists()) {
                        String nameFromFirestore = documentSnapshot.getString("username");
                        String regionFromFirestore = documentSnapshot.getString("region");

                        if (nameFromFirestore != null) {
                            currentUserName = nameFromFirestore;
                        }

                        if (getArguments() != null && getArguments().containsKey("region")) {
                            userRegion = getArguments().getString("region");
                        } else if (regionFromFirestore != null) {
                            userRegion = regionFromFirestore;
                        } else {
                            userRegion = "Global";
                        }

                        initializeChat();
                    } else {
                        Toast.makeText(getContext(), "Profil ne postoji u Firestore-u!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Greška pri čitanju Firestore-a: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etMessageInput.getText().toString().trim();
            if (!TextUtils.isEmpty(text) && chatManager != null) {
                chatManager.sendMessage(currentUserId, currentUserName, text);
                binding.etMessageInput.setText("");
                new DailyMissionsRepository().completeMission("sendChat", new DailyMissionsRepository.MissionCallback() {
                    @Override
                    public void onSuccess(boolean newlyCompleted) {
                        if (isAdded() && newlyCompleted) {
                            Toast.makeText(getContext(), "Misija 'Pošalji poruku' završena! +3⭐", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onError(String error) {
                        if (isAdded()) {
                            Toast.makeText(getContext(), "Greška: " + error, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void initializeChat() {
        chatManager = new ChatManager(userRegion, this);
        chatManager.startListening();
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter(currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        binding.rvChatMessages.setLayoutManager(layoutManager);
        binding.rvChatMessages.setAdapter(adapter);
    }

    @Override
    public void onMessagesUpdated(List<ChatMessage> messages) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            adapter.setMessages(messages);
            if (adapter.getItemCount() > 0) {
                binding.rvChatMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    @Override
    public void onError(String error) {
        if (isAdded()) {
            Toast.makeText(getContext(), "Greška: " + error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatManager != null) chatManager.stopListening();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        com.example.slagalicaapp.SlagalicaApp.isUserInChatScreen = true;

        if (userRegion != null) {
            String topicName = "chat_" + userRegion.replace(" ", "_");
            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(topicName);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        com.example.slagalicaapp.SlagalicaApp.isUserInChatScreen = false;

        if (userRegion != null) {
            String topicName = "chat_" + userRegion.replace(" ", "_");
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(topicName);
        }
    }
}