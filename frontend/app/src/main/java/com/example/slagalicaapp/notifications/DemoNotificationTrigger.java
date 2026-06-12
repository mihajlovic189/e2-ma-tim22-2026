package com.example.slagalicaapp.notifications;

import android.content.Context;

import com.example.slagalicaapp.SlagalicaApp;
import com.example.slagalicaapp.repositories.NotificationRepository;

public class DemoNotificationTrigger {

    public static void sendDemoNotifications(Context ctx) {
        NotificationRepository repo = new NotificationRepository();

        send(ctx, repo,
                SlagalicaApp.CHANNEL_CHAT, "chat",
                "Nova poruka u četu",
                "Marko: Hej, igramo večeras?");

        send(ctx, repo,
                SlagalicaApp.CHANNEL_RANKING, "ranking",
                "Rang lista ažurirana",
                "Napredovao si na #5 mesto!");

        send(ctx, repo,
                SlagalicaApp.CHANNEL_REWARDS, "rewards",
                "Dobio si nagradu!",
                "Završio si u top 10 nedeljne rang liste. +3 tokena");

        send(ctx, repo,
                SlagalicaApp.CHANNEL_OTHER, "other",
                "Poziv za partiju",
                "Ana te poziva na prijateljsku partiju");
    }

    private static void send(Context ctx, NotificationRepository repo,
                             String channel, String tip, String naslov, String opis) {
        AppNotificationManager.show(ctx, channel, naslov, opis);
        repo.sacuvaj(naslov, opis, tip);
    }
}
