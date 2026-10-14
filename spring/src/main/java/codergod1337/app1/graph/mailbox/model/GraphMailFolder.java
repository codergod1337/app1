package codergod1337.app1.graph.mailbox.model;

/**
 * Die Ordner eines Postfachs, aus denen archiviert wird (User 2026-10-14): der Posteingang und die gesendeten Mails.
 * Der Wert ist der feste Name des Ordners bei Graph (well-known folder name), so braucht es keine Ordner-id.
 */
public enum GraphMailFolder {

	/** Posteingang */
	INBOX("inbox"),

	/** Gesendete Elemente: unser Mailausgang */
	SENT_ITEMS("sentitems");

	private final String graphName;

	GraphMailFolder(String graphName) {
		this.graphName = graphName;
	}

	/** Der Name des Ordners in der Adresse bei Graph, z. B. inbox */
	public String getGraphName() {
		return graphName;
	}

}
