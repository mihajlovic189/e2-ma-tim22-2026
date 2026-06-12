package com.example.slagalicaapp.game.asocijacije;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulira lokalnu bazu podataka odnosno izvor asocijacija.
 */
public class AsocijacijeRepository {

    /**
     * Vraća nasumičnu asocijaciju iz baze.
     */
    public static Asocijacija getNasumicnaAsocijacija() {
        List<Asocijacija> sve = getPrimeriAsocijacija();
        int randomIndex = (int) (Math.random() * sve.size());
        return sve.get(randomIndex);
    }

    /**
     * Vraća listu sa primerima asocijacija.
     */
    public static List<Asocijacija> getPrimeriAsocijacija() {
        List<Asocijacija> asocijacije = new ArrayList<>();

        // Prva asocijacija
        Map<String, String> polja1 = new HashMap<>();
        polja1.put("A1", "Cev"); polja1.put("A2", "Topla"); polja1.put("A3", "Kupatilo"); polja1.put("A4", "Hladna");
        polja1.put("B1", "Godišnje"); polja1.put("B2", "Slobodno"); polja1.put("B3", "Prošlo"); polja1.put("B4", "Radno");
        polja1.put("C1", "Mala"); polja1.put("C2", "Kućna"); polja1.put("C3", "Ples"); polja1.put("C4", "Muzika");
        polja1.put("D1", "Strelice"); polja1.put("D2", "Casovnik"); polja1.put("D3", "Ruke"); polja1.put("D4", "Prsti");

        Map<String, String> kolone1 = new HashMap<>();
        kolone1.put("A", "Voda");
        kolone1.put("B", "Vreme");
        kolone1.put("C", "Zabava");
        kolone1.put("D", "Kazaljke");

        asocijacije.add(new Asocijacija(polja1, kolone1, "Sat"));

        // Druga asocijacija
        Map<String, String> polja2 = new HashMap<>();
        Map<String, String> kolone2 = new HashMap<>();

        polja2.put("A1", "Crveno"); polja2.put("A2", "Voće"); polja2.put("A3", "Sok"); polja2.put("A4", "Pita");
        kolone2.put("A", "Jabuka");

        polja2.put("B1", "Kora"); polja2.put("B2", "Stablo"); polja2.put("B3", "Grana"); polja2.put("B4", "List");
        kolone2.put("B", "Drvo");

        polja2.put("C1", "Dnevnik"); polja2.put("C2", "Sveska"); polja2.put("C3", "Knjiga"); polja2.put("C4", "Listanje");
        kolone2.put("C", "Papir");

        polja2.put("D1", "Hemijska"); polja2.put("D2", "Mastilo"); polja2.put("D3", "Grafit"); polja2.put("D4", "Pisanje");
        kolone2.put("D", "Olovka");

        asocijacije.add(new Asocijacija(polja2, kolone2, "Znanje"));

        // Treća asocijacija
        Map<String, String> polja3 = new HashMap<>();
        Map<String, String> kolone3 = new HashMap<>();
        polja3.put("A1", "Zlato"); polja3.put("A2", "Srebro"); polja3.put("A3", "Bronza"); polja3.put("A4", "Medalja");
        kolone3.put("A", "Olimpijada");
        polja3.put("B1", "Lopta"); polja3.put("B2", "Teren"); polja3.put("B3", "Sudija"); polja3.put("B4", "Gol");
        kolone3.put("B", "Fudbal");
        polja3.put("C1", "Reketi"); polja3.put("C2", "Mrežica"); polja3.put("C3", "Loptica"); polja3.put("C4", "Stoni");
        kolone3.put("C", "Tenis");
        polja3.put("D1", "Trčanje"); polja3.put("D2", "Skok"); polja3.put("D3", "Bacanje"); polja3.put("D4", "Staza");
        kolone3.put("D", "Atletika");

        asocijacije.add(new Asocijacija(polja3, kolone3, "Sport"));

        return asocijacije;
    }
}
