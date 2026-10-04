package codergod1337.app1.dms.model;

import tools.jackson.databind.JsonNode;

/**
 * Was ein User an einem DMS-Dokument ändern darf, wie die Bearbeiten-Maske es schickt. null heißt unverändert, ein
 * leerer Text heißt leeren. Alles andere am Dokument setzt das Backend.
 *
 * @param id                  der SHA-256 des Dokuments, Pflicht
 * @param fileName            neuer Dateiname, wird nach derselben Regel wie beim Upload normalisiert
 * @param fileIndex           der Index, z. B. „idx 3“
 * @param fileDescription     mehrsprachiges JSON wie überall
 * @param documentCreatedAt   Erstelldatum nach ISO 8601
 * @param fileSubClassKey     neue Dateiart: schreibt readAcl und writeAcl des Dokuments neu, braucht Schreibrolle an
 *                            der neuen Dateiart und eine freigeschaltete Endung
 * @param hookValuesByHookKey JSON-Objekt Hook-Key zu Werteliste. Ein Hook darin wird ersetzt, eine leere Liste leert
 *                            ihn, ein Hook, der fehlt, bleibt wie er ist
 * @param status              ein Status des Bereichs DMS, z. B. DELETED fürs weiche Löschen
 * @param documentLanguage    ein Code aus DmsDocumentLanguages, leer löscht die Sprache. Nur in Endungsgruppen mit
 *                            Sprache, sonst 400
 */
public record DmsSolrDocumentEditRequest(String id, String fileName, String fileIndex, String fileDescription,
		String documentCreatedAt, String fileSubClassKey, JsonNode hookValuesByHookKey, String status,
		String documentLanguage) {
}
