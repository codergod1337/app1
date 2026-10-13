package codergod1337.app1.abas.model;

/**
 * Wer einen Eintrag in die Queue gelegt hat. Der Worker kopiert die Quelle ins Auflöse-Protokoll, damit dort „ABAS hat
 * gelöscht“ von „der Admin hat gelöscht“ zu unterscheiden ist.
 */
public enum AbasSternzeitQueueSource {

	/** der Push des ERP über den öffentlichen Eingang */
	ABAS,

	/** von Hand im AbasManager: eine Kennung einreihen, Resync */
	ADMIN,

	/** der Import einer Datei im AbasManager, z. B. das Inventar eines anderen Systems (User 2026-10-13) */
	IMPORT

}
