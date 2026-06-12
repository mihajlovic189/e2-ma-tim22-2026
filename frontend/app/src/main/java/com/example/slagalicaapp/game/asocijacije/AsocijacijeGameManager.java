package com.example.slagalicaapp.game.asocijacije;

import android.os.CountDownTimer;

import java.util.HashMap;
import java.util.Map;

/**
 * Klasa koja upravlja stanjem igre Asocijacije.
 */
public class AsocijacijeGameManager {

    private final Asocijacija trenutnaAsocijacija;
    private final GameCallback callback;

    // Stanje: da li je polje/kolona otvorena
    private final Map<String, Boolean> otvorenaPolja = new HashMap<>();
    private final Map<String, Boolean> reseneKolone = new HashMap<>();
    private boolean igraZavrsena = false;

    private CountDownTimer timer;
    private static final long POCETNO_VREME_MS = 250000; // 250 sekundi

    public interface GameCallback {
        void naVremeAzurirano(int preostaleSekunde);
        void naPoljeOtvorio(String polje, String pojam);
        void naKolonaResena(String kolona, String resenje);
        void naIgraZavrsena(boolean pobeda, String konacnoResenje);
    }

    public AsocijacijeGameManager(Asocijacija asocijacija, GameCallback callback) {
        this.trenutnaAsocijacija = asocijacija;
        this.callback = callback;
        inicijalizujStanje();
    }

    private void inicijalizujStanje() {
        for (String polje : trenutnaAsocijacija.polja.keySet()) {
            otvorenaPolja.put(polje, false);
        }
        for (String kolona : trenutnaAsocijacija.resenjaKolona.keySet()) {
            reseneKolone.put(kolona, false);
        }
    }

    public void pokreniIgru() {
        if (timer != null) {
            timer.cancel();
        }
        timer = new CountDownTimer(POCETNO_VREME_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (callback != null) {
                    callback.naVremeAzurirano((int) (millisUntilFinished / 1000));
                }
            }

            @Override
            public void onFinish() {
                if (!igraZavrsena) {
                    zavrsiIgru(false);
                }
            }
        }.start();
    }

    public void zaustaviIgru() {
        if (timer != null) {
            timer.cancel();
        }
    }

    public void otvoriPolje(String polje) {
        if (igraZavrsena) return;

        Boolean otvoreno = otvorenaPolja.get(polje);
        if (otvoreno != null && !otvoreno) {
            otvorenaPolja.put(polje, true);
            String pojam = trenutnaAsocijacija.polja.get(polje);
            if (callback != null) {
                callback.naPoljeOtvorio(polje, pojam);
            }
        }
    }

    public boolean probajKolonu(String kolona, String unos) {
        if (igraZavrsena) return false;

        Boolean resena = reseneKolone.get(kolona);
        if (resena != null && resena) {
            return true; // Već rešena
        }

        String tacnoResenje = trenutnaAsocijacija.resenjaKolona.get(kolona);
        if (tacnoResenje != null && uporediResenja(tacnoResenje, unos)) {
            reseneKolone.put(kolona, true);

            // Otvori sva polja u koloni koja nisu otvorena (npr. kolona "A" -> "A1", "A2", "A3", "A4")
            for (int i = 1; i <= 4; i++) {
                String polje = kolona + i;
                if (!otvorenaPolja.getOrDefault(polje, false)) {
                    otvoriPolje(polje);
                }
            }

            if (callback != null) {
                callback.naKolonaResena(kolona, tacnoResenje);
            }
            return true;
        }
        return false;
    }

    public boolean probajKonacno(String unos) {
        if (igraZavrsena) return false;

        if (uporediResenja(trenutnaAsocijacija.konacnoResenje, unos)) {
            // Ako je konačno tačno, automatski se otvaraju sve kolone koje nisu
            for (String kolona : trenutnaAsocijacija.resenjaKolona.keySet()) {
                if (!reseneKolone.getOrDefault(kolona, false)) {
                    probajKolonu(kolona, trenutnaAsocijacija.resenjaKolona.get(kolona));
                }
            }
            zavrsiIgru(true);
            return true;
        }
        return false;
    }

    /**
     * Pomoćna metoda za upoređivanje unosa otporna na case i praznine.
     */
    private boolean uporediResenja(String tacno, String unos) {
        if (tacno == null || unos == null) return false;
        String t = tacno.trim().toLowerCase();
        String u = unos.trim().toLowerCase();
        return t.equals(u);
    }

    private void zavrsiIgru(boolean pobeda) {
        igraZavrsena = true;
        zaustaviIgru();
        if (callback != null) {
            callback.naIgraZavrsena(pobeda, trenutnaAsocijacija.konacnoResenje);
        }
    }
}

