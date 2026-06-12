package com.example.slagalicaapp.notifications;

import com.example.slagalicaapp.SlagalicaApp;
import com.example.slagalicaapp.repositories.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class SlagalicaFCMService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String tip    = data.getOrDefault("tip", "other");
        String naslov = data.getOrDefault("naslov", "");
        String opis   = data.getOrDefault("opis", "");

        String channel = tipToChannel(tip);
        AppNotificationManager.show(this, channel, naslov, opis);

        NotificationRepository repo = new NotificationRepository();
        repo.sacuvaj(naslov, opis, tip);
    }

    @Override
    public void onNewToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update("fcmToken", token);
    }

    private String tipToChannel(String tip) {
        switch (tip) {
            case "chat":    return SlagalicaApp.CHANNEL_CHAT;
            case "ranking": return SlagalicaApp.CHANNEL_RANKING;
            case "rewards": return SlagalicaApp.CHANNEL_REWARDS;
            default:        return SlagalicaApp.CHANNEL_OTHER;
        }
    }
}
