package com.example.slagalicaapp.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.slagalicaapp.data.models.Challenge;
import com.google.firebase.database.*;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChallengeManager {

    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("challenges");
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface ChallengeOperationListener {
        void onSuccess(String challengeId);
        void onError(String error);
    }

    /**
     * b. Kreiranje izazova uz proveru i skidanje uloga iz Firestore-a
     */
    public void createChallenge(String uid, String username, String region, int stars, int tokens, ChallengeOperationListener listener) {
        if (stars < 1 || tokens < 0) {
            listener.onError("Ulog mora biti najmanje 1 zvezda!");
            return;
        }
        if (stars > 10 || tokens > 2) {
            listener.onError("Maksimalan ulog je 10 zvezda i 2 tokena!");
            return;
        }

        DocumentReference userDoc = firestore.collection("users").document(uid);

        firestore.runTransaction(transaction -> {
            Long currentStars = transaction.get(userDoc).getLong("totalStars");
            Long currentTokens = transaction.get(userDoc).getLong("tokenCount");

            if (currentStars == null || currentStars < stars) {
                throw new RuntimeException("Nemate dovoljno zvezda!");
            }
            if (tokens > 0 && (currentTokens == null || currentTokens < tokens)) {
                throw new RuntimeException("Nemate dovoljno tokena!");
            }

            transaction.update(userDoc, "totalStars", currentStars - stars);
            if (tokens > 0) {
                transaction.update(userDoc, "tokenCount", (currentTokens != null ? currentTokens : 0) - tokens);
            }
            return null;
        }).addOnSuccessListener(aVoid -> {
            String challengeId = dbRef.push().getKey();
            Challenge challenge = new Challenge();
            challenge.setChallengeId(challengeId);
            challenge.setCreatorId(uid);
            challenge.setCreatorName(username);
            challenge.setRegion(region);
            challenge.setStarsWager(stars);
            challenge.setTokensWager(tokens);
            challenge.setStatus(Challenge.STATUS_WAITING);
            challenge.getJoinedPlayers().put(uid, new Challenge.ChallengePlayer(username));

            if (challengeId != null) {
                dbRef.child(challengeId).setValue(challenge)
                        .addOnSuccessListener(unused -> listener.onSuccess(challengeId))
                        .addOnFailureListener(e -> {
                            // Refund on RTDB write failure
                            isplatiKorisnika(uid, stars, tokens);
                            listener.onError(e.getMessage());
                        });
            }
        }).addOnFailureListener(e -> listener.onError(e.getMessage()));
    }

    /**
     * a, c. Prihvatanje izazova (maksimalno 4 igrača ukupno).
     * Ispravno: Firestore dedukcija PRIJE RTDB transakcije (ne unutar nje).
     */
    public void joinChallenge(String challengeId, String uid, String username, ChallengeOperationListener listener) {
        DatabaseReference challengeNode = dbRef.child(challengeId);

        // Step 1: Read challenge to validate conditions before touching money
        challengeNode.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Challenge challenge = snapshot.getValue(Challenge.class);
                if (challenge == null) {
                    listener.onError("Izazov nije pronađen.");
                    return;
                }
                if (!Challenge.STATUS_WAITING.equals(challenge.getStatus())) {
                    listener.onError("Ovaj izazov više nije dostupan.");
                    return;
                }
                if (challenge.getJoinedPlayers().size() >= 4) {
                    listener.onError("Izazov je popunjen.");
                    return;
                }
                if (challenge.getJoinedPlayers().containsKey(uid)) {
                    listener.onError("Već ste u ovom izazovu.");
                    return;
                }

                int starsWager = challenge.getStarsWager();
                int tokensWager = challenge.getTokensWager();

                // Step 2: Deduct from Firestore
                proveriISkiniSredstvaIgraču(uid, starsWager, tokensWager, new ChallengeOperationListener() {
                    @Override
                    public void onSuccess(String id) {
                        // Step 3: Add player to challenge in RTDB
                        challengeNode.runTransaction(new Transaction.Handler() {
                            @NonNull
                            @Override
                            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                Challenge c = currentData.getValue(Challenge.class);
                                if (c == null) return Transaction.success(currentData);
                                if (!Challenge.STATUS_WAITING.equals(c.getStatus())) return Transaction.abort();
                                if (c.getJoinedPlayers().size() >= 4) return Transaction.abort();
                                if (c.getJoinedPlayers().containsKey(uid)) return Transaction.abort();

                                c.getJoinedPlayers().put(uid, new Challenge.ChallengePlayer(username));
                                if (c.getJoinedPlayers().size() == 4) {
                                    c.setStatus(Challenge.STATUS_IN_PROGRESS);
                                }
                                currentData.setValue(c);
                                return Transaction.success(currentData);
                            }

                            @Override
                            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                                if (committed) {
                                    listener.onSuccess(challengeId);
                                } else {
                                    // RTDB add failed — refund the player
                                    isplatiKorisnika(uid, starsWager, tokensWager);
                                    listener.onError(error != null ? error.getMessage() : "Nije moguće pristupiti izazovu.");
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        listener.onError(error);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        });
    }

    private void proveriISkiniSredstvaIgraču(String uid, int stars, int tokens, ChallengeOperationListener listener) {
        DocumentReference userDoc = firestore.collection("users").document(uid);
        firestore.runTransaction(transaction -> {
            Long s = transaction.get(userDoc).getLong("totalStars");
            Long t = transaction.get(userDoc).getLong("tokenCount");
            if (s == null || s < stars) {
                throw new RuntimeException("Nemate dovoljno zvezda!");
            }
            if (tokens > 0 && (t == null || t < tokens)) {
                throw new RuntimeException("Nemate dovoljno tokena!");
            }
            transaction.update(userDoc, "totalStars", s - stars);
            if (tokens > 0) {
                transaction.update(userDoc, "tokenCount", (t != null ? t : 0) - tokens);
            }
            return true;
        }).addOnSuccessListener(unused -> listener.onSuccess(uid))
          .addOnFailureListener(e -> listener.onError(e.getMessage()));
    }

    /**
     * d, e. Slanje rezultata i automatska raspodela nagrada kada svi završe.
     * Isplata se poziva iz onComplete (ne unutar doTransaction) da bi se izbegla višestruka isplata.
     */
    public void submitFinalScore(String challengeId, String uid, int finalScore) {
        DatabaseReference challengeNode = dbRef.child(challengeId);

        final boolean[] shouldPayout = {false};
        final Challenge[] challengeToPayOut = {null};

        challengeNode.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Challenge challenge = currentData.getValue(Challenge.class);
                if (challenge == null) return Transaction.success(currentData);

                // Reset on each retry
                shouldPayout[0] = false;
                challengeToPayOut[0] = null;

                Challenge.ChallengePlayer player = challenge.getJoinedPlayers().get(uid);
                if (player != null && !player.isFinished()) {
                    player.setScore(finalScore);
                    player.setFinished(true);
                }

                boolean allFinished = true;
                for (Challenge.ChallengePlayer p : challenge.getJoinedPlayers().values()) {
                    if (!p.isFinished()) {
                        allFinished = false;
                        break;
                    }
                }

                if (allFinished && !Challenge.STATUS_FINISHED.equals(challenge.getStatus())) {
                    challenge.setStatus(Challenge.STATUS_FINISHED);
                    shouldPayout[0] = true;
                    challengeToPayOut[0] = challenge;
                }

                currentData.setValue(challenge);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError e, boolean committed, @Nullable DataSnapshot d) {
                if (committed && shouldPayout[0] && challengeToPayOut[0] != null) {
                    obradiIsplatuNagrada(challengeToPayOut[0]);
                }
            }
        });
    }

    private void obradiIsplatuNagrada(Challenge challenge) {
        int ukupanBrojIgrača = challenge.getJoinedPlayers().size();
        int ukupnoZvezdaPool = challenge.getStarsWager() * ukupanBrojIgrača;
        int ukupnoTokensPool = challenge.getTokensWager() * ukupanBrojIgrača;

        int nagradaZvezdePrvi = (int) (ukupnoZvezdaPool * 0.75);
        int nagradaTokeniPrvi = (int) (ukupnoTokensPool * 0.75);

        List<Map.Entry<String, Challenge.ChallengePlayer>> rangLista =
                new ArrayList<>(challenge.getJoinedPlayers().entrySet());

        rangLista.sort((o1, o2) -> Integer.compare(o2.getValue().getScore(), o1.getValue().getScore()));

        if (rangLista.size() > 0) {
            String pobednikUid = rangLista.get(0).getKey();
            isplatiKorisnika(pobednikUid, nagradaZvezdePrvi, nagradaTokeniPrvi);
        }

        if (rangLista.size() > 1) {
            String drugiUid = rangLista.get(1).getKey();
            isplatiKorisnika(drugiUid, challenge.getStarsWager(), challenge.getTokensWager());
        }
    }

    private void isplatiKorisnika(String uid, int stars, int tokens) {
        if (stars > 0) {
            firestore.collection("users").document(uid)
                    .update("totalStars", FieldValue.increment(stars));
        }
        if (tokens > 0) {
            firestore.collection("users").document(uid)
                    .update("tokenCount", FieldValue.increment(tokens));
        }
    }
}
