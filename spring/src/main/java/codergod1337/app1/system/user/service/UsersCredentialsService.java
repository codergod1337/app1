package codergod1337.app1.system.user.service;

import codergod1337.app1.system.security.TokenRevocations;
import codergod1337.app1.system.security.login.service.UsersRefreshTokenService;
import codergod1337.app1.system.user.model.UsersCredentials;
import codergod1337.app1.system.user.repository.UsersCredentialsRepository;
import codergod1337.app1.system.user.repository.UsersRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Passwort eines Users. Was hier als Passwort hereinkommt, ist schon der SHA-256 aus dem Browser, der Klartext
 * erreicht den Server nie. Gespeichert wird BCrypt darüber. Heraus geht nichts: Es gibt keinen Leseweg für den Hash.
 */
@Service
public class UsersCredentialsService {

	private final UsersCredentialsRepository usersCredentialsRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diesen User?“ über den UsersService. Der braucht aber diesen
	 * Service, um Zugangsdaten anzulegen, umzuziehen und zu löschen. Deshalb hier ausnahmsweise direkt das fremde
	 * UsersRepository, und zwar nur lesend (existsById).
	 */
	private final UsersRepository usersRepository;

	/**
	 * Standard von Spring Security: BCrypt, das Verfahren steht vor dem Hash (z. B. {@code {bcrypt}$2a$10$...}).
	 * So lässt es sich später wechseln, ohne dass alte Passwörter ungültig werden.
	 */
	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	/** Nach einem neuen Passwort fliegen alle Sitzungen des Users, auch die eigene. */
	private final UsersRefreshTokenService usersRefreshTokenService;
	private final TokenRevocations tokenRevocations;

	public UsersCredentialsService(UsersCredentialsRepository usersCredentialsRepository,
			UsersRepository usersRepository, UsersRefreshTokenService usersRefreshTokenService,
			TokenRevocations tokenRevocations) {
		this.usersCredentialsRepository = usersCredentialsRepository;
		this.usersRepository = usersRepository;
		this.usersRefreshTokenService = usersRefreshTokenService;
		this.tokenRevocations = tokenRevocations;
	}

	/** Stimmt das Passwort (SHA-256 aus dem Browser)? Gibt es keine Zugangsdaten, ist die Antwort false. */
	@Transactional(readOnly = true)
	public boolean isUsersCredentialsPasswordValid(UUID usersGuid, String password) {
		return usersCredentialsRepository.findById(usersGuid)
				.map(usersCredentials -> passwordEncoder.matches(password, usersCredentials.getPasswordHash()))
				.orElse(false);
	}

	/** true, wenn es zu dieser guid noch keine Zugangsdaten gibt, z. B. für das Startpasswort beim Import. */
	@Transactional(readOnly = true)
	public boolean isUsersCredentialsAvailable(UUID usersGuid) {
		return !usersCredentialsRepository.existsById(usersGuid);
	}

	/**
	 * Hasht das Passwort und speichert die Zugangsdaten. Rückgabe {@code void}, damit der Hash den Service nie
	 * verlässt.
	 */
	@Transactional
	public void createUsersCredentials(UUID usersGuid, String password) {
		// save würde vorhandene Zugangsdaten sonst stillschweigend überschreiben
		if (usersCredentialsRepository.existsById(usersGuid)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Zugangsdaten gibt es schon");
		}
		String passwordHash = passwordEncoder.encode(password);
		usersCredentialsRepository.save(new UsersCredentials(usersGuid, passwordHash));
	}

	/**
	 * Admin setzt das Passwort oder setzt es zurück, das alte muss er nicht kennen. Gibt es noch keine Zugangsdaten,
	 * werden sie angelegt, sonst wird der Hash ersetzt. Gibt es den User nicht, kommt 404.
	 */
	@Transactional
	public void setUsersCredentialsPassword(UUID usersGuid, String newPassword) {
		// ZIRKELSCHLUSS: direkt das fremde UsersRepository, siehe Feld usersRepository
		if (!usersRepository.existsById(usersGuid)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein User mit dieser guid");
		}
		// setzen: noch keine Zugangsdaten, also neue. Zurücksetzen: die vorhandenen bekommen den neuen Hash.
		UsersCredentials usersCredentials = usersCredentialsRepository.findById(usersGuid)
				.orElseGet(() -> new UsersCredentials(usersGuid, null));
		usersCredentials.setPasswordHash(passwordEncoder.encode(newPassword));
		usersCredentialsRepository.save(usersCredentials);
		// Wer das alte Passwort kannte, soll mit seinen Sitzungen nicht weitermachen können
		usersRefreshTokenService.deleteAllUsersRefreshTokensOfUsers(usersGuid);
		tokenRevocations.revokeUsersTokens(usersGuid);
	}

	/**
	 * Der User ändert sein eigenes Passwort, das alte muss stimmen. Falsches altes Passwort und fehlende Zugangsdaten
	 * bekommen dieselbe Absage (400), damit von außen nicht zu unterscheiden ist, welcher Fall vorliegt.
	 * Danach fliegen alle Sitzungen des Users, auch die eigene.
	 */
	@Transactional
	public void changeUsersCredentialsPassword(UUID usersGuid, String oldPassword, String newPassword) {
		UsersCredentials usersCredentials = usersCredentialsRepository.findById(usersGuid)
				.filter(found -> passwordEncoder.matches(oldPassword, found.getPasswordHash()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "das alte Passwort stimmt nicht"));
		usersCredentials.setPasswordHash(passwordEncoder.encode(newPassword));
		usersCredentialsRepository.save(usersCredentials);
		usersRefreshTokenService.deleteAllUsersRefreshTokensOfUsers(usersGuid);
		tokenRevocations.revokeUsersTokens(usersGuid);
	}

	/**
	 * Zieht die Zugangsdaten auf die neue guid um, z. B. wenn der Admin die guid eines Users ändert. Die guid ist hier
	 * der PK und lässt sich in JPA nicht ändern: neue Zeile mit demselben Hash, alte löschen. Gibt es keine
	 * Zugangsdaten, passiert nichts.
	 */
	@Transactional
	public void changeUsersCredentialsGuid(UUID usersGuidCurrent, UUID usersGuidNew) {
		usersCredentialsRepository.findById(usersGuidCurrent).ifPresent(oldUsersCredentials -> {
			UsersCredentials newUsersCredentials = new UsersCredentials(usersGuidNew,
					oldUsersCredentials.getPasswordHash());
			newUsersCredentials.setPasswordExpiresAt(oldUsersCredentials.getPasswordExpiresAt());
			usersCredentialsRepository.delete(oldUsersCredentials);
			usersCredentialsRepository.save(newUsersCredentials);
		});
	}

	/** Löscht die Zugangsdaten eines Users. Gibt es keine, passiert nichts. */
	@Transactional
	public void deleteUsersCredentials(UUID usersGuid) {
		usersCredentialsRepository.deleteById(usersGuid);
	}

}
