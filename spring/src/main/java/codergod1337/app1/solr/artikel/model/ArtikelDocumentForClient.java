package codergod1337.app1.solr.artikel.model;

import java.util.List;
import java.util.Map;

/**
 * Die Artikelmappe: das Dokument eines Artikels mit den Feldern, die der User lesen darf, und seine Familie, alle
 * Dokumente mit derselben Masternummer im Hook masterArtikelNummer (der Master trägt dort seine eigene Nummer, jeder
 * Slave die des Masters). Leer, wenn der Artikel keiner Familie angehört.
 */
public record ArtikelDocumentForClient(Map<String, Object> document, List<Map<String, Object>> masterGroup) {
}
