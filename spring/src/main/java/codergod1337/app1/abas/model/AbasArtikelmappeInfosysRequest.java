package codergod1337.app1.abas.model;

/**
 * Der Body von {@code POST /api/rest/v1/abas/artikelmappe/infosys}: einen hinterlegten Aufruf mit dem Anzeigeort
 * ARTIKELMAPPE für einen Artikel ausführen, die Platzhalter füllt das Backend aus dem Dokument des Artikels.
 *
 * @param artikelNummer  die id im Kern ARTIKEL
 * @param abasInfosysKey der Key des hinterlegten Aufrufs, z. B. {@code PRODLIST}
 */
public record AbasArtikelmappeInfosysRequest(String artikelNummer, String abasInfosysKey) {
}
