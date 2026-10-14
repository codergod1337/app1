package codergod1337.app1.message.messagesubclass.controller;

import codergod1337.app1.message.messagesubclass.model.MessageSubClass;
import codergod1337.app1.message.messagesubclass.service.MessageSubClassService;
import codergod1337.app1.system.HelperInputs;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin legt Nachrichtenarten an und pflegt sie (User 2026-10-13). Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/messagesubclass")
public class MessageSubClassAdminController {

	private final MessageSubClassService messageSubClassService;

	public MessageSubClassAdminController(MessageSubClassService messageSubClassService) {
		this.messageSubClassService = messageSubClassService;
	}

	/** Alle Felder. Den key prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MessageSubClass createMessageSubClass(@Valid @RequestBody MessageSubClass newMessageSubClassData) {
		return messageSubClassService.createMessageSubClass(newMessageSubClassData.getKey(),
				newMessageSubClassData.getDisplayName(), newMessageSubClassData.getDescription(),
				newMessageSubClassData.getListingPosition(), newMessageSubClassData.getSymbol(),
				newMessageSubClassData.getColor(), newMessageSubClassData.getReadAccessRoleKeys(),
				newMessageSubClassData.getWriteAccessRoleKeys(), newMessageSubClassData.getDisplayContextKeys(),
				newMessageSubClassData.getMailboxEmailAddresses(), newMessageSubClassData.getTriggerWords(),
				newMessageSubClassData.getTriggerHookKeys());
	}

	/** Alle Felder. Der key im Body bestimmt, welche Art geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public MessageSubClass updateMessageSubClass(@Valid @RequestBody MessageSubClass changedMessageSubClassData) {
		return messageSubClassService.updateMessageSubClass(changedMessageSubClassData.getKey(),
				changedMessageSubClassData.getDisplayName(), changedMessageSubClassData.getDescription(),
				changedMessageSubClassData.getListingPosition(), changedMessageSubClassData.getSymbol(),
				changedMessageSubClassData.getColor(), changedMessageSubClassData.getReadAccessRoleKeys(),
				changedMessageSubClassData.getWriteAccessRoleKeys(), changedMessageSubClassData.getDisplayContextKeys(),
				changedMessageSubClassData.getMailboxEmailAddresses(), changedMessageSubClassData.getTriggerWords(),
				changedMessageSubClassData.getTriggerHookKeys());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"ANGEBOT": 1, "REKLAMATION": 2}.
	 * Das Frontend zählt durch. Zurück kommen alle Arten in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<MessageSubClass> changeMessageSubClassPositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es die Art zu diesem key?
			if (messageSubClassService.isMessageSubClassKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Nachrichtenart mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return messageSubClassService.changeMessageSubClassPositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit der gelöschten Art, 400 bei ungültigem key, 404 wenn es sie nicht gibt. */
	@DeleteMapping
	public MessageSubClass deleteMessageSubClass(@RequestBody Map<String, Object> messageSubClassToDelete) {
		String key = messageSubClassToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return messageSubClassService.deleteMessageSubClass(key);
	}

}
