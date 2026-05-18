package com.example.slagalicaapp.game.skocko;

/**
 * Immutable rezultat evaluacije jednog poteza (jedne kombinacije od 4 simbola)
 * u odnosu na dobitnu kombinaciju.
 *
 * <ul>
 *   <li><b>reds</b>  — broj simbola koji su POGOĐENI i nalaze se NA TAČNOM
 *                      mestu (u GUI: crveni indikator).</li>
 *   <li><b>yellows</b> — broj simbola koji su POGOĐENI ali na POGREŠNOM
 *                      mestu (u GUI: žuti indikator).</li>
 * </ul>
 *
 * Važno: red + yellow ≤ 4 (dužina kombinacije). Pogodak (isWin) je
 * ekvivalentan {@code reds == 4}.
 *
 * Logika brojanja je "Mastermind" pravilo: svaki simbol iz unosa može
 * doprineti najviše JEDNOM indikatoru (red ILI yellow, ne oba), i svaki
 * simbol u dobitnoj kombinaciji može biti "potrošen" najviše jednom.
 * Detalji u {@link SkockoRound#evaluate}.
 */
public final class GuessFeedback {

    private final int reds;
    private final int yellows;

    public GuessFeedback(int reds, int yellows) {
        if (reds < 0 || yellows < 0) {
            throw new IllegalArgumentException(
                    "Reds i yellows ne mogu biti negativni: "
                            + reds + ", " + yellows);
        }
        if (reds + yellows > SkockoRound.COMBO_LENGTH) {
            throw new IllegalArgumentException(
                    "Ukupno reds + yellows ne sme preći " + SkockoRound.COMBO_LENGTH
                            + " (dobio: " + (reds + yellows) + ")");
        }
        this.reds = reds;
        this.yellows = yellows;
    }

    public int getReds()    { return reds; }
    public int getYellows() { return yellows; }

    /** True ako su sva 4 simbola na tačnom mestu (potpun pogodak). */
    public boolean isWin() {
        return reds == SkockoRound.COMBO_LENGTH;
    }

    @Override
    public String toString() {
        return "GuessFeedback{reds=" + reds + ", yellows=" + yellows + "}";
    }
}
