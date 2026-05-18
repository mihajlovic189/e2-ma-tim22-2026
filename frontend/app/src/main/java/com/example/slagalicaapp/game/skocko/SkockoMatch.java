package com.example.slagalicaapp.game.skocko;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Orkestrator čitavog meča Skočka (2 runde + "steal" prilike).
 *
 * Po specifikaciji 1. KT:
 * <ul>
 *   <li>Meč ima 2 runde, ukupno 2 × 30s = 60s aktivne igre.</li>
 *   <li>Svaku rundu započinje po jedan igrač:
 *       runda 1 → Igrač 1, runda 2 → Igrač 2.</li>
 *   <li>Bodovanje pogotka u 6 pokušaja: 20 (1-2), 15 (3-4), 10 (5-6).</li>
 *   <li>Ako aktivni igrač ne pogodi svoju kombinaciju, protivnik ima
 *       jednu priliku od 10s da pokuša i osvoji 10 bodova.</li>
 * </ul>
 *
 * <h3>Životni ciklus / faze (state machine)</h3>
 * <pre>
 *   ROUND_1_PLAYING ─(P1 pogodi/iskoristi pokušaje/istekne vreme)─┐
 *        │                                                         │
 *        │   ako P1 NIJE pogodio:                                 │
 *        ▼                                                         │
 *   ROUND_1_STEAL  (P2 ima 1 pokušaj, 10s, +10 bodova ako pogodi)─┤
 *        │                                                         │
 *        ▼                                                         │
 *   ROUND_2_PLAYING ◄─(ako je P1 pogodio i nema steal-a)──────────┘
 *        │
 *        │   ako P2 NIJE pogodio:
 *        ▼
 *   ROUND_2_STEAL  (P1 ima 1 pokušaj, 10s, +10 bodova ako pogodi)
 *        │
 *        ▼
 *   FINISHED
 * </pre>
 *
 * Klasa je čista poslovna logika - <b>NEMA</b> Android zavisnosti.
 * UI sloj vodi tajmer i poziva:
 * <ul>
 *   <li>{@link #submitGuess(SkockoSymbol[])} - kad igrač potvrdi unos</li>
 *   <li>{@link #markTimeExpired()} - kad odgovarajući tajmer dođe do 0</li>
 * </ul>
 *
 * <p><b>Napomena o "steal" bodovima:</b> tekst pravila (tačka d) eksplicitno
 * kaže <i>10 bodova</i>. Primer (tačka f) "15+15=30" deluje kao tipfeler u
 * specifikaciji (ne poklapa se sa pravilom). Bezbedno je menjati
 * {@link #STEAL_POINTS} konstantu ako asistent kaže drugačije.</p>
 */
public class SkockoMatch {

    public enum Player { PLAYER_ONE, PLAYER_TWO }

    public enum Phase {
        ROUND_1_PLAYING,
        ROUND_1_STEAL,
        ROUND_2_PLAYING,
        ROUND_2_STEAL,
        FINISHED
    }

    /** Bodovi koji se dobijaju za uspešan "steal" (tačka d specifikacije). */
    public static final int STEAL_POINTS = 10;

    /** Trajanje "steal" prilike u sekundama. */
    public static final int STEAL_DURATION_SECONDS = 10;

    private final SkockoRound round1;
    private final SkockoRound round2;

    /** Steal-runde se generišu tek kad zatreba (kao "tanke" rundice 1 pokušaja). */
    private SkockoRound round1Steal;
    private SkockoRound round2Steal;

    private final Map<Player, Integer> scores = new EnumMap<>(Player.class);
    private Phase phase = Phase.ROUND_1_PLAYING;

    // -------------------- Konstrukcija --------------------

    /** Standardna konstrukcija sa nasumičnim kombinacijama. */
    public SkockoMatch() {
        this(new Random());
    }

    /** Konstrukcija sa proslijeđenim RNG (deterministični testovi). */
    public SkockoMatch(Random rng) {
        this.round1 = new SkockoRound(rng);
        this.round2 = new SkockoRound(rng);
        scores.put(Player.PLAYER_ONE, 0);
        scores.put(Player.PLAYER_TWO, 0);
    }

    /** Test-friendly konstrukcija sa fiksnim kombinacijama za obe runde. */
    public SkockoMatch(SkockoSymbol[] secret1, SkockoSymbol[] secret2) {
        this.round1 = new SkockoRound(secret1);
        this.round2 = new SkockoRound(secret2);
        scores.put(Player.PLAYER_ONE, 0);
        scores.put(Player.PLAYER_TWO, 0);
    }

    // -------------------- Public API --------------------

    public Phase getPhase() {
        return phase;
    }

    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    /** Koji igrač trenutno unosi kombinaciju. Null ako je meč završen. */
    public Player getActivePlayer() {
        switch (phase) {
            case ROUND_1_PLAYING: return Player.PLAYER_ONE;  // P1 igra svoju rundu
            case ROUND_1_STEAL:   return Player.PLAYER_TWO;  // P2 krade
            case ROUND_2_PLAYING: return Player.PLAYER_TWO;  // P2 igra svoju rundu
            case ROUND_2_STEAL:   return Player.PLAYER_ONE;  // P1 krade
            default: return null;
        }
    }

    /** SkockoRound koji je trenutno aktivan (sa kombinacijom koju treba pogoditi). */
    public SkockoRound getActiveRound() {
        switch (phase) {
            case ROUND_1_PLAYING: return round1;
            case ROUND_1_STEAL:   return round1Steal;
            case ROUND_2_PLAYING: return round2;
            case ROUND_2_STEAL:   return round2Steal;
            default: return null;
        }
    }

    public int getScore(Player player) {
        Integer s = scores.get(player);
        return s == null ? 0 : s;
    }

    /**
     * Predaje pokušaj aktivnom rundi, ažurira bodove i fazu po potrebi.
     *
     * @return feedback (reds/yellows)
     * @throws IllegalStateException ako je meč završen
     */
    public GuessFeedback submitGuess(SkockoSymbol[] guess) {
        if (phase == Phase.FINISHED) {
            throw new IllegalStateException("Meč je završen.");
        }
        SkockoRound active = getActiveRound();
        GuessFeedback feedback = active.submitGuess(guess);

        // U STEAL fazi protivnik ima samo JEDAN pokušaj, bez obzira što
        // SkockoRound interno dozvoljava 6. Ako nije pogodio, forsiramo
        // kraj sub-runde odmah.
        if (isStealPhase() && !active.isFinished()) {
            active.markTimeExpired();
        }

        // Kraj sub-runde? (WON ili LOST)
        if (active.isFinished()) {
            handleSubRoundEnd(active);
        }
        return feedback;
    }

    /**
     * Pozvati kad istekne tajmer trenutne faze:
     *   - 30s u ROUND_1_PLAYING / ROUND_2_PLAYING
     *   - 10s u ROUND_1_STEAL  / ROUND_2_STEAL
     *
     * Ako je vreme isteklo u glavnoj rundi, igrač je izgubio (otvara se
     * steal prilika). Ako je vreme isteklo u steal fazi, steal je
     * promašen i prelazi se dalje.
     */
    public void markTimeExpired() {
        if (phase == Phase.FINISHED) return;
        SkockoRound active = getActiveRound();
        if (active != null && !active.isFinished()) {
            active.markTimeExpired();
        }
        handleSubRoundEnd(active);
    }

    /**
     * Koliko sekundi UI tajmer treba da odbrojava u trenutnoj fazi.
     */
    public int getCurrentPhaseDurationSeconds() {
        switch (phase) {
            case ROUND_1_PLAYING:
            case ROUND_2_PLAYING:
                return SkockoRound.ROUND_DURATION_SECONDS;
            case ROUND_1_STEAL:
            case ROUND_2_STEAL:
                return STEAL_DURATION_SECONDS;
            default:
                return 0;
        }
    }

    // -------------------- Internal: state machine --------------------

    private void handleSubRoundEnd(SkockoRound endedRound) {
        switch (phase) {
            case ROUND_1_PLAYING:
                onRound1MainEnded();
                break;
            case ROUND_1_STEAL:
                onRound1StealEnded();
                break;
            case ROUND_2_PLAYING:
                onRound2MainEnded();
                break;
            case ROUND_2_STEAL:
                onRound2StealEnded();
                break;
            default:
                break;
        }
    }

    private void onRound1MainEnded() {
        if (round1.isWon()) {
            addPoints(Player.PLAYER_ONE, round1.getEarnedPoints());
            // P1 je pogodio - bez steal-a, idemo direktno na rundu 2
            phase = Phase.ROUND_2_PLAYING;
        } else {
            // P1 promašio - P2 ima 10s priliku da pogodi P1-ovu kombinaciju.
            // Steal koristi istu kombinaciju kao i originalna runda, ali je
            // ograničen na 1 pokušaj (vidi submitGuess - forsiranje kraja).
            round1Steal = new SkockoRound(round1.revealSecret());
            phase = Phase.ROUND_1_STEAL;
        }
    }

    private void onRound1StealEnded() {
        // Steal je "jedna prilika" - ako pogodi, +10. Inače 0.
        if (round1Steal != null && round1Steal.isWon()) {
            addPoints(Player.PLAYER_TWO, STEAL_POINTS);
        }
        phase = Phase.ROUND_2_PLAYING;
    }

    private void onRound2MainEnded() {
        if (round2.isWon()) {
            addPoints(Player.PLAYER_TWO, round2.getEarnedPoints());
            phase = Phase.FINISHED;
        } else {
            round2Steal = new SkockoRound(round2.revealSecret());
            phase = Phase.ROUND_2_STEAL;
        }
    }

    private void onRound2StealEnded() {
        if (round2Steal != null && round2Steal.isWon()) {
            addPoints(Player.PLAYER_ONE, STEAL_POINTS);
        }
        phase = Phase.FINISHED;
    }

    private void addPoints(Player p, int delta) {
        scores.put(p, getScore(p) + delta);
    }

    private boolean isStealPhase() {
        return phase == Phase.ROUND_1_STEAL || phase == Phase.ROUND_2_STEAL;
    }
}
