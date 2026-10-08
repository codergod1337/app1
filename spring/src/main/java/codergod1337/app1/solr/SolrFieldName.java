package codergod1337.app1.solr;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Was bei uns ein Solr-Feldname sein darf. Der key eines Hooks ist genau das: der Name des Feldes im Kern, keine
 * eigene Key-Regel. Dieselbe Regel steht im Frontend in components/solrFieldNameRule.ts.
 */
public final class SolrFieldName {

	public static final int MAX_LENGTH = 100;

	/** Hängt Solr an jedes Feld, das ein Suggest-Gegenstück bekommt: artikelNummer → artikelNummerSuggested */
	public static final String SUGGEST_SUFFIX = "Suggested";

	/** Buchstabe am Anfang, dann Buchstaben, Ziffern und _. Solr selbst erlaubt nicht mehr. */
	private static final Pattern VALID = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

	/**
	 * Namen, die niemand von Hand vergibt: id legt der Code in jedem Kern an (eine Zeile ohne Kern in solr_field, der
	 * Initialisierer schreibt sie am Namen vorbei), _version_ und _root_ sind Solrs eigene Felder. Kein Hook und kein
	 * neues Feld eines Admins darf so heißen.
	 */
	private static final Set<String> RESERVED = Set.of("id", "_version_", "_root_");

	private SolrFieldName() {
	}

	/**
	 * true, wenn der Text ein Feldname sein darf: Buchstabe am Anfang, dann Buchstaben, Ziffern und _, nicht auf _
	 * endend (vorne und hinten _ ist bei Solr reserviert), nicht auf Suggested endend (das Gegenstück legt das Backend
	 * selbst an), nicht id und kein Solr-eigenes Feld, höchstens 100 Zeichen. {@code null} ist kein gültiger Feldname.
	 */
	public static boolean isValid(String fieldName) {
		return fieldName != null && fieldName.length() <= MAX_LENGTH && VALID.matcher(fieldName).matches()
				&& !fieldName.endsWith("_") && !fieldName.endsWith(SUGGEST_SUFFIX) && !RESERVED.contains(fieldName);
	}

}
