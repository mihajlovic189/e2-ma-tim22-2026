package com.example.slagalicaapp.repositories;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalicaapp.data.models.FriendData;
import com.example.slagalicaapp.data.models.GameInvite;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FriendsRepository {

    private final FirebaseFirestore db  = FirebaseFirestore.getInstance();
    private final DatabaseReference rtdb = FirebaseDatabase.getInstance().getReference();
    private final FirebaseAuth       auth = FirebaseAuth.getInstance();

    private ValueEventListener inviteListener;
    private final MutableLiveData<GameInvite> incomingInvite = new MutableLiveData<>();

    // ── Online presence ───────────────────────────────────────────────────────

    public void setOnline(boolean online) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        DatabaseReference ref = rtdb.child("status").child(user.getUid());
        ref.child("isOnline").setValue(online);
        if (online) {
            ref.child("isOnline").onDisconnect().setValue(false);
        }
    }

    public void setInGame(boolean inGame) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        rtdb.child("status").child(user.getUid()).child("inGame").setValue(inGame);
    }

    // ── Friends list ──────────────────────────────────────────────────────────

    public LiveData<List<FriendData>> getFriends() {
        MutableLiveData<List<FriendData>> result = new MutableLiveData<>();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { result.setValue(new ArrayList<>()); return result; }

        db.collection("users").document(user.getUid())
                .collection("friends").get()
                .addOnSuccessListener(snapshots -> {
                    List<String> uids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) uids.add(doc.getId());

                    if (uids.isEmpty()) { result.setValue(new ArrayList<>()); return; }

                    List<FriendData> list = new ArrayList<>();
                    AtomicInteger remaining = new AtomicInteger(uids.size());

                    for (String uid : uids) {
                        FriendData fd = new FriendData();
                        fd.setUid(uid);
                        fd.setAlreadyFriend(true);
                        list.add(fd);

                        // Fetch Firestore profile
                        db.collection("users").document(uid).get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) enrichFromFirestore(fd, doc);

                                    // Fetch RTDB status
                                    rtdb.child("status").child(uid)
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot snap) {
                                                    Boolean online = snap.child("isOnline").getValue(Boolean.class);
                                                    Boolean inGame = snap.child("inGame").getValue(Boolean.class);
                                                    fd.setOnline(Boolean.TRUE.equals(online));
                                                    fd.setInGame(Boolean.TRUE.equals(inGame));

                                                    if (remaining.decrementAndGet() == 0)
                                                        result.setValue(list);
                                                }
                                                @Override
                                                public void onCancelled(@NonNull DatabaseError e) {
                                                    if (remaining.decrementAndGet() == 0)
                                                        result.setValue(list);
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    if (remaining.decrementAndGet() == 0)
                                        result.setValue(list);
                                });
                    }
                })
                .addOnFailureListener(e -> result.setValue(new ArrayList<>()));

        return result;
    }

    private void enrichFromFirestore(FriendData fd, DocumentSnapshot doc) {
        fd.setUsername(doc.getString("username") != null ? doc.getString("username") : "");
        fd.setAvatarUri(doc.getString("avatarUri") != null ? doc.getString("avatarUri") : "");
        fd.setLeagueName(doc.getString("leagueName") != null ? doc.getString("leagueName") : "");
        Long ts = doc.getLong("totalStars");
        fd.setTotalStars(ts != null ? ts.intValue() : 0);

        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        String cycleMonth   = doc.getString("cycleMonth");
        if (currentMonth.equals(cycleMonth)) {
            Long ms = doc.getLong("monthlyStars");
            fd.setMonthlyStars(ms != null ? ms.intValue() : 0);
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public LiveData<List<FriendData>> searchByUsername(String query) {
        MutableLiveData<List<FriendData>> result = new MutableLiveData<>();
        FirebaseUser me = auth.getCurrentUser();
        if (me == null || query == null || query.trim().isEmpty()) {
            result.setValue(new ArrayList<>());
            return result;
        }

        final String queryLower = query.trim().toLowerCase(Locale.getDefault());

        // Load all users and filter client-side (case-insensitive substring match).
        // Firestore range queries are case-sensitive and break on mixed-case usernames.
        db.collection("users").limit(500).get()
                .addOnSuccessListener(snapshots -> {
                    db.collection("users").document(me.getUid())
                            .collection("friends").get()
                            .addOnSuccessListener(friendSnaps -> {
                                java.util.Set<String> friendUids = new java.util.HashSet<>();
                                for (QueryDocumentSnapshot f : friendSnaps) friendUids.add(f.getId());

                                List<FriendData> list = new ArrayList<>();
                                for (QueryDocumentSnapshot doc : snapshots) {
                                    if (doc.getId().equals(me.getUid())) continue;
                                    String username = doc.getString("username");
                                    if (username == null) continue;
                                    if (!username.toLowerCase(Locale.getDefault()).contains(queryLower)) continue;

                                    FriendData fd = new FriendData();
                                    fd.setUid(doc.getId());
                                    fd.setAlreadyFriend(friendUids.contains(doc.getId()));
                                    enrichFromFirestore(fd, doc);
                                    list.add(fd);
                                }
                                result.setValue(list);
                            })
                            .addOnFailureListener(e -> result.setValue(new ArrayList<>()));
                })
                .addOnFailureListener(e -> result.setValue(new ArrayList<>()));

        return result;
    }

    // ── Add / Remove friend ───────────────────────────────────────────────────

    public LiveData<Boolean> addFriend(FriendData friend) {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { result.setValue(false); return result; }

        Map<String, Object> data = new HashMap<>();
        data.put("username", friend.getUsername());
        data.put("avatarUri", friend.getAvatarUri() != null ? friend.getAvatarUri() : "");
        data.put("addedAt", System.currentTimeMillis());

        // Bidirectional friendship
        DocumentReference myRef = db.collection("users").document(me.getUid())
                .collection("friends").document(friend.getUid());
        DocumentReference theirRef = db.collection("users").document(friend.getUid())
                .collection("friends").document(me.getUid());

        // My entry
        myRef.set(data).addOnSuccessListener(u1 -> {
            // Their entry – requires my own profile data
            db.collection("users").document(me.getUid()).get()
                    .addOnSuccessListener(myDoc -> {
                        String myUsername = myDoc.getString("username") != null
                                ? myDoc.getString("username") : "Nepoznat igrač";
                        Map<String, Object> myData = new HashMap<>();
                        myData.put("username", myUsername);
                        myData.put("avatarUri", myDoc.getString("avatarUri") != null
                                ? myDoc.getString("avatarUri") : "");
                        myData.put("addedAt", System.currentTimeMillis());
                        theirRef.set(myData)
                                .addOnSuccessListener(u2 -> {
                                    // Notify the friend that they were added
                                    NotificationRepository.saveForUser(
                                            friend.getUid(),
                                            "Novi prijatelj",
                                            myUsername + " te je dodao kao prijatelja.",
                                            "other");
                                    result.setValue(true);
                                })
                                .addOnFailureListener(e -> result.setValue(false));
                    })
                    .addOnFailureListener(e -> result.setValue(false));
        }).addOnFailureListener(e -> result.setValue(false));

        return result;
    }

    public LiveData<Boolean> removeFriend(String friendUid) {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { result.setValue(false); return result; }

        db.collection("users").document(me.getUid())
                .collection("friends").document(friendUid)
                .delete()
                .addOnSuccessListener(u -> {
                    db.collection("users").document(friendUid)
                            .collection("friends").document(me.getUid())
                            .delete()
                            .addOnSuccessListener(u2 -> result.setValue(true))
                            .addOnFailureListener(e -> result.setValue(false));
                })
                .addOnFailureListener(e -> result.setValue(false));
        return result;
    }

    // ── Game invites ──────────────────────────────────────────────────────────

    /**
     * Sends a game invite. The caller must have already created the RTDB room
     * (via MatchmakingManager.createDirectRoom()) and obtained its roomId.
     */
    public LiveData<String> sendGameInvite(String toUid, String toName,
                                           String gameType, String roomId) {
        MutableLiveData<String> result = new MutableLiveData<>();
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { result.setValue(null); return result; }

        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String myName = doc.getString("username") != null
                            ? doc.getString("username") : me.getUid();

                    DatabaseReference inviteRef = rtdb.child("gameInvites").push();
                    Map<String, Object> inv = new HashMap<>();
                    inv.put("fromUid",    me.getUid());
                    inv.put("fromName",   myName);
                    inv.put("toUid",      toUid);
                    inv.put("gameType",   gameType);
                    inv.put("roomId",     roomId);
                    inv.put("status",     GameInvite.STATUS_PENDING);
                    inv.put("timestamp",  System.currentTimeMillis());

                    inviteRef.setValue(inv)
                            .addOnSuccessListener(u -> result.setValue(inviteRef.getKey()))
                            .addOnFailureListener(e -> result.setValue(null));
                })
                .addOnFailureListener(e -> result.setValue(null));

        return result;
    }

    public void updateInviteStatus(String inviteId, String status) {
        rtdb.child("gameInvites").child(inviteId).child("status").setValue(status);
    }

    /** Starts listening for invites addressed to the current user. */
    public LiveData<GameInvite> listenForIncomingInvites() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) return incomingInvite;

        DatabaseReference ref = rtdb.child("gameInvites");
        inviteListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String toUid  = child.child("toUid").getValue(String.class);
                    String status = child.child("status").getValue(String.class);
                    if (!me.getUid().equals(toUid)) continue;
                    if (!GameInvite.STATUS_PENDING.equals(status)) continue;

                    GameInvite invite = new GameInvite();
                    invite.setInviteId(child.getKey());
                    invite.setFromUid(child.child("fromUid").getValue(String.class));
                    invite.setFromName(child.child("fromName").getValue(String.class));
                    invite.setToUid(toUid);
                    invite.setGameType(child.child("gameType").getValue(String.class));
                    invite.setRoomId(child.child("roomId").getValue(String.class));
                    invite.setStatus(status);
                    Long ts = child.child("timestamp").getValue(Long.class);
                    invite.setTimestamp(ts != null ? ts : 0);

                    incomingInvite.setValue(invite);
                    break; // handle one at a time
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        ref.addValueEventListener(inviteListener);
        return incomingInvite;
    }

    /** Watches a single sent invite for status updates (sender side). */
    public LiveData<String> watchInviteStatus(String inviteId) {
        MutableLiveData<String> status = new MutableLiveData<>();
        rtdb.child("gameInvites").child(inviteId).child("status")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        status.setValue(snap.getValue(String.class));
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {}
                });
        return status;
    }

    public void stopListeningForInvites() {
        if (inviteListener != null)
            rtdb.child("gameInvites").removeEventListener(inviteListener);
    }

    public void cleanupInvite(String inviteId) {
        rtdb.child("gameInvites").child(inviteId).removeValue();
    }

    public void cleanupRoom(String gameType, String roomId) {
        if (gameType == null || roomId == null) return;
        rtdb.child("rooms").child(gameType).child(roomId).removeValue();
    }
}
