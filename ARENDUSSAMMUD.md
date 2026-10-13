# Järgmised arendussammud

Siin on kirjas valminud arendussammud ning poe järgmised tööd.

## 1. Turvaline administraatori ligipääs

**Eesmärk:** võimaldada päringuid hallata nii, et kõrvalised inimesed neid ei
näe.

Autentimine, rollid ja esimene haldusvaade on rakendatud:

- Kasutajate paroolid hoitakse ainult serveri keskkonnas või väljaspool
  lähtekoodihoidlat asuvas omaduste failis ning neid kontrollitakse PBKDF2
  räsi abil.
- Sisselogimine väljastab juhusliku, ajaliselt piiratud
  `HttpOnly`/`SameSite=Strict` küpsise ning sessiooniga seotud CSRF-tõendi.

- `ADMIN` roll näeb kõiki päringuandmeid. `VIEWER` roll näeb ainult saabumise
  aega ja huvipakkuvat toodet.
- `/admin` pakub eestikeelset sisselogimis- ja päringute vaatamise lehte.
- Lisatud on sisselogimiskatsete piiramine ning admini sisselogimise,
  väljalogimise ja päringute vaatamise auditlogi.
- Sisselogimine ja väljalogimine nõuavad sobivat Origin-päist; väljalogimisel
  kontrollitakse lisaks sessiooni CSRF-tõendit.
- Server kuulab ainult loopback-aadressil. Lisatud on Caddy puhverserveri näidis
  failis `AHS/deployment/Caddyfile`; see ei seadista veel päris serverit.

Päris avalikuks kasutuseks on vaja enda domeeni ja serverit. Suuna domeeni DNS
serverile, ava tulemüüris pordid 80 ja 443, paigalda Caddy ning seadista
`AHS/deployment/Caddyfile` järgi HTTPS-puhverserver. Caddy väljastab ja uuendab
sertifikaadid automaatselt. Rakendus peab jääma kuulama aadressil
`127.0.0.1:8080`; Caddy suunab päringud sealt edasi.

Sea serveri keskkonda järgmised väärtused:

```powershell
$env:PORT = '8080'
$env:BIND_ADDRESS = '127.0.0.1'
$env:ADMIN_COOKIE_SECURE = 'true'
$env:ADMIN_USERS_FILE = 'C:\etc\ahjusepad\admin-users.properties'
$env:DOMAIN = 'pood.example.ee'
```

Muuda `DOMAIN` enda domeeniks ning käivita Caddy samast terminalist:

```powershell
caddy run --config .\deployment\Caddyfile
```

Päris serveris käivita Java rakendus ja Caddy teenustena, et need
taaskäivitamisel uuesti käivituksid.

Kasutajafaili näidis on `AHS/deployment/admin-users.properties.example`.
Kopeeri see turvalisse asukohta, muuda näidisparoolid unikaalseteks vähemalt
16-märgilisteks saladusteks ning piira faili õigused ainult serveri
teenusekasutajale (Linuxis näiteks `chmod 600`). Ära lisa päris
`admin-users.properties` faili Git-hoidlasse; see on `.gitignore`-is.
`ADMIN_USERS_FILE` ning `ADMIN_USERNAME`/`ADMIN_PASSWORD` seadistusi ei tohi
koos kasutada. Üksiku konto jaoks võib jätkata keskkonnamuutujate kasutamist.
Kasutaja lisamiseks või eemaldamiseks muuda seadistusfaili ja käivita Java
server uuesti; olemasolevad sessioonid kehtivad ainult kuni serveri
taaskäivitamiseni või nende aegumiseni.

Administraatori lõpp-punktid:

- `POST /api/admin/login` — vormiväljad `username` ja `password`;
- `GET /api/admin/session` — kontrollib sessiooni ning väljastab CSRF-tõendi;
- `POST /api/admin/logout` — nõuab `Origin`- ja `X-CSRF-Token` päiseid;
- `GET /api/admin/inquiries` — tagastab rollile lubatud päringuandmed.

Administraatori API on välja lülitatud, kui kasutajafail ega keskkonnamuutujad
pole määratud. Kasutajafailis on kasutaja jaoks omadused `user.N.username`,
`user.N.password` ja `user.N.role`; indeksid algavad ühest ja on järjestikused.
Toetatud rollid on `ADMIN` ning `VIEWER`. Sisselogimise ja väljalogimise
Postmani päringutele lisa `Origin: https://<sinu-domeen>`; väljalogimisel lisa
ka sessiooni `csrfToken` väärtus päisesse `X-CSRF-Token`. Kohalikus HTTP-testis
kasuta sama hosti ja `http` skeemi.

Järgmised tööd on endiselt vajalikud:

- Seadistada Caddy ja Java rakendus päris domeeni ning serveri peal, enne kui
  haldusvaade avalikku internetti avatakse.

**Valmis siis, kui:** kaitsmata päring saab keeldumise ning
administraator saab andmeid turvaliselt vaadata.

## 2. Päringute haldusvaade

**Eesmärk:** muuta saabunud päringutele vastamine mugavaks.

