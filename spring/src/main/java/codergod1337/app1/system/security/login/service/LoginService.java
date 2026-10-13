package codergod1337.app1.system.security.login.service;

import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleCollectionUsersAssignmentService;
import codergod1337.app1.system.access.service.AccessRoleUsersAssignmentService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.graph.mail.model.GraphMailTemplateType;
import codergod1337.app1.graph.mail.service.GraphMailTemplateService;
import codergod1337.app1.graph.mail.service.LoginGraphMailPlaceholderGroup;
import codergod1337.app1.graph.mail.service.UsersGraphMailPlaceholderGroup;
import codergod1337.app1.system.security.JwtConfig;
import codergod1337.app1.system.security.TokenRevocations;
import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.security.login.model.IssuedSession;
import codergod1337.app1.system.security.login.model.Login2faPollToken;
import codergod1337.app1.system.security.login.model.Login2faTokens;
import codergod1337.app1.system.security.login.model.SessionInfo;
import codergod1337.app1.system.security.login.model.UsersRefreshToken;
import codergod1337.app1.system.security.login.model.UsersSessionForClient;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.service.UsersCredentialsService;
import codergod1337.app1.system.user.service.UsersService;
import codergod1337.app1.system.user.service.UsersDebuffsService;
import codergod1337.app1.system.user.service.UsersSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Der Ablauf von Login, Refresh und Logout. Jeder Login läuft über die 2FA: start (Passwort, der Aktivierungslink
 * geht an den User), confirm (der Link), claim (Sitzung abholen). Jeder Fehlschlag bekommt dieselbe Absage, von außen
 * ist nicht zu unterscheiden, ob die email unbekannt oder das Passwort falsch war.
 */
@Service
public class LoginService {

	private static final String LOGIN_FAILED = "Anmeldung fehlgeschlagen";

	/** die UsersSetting mit der Sprache der Oberfläche, gesetzt vom Frontend (useLanguage) */
	private static final String LANGUAGE_SETTING_KEY = "LANGUAGE";

	private final UsersService usersService;
	private final UsersCredentialsService usersCredentialsService;
	private final UsersSettingsService usersSettingsService;
	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;
	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final UsersLogin2faService usersLogin2faService;
	private final GraphMailTemplateService graphMailTemplateService;
	private final UsersRefreshTokenService usersRefreshTokenService;
	private final UsersDebuffsService usersDebuffsService;
	private final CurrentUsersProvider currentUsersProvider;
	private final TokenRevocations tokenRevocations;
	private final JwtEncoder jwtEncoder;

	/** Wie lange ein JWT gilt, app1.jwt.validity */
	private final Duration jwtValidity;

	/** Die Seite hinter dem Aktivierungslink, app1.login.confirm-url. Der Token hängt hinter einem # dahinter. */
	private final String confirmUrl;

	/** Wie lange eine Wartezeile gilt, app1.login.two-factor-validity, steht so in der Mail */
	private final Duration twoFactorValidity;

	public LoginService(UsersService usersService, UsersCredentialsService usersCredentialsService,
			UsersSettingsService usersSettingsService,
			AccessRoleUsersAssignmentService accessRoleUsersAssignmentService,
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService,
			AccessRoleCollectionService accessRoleCollectionService,
			UsersLogin2faService usersLogin2faService, GraphMailTemplateService graphMailTemplateService,
			UsersRefreshTokenService usersRefreshTokenService, UsersDebuffsService usersDebuffsService,
			CurrentUsersProvider currentUsersProvider, TokenRevocations tokenRevocations, JwtEncoder jwtEncoder,
			@Value("${app1.jwt.validity}") Duration jwtValidity,
			@Value("${app1.login.confirm-url}") String confirmUrl,
			@Value("${app1.login.two-factor-validity}") Duration twoFactorValidity) {
		this.usersService = usersService;
		this.usersCredentialsService = usersCredentialsService;
		this.usersSettingsService = usersSettingsService;
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.usersLogin2faService = usersLogin2faService;
		this.graphMailTemplateService = graphMailTemplateService;
		this.usersRefreshTokenService = usersRefreshTokenService;
		this.usersDebuffsService = usersDebuffsService;
		this.currentUsersProvider = currentUsersProvider;
		this.tokenRevocations = tokenRevocations;
		this.jwtEncoder = jwtEncoder;
		this.jwtValidity = jwtValidity;
		this.confirmUrl = confirmUrl;
		this.twoFactorValidity = twoFactorValidity;
	}

