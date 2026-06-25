package com.example.slagalicaapp.game.korakpokorak;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class KorakPoKorakRepository {

    private static final Random random = new Random();

    public static KorakPoKorakItem getRandomItem() {
        List<KorakPoKorakItem> items = getItems();
        return items.get(random.nextInt(items.size()));
    }

    public static List<KorakPoKorakItem> getItems() {
        return Arrays.asList(
                new KorakPoKorakItem("TENIS", Arrays.asList(
                        "Igra se na terenu",
                        "Koriste se reketi",
                        "Loptice su žute",
                        "Postoje gemovi i setovi",
                        "Igra se 1 na 1 ili 2 na 2",
                        "Wimbledon je najpoznatiji turnir",
                        "Novak Đoković je srpski prvak"
                )),
                new KorakPoKorakItem("FUDBAL", Arrays.asList(
                        "Najgledaniji sport na svetu",
                        "Igra se loptom",
                        "Na terenu je 11 igrača po timu",
                        "Golman brani mrežu",
                        "Traje 90 minuta",
                        "Svetsko prvoenstvo se održava svake 4 godine",
                        "Srbija ima svoju reprezentaciju"
                )),
                new KorakPoKorakItem("KNJIGA", Arrays.asList(
                        "Pravi se od papira",
                        "Sadrži stranice",
                        "Ima naslov i autora",
                        "Čita se",
                        "Čuva se u biblioteci",
                        "Može biti roman, poezija ili naučna",
                        "Gutenberg je izumeo štampu"
                )),
                new KorakPoKorakItem("MORE", Arrays.asList(
                        "Ima slanu vodu",
                        "Pokriva 70% Zemljine površine",
                        "U njemu žive ribe i morske životinje",
                        "Postoje talasi i plima",
                        "Brodovi plove po njemu",
                        "Jadransko more je kod Srbije blizu",
                        "Mediteran je poznato more"
                )),
                new KorakPoKorakItem("POZORIŠTE", Arrays.asList(
                        "Umetnost javnog nastupa",
                        "Glumci igraju uloge",
                        "Postoje pozornica i gledalište",
                        "Pišu se drame i komedije",
                        "Šekspir je najpoznatiji dramatičar",
                        "Narodno pozorište je u Beogradu",
                        "Publika aplauzom nagrađuje predstavu"
                )),
                new KorakPoKorakItem("MUZIKA", Arrays.asList(
                        "Stvara se zvukom",
                        "Koriste se instrumenti",
                        "Ima ritam i melodiju",
                        "Postoje note i partiture",
                        "Može biti klasična, rok ili pop",
                        "Pevač i bend nastupaju na koncertu",
                        "Betoven je kompozitor"
                )),
                new KorakPoKorakItem("PLANINA", Arrays.asList(
                        "Visoki deo terena",
                        "Na vrhu može biti sneg",
                        "Planinari je penju",
                        "U njoj žive medvedi i vukovi",
                        "Kopanik je planinski centar u Srbiji",
                        "Everest je najviša na svetu",
                        "Skijaši je vole zimi"
                )),
                new KorakPoKorakItem("KOMPJUTER", Arrays.asList(
                        "Elektronski uređaj",
                        "Ima ekran i tastaturu",
                        "Koristi se za rad i zabavu",
                        "Radi na struju",
                        "Čuva podatke",
                        "Ima operativni sistem",
                        "Internet se koristi na njemu"
                ))
        );
    }
}
