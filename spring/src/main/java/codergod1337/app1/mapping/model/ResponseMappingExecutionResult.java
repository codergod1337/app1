package codergod1337.app1.mapping.model;

import java.util.List;
import java.util.Map;

/**
 * Das Ergebnis, wenn ein Gesamt-Mapping gegen eine Antwort gelaufen ist: die Werte je Key, aus denen der Schreibweg
 * des Ziels sein Dokument baut. Jeder Wert ist eine Liste, ein Einzelwert eine Liste mit einem Element; Lücken einer
 * Liste sind null, damit die Listen eines Mappings zeilengleich bleiben; eine Tabelle je Zeile ein JSON-Objekt als Text.
 *
 * @param responseMappingKey das gewählte Mapping
 * @param responseObjectType der Typ der Antwort
 * @param targetKey          das Ziel des Mappings, SOLR_<KERN>, null bei einem Mapping nur für Subfetches
 * @param primaryIdKey       der Key des Eintrags mit der Rolle PRIMARY, null wenn keiner
 * @param valuesByKey        die Werte je Key in der Reihenfolge der Einträge; Kaskade schon aufgelöst
 * @param subfetchLines      die Subfetch-Einträge des Mappings für den Kopf der Antwort: Das Mapping holt nichts, der
 *                           Aufrufer, der ABAS kennt, holt sie und legt die Werte der Unterantworten dazu
 * @param rowSubfetches      die Subfetches je Zeile eines TABLE-Eintrags (User 2026-10-13), Eingaben schon aufgelöst;
 *                           der Aufrufer holt sie und hängt die Werte der Unterantwort in die Zeile
 * @param warnings           was beim Lauf nicht ging, ohne den Lauf zu stoppen: ein Subfetch ohne Wert oder ohne
 *                           Antwort, mit Key und Grund (wie syncFehler der Vorlage)
 */
public record ResponseMappingExecutionResult(String responseMappingKey, String responseObjectType, String targetKey,
		String primaryIdKey, Map<String, List<Object>> valuesByKey, List<ResponseMappingLine> subfetchLines,
		List<ResponseMappingRowSubfetch> rowSubfetches, List<String> warnings) {
}