	/**
	 * Schritt 1: email und Passwort (SHA-256). Unbekannte email, falsches Passwort, Service-Account oder Status nicht
	 * ACTIVE: 401. Sonst neue Wartezeile, dann geht die 2FA-Mail (Vorlage LOGIN_2FA, in der Sprache des Users) mit
	 * Link und Token an seine E-Mail. Scheitert der Versand: 503, die Transaktion rollt die Wartezeile zurück. Zurück
	 * kommt nur der Abholschein.
	 */
	@Transactional
	public Login2faPollToken startLogin(Area area, String email, String password) {
		Users users = usersService.findUsersByEmail(email)
				// Service-Accounts sind Dienste und kommen über diesen Weg nicht herein
				.filter(found -> !found.isServiceAccount())
				.filter(found -> "ACTIVE".equals(found.getStatus()))
				.filter(found -> usersCredentialsService.isUsersCredentialsPasswordValid(found.getGuid(), password))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED));
		Login2faTokens login2faTokens = usersLogin2faService.createUsersLogin2fa(users.getGuid(), area);

		// Hinter dem #: Diesen Teil schickt ein Browser nie an einen Server, auch ein Link-Scanner bestätigt so nichts
		String confirmLink = confirmUrl + "#" + login2faTokens.confirmationToken();
		String language = usersSettingsService.findUsersSettingsValue(users.getGuid(), LANGUAGE_SETTING_KEY).orElse(null);
		graphMailTemplateService.sendGraphMailFromTemplate(GraphMailTemplateType.LOGIN_2FA, language, Map.of(
				GraphMailTemplateType.PLACEHOLDER_GROUP_USERS, UsersGraphMailPlaceholderGroup.valuesOf(users),
				GraphMailTemplateType.PLACEHOLDER_GROUP_LOGIN, LoginGraphMailPlaceholderGroup.valuesOf(confirmLink,
						login2faTokens.confirmationToken(), Math.max(1, twoFactorValidity.toMinutes()))),
				users.getEmail());
		return new Login2faPollToken(login2faTokens.pollToken());
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

	/** Die Sitzungen des angemeldeten Users, je belegtem Platz eine, die eigene (der Platz aus dem Token) markiert. */
	@Transactional(readOnly = true)
	public List<UsersSessionForClient> getCurrentUsersSessions() {
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		int currentSessionNumber = currentUsersProvider.getCurrentUsersSessionNumber();
		List<UsersSessionForClient> sessions = new ArrayList<>();
		for (UsersRefreshToken living : usersRefreshTokenService.getLivingUsersRefreshTokensOfUsers(usersGuid)) {
			sessions.add(new UsersSessionForClient(living.getSessionNumber(), living.getArea(),
					living.getFamilyCreatedAt(), living.getRefreshCount(), living.getCreatedAt(), living.getExpiresAt(),
					living.getSessionNumber() == currentSessionNumber));
		}
		return sessions;
	}

	/**
	 * Eine Sitzung des angemeldeten Users beenden: Die Kette auf diesem Platz wird gelöscht, das Gerät scheitert beim
	 * nächsten Refresh. Zurück kommt, ob es die eigene war: Dann löscht der Controller auch die Cookies.
	 */
	@Transactional
	public boolean endCurrentUsersSession(int sessionNumber) {
		usersRefreshTokenService.deleteUsersRefreshTokenFamilyBySessionNumber(currentUsersProvider.getCurrentUsersGuid(),
				sessionNumber);
		return sessionNumber == currentUsersProvider.getCurrentUsersSessionNumber();
	}

	/**
	 * Alle Sitzungen des angemeldeten Users beenden, auch die eigene. Dazu die laufenden JWTs sperren, damit fremde
	 * Fenster sofort fliegen und nicht erst beim nächsten Refresh.
	 */
	@Transactional
	public void endAllCurrentUsersSessions() {
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		usersRefreshTokenService.deleteAllUsersRefreshTokensOfUsers(usersGuid);
		tokenRevocations.revokeUsersTokens(usersGuid);
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
	 * (fehlt ohne ARC), area, session, debuffs (hat der User Debuffs), iat und exp. Slave-ARCs zählen nicht mit. Ab hier
	 * prüft das Backend nur noch das Token, ohne Datenbank.
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
				.claim(JwtConfig.CLAIM_SESSION, sessionNumber)
				.claim(JwtConfig.CLAIM_DEBUFFS, usersDebuffsService.hasUsersDebuffs(usersGuid));
		accessRoleCollection.ifPresent(found -> jwtClaimsBuilder.claim(JwtConfig.CLAIM_ACCESS_ROLE_COLLECTION, found.getKey()));
		return jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsBuilder.build())).getTokenValue();
	}

}
