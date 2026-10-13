package codergod1337.app1.solr.dms.model;

import codergod1337.app1.solr.share.model.ShareTokenKind;
import java.time.Instant;
import java.util.Map;

/**
 * Was die Station „Detailansicht“ einem Empfänger ohne Konto gibt: die id, die Art des Scheins, ob der Download
 * dazu darf, bis wann und wie oft noch, und die Werte der freigegebenen Felder frisch aus Solr, nur diese.
 */
public record DmsSharedDocument(String id, ShareTokenKind kind, boolean binaryIncluded, Instant expiresAt, int usesLeft,
		Map<String, Object> fieldValues) {
}
