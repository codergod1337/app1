package codergod1337.app1.system.security.login.service;

import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleCollectionUsersAssignmentService;
import codergod1337.app1.system.access.service.AccessRoleUsersAssignmentService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.security.JwtConfig;
import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.security.login.model.IssuedSession;
import codergod1337.app1.system.security.login.model.Login2faTokens;
import codergod1337.app1.system.security.login.model.SessionInfo;
import codergod1337.app1.system.security.login.model.UsersRefreshToken;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.service.UsersCredentialsService;
import codergod1337.app1.system.user.service.UsersService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der Ablauf von Login, Refresh und Logout. Jeder Login läuft über die 2FA: start (Passwort), confirm
 * (Aktivierungslink), claim (Sitzung abholen). Jeder Fehlschlag bekommt dieselbe Absage, von außen ist nicht zu
 * unterscheiden, ob die email unbekannt oder das Passwort falsch war.
 */
@Service
public class LoginService {

	private static final String LOGIN_FAILED = "Anmeldung fehlgeschlagen";

	private final UsersService usersService;
	private final UsersCredentialsService usersCredentialsService;
	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;
	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final UsersLogin2faService usersLogin2faService;
	private final UsersRefreshTokenService usersRefreshTokenService;
	private final CurrentUsersProvider currentUsersProvider;
	private final JwtEncoder jwtEncoder;

	/** Wie lange ein JWT gilt, app1.jwt.validity */
	private final Duration jwtValidity;

	public LoginService(UsersService usersService, UsersCredentialsService usersCredentialsService,
			AccessRoleUsersAssignmentService accessRoleUsersAssignmentService,
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService,
			AccessRoleCollectionService accessRoleCollectionService,
			UsersLogin2faService usersLogin2faService, UsersRefreshTokenService usersRefreshTokenService,
			CurrentUsersProvider currentUsersProvider, JwtEncoder jwtEncoder,
			@Value("${app1.jwt.validity}") Duration jwtValidity) {
		this.usersService = usersService;
		this.usersCredentialsService = usersCredentialsService;
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.usersLogin2faService = usersLogin2faService;
		this.usersRefreshTokenService = usersRefreshTokenService;
		this.currentUsersProvider = currentUsersProvider;
		this.jwtEncoder = jwtEncoder;
		this.jwtValidity = jwtValidity;
	}

