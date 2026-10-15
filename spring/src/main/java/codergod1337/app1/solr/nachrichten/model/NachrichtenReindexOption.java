package codergod1337.app1.solr.nachrichten.model;

/**
 * Was ein Lauf über alle Nachrichten nachzieht (User 2026-10-14). Jede Option ist eine Prüfung (SolrReindexCheck), die
 * SolrDocumentNachrichtenService.reindexNachrichtenDocuments baut; eine neue Option ist ein neuer Wert hier und eine
 * neue Prüfung dort.
 */
public enum NachrichtenReindexOption {

	/** readAcl und writeAcl neu aus der Nachrichtenart der Nachricht, wie sie jetzt in Postgres steht */
	ACLS_FROM_MESSAGE_SUB_CLASS

}
