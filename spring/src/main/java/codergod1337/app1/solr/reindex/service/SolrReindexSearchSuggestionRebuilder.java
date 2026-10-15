package codergod1337.app1.solr.reindex.service;

import codergod1337.app1.solr.reindex.model.SolrReindexCheck;
import java.util.Map;
import java.util.Set;
import org.apache.solr.common.SolrInputDocument;

/**
 * Suche neu aufbauen (User 2026-10-14): jedes Dokument eines Kerns einmal neu schreiben, ohne etwas zu vergleichen.
 * Gestartet wird das nur von Hand, nachdem sich das Regelwerk der Suche geändert hat: ein Textfeld bekommt einen
 * anderen Typ (etwa name von text_edge auf text_ngram), ein vorhandenes Feld bekommt neu suggest, die Zerlegung eines
 * Typs ändert sich. Solr zerlegt einen Wert nur beim Schreiben; was davor im Index lag, behält die alten Einträge, bis
 * das Dokument neu geschrieben wird.
 *
 * Geladen wird nur die id, geschrieben nur cursorDate (setzt die Schleife). Bei diesem Atomic Update liest Solr alle
 * gespeicherten Werte des Dokuments, auch die aus docValues, und indexiert das ganze Dokument nach dem aktuellen Schema
 * neu: jedes Textfeld neu zerlegt, jeder Suggest-Zwilling über copyField neu befüllt. Ein Feld, das weder gespeichert
 * noch docValues noch Kopierziel ist, verlöre dabei seinen Wert; so ein Feld gibt es in keinem Kern, alle Felder sind
 * gespeichert (geprüft 2026-10-14). Ein Feld mit neuer Art der Ablage (string zu long, docValues an oder aus) lässt sich
 * so nicht umstellen, das heißt Kern leeren und neu befüllen.
 */
public class SolrReindexSearchSuggestionRebuilder implements SolrReindexCheck {

	@Override
	public Set<String> loadedFieldNames() {
		return Set.of();
	}

	@Override
	public boolean addChanges(Map<String, Object> document, SolrInputDocument update) {
		// nichts zu vergleichen, nichts eigenes zu setzen: schreiben heißt neu aufbauen
		return true;
	}

}
