package codergod1337.app1.system.user.service;

import codergod1337.app1.system.access.service.AccessRoleCollectionUsersAssignmentService;
import codergod1337.app1.system.access.service.AccessRoleUsersAssignmentService;
import codergod1337.app1.system.security.TokenRevocations;
import codergod1337.app1.system.security.login.service.UsersLogin2faService;
import codergod1337.app1.system.security.login.service.UsersRefreshTokenService;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.repository.UsersRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsersService {

	private final UsersRepository usersRepository;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Zugangsdaten anlegen, umziehen und löschen geht über diesen Service. Der
	 * UsersCredentialsService müsste umgekehrt hier fragen, ob es einen User gibt. Damit es keinen Kreis gibt, liest er
	 * stattdessen direkt das UsersRepository (dort kommentiert).
	 */
	private final UsersCredentialsService usersCredentialsService;
	private final UsersSettingsService usersSettingsService;
	private final UsersDetailsService usersDetailsService;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Die Zuordnungen User ↔ AR ziehen beim guid-Wechsel über diesen Service um und werden
	 * beim Löschen weggeräumt. Der AccessRoleUsersAssignmentService müsste umgekehrt hier fragen, ob es einen User gibt.
	 * Damit es keinen Kreis gibt, liest er stattdessen direkt das UsersRepository (dort kommentiert).
	 */
	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Die Zuordnung User ↔ ARC zieht beim guid-Wechsel über diesen Service um und wird beim
	 * Löschen weggeräumt. Der AccessRoleCollectionUsersAssignmentService müsste umgekehrt hier fragen, ob es einen User
	 * gibt. Damit es keinen Kreis gibt, liest er stattdessen direkt das UsersRepository (dort kommentiert).
	 */
	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;
	private final UsersRefreshTokenService usersRefreshTokenService;
	private final UsersLogin2faService usersLogin2faService;
	private final TokenRevocations tokenRevocations;

	public UsersService(UsersRepository usersRepository, UsersCredentialsService usersCredentialsService,
			UsersSettingsService usersSettingsService, UsersDetailsService usersDetailsService,
			AccessRoleUsersAssignmentService accessRoleUsersAssignmentService,
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService,
			UsersRefreshTokenService usersRefreshTokenService, UsersLogin2faService usersLogin2faService,
			TokenRevocations tokenRevocations) {
		this.usersRepository = usersRepository;
		this.usersCredentialsService = usersCredentialsService;
		this.usersSettingsService = usersSettingsService;
		this.usersDetailsService = usersDetailsService;
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
		this.usersRefreshTokenService = usersRefreshTokenService;
		this.usersLogin2faService = usersLogin2faService;
		this.tokenRevocations = tokenRevocations;
	}

	/**
	 * Legt einen User mit beliebig vielen Feldern an. Fehlen guid, createdAt oder status ({@code null}), vergibt sie
	 * der Konstruktor von {@link Users}.
	 */
	@Transactional
	public Users createUsers(UUID guid, String email, String username, String vorname, String nachname,
			OffsetDateTime createdAt, boolean serviceAccount, String status) {
		// Kleinschreibung, weil der Unique-Index sonst Paul@gmx.de und paul@gmx.de als zwei Adressen zulässt
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		String normalizedUsername = blankToNull(username);

		// Pflicht: save mit einer vorhandenen guid wäre ein Update und würde den bestehenden User überschreiben
		if (guid != null && !isGuidAvailable(guid)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "guid ist bereits vergeben");
		}
		if (!isEmailAvailable(normalizedEmail)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email ist bereits vergeben");
		}
		if (normalizedUsername != null && !isUsernameAvailable(normalizedUsername)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "username ist bereits vergeben");
		}

		Users users = new Users(guid, normalizedEmail, normalizedUsername, vorname, nachname, createdAt, serviceAccount,
				status);
		return usersRepository.save(users);
	}

	/**
	 * Registrierung: nur email und Passwort. guid und createdAt vergibt der Konstruktor von {@link Users}.
	 * Eine Transaktion: Scheitern die Zugangsdaten, wird auch der User zurückgerollt.
	 */
	@Transactional
	public Users createUsers(String email, String password) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		if (!isEmailAvailable(normalizedEmail)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email ist bereits vergeben");
		}
		Users users = usersRepository.save(new Users(normalizedEmail));
		usersCredentialsService.createUsersCredentials(users.getGuid(), password);
		return users;
	}

	/**
	 * PUT: ersetzt username, vorname und nachname eines vorhandenen Users, was fehlt, wird geleert.
	 * email, serviceAccount und status: {@code null} heißt unverändert. createdAt wird nie angefasst.
	 * Wechselt der Status weg von ACTIVE, enden sofort alle Sitzungen: Der Login prüft den Status, der Refresh nicht.
	 */
	@Transactional
	public Users updateUsers(UUID guid, String email, String username, String vorname, String nachname,
			Boolean serviceAccount, String status) {
		Users users = getUsersByGuid(guid);

		String newEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : users.getEmail();
		String newUsername = blankToNull(username);

		// Nur prüfen, wenn sich der Wert ändert, sonst stünde der User sich selbst im Weg
		if (!newEmail.equals(users.getEmail()) && !isEmailAvailable(newEmail)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email ist bereits vergeben");
		}
		if (newUsername != null && !newUsername.equals(users.getUsername()) && !isUsernameAvailable(newUsername)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "username ist bereits vergeben");
		}

		users.setEmail(newEmail);
		users.setUsername(newUsername);
		users.setVorname(vorname);
		users.setNachname(nachname);
		if (serviceAccount != null) {
			users.setServiceAccount(serviceAccount);
		}
		if (status != null && !status.equals(users.getStatus())) {
			users.setStatus(status);
			if (!"ACTIVE".equals(status)) {
				endAllUsersSessions(guid);
			}
		}
		return usersRepository.save(users);
	}

	/**
	 * Der User löscht sich selbst: Nur der Status wird DELETED, die Zeile bleibt (soft delete). Das Passwort (SHA-256
	 * aus dem Browser) muss stimmen, sonst 400. Danach enden alle Sitzungen, anmelden geht nicht mehr. Endgültig
	 * löschen kann nur der Admin.
	 */
	@Transactional
	public Users softDeleteUsers(UUID guid, String password) {
		Users users = getUsersByGuid(guid);
		if (!usersCredentialsService.isUsersCredentialsPasswordValid(guid, password)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "das Passwort stimmt nicht");
		}
		users.setStatus("DELETED");
		endAllUsersSessions(guid);
		return usersRepository.save(users);
	}

	/**
	 * Nur Admin: ändert die guid eines Users. Die ID lässt sich in JPA nicht ändern, deshalb wird der User unter der
	 * neuen guid neu angelegt und der alte gelöscht. Zugangsdaten, Einstellungen und Details ziehen über ihren eigenen
	 * Service mit um, der Status bleibt.
	 */
	@Transactional
	public Users changeUsersGuid(UUID guidCurrent, UUID guidNew) {
		if (guidCurrent.equals(guidNew)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guidNew ist gleich guidCurrent");
		}
		Users oldUsers = getUsersByGuid(guidCurrent);
		if (!isGuidAvailable(guidNew)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "guidNew ist bereits vergeben");
		}

		usersCredentialsService.changeUsersCredentialsGuid(guidCurrent, guidNew);
		usersSettingsService.changeAllUsersSettingsGuid(guidCurrent, guidNew);
		usersDetailsService.changeUsersDetailsGuid(guidCurrent, guidNew);
		accessRoleUsersAssignmentService.changeAllAccessRoleUsersAssignmentsGuid(guidCurrent, guidNew);
		accessRoleCollectionUsersAssignmentService.changeAccessRoleCollectionUsersAssignmentGuid(guidCurrent, guidNew);
		// Sitzungen ziehen nicht mit um: Das JWT trägt noch die alte guid, der User meldet sich neu an
		endAllUsersSessions(guidCurrent);

		// Der Status muss mit: Sonst wäre ein gesperrter User nach dem guid-Wechsel wieder ACTIVE
		Users newUsers = new Users(guidNew, oldUsers.getEmail(), oldUsers.getUsername(), oldUsers.getVorname(),
				oldUsers.getNachname(), oldUsers.getCreatedAt(), oldUsers.isServiceAccount(), oldUsers.getStatus());

		// Erst löschen und flushen: Hibernate schreibt sonst das Insert vor dem Delete, und der Unique-Index auf
		// email schlägt an, weil die alte Zeile noch dieselbe Adresse hat
		usersRepository.delete(oldUsers);
		usersRepository.flush();
		return usersRepository.save(newUsers);
	}

	/**
	 * Nur Admin, endgültig: löscht den User samt Einstellungen, Details und Zugangsdaten. Ohne FK-Constraints räumt die
	 * DB nichts selbst weg. Die User-Zeile zuletzt, damit bei einem Abbruch noch erkennbar ist, wozu die übrigen Zeilen
	 * gehören. Gibt den User zurück, wie er vor dem Löschen war.
	 */
	@Transactional
	public Users deleteUsers(UUID guid) {
		Users users = getUsersByGuid(guid);
		usersSettingsService.deleteAllUsersSettings(guid);
		usersDetailsService.deleteUsersDetails(guid);
		usersCredentialsService.deleteUsersCredentials(guid);
		accessRoleUsersAssignmentService.deleteAllAccessRoleUsersAssignmentsOfUsers(guid);
		accessRoleCollectionUsersAssignmentService.deleteAccessRoleCollectionUsersAssignmentOfUsers(guid);
		endAllUsersSessions(guid);
		usersRepository.delete(users);
		return users;
	}

	/** Gibt es den User nicht, kommt 404. */
	@Transactional(readOnly = true)
	public Users getUsersByGuid(UUID guid) {
		return usersRepository.findById(guid)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein User mit dieser guid"));
	}

	/**
	 * Für den Login: leer statt 404, damit „unbekannte email“ und „falsches Passwort“ dieselbe Absage bekommen.
	 * Groß- und Kleinschreibung der email spielt keine Rolle.
	 */
	@Transactional(readOnly = true)
	public Optional<Users> findUsersByEmail(String email) {
		return usersRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
	}

	/** Gibt es den User nicht, kommt 404. Groß- und Kleinschreibung der email spielt keine Rolle. */
	@Transactional(readOnly = true)
	public Users getUsersByEmail(String email) {
		return usersRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein User mit dieser email"));
	}

	@Transactional(readOnly = true)
	public List<Users> getAllUsers() {
		return usersRepository.findAll();
	}

	@Transactional(readOnly = true)
	public boolean isGuidAvailable(UUID guid) {
		return !usersRepository.existsById(guid);
	}

	@Transactional(readOnly = true)
	public boolean isEmailAvailable(String email) {
		return !usersRepository.existsByEmail(email);
	}

	@Transactional(readOnly = true)
	public boolean isUsernameAvailable(String username) {
		return !usersRepository.existsByUsername(username);
	}

	/** Alle Sitzungen des Users enden sofort: Ketten und offene Logins weg, laufende JWTs auf die Sperrliste. */
	private void endAllUsersSessions(UUID guid) {
		usersRefreshTokenService.deleteAllUsersRefreshTokensOfUsers(guid);
		usersLogin2faService.deleteAllUsersLogin2faOfUsers(guid);
		tokenRevocations.revokeUsersTokens(guid);
	}

	/** Leere Texte werden zu {@code null}, sonst gälte ein leerer username beim zweiten User schon als vergeben. */
	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
