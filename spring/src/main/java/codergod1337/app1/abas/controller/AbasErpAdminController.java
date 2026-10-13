package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.service.AbasErpRestClientService;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Was das Frontend über den ABAS-Server wissen darf: die Basis-URL für die Vorschau einer Anfrage im Mapping-Tab.
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** sichert ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/abas/erp")
public class AbasErpAdminController {

	private final AbasErpRestClientService abasErpRestClientService;

	public AbasErpAdminController(AbasErpRestClientService abasErpRestClientService) {
		this.abasErpRestClientService = abasErpRestClientService;
	}

	/** {"baseUrl": "https://host:4443/mw/r/mandant"}, baseUrl null, wenn ABAS nicht eingerichtet ist */
	@GetMapping("/base-url")
	public Map<String, String> getAbasErpBaseUrl() {
		return Collections.singletonMap("baseUrl", abasErpRestClientService.getAbasErpBaseUrl());
	}

}
