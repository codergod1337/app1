package codergod1337.app1.mapping.model;

/**
 * Eine Eingabe eines Subfetch-Eintrags (User 2026-10-10): wo der Wert herkommt, entweder aus dem JSON der Antwort
 * ({@code path}, Adress-Grammatik des Mappings) oder statisch ({@code value}); beides leer heißt, der Name wird ohne
 * Wert gesendet. Beim Sternzeit-Fetch heißt die eine Eingabe {@code sternzeit}, beim Infosystem je Platzhalter der
 * Vorlage eine mit dessen Namen. Als Liste in {@code ResponseMappingLine.subfetchInputParametersJson}.
 */
public record ResponseMappingSubfetchInputParameter(String name, String path, String value) {

	/** Die Eingabe des Sternzeit-Fetchs */
	public static final String STERNZEIT_PARAMETER_NAME = "sternzeit";

}
