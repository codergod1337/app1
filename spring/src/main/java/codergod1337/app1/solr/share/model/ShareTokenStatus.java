package codergod1337.app1.solr.share.model;

/**
 * Ob ein Abholschein noch gilt. EXPIRED durch Ablaufdatum, durch die letzte erlaubte Einlösung oder von Hand
 * zurückgezogen; die Zeile bleibt als History stehen, gelöscht wird nie (User 2026-10-09).
 */
public enum ShareTokenStatus {

	ACTIVE,

	EXPIRED

}
