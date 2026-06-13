# Slagalica Kviz - Mobilna Aplikacija
Mobilna aplikacija po ugledu na kviz "Slagalica", realizovana kao projektni zadatak u okviru predmeta **Mobilne aplikacije** na usmerenju Računarstvo i automatika (školska godina 2025/26)
Aplikacija podržava igranje jedan na jedan, rangiranje i takmičenje igrača

## Status Projekta (Implementirane Funkcionalnosti)
U okviru prve dve kontrolne tačke (KT1 i KT2), uspešno je implementiran kompletan grafički korisnički interfejs (GUI), arhitektura aplikacije i sledeće osnovne funkcionalnosti:

### Autentifikacija i Korisnici
* Registracija i Logovanje: Unos podataka, verifikacija naloga putem linka na mejlu i resetovanje lozinke.
* Profil Korisnika: Prikaz osnovnih podataka (korisničko ime, avatar, liga, region, broj tokena/zvezda) i detaljna statistika uspešnosti po igrama.

### Implementirane Igre (Sve runda-po-runda funkcionalnosti)
1. Ko zna zna – 5 pitanja opšteg znanja sa ponuđenim odgovorima (10 bodova za tačan, -5 za netačan).
2. Spojnice – Povezivanje pojmova iz leve i desne kolone.
3. Asocijacije – Otvaranje polja i pogađanje kolona radi rešavanja konačnog pojma.
4. Skočko – Pogodak kombinacije od 4 znaka u 6 pokušaja.
5. Korak po korak – Pronalaženje zadatog pojma kroz maksimalno 7 koraka.
6. Moj Broj – Dobijanje traženog broja kombinovanjem 6 nasumičnih brojeva i operatora (podržan i Shake senzor za stopiranje).

### Notifikacije
* Implementiran sistem notifikacija, pregled istorije i filtriranje na pročitane/nepročitane.

## Tehnologije i Arhitektura
* Arhitektura: Troslojna arhitektura (jasno razdvojeni prezentacioni deo, poslovna logika i upravljanje podacima).
* Baza podataka: Firebase (korišćen za autentifikaciju i čuvanje podataka neophodnih za igre kao što su RTDB i Firestore).

## Pokretanje Aplikacije
Kako je fokus prve dve kontrolne tačke na stabilnosti runda-po-runda logike i interfejsa, u nastavku je detaljno uputstvo kako da se pokrene aplikacija, kako da se prebaci na fizičke uređaje i testira implementirani matchmaking sistem.

### 1. Otvaranje projekta u IDE-u
1. Klonirajte repozitorijum na računar:
   git clone https://github.com/mihajlovic189/e2-ma-tim22-2026.git
2. Otvorite Android Studio.
3. Izaberite opciju Open i selektujte koren (root) folder kloniranog projekta.
4. Sačekajte da se završi kompletan Gradle Sync i indeksiranje projekta.

### 2. Prebacivanje aplikacije na fizički Android telefon
Da biste aplikaciju testirali u realnim uslovima (što je neophodno za funkcionalnosti poput Shake senzora u igri Moj broj), preporučuje se pokretanje na fizičkim uređajima:

1. Uključivanje Developer opcija na telefonu:
   * Na Android telefonu idite u Settings -> About Phone.
   * Kliknite na Build Number 7 puta uzastopno dok se ne pojavi poruka "You are now a developer!".
   * Vratite se nazad u podešavanja, otvorite novu sekciju Developer Options i obavezno uključite USB Debugging.
2. Povezivanje sa računarom:
   * Povežite telefon sa računarom putem USB kabla. 
   * Na ekranu telefona će iskočiti prozor sa pitanjem "Allow USB debugging?" – štiklirajte "Always allow" i kliknite Allow.
3. Instalacija iz Android Studija:
   * U gornjem toolbar-u Android Studija, u padajućem meniju za izbor uređaja, selektujte vaš prepoznati fizički telefon umesto emulatora.
   * Kliknite na zeleno dugme Run. Android Studio će kompajlirati APK i automatski instalirati i pokrenuti aplikaciju na vašem telefonu.

## Protokol za Testiranje Funkcionalnosti (KT1 + KT2)
Nakon uspešnog pokretanja aplikacije na uređajima, testiranje implementiranih delova se vrši kroz sledeći scenario:

### Korak 1: Prvi susret i Autentifikacija
* Stranica za Registraciju/Login: Prilikom prvog paljenja, korisnika odmah dočekuje ekran za prijavu. Ovde se može izvršiti registracija novog naloga (unosom mejla, korisničkog imena, lozinke i odabirom regiona Srbije za koji igrač nastupa).
* Igranje kao gost (Neregistrovani igrač): Ukoliko ne želite da pravite nalog, aplikacija nudi opciju "Igraj kao gost". Neregistrovani igrač odmah dobija pristup igranju, ali bez vođenja lične statistike i napredovanja kroz lige.

### Korak 2: Profil i Praćenje Statistike
* Nakon logovanja sa registrovanim nalogom, otvara se matični ekran aplikacije gde korisnik može da poseti svoj Profil.
* Na profilu se može pratiti detaljno implementirana statistika uspešnosti za KT1 i KT2 (odnos pogođenih/promašenih pitanja u Ko zna zna, procenti uspešnosti po koracima u Korak po korak, uspešnost povezivanja u Spojnicama, itd.).

### Korak 3: Matchmaking i Zajednički Room (Igra 1 na 1)
Za testiranje same igre i mrežne sinhronizacije logike, potrebno je pokrenuti aplikaciju na dva različita telefona (ili na jednom fizičkom telefonu i jednom emulatoru) i ulogovati se sa dva različita naloga.
1. Pokretanje i Matchmaking: Na Telefonu A i Telefonu B istovremeno otvorite istu igru (na primer, obojica pritisnete na igru Spojnice).
   * U tom trenutku pozadinska poslovna logika pokreće proces matchmaking-a (pretrage slobodnih igrača u realnom vremenu).
2. Spajanje u Room:
   * Sistem automatski prepoznaje oba zahteva, spaja ta dva igrača (uređaja) i ubacuje ih u jedinstvenu zajedničku sobu (Room) kreiranu na Firebase-u.
3. Igranje jedan protiv drugog:
   * Igra počinje sinhronizovano na oba ekrana. Na primer, u Spojnicama, Telefon A započinje rundu i povezuje pojmove, dok Telefon B na svom ekranu vidi da je u toku runda protivnika. 
   * Nakon što Telefon A završi ili mu istekne vreme, Telefon B dobija svojih 30 sekundi da poveže preostale pojmove.
   * Ovaj identičan princip sinhronizovanog povezivanja u zajednički Room i runda-po-runda igranja uspešno je implementiran i funkcioniše za sve preostale igre (Ko zna zna, Asocijacije, Skočko, Korak po korak i Moj broj).
