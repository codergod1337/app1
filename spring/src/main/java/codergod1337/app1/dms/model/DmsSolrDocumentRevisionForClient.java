package codergod1337.app1.dms.model;

import java.time.Instant;
import java.util.List;

/**
 * Eine Zeile der Revisionsliste eines DMS-Dokuments, allein aus dem Register in Postgres, ohne das ZIP zu öffnen. Die
 * Zeile ist der gesicherte Stand und zugleich die Änderung, die ihn abgelöst hat: Zeitpunkt, User und Felder gehören zu
 * dieser Änderung.
 *
 * @param revisionNo                die Nummer des gesicherten Stands, 0 heißt: so wurde es hochgeladen
 * @param changedAt                 wann die Änderung lief
 * @param changedByUsersGuid        wer sie ausgelöst hat
 * @param changedByUsersDisplayName sein Anzeigename, sonst die guid
 * @param changedFieldNames         die Felder, deren Wert die Änderung wirklich geändert hat
 */
public record DmsSolrDocumentRevisionForClient(long revisionNo, Instant changedAt, String changedByUsersGuid,
		String changedByUsersDisplayName, List<String> changedFieldNames) {
}
