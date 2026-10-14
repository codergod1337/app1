package codergod1337.app1.message.archive.controller;

import codergod1337.app1.message.archive.model.MessageArchivePreparation;
import codergod1337.app1.message.archive.model.MessageArchiveRequest;
import codergod1337.app1.message.archive.model.MessageArchiveResult;
import codergod1337.app1.message.archive.service.MessageArchivePreparationService;
import codergod1337.app1.message.archive.service.MessageArchiveService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Archivieren von Mails (User 2026-10-14). Der Pfad liegt unter /api/rest/v1/**, also angemeldet; die Rolle des
 * Archivierens und den Zugriff aufs Postfach prüft der Service bei jedem Aufruf. Postfach und Mail stehen im Body, nie in
 * der URL.
 */
@RestController
@RequestMapping("/api/rest/v1/message/archive")
public class MessageArchiveController {

	private final MessageArchivePreparationService messageArchivePreparationService;
	private final MessageArchiveService messageArchiveService;

	public MessageArchiveController(MessageArchivePreparationService messageArchivePreparationService,
			MessageArchiveService messageArchiveService) {
		this.messageArchivePreparationService = messageArchivePreparationService;
		this.messageArchiveService = messageArchiveService;
	}

	/**
	 * Archiviert eine Mail: {mailbox, exchangeMailId, folder, messageSubClassKey, wholeMail, hooks, attachments:
	 * [{sha256, action, fileSubClassKey}]}. Zurück die laufende Nummer und was mit den Anhängen geschah. 409, wenn die
	 * Mail schon im Archiv liegt.
	 */
	@PostMapping
	public MessageArchiveResult archiveMessage(@RequestBody MessageArchiveRequest request) {
		return messageArchiveService.archiveMessage(request);
	}

	/** {"mailbox": "…", "exchangeMailId": "…"}: alles für die Aufbereiten-Seite. Speichert nichts. */
	@PostMapping("/prepare")
	public MessageArchivePreparation prepareMessageArchive(@RequestBody Map<String, Object> body) {
		return messageArchivePreparationService.prepareMessageArchive(requiredText(body, "mailbox"),
				requiredText(body, "exchangeMailId"));
	}

	/**
	 * {"internetMessageIds": ["<a@b>", …]}: welche Mails der Postfachliste schon im Archiv liegen, als internetMessageId
	 * -> laufende Nummer. Fehlt eine, ist sie nicht archiviert.
	 */
	@PostMapping("/archived")
	public Map<String, String> getArchivedNachrichtenIds(@RequestBody Map<String, Object> body) {
		if (!(body.get("internetMessageIds") instanceof List<?> list)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "internetMessageIds fehlt oder ist keine Liste");
		}
		List<String> internetMessageIds = new ArrayList<>();
		for (Object item : list) {
			if (!(item instanceof String value)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "internetMessageIds enthält einen Eintrag, der kein Text ist");
			}
			internetMessageIds.add(value);
		}
		return messageArchiveService.getArchivedNachrichtenIds(internetMessageIds);
	}

	private static String requiredText(Map<String, Object> body, String key) {
		if (!(body.get(key) instanceof String value) || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " fehlt");
		}
		return value;
	}

}
