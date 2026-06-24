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
import com.example.slagalicaapp.notifications.DemoNotificationTrigger;
import com.example.slagalicaapp.ui.fragments.HomeFragment;
import com.example.slagalicaapp.ui.fragments.LoginFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) sendDemos();
            });

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

                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot != null && documentSnapshot.exists()) {
                                String region = documentSnapshot.getString("region");
                                if (region != null && !region.trim().isEmpty()) {

                                    startBackgroundChatNotificationListener(uid, region);
                                }
                            }
                        });

                proveriIDodeliDnevneTokene();
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
        if (backgroundChatListener != null && backgroundChatRef != null) {
            backgroundChatRef.removeEventListener(backgroundChatListener);
            backgroundChatListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopGlobalInviteListener();
        dismissInviteDialog();
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

    // ── Notification (background) ─────────────────────────────────────────────

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

        watchInviteStatusForReceiver(invite);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requestNotifPermissionAndSendDemos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                sendDemos();
            } else {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            sendDemos();
        }
    }

    private void startBackgroundChatNotificationListener(String myUid, String region) {
        if (backgroundChatListener != null) return;

        backgroundChatRef = FirebaseDatabase.getInstance().getReference()
                .child("regional_chats").child(region);

        backgroundChatListener = new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                if (timestamp == null || (System.currentTimeMillis() - timestamp) > 10_000) {
                    return;
                }

                String senderId = snapshot.child("senderId").getValue(String.class);
                String senderName = snapshot.child("senderName").getValue(String.class);
                String text = snapshot.child("text").getValue(String.class);

                if (senderId != null && !senderId.equals(myUid) && !SlagalicaApp.isUserInChatScreen) {
                    prikažiLokalnuChatNotifikaciju(senderName, text);
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        backgroundChatRef.limitToLast(1).addChildEventListener(backgroundChatListener);
    }

    private void prikažiLokalnuChatNotifikaciju(String naslov, String poruka) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = "regional_chat_sim";
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Regionalni Čet", NotificationManager.IMPORTANCE_DEFAULT);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Nova poruka od: " + naslov)
                .setContentText(poruka)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void sendDemos() {
        DemoNotificationTrigger.sendDemoNotifications(this);
    }
}