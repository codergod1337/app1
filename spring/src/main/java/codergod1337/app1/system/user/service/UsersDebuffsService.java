package codergod1337.app1.system.user.service;

import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.system.security.TokenRevocations;
import codergod1337.app1.system.security.login.service.UsersRefreshTokenService;
import codergod1337.app1.system.user.model.UsersDebuffType;
import codergod1337.app1.system.user.model.UsersDebuffs;
import codergod1337.app1.system.user.repository.UsersDebuffsRepository;
import codergod1337.app1.system.user.repository.UsersRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Debuffs eines Users (UsersDebuffs): Einschränkungen, die der Admin je User setzt. Ein Debuff ändert, was der User
 * aus Solr bekommt, deshalb schreibt ihn nur der Admin, nie der User selbst. Nach jeder Änderung enden alle Sitzungen des
 * Users: Das Flag "hat Debuffs" steht im Token und entsteht beim Login.
 */
@Service
public class UsersDebuffsService {

	private final UsersDebuffsRepository usersDebuffsRepository;

	/**
	 * ZIRKELSCHLUSS: Ob es den User gibt, müsste der UsersService sagen. Der ruft beim Löschen und beim guid-Wechsel diesen
	 * Service. Deshalb hier direkt das fremde UsersRepository, nur lesend (existsById).
	 */
	private final UsersRepository usersRepository;
	private final SolrFieldService solrFieldService;
	private final UsersRefreshTokenService usersRefreshTokenService;
	private final TokenRevocations tokenRevocations;

	public UsersDebuffsService(UsersDebuffsRepository usersDebuffsRepository, UsersRepository usersRepository,
			SolrFieldService solrFieldService, UsersRefreshTokenService usersRefreshTokenService,
			TokenRevocations tokenRevocations) {
		this.usersDebuffsRepository = usersDebuffsRepository;
		this.usersRepository = usersRepository;
		this.solrFieldService = solrFieldService;
		this.usersRefreshTokenService = usersRefreshTokenService;
		this.tokenRevocations = tokenRevocations;
	}

	/** Die Debuffs eines Users. Ohne Rollenprüfung: Die Auflösung ruft das für den angemeldeten User. */
	@Transactional(readOnly = true)
	public List<UsersDebuffs> getUsersDebuffsByUsersGuid(UUID usersGuid) {
		return usersDebuffsRepository.findByUsersGuid(usersGuid);
	}

	/** Hat der User mindestens einen Debuff? Für das Flag im Token beim Login. */
	@Transactional(readOnly = true)
	public boolean hasUsersDebuffs(UUID usersGuid) {
		return usersDebuffsRepository.existsByUsersGuid(usersGuid);
	}

	/**
	 * Neuer Debuff. 404 bei unbekanntem User, 400 bei unbekanntem Feld, bei einem Textfeld oder ohne Werte, 409 wenn der
	 * User für dieses Feld und diesen Typ schon einen hat. Danach enden alle Sitzungen des Users.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public UsersDebuffs createUsersDebuffs(UUID usersGuid, UsersDebuffType type, String solrFieldName,
			List<String> values) {
		// ZIRKELSCHLUSS: direkt das fremde UsersRepository, siehe Feld usersRepository
		if (usersGuid == null || !usersRepository.existsById(usersGuid)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein User mit dieser guid");
		}
		if (type == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type fehlt");
		}
		requireFilterableSolrFieldName(solrFieldName);
		List<String> checkedValues = checkedValues(values);
		boolean taken = usersDebuffsRepository.findByUsersGuid(usersGuid).stream()
				.anyMatch(debuff -> debuff.getType() == type && debuff.getSolrFieldName().equals(solrFieldName));
		if (taken) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"der User hat für " + solrFieldName + " schon einen Debuff " + type);
		}
		UsersDebuffs saved = usersDebuffsRepository.save(new UsersDebuffs(usersGuid, type, solrFieldName, checkedValues));
		endAllUsersSessions(usersGuid);
		return saved;
	}

	/** Neue Werte für einen Debuff, Typ und Feld bleiben. 400 ohne Werte. Danach enden alle Sitzungen des Users. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public UsersDebuffs changeUsersDebuffsValues(Long id, List<String> values) {
		UsersDebuffs usersDebuffs = findUsersDebuffsForWrite(id);
		usersDebuffs.setValues(checkedValues(values));
		UsersDebuffs saved = usersDebuffsRepository.save(usersDebuffs);
		endAllUsersSessions(saved.getUsersGuid());
		return saved;
	}

	/** Löscht einen Debuff. Danach enden alle Sitzungen des Users. Gibt den Debuff zurück, wie er vorher war. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public UsersDebuffs deleteUsersDebuffs(Long id) {
		UsersDebuffs usersDebuffs = findUsersDebuffsForWrite(id);
		usersDebuffsRepository.delete(usersDebuffs);
		endAllUsersSessions(usersDebuffs.getUsersGuid());
		return usersDebuffs;
	}

	/** Hängt alle Debuffs des Users an die neue guid, wenn der Admin die guid eines Users ändert. */
	@Transactional
	public void changeAllUsersDebuffsGuid(UUID usersGuidCurrent, UUID usersGuidNew) {
		for (UsersDebuffs usersDebuffs : usersDebuffsRepository.findByUsersGuid(usersGuidCurrent)) {
			usersDebuffs.setUsersGuid(usersGuidNew);
		}
	}

	/** Löscht alle Debuffs eines Users, beim Löschen des Users. */
	@Transactional
	public void deleteAllUsersDebuffs(UUID usersGuid) {
		usersDebuffsRepository.deleteByUsersGuid(usersGuid);
	}

	/** Ein Feld oder Hook, nach dem sich exakt filtern lässt: string, Zahl, Datum, boolean. Kein Textfeld. */
	private void requireFilterableSolrFieldName(String solrFieldName) {
		if (solrFieldName == null || solrFieldName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "solrFieldName fehlt");
		}
		// Hooks liegen als Zeilen in solr_field, eine Suche nach dem Namen findet beides
		SolrField solrField = solrFieldService.findSolrFieldByName(solrFieldName);
		if (solrField == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, solrFieldName + " ist weder Feld noch Hook");
		}
		if (solrField.getType().isText()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					solrFieldName + " ist ein Textfeld, danach lässt sich nicht exakt filtern");
		}
	}

	/** Mindestens ein Wert, keiner leer, keine Doppelten, Leerraum am Rand weg */
	private static List<String> checkedValues(List<String> values) {
		List<String> checked = new ArrayList<>();
		if (values != null) {
			for (String value : values) {
				if (value == null || value.isBlank()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ein Wert ist leer");
				}
				String trimmed = value.trim();
				if (!checked.contains(trimmed)) {
					checked.add(trimmed);
				}
			}
		}
		if (checked.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "values fehlt oder ist leer");
		}
		return checked;
	}

	private UsersDebuffs findUsersDebuffsForWrite(Long id) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		return usersDebuffsRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Debuff mit dieser id"));
	}

	/** Alle Sitzungen des Users beenden: Refresh-Tokens weg, laufende JWTs gesperrt. Das Flag im Token entsteht beim Login neu. */
	private void endAllUsersSessions(UUID usersGuid) {
		usersRefreshTokenService.deleteAllUsersRefreshTokensOfUsers(usersGuid);
		tokenRevocations.revokeUsersTokens(usersGuid);
	}

}
