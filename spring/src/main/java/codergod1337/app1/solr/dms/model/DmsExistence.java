package codergod1337.app1.solr.dms.model;

/**
 * Gibt es diese Bytes schon im DMS? Die id ist der SHA-256 der Bytes, dazu Dateiart und Status des vorhandenen
 * Dokuments, ohne Rechteprüfung: Die Uploadliste braucht das für die Kollisionserkennung, bevor sie etwas schickt.
 */
public record DmsExistence(boolean exists, String id, String fileSubClassKey, String status) {
}
