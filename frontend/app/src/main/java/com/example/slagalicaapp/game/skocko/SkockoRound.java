package com.example.slagalicaapp.game.skocko;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Poslovna logika jedne RUNDE igre Skočko.
 *
 * Po specifikaciji 1. KT:
 * <ul>
 *   <li>Kombinacija je dužine {@value #COMBO_LENGTH} simbola
 *       iz skupa od 6 ({@link SkockoSymbol}).</li>
 *   <li>Maksimalno {@value #MAX_ATTEMPTS} pokušaja po rundi.</li>
 *   <li>Trajanje runde je {@value #ROUND_DURATION_SECONDS} sekundi
 *       (UI sloj poziva {@link #markTimeExpired()} kad istekne tajmer).</li>
 *   <li>Bodovanje: pokušaji 1-2 → 20, 3-4 → 15, 5-6 → 10.</li>
 * </ul>
 *
 * Klasa je čista poslovna logika - <b>NEMA</b> Android zavisnosti i
 * <b>NE</b> upravlja vremenom sama. Tajmer (CountDownTimer) se vodi
 * spolja, u UI sloju, a kad istekne UI samo pozove
 * {@link #markTimeExpired()} na ovoj rundi.
 *
 * Klasa je stateful: pravi se nova instanca po rundi. Konstruktorom se
 * može proslediti seed-ovan {@link Random} radi determinističkih testova.
 */
public class SkockoRound {

    /** Dužina dobitne kombinacije. */
    public static final int COMBO_LENGTH = 4;

    /** Maksimalan broj pokušaja po rundi. */
    public static final int MAX_ATTEMPTS = 6;

    /** Trajanje runde u sekundama. */
    public static final int ROUND_DURATION_SECONDS = 30;

    /** Stanje runde. */
    public enum State {
        /** Runda u toku, igrač može da unosi kombinacije. */
        IN_PROGRESS,
        /** Igrač je pogodio kompletnu kombinaciju (reds == 4). */
        WON,
        /** Iskorišćena su sva 6 pokušaja, ili je istekao tajmer. */
        LOST
    }

    private final SkockoSymbol[] secret;
    private final List<GuessFeedback> history = new ArrayList<>();
    private State state = State.IN_PROGRESS;
    /** 1-based: koji pokušaj je doveo do pobede (0 ako još nije pobedio). */
    private int winningAttempt = 0;

    // -------------------- Konstrukcija --------------------

    /** Pravi rundu sa nasumičnom dobitnom kombinacijom. */
    public SkockoRound() {
        this(new Random());
    }

    /** Pravi rundu sa nasumičnom kombinacijom, koristeći prosleđen RNG. */
    public SkockoRound(Random rng) {
        this.secret = generateSecret(rng);
    }

    /**
     * Test-friendly konstruktor: prosleđuje se fiksna dobitna kombinacija.
     * Korisno za unit testove.
     */
    public SkockoRound(SkockoSymbol[] fixedSecret) {
        if (fixedSecret == null || fixedSecret.length != COMBO_LENGTH) {
            throw new IllegalArgumentException(
                    "Kombinacija mora imati tačno " + COMBO_LENGTH + " simbola.");
        }
        for (SkockoSymbol s : fixedSecret) {
            if (s == null) {
                throw new IllegalArgumentException("Simbol u kombinaciji ne sme biti null.");
            }
        }
        this.secret = Arrays.copyOf(fixedSecret, COMBO_LENGTH);
    }

    private static SkockoSymbol[] generateSecret(Random rng) {
        SkockoSymbol[] arr = new SkockoSymbol[COMBO_LENGTH];
        for (int i = 0; i < COMBO_LENGTH; i++) {
            arr[i] = SkockoSymbol.random(rng);
        }
        return arr;
    }

    // -------------------- Public API --------------------

    /**
     * Primi pokušaj igrača. Vraća feedback (broj crvenih i žutih).
     * Automatski ažurira stanje (WON ako je pogodio, LOST ako iskoristi
     * sve pokušaje).
     *
     * @throws IllegalStateException ako runda više nije u toku
     * @throws IllegalArgumentException ako kombinacija ne sadrži
     *         tačno {@value #COMBO_LENGTH} ne-null simbola
     */
    public GuessFeedback submitGuess(SkockoSymbol[] guess) {
        if (state != State.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Runda više nije u toku (stanje: " + state + ").");
        }
        validateGuess(guess);

        GuessFeedback feedback = evaluate(guess, secret);
        history.add(feedback);

        if (feedback.isWin()) {
            winningAttempt = history.size();
            state = State.WON;
        } else if (history.size() >= MAX_ATTEMPTS) {
            state = State.LOST;
        }
        return feedback;
    }

    /**
     * Eksplicitno markira da je vreme isteklo. Ako runda još nije
     * završena, prelazi u {@link State#LOST}. UI poziva ovo iz
     * {@code CountDownTimer.onFinish()}.
     */
    public void markTimeExpired() {
        if (state == State.IN_PROGRESS) {
            state = State.LOST;
        }
    }

    public State getState() {
        return state;
    }

    /** True ako je runda u stanju {@link State#WON}. */
    public boolean isWon() {
        return state == State.WON;
    }

    /** True ako runda više nije aktivna (WON ili LOST). */
    public boolean isFinished() {
        return state != State.IN_PROGRESS;
    }

    /**
     * Broj pokušaja koje je igrač već iskoristio (0..{@value #MAX_ATTEMPTS}).
     */
    public int getAttemptsUsed() {
        return history.size();
    }

    /**
     * Broj pokušaja koji su preostali (uključujući trenutni ako runda traje).
     */
    public int getAttemptsRemaining() {
        return MAX_ATTEMPTS - history.size();
    }

    /**
     * 1-based redni broj NAREDNOG pokušaja (1..{@value #MAX_ATTEMPTS}).
     * Vraća {@value #MAX_ATTEMPTS} + 1 ako su svi pokušaji potrošeni.
     */
    public int getCurrentAttemptNumber() {
        return history.size() + 1;
    }

    /** 1-based broj pokušaja u kome je igrač pobedio. 0 ako nije pobedio. */
    public int getWinningAttempt() {
        return winningAttempt;
    }

    /** Read-only istorija feedback-a po pokušajima (najraniji prvi). */
    public List<GuessFeedback> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** Kopija dobitne kombinacije - za prikaz nakon kraja runde. */
    public SkockoSymbol[] revealSecret() {
        return Arrays.copyOf(secret, COMBO_LENGTH);
    }

    /**
     * Bodovi koje je igrač osvojio u OVOJ rundi po standardnom tier-u:
     * <pre>
     *   pokušaj 1 ili 2 → 20
     *   pokušaj 3 ili 4 → 15
     *   pokušaj 5 ili 6 → 10
     *   nije pobedio    → 0
     * </pre>
     * Steal logika ({@code 10 bodova za protivnika u 10s}) se NE računa
     * ovde - to vodi {@link SkockoMatch}.
     */
    public int getEarnedPoints() {
        if (!isWon()) return 0;
        return scoreForAttempt(winningAttempt);
    }

    /** Vraća tier bodove za dati 1-based pokušaj. Public radi testiranja. */
    public static int scoreForAttempt(int attempt1Based) {
        if (attempt1Based <= 2) return 20;
        if (attempt1Based <= 4) return 15;
        if (attempt1Based <= MAX_ATTEMPTS) return 10;
        return 0;
    }

    // -------------------- Internal --------------------

    private static void validateGuess(SkockoSymbol[] guess) {
        if (guess == null || guess.length != COMBO_LENGTH) {
            throw new IllegalArgumentException(
                    "Pokušaj mora imati tačno " + COMBO_LENGTH + " simbola.");
        }
        for (int i = 0; i < COMBO_LENGTH; i++) {
            if (guess[i] == null) {
                throw new IllegalArgumentException(
                        "Simbol na poziciji " + i + " je null.");
            }
        }
    }

    /**
     * Mastermind algoritam za reds/yellows. Ispravno baratamo duplikatima:
     * svaki simbol u dobitnoj kombinaciji može biti "potrošen" najviše
     * jednom, i svaki simbol u pokušaju može doprineti najviše jednom
     * indikatoru (crvenom ILI žutom).
     *
     * Algoritam (2 prolaska):
     *   1) Prvi prolazak: za svaku poziciju, ako su isti — povećaj reds;
     *      inače upiši simbol iz secret-a i guess-a u histograme.
     *   2) Drugi prolazak: za svaki simbol, yellows += min(secretHist, guessHist).
     */
    static GuessFeedback evaluate(SkockoSymbol[] guess, SkockoSymbol[] secret) {
        int reds = 0;
        int[] secretHist = new int[SkockoSymbol.values().length];
        int[] guessHist  = new int[SkockoSymbol.values().length];

        for (int i = 0; i < COMBO_LENGTH; i++) {
            if (guess[i] == secret[i]) {
                reds++;
            } else {
                secretHist[secret[i].ordinal()]++;
                guessHist[guess[i].ordinal()]++;
            }
        }

        int yellows = 0;
        for (int s = 0; s < secretHist.length; s++) {
            yellows += Math.min(secretHist[s], guessHist[s]);
        }
        return new GuessFeedback(reds, yellows);
    }
}
