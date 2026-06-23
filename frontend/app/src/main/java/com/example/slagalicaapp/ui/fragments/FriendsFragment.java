package com.example.slagalicaapp.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.MatchmakingManager;
import com.example.slagalicaapp.data.models.FriendData;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class FriendsFragment extends Fragment {

    private RecyclerView rvFriends;
    private DatabaseReference db;
    private FirebaseAuth auth;
    private String currentUserName = "Marko";
    private ValueEventListener inviteResponseListener;
    private DatabaseReference currentInviteRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        db = FirebaseDatabase.getInstance().getReference();
        auth = FirebaseAuth.getInstance();

        rvFriends = view.findViewById(R.id.rvFriends);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));

        return view;
    }

    /**
     * Pozovi ovu metodu unutar tvog FriendsAdapter-a kada korisnik klikne na nekog prijatelja.
     */
    public void prikaziIzborIgre(FriendData friend) {
        String[] igre = {"Ko zna zna", "Spojnice", "Asocijacije", "Skočko", "Korak po korak", "Moj broj"};
        String[] gameTypes = {"KO_ZNA_ZNA", "SPOJNICE", "ASOCIJACIJE", "SKOCKO", "KORAK_PO_KORAK", "MOJ_BROJ"};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Izaberi igru za duel sa " + friend.getUsername());
        builder.setItems(igre, (dialog, which) -> {
            String izabranaIgra = gameTypes[which];
            posaljiPozivnicu(friend, izabranaIgra);
        });
        builder.show();
    }

    private void posaljiPozivnicu(FriendData friend, String gameType) {
        if (auth.getCurrentUser() == null) return;
        String myUid = auth.getCurrentUser().getUid();

        final String[] finalRoomIdHolder = new String[1];

        MatchmakingManager manager = new MatchmakingManager(gameType, currentUserName, myUid, new MatchmakingManager.MatchmakingListener() {
            @Override
            public void onMatchFound(String roomId, int playerNumber) {
                pokreniIgru(roomId, gameType, playerNumber);
            }

            @Override
            public void onWaiting() {}

            @Override
            public void onError(String message) {
                Toast.makeText(getContext(), "Greška: " + message, Toast.LENGTH_SHORT).show();
            }
        });

        String generatedRoomId = manager.createDirectRoom(() -> {
            String currentRoomId = finalRoomIdHolder[0];

            if (currentRoomId == null) return;

            Map<String, Object> inviteData = new HashMap<>();
            inviteData.put("senderUid", myUid);
            inviteData.put("senderName", currentUserName);
            inviteData.put("gameType", gameType);
            inviteData.put("roomId", currentRoomId);
            inviteData.put("status", "pending");

            currentInviteRef = db.child("game_invites").child(friend.getUid());
            currentInviteRef.setValue(inviteData)
                    .addOnSuccessListener(unused -> {
                        prikaziCekanjeDijalog(friend, manager);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Neuspešno slanje pozivnice: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        finalRoomIdHolder[0] = generatedRoomId;

        if (generatedRoomId == null) {
            Toast.makeText(getContext(), "Greška pri kreiranju sobe.", Toast.LENGTH_SHORT).show();
        }
    }

    private void prikaziCekanjeDijalog(FriendData friend, MatchmakingManager manager) {
        AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Pozivnica poslata")
                .setMessage("Čeka se da " + friend.getUsername() + " prihvati izazov...")
                .setCancelable(false)
                .setNegativeButton("Otkaži", (dialog, which) -> {
                    if (currentInviteRef != null) currentInviteRef.removeValue();
                    manager.cancelSearch();
                    if (inviteResponseListener != null && currentInviteRef != null) {
                        currentInviteRef.removeEventListener(inviteResponseListener);
                    }
                }).create();

        progressDialog.show();

        inviteResponseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);
                if ("rejected".equals(status)) {
                    Toast.makeText(getContext(), friend.getUsername() + " je odbio poziv.", Toast.LENGTH_SHORT).show();
                    progressDialog.dismiss();
                    if (currentInviteRef != null) {
                        currentInviteRef.removeValue();
                        currentInviteRef.removeEventListener(this);
                    }
                }
                if ("accepted".equals(status)) {
                    progressDialog.dismiss();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        currentInviteRef.addValueEventListener(inviteResponseListener);
    }

    private void pokreniIgru(String roomId, String gameType, int playerNumber) {
        if (inviteResponseListener != null && currentInviteRef != null) {
            currentInviteRef.removeEventListener(inviteResponseListener);
            currentInviteRef.removeValue();
        }

        Intent intent = new Intent(getActivity(), GameActivity.class);
        intent.putExtra("ROOM_ID", roomId);
        intent.putExtra("GAME_TYPE", gameType);
        intent.putExtra("PLAYER_NUMBER", playerNumber);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (inviteResponseListener != null && currentInviteRef != null) {
            currentInviteRef.removeEventListener(inviteResponseListener);
        }
    }
}