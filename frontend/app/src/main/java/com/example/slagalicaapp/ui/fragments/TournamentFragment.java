package com.example.slagalicaapp.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.repositories.TournamentRepository;
import com.example.slagalicaapp.ui.activities.GameActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

public class TournamentFragment extends Fragment {

    private TournamentRepository repo;

    // State preserved across GameActivity launches (fragment is stopped, not destroyed)
    private String tournamentId;
    private String mySlot;          // "p1"–"p4"
    private boolean semiLaunched   = false;
    private boolean finalLaunched  = false;
    private boolean lostSemi       = false;
    private boolean done           = false;
    private boolean cancelPending  = false;

    // Views
    private LinearLayout layoutWaiting, layoutBracket, layoutResult;
    private TextView tvWaitingCount, tvBracketStatus, tvResultTitle, tvResultBody;
    private TextView tvNameP1, tvNameP2, tvNameP3, tvNameP4, tvNameF1, tvNameF2;
    private LinearLayout layoutFinalRow;
    private Button btnCancelJoin, btnResultOk;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tournament, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutWaiting  = view.findViewById(R.id.layoutWaiting);
        layoutBracket  = view.findViewById(R.id.layoutBracket);
        layoutResult   = view.findViewById(R.id.layoutResult);
        tvWaitingCount = view.findViewById(R.id.tvWaitingCount);
        tvBracketStatus = view.findViewById(R.id.tvBracketStatus);
        tvResultTitle  = view.findViewById(R.id.tvResultTitle);
        tvResultBody   = view.findViewById(R.id.tvResultBody);
        tvNameP1 = view.findViewById(R.id.tvNameP1);
        tvNameP2 = view.findViewById(R.id.tvNameP2);
        tvNameP3 = view.findViewById(R.id.tvNameP3);
        tvNameP4 = view.findViewById(R.id.tvNameP4);
        tvNameF1 = view.findViewById(R.id.tvNameF1);
        tvNameF2 = view.findViewById(R.id.tvNameF2);
        layoutFinalRow = view.findViewById(R.id.layoutFinalRow);
        btnCancelJoin  = view.findViewById(R.id.btnCancelJoin);
        btnResultOk    = view.findViewById(R.id.btnResultOk);

        btnCancelJoin.setOnClickListener(v -> cancelAndReturn());
        btnResultOk.setOnClickListener(v -> navigateBack());

        repo = new TournamentRepository();

        if (savedInstanceState != null) {
            tournamentId  = savedInstanceState.getString("tournamentId");
            mySlot        = savedInstanceState.getString("mySlot");
            semiLaunched  = savedInstanceState.getBoolean("semiLaunched", false);
            finalLaunched = savedInstanceState.getBoolean("finalLaunched", false);
            lostSemi      = savedInstanceState.getBoolean("lostSemi", false);
            done          = savedInstanceState.getBoolean("done", false);
        }

        if (done) {
            // Nothing to do, result screen already shown via restoreResultFromBundle
        } else if (lostSemi) {
            showLostSemiScreen();
        } else if (tournamentId != null && mySlot != null) {
            showBracketLayout();
            repo.listenToTournament(tournamentId, makeTournamentListener());
        } else {
            startJoining();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (tournamentId != null) outState.putString("tournamentId", tournamentId);
        if (mySlot != null)       outState.putString("mySlot", mySlot);
        outState.putBoolean("semiLaunched",  semiLaunched);
        outState.putBoolean("finalLaunched", finalLaunched);
        outState.putBoolean("lostSemi",      lostSemi);
        outState.putBoolean("done",          done);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (!done && !lostSemi) {
            repo.stopTournamentListener();
        }
    }

    // ── Joining ──────────────────────────────────────────────────────────────

