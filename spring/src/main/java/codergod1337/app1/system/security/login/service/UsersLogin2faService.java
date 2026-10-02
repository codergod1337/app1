package codergod1337.app1.system.security.login.service;

import codergod1337.app1.system.security.login.LoginToken;
import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.security.login.model.Login2faTokens;
import codergod1337.app1.system.security.login.model.UsersLogin2fa;
import codergod1337.app1.system.security.login.repository.UsersLogin2faRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Wartezeilen der 2FA: anlegen bei start, bestätigen per Aktivierungslink, einlösen bei claim. Jeder Fehlschlag
 * bekommt dieselbe Absage, von außen ist nicht zu unterscheiden, woran es lag.
 */
@Service
public class UsersLogin2faService {

	private static final String LOGIN_FAILED = "Anmeldung fehlgeschlagen";

	private final UsersLogin2faRepository usersLogin2faRepository;

	/** Wie lange eine Wartezeile gilt, app1.login.two-factor-validity */
	private final Duration twoFactorValidity;

	public UsersLogin2faService(UsersLogin2faRepository usersLogin2faRepository,
			@Value("${app1.login.two-factor-validity}") Duration twoFactorValidity) {
		this.usersLogin2faRepository = usersLogin2faRepository;
		this.twoFactorValidity = twoFactorValidity;
	}

	/** Neue Wartezeile, räumt Abgelaufenes nebenbei weg. Zurück kommen die zwei Klartexte, nur dieses eine Mal. */
	@Transactional
	public Login2faTokens createUsersLogin2fa(UUID usersGuid, Area area) {
		OffsetDateTime now = OffsetDateTime.now();
		usersLogin2faRepository.deleteByExpiresAtBefore(now);

		String pollToken = LoginToken.createLoginToken();
		String confirmationToken = LoginToken.createLoginToken();
		usersLogin2faRepository.save(new UsersLogin2fa(LoginToken.hashLoginToken(pollToken),
				LoginToken.hashLoginToken(confirmationToken), usersGuid, area, now.plus(twoFactorValidity)));
		return new Login2faTokens(pollToken, confirmationToken);
	}

	/** Per Aktivierungslink bestätigen. Unbekannt oder abgelaufen: 401. Ein zweiter Klick gilt als Erfolg. */
	@Transactional
	public void confirmUsersLogin2fa(String confirmationToken) {
		String confirmationTokenHash = LoginToken.hashLoginToken(confirmationToken);
		OffsetDateTime now = OffsetDateTime.now();
		if (usersLogin2faRepository.markConfirmedIfActive(confirmationTokenHash, now) == 0
				&& !usersLogin2faRepository.existsByConfirmationTokenHashAndConfirmedAtIsNotNullAndExpiresAtAfter(
						confirmationTokenHash, now)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
		}
	}

	/**
	 * Den Abholschein einlösen. Optional.empty(): noch nicht bestätigt, weiter warten.
	 * Bestätigt: Die Zeile wird gelöscht (einmalig), zurück kommt die guid des Users.
	 * Unbekannt, abgelaufen, falsche area oder jemand war schneller: 401.
	 */
	@Transactional
	public Optional<UUID> claimUsersLogin2fa(String pollToken, Area area) {
		String pollTokenHash = LoginToken.hashLoginToken(pollToken);
		OffsetDateTime now = OffsetDateTime.now();
		UsersLogin2fa usersLogin2fa = usersLogin2faRepository.findByPollTokenHash(pollTokenHash)
				.filter(found -> found.getArea() == area && found.getExpiresAt().isAfter(now))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED));
		if (usersLogin2fa.getConfirmedAt() == null) {
			return Optional.empty();
		}
		// Einmalig: Nur wer die Zeile löscht, bekommt die Sitzung
		if (usersLogin2faRepository.deleteIfConfirmedAndActive(pollTokenHash, area, now) == 0) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
		}
		return Optional.of(usersLogin2fa.getUsersGuid());
	}

	/** guid-Wechsel oder User gelöscht: laufende Anmeldungen weg. */
	@Transactional
	public void deleteAllUsersLogin2faOfUsers(UUID usersGuid) {
		usersLogin2faRepository.deleteByUsersGuid(usersGuid);
	}

}
