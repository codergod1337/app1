package codergod1337.app1.solr.todojob.model;

/**
 * Der Ausgang eines abgeschlossenen Jobs (User 2026-10-16). GUT_GEMACHT ist der einzige Weg über eine Rückmeldung: Der
 * Owner hat sie angenommen, oder ohne Bestätigungspflicht hat der Servant fertig gemeldet. Eine unbefriedigende
 * Rückmeldung ist kein Ausgang, sondern das Zurückgeben (FERTIG zurück nach IN_BEARBEITUNG). Vorschläge und Muster nehmen
 * nur Jobs mit GUT_GEMACHT.
 */
public final class TodojobAusgang {

	public static final String GUT_GEMACHT = "GUT_GEMACHT";
	public static final String VERFALLEN = "VERFALLEN";
	public static final String ZURUECKGEZOGEN = "ZURUECKGEZOGEN";

	private TodojobAusgang() {
	}

}
