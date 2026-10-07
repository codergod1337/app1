package codergod1337.app1.graph.mail.controller;

import codergod1337.app1.graph.mail.model.GraphMailTemplateTypeForClient;
import codergod1337.app1.graph.mail.service.GraphMailTemplateService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Die Vorlagentypen mit ihren Platzhaltergruppen, für die Auswahl und die Hinweise im Editor des GraphManagers. */
@RestController
@RequestMapping("/api/rest/v1/admin/graph/mailtemplatetype")
public class GraphMailTemplateTypeAdminController {

	private final GraphMailTemplateService graphMailTemplateService;

	public GraphMailTemplateTypeAdminController(GraphMailTemplateService graphMailTemplateService) {
		this.graphMailTemplateService = graphMailTemplateService;
	}

	@GetMapping
	public List<GraphMailTemplateTypeForClient> getAllGraphMailTemplateTypes() {
		return graphMailTemplateService.getAllGraphMailTemplateTypes();
	}

}
