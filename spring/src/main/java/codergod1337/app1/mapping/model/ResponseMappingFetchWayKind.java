package codergod1337.app1.mapping.model;

/** Wie die Antwort eines {@link ResponseMapping} geholt wird */
public enum ResponseMappingFetchWayKind {

	/** Ein Objekt per Sternzeit; die Beispiel-Sternzeit steht dabei, damit sich das Muster jederzeit neu holen lässt */
	STERNZEIT,

	/** Ein Infosystem mit Gruppe, Name und den Aktionen als Parameter */
	INFOSYS,

	/** Ein JSON von außerhalb, geladen aus Datei oder Text, kein Fetch */
	JSON

}
