package codergod1337.app1.system.security.login.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wer gerade da ist. Das Frontend kann das JWT im httpOnly-Cookie nicht lesen und fragt deshalb hier.
 * Unangemeldet: loggedIn = false, keine AR, keine ARC, alles andere leer. Das Frontend zeigt danach nur die Kacheln
 * an, die der Besucher nutzen darf.
 *
 * accessRoleKeys enthält auch die AR aus der ARC. accessRoleCollectionKey ist null, wenn der User keine ARC hat.
 */
public record SessionInfo(boolean loggedIn, UUID usersGuid, String email, List<String> accessRoleKeys,
		String accessRoleCollectionKey, Area area, int sessionNumber, Instant tokenExpiresAt) {

	/** Die Sitzung eines Unangemeldeten */
	public static SessionInfo anonymous() {
		return new SessionInfo(false, null, null, List.of(), null, null, 0, null);
	}

}