	/**
	 * Schritt 1: email und Passwort (SHA-256). Unbekannte email, falsches Passwort, Service-Account oder Status nicht
	 * ACTIVE: 401. Sonst neue Wartezeile, zurück kommen Abholschein und Bestätigungs-Token.
	 */
	@Transactional
	public Login2faTokens startLogin(Area area, String email, String password) {
		Users users = usersService.findUsersByEmail(email)
				// Service-Accounts sind Dienste und kommen über diesen Weg nicht herein
				.filter(found -> !found.isServiceAccount())
				.filter(found -> "ACTIVE".equals(found.getStatus()))
				.filter(found -> usersCredentialsService.isUsersCredentialsPasswordValid(found.getGuid(), password))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED));
		return usersLogin2faService.createUsersLogin2fa(users.getGuid(), area);
	}

	/** Schritt 2: der Aktivierungslink */
	@Transactional
	public void confirmLogin(String confirmationToken) {
		usersLogin2faService.confirmUsersLogin2fa(confirmationToken);
	}

	/**
	 * Schritt 3: Optional.empty(), solange noch nicht bestätigt ist. Sonst Platz wählen, Kette anlegen und das JWT mit
	 * den AR des Users ausstellen.
	 */
	@Transactional
	public Optional<IssuedSession> claimLogin(Area area, String pollToken) {
		return usersLogin2faService.claimUsersLogin2fa(pollToken, area).map(usersGuid -> {
			int sessionNumber = usersRefreshTokenService.chooseUsersRefreshTokenSessionNumber(usersGuid);
			String refreshToken = usersRefreshTokenService.createUsersRefreshToken(usersGuid, area, sessionNumber);
			return new IssuedSession(createJwt(usersGuid, area, sessionNumber), refreshToken, sessionNumber);
		});
	}

	/**
	 * Refresh: Token verbrauchen, Nachfolger anlegen, neues JWT mit frisch gelesenen AR. So wirken geänderte Rechte
	 * spätestens beim nächsten Refresh. Gelöschter User oder Service-Account: alle Ketten weg, 401.
	 *
	 * noRollbackFor: Die 401 rollte sonst den Verbrauch und das Löschen der Ketten zurück.
	 */
	@Transactional(noRollbackFor = ResponseStatusException.class)
	public IssuedSession refreshLogin(String refreshToken) {
		UsersRefreshToken consumedUsersRefreshToken = usersRefreshTokenService.consumeUsersRefreshToken(refreshToken);
		UUID usersGuid = consumedUsersRefreshToken.getUsersGuid();
		if (usersService.isGuidAvailable(usersGuid) || usersService.getUsersByGuid(usersGuid).isServiceAccount()) {
			usersRefreshTokenService.deleteAllUsersRefreshTokensOfUsers(usersGuid);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
		}
		String successorRefreshToken = usersRefreshTokenService.createSuccessorUsersRefreshToken(consumedUsersRefreshToken);
		int sessionNumber = consumedUsersRefreshToken.getSessionNumber();
		return new IssuedSession(createJwt(usersGuid, consumedUsersRefreshToken.getArea(), sessionNumber),
				successorRefreshToken, sessionNumber);
	}

	/** Logout: die Kette dieses Geräts löschen. Das laufende JWT gilt noch bis zu seinem Ablauf, erneuern geht nicht mehr. */
	@Transactional
	public void logout(String refreshToken) {
		usersRefreshTokenService.deleteUsersRefreshTokenFamily(refreshToken);
	}

	/** Wer gerade da ist: die Sitzung des Angemeldeten oder die leere Sitzung eines Unangemeldeten. */
	@Transactional(readOnly = true)
	public SessionInfo getSessionInfo() {
		if (!currentUsersProvider.isUsersLoggedIn()) {
			return SessionInfo.anonymous();
		}
		Users users = currentUsersProvider.getCurrentUsers();
		return new SessionInfo(true, users.getGuid(), users.getEmail(),
				currentUsersProvider.getCurrentUsersAccessRoleKeys(),
				currentUsersProvider.getCurrentUsersAccessRoleCollectionKey(), currentUsersProvider.getCurrentUsersArea(),
				currentUsersProvider.getCurrentUsersSessionNumber(), currentUsersProvider.getCurrentUsersTokenExpiresAt());
	}

	/**
	 * Signiert das JWT: sub = guid, roles = die direkt zugewiesenen AR und die AR aus der ARC, arc = der Key der ARC
	 * (fehlt ohne ARC), area, session, iat und exp. Slave-ARCs zählen nicht mit. Ab hier prüft das Backend nur noch das
	 * Token, ohne Datenbank.
	 */
	private String createJwt(UUID usersGuid, Area area, int sessionNumber) {
		// Eine ARC ist optional. Eine inzwischen gelöschte ARC zählt wie keine.
		Optional<AccessRoleCollection> accessRoleCollection = accessRoleCollectionUsersAssignmentService
				.findAccessRoleCollectionKeyByUsersGuid(usersGuid)
				.flatMap(accessRoleCollectionService::findAccessRoleCollectionByKey);

		Set<String> accessRoleKeys = new LinkedHashSet<>(
				accessRoleUsersAssignmentService.getAccessRoleKeysByUsersGuid(usersGuid));
		accessRoleCollection.map(AccessRoleCollection::getAccessRoleKeys).ifPresent(accessRoleKeys::addAll);

		Instant now = Instant.now();
		JwtClaimsSet.Builder jwtClaimsBuilder = JwtClaimsSet.builder()
				.subject(usersGuid.toString())
				.issuedAt(now)
				.expiresAt(now.plus(jwtValidity))
				.claim(JwtConfig.CLAIM_ROLES, List.copyOf(accessRoleKeys))
				.claim(JwtConfig.CLAIM_AREA, area.name())
				.claim(JwtConfig.CLAIM_SESSION, sessionNumber);
		accessRoleCollection.ifPresent(found -> jwtClaimsBuilder.claim(JwtConfig.CLAIM_ACCESS_ROLE_COLLECTION, found.getKey()));
		return jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsBuilder.build())).getTokenValue();
	}

}
