package codergod1337.app1.dms.model;

/**
 * Die Schreibvorgänge am Kern DMS, je Vorgang eine Methode im DmsSolrDocumentWriteService (Vorgabe 2026-10-07). Der
 * Name steht mit jedem Schreiben im Log, zusammen mit current user, id und Feldnamen.
 *
 * Ob ein Vorgang revisioniert wird, steht hier am Vorgang, nicht in der Schreibmethode (User 2026-10-08): Ein
 * revisionierter Vorgang sichert vor dem Schreiben den ganzen Stand des Dokuments als ZIP und zählt fileIdRevisionNo
 * hoch. Anlegen hat keinen Vorzustand, die Dateifreigabe hat ihr eigenes Register in Postgres und schreibt viele
 * Dokumente auf einmal. Ein neuer Vorgang kann ohne diese Entscheidung nicht entstehen, der Konstruktor verlangt sie.
 */
public enum DmsSolrDocumentWriteProcess {

	/** Upload neu: das ganze Dokument, readAcl und writeAcl aus der Dateiart */
	DMS_UPLOAD_CREATE(false),

	/** dieselben Bytes noch einmal hochgeladen: Hooks ADD, Beschreibung angehängt, sonst nichts */
	DMS_UPLOAD_MERGE_HOOKS(true),

	/** neue Fassung einer Versionskette: das ganze Dokument, Dateiart und ACLs vom Kopf der Kette */
	DMS_NEW_VERSION_CREATE(false),

	/** die bisherigen ACTIVE-Glieder der Kette auf ARCHIVED */
	DMS_NEW_VERSION_ARCHIVE_PREVIOUS(true),

	/** die Bearbeiten-Maske: nur die geschickten Felder, bei Dateiartwechsel ACLs neu */
	DMS_EDIT(true),

	/** Dateiartwechsel: jedes andere Glied der Kette bekommt dieselbe Dateiart und ACLs, je Glied mit eigener Revision */
	DMS_EDIT_FILE_SUB_CLASS_OF_CHAIN(true),

	/** weiches Löschen: status DELETED */
	DMS_SOFT_DELETE(true),

	/** eine Revision zurückladen: jedes Feld aus dem ZIP, der heutige Stand wird vorher selbst gesichert */
	DMS_REVISION_RESTORE(true),

	/**
	 * der Download-Zähler: downloadCount INC 1 nach jedem Download oder Viewer-Abruf, Buchhaltung des Backends, keine
	 * Änderung durch einen Menschen, deshalb ohne Revision, ohne Stempel und ohne Schreibcheck (die Leseprüfung hat der
	 * Download gemacht, User 2026-10-08)
	 */
	DMS_DOWNLOAD_COUNT(false),

	/** Dateifreigabe setzen: die guid des Empfängers ADD in userGrantedAccess jedes Treffers */
	DMS_DOCUMENT_GRANT_ADD_GRANTEE(false),

	/** Dateifreigabe entziehen oder abgelaufen: die guid des Empfängers REMOVE aus userGrantedAccess */
	DMS_DOCUMENT_GRANT_REMOVE_GRANTEE(false);

	private final boolean revisioned;

	DmsSolrDocumentWriteProcess(boolean revisioned) {
		this.revisioned = revisioned;
	}

	/** true: vor dem Schreiben der ganze Stand als ZIP, fileIdRevisionNo INC im selben Update */
	public boolean isRevisioned() {
		return revisioned;
	}

}
