package codergod1337.app1.mapping.model;

/**
 * Eine Spalte eines Tabellen-Eintrags (User 2026-10-10): der Name im JSON-Objekt der Zeile und die Adresse je Element
 * der Liste, z. B. {@code zn} und {@code fields.zn.text}. Als Liste in {@code ResponseMappingLine.tableColumnsJson}.
 */
public record ResponseMappingTableColumn(String name, String elementPath) {
}
