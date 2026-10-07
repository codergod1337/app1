package codergod1337.app1.graph.mail.controller;

import codergod1337.app1.graph.mail.model.GraphMailTemplate;
import codergod1337.app1.graph.mail.model.GraphMailTemplatePreview;
import codergod1337.app1.graph.mail.model.GraphMailTemplateType;
import codergod1337.app1.graph.mail.service.GraphMailTemplateService;
import codergod1337.app1.system.HelperInputs;
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
 * Der Admin pflegt die Mailvorlagen im GraphManager. Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und
 * @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/graph/mailtemplate")
public class GraphMailTemplateAdminController {

	private final GraphMailTemplateService graphMailTemplateService;

	public GraphMailTemplateAdminController(GraphMailTemplateService graphMailTemplateService) {
		this.graphMailTemplateService = graphMailTemplateService;
	}

	/** Alle Vorlagen in Listenreihenfolge. */
	@GetMapping
	public List<GraphMailTemplate> getAllGraphMailTemplates() {
		return graphMailTemplateService.getAllGraphMailTemplates();
	}

	/** Alle Felder. key, Typ und Platzhalter prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GraphMailTemplate createGraphMailTemplate(@Valid @RequestBody GraphMailTemplate newGraphMailTemplateData) {
		return graphMailTemplateService.createGraphMailTemplate(newGraphMailTemplateData.getKey(),
				newGraphMailTemplateData.getType(), newGraphMailTemplateData.getDisplayName(),
				newGraphMailTemplateData.getDescription(), newGraphMailTemplateData.getSubject(),
				newGraphMailTemplateData.getHtmlBody(), newGraphMailTemplateData.getListingPosition());
	}

	/** Alle Felder. Der key im Body bestimmt, welche Vorlage geändert wird, key und Typ ändern sich nie. */
	@PutMapping
	public GraphMailTemplate updateGraphMailTemplate(@Valid @RequestBody GraphMailTemplate changedGraphMailTemplateData) {
		return graphMailTemplateService.updateGraphMailTemplate(changedGraphMailTemplateData.getKey(),
				changedGraphMailTemplateData.getDisplayName(), changedGraphMailTemplateData.getDescription(),
				changedGraphMailTemplateData.getSubject(), changedGraphMailTemplateData.getHtmlBody(),
				changedGraphMailTemplateData.getListingPosition());
	}

	/** Neue Positionen nach Drag and Drop: jeder key bekommt seine Position, {"LOGIN_2FA": 1}. Zurück kommen alle. */
	@PostMapping("/position")
	public List<GraphMailTemplate> changeGraphMailTemplatePositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			if (!HelperInputs.isValidKey(newPosition.getKey())
					|| graphMailTemplateService.isGraphMailTemplateKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND,
						"keine Mailvorlage mit key " + newPosition.getKey());
			}
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		return graphMailTemplateService.changeGraphMailTemplatePositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit der gelöschten Vorlage, 400 bei ungültigem key, 404 wenn es sie nicht gibt. */
	@DeleteMapping
	public GraphMailTemplate deleteGraphMailTemplate(@RequestBody Map<String, Object> graphMailTemplateToDelete) {
		String key = graphMailTemplateToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return graphMailTemplateService.deleteGraphMailTemplate(key);
	}

	/**
	 * Das Muster: {type, language, subject, htmlBody} so, wie sie gerade im Formular stehen, zurück kommen Betreff und
	 * Text in dieser Sprache mit Beispielwerten. Nichts wird gespeichert oder verschickt. 400 bei fehlendem Typ.
	 */
	@PostMapping("/preview")
	public GraphMailTemplatePreview renderGraphMailTemplatePreview(@RequestBody Map<String, Object> previewData) {
		GraphMailTemplateType type;
		try {
			type = GraphMailTemplateType.valueOf(previewData.get("type") instanceof String typeValue ? typeValue : "");
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type fehlt oder ist unbekannt");
		}
		String language = previewData.get("language") instanceof String languageValue ? languageValue : "de";
		String subject = previewData.get("subject") instanceof String subjectValue ? subjectValue : "";
		String htmlBody = previewData.get("htmlBody") instanceof String htmlBodyValue ? htmlBodyValue : "";
		return graphMailTemplateService.renderGraphMailTemplatePreview(type, language, subject, htmlBody);
	}

}
