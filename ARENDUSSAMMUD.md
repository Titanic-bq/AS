# Järgmised arendussammud

See on kavandatud tööjärjekord, mitte juba valmis loend. Enne
kasutuselevõttu tuleks lahendada administreerimise ja
isikuandmete turvalisus.

## 1. Turvaline administraatori ligipääs

**Eesmärk:** võimaldada päringuid hallata nii, et kõrvalised inimesed neid ei
näe.

- Asendada lihtne `ADMIN_TOKEN`-päis sobiva autentimisega.
- Lisada rollipõhine ligipääs ja administraatori tegevuste logimine.
- Kasutada HTTPS-i ning hoida saladusi keskkonnas, mitte lähtekoodis.
- Piirata administraatori API võrgu- ja päringusageduse reeglitega.
- Kontrollida, et päringuandmeid tagastatakse ainult volitatud kasutajale.

**Valmis siis, kui:** kaitsmata päring saab keeldumise ning
administraator saab andmeid turvaliselt vaadata.

## 2. Päringute haldusvaade

**Eesmärk:** muuta saabunud päringutele vastamine mugavaks.

- Luua sisselogimise taga administraatori vaade.
- Kuvada päringu saabumise aeg, kontaktandmed ja huvipakkuv toode.
- Lisada otsing, kuupäeva järgi filtreerimine ning olekud, näiteks „Uus“,
  „Võtame ühendust“ ja „Suletud“.
- Lubada päringut eksportida ainult sobiva ligipääsuga kasutajal.

**Eeldus:** esmalt peab olema valmis administraatori autentimine.

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

- Saata ettevõtte aadressile teavitus uuest päringust.
- Saata kliendile kinnitus, kui e-posti teenus on seadistatud.
- Hoida e-posti teenuse võtmeid serveri keskkonnaseadetes.
- Käsitleda ajutisi saatmisvigu nii, et päring ise jääks salvestatuks ja
  teavitust saaks uuesti proovida.

**Valmis siis, kui:** testpäring salvestub ka e-posti teenuse tõrke korral ning
teavituse tulemus on jälgitav.

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

## 7. Ligipääsetavus ja jõudlus

**Eesmärk:** teha pood mugavaks erinevate seadmete ja abitehnoloogiatega
kasutajatele.

- Parandada Lighthouse'i leitud värvikontrasti probleemid.
- Kontrollida kogu lehte klaviatuuri ja ekraanilugejaga.
- Kontrollida piltide suurusi, fontide laadimist ning mobiilivaate toimimist.
- Käivitada Lighthouse pärast muudatusi ja talletada tulemused võrdlemiseks.

**Valmis siis, kui:** olulised kasutajateekonnad on klaviatuuriga läbitavad ja
uued Lighthouse'i mõõtmised kinnitavad parandusi.

## 8. Tootmiskeskkonda juurutamine ja seire

**Eesmärk:** käitada teenust usaldusväärselt väljaspool arendaja arvutit.

- Valida püsiv majutuskoht ning seadistada HTTPS ja domeen.
- Seadistada eraldi test- ja tootmiskeskkond.
- Lisada vealogimine, tervisekontrolli jälgimine ja varunduse seire.
- Kirjeldada versiooni uuendamise ning tagasipööramise protseduur.
- Hoida päringud, saladused ja varukoopiad veebiserveri avalikust kaustast
  väljas.

**Valmis siis, kui:** teenust saab uuendada ja taastada dokumenteeritud
protseduuriga ning tõrked on märgatavad.

## Soovituslik järjekord

1. Testid ja rakenduse käivitamise korratavus.
2. Administraatori autentimine ja andmekaitse.
3. Andmebaas, varundus ning säilitustähtajad.
4. Päringute haldusvaade.
5. E-posti teavitused.
6. Toodete administreerimine ja tõlked.
7. Ligipääsetavus, jõudlus ning tootmiskeskkonda juurutamine.

Väikese demo puhul saab samme teha järk-järgult. Enne avalikku kasutuselevõttu,
on kohustuslik teha turvalise autentimise, HTTPS-i, varundamise ega
isikuandmete halduse samme.
