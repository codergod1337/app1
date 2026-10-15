package codergod1337.app1.solr.todojob.controller;

import codergod1337.app1.solr.todojob.model.TodojobAccessForClient;
import codergod1337.app1.solr.todojob.model.TodojobCompleteRequest;
import codergod1337.app1.solr.todojob.model.TodojobCreateRequest;
import codergod1337.app1.solr.todojob.model.TodojobDueDateRequest;
import codergod1337.app1.solr.todojob.model.TodojobExcerptRequest;
import codergod1337.app1.solr.todojob.model.TodojobIdRequest;
import codergod1337.app1.solr.todojob.model.TodojobLabelsRequest;
import codergod1337.app1.solr.todojob.model.TodojobListRequest;
import codergod1337.app1.solr.todojob.model.TodojobNumberedRequest;
import codergod1337.app1.solr.todojob.model.TodojobResolvedDocumentsPage;
import codergod1337.app1.solr.todojob.model.TodojobReturnRequest;
import codergod1337.app1.solr.todojob.model.TodojobRueckmeldungEntryRequest;
import codergod1337.app1.solr.todojob.model.TodojobSearchRequest;
import codergod1337.app1.solr.todojob.model.TodojobSuggestRequest;
import codergod1337.app1.solr.todojob.model.TodojobTextRequest;
import codergod1337.app1.solr.todojob.model.TodojobWaitingCounts;
import codergod1337.app1.solr.todojob.service.SolrDocumentTodojobService;
import codergod1337.app1.solr.todojob.service.SolrDocumentTodojobUtilsService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Jobs des Kerns TODOJOB (User 2026-10-16). Der Pfad liegt unter /api/rest/v1/**, also angemeldet, mehr Tür gibt es
 * nicht; die Parteien-Prüfung und jede Regel eines Vorgangs prüft der Service, hier nur die Form. Die id eines Jobs ist eine
 * laufende Nummer (Long), sie darf in die URL. Jeder Vorgang antwortet mit dem Job, wie der User ihn danach sieht.
 */
@RestController
@RequestMapping("/api/rest/v1/solr/todojob")
public class SolrDocumentTodojobController {

	private final SolrDocumentTodojobService solrDocumentTodojobService;
	private final SolrDocumentTodojobUtilsService solrDocumentTodojobUtilsService;

	public SolrDocumentTodojobController(SolrDocumentTodojobService solrDocumentTodojobService,
			SolrDocumentTodojobUtilsService solrDocumentTodojobUtilsService) {
		this.solrDocumentTodojobService = solrDocumentTodojobService;
		this.solrDocumentTodojobUtilsService = solrDocumentTodojobUtilsService;
	}

	// ===== Lesen =====

	/** Was der User im Kern TODOJOB darf und welche Rückmeldungsarten es gibt. Für die Masken, kein Schutz. */
	@GetMapping("/access")
	public TodojobAccessForClient access() {
		solrDocumentTodojobUtilsService.requireCurrentUser();
		return solrDocumentTodojobUtilsService.accessForClient();
	}

	/** Die Jobseite: der Job für eine Partei, beim ersten Öffnen durch einen Servant mit READ im Log. 404 sonst. */
	@GetMapping("/id/{id}")
	public Map<String, Object> getById(@PathVariable Long id) {
		Map<String, Object> job = solrDocumentTodojobService.getByIdAndMarkRead(String.valueOf(id));
		if (job == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Job " + id);
		}
		return job;
	}

	/** Einer der drei Stapel: {"kind": "FOR_ME" | "FROM_ME" | "DONE", "page": 0} */
	@PostMapping("/list")
	public TodojobResolvedDocumentsPage list(@RequestBody TodojobListRequest listRequest) {
		if (listRequest.kind() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind fehlt");
		}
		return solrDocumentTodojobService.resolveTodojobsForCurrentUser(listRequest.kind(), pageOf(listRequest.page()));
	}

	/** Bei wie vielen Jobs der Ball beim User liegt, für die Zahl im Benutzermenü */
	@GetMapping("/waiting")
	public TodojobWaitingCounts waiting() {
		return solrDocumentTodojobService.countTodojobsWaitingForCurrentUser();
	}

	/** Der Auszug für eine Mappe: {"displayContextKey": "ARTIKELMAPPE", "hookKey": "artikelNummer", "hookValue": "10001234", "page": 0} */
	@PostMapping("/excerpt")
	public TodojobResolvedDocumentsPage excerpt(@RequestBody TodojobExcerptRequest excerptRequest) {
		return solrDocumentTodojobService.resolveTodojobExcerptsByDisplayContext(excerptRequest.displayContextKey(),
				excerptRequest.hookKey(), excerptRequest.hookValue(), pageOf(excerptRequest.page()));
	}

	/** Die Suche über die eigenen Jobs: {"searchText": "datenblatt", "exactFilterValuesByFieldName": {"status": ["OFFEN"]}, "page": 0} */
	@PostMapping("/search")
	public TodojobResolvedDocumentsPage search(@RequestBody TodojobSearchRequest searchRequest) {
		return solrDocumentTodojobService.searchTodojobs(searchRequest.searchText(),
				searchRequest.exactFilterValuesByFieldName() == null ? Map.of() : searchRequest.exactFilterValuesByFieldName(),
				pageOf(searchRequest.page()));
	}

