package codergod1337.app1.system.user.controller;

import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.UsersSettings;
import codergod1337.app1.system.user.service.UsersSettingsService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der User-Weg zu {@link UsersSettings}: jeder mit gültigem Token, jeder nur bei sich selbst. Keiner dieser Einstiege
 * nimmt eine guid entgegen, sie kommt aus dem Token. Es gibt hier also keine Möglichkeit, an fremden Einstellungen zu
 * drehen. Der Pfad liegt unter /api/rest/v1/**, also angemeldet, ohne Admin.
 */
@RestController
@RequestMapping("/api/rest/v1/userssettings")
public class UsersSettingsController {

	private final UsersSettingsService usersSettingsService;
	private final CurrentUsersProvider currentUsersProvider;

	public UsersSettingsController(UsersSettingsService usersSettingsService,
			CurrentUsersProvider currentUsersProvider) {
		this.usersSettingsService = usersSettingsService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/** Die eigenen Einstellungen, ohne Zeilen eine leere Liste. */
	@GetMapping
	public List<UsersSettings> getUsersSettings() {
		return usersSettingsService.getUsersSettingsByUsersGuid(currentUsersProvider.getCurrentUsersGuid());
	}

	/** Einen eigenen Wert setzen: {"key": "LANGUAGE", "value": "de"}. Die Zeile entsteht beim ersten Setzen. 400 bei ungültigem key. */
	@PutMapping
	public UsersSettings changeUsersSettings(@RequestBody Map<String, Object> changedUsersSettingsData) {
		String key = readKey(changedUsersSettingsData);
		Object rawValue = changedUsersSettingsData.get("value");
		if (rawValue != null && !(rawValue instanceof String)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value ist kein Text");
		}
		return usersSettingsService.changeUsersSettings(currentUsersProvider.getCurrentUsersGuid(), key,
				(String) rawValue);
	}

	/** Einen eigenen Wert entfernen: {"key": "LANGUAGE"}. Danach gilt wieder der Standard des Frontends. */
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteUsersSettings(@RequestBody Map<String, Object> usersSettingsToDelete) {
		usersSettingsService.deleteUsersSettings(currentUsersProvider.getCurrentUsersGuid(),
				readKey(usersSettingsToDelete));
	}

	/** Der key ist Pflicht, das Format prüft der Service. */
	private static String readKey(Map<String, Object> body) {
		if (!(body.get("key") instanceof String key) || key.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt");
		}
		return key;
	}

}
