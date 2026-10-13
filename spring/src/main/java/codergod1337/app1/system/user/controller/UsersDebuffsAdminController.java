package codergod1337.app1.system.user.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.user.model.UsersDebuffType;
import codergod1337.app1.system.user.model.UsersDebuffs;
import codergod1337.app1.system.user.service.UsersDebuffsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin setzt die Debuffs eines Users, in der Maske User bearbeiten. Keine Admin-Prüfung hier: Der Pfad
 * /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/users/debuffs")
public class UsersDebuffsAdminController {

	private final UsersDebuffsService usersDebuffsService;

	public UsersDebuffsAdminController(UsersDebuffsService usersDebuffsService) {
		this.usersDebuffsService = usersDebuffsService;
	}

	/** Die Debuffs eines Users, ohne Zeilen eine leere Liste. */
	@GetMapping("/{guid}")
	public List<UsersDebuffs> getUsersDebuffs(@PathVariable String guid) {
		if (!HelperInputs.isValidGuid(guid)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guid ist ungültig");
		}
		return usersDebuffsService.getUsersDebuffsByUsersGuid(UUID.fromString(guid));
	}

	/** Neuer Debuff: {"usersGuid": "…", "type": "RESOLVE", "solrFieldName": "kundenNummer", "values": ["4711"]} */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UsersDebuffs createUsersDebuffs(@RequestBody Map<String, Object> newUsersDebuffsData) {
		String guid = newUsersDebuffsData.get("usersGuid") instanceof String guidValue ? guidValue : null;
		if (!HelperInputs.isValidGuid(guid)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid fehlt oder ist ungültig");
		}
		String solrFieldName = newUsersDebuffsData.get("solrFieldName") instanceof String nameValue ? nameValue : null;
		return usersDebuffsService.createUsersDebuffs(UUID.fromString(guid), readType(newUsersDebuffsData),
				solrFieldName, readValues(newUsersDebuffsData));
	}

	/** Neue Werte: {"id": 7, "values": ["4711", "4712"]}. Typ und Feld ändern sich nie. */
	@PutMapping
	public UsersDebuffs changeUsersDebuffsValues(@RequestBody Map<String, Object> changedUsersDebuffsData) {
		return usersDebuffsService.changeUsersDebuffsValues(readId(changedUsersDebuffsData),
				readValues(changedUsersDebuffsData));
	}

	/** Löschen: {"id": 7}. Zurück kommt der Debuff, wie er vorher war. */
	@DeleteMapping
	public UsersDebuffs deleteUsersDebuffs(@RequestBody Map<String, Object> usersDebuffsToDelete) {
		return usersDebuffsService.deleteUsersDebuffs(readId(usersDebuffsToDelete));
	}

	private static Long readId(Map<String, Object> body) {
		if (!(body.get("id") instanceof Number id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt oder ist keine Zahl");
		}
		return id.longValue();
	}

	private static UsersDebuffType readType(Map<String, Object> body) {
		if (!(body.get("type") instanceof String type)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type fehlt");
		}
		try {
			return UsersDebuffType.valueOf(type);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type " + type + " ist unbekannt");
		}
	}

	/** values: eine Liste von Texten; ein einzelner Text zählt als Liste mit einem Eintrag */
	private static List<String> readValues(Map<String, Object> body) {
		Object raw = body.get("values");
		if (raw instanceof String single) {
			return List.of(single);
		}
		if (!(raw instanceof List<?> list)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "values fehlt oder ist keine Liste");
		}
		List<String> values = new ArrayList<>();
		for (Object entry : list) {
			if (!(entry instanceof String text)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "values enthält einen Eintrag, der kein Text ist");
			}
			values.add(text);
		}
		return values;
	}

}
