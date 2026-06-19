package com.example.slagalicaapp.utils;

import com.example.slagalicaapp.data.models.League;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central authority for league definitions and progression rules.
 *
 * Thresholds (stars required):
 *   Liga 0 – Početnik:         0
 *   Liga 1 – Bronzana liga:  100
 *   Liga 2 – Srebrna liga:   200  (100 × 2)
 *   Liga 3 – Zlatna liga:    400  (200 × 2)
 *   Liga 4 – Dijamantska:    800  (400 × 2)
 *   Liga 5 – Majstorska:    1600  (800 × 2)
 *
 * Daily tokens = BASE_DAILY_TOKENS + league.dailyBonusTokens
 * Monthly penalty (no top-3 placement) = keep 70 % of totalStars (lose 30 %).
 */
public final class LeagueManager {

    public static final int BASE_DAILY_TOKENS = 5;
    private static final float MONTHLY_KEEP_FACTOR = 0.70f;

    public static final List<League> LEAGUES = Collections.unmodifiableList(Arrays.asList(
            new League(0, "Rookie",  "🌱",  0,    0),
            new League(1, "Bronze",  "🥈",  100,  1),   // 🥈 icon on Bronze (swapped with Silver)
            new League(2, "Silver",  "🥉",  200,  2),   // 🥉 icon on Silver (swapped with Bronze)
            new League(3, "Gold",    "🥇",  400,  3),
            new League(4, "Diamond", "💎",  800,  4),
            new League(5, "Master",  "👑",  1600, 5)
    ));

    private LeagueManager() {}

    /** Returns the highest league the player qualifies for given their total stars. */
    public static League forStars(int totalStars) {
        League result = LEAGUES.get(0);
        for (League l : LEAGUES) {
            if (totalStars >= l.starsRequired) result = l;
            else break;
        }
        return result;
    }

    /** Total daily token allowance for the given star count. */
    public static int dailyTokens(int totalStars) {
        return BASE_DAILY_TOKENS + forStars(totalStars).dailyBonusTokens;
    }

    /**
     * Applies the 30 % monthly penalty (player did not place on the regional
     * leaderboard) and returns the new star count (minimum 0).
     */
    public static int applyMonthlyPenalty(int totalStars) {
        return Math.max(0, (int) (totalStars * MONTHLY_KEEP_FACTOR));
    }

    /** Returns a league by index, or the first league if the index is out of range. */
    public static League byIndex(int index) {
        if (index >= 0 && index < LEAGUES.size()) return LEAGUES.get(index);
        return LEAGUES.get(0);
    }
}
