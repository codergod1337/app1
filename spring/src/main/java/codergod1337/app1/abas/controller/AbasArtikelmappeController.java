package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasArtikelmappeInfosysRequest;
import codergod1337.app1.abas.model.AbasArtikelmappeSternzeitRequest;
import codergod1337.app1.abas.model.AbasBelegnummerLookupResult;
import codergod1337.app1.abas.model.AbasInfosys;
import codergod1337.app1.abas.service.AbasArtikelmappeNachladeService;
import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.system.HelperInputs;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Nachladen der Artikelmappe live bei ABAS (User 2026-10-11). Der Pfad liegt unter /api/rest/v1/**, also
 * angemeldet; die Tür ARTIKEL und die Feldgrenze des Users prüft der Service über das Dokument des Artikels. Hier nur
 * die Form des Bodys.
 */
@RestController
@RequestMapping("/api/rest/v1/abas/artikelmappe")
public class AbasArtikelmappeController {

	/** Eine Artikelnummer, wie der SolrDocumentArtikelController sie prüft */
	private static final String ARTICLE_NUMBER_PATTERN = "[A-Za-z0-9._-]{1,64}";

	/** Ein Solr-Feldname */
	private static final String FIELD_NAME_PATTERN = "[A-Za-z][A-Za-z0-9_]{0,99}";

	private final AbasArtikelmappeNachladeService abasArtikelmappeNachladeService;

	public AbasArtikelmappeController(AbasArtikelmappeNachladeService abasArtikelmappeNachladeService) {
		this.abasArtikelmappeNachladeService = abasArtikelmappeNachladeService;
	}

	/** Die Aufrufe mit dem Anzeigeort ARTIKELMAPPE: je einer ein Tab der Mappe */
	@GetMapping("/infosys")
	public List<AbasInfosys> getAbasInfosysListForArtikelmappe() {
		return abasArtikelmappeNachladeService.getAbasInfosysListForArtikelmappe();
	}

	/** Body {@link AbasArtikelmappeInfosysRequest}: {"artikelNummer": "10502057", "abasInfosysKey": "PRODLIST"} */
	@PostMapping("/infosys")
	public ResponseMappingExecutionResult fetchAndMapAbasInfosysForArtikel(
			@RequestBody AbasArtikelmappeInfosysRequest infosysRequest) {
		requireArticleNumber(infosysRequest.artikelNummer());
		if (!HelperInputs.isValidKey(infosysRequest.abasInfosysKey())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abasInfosysKey fehlt oder ist ungültig");
		}
		return abasArtikelmappeNachladeService.fetchAndMapAbasInfosysForArtikel(infosysRequest.abasInfosysKey(),
				infosysRequest.artikelNummer());
	}

	/** Body {@link AbasArtikelmappeSternzeitRequest}: {"artikelNummer": "10502057", "fieldName": "yqmappqmpruefv"} */
	@PostMapping("/sternzeit")
	public ResponseMappingExecutionResult fetchAndMapAbasSternzeitFromArtikelField(
			@RequestBody AbasArtikelmappeSternzeitRequest sternzeitRequest) {
		requireArticleNumber(sternzeitRequest.artikelNummer());
		if (sternzeitRequest.fieldName() == null || !sternzeitRequest.fieldName().matches(FIELD_NAME_PATTERN)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldName fehlt oder ist kein Feldname");
		}
		return abasArtikelmappeNachladeService.fetchAndMapAbasSternzeitFromArtikelField(sternzeitRequest.artikelNummer(),
				sternzeitRequest.fieldName());
	}

	/**
	 * Body {"artikelNummer": "45600025"}: einen Artikel, den der Kern nicht kennt, über die Belegnummer bei ABAS suchen
	 * und in den Kern holen. 404, wenn ABAS keinen Artikel dazu kennt.
	 */
	@PostMapping("/artikel")
	public AbasBelegnummerLookupResult fetchArtikelIntoCoreByArtikelNummer(@RequestBody Map<String, Object> artikelRequest) {
		if (!(artikelRequest.get("artikelNummer") instanceof String artikelNummer)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artikelNummer fehlt");
		}
		requireArticleNumber(artikelNummer);
		return abasArtikelmappeNachladeService.fetchArtikelIntoCoreByArtikelNummer(artikelNummer);
	}

	private static void requireArticleNumber(String artikelNummer) {
		if (artikelNummer == null || !artikelNummer.matches(ARTICLE_NUMBER_PATTERN)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artikelNummer hat nicht die Form einer Artikelnummer");
		}
	}

}
