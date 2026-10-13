package codergod1337.app1.abas.model;

/**
 * Der Body von {@code POST /api/rest/v1/abas/artikelmappe/sternzeit}: das Objekt hinter der Sternzeit in einem Feld
 * des Artikels holen und mit dem Mapping seines Typs zerlegen.
 *
 * @param artikelNummer die id im Kern ARTIKEL
 * @param fieldName     das Feld des Dokuments, das die Sternzeit trägt, z. B. {@code yqmappqmpruefv}
 */
public record AbasArtikelmappeSternzeitRequest(String artikelNummer, String fieldName) {
}
