package com.example.slagalicaapp.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.slagalicaapp.SlagalicaApp;
import com.example.slagalicaapp.repositories.NotificationStore;

public class DemoNotificationTrigger {

    private static final String PREFS = "SlagalicaPrefs";

    /** Poziva se pri prvom pokretanju aplikacije — dodaje demo notifikacije jednom. */
    public static void sendDemoNotifications(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean("demo_seeded_v2", false)) return;

        // --- Čet ---
        NotificationStore.save(ctx, "Nova poruka u četu",
                "Marko: Hej, igramo večeras?", "chat");
        NotificationStore.save(ctx, "Nova poruka u četu",
                "Ana: Odlična partija, hvala ti!", "chat");
        NotificationStore.save(ctx, "Nova poruka u četu",
                "Nikola: Kada si slobodan za meč?", "chat");

        // --- Rang lista ---
        NotificationStore.save(ctx, "Napredak na rang listi",
                "Napredovao si na #5 mesto nedeljne rang liste!", "ranking");
        NotificationStore.save(ctx, "Novi lični rekord!",
                "Postavio si lični rekord u Skočku – 450 poena.", "ranking");
        NotificationStore.save(ctx, "Rang lista ažurirana",
                "Tvoj rang ovog meseca: #12. Igraj više da napreduje!", "ranking");

        // --- Nagrade ---
        NotificationStore.save(ctx, "Dobijena nagrada!",
                "Završio si u top 10 nedeljne rang liste. +5 tokena dodato.", "rewards");
        NotificationStore.save(ctx, "Dnevna nagrada",
                "Prijavom danas dobijaš +2 tokena. Nastavi niz prijava!", "rewards");
        NotificationStore.save(ctx, "Posebna nagrada – liga",
                "Plasirao si se u Srebrnu ligu. +10 tokena nagrade!", "rewards");

        // --- Ostalo ---
        NotificationStore.save(ctx, "Poziv za prijateljsku partiju",
                "Ana te poziva na prijateljsku partiju u Skočko.", "other");
        NotificationStore.save(ctx, "Novi pratilac",
                "Marko je prihvatio tvoj zahtev za prijateljstvo.", "other");
        NotificationStore.save(ctx, "Prelazak u novu ligu",
                "Čestitamo! Prešao si u Zlatnu ligu sledeće nedelje.", "other");
        NotificationStore.save(ctx, "Dobrodošao u Slagalicu!",
                "Igraj igrice, osvajaj nagrade i penjaj se na rang listi.", "other");

        // Prikaži push notifikaciju za prvu čet i nagradu
        AppNotificationManager.show(ctx, SlagalicaApp.CHANNEL_CHAT,
                "Nova poruka u četu", "Marko: Hej, igramo večeras?");
        AppNotificationManager.show(ctx, SlagalicaApp.CHANNEL_REWARDS,
                "Dobijena nagrada!", "+5 tokena dodato na tvoj račun.");

        prefs.edit().putBoolean("demo_seeded_v2", true).apply();
    }

    /** Ručno dodaje 4 svježe test notifikacije bez seeded checka. */
    public static void addTestNotifications(Context ctx) {
        long ts = System.currentTimeMillis() % 10000;
        NotificationStore.save(ctx,
                "Test čet poruka [" + ts + "]",
                "Ovo je test poruka iz četa. Klikni da označiš kao pročitano.", "chat");
        NotificationStore.save(ctx,
                "Test rang lista [" + ts + "]",
                "Testni napredak – sada si na #3 mestu!", "ranking");
        NotificationStore.save(ctx,
                "Test nagrada [" + ts + "]",
                "Testna nagrada: +5 tokena za odlično igranje.", "rewards");
        NotificationStore.save(ctx,
                "Test poziv [" + ts + "]",
                "Ana te poziva na prijateljsku partiju u Skočko.", "other");
    }
}
