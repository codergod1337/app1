package codergod1337.app1.graph.mailbox.controller;

import codergod1337.app1.graph.mailbox.model.Mailbox;
import codergod1337.app1.graph.mailbox.service.MailboxService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin legt Postfächer an und pflegt sie (User 2026-10-13). Die ganze Liste gibt es nur hier, jeder andere User
 * bekommt seine Postfächer über MailboxController.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/graph/mailbox")
public class MailboxAdminController {

	private final MailboxService mailboxService;

	public MailboxAdminController(MailboxService mailboxService) {
		this.mailboxService = mailboxService;
	}

	/** Alle Postfächer, sortiert nach Position und Adresse */
	@GetMapping
	public List<Mailbox> getAllMailboxes() {
		return mailboxService.getAllMailboxes();
	}

	/** Alle Felder. Die Adresse prüft und normalisiert der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Mailbox createMailbox(@Valid @RequestBody Mailbox newMailboxData) {
		return mailboxService.createMailbox(newMailboxData.getEmailAddress(), newMailboxData.getDisplayName(),
				newMailboxData.getDescription(), newMailboxData.getListingPosition(),
				newMailboxData.getAccessRoleCollectionKeys());
	}

	/** Alle Felder. Die Adresse im Body bestimmt, welches Postfach geändert wird, sie selbst ändert sich nie. */
	@PutMapping
	public Mailbox updateMailbox(@Valid @RequestBody Mailbox changedMailboxData) {
		return mailboxService.updateMailbox(changedMailboxData.getEmailAddress(), changedMailboxData.getDisplayName(),
				changedMailboxData.getDescription(), changedMailboxData.getListingPosition(),
				changedMailboxData.getAccessRoleCollectionKeys());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jede Adresse bekommt ihre Position,
	 * {"bestellung@firma.de": 1, "info@firma.de": 2}. Zurück kommen alle Postfächer in neuer Reihenfolge.
	 */
	@PostMapping("/position")
	public List<Mailbox> changeMailboxPositions(@RequestBody Map<String, Object> newPositionByEmailAddress) {
		Map<String, Integer> checkedPositionByEmailAddress = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByEmailAddress.entrySet()) {
			// 1. gibt es das Postfach zu dieser Adresse?
			if (mailboxService.isMailboxEmailAddressAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Postfach " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByEmailAddress.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return mailboxService.changeMailboxPositions(checkedPositionByEmailAddress);
	}

	/** Nur per Adresse: {"emailAddress": "…"}. 200 mit dem gelöschten Postfach, 400 ohne Adresse, 404 wenn es sie nicht gibt. */
	@DeleteMapping
	public Mailbox deleteMailbox(@RequestBody Map<String, Object> mailboxToDelete) {
		if (!(mailboxToDelete.get("emailAddress") instanceof String emailAddress) || emailAddress.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "emailAddress fehlt");
		}
		return mailboxService.deleteMailbox(emailAddress);
	}

}
