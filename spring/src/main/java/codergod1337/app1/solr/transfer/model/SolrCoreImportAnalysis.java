package codergod1337.app1.solr.transfer.model;

import java.util.List;

/**
 * Was die Prüfung einer Import-Datei über sie weiß (User 2026-10-14): wie viele Dokumente, ob die Datei vollständig
 * ist, je Feld der Datei, ob der Kern es kennt und ob die Werte zum Typ passen, und welche Felder des Kerns fehlen. Die
 * Datei liegt danach als Temp-Datei auf dem Server, der Import liest sie über importId.
 *
 * @param documents            gelesene Dokumente, ohne Schlusszeile und ohne unlesbare
 * @param complete             die Schlusszeile des Exports ist da und nennt dieselbe Anzahl
 * @param declaredDocuments    die Anzahl laut Schlusszeile, null ohne Schlusszeile
 * @param unreadableDocuments  Zeilen, die kein JSON-Objekt sind
 * @param firstUnreadable      die erste davon mit Zeilennummer und Grund
 * @param fileFields           je Feld der Datei, nach Namen
 * @param missingCoreFields    die Felder des Kerns, die in keinem Dokument der Datei stehen
 */
public record SolrCoreImportAnalysis(long importId, String coreKey, String fileName, long documents, boolean complete,
		Long declaredDocuments, long unreadableDocuments, String firstUnreadable, List<FileField> fileFields,
		List<MissingCoreField> missingCoreFields) {

	/**
	 * Ein Feld der Datei.
	 *
	 * @param count              in so vielen Dokumenten hat es einen Wert
	 * @param sampleValue        der erste Wert, gekürzt
	 * @param coreFieldType      der Typ des gleichnamigen Felds im Kern, null wenn der Kern es nicht kennt
	 * @param essential          das gleichnamige Feld des Kerns ist essenziell
	 * @param skipped            übergangen: ein Suggest-Zwilling oder Solrs _version_, beides entsteht beim Schreiben neu
	 * @param invalidValues      so viele Werte passen nicht zum Typ des Kernfelds
	 * @param firstInvalidValue  der erste davon mit Zeilennummer
	 */
	public record FileField(String name, long count, String sampleValue, String coreFieldType, boolean essential,
			boolean skipped, long invalidValues, String firstInvalidValue) {
	}

	/** Ein Feld des Kerns, das in der Datei fehlt; ein essenzielles braucht eine Zuordnung, sonst startet kein Import */
	public record MissingCoreField(String name, String type, boolean essential) {
	}

}
