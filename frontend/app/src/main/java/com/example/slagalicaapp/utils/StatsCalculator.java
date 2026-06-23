package com.example.slagalicaapp.utils;

import java.util.HashMap;
import java.util.Map;

public class StatsCalculator {

    public static Map<String, Integer> obradiKrajPartije(boolean samJaPobednik, int mojSkor, int trenutneZvezde) {
        int bodovneZvezde = mojSkor / 40;
        int razlikaZvezda;

        if (samJaPobednik) {
            razlikaZvezda = 10 + bodovneZvezde;
        } else {
            razlikaZvezda = -10 + bodovneZvezde;
        }

        int finalneZvezde = trenutneZvezde + razlikaZvezda;
        if (finalneZvezde < 0) {
            finalneZvezde = 0;
        }

        int stariTokeniIzZvezda = trenutneZvezde / 50;
        int noviTokeniIzZvezda = finalneZvezde / 50;
        int nagradniTokeni = Math.max(0, noviTokeniIzZvezda - stariTokeniIzZvezda);

        Map<String, Integer> epilog = new HashMap<>();
        epilog.put("finalStars", finalneZvezde);
        epilog.put("rewardTokens", nagradniTokeni);
        epilog.put("starDifference", razlikaZvezda);
        return epilog;
    }
}