    private void startJoining() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Niste prijavljeni.", Toast.LENGTH_SHORT).show();
            navigateBack();
            return;
        }

        showWaitingLayout("Uplata ulaznice (3 tokena)...", false);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    String username = doc.getString("username");
                    String avatarUri = doc.getString("avatarUri");
                    String leagueIcon = doc.getString("leagueName");
                    if (username == null || username.isEmpty()) username = "Igrač";

                    final String name = username;

                    repo.joinTournament(user.getUid(), name, avatarUri,
                            leagueIcon != null ? leagueIcon : "",
                            new TournamentRepository.JoinCallback() {
                                @Override
                                public void onWaiting(int count) {
                                    runOnMain(() -> showWaitingLayout(
                                            "Igrači: " + count + " / 4", true));
                                }

                                @Override
                                public void onStarted(String tid, String slot) {
                                    tournamentId = tid;
                                    mySlot = slot;
                                    runOnMain(() -> {
                                        showBracketLayout();
                                        repo.listenToTournament(tid, makeTournamentListener());
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    runOnMain(() -> {
                                        Toast.makeText(getContext(), message,
                                                Toast.LENGTH_LONG).show();
                                        navigateBack();
                                    });
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Greška pri učitavanju profila.",
                            Toast.LENGTH_SHORT).show();
                    navigateBack();
                });
    }

    // ── Tournament listener ───────────────────────────────────────────────────

    private void onTournamentUpdate(DataSnapshot snap) {
        if (!isAdded() || getActivity() == null || snap == null) return;
        runOnMain(() -> processSnapshot(snap));
    }

    private void processSnapshot(DataSnapshot snap) {
        if (!snap.exists() || done || lostSemi) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String myUid = user.getUid();

        String status           = snap.child("status").getValue(String.class);
        String semi1WinnerUid   = snap.child("semi1WinnerUid").getValue(String.class);
        String semi2WinnerUid   = snap.child("semi2WinnerUid").getValue(String.class);
        String finalWinnerUid   = snap.child("finalWinnerUid").getValue(String.class);
        String semi1RoomId      = snap.child("semi1RoomId").getValue(String.class);
        String semi2RoomId      = snap.child("semi2RoomId").getValue(String.class);
        String finalRoomId      = snap.child("finalRoomId").getValue(String.class);

        updatePlayerNames(snap);

        if ("done".equals(status)) {
            showResultScreen(finalWinnerUid, myUid, snap);
            return;
        }

        boolean inSemi1       = "p1".equals(mySlot) || "p2".equals(mySlot);
        String myRound        = inSemi1 ? "semi1" : "semi2";
        String myRoomId       = inSemi1 ? semi1RoomId : semi2RoomId;
        String mySemiWinner   = inSemi1 ? semi1WinnerUid : semi2WinnerUid;

        if ("semi".equals(status)) {
            if (!semiLaunched && myRoomId != null) {
                semiLaunched = true;
                tvBracketStatus.setText("Polufinale se sprema...");
                String roomToLaunch = myRoomId;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isAdded()) return;
                    launchGameActivity(roomToLaunch, myRound);
                }, 2500);
                return;
            }

            // Returned from GameActivity (or reconnected)
            if (mySemiWinner == null) {
                tvBracketStatus.setText("Polufinale u toku...");
            } else if (mySemiWinner.equals(myUid)) {
                tvBracketStatus.setText("Prošli ste dalje! Čekamo drugi meč...");
            } else {
                lostSemi = true;
                showLostSemiScreen();
            }
            return;
        }

        if ("final".equals(status)) {
            boolean imInFinal = myUid.equals(semi1WinnerUid) || myUid.equals(semi2WinnerUid);

            if (semi1WinnerUid != null && semi2WinnerUid != null) {
                tvNameF1.setText(getPlayerNameByUid(snap, semi1WinnerUid));
                tvNameF2.setText(getPlayerNameByUid(snap, semi2WinnerUid));
                layoutFinalRow.setVisibility(View.VISIBLE);
            }

            if (!imInFinal) {
                lostSemi = true;
                showLostSemiScreen();
                return;
            }

            if (!finalLaunched && finalRoomId != null) {
                finalLaunched = true;
                tvBracketStatus.setText("FINALE! Pripremite se...");
                String roomToLaunch = finalRoomId;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isAdded()) return;
                    launchGameActivity(roomToLaunch, "final");
                }, 2500);
            }
            // If finalLaunched we returned from the final and are waiting for status="done"
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void updatePlayerNames(DataSnapshot snap) {
        tvNameP1.setText(getPlayerNameBySlot(snap, "p1"));
        tvNameP2.setText(getPlayerNameBySlot(snap, "p2"));
        tvNameP3.setText(getPlayerNameBySlot(snap, "p3"));
        tvNameP4.setText(getPlayerNameBySlot(snap, "p4"));
    }

    private String getPlayerNameBySlot(DataSnapshot snap, String slot) {
        String name = snap.child(slot).child("username").getValue(String.class);
        return name != null && !name.isEmpty() ? name : "?";
    }

    private String getPlayerNameByUid(DataSnapshot snap, String uid) {
        for (String slot : new String[]{"p1", "p2", "p3", "p4"}) {
            String pUid = snap.child(slot).child("uid").getValue(String.class);
            if (uid != null && uid.equals(pUid)) {
                String name = snap.child(slot).child("username").getValue(String.class);
                return name != null ? name : "?";
            }
        }
        return "?";
    }

    private void showWaitingLayout(String countText, boolean showCancel) {
        layoutWaiting.setVisibility(View.VISIBLE);
        layoutBracket.setVisibility(View.GONE);
        layoutResult.setVisibility(View.GONE);
        tvWaitingCount.setText(countText);
        btnCancelJoin.setVisibility(showCancel ? View.VISIBLE : View.GONE);
    }

    private void showBracketLayout() {
        layoutWaiting.setVisibility(View.GONE);
        layoutBracket.setVisibility(View.VISIBLE);
        layoutResult.setVisibility(View.GONE);
    }

    private void showResultScreen(String finalWinnerUid, String myUid, DataSnapshot snap) {
        done = true;
        repo.stopTournamentListener();
        layoutBracket.setVisibility(View.GONE);
        layoutWaiting.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);

        if (myUid.equals(finalWinnerUid)) {
            tvResultTitle.setText("Pobedili ste turnir!");
            tvResultBody.setText("+20 zvezda i +3 tokena za pobedu u finalu!");
        } else {
            String winnerName = snap != null ? getPlayerNameByUid(snap, finalWinnerUid) : "?";
            tvResultTitle.setText("Turnir završen");
            tvResultBody.setText("Pobednik: " + winnerName);
        }
    }

    private void showLostSemiScreen() {
        repo.stopTournamentListener();
        layoutBracket.setVisibility(View.GONE);
        layoutWaiting.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        tvResultTitle.setText("Ispali ste u polufinaleu");
        tvResultBody.setText("Nažalost, niste prošli dalje. Srećno sledeći put!");
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void launchGameActivity(String roomId, String round) {
        Intent intent = new Intent(getActivity(), GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_ROOM_ID, roomId);
        intent.putExtra(GameActivity.EXTRA_PLAYER_NUM, 1); // overridden by resolvePlayerNumberFromRoom
        intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, "");
        intent.putExtra(GameActivity.EXTRA_TOURNAMENT_ID, tournamentId);
        intent.putExtra(GameActivity.EXTRA_TOURNAMENT_ROUND, round);
        startActivity(intent);
    }

    private void cancelAndReturn() {
        if (cancelPending) return;
        cancelPending = true;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && tournamentId == null) {
            repo.cancelJoin(user.getUid());
        }
        repo.stopTournamentListener();
        navigateBack();
    }

    private void navigateBack() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        }
    }

    private void runOnMain(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }

    private TournamentRepository.TournamentListener makeTournamentListener() {
        return new TournamentRepository.TournamentListener() {
            @Override
            public void onUpdate(DataSnapshot snapshot) {
                onTournamentUpdate(snapshot);
            }
            @Override
            public void onError(String message) {
                runOnMain(() -> Toast.makeText(getContext(), "Greška: " + message,
                        Toast.LENGTH_SHORT).show());
            }
        };
    }
}
