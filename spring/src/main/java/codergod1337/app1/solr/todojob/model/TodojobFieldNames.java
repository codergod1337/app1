package codergod1337.app1.solr.todojob.model;

import java.util.List;

/**
 * Die Feldnamen des Kerns TODOJOB, mit denen das Backend rechnet (Vorgabe 2026-10-07: hart im Code des Kerns, fehlt ein
 * Feld in solr_field, fliegen Fehler). Die Zeilen selbst stehen in solr_field, angelegt am 2026-10-16.
 */
public final class TodojobFieldNames {

	public static final String ID = "id";
	public static final String STATUS = "status";
	public static final String AUSGANG = "ausgang";
	public static final String VERB = "verb";
	public static final String OBJEKT = "objekt";
	public static final String AUFGABE = "aufgabe";
	public static final String KONTEXT = "kontext";
	public static final String RUECKMELDUNGS_ART = "rueckmeldungsArt";
	public static final String BESTAETIGUNG_NOETIG = "bestaetigungNoetig";
	public static final String DAUERHAFT_RELEVANT = "dauerhaftRelevant";
	public static final String DISPLAY_CONTEXT_KEYS = "displayContextKeys";
	public static final String OWNER_GUID = "ownerGuid";
	public static final String OWNER_ACCESS_ROLE_KEYS = "ownerAccessRoleKeys";
	public static final String OWNER_ARC_KEYS = "ownerArcKeys";
	public static final String SERVANT_GUIDS = "servantGuids";
	public static final String SERVANT_ACCESS_ROLE_KEYS = "servantAccessRoleKeys";
	public static final String SERVANT_ARC_KEYS = "servantArcKeys";
	public static final String CLAIM_GUID = "claimGuid";
	public static final String ERSTELLT_AM = "erstelltAm";
	public static final String FRIST_AM = "fristAm";
	public static final String ABGESCHLOSSEN_AM = "abgeschlossenAm";
	public static final String RUECKMELDUNG = "rueckmeldung";
	public static final String RUECKFRAGEN = "rueckfragen";
	public static final String RUECKANTWORTEN = "rueckantworten";
	public static final String OFFENE_RUECKFRAGEN = "offeneRueckfragen";
	public static final String WIEDEREROEFFNET_ANZAHL = "wiedereroeffnetAnzahl";
	public static final String LOG = "log";
	public static final String LAST_CHANGED_AT = "lastChangedAt";
	public static final String CHANGING_USERS = "changingUsers";

	/**
	 * Der Auszug (User 2026-10-16): was eine Mappe über ihren Anzeigeort von einem Job sieht, ohne Partei zu sein. Nie
	 * Log, Rückfragen, Rückantworten, Adressen oder Claim. Die Hooks kommen dazu, denn über einen davon wurde geladen.
	 */
	public static final List<String> EXCERPT_FIELD_NAMES = List.of(ID, STATUS, AUSGANG, VERB, OBJEKT, AUFGABE,
			RUECKMELDUNGS_ART, DAUERHAFT_RELEVANT, DISPLAY_CONTEXT_KEYS, ERSTELLT_AM, FRIST_AM, ABGESCHLOSSEN_AM,
			RUECKMELDUNG);

	private TodojobFieldNames() {
	}

}
