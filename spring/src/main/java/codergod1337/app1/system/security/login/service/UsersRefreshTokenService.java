package codergod1337.app1.system.security.login.service;

import codergod1337.app1.system.security.TokenRevocations;
import codergod1337.app1.system.security.login.LoginToken;
import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.security.login.model.UsersRefreshToken;
import codergod1337.app1.system.security.login.repository.UsersRefreshTokenRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Refresh-Tokens: eine Kette pro Gerät, bei jeder Erneuerung ein neues Glied (Rotation). Ein verbrauchter Token,
 * der wieder auftaucht, ist Diebstahl. Jeder Fehlschlag bekommt dieselbe Absage.
 */
@Service
public class UsersRefreshTokenService {

	private static final String LOGIN_FAILED = "Anmeldung fehlgeschlagen";

	private final UsersRefreshTokenRepository usersRefreshTokenRepository;

	/** Wie lange ein Refresh-Token gilt, app1.refresh.validity */
	private final Duration refreshValidity;

	/** Wie viele Sitzungen ein User gleichzeitig haben darf, app1.login.max-sessions */
	private final int maxSessions;

	private final TokenRevocations tokenRevocations;

	public UsersRefreshTokenService(UsersRefreshTokenRepository usersRefreshTokenRepository,
			@Value("${app1.refresh.validity}") Duration refreshValidity,
			@Value("${app1.login.max-sessions}") int maxSessions, TokenRevocations tokenRevocations) {
		this.usersRefreshTokenRepository = usersRefreshTokenRepository;
		this.refreshValidity = refreshValidity;
		this.maxSessions = maxSessions;
		this.tokenRevocations = tokenRevocations;
	}

	/**
	 * Der Platz für eine neue Anmeldung: der erste freie. Sind alle belegt, der Platz, der am längsten nicht erneuert
	 * wurde, also dessen aktuelles Glied am ältesten ist.
	 */
	@Transactional(readOnly = true)
	public int chooseUsersRefreshTokenSessionNumber(UUID usersGuid) {
		List<UsersRefreshToken> livingUsersRefreshTokens = usersRefreshTokenRepository
				.findByUsersGuidAndUsedAtIsNullAndExpiresAtAfter(usersGuid, OffsetDateTime.now());
		Set<Integer> takenSessionNumbers = livingUsersRefreshTokens.stream()
				.map(UsersRefreshToken::getSessionNumber)
				.collect(Collectors.toSet());
		for (int sessionNumber = 1; sessionNumber <= maxSessions; sessionNumber++) {
			if (!takenSessionNumbers.contains(sessionNumber)) {
				return sessionNumber;
			}
		}
		return livingUsersRefreshTokens.stream()
				.min(Comparator.comparing(UsersRefreshToken::getCreatedAt))
				.map(UsersRefreshToken::getSessionNumber)
				.orElse(1);
	}

	/**
	 * Neue Kette auf diesem Platz. Räumt Abgelaufenes des Users weg. War der Platz belegt, fliegt dessen ganze Kette:
	 * Das alte Gerät verliert seine Sitzung. Zurück kommt der Klartext fürs Cookie.
	 */
	@Transactional
	public String createUsersRefreshToken(UUID usersGuid, Area area, int sessionNumber) {
		OffsetDateTime now = OffsetDateTime.now();
		usersRefreshTokenRepository.deleteByUsersGuidAndExpiresAtBefore(usersGuid, now);
		for (UsersRefreshToken livingUsersRefreshToken : usersRefreshTokenRepository
				.findByUsersGuidAndUsedAtIsNullAndExpiresAtAfter(usersGuid, now)) {
			if (livingUsersRefreshToken.getSessionNumber() == sessionNumber) {
				usersRefreshTokenRepository.deleteByFamilyId(livingUsersRefreshToken.getFamilyId());
			}
		}

		String refreshToken = LoginToken.createLoginToken();
		usersRefreshTokenRepository.save(new UsersRefreshToken(LoginToken.hashLoginToken(refreshToken), usersGuid, area,
				UUID.randomUUID(), sessionNumber, now.plus(refreshValidity), now, 0));
		return refreshToken;
	}

	/**
	 * Verbraucht einen Token beim Refresh und gibt die verbrauchte Zeile zurück (guid, area, Kette, Platz).
	 * Unbekannt: 401. Abgelaufen: die Kette löschen, 401.
	 * Schon verbraucht: Diebstahl. Alle Ketten des Users löschen, 401.
	 *
	 * noRollbackFor: Die 401 ist eine RuntimeException und rollte sonst genau das Löschen der Ketten zurück.
	 */
	@Transactional(noRollbackFor = ResponseStatusException.class)
	public UsersRefreshToken consumeUsersRefreshToken(String refreshToken) {
		String tokenHash = LoginToken.hashLoginToken(refreshToken);
		OffsetDateTime now = OffsetDateTime.now();
		UsersRefreshToken usersRefreshToken = usersRefreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED));

		// Abgelaufen ist normal (zwei Wochen nicht da), kein Diebstahl. Deshalb steht diese Prüfung zuerst.
		if (!usersRefreshToken.getExpiresAt().isAfter(now)) {
			usersRefreshTokenRepository.deleteByFamilyId(usersRefreshToken.getFamilyId());
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
		}
		// Schon verbraucht: Jemand benutzt einen alten Token. Alle Geräte fliegen, Dieb und Bestohlener.
		if (usersRefreshTokenRepository.markUsedIfActive(tokenHash, now) == 0) {
			usersRefreshTokenRepository.deleteByUsersGuid(usersRefreshToken.getUsersGuid());
			// auch die laufenden JWTs, sonst arbeitete der Dieb bis zu deren Ablauf weiter
			tokenRevocations.revokeUsersTokens(usersRefreshToken.getUsersGuid());
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
		}
		return usersRefreshToken;
	}

	/**
	 * Nachfolger in derselben Kette: gleiche area, gleicher Platz, gleiches „angemeldet seit“, refreshCount + 1, wieder
	 * volle Laufzeit. Zurück kommt der Klartext fürs Cookie.
	 */
	@Transactional
	public String createSuccessorUsersRefreshToken(UsersRefreshToken consumedUsersRefreshToken) {
		String refreshToken = LoginToken.createLoginToken();
		usersRefreshTokenRepository.save(new UsersRefreshToken(LoginToken.hashLoginToken(refreshToken),
				consumedUsersRefreshToken.getUsersGuid(), consumedUsersRefreshToken.getArea(),
				consumedUsersRefreshToken.getFamilyId(), consumedUsersRefreshToken.getSessionNumber(),
				OffsetDateTime.now().plus(refreshValidity), consumedUsersRefreshToken.getFamilyCreatedAt(),
				consumedUsersRefreshToken.getRefreshCount() + 1));
		return refreshToken;
	}

	/** Logout: die Kette dieses Geräts löschen. Ein unbekannter Token ist kein Fehler. */
	@Transactional
	public void deleteUsersRefreshTokenFamily(String refreshToken) {
		usersRefreshTokenRepository.findByTokenHash(LoginToken.hashLoginToken(refreshToken))
				.ifPresent(usersRefreshToken -> usersRefreshTokenRepository.deleteByFamilyId(usersRefreshToken.getFamilyId()));
	}

	/** Alle Ketten eines Users: guid-Wechsel, User gelöscht, Diebstahl. */
	@Transactional
	public void deleteAllUsersRefreshTokensOfUsers(UUID usersGuid) {
		usersRefreshTokenRepository.deleteByUsersGuid(usersGuid);
	}

}
