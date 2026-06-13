package com.example.slagalicaapp.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.slagalicaapp.SlagalicaApp;
import com.example.slagalicaapp.repositories.NotificationStore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class SlagalicaFCMService extends FirebaseMessagingService {

    private static final String TAG = "SlagalicaFCM";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = null;
        String body  = null;
        String type  = "other";

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

        if (title != null && body != null) {
            String channel = channelForType(type);
            AppNotificationManager.show(getApplicationContext(), channel, title, body);
            NotificationStore.save(getApplicationContext(), title, body, type);
            Log.d(TAG, "Notification saved locally: type=" + type);
        }
    }

    private String channelForType(String type) {
        switch (type) {
            case "chat":    return SlagalicaApp.CHANNEL_CHAT;
            case "ranking": return SlagalicaApp.CHANNEL_RANKING;
            case "rewards": return SlagalicaApp.CHANNEL_REWARDS;
            default:        return SlagalicaApp.CHANNEL_OTHER;
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);
    }
}