	/** Ähnliche Jobs beim Erteilen: {"verb": "beschaffe", "objekt": "Datenblatt"} */
	@PostMapping("/suggest")
	public List<Map<String, Object>> suggest(@RequestBody TodojobSuggestRequest suggestRequest) {
		boolean verbGiven = suggestRequest.verb() != null && !suggestRequest.verb().isBlank();
		boolean objektGiven = suggestRequest.objekt() != null && !suggestRequest.objekt().isBlank();
		if (!verbGiven && !objektGiven) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verb oder objekt muss da sein");
		}
		return solrDocumentTodojobService.suggestSimilarTodojobs(suggestRequest.verb(), suggestRequest.objekt());
	}

	/** Vervollständigung beim Tippen: {"fieldName": "verb", "prefix": "prü"} */
	@PostMapping("/complete")
	public List<String> complete(@RequestBody TodojobCompleteRequest completeRequest) {
		return solrDocumentTodojobService.completeFieldValues(completeRequest.fieldName(), completeRequest.prefix());
	}

	// ===== Erteilen =====

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Object> create(@RequestBody TodojobCreateRequest createRequest) {
		return solrDocumentTodojobService.createTodojob(createRequest);
	}

	// ===== Vorgänge, je einer je Ereignis =====

	@PostMapping("/claim")
	public Map<String, Object> claim(@RequestBody TodojobIdRequest request) {
		return solrDocumentTodojobService.claimTodojob(request.id());
	}

	@PostMapping("/claim/release")
	public Map<String, Object> releaseClaim(@RequestBody TodojobIdRequest request) {
		return solrDocumentTodojobService.releaseClaim(request.id());
	}

	/** {"id": "4711", "text": "Begründung"} */
	@PostMapping("/claim/take")
	public Map<String, Object> takeOverClaim(@RequestBody TodojobTextRequest request) {
		return solrDocumentTodojobService.takeOverClaim(request.id(), request.text());
	}

	/** {"id": "4711", "type": "DMS_DATEI", "value": "e3b0…"} oder {"id": "4711", "type": "COMMENT", "text": "…"} */
	@PostMapping("/rueckmeldung")
	public Map<String, Object> addRueckmeldungEntry(@RequestBody TodojobRueckmeldungEntryRequest request) {
		return solrDocumentTodojobService.addRueckmeldungEntry(request.id(), request.type(), request.text(), request.value());
	}

	/** {"id": "4711", "nr": 2} */
	@PostMapping("/rueckmeldung/retract")
	public Map<String, Object> retractRueckmeldungEntry(@RequestBody TodojobNumberedRequest request) {
		return solrDocumentTodojobService.retractRueckmeldungEntry(request.id(), nrOf(request.nr()));
	}

	/** {"id": "4711", "text": "Reicht Rev. B?"} */
	@PostMapping("/question")
	public Map<String, Object> askQuestion(@RequestBody TodojobTextRequest request) {
		return solrDocumentTodojobService.askQuestion(request.id(), request.text());
	}

	/** {"id": "4711", "nr": 1, "text": "Nein, Rev. C."} */
	@PostMapping("/answer")
	public Map<String, Object> answerQuestion(@RequestBody TodojobNumberedRequest request) {
		return solrDocumentTodojobService.answerQuestion(request.id(), nrOf(request.nr()), request.text());
	}

	@PostMapping("/respond")
	public Map<String, Object> respond(@RequestBody TodojobIdRequest request) {
		return solrDocumentTodojobService.respondTodojob(request.id());
	}

	@PostMapping("/confirm")
	public Map<String, Object> confirm(@RequestBody TodojobIdRequest request) {
		return solrDocumentTodojobService.confirmTodojob(request.id());
	}

	/** {"id": "4711", "text": "Das ist Rev. B", "aufgabe": null, "kontext": null} */
	@PostMapping("/return")
	public Map<String, Object> returnTodojob(@RequestBody TodojobReturnRequest request) {
		return solrDocumentTodojobService.returnTodojob(request.id(), request.text(), request.aufgabe(), request.kontext());
	}

	@PostMapping("/withdraw")
	public Map<String, Object> withdraw(@RequestBody TodojobIdRequest request) {
		return solrDocumentTodojobService.withdrawTodojob(request.id());
	}

	/** {"id": "4711", "text": "Chef will das bis Freitag"} */
	@PostMapping("/comment")
	public Map<String, Object> comment(@RequestBody TodojobTextRequest request) {
		return solrDocumentTodojobService.commentTodojob(request.id(), request.text());
	}

	/** {"id": "4711", "fristAm": "2026-10-23T00:00:00Z"}, null löscht die Frist */
	@PostMapping("/due-date")
	public Map<String, Object> changeDueDate(@RequestBody TodojobDueDateRequest request) {
		return solrDocumentTodojobService.changeDueDate(request.id(), request.fristAm());
	}

	/** {"id": "4711", "displayContextKeys": ["ARTIKELMAPPE"], "dauerhaftRelevant": true} */
	@PostMapping("/labels")
	public Map<String, Object> changeLabels(@RequestBody TodojobLabelsRequest request) {
		return solrDocumentTodojobService.changeLabels(request.id(), request.displayContextKeys(), request.dauerhaftRelevant());
	}

	// ===== Form =====

	private static int pageOf(Integer page) {
		return page == null ? 0 : page;
	}

	private static int nrOf(Integer nr) {
		if (nr == null || nr < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nr fehlt oder ist kleiner als 1");
		}
		return nr;
	}

}
