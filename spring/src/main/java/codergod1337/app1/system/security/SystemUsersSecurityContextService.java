package codergod1337.app1.system.security;

import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleCollectionUsersAssignmentService;
import codergod1337.app1.system.access.service.AccessRoleUsersAssignmentService;
import codergod1337.app1.system.user.service.UsersDebuffsService;
import codergod1337.app1.system.user.service.UsersService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Lässt einen Job ohne Token als System-User laufen (Zugriffsfall {@code system} in architektur.md): Für die Dauer des
 * Jobs steht im Security-Context dasselbe Token-Format wie nach einem Login, mit der guid aus
 * {@code app1.system-users.guid} und den AR des System-Users aus der Datenbank, direkt zugewiesen und aus seiner ARC
 * wie beim Login. Damit gehen Jobs durch dieselben Methoden und Prüfungen wie ein Mensch, der
 * {@link SecurityContextCurrentUsersProvider} bleibt unverändert, und das Log zeigt den System-User als den, der
 * geschrieben hat.
 *
 * Das Token wird nie signiert und verlässt nie den Prozess, es trägt nur die Claims. area und session fehlen: Der
 * System-User hat keinen Login und keine Sitzung. Die AR werden bei jedem Job frisch gelesen, eine Änderung wirkt also
 * beim nächsten Job.
 */
@Service
public class SystemUsersSecurityContextService {

	/** Der Text des Tokens im Context, nur zur Kennzeichnung: Niemand prüft oder verschickt ihn */
	private static final String SYSTEM_USERS_TOKEN_VALUE = "system-users";

	private final UUID systemUsersGuid;
	private final UsersService usersService;
	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;
	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final UsersDebuffsService usersDebuffsService;

	public SystemUsersSecurityContextService(@Value("${app1.system-users.guid}") UUID systemUsersGuid,
			UsersService usersService, AccessRoleUsersAssignmentService accessRoleUsersAssignmentService,
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService,
			AccessRoleCollectionService accessRoleCollectionService, UsersDebuffsService usersDebuffsService) {
		this.systemUsersGuid = systemUsersGuid;
		this.usersService = usersService;
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.usersDebuffsService = usersDebuffsService;
	}

	/**
	 * Den Job als System-User laufen lassen und danach den vorherigen Context zurücksetzen, auch wenn der Job wirft.
	 * Fehlt der System-User in users, wirft schon das Umschalten, der Job läuft dann nicht.
	 */
	public void runAsSystemUsers(Runnable job) {
		SecurityContext previousContext = SecurityContextHolder.getContext();
		SecurityContext systemUsersContext = SecurityContextHolder.createEmptyContext();
		systemUsersContext.setAuthentication(createSystemUsersAuthentication());
		SecurityContextHolder.setContext(systemUsersContext);
		try {
			job.run();
		} finally {
			// Worker-Threads kommen aus einem Pool: Bliebe der Context stehen, liefe der nächste Auftrag des Threads
			// ungefragt als System-User
			if (previousContext.getAuthentication() == null) {
				SecurityContextHolder.clearContext();
			} else {
				SecurityContextHolder.setContext(previousContext);
			}
		}
	}

	/** Die Claims wie in LoginService.createJwt, die Berechtigungen ROLE_<key> wie in SecurityConfig */
	private JwtAuthenticationToken createSystemUsersAuthentication() {
		if (usersService.isGuidAvailable(systemUsersGuid)) {
			throw new IllegalStateException("der System-User " + systemUsersGuid
					+ " (app1.system-users.guid) fehlt in users, Jobs als System-User laufen nicht");
		}
		// Eine ARC ist optional, eine inzwischen gelöschte zählt wie keine
		Optional<AccessRoleCollection> accessRoleCollection = accessRoleCollectionUsersAssignmentService
				.findAccessRoleCollectionKeyByUsersGuid(systemUsersGuid)
				.flatMap(accessRoleCollectionService::findAccessRoleCollectionByKey);
		Set<String> accessRoleKeys = new LinkedHashSet<>(
				accessRoleUsersAssignmentService.getAccessRoleKeysByUsersGuid(systemUsersGuid));
		accessRoleCollection.map(AccessRoleCollection::getAccessRoleKeys).ifPresent(accessRoleKeys::addAll);

		Jwt.Builder jwtBuilder = Jwt.withTokenValue(SYSTEM_USERS_TOKEN_VALUE)
				.header("alg", "none")
				.subject(systemUsersGuid.toString())
				.issuedAt(Instant.now())
				.claim(JwtConfig.CLAIM_ROLES, List.copyOf(accessRoleKeys))
				.claim(JwtConfig.CLAIM_DEBUFFS, usersDebuffsService.hasUsersDebuffs(systemUsersGuid));
		accessRoleCollection
				.ifPresent(found -> jwtBuilder.claim(JwtConfig.CLAIM_ACCESS_ROLE_COLLECTION, found.getKey()));
		List<GrantedAuthority> authorities = accessRoleKeys.stream()
				.<GrantedAuthority>map(accessRoleKey -> new SimpleGrantedAuthority("ROLE_" + accessRoleKey))
				.toList();
		return new JwtAuthenticationToken(jwtBuilder.build(), authorities);
	}

}
