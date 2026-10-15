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

	/** Hängt Solr an jedes Feld, das ein Ngram-Gegenstück bekommt (User 2026-10-16): objekt → objektNgram */
	public static final String NGRAM_SUFFIX = "Ngram";

	/** Buchstabe am Anfang, dann Buchstaben, Ziffern und _. Solr selbst erlaubt nicht mehr. */
	private static final Pattern VALID = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

	/**
	 * Solrs eigene Felder, kein Hook und kein Feld darf so heißen. id gehört nicht dazu: Es ist ein normales Feld ohne
	 * Kern in solr_field, angelegt im Kern-Tab oder per Import wie jedes andere (User 2026-10-09).
	 */
	private static final Set<String> RESERVED = Set.of("_version_", "_root_");

	private SolrFieldName() {
	}

	/**
	 * true, wenn der Text ein Feldname sein darf: Buchstabe am Anfang, dann Buchstaben, Ziffern und _, nicht auf _
	 * endend (vorne und hinten _ ist bei Solr reserviert), nicht auf Suggested oder Ngram endend (die Gegenstücke legt
	 * das Backend selbst an), kein Solr-eigenes Feld, höchstens 100 Zeichen. {@code null} ist kein gültiger Feldname.
	 */
	public static boolean isValid(String fieldName) {
		return fieldName != null && fieldName.length() <= MAX_LENGTH && VALID.matcher(fieldName).matches()
				&& !fieldName.endsWith("_") && !fieldName.endsWith(SUGGEST_SUFFIX) && !fieldName.endsWith(NGRAM_SUFFIX)
				&& !RESERVED.contains(fieldName);
	}

}
