package codergod1337.app1.dms.model;

/**
 * Warum die Suche nichts gefragt hat: Statt einer Abfrage kommt eine leere Trefferseite mit diesem Hinweis. Das Frontend
 * übersetzt ihn, das Backend schickt keinen Text.
 */
public enum DmsSearchHint {

	/** kein Suchwort mit drei Zeichen und kein Filter: ohne beides wird nie ins Leere gesucht */
	NO_SEARCH_WORD_AND_NO_FILTER,

	/** mehr Wörter als SolrSearchText.MAX_WORDS: Eingabe kürzen oder Filter setzen */
	TOO_MANY_SEARCH_WORDS

}
