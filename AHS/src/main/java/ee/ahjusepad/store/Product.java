package ee.ahjusepad.store;

/**
 * Ühe poe kataloogis kuvatava toote andmed.
 *
 * @param name        toote nimi, mida näidatakse tootekaardil
 * @param type        kategooria; väärtus peab sobima filtrite kategooriatega
 * @param detail      lühike tehniline kirjeldus, näiteks kütus või võimsus
 * @param price       kuvatav hind või tekst, mis suunab hinda küsima
 * @param image       tootekaardil kasutatava pildi veebiaadress
 * @param badge       valikuline märgis, näiteks „Uus“ või „Enimmüüdud“
 * @param description pikem tutvustus, mida saab kasutada toote lisavaates
 */
public record Product(
        String name,
        String type,
        String detail,
        String price,
        String image,
        String badge,
        String description) {
}
