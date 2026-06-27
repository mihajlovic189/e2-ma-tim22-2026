package com.example.slagalicaapp.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.MatchmakingManager;
import com.example.slagalicaapp.databinding.FragmentHomeBinding;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.slagalicaapp.viewmodels.AuthViewModel;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private MatchmakingManager matchmaking;
    private String currentPlayerName;
    private String currentProfileUid;
    private int currentTokens = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        authViewModel.loadProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                binding.headerStats.tvTokens.setText(String.valueOf(profile.getTokenCount()));
                binding.headerStats.tvStars.setText(String.valueOf(profile.getTotalStars()));
                binding.headerStats.tvLeague.setText(profile.getLeagueName());
                currentPlayerName = profile.getUsername();
                currentProfileUid = profile.getUid();
                currentTokens = profile.getTokenCount();
            }
        });

        binding.btnProfile.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ProfileFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnStartGame.setOnClickListener(v -> {
            binding.btnStartGame.setEnabled(false);
            startWholeGameMatchmaking();
        });

        binding.btnNotifications.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new NotificationsFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnFriends.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new FriendsFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnRegionMap.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new RegionMapFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnRegionalChat.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new RegionalChatFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnLeaderboard.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new LeaderboardFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnDailyMissions.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new DailyMissionsFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnRegionalChallenges.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ChallengesFragment())
                        .addToBackStack(null)
                        .commit());

        return binding.getRoot();
    }

    private void startWholeGameMatchmaking() {
        String playerName = resolvePlayerName();
        if (TextUtils.isEmpty(playerName)) {
            Toast.makeText(getContext(), "Profil se još učitava, pokušaj ponovo", Toast.LENGTH_SHORT).show();
            binding.btnStartGame.setEnabled(true);
            return;
        }

        if (currentTokens < 1) {
            Toast.makeText(getContext(), "Nemate dovoljno žetona! Svaki dan dobijate 5 novih.", Toast.LENGTH_LONG).show();
            binding.btnStartGame.setEnabled(true);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            binding.btnStartGame.setEnabled(true);
            return;
        }

        String playerUid = user.getUid();
        final String finalPlayerName = playerName;

        FirebaseFirestore.getInstance().collection("users").document(playerUid)
                .update("tokenCount", FieldValue.increment(-1))
                .addOnSuccessListener(unused -> {

            Toast.makeText(getContext(), "Žeton iskorišćen! Traženje protivnika za partiju...", Toast.LENGTH_SHORT).show();

            // Pokrećemo generalni matchmaking (prosleđujemo "SLAGALICA_CELE_IGRE" ili sličan tag, menadžer će to obraditi)
            matchmaking = new MatchmakingManager("SLAGALICA_MECH", finalPlayerName, playerUid, new MatchmakingManager.MatchmakingListener() {
                @Override
                public void onMatchFound(String roomId, int playerNumber) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Intent intent = new Intent(getActivity(), GameActivity.class);
                        intent.putExtra(GameActivity.EXTRA_ROOM_ID, roomId);
                        intent.putExtra(GameActivity.EXTRA_PLAYER_NUM, playerNumber);
                        intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, finalPlayerName);
                        // Ne šaljemo pojedinačni GAME_TYPE jer GameActivity kreće od prve igre automatski
                        startActivity(intent);
                        binding.btnStartGame.setEnabled(true);
                    });
                }

                @Override
                public void onWaiting() {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Tražim protivnika za igru...", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show();
                        binding.btnStartGame.setEnabled(true);
                    });
                }
            });

            matchmaking.findMatch();

        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Greška sa bazom.", Toast.LENGTH_SHORT).show();
            binding.btnStartGame.setEnabled(true);
        });
    }

    private String resolvePlayerName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (!TextUtils.isEmpty(currentProfileUid) && currentProfileUid.equals(user.getUid())
                    && !TextUtils.isEmpty(currentPlayerName)) {
                return currentPlayerName;
            }
            if (!TextUtils.isEmpty(user.getDisplayName())) {
                return user.getDisplayName();
            }
            if (!TextUtils.isEmpty(user.getEmail())) {
                return user.getEmail();
            }
        }
        return !TextUtils.isEmpty(currentPlayerName) ? currentPlayerName : null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}