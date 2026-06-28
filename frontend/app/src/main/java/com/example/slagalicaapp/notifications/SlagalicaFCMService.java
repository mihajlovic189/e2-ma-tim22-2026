package com.example.slagalicaapp.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.slagalicaapp.SlagalicaApp;
import com.example.slagalicaapp.repositories.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class SlagalicaFCMService extends FirebaseMessagingService {

    private static final String TAG = "SlagalicaFCM";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String source = remoteMessage.getData().get("source");
        String title  = null;
        String body   = null;
        String type   = "other";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body  = remoteMessage.getNotification().getBody();
        }
        if (remoteMessage.getData().size() > 0) {
            if (title == null) title = remoteMessage.getData().get("title");
            if (body  == null) body  = remoteMessage.getData().get("body");
            String dataType = remoteMessage.getData().get("type");
            if (dataType != null) type = dataType;
        }
        if (title == null || body == null) return;

        if ("direct_fcm".equals(source)) {
            // Cloud function already wrote this to Firestore — do NOT save again.
            // For chat the RTDB listener (prikažiLokalnuChatNotifikaciju) shows the
            // system notification while the app process is alive; only show it here
            // when the app was killed (isChatListenerRunning == false).
            boolean rtdbHandlingChat = "chat".equals(type) && SlagalicaApp.isChatListenerRunning;
            if (!SlagalicaApp.isAppInForeground && !rtdbHandlingChat) {
                AppNotificationManager.show(getApplicationContext(), channelForType(type), title, body);
                Log.d(TAG, "System notification shown (killed-app path): type=" + type);
            }
        } else {
            // Any other source: save to Firestore and notify.
            new NotificationRepository().save(title, body, type);
            Log.d(TAG, "Notification saved to Firestore: type=" + type);
            if (!SlagalicaApp.isAppInForeground) {
                AppNotificationManager.show(getApplicationContext(), channelForType(type), title, body);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);
        saveFcmToken(token);
    }

    private void saveFcmToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .update("fcmToken", token)
                .addOnFailureListener(e ->
                        FirebaseFirestore.getInstance()
                                .collection("users").document(user.getUid())
                                .set(java.util.Collections.singletonMap("fcmToken", token),
                                        com.google.firebase.firestore.SetOptions.merge())
                );
    }

    private String channelForType(String type) {
        switch (type) {
            case "chat":    return SlagalicaApp.CHANNEL_CHAT;
            case "ranking": return SlagalicaApp.CHANNEL_RANKING;
            case "rewards": return SlagalicaApp.CHANNEL_REWARDS;
            default:        return SlagalicaApp.CHANNEL_OTHER;
        }
    }
}
