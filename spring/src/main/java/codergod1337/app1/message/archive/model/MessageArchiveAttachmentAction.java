package codergod1337.app1.message.archive.model;

/** Was der User beim Archivieren mit einem Anhang will (User 2026-10-14) */
public enum MessageArchiveAttachmentAction {

	/** An die Nachricht: neu mit der gewählten Dateiart und den Hooks, vorhanden per Merge mit den Hooks */
	ARCHIVE,

	/** Eine Signatur: als Bild (BILDER) mit wtf = ATTACHMENT_SIGNATURE ins DMS, ohne weitere Hooks, still an die Nachricht */
	SIGNATURE,

	/** Nie archivieren: neu als TEMP mit wtf = ATTACHMENT_IGNORE ins DMS, vorhanden bekommt das Flag; nicht an die Nachricht */
	IGNORE,

	/** Diesmal nichts tun */
	SKIP
}
