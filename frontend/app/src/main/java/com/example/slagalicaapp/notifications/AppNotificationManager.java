package com.example.slagalicaapp.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.ui.activities.MainActivity;

import java.util.concurrent.atomic.AtomicInteger;

public class AppNotificationManager {

    private static final AtomicInteger idCounter = new AtomicInteger(1000);

    public static void show(Context ctx, String channel, String title, String body) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        try {
            nm.notify(idCounter.getAndIncrement(), builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS permission not granted — notification silently skipped
        }
    }
}