- Administraatori vaade kuvab kuupäeva, kontakti, huvipakkuva toote ja sõnumi;
  vaatlejale kuvatakse ainult kuupäev ning huvipakkuv toode.
- Lisada otsing, kuupäeva järgi filtreerimine ning olekud, näiteks „Uus“,
  „Võtame ühendust“ ja „Suletud“.
- Lubada päringuid eksportida ainult `ADMIN` rolliga kasutajal.

**Eeldus:** autentimine, rollid ja lihtne päringute vaatamine on valmis.

## 3. Andmebaas, varundus ja andmete elutsükkel

**Eesmärk:** kaitsta päringuid kadumise eest ja hallata isikuandmeid.

- Asendada JSONL-fail andmebaasiga, kui rakendus liigub päris kasutusse või
  päringute hulk kasvab.
- Teha automaatsed varukoopiad ning aeg-ajalt kontrollida taastamist.
- Määrata, kui kaua päringuandmeid säilitatakse, ja lisada kustutamise
  töövoog.
- Piirata juurdepääsu andmefailidele ning varukoopiatele.

**Valmis siis, kui:** andmed on taastatavad, säilitustähtaeg on määratud ja
kustutamine toimib dokumenteeritud viisil.

## 4. Päringute e-posti teavitused

**Eesmärk:** anda ettevõttele ja päringu saatjale päringu saabumisest teada.

- Uue päringu teavitus saadetakse Formspree vormi kaudu Gmaili aadressile,
  mis on valitud Formspree Workflow e-posti tegevuses.
- Poe vorm salvestab päringu endiselt `data/inquiries.jsonl` faili ning saadab
  seejärel väljad Formspreele; Formspree tõrge logitakse ega kustuta salvestatud
  päringut.
- Kasutusel olev vorm: `https://formspree.io/f/xjygezjb`. Teise vormi jaoks
  seadista `FORMSPREE_ENDPOINT` serveri keskkonnas kujul
  `https://formspree.io/f/<vormi-id>`.
- Formspree ei vaja Gmaili parooli ega API-võtit. Teavituse saaja ja
  e-kirja mall seadistatakse Formspree juhtpaneeli Workflow vaates.
- Saata kliendile kinnitus, kui e-posti teenus on seadistatud.
- Hoida võimalike tulevaste e-posti teenuse võtmeid serveri keskkonnaseadetes.
- Käsitleda ajutisi saatmisvigu nii, et päring ise jääks salvestatuks ja
  teavituse tulemust saaks jälgida serveri logidest.

**Valmis siis, kui:** Formspree saab vormi kaudu uue päringu ning saadab
seadistatud Gmaili aadressile teavituse. Tee enne kasutuselevõttu päris
vormitest ja kontrolli ka Gmaili rämpsposti kausta.

## 5. Testid ja pidev kontroll

**Eesmärk:** leida vead enne, kui muudatus jõuab kasutajani.

- Lisada Java testid kataloogi filtreerimisele, JSON-kodeerimisele ja vormi
  valideerimisele.
- Lisada API integratsioonitestid edukatele ja vigastele päringutele.
- Kontrollida brauseris keelevahetust, otsingut, kategooriaid ja vormi.
- Lisada automaatne kompileerimine ning testide käivitamine enne muudatuste
  ühendamist.

**Valmis siis, kui:** põhifunktsioonidel on korratavad automaattestid ja
testid käivituvad ühe dokumenteeritud käsuga.

## 6. Toodete haldus ja keeleandmed

**Eesmärk:** muuta kataloogi uuendamine lihtsamaks ning vähendada tõlkevigade
riski.

- Hoida tooteteavet andmebaasis või valideeritud andmefailis, mitte ainult
  `Catalog.java` lähtekoodis.
- Luua toodete lisamise, muutmise ja peitmise töövoog.
- Hoida iga toote eestikeelsed ja ingliskeelsed nimed ning kirjeldused
  selgelt seotud väljadena.
- Koondada kategooriate sisemised võtmed ja nende tõlked ühte
  autoriteetsesse kohta.

**Valmis siis, kui:** kataloogi saab uuendada Java koodi muutmata ning mõlemad
keeled kuvavad sama tooteinfot.

## 7. Tootmiskeskkonda juurutamine ja seire

**Eesmärk:** käitada teenust usaldusväärselt väljaspool arendaja arvutit.

- Valida püsiv majutuskoht ning seadistada HTTPS ja domeen.
- Seadistada eraldi test- ja tootmiskeskkond.
- Lisada vealogimine, tervisekontrolli jälgimine ja varunduse seire.
- Kirjeldada versiooni uuendamise ning tagasipööramise protseduur.
- Hoida päringud, saladused ja varukoopiad veebiserveri avalikust kaustast
  väljas.

**Valmis siis, kui:** teenust saab uuendada ja taastada dokumenteeritud
protseduuriga ning tõrked on märgatavad.

Väikese demo puhul saab samme teha järk-järgult. Enne avalikku kasutuselevõttu,
on kohustuslik teha turvalise autentimise, HTTPS-i, varundamise ega
isikuandmete halduse samme.
