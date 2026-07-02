package com.example.slagalicaapp.ui.dialogs;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.slagalicaapp.R;

import java.util.Random;

/**
 * Shows the "you earned a cycle reward" dialog with a bounce-in icon, a confetti
 * burst and a short chime — used both when opening the app right after a
 * weekly/monthly cycle ends and from the Leaderboard screen.
 */
public final class RewardDialogHelper {

    private static final String[] CONFETTI_EMOJI = {"⭐", "🎉", "🎊", "✨"};
    private static final int CONFETTI_COUNT = 18;

    private RewardDialogHelper() {}

    public static void show(Context context, String title, String body, Runnable onDismissed) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_reward, null);
        dialog.setContentView(root);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TextView tvTitle = root.findViewById(R.id.tvRewardTitle);
        TextView tvBody = root.findViewById(R.id.tvRewardBody);
        View icon = root.findViewById(R.id.ivRewardIcon);
        FrameLayout confettiContainer = root.findViewById(R.id.confettiContainer);

        tvTitle.setText(title != null ? title : "Nagrada za plasman!");
        tvBody.setText(body != null ? body : "");

        MediaPlayer[] player = new MediaPlayer[1];
        try {
            player[0] = MediaPlayer.create(context, R.raw.reward_chime);
            if (player[0] != null) {
                player[0].setOnCompletionListener(MediaPlayer::release);
                player[0].start();
            }
        } catch (Exception ignored) {
            // Audio is a nice-to-have; never block the reward reveal on it.
        }

        root.findViewById(R.id.btnRewardClose).setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            if (player[0] != null) {
                try { if (player[0].isPlaying()) player[0].stop(); player[0].release(); } catch (Exception ignored) {}
                player[0] = null;
            }
            if (onDismissed != null) onDismissed.run();
        });

        dialog.show();

        icon.setScaleX(0f);
        icon.setScaleY(0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 0f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0f, 1f);
        scaleX.setDuration(500);
        scaleY.setDuration(500);
        scaleX.setInterpolator(new OvershootInterpolator(3f));
        scaleY.setInterpolator(new OvershootInterpolator(3f));
        scaleX.start();
        scaleY.start();

        confettiContainer.post(() -> spawnConfetti(context, confettiContainer));
    }

    private static void spawnConfetti(Context context, FrameLayout container) {
        int width = container.getWidth();
        int height = container.getHeight();
        if (width == 0 || height == 0) return;

        Random random = new Random();
        for (int i = 0; i < CONFETTI_COUNT; i++) {
            TextView particle = new TextView(context);
            particle.setText(CONFETTI_EMOJI[random.nextInt(CONFETTI_EMOJI.length)]);
            particle.setTextSize(20f + random.nextInt(10));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            container.addView(particle, lp);

            float startX = width / 2f;
            float startY = height * 0.35f;
            particle.setX(startX);
            particle.setY(startY);
            particle.setAlpha(0f);

            float angle = (float) (random.nextDouble() * Math.PI * 2);
            float distance = 120f + random.nextFloat() * 260f;
            float endX = startX + (float) Math.cos(angle) * distance;
            float endY = startY + (float) Math.sin(angle) * distance + 200f; // slight downward drift ("gravity")
            long duration = 700 + random.nextInt(500);
            long startDelay = random.nextInt(150);

            particle.animate()
                    .x(endX)
                    .y(endY)
                    .alpha(1f)
                    .rotationBy(random.nextBoolean() ? 360f : -360f)
                    .setStartDelay(startDelay)
                    .setDuration(duration)
                    .withEndAction(() -> particle.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction(() -> container.removeView(particle))
                            .start())
                    .start();
        }
    }
}
