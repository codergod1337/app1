package codergod1337.app1.abas.model;

/**
 * Was mit einer Sternzeit in der Queue geschehen soll. Steht wörtlich am Eintrag, der Worker entscheidet danach, was er
 * tut; es gibt keine dritte Aktion.
 */
public enum AbasSternzeitQueueAction {

	/** bei ABAS holen, mappen und in den Kern schreiben (ABAS: „hat sich geändert, holt sie euch“) */
	FETCH,

	/** im Kern auflösen und den Stammsatz entfernen, ohne ABAS-Aufruf (ABAS: „wurde bei uns gelöscht“) */
	DELETE

}
