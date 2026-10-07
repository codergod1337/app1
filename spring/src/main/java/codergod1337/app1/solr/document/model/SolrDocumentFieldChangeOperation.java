package codergod1337.app1.solr.document.model;

/**
 * Die eine Operation je Feldänderung eines Atomic Updates (Vorgabe 2026-10-07). Ein Update ist nie „neue Werte für das
 * Feld“, sondern je Feld eine ausdrückliche Operation. Einwertige Felder kennen SET und INC, Listen kennen SET_ALL, ADD
 * und REMOVE. Eine Listenoperation auf einem einwertigen Feld oder SET auf einer Liste ist ein Fehler, kein stilles
 * Raten (SolrDocumentChecker.checkDocumentFieldChanges).
 *
 * Der Schreibservice vergleicht nie selbst alt gegen neu: Wer ein Delta will, nennt die Werte, wer eine Liste will,
 * nennt die ganze Liste.
 */
public enum SolrDocumentFieldChangeOperation {

	/** Einwertig: den Wert setzen, ohne Wert nimmt Solr das Feld aus dem Dokument */
	SET("set", false),

	/** Zahl: um den Wert hochzählen, z. B. downloadCount */
	INC("inc", false),

	/** Liste: die ganze Liste ersetzen, leer nimmt das Feld aus dem Dokument */
	SET_ALL("set", true),

	/** Liste: die genannten Werte dazu, Vorhandenes bleibt, keine Doppelten. Immer add-distinct, nie add. */
	ADD("add-distinct", true),

	/** Liste: die genannten Werte raus, alle anderen bleiben */
	REMOVE("remove", true);

	private final String solrModifier;
	private final boolean forMultiValuedField;

	SolrDocumentFieldChangeOperation(String solrModifier, boolean forMultiValuedField) {
		this.solrModifier = solrModifier;
		this.forMultiValuedField = forMultiValuedField;
	}

	/** Der Name des Modifiers im Atomic Update von Solr */
	public String getSolrModifier() {
		return solrModifier;
	}

	/** true bei SET_ALL, ADD und REMOVE: nur auf einem multiValued-Feld erlaubt. false bei SET und INC: nur einwertig. */
	public boolean isForMultiValuedField() {
		return forMultiValuedField;
	}

}
