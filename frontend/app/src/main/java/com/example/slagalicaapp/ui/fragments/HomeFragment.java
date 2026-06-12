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
import com.example.slagalicaapp.viewmodels.AuthViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private MatchmakingManager matchmaking;
    private String currentPlayerName;
    private String currentProfileUid;

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
            }
        });

        binding.btnProfile.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ProfileFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnKorakPoKorak.setOnClickListener(v -> {
            binding.btnKorakPoKorak.setEnabled(false);
            Toast.makeText(getContext(), "Pokrecemo Korak po korak matchmaking...", Toast.LENGTH_SHORT).show();
            startMatchmaking(GameActivity.GAME_KORAK);
        });

        binding.btnMojBroj.setOnClickListener(v -> {
            binding.btnMojBroj.setEnabled(false);
            Toast.makeText(getContext(), "Pokrecemo Moj broj matchmaking...", Toast.LENGTH_SHORT).show();
            startMatchmaking(GameActivity.GAME_MOJ_BROJ);
        });

        binding.btnAsocijacija.setOnClickListener(v -> {
            binding.btnAsocijacija.setEnabled(false);
            Toast.makeText(getContext(), "Pokrecemo Asocijacije matchmaking...", Toast.LENGTH_SHORT).show();
            startMatchmaking(GameActivity.GAME_ASOCIJACIJE);
        });

        binding.btnSkocko.setOnClickListener(v -> {
            binding.btnSkocko.setEnabled(false);
            Toast.makeText(getContext(), "Pokrecemo Skocko matchmaking...", Toast.LENGTH_SHORT).show();
            startMatchmaking(GameActivity.GAME_SKOCKO);
        });

        View koZnaZnaButton = binding.getRoot().findViewById(R.id.btnKoZnaZna);
        if (koZnaZnaButton != null) {
            koZnaZnaButton.setOnClickListener(v -> {
                koZnaZnaButton.setEnabled(false);
                Toast.makeText(getContext(), "Pokrecemo Ko Zna Zna matchmaking...", Toast.LENGTH_SHORT).show();
                startMatchmaking(GameActivity.GAME_KO_ZNA_ZNA);
            });
        }

        View spojniceButton = binding.getRoot().findViewById(R.id.btnSpojnice);
        if (spojniceButton != null) {
            spojniceButton.setOnClickListener(v -> {
                spojniceButton.setEnabled(false);
                Toast.makeText(getContext(), "Pokrecemo Spojnice matchmaking...", Toast.LENGTH_SHORT).show();
                startMatchmaking(GameActivity.GAME_SPOJNICE);
            });
        }

        binding.btnNotifications.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new NotificationsFragment())
                        .addToBackStack(null)
                        .commit());

        return binding.getRoot();
    }

    private void startMatchmaking(String gameType) {
        String playerName = resolvePlayerName();
        if (TextUtils.isEmpty(playerName)) {
            Toast.makeText(getContext(), "Profil se još učitava, pokušaj ponovo", Toast.LENGTH_SHORT).show();
            resetMatchmakingButton(gameType);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String playerUid = user != null ? user.getUid() : null;
        final String finalPlayerName = playerName;
        matchmaking = new MatchmakingManager(gameType, playerName, playerUid, new MatchmakingManager.MatchmakingListener() {
            @Override
            public void onMatchFound(String roomId, int playerNumber) {
                requireActivity().runOnUiThread(() -> {
                    Intent intent = new Intent(getActivity(), GameActivity.class);
                    intent.putExtra(GameActivity.EXTRA_GAME_TYPE, gameType);
                    intent.putExtra(GameActivity.EXTRA_ROOM_ID, roomId);
                    intent.putExtra(GameActivity.EXTRA_PLAYER_NUM, playerNumber);
                    intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, finalPlayerName);
                    startActivity(intent);
                    resetMatchmakingButton(gameType);
                });
            }

            @Override
            public void onWaiting() {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Tražim protivnika...", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show();
                    resetMatchmakingButton(gameType);
                });
            }
        });

        matchmaking.findMatch();
    }

    private void resetMatchmakingButton(String gameType) {
        if (GameActivity.GAME_KORAK.equals(gameType)) {
            binding.btnKorakPoKorak.setEnabled(true);
        } else if (GameActivity.GAME_MOJ_BROJ.equals(gameType)) {
            binding.btnMojBroj.setEnabled(true);
        } else if (GameActivity.GAME_SKOCKO.equals(gameType)) {
            binding.btnSkocko.setEnabled(true);
        } else if (GameActivity.GAME_KO_ZNA_ZNA.equals(gameType)) {
            View btn = binding.getRoot().findViewById(R.id.btnKoZnaZna);
            if (btn != null) btn.setEnabled(true);
        } else if (GameActivity.GAME_SPOJNICE.equals(gameType)) {
            View btn = binding.getRoot().findViewById(R.id.btnSpojnice);
            if (btn != null) btn.setEnabled(true);
        } else if (GameActivity.GAME_ASOCIJACIJE.equals(gameType)) {
            binding.btnAsocijacija.setEnabled(true);
        }
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

        if (!TextUtils.isEmpty(currentPlayerName)) {
            return currentPlayerName;
        }

        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
