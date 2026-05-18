package com.example.slagalicaapp.game.skocko;

import java.util.Random;

/**
 * Šest simbola koji se koriste u igri Skočko.
 *
 * Redosled odgovara specifikaciji:
 * skočko, kvadrat, krug, srce, trougao, zvezda.
 *
 * Enum je čista poslovna logika - bez Android zavisnosti.
 */
public enum SkockoSymbol {
    SKOCKO,
    KVADRAT,
    KRUG,
    SRCE,
    TROUGAO,
    ZVEZDA;

    /**
     * Vraća nasumičan simbol koristeći prosleđen {@link Random}.
     * Prosleđivanje Random-a omogućava deterministične testove
     * (seedovan Random => predvidljiv niz).
     */
    public static SkockoSymbol random(Random rng) {
        SkockoSymbol[] all = values();
        return all[rng.nextInt(all.length)];
    }

    /** Convenience overload sa internim Random-om (za produkciju). */
    public static SkockoSymbol random() {
        return random(new Random());
    }
}
