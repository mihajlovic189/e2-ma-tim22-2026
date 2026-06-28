package com.example.slagalicaapp;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class SlagalicaApp extends Application {

    public static final String CHANNEL_CHAT    = "channel_chat";
    public static final String CHANNEL_RANKING = "channel_ranking";
    public static final String CHANNEL_REWARDS = "channel_rewards";
    public static final String CHANNEL_OTHER   = "channel_other";
    public static boolean isUserInChatScreen    = false;
    public static boolean isAppInForeground    = false;
    /** True while MainActivity's RTDB chat listener is active (app process alive, not killed). */
    public static boolean isChatListenerRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_CHAT, "Čet poruke", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_RANKING, "Rang lista", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_REWARDS, "Nagrade", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_OTHER, "Ostalo", NotificationManager.IMPORTANCE_DEFAULT));
    }
}
