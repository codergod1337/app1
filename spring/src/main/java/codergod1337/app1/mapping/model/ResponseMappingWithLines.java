package codergod1337.app1.mapping.model;

import java.util.List;

/**
 * Ein Mapping mit seinen Einträgen in Reihenfolge, so geht es zum Client und so kommt es zum Anlegen und Ändern zurück:
 * Das Gesamt-Mapping einer Antwort ist eine Einheit, der Admin speichert es nie halb.
 */
public record ResponseMappingWithLines(ResponseMapping responseMapping, List<ResponseMappingLine> lines) {
}
