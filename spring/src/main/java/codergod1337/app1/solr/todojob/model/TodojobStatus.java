package codergod1337.app1.solr.todojob.model;

import java.util.List;

/**
 * Die fünf Status eines Jobs (Namen des Users, 2026-10-16), als EntityState im Bereich TODOJOB in Postgres, die Keys
 * hier, weil das Backend damit rechnet. Ein Status, nichts aus anderen Feldern abgeleitet:
 *
 * <ul>
 * <li>OFFEN: erteilt, noch niemand dran. Bei einem Job an eine Gruppe noch kein Claim.</li>
 * <li>CLAIMED: ein Servant hat sich eingetragen, noch kein Beitrag.</li>
 * <li>IN_BEARBEITUNG: der erste Beitrag oder die erste Rückfrage ist da, oder der Owner hat zurückgegeben.</li>
 * <li>FERTIG: der Servant hat fertig gemeldet, der Owner muss bestätigen oder zurückgeben (nur mit bestaetigungNoetig).</li>
 * <li>ABGESCHLOSSEN: terminal, der Ausgang steht in {@link TodojobAusgang}.</li>
 * </ul>
 */
public final class TodojobStatus {

	/** Der Bereich der Status in entity_state */
	public static final String SCOPE = "TODOJOB";

	public static final String OFFEN = "OFFEN";
	public static final String CLAIMED = "CLAIMED";
	public static final String IN_BEARBEITUNG = "IN_BEARBEITUNG";
	public static final String FERTIG = "FERTIG";
	public static final String ABGESCHLOSSEN = "ABGESCHLOSSEN";

	/** Die Status, in denen der Servant noch etwas tun kann: Beitrag, Rückfrage, Fertigmelden */
	public static final List<String> SERVANT_WORKING_STATUSES = List.of(OFFEN, CLAIMED, IN_BEARBEITUNG);

	/** Ein fq für alles, was nicht abgeschlossen ist: der Eingang */
	public static final String OPEN_STATUS_FILTER_QUERY = "status:(" + OFFEN + " OR " + CLAIMED + " OR " + IN_BEARBEITUNG
			+ " OR " + FERTIG + ")";

	private TodojobStatus() {
	}

}
