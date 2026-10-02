package codergod1337.app1.system.security;

import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.user.model.Users;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Woher Controller und Services erfahren, wer gerade fragt. Kein Service bekommt den current user als Parameter und
 * fasst den Security-Context selbst an. Fast alles kommt aus dem Token und kostet keinen DB-Zugriff, ist dafür aber so
 * alt wie das Token. Ist niemand angemeldet, werfen alle Methoden außer isUsersLoggedIn.
 */
public interface CurrentUsersProvider {

	/** Ist überhaupt jemand angemeldet? Zuerst fragen, wenn ein Weg auch ohne Anmeldung erreichbar ist. */
	boolean isUsersLoggedIn();

	/** Die guid des angemeldeten Users, der sub-Claim des Tokens */
	UUID getCurrentUsersGuid();

	/** Der ganze Datensatz. Kostet als einzige Methode eine DB-Abfrage, ist dafür tagesaktuell. */
	Users getCurrentUsers();

	/** Die AR-Keys aus dem Token, direkt zugewiesene und die aus der ARC. Ein User ohne AR ergibt eine leere Liste. */
	List<String> getCurrentUsersAccessRoleKeys();

	/** Der Key der ARC aus dem Token. null, wenn der User keine hat: Eine ARC ist optional. */
	String getCurrentUsersAccessRoleCollectionKey();

	/** Über welchen Login das Token entstand */
	Area getCurrentUsersArea();

	/** Der Sitzungsplatz 1 bis 3 */
	int getCurrentUsersSessionNumber();

	/** Wann das Token abläuft. Das Frontend kann es nicht lesen und plant damit den nächsten Refresh. */
	Instant getCurrentUsersTokenExpiresAt();

}
