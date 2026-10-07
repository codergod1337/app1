package codergod1337.app1.dms.model;

import tools.jackson.databind.JsonNode;

/**
 * Was die Bearbeiten-Maske schickt (Vorgabe 2026-10-07): nur die Felder, die sich geändert haben, je Feld der Endwert,
 * wie er jetzt sein soll, kein Delta. Eine Liste (Hooks) kommt als fertige Liste, daraus wird SET_ALL. null, ein leerer
 * Text oder eine leere Liste leert das Feld. Felder, die der Client nicht ändern darf, werden ignoriert.
 *
 * @param id                     der SHA-256 des Dokuments, Pflicht
 * @param fieldValuesByFieldName JSON-Objekt, jedes Feld unter seinem Solr-Namen, Hooks flach wie Felder
 */
public record DmsSolrDocumentPatchRequest(String id, JsonNode fieldValuesByFieldName) {
}
