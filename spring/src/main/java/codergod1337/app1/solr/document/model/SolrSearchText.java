package codergod1337.app1.solr.document.model;

import java.util.ArrayList;
import java.util.List;
import org.apache.solr.client.solrj.util.ClientUtils;

/**
 * Die zerlegte Sucheingabe: aus dem, was der User tippt, wird hier der edismax-Query-String. Kein Suchservice reicht
 * Freitext roh an Solr durch (Vorgabe 2026-10-07): roh stünde die ganze Solr-Syntax offen, {@code *:*} wäre der
 * verbotene Vollscan, {@code readAcl:X} eine Abfrage an einem Feld außerhalb der qf, kaputte Syntax ein 400.
 *
 * Zwei Operatoren, unsere: ein führendes {@code +} macht ein Wort zur Pflicht, ein führendes {@code -} zum Ausschluss,
 * ohne Präfix bleibt es optional. Alles andere wird mit {@link ClientUtils#escapeQueryChars} zu Literaltext. Kein
 * Stemmer: Die Feldtypen des Basis-Schemas zerlegen nur nach Leerzeichen und Kleinschreibung, die Eingabe trifft, was
 * so im Feld steht, dazu die Präfixe über die Suggested-Zwillinge.
 *
 * Kernunabhängig wie die Rechterechnung, weil die Operatoren in jedem Kern dieselben sind. Kein Service, kein Solr:
 * nur Text rein, Text raus.
 *
 * @param searchable    es gibt etwas zu suchen: mindestens ein Wort ohne {@code -} mit {@link #MIN_CHARACTERS_OF_SEARCH_WORD}
 *                      Zeichen. Eine Eingabe nur aus Ausschlüssen wäre wieder ein Vollscan.
 * @param tooManyWords  mehr als {@link #MAX_WORDS} Wörter: Es wird nicht gesucht, der Aufrufer antwortet leer mit
 *                      Hinweis. Kein stilles Abschneiden, das lieferte Treffer zu einer Suche, die so niemand gestellt hat.
 * @param query         der fertige, entschärfte edismax-Query-String, null solange nicht searchable
 * @param positiveWords die Wörter ohne Präfix, Ausschlüsse ausgenommen: Damit begründet der Kern einen Treffer, nie mit
 *                      einem Ausschluss. Leer, solange nicht searchable.
 */
public record SolrSearchText(boolean searchable, boolean tooManyWords, String query, List<String> positiveWords) {

	/**
	 * Mehr Wörter nimmt keine Suche an, die Maske sperrt bei derselben Zahl (User 2026-10-07: sechs). Jedes Wort läuft
	 * über jedes Feld der qf, mit vielen Hooks und ihren Zwillingen sind das schnell dreißig Klauseln je Wort. Solr
	 * deckelt bei maxBooleanClauses hart (bei uns 3000 statt 1024), lange davor wird jedes weitere Wort nur teuer.
	 */
	public static final int MAX_WORDS = 6;

	/**
	 * Ab so vielen Zeichen zählt ein Wort als Suchwort, dieselbe Grenze wie die Suggested-Zwillinge (EdgeNGram ab drei
	 * Zeichen). Darunter wird nicht gesucht: Bei Millionen Dokumenten ist ein Präfix aus zwei Zeichen kein Suchen.
	 */
	public static final int MIN_CHARACTERS_OF_SEARCH_WORD = 3;

	private static final String MANDATORY_PREFIX = "+";
	private static final String EXCLUDE_PREFIX = "-";

	/** Ein Wort der Eingabe: sein Präfix ("", "+" oder "-") und der Text */
	private record Word(String prefix, String text) {
	}

	private static final SolrSearchText NOT_SEARCHABLE = new SolrSearchText(false, false, null, List.of());
	private static final SolrSearchText TOO_MANY_WORDS = new SolrSearchText(false, true, null, List.of());

	/** Zerlegt die Eingabe und baut den Query-String. null und Leeres sind schlicht nicht searchable. */
	public static SolrSearchText parse(String input) {
		String raw = input == null ? "" : input.strip();
		if (raw.isEmpty()) {
			return NOT_SEARCHABLE;
		}
		List<Word> words = new ArrayList<>();
		for (String token : raw.split("\\s+")) {
			String prefix = "";
			String text = token;
			if (token.startsWith(MANDATORY_PREFIX) || token.startsWith(EXCLUDE_PREFIX)) {
				prefix = token.substring(0, 1);
				text = token.substring(1);
			}
			// ein nacktes + oder - ist kein Wort: überlesen, nicht zählen
			if (text.isEmpty()) {
				continue;
			}
			words.add(new Word(prefix, text));
		}
		if (words.isEmpty()) {
			return NOT_SEARCHABLE;
		}
		if (words.size() > MAX_WORDS) {
			return TOO_MANY_WORDS;
		}
		boolean searchable = words.stream().anyMatch(
				word -> !EXCLUDE_PREFIX.equals(word.prefix()) && word.text().length() >= MIN_CHARACTERS_OF_SEARCH_WORD);
		if (!searchable) {
			return NOT_SEARCHABLE;
		}
		StringBuilder query = new StringBuilder();
		List<String> positiveWords = new ArrayList<>();
		for (Word word : words) {
			if (!query.isEmpty()) {
				query.append(' ');
			}
			query.append(word.prefix()).append(ClientUtils.escapeQueryChars(word.text()));
			if (!EXCLUDE_PREFIX.equals(word.prefix())) {
				positiveWords.add(word.text());
			}
		}
		return new SolrSearchText(true, false, query.toString(), List.copyOf(positiveWords));
	}

}
