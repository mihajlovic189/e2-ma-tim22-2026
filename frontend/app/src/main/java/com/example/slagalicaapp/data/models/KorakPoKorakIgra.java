package com.example.slagalicaapp.data.models;

public class KorakPoKorakIgra {
    public String getKonacnoResenje() {
        return konacnoResenje;
    }

    public void setKonacnoResenje(String konacnoResenje) {
        this.konacnoResenje = konacnoResenje;
    }

    String konacnoResenje;

    public String[] getKoraci() {
        return koraci;
    }

    public void setKoraci(String[] koraci) {
        this.koraci = koraci;
    }

    String[] koraci;

    public KorakPoKorakIgra(String resenje, String... koraci) {
        this.konacnoResenje = resenje;
        this.koraci = koraci;
    }
}
