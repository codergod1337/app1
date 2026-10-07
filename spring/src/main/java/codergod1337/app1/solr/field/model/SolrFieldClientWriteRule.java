package codergod1337.app1.solr.field.model;

/**
 * Wer ein Feld setzt, die Schreibregel je Feld (Vorgabe 2026-10-07). Nur in unserer Felddefinition, nie in Solr: Der
 * Schema-Abgleich ignoriert sie, die Schreibmechanik liest sie aus den Stammdaten, die Masken auch. Schickt der Client
 * ein Feld, das er nicht setzen darf, wird es ignoriert, nicht abgelehnt. Das Backend setzt seine Felder nach denen des
 * Clients, ein mitgeschickter Wert wird überschrieben. Die Sonderprüfungen (Pflicht, Werteliste, Prüfung gegen die
 * Datei) bleiben Code des Kerns.
 */
public enum SolrFieldClientWriteRule {

	/** Der Client setzt das Feld beim Anlegen und ändert es beim Bearbeiten, z. B. fileDescription, status, jeder Hook */
	CLIENT_SETS_AND_CHANGES,

	/** Der Client setzt das Feld nur beim Anlegen, danach wird es ignoriert, z. B. versionChain, fileSizeBytes */
	CLIENT_SETS_ONCE,

	/**
	 * Nie vom Client, der Schreibservice des Kerns setzt es selbst, z. B. sftpPath, readAcl, writeAcl, uploader. Standard
	 * für neue Felder und fest für die eingebauten id, cursorDate und userGrantedAccess.
	 */
	BACKEND_ONLY;

	/** Darf der Client das Feld beim Anlegen mitgeben? */
	public boolean isClientAllowedToSetOnCreate() {
		return this != BACKEND_ONLY;
	}

	/** Darf der Client das Feld beim Bearbeiten ändern? */
	public boolean isClientAllowedToChangeOnUpdate() {
		return this == CLIENT_SETS_AND_CHANGES;
	}

}
