package codergod1337.app1.system.user.service;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.user.model.UsersSettings;
import codergod1337.app1.system.user.repository.UsersSettingsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Anzeigeeinstellungen eines Users als Schlüssel-Wert-Paare, z. B. LANGUAGE. Wie in der Vorlage: Es gibt keinen
 * Gast-User und keine Vorgabe-Zeilen. Eine Einstellung existiert nur, wenn der User sie selbst gesetzt hat. Fehlt sie,
 * gilt der eingebaute Standard des Frontends, denn nur das Frontend muss sie darstellen.
 *
 * Hier gehört nur Anzeige hinein und nie ein Wert, der über Zugriff entscheidet: Jeder User schreibt seine
 * Einstellungen selbst, wer seine Keys selbst setzen kann, gäbe sich sonst Rechte. Wer fragt, sagt der Controller
 * (guid aus dem Token), dieser Service bekommt die guid als Parameter, weil ihn auch Import und Userverwaltung rufen.
 */
@Service
public class UsersSettingsService {

	private final UsersSettingsRepository usersSettingsRepository;

	public UsersSettingsService(UsersSettingsRepository usersSettingsRepository) {
		this.usersSettingsRepository = usersSettingsRepository;
	}

	/** Alle Einstellungen aller User, z. B. für den Export der Stammdaten. */
	@Transactional(readOnly = true)
	public List<UsersSettings> getAllUsersSettings() {
		return usersSettingsRepository.findAll();
	}

	/** Die Einstellungen eines Users, z. B. die eigenen nach dem Login. Ohne Zeilen eine leere Liste. */
	@Transactional(readOnly = true)
	public List<UsersSettings> getUsersSettingsByUsersGuid(UUID usersGuid) {
		return usersSettingsRepository.findByUsersGuid(usersGuid);
	}

	/**
	 * Setzt einen Wert und legt die Zeile an, wenn es sie noch nicht gibt: Eine Einstellung entsteht in dem Augenblick,
	 * in dem sie zum ersten Mal gesetzt wird, ein getrennter Anlegeweg hieße nur, dass jeder Aufrufer vorher fragen
	 * müsste. 400 bei ungültigem key (normale Key-Regel, z. B. LANGUAGE). Der Wert darf leer sein: eine Einstellung ohne
	 * Inhalt ist etwas anderes als eine, die es nicht gibt.
	 */
	@Transactional
	public UsersSettings changeUsersSettings(UUID usersGuid, String key, String value) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		UsersSettings usersSettings = usersSettingsRepository.findByUsersGuidAndKey(usersGuid, key)
				.orElseGet(() -> new UsersSettings(usersGuid, key, null));
		usersSettings.setValue(value);
		return usersSettingsRepository.save(usersSettings);
	}

	/**
	 * Entfernt eine Einstellung, danach gilt wieder der Standard des Frontends. Keine Zeile zu treffen ist kein Fehler,
	 * das Ziel ist dann ohnehin erreicht.
	 */
	@Transactional
	public void deleteUsersSettings(UUID usersGuid, String key) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		usersSettingsRepository.deleteByUsersGuidAndKey(usersGuid, key);
	}

	/**
	 * Neue Einstellung, z. B. beim Import. 400 bei leerem oder zu langem key, 409 wenn der User diesen key schon hat:
	 * Vorhandenes wird nie überschrieben.
	 */
	@Transactional
	public UsersSettings createUsersSettings(UUID usersGuid, String key, String value) {
		if (key == null || key.isBlank() || key.length() > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist länger als 200 Zeichen");
		}
		boolean taken = usersSettingsRepository.findByUsersGuid(usersGuid).stream()
				.anyMatch(usersSettings -> usersSettings.getKey().equals(key));
		if (taken) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Einstellung " + key + " gibt es schon");
		}
		return usersSettingsRepository.save(new UsersSettings(usersGuid, key, value));
	}

	/** Hängt alle Einstellungen des Users an die neue guid, z. B. wenn der Admin die guid eines Users ändert. */
	@Transactional
	public void changeAllUsersSettingsGuid(UUID usersGuidCurrent, UUID usersGuidNew) {
		for (UsersSettings usersSettings : usersSettingsRepository.findByUsersGuid(usersGuidCurrent)) {
			usersSettings.setUsersGuid(usersGuidNew);
		}
	}

	/** Löscht alle Einstellungen eines Users. */
	@Transactional
	public void deleteAllUsersSettings(UUID usersGuid) {
		usersSettingsRepository.deleteByUsersGuid(usersGuid);
	}

}
