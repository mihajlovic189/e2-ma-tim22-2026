package com.example.slagalicaapp.ui.activities;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.SlagalicaApp;
import com.example.slagalicaapp.data.firebase.MatchmakingManager;
import com.example.slagalicaapp.data.models.GameInvite;
import com.example.slagalicaapp.notifications.AppNotificationManager;
import com.example.slagalicaapp.repositories.LeaderboardRepository;
import com.example.slagalicaapp.repositories.NotificationRepository;
import com.example.slagalicaapp.ui.fragments.HomeFragment;
import com.example.slagalicaapp.ui.fragments.LoginFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int INVITE_NOTIF_ID = 9001;
    private com.google.firebase.database.ChildEventListener backgroundChatListener;
    private com.google.firebase.database.DatabaseReference backgroundChatRef;
    private long chatListenerStartTime = 0L;

    // Firestore listener for non-chat notifications (friend requests, rewards, etc.)
    private ListenerRegistration notifListener;
    private long notifListenerStartTime = 0L;

    // RTDB listener for cross-user notifications (friend requests via saveForUser)
    private com.google.firebase.database.ChildEventListener rtdbNotifListener;
    private com.google.firebase.database.DatabaseReference rtdbNotifRef;
    private long rtdbNotifStartTime = 0L;

    private static final Map<String, String> GAME_LABELS = new LinkedHashMap<>();
    static {
        GAME_LABELS.put("KORAK_PO_KORAK",  "Korak po korak");
        GAME_LABELS.put("MOJ_BROJ",        "Moj broj");
        GAME_LABELS.put("ASOCIJACIJE",     "Asocijacije");
        GAME_LABELS.put("SKOCKO",          "Skočko");
        GAME_LABELS.put("KO_ZNA_ZNA",      "Ko zna zna");
        GAME_LABELS.put("SPOJNICE",        "Spojnice");
    }

    private FirebaseAuth.AuthStateListener authStateListener;

    // Global invite receiver state
    private ChildEventListener  inviteChildListener;
    private ValueEventListener  inviteStatusListener;
    private DatabaseReference   inviteQueryRef;
    private DatabaseReference   currentInviteNodeRef;
    private AlertDialog         inviteReceivedDialog;
    private CountDownTimer      inviteCountDown;
    private String              pendingInviteId;
    private GameInvite          pendingInvite;   // stored when app is in background
    private boolean             isInForeground;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            boolean canAutoLogin = user != null && (user.isAnonymous() || user.isEmailVerified());

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, canAutoLogin ? new HomeFragment() : new LoginFragment())
                    .commit();

            if (canAutoLogin) {
                requestNotifPermissionAndSendDemos();
                // DODATO: Provera i dodela 5 dnevnih tokena pri automatskom ulasku
                proveriIDodeliDnevneTokene();
            }
        }
    }

    /**
     * DODATO: Implementacija dnevnog sistema tokena (Tačka 3.a pravila)
     * Proverava datum poslednjeg ulaska i dodeljuje 5 tokena ako je počeo novi dan.
     */
    private void proveriIDodeliDnevneTokene() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        String danasnjiDatum = sdf.format(new java.util.Date());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String poslednjaProvera = snapshot.child("lastTokenCheck").getValue(String.class);
                int trenutniTokeni = snapshot.child("tokens").getValue(Integer.class) != null ?
                        snapshot.child("tokens").getValue(Integer.class) : 0;

                // Ako korisnik otvara aplikaciju prvi put u danu (ili uopšte)
                if (poslednjaProvera == null || !poslednjaProvera.equals(danasnjiDatum)) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("tokens", trenutniTokeni + 5);
                    updates.put("lastTokenCheck", danasnjiDatum);

                    userRef.updateChildren(updates).addOnSuccessListener(aVoid ->
                            Toast.makeText(MainActivity.this, "Dobili ste 5 dnevnih tokena za današnji dan!", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
        com.example.slagalicaapp.SlagalicaApp.isAppInForeground = true;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(INVITE_NOTIF_ID);

        if (pendingInvite != null && pendingInviteId != null) {
            GameInvite invite = pendingInvite;
            pendingInvite = null;
            showInviteDialog(invite);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
        com.example.slagalicaapp.SlagalicaApp.isAppInForeground = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        authStateListener = auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                String uid = user.getUid();

                DatabaseReference ref = FirebaseDatabase.getInstance().getReference()
                        .child("status").child(uid);
                ref.child("isOnline").setValue(true);
                ref.child("isOnline").onDisconnect().setValue(false);

                startGlobalInviteListener(uid);

                // Save FCM token so cloud functions can send direct notifications
                com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                        .addOnSuccessListener(token -> {
                            if (token != null) {
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users").document(uid)
                                        .update("fcmToken", token);
                            }
                        });

                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            String region = "Global";
                            if (documentSnapshot != null && documentSnapshot.exists()) {
                                String r = documentSnapshot.getString("region");
                                if (r != null && !r.trim().isEmpty()) region = r;
                            }
                            // Subscribe to chat topic so FCM messages reach this device
                            // even if the user never opened the chat screen
                            String topicName = "chat_" + region.replace(" ", "_");
                            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                                    .subscribeToTopic(topicName);
                            startBackgroundChatNotificationListener(uid, region);
                        });

                startNotifListener(uid);

                proveriIDodeliDnevneTokene();
                new LeaderboardRepository().checkAndDistributeRewards(MainActivity.this, null);
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopGlobalInviteListener();
        dismissInviteDialog();
        if (backgroundChatListener != null && backgroundChatRef != null) {
            backgroundChatRef.removeEventListener(backgroundChatListener);
            backgroundChatListener = null;
        }
        SlagalicaApp.isChatListenerRunning = false;
        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }
        if (rtdbNotifListener != null && rtdbNotifRef != null) {
            rtdbNotifRef.removeEventListener(rtdbNotifListener);
            rtdbNotifListener = null;
        }
        SlagalicaApp.isNotifListenerRunning = false;
    }

    // ── Global invite listener ────────────────────────────────────────────────

    private void startGlobalInviteListener(String myUid) {
        if (inviteChildListener != null) return;

        inviteQueryRef = FirebaseDatabase.getInstance().getReference()
                .child("gameInvites");

        inviteChildListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snap, String prev) {
                handleInviteSnapshot(snap, myUid);
            }
            @Override public void onChildChanged(@NonNull DataSnapshot snap, String prev) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snap) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snap, String prev) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };

        inviteQueryRef.orderByChild("toUid").equalTo(myUid)
                .addChildEventListener(inviteChildListener);
    }

    private void stopGlobalInviteListener() {
        if (inviteChildListener != null && inviteQueryRef != null) {
            inviteQueryRef.removeEventListener(inviteChildListener);
            inviteChildListener = null;
        }
        if (inviteStatusListener != null && currentInviteNodeRef != null) {
            currentInviteNodeRef.removeEventListener(inviteStatusListener);
            inviteStatusListener = null;
        }
    }

    private void handleInviteSnapshot(DataSnapshot snap, String myUid) {
        String toUid  = snap.child("toUid").getValue(String.class);
        String status = snap.child("status").getValue(String.class);
        if (!myUid.equals(toUid)) return;
        if (!GameInvite.STATUS_PENDING.equals(status)) return;

        String inviteId = snap.getKey();
        if (inviteId == null || inviteId.equals(pendingInviteId)) return;

        GameInvite invite = new GameInvite();
        invite.setInviteId(inviteId);
        invite.setFromUid(snap.child("fromUid").getValue(String.class));
        invite.setFromName(snap.child("fromName").getValue(String.class));
        invite.setToUid(toUid);
        invite.setGameType(snap.child("gameType").getValue(String.class));
        invite.setRoomId(snap.child("roomId").getValue(String.class));
        invite.setStatus(status);
        Long ts = snap.child("timestamp").getValue(Long.class);
        invite.setTimestamp(ts != null ? ts : 0);

        pendingInviteId = inviteId;

        runOnUiThread(() -> {
            if (isInForeground) {
                showInviteDialog(invite);
            } else {
                postInviteNotification(invite);
                watchInviteStatusForReceiver(invite);
            }
        });
    }

    private void watchInviteStatusForReceiver(GameInvite invite) {
        if (inviteStatusListener != null && currentInviteNodeRef != null) {
            currentInviteNodeRef.removeEventListener(inviteStatusListener);
        }
        currentInviteNodeRef = FirebaseDatabase.getInstance().getReference()
                .child("gameInvites").child(invite.getInviteId()).child("status");

        inviteStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                String s = snap.getValue(String.class);
                if (GameInvite.STATUS_CANCELLED.equals(s) || s == null) {
                    if (pendingInviteId == null) return;
                    pendingInviteId = null;
                    pendingInvite   = null;
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                            .cancel(INVITE_NOTIF_ID);
                    runOnUiThread(() -> dismissInviteDialog());
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        currentInviteNodeRef.addValueEventListener(inviteStatusListener);
    }

    // ── Invite dialog (foreground) ────────────────────────────────────────────

    private void showInviteDialog(GameInvite invite) {
        dismissInviteDialog();

        String label = GAME_LABELS.getOrDefault(invite.getGameType(), invite.getGameType());

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Poziv na igru od " + invite.getFromName())
                .setMessage("Igra: " + label + "\n\nPrihvatiš li poziv? (10)")
                .setCancelable(false)
                .setPositiveButton("Prihvati", (d, w) -> acceptInvite(invite))
                .setNegativeButton("Odbij",    (d, w) -> declineInvite(invite));

        inviteReceivedDialog = b.show();

        inviteCountDown = new CountDownTimer(10_000, 1_000) {
            @Override
            public void onTick(long remaining) {
                long sec = remaining / 1000;
                if (inviteReceivedDialog != null && inviteReceivedDialog.isShowing()) {
                    inviteReceivedDialog.setMessage(
                            "Igra: " + label + "\n\nPrihvatiš li poziv? (" + sec + ")");
                }
            }
            @Override
            public void onFinish() {
                declineInvite(invite);
            }
        }.start();

        watchInviteStatusForReceiver(invite);
    }

    private void dismissInviteDialog() {
        if (inviteCountDown != null) {
            inviteCountDown.cancel();
            inviteCountDown = null;
        }
        if (inviteReceivedDialog != null && inviteReceivedDialog.isShowing()) {
            inviteReceivedDialog.dismiss();
        }
        inviteReceivedDialog = null;
    }

    private void acceptInvite(GameInvite invite) {
        if (inviteCountDown != null) inviteCountDown.cancel();
        FirebaseDatabase.getInstance().getReference()
                .child("gameInvites").child(invite.getInviteId())
                .child("status").setValue(GameInvite.STATUS_ACCEPTED);
        pendingInviteId = null;

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        String myName = me != null && me.getDisplayName() != null ? me.getDisplayName() : "";
        String myUid  = me != null ? me.getUid() : "";

        MatchmakingManager mm = new MatchmakingManager(invite.getGameType(), myName, myUid,
                new MatchmakingManager.MatchmakingListener() {
                    @Override public void onMatchFound(String roomId, int playerNumber) {
                        runOnUiThread(() -> launchGame(roomId, invite.getGameType(), 2, myName));
                    }
                    @Override public void onWaiting() {}
                    @Override public void onError(String msg) {}
                });
        mm.joinDirectRoom(invite.getRoomId());
    }

    /**
     * PROMENJENO: Ispravljena putanja brisanja sobe.
     * Soba se sada ispravno briše sa lokacije "rooms/SLAGALICA_MECH/{roomId}" u skladu sa novom arhitekturom.
     */
    private void declineInvite(GameInvite invite) {
        dismissInviteDialog();
        DatabaseReference rtdb = FirebaseDatabase.getInstance().getReference();
        rtdb.child("gameInvites").child(invite.getInviteId())
                .child("status").setValue(GameInvite.STATUS_DECLINED);
        rtdb.child("gameInvites").child(invite.getInviteId()).removeValue();

        // KORREKCIJA: Umesto invite.getGameType() sada brišemo fiksnu krovnu strukturu meča
        rtdb.child("rooms").child(GameActivity.GAME_MECH).child(invite.getRoomId()).removeValue();
        pendingInviteId = null;
    }

    private void launchGame(String roomId, String gameType, int playerNum, String playerName) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_ROOM_ID, roomId);
        intent.putExtra(GameActivity.EXTRA_PLAYER_NUM, playerNum);
        intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, playerName);
        startActivity(intent);
    }

    private void postInviteNotification(GameInvite invite) {
        pendingInvite = invite;
        String label = GAME_LABELS.getOrDefault(invite.getGameType(), invite.getGameType());

        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent, flags);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, SlagalicaApp.CHANNEL_CHAT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Poziv na igru")
                .setContentText(invite.getFromName() + " te poziva na " + label)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(INVITE_NOTIF_ID, nb.build());

        // Save to in-app notification history for the recipient
        String inviteBody = invite.getFromName() + " te poziva na " + label;
        new NotificationRepository().save("Poziv na igru", inviteBody, "other");

        watchInviteStatusForReceiver(invite);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requestNotifPermissionAndSendDemos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void startBackgroundChatNotificationListener(String myUid, String region) {
        if (backgroundChatListener != null) return;

        chatListenerStartTime = System.currentTimeMillis();
        backgroundChatRef = FirebaseDatabase.getInstance().getReference()
                .child("regional_chats").child(region);

        backgroundChatListener = new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                if (timestamp == null || timestamp < chatListenerStartTime) return;

                String senderId   = snapshot.child("senderId").getValue(String.class);
                String senderName = snapshot.child("senderName").getValue(String.class);
                String text       = snapshot.child("text").getValue(String.class);
                String msgId      = snapshot.getKey();

                if (senderId != null && !senderId.equals(myUid) && !SlagalicaApp.isUserInChatScreen) {
                    prikažiLokalnuChatNotifikaciju(senderName, text, msgId);
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        SlagalicaApp.isChatListenerRunning = true;
        backgroundChatRef.limitToLast(50).addChildEventListener(backgroundChatListener);
    }

    /** Watches for new notification docs and shows system notifications.
     *  Works without cloud functions (pure client-side).
     *  Listens on both Firestore (self-generated) and RTDB (cross-user via saveForUser). */
    private void startNotifListener(String uid) {
        if (notifListener != null) return;
        notifListenerStartTime = System.currentTimeMillis();
        SlagalicaApp.isNotifListenerRunning = true;

        // Firestore listener — catches rewards, self-generated notifications
        notifListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        if (change.getType() != DocumentChange.Type.ADDED) continue;
                        com.google.firebase.firestore.DocumentSnapshot doc = change.getDocument();

                        Long ts = doc.getLong("timestamp");
                        if (ts == null || ts < notifListenerStartTime) continue;

                        String type  = doc.getString("type");
                        if ("chat".equals(type)) continue; // handled by prikažiLokalnuChatNotifikaciju

                        String title = doc.getString("title");
                        String body  = doc.getString("body");
                        if (title == null || body == null) continue;

                        AppNotificationManager.show(getApplicationContext(),
                                channelForType(type), title, body);
                    }
                });

        // RTDB listener — catches cross-user notifications (friend requests)
        rtdbNotifStartTime = System.currentTimeMillis();
        rtdbNotifRef = FirebaseDatabase.getInstance().getReference()
                .child("notifications").child(uid);
        rtdbNotifListener = new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull com.google.firebase.database.DataSnapshot snapshot,
                                     @Nullable String previousChildName) {
                Long ts = snapshot.child("timestamp").getValue(Long.class);
                if (ts == null || ts < rtdbNotifStartTime) return;

                String type = snapshot.child("type").getValue(String.class);
                String title = snapshot.child("title").getValue(String.class);
                String body = snapshot.child("body").getValue(String.class);
                if (title == null || body == null) return;

                AppNotificationManager.show(getApplicationContext(),
                        channelForType(type), title, body);
            }
            @Override public void onChildChanged(@NonNull com.google.firebase.database.DataSnapshot snapshot,
                                                  @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull com.google.firebase.database.DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull com.google.firebase.database.DataSnapshot snapshot,
                                                @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        };
        rtdbNotifRef.addChildEventListener(rtdbNotifListener);
    }

    private String channelForType(String type) {
        if (type == null) return SlagalicaApp.CHANNEL_OTHER;
        switch (type) {
            case "chat":    return SlagalicaApp.CHANNEL_CHAT;
            case "ranking": return SlagalicaApp.CHANNEL_RANKING;
            case "rewards": return SlagalicaApp.CHANNEL_REWARDS;
            default:        return SlagalicaApp.CHANNEL_OTHER;
        }
    }

    private void prikažiLokalnuChatNotifikaciju(String senderName, String text, String msgId) {
        if (senderName == null || text == null || msgId == null) return;

        String title = "Nova poruka od: " + senderName;

        // Show system notification
        AppNotificationManager.show(getApplicationContext(),
                SlagalicaApp.CHANNEL_CHAT, title, text);

        // Save to Firestore using msgId as document ID so the cloud function write
        // (if deployed) lands on the same document rather than creating a duplicate.
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) return;
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("title", title);
        data.put("body", text);
        data.put("type", "chat");
        data.put("timestamp", System.currentTimeMillis());
        data.put("read", false);
        FirebaseFirestore.getInstance()
                .collection("users").document(me.getUid())
                .collection("notifications").document(msgId)
                .set(data, SetOptions.merge());
    }

}