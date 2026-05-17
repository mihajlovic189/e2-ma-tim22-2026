package com.example.slagalicaapp.ui.header;

import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalicaapp.R;

/**
 * Helper klasa za upravljanje deljenim "Game Header" GUI-jem
 * (view_game_header.xml) koji se preko {@code <include/>} ubacuje
 * iznad postojecih game ekrana (Asocijacije, Skocko, ...).
 *
 * Enkapsulira:
 *  - {@link CountDownTimer} koji broji unazad od {@link #DEFAULT_DURATION_MS}
 *  - update centralnog tajmer TextView-a
 *  - prikaz imena i score-a za Igraca 1 i Igraca 2
 *  - callback {@link OnTimerFinishedListener} kada vreme istekne
 *
 * Pravi se po jedna instanca po fragment-u; obavezno pozvati
 * {@link #release()} u {@code onDestroyView()} da se izbegne curenje memorije.
 *
 * Tipicna upotreba (u fragment-u):
 * <pre>
 *   header = new GameHeaderController(rootView);
 *   header.setPlayerNames("Pera", "Mika");
 *   header.setOnTimerFinishedListener(() -> onVremeIsteklo());
 *   header.start();
 *   ...
 *   header.release(); // u onDestroyView()
 * </pre>
 */
public class GameHeaderController {

    /** Default trajanje runde - 60 sekundi (1. KT zahtev). */
    public static final long DEFAULT_DURATION_MS = 60_000L;

    /** Tick svake sekunde. */
    private static final long TICK_INTERVAL_MS = 1_000L;

    /** Callback koji se okida kada tajmer dodje do 0. */
    public interface OnTimerFinishedListener {
        void onTimerFinished();
    }

    private final TextView timerView;
    private final TextView player1NameView;
    private final TextView player1ScoreView;
    private final TextView player2NameView;
    private final TextView player2ScoreView;

    private final long durationMs;

    @Nullable private CountDownTimer countDownTimer;
    @Nullable private OnTimerFinishedListener listener;

    private int player1Score = 0;
    private int player2Score = 0;
    private long lastTickRemainingMs;
    private boolean running = false;

    /**
     * @param rootView Bilo koji parent view koji u svom layout stablu
     *                 sadrzi {@code <include layout="@layout/view_game_header"/>}.
     */
    public GameHeaderController(@NonNull View rootView) {
        this(rootView, DEFAULT_DURATION_MS);
    }

    public GameHeaderController(@NonNull View rootView, long durationMs) {
        this.durationMs = durationMs;
        this.lastTickRemainingMs = durationMs;

        this.timerView        = rootView.findViewById(R.id.header_timer);
        this.player1NameView  = rootView.findViewById(R.id.header_player1_name);
        this.player1ScoreView = rootView.findViewById(R.id.header_player1_score);
        this.player2NameView  = rootView.findViewById(R.id.header_player2_name);
        this.player2ScoreView = rootView.findViewById(R.id.header_player2_score);

        // Inicijalni prikaz (pre nego sto pokrenemo tajmer).
        renderTimer(durationMs);
        renderScores();
    }

    // -------------------- Public API --------------------

    public void setPlayerNames(@Nullable String player1Name, @Nullable String player2Name) {
        if (player1NameView != null && player1Name != null) {
            player1NameView.setText(player1Name);
        }
        if (player2NameView != null && player2Name != null) {
            player2NameView.setText(player2Name);
        }
    }

    public void setOnTimerFinishedListener(@Nullable OnTimerFinishedListener listener) {
        this.listener = listener;
    }

    /** Pokrece tajmer od pune vrednosti (60s). */
    public void start() {
        cancelInternal();
        lastTickRemainingMs = durationMs;
        countDownTimer = buildTimer(durationMs);
        countDownTimer.start();
        running = true;
    }

    /** Zaustavlja tajmer ali ZADRZAVA prikazanu vrednost (pauza). */
    public void stop() {
        cancelInternal();
        running = false;
    }

    /**
     * Zaustavlja tajmer i vraca prikaz na pocetnih {@link #durationMs}.
     * NE startuje ga ponovo - za to pozvati {@link #start()}.
     */
    public void reset() {
        cancelInternal();
        lastTickRemainingMs = durationMs;
        renderTimer(durationMs);
        running = false;
    }

    /** Da li tajmer trenutno aktivno odbrojava. */
    public boolean isRunning() {
        return running;
    }

    public void addPointsToPlayer1(int delta) {
        player1Score += delta;
        renderScores();
    }

    public void addPointsToPlayer2(int delta) {
        player2Score += delta;
        renderScores();
    }

    public void setScores(int p1, int p2) {
        this.player1Score = p1;
        this.player2Score = p2;
        renderScores();
    }

    public int getPlayer1Score() { return player1Score; }
    public int getPlayer2Score() { return player2Score; }

    /**
     * Obavezno pozvati u {@code onDestroyView()} kako CountDownTimer ne bi
     * drzao referencu na uniseni view i pravio leak.
     */
    public void release() {
        cancelInternal();
        listener = null;
    }

    // -------------------- Internal --------------------

    private CountDownTimer buildTimer(long startFromMs) {
        return new CountDownTimer(startFromMs, TICK_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                lastTickRemainingMs = millisUntilFinished;
                renderTimer(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                lastTickRemainingMs = 0;
                renderTimer(0);
                running = false;
                if (listener != null) {
                    listener.onTimerFinished();
                }
            }
        };
    }

    private void cancelInternal() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void renderTimer(long remainingMs) {
        if (timerView == null) return;
        // Zaokruzujemo na celu sekundu navise (60 -> 60, 59.x -> 60 na startu,
        // 0.x -> 1 dok ne padne na pravu 0).
        long seconds = (remainingMs + 999L) / 1000L;
        timerView.setText(String.valueOf(seconds));
    }

    private void renderScores() {
        if (player1ScoreView != null) {
            player1ScoreView.setText(String.valueOf(player1Score));
        }
        if (player2ScoreView != null) {
            player2ScoreView.setText(String.valueOf(player2Score));
        }
    }
}
