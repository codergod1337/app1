package codergod1337.app1.mapping.model;

/**
 * Ein Feld, das zusammen mit dem Typ die Antwort kennzeichnet, eine Zeile der JSON-Liste in
 * {@link ResponseMapping#getMatchConditionsJson()}: Das Mapping gilt nur, wenn an {@code path} der Wert {@code equals}
 * steht. Beispiel: {@code $.content.data.head.fields.grbez.value} gleich {@code Ansprechpartner}.
 */
public record ResponseMappingMatchCondition(String path, String equals) {
}
