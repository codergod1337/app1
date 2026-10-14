package codergod1337.app1.graph.mailbox.controller;

import codergod1337.app1.graph.mailbox.model.GraphMailFolder;
import codergod1337.app1.graph.mailbox.model.GraphMailHeader;
import codergod1337.app1.graph.mailbox.model.GraphMailView;
import codergod1337.app1.graph.mailbox.model.MailboxesOfCurrentUser;
import codergod1337.app1.graph.mailbox.service.GraphMailboxReadService;
import codergod1337.app1.graph.mailbox.service.MailboxService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Die Postfächer des angemeldeten Users und ihre Mails (User 2026-10-13/14). Der Pfad liegt unter /api/rest/v1/**,
 * also angemeldet; die Rolle des Archivierens und welche Postfächer er hat, prüft der Service bei jedem Aufruf. Die
 * Postfachadresse steht im Body, nie in der URL (dort nur guid und Long-id), deshalb POST auch zum Lesen.
 */
@RestController
@RequestMapping("/api/rest/v1/graph/mailbox")
public class MailboxController {

	private final MailboxService mailboxService;
	private final GraphMailboxReadService graphMailboxReadService;

	public MailboxController(MailboxService mailboxService, GraphMailboxReadService graphMailboxReadService) {
		this.mailboxService = mailboxService;
		this.graphMailboxReadService = graphMailboxReadService;
	}

	/** Seine Dienstpostfächer und daneben seine eigene E-Mail, wenn sie auf die Domäne endet. 403 ohne die Rolle. */
	@GetMapping("/mine")
	public MailboxesOfCurrentUser getMailboxesOfCurrentUser() {
		return mailboxService.getMailboxesOfCurrentUser();
	}

	/** {"mailbox": "…", "folder": "INBOX" | "SENT_ITEMS"}: die neuesten Mails des Ordners. 400 bei fehlendem Wert. */
	@PostMapping("/headers")
	public List<GraphMailHeader> getMailHeaders(@RequestBody Map<String, Object> body) {
		if (!(body.get("folder") instanceof String folderName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "folder fehlt");
		}
		GraphMailFolder folder;
		try {
			folder = GraphMailFolder.valueOf(folderName);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "folder ist INBOX oder SENT_ITEMS");
		}
		return graphMailboxReadService.getMailHeaders(requiredText(body, "mailbox"), folder);
	}

	/** {"mailbox": "…", "exchangeMailId": "…"}: eine Mail mit allen Feldern, das Graph-JSON unverändert. */
	@PostMapping("/mail")
	public JsonNode getMail(@RequestBody Map<String, Object> body) {
		return graphMailboxReadService.getMail(requiredText(body, "mailbox"), requiredText(body, "exchangeMailId"));
	}

	/**
	 * {"mailbox": "…", "exchangeMailId": "…"}: eine Mail zum Anzeigen, Kopf, ganze Mail und neuer Teil als HTML mit
	 * eingesetzten Bildern, die Anhänge ohne Bytes.
	 */
	@PostMapping("/mail/view")
	public GraphMailView getMailView(@RequestBody Map<String, Object> body) {
		return graphMailboxReadService.getMailView(requiredText(body, "mailbox"), requiredText(body, "exchangeMailId"));
	}

	private static String requiredText(Map<String, Object> body, String key) {
		if (!(body.get(key) instanceof String value) || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " fehlt");
		}
		return value;
	}

}
