package com.example.slagalicaapp.game.asocijacije;

/**
 * Poslovna logika tabele za igru "Asocijacije".
 *
 * Po specifikaciji 1. KT:
 * <ul>
 *   <li>Igrač ima 4 kolone, svaka sa 4 skrivena polja koja asociraju na
 *       rešenje te kolone (5. polje).</li>
 *   <li>Rešenja svih kolona asociraju na <i>konačno rešenje</i>.</li>
 *   <li>Igrač otvara polja jedno po jedno i u svakom trenutku može da
 *       pogađa rešenje neke kolone ili konačno rešenje.</li>
 *   <li>Trajanje runde: <b>2 minuta (120s)</b>.</li>
 * </ul>
 *
 * <h3>Bodovanje (tačke f, g specifikacije)</h3>
 * <pre>
 *   Rešenje kolone:   2 + (broj NEOTVORENIH polja u koloni)
 *                     → max 6 (sva polja zatvorena), min 2 (sva otvorena)
 *
 *   Konačno rešenje:  7
 *                   + 6  za svaku NETAKNUTU kolonu (0 otvorenih polja, nije rešena)
 *                   + (2 + neotvorena_polja)  za svaku DELIMIČNO OTVORENU
 *                                              ali NEREŠENU kolonu
 *                   (već rešene kolone ne donose dodatne bodove
 *                    jer je igrač već dobio bodove za njih)
 * </pre>
 *
 * <h3>Primer iz specifikacije (tačka h)</h3>
 * Igrač otvori 1 polje u 1. koloni i pogodi konačno rešenje:
 * <ul>
 *   <li>7 za konačno</li>
 *   <li>2 + 3 = 5 za 1. kolonu (delimično otvorena, 3 neotvorena polja)</li>
 *   <li>3 × 6 = 18 za 3 netaknute kolone</li>
 *   <li>Ukupno: <b>30 bodova</b></li>
 * </ul>
 *
 * Klasa je čista poslovna logika - <b>NEMA</b> Android zavisnosti.
 */
public class AsocijacijeBoard {

    /** Broj kolona. */
    public static final int NUM_COLUMNS = 4;

    /** Broj skrivenih polja po koloni. */
    public static final int FIELDS_PER_COLUMN = 4;

    /** Trajanje runde u sekundama. */
    public static final int ROUND_DURATION_SECONDS = 120;

    /** Bodovni multiplikator iz formule za kolonu. */
    public static final int COLUMN_BASE_POINTS = 2;

    /** Bodovi po neotvorenom polju u koloni. */
    public static final int COLUMN_POINTS_PER_UNOPENED_FIELD = 1;

    /** Bazni bodovi za pogađanje konačnog rešenja. */
    public static final int FINAL_BASE_POINTS = 7;

    /** Bonus za svaku netaknutu (i nerešenu) kolonu pri pogađanju konačnog. */
    public static final int FINAL_UNTOUCHED_COLUMN_BONUS = 6;

    private final String[][] fields;          // [col][row]
    private final String[] columnSolutions;   // [col]
    private final String finalSolution;

    private final boolean[][] opened = new boolean[NUM_COLUMNS][FIELDS_PER_COLUMN];
    private final boolean[] columnSolved = new boolean[NUM_COLUMNS];
    private boolean finalSolved = false;
    private boolean timeExpired = false;

    /**
     * @param fields           [NUM_COLUMNS][FIELDS_PER_COLUMN] - reči/izrazi
     *                         u svakom polju, po koloni i redu.
     * @param columnSolutions  rešenja kolona (po koloni)
     * @param finalSolution    konačno rešenje
     */
    public AsocijacijeBoard(String[][] fields,
                            String[] columnSolutions,
                            String finalSolution) {
        validateGrid(fields);
        if (columnSolutions == null || columnSolutions.length != NUM_COLUMNS) {
            throw new IllegalArgumentException(
                    "columnSolutions mora imati tačno " + NUM_COLUMNS + " elemenata.");
        }
        if (finalSolution == null || finalSolution.trim().isEmpty()) {
            throw new IllegalArgumentException("finalSolution ne sme biti prazno.");
        }
        for (int c = 0; c < NUM_COLUMNS; c++) {
            if (columnSolutions[c] == null || columnSolutions[c].trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Rešenje kolone " + c + " je prazno.");
            }
        }

        // Defenzivna kopija
        this.fields = new String[NUM_COLUMNS][FIELDS_PER_COLUMN];
        for (int c = 0; c < NUM_COLUMNS; c++) {
            System.arraycopy(fields[c], 0, this.fields[c], 0, FIELDS_PER_COLUMN);
        }
        this.columnSolutions = columnSolutions.clone();
        this.finalSolution = finalSolution;
    }

    private static void validateGrid(String[][] fields) {
        if (fields == null || fields.length != NUM_COLUMNS) {
            throw new IllegalArgumentException(
                    "fields mora imati tačno " + NUM_COLUMNS + " kolona.");
        }
        for (int c = 0; c < NUM_COLUMNS; c++) {
            if (fields[c] == null || fields[c].length != FIELDS_PER_COLUMN) {
                throw new IllegalArgumentException(
                        "Kolona " + c + " mora imati tačno " + FIELDS_PER_COLUMN + " polja.");
            }
            for (int r = 0; r < FIELDS_PER_COLUMN; r++) {
                if (fields[c][r] == null) {
                    throw new IllegalArgumentException(
                            "Polje [" + c + "][" + r + "] je null.");
                }
            }
        }
    }

