package codergod1337.app1.system.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Die Sperrliste: pro User der Zeitpunkt, ab dem seine Tokens wieder gelten. Ein JWT lässt sich sonst nicht
 * zurückrufen und gälte bis zu seinem Ablauf. Mit der Liste wirken geänderte Rechte, ein gelöschter User oder ein
 * erkannter Diebstahl sofort, das Frontend holt sich per Refresh ein neues Token.
 *
 * Nur im Arbeitsspeicher: Nach einem Neustart sind ohnehin alle JWTs ungültig, weil der Schlüssel neu ist.
 */
@Component
public class TokenRevocations {

	private final Map<UUID, Instant> revokedUntilByUsersGuid = new ConcurrentHashMap<>();

	/**
	 * Tokens dieses Users gelten erst ab jetzt. Auf Sekunden abgeschnitten, weil iat im JWT nur Sekunden kennt: Ein
	 * Token, das in derselben Sekunde neu ausgestellt wird, darf nicht gleich wieder durchfallen.
	 */
	public void revokeUsersTokens(UUID usersGuid) {
		revokedUntilByUsersGuid.put(usersGuid, Instant.now().truncatedTo(ChronoUnit.SECONDS));
	}

	/** Wurde das Token vor der Sperre ausgestellt? Ohne iat gilt es als gesperrt. */
	public boolean isUsersTokenRevoked(UUID usersGuid, Instant issuedAt) {
		Instant revokedUntil = revokedUntilByUsersGuid.get(usersGuid);
		if (revokedUntil == null) {
			return false;
		}
		return issuedAt == null || issuedAt.isBefore(revokedUntil);
	}

}
