package codergod1337.app1.dms.model;

import tools.jackson.databind.JsonNode;

/**
 * Eine Dateifreigabe im Kern DMS, wie die Maske sie schickt: Empfänger, die exakten Feldwerte der Auflösung
 * (JSON-Objekt, je Feld ein Wert oder eine Liste), optional ein Ablauf nach ISO 8601. Für die Vorschau fehlt der
 * Empfänger, dafür sagen page und size, welche Trefferseite die Maske sehen will. Das Format prüft der Controller, die
 * Regeln der Service.
 */
public record DmsNewDocumentGrantRequest(String granteeUsersGuid, JsonNode fieldValuesByFieldName, String validUntil,
		Integer page, Integer size) {
}
