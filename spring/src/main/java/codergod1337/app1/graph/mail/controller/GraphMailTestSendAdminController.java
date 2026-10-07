package codergod1337.app1.graph.mail.controller;

import codergod1337.app1.graph.mail.model.GraphMailMessage;
import codergod1337.app1.graph.mail.model.GraphMailTestSendRequest;
import codergod1337.app1.graph.mail.service.GraphMailSendService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der Admin schickt eine Test-Mail über Graph, um Zertifikat, Berechtigung und Absenderpostfach zu prüfen. Absender,
 * Empfänger, Betreff und Text kommen im Body, keine Vorlage. Keine Admin-Prüfung hier: Der Pfad
 * /api/rest/v1/admin/** sichert ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/graph/testmail")
public class GraphMailTestSendAdminController {

	private final GraphMailSendService graphMailSendService;

	public GraphMailTestSendAdminController(GraphMailSendService graphMailSendService) {
		this.graphMailSendService = graphMailSendService;
	}

	/** 200 ohne Inhalt, wenn Graph die Mail angenommen hat. 400 bei fehlenden Feldern, 503 wenn Graph ablehnt. */
	@PostMapping
	public void sendGraphTestMail(@RequestBody GraphMailTestSendRequest testMail) {
		if (!isEmailAddress(testMail.senderAddress())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "senderAddress fehlt oder ist keine Adresse");
		}
		if (!isEmailAddress(testMail.recipientEmail())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientEmail fehlt oder ist keine Adresse");
		}
		if (testMail.subject() == null || testMail.subject().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject fehlt");
		}
		if (testMail.htmlBody() == null || testMail.htmlBody().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "htmlBody fehlt");
		}
		graphMailSendService.sendGraphMail(new GraphMailMessage(testMail.senderAddress().trim(),
				testMail.recipientEmail().trim(), testMail.subject(), testMail.htmlBody()));
	}

	private static boolean isEmailAddress(String address) {
		return address != null && address.contains("@") && !address.isBlank();
	}

}
