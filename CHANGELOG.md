# Muudatuste ajalugu

Siin on kirjas Ahjusepa poe olulisemad valminud muudatused. See fail kirjeldab
tehtud tööd; tulevikuplaanid on eraldi failis
[ARENDUSSAMMUD.md](./ARENDUSSAMMUD.md).

## 2026-10-13

### Administraatori sisselogimine (esimene etapp)

- Asendati `X-Admin-Token` ligipääs keskkonnamuutujatest seadistatava
  kasutajanime ja parooliga.
- Lisati PBKDF2 paroolikontroll, piiratud juhuslikud sessioonid ja
  `HttpOnly`/`SameSite=Strict` küpsis ning sessioonipõhine CSRF-tõend.
- Lisati mitme konto seadistusfail ning `ADMIN` ja piiratud andmetega
  `VIEWER` roll.
- Kaitsti sisselogimist ja väljalogimist Origin-kontrolliga ning päringute API
  sessiooniga; lisati sisselogimiskatsete piirang ja admini tegevuste logimine.
- Lisati `/admin` haldusleht, kus administraator näeb kõiki päringuandmeid ja
  vaatleja ainult päringu saabumise aega ning huvipakkuvat toodet.
- Piirati serveri kuulamine loopback-aadressile, et vältida otse HTTP kaudu
  võrku avaldamist; väliseks HTTPS-juurdepääsuks tuleb kasutada puhverserverit.
- Lisati Caddy HTTPS-puhverserveri näidis ja jäeti päris domeeni/serveri
  seadistus juurutuskeskkonna tööks.
- Päris domeeni ja serveri HTTPS-juurutus on endiselt seadistamata.
- Ühendati poe päringu salvestamise järel Formspree vormiga
  `xjygezjb`, et saada vormis määratud ühele e-posti aadressile teavitus.
  Teavituse tõrge ei mõjuta päringu kohalikku salvestamist.

### Dokumentatsioon

- Lisati eestikeelne muudatuste ajalugu.
- Lisati eraldi nimekiri järgmistest arendussammudest.
- Dokumenteeriti `POST /api/inquiries` päringu saatmine, väljad ja vastused.

## 2026-10-12

### Eesti- ja ingliskeelne pood

- Lisati navigatsiooni eesti/inglise keele valik nii töölaua- kui mobiilivaates.
- Tõlgiti poe põhitekstid, tooted, filtrid ja päringuvorm eesti keelde.
- Keelevalik salvestatakse brauserisse, et see säiliks lehe uuesti avamisel.
- Otsing ja kategooriafiltrid jäeti API sisemiste ingliskeelsete väärtustega
  kooskõlla.

### Arendaja dokumentatsioon

- Kaardistati rakenduse Java- ja veebifailid, päringute käik ning API.
- Lisati juhised rakenduse käivitamiseks, toodete ja kategooriate muutmiseks
  ning levinumate probleemide kontrollimiseks.

### Jõudlus ja veebivarad

- Lisati reageerivad pildiallikad Unsplashist laaditavatele piltidele.
- Lisati kolmandate osapoolte pildi- ja fonditeenuste eellaadimise vihjed.
- Lisati staatiliste tekstiressursside gzip-pakkimine ning vahemälupäised.

### Kataloogi ja stiilide hooldus

- Koondati osa korduvatest CSS-reeglitest ja eemaldati tarbetuid deklaratsioone.
- CSS jäeti laiendatud ja loetavasse vormingusse.

## Varasemad muudatused

### Rakenduse käivitamine ja failid

- Seadistati VS Code Java projekt kasutama `src/main/resources` kausta, et
  veebifailid oleksid Java rakendusele kättesaadavad.
- Lisati `.gitignore`-i genereeritud Java väljund ning kohalik päringufail
  `data/inquiries.jsonl`.
- Korrastati HTML-i, CSS-i, JavaScripti ja Java lähtekoodi eestikeelseid
  selgitavaid kommentaare.

### Poe funktsioonid

- Staatiline veebileht teenindatakse Java HTTP-serverist.
- Tooted tulevad Java kataloogist API kaudu; olemas on kategooriafiltrid ja
  otsing.
- Konsultatsiooni-/hinnapäringuid valideeritakse ning salvestatakse
  `data/inquiries.jsonl` JSONL-faili.
- Lisati tervisekontrolli, kategooriate, toodete, päringute ning valikuline
  administraatori päringute API.