    // -------------------- Public API: otvaranje polja --------------------

    /**
     * Otvori skriveno polje u (kolona, red).
     *
     * @return reč/izraz iz polja, ili {@code null} ako igra više ne dozvoljava
     *         otvaranje (game over) ili je polje već otvoreno ili kolona rešena.
     */
    public String openField(int col, int row) {
        if (isGameOver()) return null;
        if (!isValidCoord(col, row)) return null;
        if (columnSolved[col]) return null;     // sva polja kolone su već "otkrivena"
        if (opened[col][row]) return null;      // već otvoreno
        opened[col][row] = true;
        return fields[col][row];
    }

    public boolean isFieldOpened(int col, int row) {
        if (!isValidCoord(col, row)) return false;
        return opened[col][row] || columnSolved[col];
    }

    /** Vraća tekst polja bez ikakve provere otvorenosti (interna upotreba/UI reveal). */
    public String getFieldText(int col, int row) {
        if (!isValidCoord(col, row)) return null;
        return fields[col][row];
    }

    public boolean isColumnSolved(int col) {
        if (col < 0 || col >= NUM_COLUMNS) return false;
        return columnSolved[col];
    }

    public String getColumnSolution(int col) {
        if (col < 0 || col >= NUM_COLUMNS) return null;
        return columnSolutions[col];
    }

    public String getFinalSolution() {
        return finalSolution;
    }

    // -------------------- Public API: pogađanje --------------------

    /**
     * Pokušaj pogađanja rešenja kolone.
     * <ul>
     *   <li>Ako je tačan: vraća {@code AsocijacijeGuessResult{correct=true,
     *       points = 2 + brojNeotvorenihPolja}}, kolona se markira kao rešena
     *       (sva polja postaju "otkrivena" - vidi {@link #isFieldOpened}).</li>
     *   <li>Ako je netačan, kolona već rešena, ili je igra završena:
     *       vraća {@link AsocijacijeGuessResult#miss()}.</li>
     * </ul>
     */
    public AsocijacijeGuessResult guessColumn(int col, String attempt) {
        if (isGameOver()) return AsocijacijeGuessResult.miss();
        if (col < 0 || col >= NUM_COLUMNS) return AsocijacijeGuessResult.miss();
        if (columnSolved[col]) return AsocijacijeGuessResult.miss();
        if (!matches(attempt, columnSolutions[col])) return AsocijacijeGuessResult.miss();

        int points = pointsForColumnAt(col);
        columnSolved[col] = true;
        return new AsocijacijeGuessResult(true, points);
    }

    /**
     * Pokušaj pogađanja konačnog rešenja. Ako je tačan, igra se završava
     * i obračunavaju se bodovi po formuli iz tačke (g) specifikacije.
     */
    public AsocijacijeGuessResult guessFinal(String attempt) {
        if (isGameOver()) return AsocijacijeGuessResult.miss();
        if (!matches(attempt, finalSolution)) return AsocijacijeGuessResult.miss();

        int points = FINAL_BASE_POINTS;
        for (int c = 0; c < NUM_COLUMNS; c++) {
            if (columnSolved[c]) {
                // već rešena - igrač je već dobio bodove
                continue;
            }
            int openedInCol = openedCountInColumn(c);
            if (openedInCol == 0) {
                // netaknuta kolona
                points += FINAL_UNTOUCHED_COLUMN_BONUS;
            } else {
                // delimično otvorena - bodovi po formuli kolone
                points += pointsForColumnAt(c);
            }
        }
        finalSolved = true;
        return new AsocijacijeGuessResult(true, points);
    }

    // -------------------- State --------------------

    public boolean isFinalSolved() {
        return finalSolved;
    }

    public boolean isTimeExpired() {
        return timeExpired;
    }

    public boolean isGameOver() {
        return finalSolved || timeExpired;
    }

    /** UI poziva kad istekne tajmer (120s). */
    public void markTimeExpired() {
        timeExpired = true;
    }

    // -------------------- Internal helpers --------------------

    private boolean isValidCoord(int col, int row) {
        return col >= 0 && col < NUM_COLUMNS
                && row >= 0 && row < FIELDS_PER_COLUMN;
    }

    private int openedCountInColumn(int col) {
        int count = 0;
        for (int r = 0; r < FIELDS_PER_COLUMN; r++) {
            if (opened[col][r]) count++;
        }
        return count;
    }

    private int pointsForColumnAt(int col) {
        int unopened = FIELDS_PER_COLUMN - openedCountInColumn(col);
        return COLUMN_BASE_POINTS + unopened * COLUMN_POINTS_PER_UNOPENED_FIELD;
    }

    /**
     * Poređenje stringova ignoriše velika/mala slova i okruženi razmak.
     * Tolerantno na ć/č/š/ž/đ tako što ih NORMALIZUJE - npr. "Niš" i "NIS"
     * će se SMATRATI različitim. Ako je potrebna tolerancija na dijakritike,
     * lako je proširiti ovu metodu (Normalizer.normalize(..., NFD)
     * pa replaceAll). Za 1. KT držimo se striktnog poređenja sa tolerancijom
     * samo na case i whitespace.
     */
    private static boolean matches(String attempt, String solution) {
        if (attempt == null || solution == null) return false;
        return attempt.trim().equalsIgnoreCase(solution.trim());
    }
}
