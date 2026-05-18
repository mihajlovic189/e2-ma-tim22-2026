package com.example.slagalicaapp.game.asocijacije;

/**
 * Immutable rezultat jednog pokušaja pogađanja (rešenja kolone ili
 * konačnog rešenja) u igri Asocijacije.
 */
public final class AsocijacijeGuessResult {

    private final boolean correct;
    private final int pointsAwarded;

    public AsocijacijeGuessResult(boolean correct, int pointsAwarded) {
        if (pointsAwarded < 0) {
            throw new IllegalArgumentException(
                    "Bodovi ne mogu biti negativni: " + pointsAwarded);
        }
        this.correct = correct;
        this.pointsAwarded = pointsAwarded;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public static AsocijacijeGuessResult miss() {
        return new AsocijacijeGuessResult(false, 0);
    }

    @Override
    public String toString() {
        return "AsocijacijeGuessResult{correct=" + correct
                + ", points=" + pointsAwarded + "}";
    }
}
