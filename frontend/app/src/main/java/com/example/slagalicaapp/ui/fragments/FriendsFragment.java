package com.example.slagalicaapp.ui.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.slagalicaapp.data.firebase.MatchmakingManager;
import com.example.slagalicaapp.data.models.FriendData;
import com.example.slagalicaapp.data.models.GameInvite;
import com.example.slagalicaapp.databinding.FragmentFriendsBinding;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.example.slagalicaapp.ui.adapters.FriendsAdapter;
import com.example.slagalicaapp.viewmodels.FriendsViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import androidx.activity.result.ActivityResultLauncher;

public class FriendsFragment extends Fragment {

    private FragmentFriendsBinding binding;
    private FriendsViewModel       vm;
    private FriendsAdapter         friendsAdapter;
    private FriendsAdapter         searchAdapter;

    private AlertDialog inviteSentDialog;
    private String      sentInviteId;
    private String      sentRoomId;
    private String      sentGameType;


    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), this::onQrResult);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFriendsBinding.inflate(inflater, container, false);
        vm      = new ViewModelProvider(this).get(FriendsViewModel.class);

        setupAdapters();
        setupTabs();
        setupListeners();
        loadFriends();

        return binding.getRoot();
    }

    private void setupAdapters() {
        friendsAdapter = new FriendsAdapter(FriendsAdapter.Mode.FRIENDS, new FriendsAdapter.Callbacks() {
            @Override public void onInviteClick(FriendData f) { sendInvite(f); }
            @Override public void onAddClick(FriendData f)    {}
            @Override public void onRemoveClick(FriendData f) { removeFriend(f); }
        });

        searchAdapter = new FriendsAdapter(FriendsAdapter.Mode.SEARCH, new FriendsAdapter.Callbacks() {
            @Override public void onInviteClick(FriendData f) {}
            @Override public void onAddClick(FriendData f)    { addFriend(f); }
            @Override public void onRemoveClick(FriendData f) { removeFriend(f); }
        });

        binding.rvFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvFriends.setAdapter(friendsAdapter);

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSearchResults.setAdapter(searchAdapter);
    }

    private void setupTabs() {
        binding.tabFriendsList.setOnClickListener(v -> switchTab(true));
        binding.tabSearch.setOnClickListener(v -> switchTab(false));
        switchTab(true);
    }

    private void switchTab(boolean showFriends) {
        binding.panelFriendsList.setVisibility(showFriends ? View.VISIBLE : View.GONE);
        binding.panelSearch.setVisibility(showFriends ? View.GONE : View.VISIBLE);
        binding.tabFriendsList.setTextColor(showFriends ? Color.WHITE : Color.parseColor("#9CA3AF"));
        binding.tabSearch.setTextColor(showFriends ? Color.parseColor("#9CA3AF") : Color.WHITE);
    }

    private void setupListeners() {
        binding.btnFriendsBack.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        binding.btnScanQr.setOnClickListener(v -> {
            ScanOptions opts = new ScanOptions();
            opts.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            opts.setPrompt("Skeniraj QR kod prijatelja");
            opts.setOrientationLocked(false);
            qrScanLauncher.launch(opts);
        });

        binding.btnSearch.setOnClickListener(v -> {
            String q = binding.etSearchUsername.getText().toString().trim();
            if (q.isEmpty()) return;
            vm.searchByUsername(q).observe(getViewLifecycleOwner(), results -> {
                searchAdapter.setItems(results);
                binding.tvSearchEmpty.setVisibility(
                        results == null || results.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void loadFriends() {
        vm.getFriends().observe(getViewLifecycleOwner(), friends -> {
            friendsAdapter.setItems(friends);
            binding.tvNoFriends.setVisibility(
                    friends == null || friends.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void addFriend(FriendData fd) {
        vm.addFriend(fd).observe(getViewLifecycleOwner(), ok -> {
            if (Boolean.TRUE.equals(ok)) {
                Toast.makeText(getContext(), fd.getUsername() + " dodat u prijatelje!", Toast.LENGTH_SHORT).show();
                fd.setAlreadyFriend(true);
                searchAdapter.notifyDataSetChanged();
                loadFriends();
            } else {
                Toast.makeText(getContext(), "Greška pri dodavanju.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeFriend(FriendData fd) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Ukloni prijatelja")
                .setMessage("Sigurno želiš da ukloniš " + fd.getUsername() + " iz liste prijatelja?")
                .setPositiveButton("Ukloni", (d, w) ->
                        vm.removeFriend(fd.getUid()).observe(getViewLifecycleOwner(), ok -> {
                            if (Boolean.TRUE.equals(ok)) {
                                fd.setAlreadyFriend(false);
                                searchAdapter.notifyDataSetChanged();
                                loadFriends();
                            } else {
                                Toast.makeText(getContext(), "Greška.", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void onQrResult(ScanIntentResult result) {
        if (result == null || result.getContents() == null) return;
        String content = result.getContents();

        if (!content.startsWith("slagalica://invite?")) {
            Toast.makeText(getContext(), "Nevažeći QR kod.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String[] params = content.substring("slagalica://invite?".length()).split("&");
            String uid = "", username = "";
            for (String p : params) {
                if (p.startsWith("uid="))      uid      = p.substring(4);
                if (p.startsWith("username=")) username = p.substring(9);
            }
            FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
            if (uid.isEmpty() || (me != null && uid.equals(me.getUid()))) {
                Toast.makeText(getContext(), "Ne možeš dodati sebe.", Toast.LENGTH_SHORT).show();
                return;
            }
            addFriend(new FriendData(uid, username, ""));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Greška pri čitanju QR koda.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendInvite(FriendData friend) {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) return;

        String gameType      = GameActivity.GAME_MECH;
        String myDisplayName = me.getDisplayName() != null ? me.getDisplayName() : "";

        MatchmakingManager mm = new MatchmakingManager(gameType, myDisplayName, me.getUid(),
                new MatchmakingManager.MatchmakingListener() {
                    @Override public void onMatchFound(String roomId, int playerNumber) {}
                    @Override public void onWaiting() {}
                    @Override public void onError(String msg) {}
                });

        String roomId = mm.createDirectRoom(null);
        if (roomId == null) {
            Toast.makeText(getContext(), "Greška pri kreiranju sobe.", Toast.LENGTH_SHORT).show();
            return;
        }
        sentRoomId   = roomId;
        sentGameType = gameType;

        vm.sendGameInvite(friend.getUid(), friend.getUsername(), gameType, roomId)
                .observe(getViewLifecycleOwner(), inviteId -> {
                    if (inviteId == null) {
                        Toast.makeText(getContext(), "Greška pri slanju poziva.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sentInviteId = inviteId;
                    showInviteSentDialog(friend, gameType, inviteId, roomId, myDisplayName);
                });
    }

    private void showInviteSentDialog(FriendData friend, String gameType,
                                      String inviteId, String roomId, String myDisplayName) {
        inviteSentDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Poziv poslat")
                .setMessage("Čekam odgovor od " + friend.getUsername() + "…")
                .setCancelable(false)
                .setNegativeButton("Otkaži poziv", (d, w) -> {
                    vm.updateInviteStatus(inviteId, GameInvite.STATUS_CANCELLED);
                    vm.cleanupInvite(inviteId);
                    vm.cleanupRoom(gameType, roomId);
                    sentInviteId = null;
                })
                .show();

        vm.watchInviteStatus(inviteId).observe(getViewLifecycleOwner(), status -> {
            if (status == null) {
                if (inviteSentDialog != null && inviteSentDialog.isShowing()) {
                    dismissSentDialog();
                    vm.cleanupRoom(gameType, roomId);
                    Toast.makeText(getContext(), friend.getUsername() + " je odbio poziv.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            switch (status) {
                case GameInvite.STATUS_ACCEPTED:
                    dismissSentDialog();
                    launchGame(roomId, myDisplayName);
                    vm.cleanupInvite(inviteId);
                    break;
                case GameInvite.STATUS_DECLINED:
                    dismissSentDialog();
                    Toast.makeText(getContext(), friend.getUsername() + " je odbio poziv.", Toast.LENGTH_SHORT).show();
                    vm.cleanupInvite(inviteId);
                    vm.cleanupRoom(gameType, roomId);
                    break;
                case GameInvite.STATUS_CANCELLED:
                    dismissSentDialog();
                    break;
            }
        });
    }

    private void dismissSentDialog() {
        if (inviteSentDialog != null && inviteSentDialog.isShowing()) inviteSentDialog.dismiss();
        inviteSentDialog = null;
        sentInviteId     = null;
    }

    private void launchGame(String roomId, String playerName) {
        Intent intent = new Intent(getActivity(), GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_ROOM_ID,    roomId);
        intent.putExtra(GameActivity.EXTRA_PLAYER_NUM, 1);
        intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, playerName);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissSentDialog();
        binding = null;
    }
}
