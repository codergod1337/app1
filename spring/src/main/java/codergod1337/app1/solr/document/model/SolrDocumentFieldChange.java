package codergod1337.app1.solr.document.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Eine Feldänderung eines Atomic Updates: der Feldname in Solr, die Operation und ihre Werte. Die Werte sind String,
 * Boolean, Long, Double oder Instant, so wie der SolrDocumentChecker sie prüft; bei SET und INC höchstens einer, bei
 * SET_ALL, ADD und REMOVE beliebig viele. Keine Werte bei SET oder SET_ALL heißt: Das Feld wird aus dem Dokument
 * genommen.
 *
 * Die Fabrikmethoden sind der einzige Weg, der im Code lesbar sagt, was passiert: {@code set(...)}, {@code inc(...)},
 * {@code setAll(...)}, {@code add(...)}, {@code remove(...)}.
 *
 * @param fieldName der Name des Feldes oder Hooks im Kern
 * @param operation die Operation
 * @param values    die Werte, nie null, bei einwertigen Operationen höchstens einer
 */
public record SolrDocumentFieldChange(String fieldName, SolrDocumentFieldChangeOperation operation,
		List<Object> values) {

	public SolrDocumentFieldChange {
		values = values == null ? List.of() : List.copyOf(values);
	}

	/** Einwertig setzen. null nimmt das Feld aus dem Dokument. */
	public static SolrDocumentFieldChange set(String fieldName, Object value) {
		return new SolrDocumentFieldChange(fieldName, SolrDocumentFieldChangeOperation.SET,
				value == null ? List.of() : List.of(value));
	}

	/** Eine Zahl hochzählen, z. B. um 1 */
	public static SolrDocumentFieldChange inc(String fieldName, long amount) {
		return new SolrDocumentFieldChange(fieldName, SolrDocumentFieldChangeOperation.INC, List.of(amount));
	}

	/** Die ganze Liste ersetzen. null oder leer nimmt das Feld aus dem Dokument. */
	public static SolrDocumentFieldChange setAll(String fieldName, Collection<?> values) {
		return new SolrDocumentFieldChange(fieldName, SolrDocumentFieldChangeOperation.SET_ALL, copyOf(values));
	}

	/** Werte dazu, ohne Doppelte, Vorhandenes bleibt */
	public static SolrDocumentFieldChange add(String fieldName, Collection<?> values) {
		return new SolrDocumentFieldChange(fieldName, SolrDocumentFieldChangeOperation.ADD, copyOf(values));
	}

	/** Werte raus, alle anderen bleiben */
	public static SolrDocumentFieldChange remove(String fieldName, Collection<?> values) {
		return new SolrDocumentFieldChange(fieldName, SolrDocumentFieldChangeOperation.REMOVE, copyOf(values));
	}

	private static List<Object> copyOf(Collection<?> values) {
		if (values == null) {
			return List.of();
		}
		List<Object> copy = new ArrayList<>(values.size());
		for (Object value : values) {
			if (value != null) {
				copy.add(value);
			}
		}
		return copy;
	}

}
