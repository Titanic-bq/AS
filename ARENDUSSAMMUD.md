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

Autentimine, rollid ja põhivaade on rakendatud ning nende kohalikku toimimist
on kontrollitud. Enne avalikku kasutuselevõttu on veel vaja:

- Seadistada Caddy ja Java rakendus päris domeeni ning serveri peal, enne kui
  haldusvaade avalikku internetti avatakse. Tootmises peab
  `ADMIN_COOKIE_SECURE=true` ning rakendus olema puhverserverile kättesaadav
  ainult loopback-aadressil.

## 2. Päringute haldusvaade

**Eesmärk:** muuta saabunud päringutele vastamine mugavaks.

- Valmis: admin näeb saabumise aega, kontaktandmeid, huvipakkuvat toodet ja
  sõnumit; vaatleja näeb ainult saabumise aega ja huvipakkuvat toodet.
- Järgmise täiendusena lisada otsing, kuupäeva järgi filtreerimine ning
  olekud, näiteks „Uus“, „Võtame ühendust“ ja „Suletud“.
- Vajaduse korral lisada päringute eksport ainult `ADMIN` rolliga kasutajale.

Autentimine, rollid ja lihtne päringute vaatamine on rakendatud. Haldusvaade
ei ole veel avalikku tootmiskeskkonda juurutatud.

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

- Rakendus saadab teavituse päringu Formspree vormi kaudu; Formspree saadab
  teavituse sellele Gmaili aadressile, mis on valitud Workflow
  e-posti tegevuses. Päris domeenilt saabumise ja Gmaili kohalejõudmise test
  on veel tegemata.
- Poe vorm salvestab päringu esmalt `data/inquiries.jsonl` faili ning saadab
  seejärel brauserist väljad Formspreele. Nii saab Formspree tavapärase
  veebivormi päringu koos brauseri päritoluinfoga, mitte anonüümse
  serveri-serveri päringuna.
- Kasutusel olev vorm: `https://formspree.io/f/xjygezjb`. Vormi vahetamisel
  muuda aadressi `AHS/src/main/resources/web/app.js` failis.
- Formspree ei vaja Gmaili parooli ega API-võtit. Teavituse saaja ja
  e-kirja mall seadistatakse Formspree juhtpaneeli Workflow vaates.
- Kui kohalik salvestamine õnnestub, aga Formspree päring ebaõnnestub, jääb
  kohalik päring alles ning veebileht annab teavituse saatmise tõrkest teada.
- Testida teavituse tegelikku kohalejõudmist avalikult domeenilt ning
  kontrollida Formspree Submissions- ja Spam-vaateid ning Gmaili rämpsposti.
  Kohalikult `localhost`-aadressilt saadetud test ei vasta domeenipiiranguga
  tootmistestile.
- Kliendile automaatse kinnituse saatmine pole veel lisatud.

Formspree ühenduse kood on rakendatud, kuid töö pole täielikult kinnitatud enne,
kui päris domeenilt saadetud test jõuab Formspree Submissions-vaatesse ja
teavitus Gmaili postkasti või rämpsposti.

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

Väikese demo puhul saab samme teha järk-järgult. Enne avalikku kasutuselevõttu
tuleb kindlasti seadistada HTTPS ja admini turvaseaded ning määrata päringute
varundamise, säilitamise ja kustutamise kord.
