package codergod1337.app1.message.archive.model;

/**
 * Wie ein Anhang beim Archivieren dasteht (User 2026-10-14). Erst wird nur der Hash geprüft; gibt es die Bytes schon im
 * DMS, wird das ganze Dokument mit den Rechten des Archivierenden geholt, und daraus folgt der Stand.
 */
public enum MessageArchiveAttachmentState {

	/** Nicht im DMS: Die Dateiart ist Pflicht, vorgeschlagen über die Triggerwörter, ohne Treffer TEMP */
	NEW,

	/** Schon im DMS: keine Dateiart nötig, beim Archivieren kommen die Hooks der Nachricht dazu */
	MERGE,

	/** Schon im DMS mit wtf = ATTACHMENT_SIGNATURE: wird still an der Nachricht eingetragen, nie gelistet, bekommt nie Hooks */
	SIGNATURE,

	/** Schon im DMS mit wtf = ATTACHMENT_IGNORE: ausgegraut, wird nicht archiviert, kann aber trotzdem gewählt werden */
	IGNORED,

	/** Kein Datei-Anhang: eine angehängte Mail oder ein Verweis, ohne Bytes */
	NO_FILE,

	/** Die Bytes gibt es im DMS, aber der Archivierende bekommt das Dokument nicht: Fehler, Archivieren bleibt gesperrt */
	NO_ACCESS
}
