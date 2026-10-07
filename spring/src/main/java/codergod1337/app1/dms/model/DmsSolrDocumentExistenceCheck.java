package codergod1337.app1.dms.model;

/**
 * Die Antwort des Existenzchecks (Vorgabe 2026-10-07): Gibt es diese Bytes schon im DMS? Nur id, Dateiart und Status,
 * nie mehr, ohne Rechteprüfung. Damit entscheidet die Maske vor dem Upload: Merge nur bei gleicher Dateiart und Status
 * ACTIVE oder ARCHIVED, sonst lehnt der Upload ab.
 */
public record DmsSolrDocumentExistenceCheck(boolean exists, String id, String fileSubClassKey, String status) {

	public static DmsSolrDocumentExistenceCheck missing() {
		return new DmsSolrDocumentExistenceCheck(false, null, null, null);
	}

}
