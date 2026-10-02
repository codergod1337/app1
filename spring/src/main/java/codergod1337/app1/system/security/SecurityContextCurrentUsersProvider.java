package codergod1337.app1.system.security;

import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.repository.UsersRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Die einzige Stelle, die den Security-Context von Spring direkt anfasst. Die Signatur hat die Filterkette schon
 * geprüft, was hier ankommt, ist echt. Fragt jemand ohne angemeldeten User nach der Identität, ist das ein Fehler im
 * Aufbau und kein leerer Sonderfall: Dann wird geworfen.
 */
@Component
public class SecurityContextCurrentUsersProvider implements CurrentUsersProvider {

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme der User über den UsersService. Der wird diesen Provider aber selbst brauchen,
	 * sobald seine Methoden den current user prüfen. Deshalb hier ausnahmsweise direkt das fremde UsersRepository, und
	 * zwar nur lesend (findById).
	 */
	private final UsersRepository usersRepository;

	public SecurityContextCurrentUsersProvider(UsersRepository usersRepository) {
		this.usersRepository = usersRepository;
	}

	@Override
	public boolean isUsersLoggedIn() {
		return findJwt() != null;
	}

	@Override
	public UUID getCurrentUsersGuid() {
		return UUID.fromString(requireJwt().getSubject());
	}

	/** Gibt es den User nicht mehr, gehört das Token zu einem gelöschten User: Das soll auffallen. */
	@Override
	public Users getCurrentUsers() {
		UUID usersGuid = getCurrentUsersGuid();
		// ZIRKELSCHLUSS: direkt das fremde UsersRepository, siehe Feld usersRepository
		return usersRepository.findById(usersGuid)
				.orElseThrow(() -> new IllegalStateException("das Token gehört zu einem gelöschten User: " + usersGuid));
	}

	@Override
	public List<String> getCurrentUsersAccessRoleKeys() {
		List<String> accessRoleKeys = requireJwt().getClaimAsStringList(JwtConfig.CLAIM_ROLES);
		return accessRoleKeys != null ? List.copyOf(accessRoleKeys) : List.of();
	}

	@Override
	public String getCurrentUsersAccessRoleCollectionKey() {
		return requireJwt().getClaimAsString(JwtConfig.CLAIM_ACCESS_ROLE_COLLECTION);
	}

	@Override
	public Area getCurrentUsersArea() {
		return Area.valueOf(requireJwt().getClaimAsString(JwtConfig.CLAIM_AREA));
	}

	@Override
	public int getCurrentUsersSessionNumber() {
		return requireJwt().<Number>getClaim(JwtConfig.CLAIM_SESSION).intValue();
	}

	@Override
	public Instant getCurrentUsersTokenExpiresAt() {
		return requireJwt().getExpiresAt();
	}

	/** Das Token der laufenden Anfrage, null wenn niemand angemeldet ist */
	private Jwt findJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication instanceof JwtAuthenticationToken jwtAuthentication ? jwtAuthentication.getToken() : null;
	}

	private Jwt requireJwt() {
		Jwt jwt = findJwt();
		if (jwt == null) {
			throw new IllegalStateException("niemand angemeldet: hier darf nichts nach der Identität fragen");
		}
		return jwt;
	}

}
