package com.example.slagalicaapp.game.asocijacije;

import java.util.Map;

/**
 * Model koji predstavlja jednu asocijaciju.
 * Sadrži pojmove za sva polja, rešenja kolona i konačno rešenje.
 */
public class Asocijacija {
    /**
     * Mapa svih polja, gde je ključ oznaka polja (npr. "A1", "B2"), a vrednost pojam.
     */
    public final Map<String, String> polja;

    /**
     * Mapa rešenja kolona, gde je ključ kolona (npr. "A", "B", "C", "D"), a vrednost rešenje.
     */
    public final Map<String, String> resenjaKolona;

    /**
     * Konačno rešenje cele asocijacije.
     */
    public final String konacnoResenje;

    public Asocijacija(Map<String, String> polja, Map<String, String> resenjaKolona, String konacnoResenje) {
        this.polja = polja;
        this.resenjaKolona = resenjaKolona;
        this.konacnoResenje = konacnoResenje;
    }
}

