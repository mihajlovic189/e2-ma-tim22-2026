package com.example.slagalicaapp.ui.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.firebase.ChallengeManager;
import com.example.slagalicaapp.data.models.Challenge;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChallengesFragment extends Fragment {

    private TextView tvRegionTitle;
    private Button btnCreateChallenge;
    private RecyclerView rvChallenges;
    private ChallengeAdapter adapter;
    private List<Challenge> challengeList = new ArrayList<>();

    private String userRegion = "";
    private String currentUsername = "";
    private String currentUid;

    private final ChallengeManager challengeManager = new ChallengeManager();
    private DatabaseReference dbRef;
    private ValueEventListener challengesListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_challenges, container, false);

        tvRegionTitle = view.findViewById(R.id.tvRegionTitle);
        btnCreateChallenge = view.findViewById(R.id.btnCreateChallenge);
        rvChallenges = view.findViewById(R.id.rvChallenges);

        currentUid = FirebaseAuth.getInstance().getUid();
        dbRef = FirebaseDatabase.getInstance().getReference("challenges");

        rvChallenges.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChallengeAdapter();
        rvChallenges.setAdapter(adapter);

        ucitajPodatkeKorisnika();
        btnCreateChallenge.setOnClickListener(v -> prikaziDijalogZaKreiranje());

        return view;
    }

    private void ucitajPodatkeKorisnika() {
        if (currentUid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(currentUid)
                .get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        userRegion = documentSnapshot.getString("region");
                        currentUsername = documentSnapshot.getString("username");
                        if (userRegion == null) userRegion = "Vojvodina";

                        tvRegionTitle.setText(getString(R.string.challenges_title_prefix, userRegion));
                        slusajAktivneIzazove();
                    }
                });
    }

    private void slusajAktivneIzazove() {
        challengesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                challengeList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Challenge challenge = ds.getValue(Challenge.class);
                    if (challenge != null) {
                        challengeList.add(challenge);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbRef.orderByChild("region").equalTo(userRegion).addValueEventListener(challengesListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (challengesListener != null) {
            dbRef.orderByChild("region").equalTo(userRegion).removeEventListener(challengesListener);
        }
    }

    private void prikaziDijalogZaKreiranje() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.dialog_create_title));

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_challenge, null);
        EditText etStars = view.findViewById(R.id.etStarsWager);
        EditText etTokens = view.findViewById(R.id.etTokensWager);

        builder.setView(view);
        builder.setPositiveButton(getString(R.string.dialog_btn_wager), (dialog, which) -> {
            String starsStr = etStars.getText().toString().trim();
            String tokensStr = etTokens.getText().toString().trim();

            int stars = starsStr.isEmpty() ? 0 : Integer.parseInt(starsStr);
            int tokens = tokensStr.isEmpty() ? 0 : Integer.parseInt(tokensStr);

            if (stars < 1) {
                Toast.makeText(getContext(), "Ulog mora biti najmanje 1 zvezda!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (stars > 10 || tokens > 2) {
                Toast.makeText(getContext(), getString(R.string.error_max_wager), Toast.LENGTH_SHORT).show();
                return;
            }

            challengeManager.createChallenge(currentUid, currentUsername, userRegion, stars, tokens, new ChallengeManager.ChallengeOperationListener() {
                @Override
                public void onSuccess(String challengeId) {
                    Toast.makeText(getContext(), getString(R.string.challenge_created_success), Toast.LENGTH_SHORT).show();
                    pokreniPartiju(challengeId);
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                }
            });
        });
        builder.setNegativeButton(getString(R.string.dialog_btn_cancel), null);
        builder.show();
    }

    private void pokreniPartiju(String challengeId) {
        Intent intent = new Intent(getActivity(), GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_CHALLENGE_ID, challengeId);
        intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, currentUsername);
        startActivity(intent);
    }

    private class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.ChallengeViewHolder> {

        @NonNull
        @Override
        public ChallengeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_challenge, parent, false);
            return new ChallengeViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ChallengeViewHolder holder, int position) {
            Challenge c = challengeList.get(position);

            holder.tvCreator.setText(getString(R.string.challenge_host_label, c.getCreatorName()));
            holder.tvWager.setText(getString(R.string.challenge_wager_label, c.getStarsWager(), c.getTokensWager()));

            int trenutnoIgraca = c.getJoinedPlayers().size();
            holder.tvCount.setText(getString(R.string.challenge_players_count, trenutnoIgraca, 4));

            if (Challenge.STATUS_FINISHED.equals(c.getStatus())) {
                holder.btnAction.setText(getString(R.string.btn_action_results));
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.btn_results_blue)));
                holder.btnAction.setEnabled(true);
                holder.btnAction.setOnClickListener(v -> prikaziRezultateTurnira(c));
            } else if (Challenge.STATUS_IN_PROGRESS.equals(c.getStatus())) {
                holder.btnAction.setText(getString(R.string.btn_action_in_progress));
                holder.btnAction.setEnabled(false);
            } else {
                Challenge.ChallengePlayer myPlayer = c.getJoinedPlayers().get(currentUid);
                if (myPlayer != null) {
                    if (myPlayer.isFinished()) {
                        holder.btnAction.setText(getString(R.string.btn_action_in_progress));
                        holder.btnAction.setEnabled(false);
                    } else {
                        holder.btnAction.setText(getString(R.string.btn_action_play_again));
                        holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.btn_create_orange)));
                        holder.btnAction.setEnabled(true);
                        holder.btnAction.setOnClickListener(v -> pokreniPartiju(c.getChallengeId()));
                    }
                } else {
                    holder.btnAction.setText(getString(R.string.btn_action_accept));
                    holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.btn_accept_green)));
                    holder.btnAction.setEnabled(true);
                    holder.btnAction.setOnClickListener(v -> {
                        challengeManager.joinChallenge(c.getChallengeId(), currentUid, currentUsername, new ChallengeManager.ChallengeOperationListener() {
                            @Override
                            public void onSuccess(String challengeId) {
                                pokreniPartiju(challengeId);
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return challengeList.size();
        }

        class ChallengeViewHolder extends RecyclerView.ViewHolder {
            TextView tvCreator, tvWager, tvCount;
            Button btnAction;

            public ChallengeViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCreator = itemView.findViewById(R.id.tvCreatorName);
                tvWager = itemView.findViewById(R.id.tvWagerInfo);
                tvCount = itemView.findViewById(R.id.tvPlayersCount);
                btnAction = itemView.findViewById(R.id.btnJoinOrView);
            }
        }
    }

    private void prikaziRezultateTurnira(Challenge challenge) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.dialog_results_title));

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.dialog_results_wager_summary, challenge.getStarsWager(), challenge.getTokensWager()));
        sb.append(getString(R.string.dialog_results_heading));
        sb.append("----------------------------------------\n");

        List<Challenge.ChallengePlayer> sortirani = new ArrayList<>(challenge.getJoinedPlayers().values());
        sortirani.sort((o1, o2) -> Integer.compare(o2.getScore(), o1.getScore()));

        for (int i = 0; i < sortirani.size(); i++) {
            Challenge.ChallengePlayer p = sortirani.get(i);
            sb.append(getString(R.string.dialog_results_row, (i + 1), p.getName(), p.getScore()));
            if (i == 0) sb.append(getString(R.string.suffix_first_place));
            if (i == 1) sb.append(getString(R.string.suffix_second_place));
            sb.append("\n");
        }

        builder.setMessage(sb.toString());
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}