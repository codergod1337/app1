package codergod1337.app1.system.user.service;

import codergod1337.app1.system.user.model.UsersSettings;
import codergod1337.app1.system.user.repository.UsersSettingsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
