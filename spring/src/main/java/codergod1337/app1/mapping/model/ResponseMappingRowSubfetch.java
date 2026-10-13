package codergod1337.app1.mapping.model;

import java.util.Map;

/**
 * Ein Subfetch, der für eine Zeile eines TABLE-Eintrags zu holen ist (User 2026-10-13): Das Mapping hat die Eingaben
 * gegen das Zeilenelement schon aufgelöst, holen tut der Aufrufer, der ABAS kennt, und hängt die Werte der Unterantwort
 * über {@code ResponseMappingExecutionService.attachRowSubfetchResult} in diese Zeile.
 *
 * @param line        der Subfetch-Eintrag mit {@code tableLineKey}
 * @param tableKey    der Key des TABLE-Eintrags, dessen Zeile gemeint ist
 * @param rowIndex    die Zeile in der Liste der gelieferten Zeilen dieses Keys, ab 0 (Zeilen ohne Wert sind dort schon weg)
 * @param inputValues die Eingaben je Name, aus der Zeile oder statisch; ohne Wert null
 */
public record ResponseMappingRowSubfetch(ResponseMappingLine line, String tableKey, int rowIndex,
		Map<String, String> inputValues) {
}
