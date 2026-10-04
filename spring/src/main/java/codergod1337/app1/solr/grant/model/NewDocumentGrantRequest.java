package codergod1337.app1.solr.grant.model;

import tools.jackson.databind.JsonNode;

/**
 * Eine Dateifreigabe, wie die Maske sie schickt: Empfänger, Kern, die exakten Feldwerte der Auflösung (JSON-Objekt, je
 * Feld ein Wert oder eine Liste), optional ein Ablauf nach ISO 8601. Für die Vorschau fehlt der Empfänger, dafür sagen
 * page und size, welche Trefferseite die Maske sehen will. Das Format prüft der Controller, die Regeln der Service.
 */
public record NewDocumentGrantRequest(String granteeUsersGuid, String coreKey, JsonNode fieldValuesByFieldName,
		String validUntil, Integer page, Integer size) {
}
