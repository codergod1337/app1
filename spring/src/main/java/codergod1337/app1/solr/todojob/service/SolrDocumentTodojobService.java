package codergod1337.app1.solr.todojob.service;

import codergod1337.app1.file.filesubclass.service.FileSubClassDestinationService;
import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.todojob.model.CounterTodojobId;
import codergod1337.app1.solr.todojob.model.TodojobAusgang;
import codergod1337.app1.solr.todojob.model.TodojobCreateRequest;
import codergod1337.app1.solr.todojob.model.TodojobFieldNames;
import codergod1337.app1.solr.todojob.model.TodojobListKind;
import codergod1337.app1.solr.todojob.model.TodojobLogAction;
import codergod1337.app1.solr.todojob.model.TodojobResolvedDocumentsPage;
import codergod1337.app1.solr.todojob.model.TodojobRueckmeldungEntryType;
import codergod1337.app1.solr.todojob.model.TodojobStatus;
import codergod1337.app1.solr.todojob.model.TodojobWaitingCounts;
import codergod1337.app1.solr.todojob.repository.CounterTodojobIdRepository;
import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleService;
import codergod1337.app1.system.user.service.UsersService;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lesen und Schreiben im Kern TODOJOB (User 2026-10-16): ein Job ist ein Befehl aus Verb und Objekt, vom Owner an
 * Servants (Personen, ARs oder ARCs), mit Rückmeldung in Schritten, Rückfragen und Rückantworten, Claim, Log und
 * Labels (Anzeigeorte). Jeder Vorgang eines Menschen ist ein Atomic Update: nur die genannten Felder, Listen nur per
 * ADD, dazu immer ein Log-Eintrag, lastChangedAt und changingUsers. Nichts wird je überschrieben, was ein anderer
 * geschrieben hat; die Rückmeldung bleibt vollständig, auch nach dem Zurückgeben.
 *
 * Drei Wege zu einem Job: Partei (ganzer Job, Parteien-fq), über eine Entity per Anzeigeort (fester Auszug), Admin
 * (anderes Thema, noch nicht gebaut). Keine Tür-Rolle, angemeldet reicht (User 2026-10-16). Rollen, fq, fl und die
 * JSON-Einträge liegen im SolrDocumentTodojobUtilsService.
 */
@Service
public class SolrDocumentTodojobService {

	/** Der Kern, zugleich sein Name in Solr */
	public static final String CORE_KEY = "TODOJOB";

	private static final Logger log = LoggerFactory.getLogger(SolrDocumentTodojobService.class);

	private static final String NEWEST_FIRST = TodojobFieldNames.ERSTELLT_AM + " desc, id asc";
	private static final String DONE_NEWEST_FIRST = TodojobFieldNames.ABGESCHLOSSEN_AM + " desc, id asc";

	/** Höchstens so lang dürfen Verb und Objekt sein: ein Label, kein Text */
	private static final int MAX_LABEL_LENGTH = 200;

	/** So viele ähnliche Jobs kommen als Vorschlag */
	private static final int SUGGESTION_ROWS = 10;

	/** So viele Werte bietet die Vervollständigung von Verb und Objekt */
	private static final int COMPLETION_VALUES = 10;

	private final SolrDocumentService solrDocumentService;
	private final SolrClientRegistry solrClientRegistry;
	private final SolrDocumentTodojobUtilsService utils;
	private final CounterTodojobIdRepository counterTodojobIdRepository;
	private final UsersService usersService;
	private final AccessRoleService accessRoleService;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final FileSubClassDestinationService fileSubClassDestinationService;

	/** Treffer je Seite beim Auflösen, app1.solr.resolve.page-size */
	private final int resolvePageSize;

	/** Treffer insgesamt beim Auflösen, app1.solr.resolve.max-results; eine Seite darüber ist leer */
	private final int resolveMaxResults;

	/** Treffer je Seite der Suche, app1.solr.search.hits */
	private final int searchHits;

	public SolrDocumentTodojobService(SolrDocumentService solrDocumentService, SolrClientRegistry solrClientRegistry,
			SolrDocumentTodojobUtilsService utils, CounterTodojobIdRepository counterTodojobIdRepository,
			UsersService usersService, AccessRoleService accessRoleService,
			AccessRoleCollectionService accessRoleCollectionService,
			FileSubClassDestinationService fileSubClassDestinationService,
			@Value("${app1.solr.resolve.page-size}") int resolvePageSize,
			@Value("${app1.solr.resolve.max-results}") int resolveMaxResults,
			@Value("${app1.solr.search.hits}") int searchHits) {
		this.solrDocumentService = solrDocumentService;
		this.solrClientRegistry = solrClientRegistry;
		this.utils = utils;
		this.counterTodojobIdRepository = counterTodojobIdRepository;
		this.usersService = usersService;
		this.accessRoleService = accessRoleService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.fileSubClassDestinationService = fileSubClassDestinationService;
		this.resolvePageSize = resolvePageSize;
		this.resolveMaxResults = resolveMaxResults;
		this.searchHits = searchHits;
	}

	// ===== Erteilen =====

	/** Die nächste laufende Nummer: eine neue Zeile im Zähler, ihre id */
	@Transactional
	public String nextTodojobId() {
		return String.valueOf(counterTodojobIdRepository.save(new CounterTodojobId()).getId());
	}

	/**
	 * Erteilt einen Job: prüft jede Eingabe, setzt die Felder des Backends (Owner aus dem Token, Status OFFEN, Zähler,
	 * Zeitpunkte, Log CREATED) und schreibt das Dokument voll. Zurück kommt der Job, wie der Owner ihn sieht.
	 */
	public Map<String, Object> createTodojob(TodojobCreateRequest request) {
		utils.requireCurrentUser();
		String verb = checkedLabel(request.verb(), "verb");
		String objekt = checkedLabel(request.objekt(), "objekt");
		if (request.rueckmeldungsArt() == null || !request.rueckmeldungsArt().isRueckmeldungsArt()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rueckmeldungsArt fehlt oder ist keine Rückmeldungsart");
		}
		List<String> kontext = checkedTexts(request.kontext(), "kontext");
		List<String> displayContextKeys = checkedDisplayContextKeys(request.displayContextKeys());
		List<String> ownerAccessRoleKeys = checkedAccessRoleKeys(request.ownerAccessRoleKeys(), "ownerAccessRoleKeys");
		List<String> ownerArcKeys = checkedArcKeys(request.ownerArcKeys(), "ownerArcKeys");
		List<String> servantGuids = checkedUsersGuids(request.servantGuids());
		List<String> servantAccessRoleKeys = checkedAccessRoleKeys(request.servantAccessRoleKeys(), "servantAccessRoleKeys");
		List<String> servantArcKeys = checkedArcKeys(request.servantArcKeys(), "servantArcKeys");
		if (servantGuids.isEmpty() && servantAccessRoleKeys.isEmpty() && servantArcKeys.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ein Job braucht mindestens einen Servant: User, AR oder ARC");
		}
		Map<String, List<String>> hookValuesByHookKey = checkedHookValues(request.hookValuesByHookKey());

		Instant now = Instant.now();
		Map<String, Object> document = new LinkedHashMap<>();
		document.put(TodojobFieldNames.ID, nextTodojobId());
		document.put(TodojobFieldNames.STATUS, TodojobStatus.OFFEN);
		document.put(TodojobFieldNames.VERB, verb);
		document.put(TodojobFieldNames.OBJEKT, objekt);
		if (request.aufgabe() != null && !request.aufgabe().isBlank()) {
			document.put(TodojobFieldNames.AUFGABE, request.aufgabe().trim());
		}
		if (!kontext.isEmpty()) {
			document.put(TodojobFieldNames.KONTEXT, kontext);
		}
		document.put(TodojobFieldNames.RUECKMELDUNGS_ART, request.rueckmeldungsArt().name());
		document.put(TodojobFieldNames.BESTAETIGUNG_NOETIG, request.bestaetigungNoetig());
		document.put(TodojobFieldNames.DAUERHAFT_RELEVANT, request.dauerhaftRelevant());
		if (!displayContextKeys.isEmpty()) {
			document.put(TodojobFieldNames.DISPLAY_CONTEXT_KEYS, displayContextKeys);
		}
		document.put(TodojobFieldNames.OWNER_GUID, utils.currentUsersGuid());
		putIfNotEmpty(document, TodojobFieldNames.OWNER_ACCESS_ROLE_KEYS, ownerAccessRoleKeys);
		putIfNotEmpty(document, TodojobFieldNames.OWNER_ARC_KEYS, ownerArcKeys);
		putIfNotEmpty(document, TodojobFieldNames.SERVANT_GUIDS, servantGuids);
		putIfNotEmpty(document, TodojobFieldNames.SERVANT_ACCESS_ROLE_KEYS, servantAccessRoleKeys);
		putIfNotEmpty(document, TodojobFieldNames.SERVANT_ARC_KEYS, servantArcKeys);
		document.put(TodojobFieldNames.ERSTELLT_AM, now);
		if (request.fristAm() != null) {
			document.put(TodojobFieldNames.FRIST_AM, request.fristAm());
		}
		document.put(TodojobFieldNames.OFFENE_RUECKFRAGEN, 0L);
		document.put(TodojobFieldNames.WIEDEREROEFFNET_ANZAHL, 0L);
		document.put(TodojobFieldNames.LOG, List.of(utils.toJsonEntry(logEntry(now, TodojobLogAction.CREATED))));
		document.put(TodojobFieldNames.LAST_CHANGED_AT, now);
		document.put(TodojobFieldNames.CHANGING_USERS, List.of(utils.currentUsersGuid()));
		hookValuesByHookKey.forEach(document::put);

		solrDocumentService.writeNewDocument(CORE_KEY, document);
		log.info("Job {} erteilt von {}: {} {}", document.get(TodojobFieldNames.ID), utils.currentUsersGuid(), verb, objekt);
		return getById(String.valueOf(document.get(TodojobFieldNames.ID)));
	}

	// ===== Lesen =====

	/**
	 * Ein Job über seine Nummer, nur für eine Partei, mit den Feldern, die der User lesen darf; null, wenn es ihn nicht
	 * gibt oder der User keine Partei ist. Echtzeit-Lesen (/get): sieht auch einen gerade geschriebenen Job.
	 */
	public Map<String, Object> getById(String id) {
		utils.requireCurrentUser();
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", utils.partyFilterQueryOfCurrentUser());
		utils.buildUserSolrResponseFieldList(params);
		return realtimeGet(id, params);
	}

	/**
	 * Die Jobseite: der Job wie bei getById, und beim ersten Öffnen durch einen Servant ein READ im Log, je User einmal.
	 * Der Owner liest ohne Spur, er kennt seinen Job.
	 */
	public Map<String, Object> getByIdAndMarkRead(String id) {
		Map<String, Object> job = getById(id);
		if (job == null || !utils.isServantSide(job) || utils.isOwnerSide(job)) {
			return job;
		}
		String me = utils.currentUsersGuid();
		boolean alreadyRead = utils.parseEntries(job.get(TodojobFieldNames.LOG)).stream()
				.anyMatch(entry -> TodojobLogAction.READ.name().equals(entry.get("action")) && me.equals(entry.get("user")));
		if (alreadyRead) {
			return job;
		}
		return update(id, new LinkedHashMap<>(), logEntry(Instant.now(), TodojobLogAction.READ));
	}

	/** Die drei Stapel des Schreibtischs, Seiten ab 0 mit der Größe aus der YAML */
	public TodojobResolvedDocumentsPage resolveTodojobsForCurrentUser(TodojobListKind kind, int page) {
		utils.requireCurrentUser();
		ModifiableSolrParams params = new ModifiableSolrParams();
		switch (kind) {
			case FOR_ME -> {
				params.add("fq", utils.servantFilterQueryOfCurrentUser());
				params.add("fq", TodojobStatus.OPEN_STATUS_FILTER_QUERY);
				params.set("sort", NEWEST_FIRST);
			}
			case FROM_ME -> {
				params.add("fq", utils.ownerFilterQueryOfCurrentUser());
				params.add("fq", TodojobStatus.OPEN_STATUS_FILTER_QUERY);
				params.set("sort", NEWEST_FIRST);
			}
			case DONE -> {
				params.add("fq", utils.partyFilterQueryOfCurrentUser());
				params.add("fq", TodojobFieldNames.STATUS + ":" + TodojobStatus.ABGESCHLOSSEN);
				params.set("sort", DONE_NEWEST_FIRST);
			}
		}
		utils.buildUserSolrResponseFieldList(params);
		return resolvePage(params, page);
	}

	/**
	 * Bei wie vielen Jobs der Ball beim User liegt, für die Zahl im Benutzermenü: als Servant die offenen Jobs ohne
	 * fremden Claim und ohne offene Rückfrage, als Owner die Jobs in FERTIG und die mit offener Rückfrage.
	 */
	public TodojobWaitingCounts countTodojobsWaitingForCurrentUser() {
		utils.requireCurrentUser();
		String me = ClientUtils.escapeQueryChars(utils.currentUsersGuid());
		ModifiableSolrParams servant = new ModifiableSolrParams();
		servant.add("fq", utils.servantFilterQueryOfCurrentUser());
		servant.add("fq", TodojobFieldNames.STATUS + ":(" + String.join(" OR ", TodojobStatus.SERVANT_WORKING_STATUSES) + ")");
		servant.add("fq", "(" + TodojobFieldNames.CLAIM_GUID + ":" + me + " OR (*:* -" + TodojobFieldNames.CLAIM_GUID + ":*))");
		servant.add("fq", "(" + TodojobFieldNames.OFFENE_RUECKFRAGEN + ":0 OR (*:* -" + TodojobFieldNames.OFFENE_RUECKFRAGEN + ":*))");
		ModifiableSolrParams owner = new ModifiableSolrParams();
		owner.add("fq", utils.ownerFilterQueryOfCurrentUser());
		owner.add("fq", "(" + TodojobFieldNames.STATUS + ":" + TodojobStatus.FERTIG + " OR "
				+ TodojobFieldNames.OFFENE_RUECKFRAGEN + ":[1 TO *])");
		return new TodojobWaitingCounts(count(servant), count(owner));
	}

	/**
	 * Der zweite Weg: die Jobs eines Anzeigeorts zu einem Hook-Wert, z. B. ARTIKELMAPPE und artikelNummer 10001234, als
	 * fester Auszug ohne Parteien-Prüfung. Offene Jobs immer, abgeschlossene nur mit dauerhaftRelevant.
	 */
	public TodojobResolvedDocumentsPage resolveTodojobExcerptsByDisplayContext(String displayContextKey, String hookKey,
			String hookValue, int page) {
		utils.requireCurrentUser();
		if (displayContextKey == null || !fileSubClassDestinationService.isFileSubClassDestinationKeyKnown(displayContextKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unbekannter Anzeigeort");
		}
		List<String> hookKeys = utils.hookKeysOfCore();
		if (hookKey == null || !hookKeys.contains(hookKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hookKey ist kein Hook des Kerns");
		}
		if (hookValue == null || hookValue.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hookValue fehlt");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", TodojobFieldNames.DISPLAY_CONTEXT_KEYS + ":" + ClientUtils.escapeQueryChars(displayContextKey));
		params.add("fq", hookKey + ":" + ClientUtils.escapeQueryChars(hookValue.trim()));
		params.add("fq", "(" + TodojobStatus.OPEN_STATUS_FILTER_QUERY + " OR " + TodojobFieldNames.DAUERHAFT_RELEVANT + ":true)");
		params.set("sort", NEWEST_FIRST);
		List<String> fieldList = new ArrayList<>(TodojobFieldNames.EXCERPT_FIELD_NAMES);
		fieldList.addAll(hookKeys);
		params.set("fl", String.join(",", fieldList));
		return resolvePage(params, page);
	}

	/**
	 * Die Suche über die eigenen Jobs (User 2026-10-16): edismax über die Felder mit Suchgewicht, über den Ngram- oder
	 * Suggested-Zwilling, wo es einen gibt, dazu die Chips des Clients. Dasselbe Parteien-fq wie beim Öffnen, keine
	 * Suchrolle. Ohne Text nur die Chips, neueste zuerst.
	 */
	public TodojobResolvedDocumentsPage searchTodojobs(String searchText, Map<String, List<String>> exactFilterValuesByFieldName,
			int page) {
		utils.requireCurrentUser();
		String text = searchText == null ? "" : searchText.trim();
		if (text.isEmpty() && exactFilterValuesByFieldName.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weder Suchtext noch Filter");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", utils.partyFilterQueryOfCurrentUser());
		if (text.isEmpty()) {
			params.set("q", "*:*");
			params.set("sort", NEWEST_FIRST);
		} else {
			List<String> qf = new ArrayList<>();
			for (SolrField solrField : utils.searchedFieldsOfCore()) {
				// der Ngram-Zwilling kennt jedes Wortstück, der Suggested-Zwilling den Wortanfang, sonst das Feld selbst
				String queriedName = solrField.isNgram() && solrField.getType().isNgramTwinAllowed()
						? solrField.getName() + SolrFieldName.NGRAM_SUFFIX
						: solrField.isSuggest() ? solrField.getName() + SolrFieldName.SUGGEST_SUFFIX : solrField.getName();
				qf.add(queriedName + "^" + solrField.getSearchWeight());
			}
			if (qf.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Feld im Kern " + CORE_KEY + " hat ein Suchgewicht");
			}
			params.set("defType", "edismax");
			params.set("q", text);
			params.set("qf", String.join(" ", qf));
			params.set("q.op", "OR");
			params.set("mm", "1");
			params.set("sow", "true");
			params.set("sort", "score desc, " + NEWEST_FIRST);
		}
		List<String> readableFieldNames = utils.buildUserSolrResponseFieldList(params);
		List<String> debuffedFieldNames = utils.buildUserDebuffsForSolrRequest(params);
		utils.buildClientConditionsForSolrRequest(params, exactFilterValuesByFieldName, readableFieldNames, debuffedFieldNames);
		long start = (long) page * searchHits;
		if (page < 0 || start > Integer.MAX_VALUE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist ungültig");
		}
		params.set("start", (int) start);
		params.set("rows", searchHits);
		QueryResponse response = query(params);
		return new TodojobResolvedDocumentsPage(response.getResults().getNumFound(), page, searchHits,
				documentsOf(response));
	}

	/**
	 * Ähnliche Jobs für den Vorschlag beim Erteilen: dasselbe Verb, ein ähnliches Objekt (Ngram-Zwilling), nur eigene
	 * Jobs mit Ausgang GUT_GEMACHT, die besten zuerst. Zurück kommen Verb, Objekt, Aufgabe, Kontext, Rückmeldung,
	 * Rückfragen und Rückantworten, damit der Owner Eingrenzungen und Antworten übernimmt, bevor jemand wieder fragt.
	 */
	public List<Map<String, Object>> suggestSimilarTodojobs(String verb, String objekt) {
		utils.requireCurrentUser();
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", utils.partyFilterQueryOfCurrentUser());
		params.add("fq", TodojobFieldNames.AUSGANG + ":" + TodojobAusgang.GUT_GEMACHT);
		if (verb != null && !verb.isBlank()) {
			params.add("fq", TodojobFieldNames.VERB + ":" + ClientUtils.escapeQueryChars(verb.trim()));
		}
		if (objekt != null && !objekt.isBlank()) {
			List<String> words = new ArrayList<>();
			for (String word : objekt.trim().split("\\s+")) {
				if (word.length() >= 3) {
					words.add(ClientUtils.escapeQueryChars(word));
				}
			}
			params.set("q", words.isEmpty() ? "*:*"
					: TodojobFieldNames.OBJEKT + SolrFieldName.NGRAM_SUFFIX + ":(" + String.join(" OR ", words) + ")");
			params.set("sort", "score desc, " + DONE_NEWEST_FIRST);
		} else {
			params.set("q", "*:*");
			params.set("sort", DONE_NEWEST_FIRST);
		}
		params.set("fl", String.join(",", List.of(TodojobFieldNames.ID, TodojobFieldNames.VERB, TodojobFieldNames.OBJEKT,
				TodojobFieldNames.AUFGABE, TodojobFieldNames.KONTEXT, TodojobFieldNames.RUECKMELDUNGS_ART,
				TodojobFieldNames.RUECKMELDUNG, TodojobFieldNames.RUECKFRAGEN, TodojobFieldNames.RUECKANTWORTEN,
				TodojobFieldNames.ABGESCHLOSSEN_AM)));
		params.set("rows", SUGGESTION_ROWS);
		return documentsOf(query(params));
	}

	/**
	 * Die Vervollständigung von Verb oder Objekt beim Tippen: die Werte, die schon in eigenen Jobs stehen und mit dem
	 * Getippten beginnen (Suggested-Zwilling), als Facette ohne Doppelte, die häufigsten zuerst.
	 */
	public List<String> completeFieldValues(String fieldName, String prefix) {
		utils.requireCurrentUser();
		if (!TodojobFieldNames.VERB.equals(fieldName) && !TodojobFieldNames.OBJEKT.equals(fieldName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vervollständigt werden nur verb und objekt");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", utils.partyFilterQueryOfCurrentUser());
		String text = prefix == null ? "" : prefix.trim();
		params.set("q", text.isEmpty() ? "*:*"
				: fieldName + SolrFieldName.SUGGEST_SUFFIX + ":" + ClientUtils.escapeQueryChars(text.toLowerCase(Locale.ROOT)));
		params.set("rows", 0);
		params.set("facet", true);
		params.set("facet.field", fieldName);
		params.set("facet.limit", COMPLETION_VALUES);
		params.set("facet.mincount", 1);
		QueryResponse response = query(params);
		List<String> values = new ArrayList<>();
		FacetField facetField = response.getFacetField(fieldName);
		if (facetField != null && facetField.getValues() != null) {
			for (FacetField.Count count : facetField.getValues()) {
				values.add(count.getName());
			}
		}
		return values;
	}

	// ===== Vorgänge, je einer je Ereignis =====

	/** Claim: ein Servant trägt sich für einen Gruppenjob ein, der noch OFFEN ist. 409, wenn schon jemand drinsteht. */
	public Map<String, Object> claimTodojob(String id) {
		Map<String, Object> job = loadJobForAction(id);
		requireServantSide(job);
		if (!SolrDocumentTodojobUtilsService.isGroupJob(job)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ein Job an eine Person braucht keinen Claim");
		}
		String holder = SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.CLAIM_GUID));
		if (holder != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "schon beansprucht von " + holder);
		}
		requireStatus(job, TodojobStatus.OFFEN);
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.CLAIM_GUID, Map.of("set", utils.currentUsersGuid()));
		modifiers.put(TodojobFieldNames.STATUS, Map.of("set", TodojobStatus.CLAIMED));
		return update(id, modifiers, logEntry(Instant.now(), TodojobLogAction.CLAIMED));
	}

	/** Claim lösen: der Inhaber oder die Owner-Seite. Ein Job in CLAIMED geht zurück auf OFFEN, sonst bleibt der Status. */
	public Map<String, Object> releaseClaim(String id) {
		Map<String, Object> job = loadJobForAction(id);
		String holder = SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.CLAIM_GUID));
		if (holder == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Claim an diesem Job");
		}
		if (!holder.equals(utils.currentUsersGuid()) && !utils.isOwnerSide(job)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "nur der Inhaber des Claims oder der Owner löst ihn");
		}
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.CLAIM_GUID, setNull());
		if (TodojobStatus.CLAIMED.equals(statusOf(job))) {
			modifiers.put(TodojobFieldNames.STATUS, Map.of("set", TodojobStatus.OFFEN));
		}
		return update(id, modifiers, logEntry(Instant.now(), TodojobLogAction.CLAIM_RELEASED));
	}

	/** Übernahme: ein anderes Gruppenmitglied zieht den Claim an sich, mit Begründung im Log. */
	public Map<String, Object> takeOverClaim(String id, String text) {
		Map<String, Object> job = loadJobForAction(id);
		requireServantSide(job);
		String holder = SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.CLAIM_GUID));
		if (holder == null || holder.equals(utils.currentUsersGuid())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein fremder Claim an diesem Job");
		}
		requireNotFinished(job);
		String reason = checkedText(text, "text");
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.CLAIM_GUID, Map.of("set", utils.currentUsersGuid()));
		Map<String, Object> entry = logEntry(Instant.now(), TodojobLogAction.CLAIM_TAKEN);
		entry.put("text", reason);
		entry.put("from", holder);
		return update(id, modifiers, entry);
	}

	/**
	 * Ein Beitrag zur Rückmeldung: ein Eintrag mit Art, Text oder Wert, nur angehängt. Setzt IN_BEARBEITUNG; bei einem
	 * Gruppenjob ohne Claim trägt sich der Schreiber damit ein. Nur ein Servant, bei Claim nur dessen Inhaber.
	 */
	public Map<String, Object> addRueckmeldungEntry(String id, TodojobRueckmeldungEntryType type, String text, String value) {
		Map<String, Object> job = loadJobForAction(id);
		requireWorkingServant(job);
		if (type == null || type == TodojobRueckmeldungEntryType.RETRACTED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type fehlt oder ist RETRACTED, das geht über retract");
		}
		if (type == TodojobRueckmeldungEntryType.LESEN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LESEN verlangt keinen Eintrag, nur das Fertigmelden");
		}
		String checkedText = text == null ? null : text.trim();
		String checkedValue = checkedEntryValue(type, value);
		if ((type == TodojobRueckmeldungEntryType.COMMENT || type == TodojobRueckmeldungEntryType.TEXT)
				&& (checkedText == null || checkedText.isEmpty())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type + " braucht einen Text");
		}
		Instant now = Instant.now();
		List<Map<String, Object>> entries = utils.parseEntries(job.get(TodojobFieldNames.RUECKMELDUNG));
		Map<String, Object> entry = utils.newEntry(now);
		entry.put("nr", entries.size() + 1);
		entry.put("type", type.name());
		if (checkedText != null && !checkedText.isEmpty()) {
			entry.put("text", checkedText);
		}
		if (checkedValue != null) {
			entry.put("value", checkedValue);
		}
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.RUECKMELDUNG, Map.of("add-distinct", List.of(utils.toJsonEntry(entry))));
		modifiers.put(TodojobFieldNames.STATUS, Map.of("set", TodojobStatus.IN_BEARBEITUNG));
		List<Map<String, Object>> logEntries = new ArrayList<>();
		if (SolrDocumentTodojobUtilsService.isGroupJob(job)
				&& SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.CLAIM_GUID)) == null) {
			// der erste Beitrag an einem Gruppenjob ist zugleich der Claim
			modifiers.put(TodojobFieldNames.CLAIM_GUID, Map.of("set", utils.currentUsersGuid()));
			logEntries.add(logEntry(now, TodojobLogAction.CLAIMED));
		}
		return update(id, modifiers, logEntries);
	}

	/** Einen Eintrag der Rückmeldung zurücknehmen: ein neuer Eintrag RETRACTED mit der nr, nichts wird gelöscht. */
	public Map<String, Object> retractRueckmeldungEntry(String id, int nr) {
		Map<String, Object> job = loadJobForAction(id);
		requireWorkingServant(job);
		List<Map<String, Object>> entries = utils.parseEntries(job.get(TodojobFieldNames.RUECKMELDUNG));
		boolean exists = entries.stream().anyMatch(entry -> numberOf(entry.get("nr")) == nr
				&& !TodojobRueckmeldungEntryType.RETRACTED.name().equals(entry.get("type")));
		if (!exists) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Eintrag " + nr);
		}
		if (retractedNumbers(entries).contains(nr)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eintrag " + nr + " ist schon zurückgenommen");
		}
		Map<String, Object> entry = utils.newEntry(Instant.now());
		entry.put("nr", entries.size() + 1);
		entry.put("type", TodojobRueckmeldungEntryType.RETRACTED.name());
		entry.put("retracts", nr);
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.RUECKMELDUNG, Map.of("add-distinct", List.of(utils.toJsonEntry(entry))));
		return update(id, modifiers, List.of());
	}

	/** Eine Rückfrage des Servants an den Owner: Eintrag in rueckfragen, Zähler hoch, der Status bleibt, höchstens IN_BEARBEITUNG. */
	public Map<String, Object> askQuestion(String id, String text) {
		Map<String, Object> job = loadJobForAction(id);
		requireServantSide(job);
		requireNotFinished(job);
		String question = checkedText(text, "text");
		Instant now = Instant.now();
		int nr = utils.parseEntries(job.get(TodojobFieldNames.RUECKFRAGEN)).size() + 1;
		Map<String, Object> entry = utils.newEntry(now);
		entry.put("nr", nr);
		entry.put("text", question);
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.RUECKFRAGEN, Map.of("add-distinct", List.of(utils.toJsonEntry(entry))));
		modifiers.put(TodojobFieldNames.OFFENE_RUECKFRAGEN, Map.of("inc", 1));
		String status = statusOf(job);
		if (TodojobStatus.OFFEN.equals(status) || TodojobStatus.CLAIMED.equals(status)) {
			modifiers.put(TodojobFieldNames.STATUS, Map.of("set", TodojobStatus.IN_BEARBEITUNG));
		}
		Map<String, Object> logEntry = logEntry(now, TodojobLogAction.QUESTION);
		logEntry.put("nr", nr);
		return update(id, modifiers, logEntry);
	}

	/** Die Rückantwort des Owners auf die Rückfrage nr: Eintrag in rueckantworten, Zähler runter. */
	public Map<String, Object> answerQuestion(String id, int nr, String text) {
		Map<String, Object> job = loadJobForAction(id);
		requireOwnerSide(job);
		boolean questionExists = utils.parseEntries(job.get(TodojobFieldNames.RUECKFRAGEN)).stream()
				.anyMatch(entry -> numberOf(entry.get("nr")) == nr);
		if (!questionExists) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Rückfrage " + nr);
		}
		boolean answered = utils.parseEntries(job.get(TodojobFieldNames.RUECKANTWORTEN)).stream()
				.anyMatch(entry -> numberOf(entry.get("nr")) == nr);
		if (answered) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rückfrage " + nr + " ist schon beantwortet");
		}
		String answer = checkedText(text, "text");
		Instant now = Instant.now();
		Map<String, Object> entry = utils.newEntry(now);
		entry.put("nr", nr);
		entry.put("text", answer);
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.RUECKANTWORTEN, Map.of("add-distinct", List.of(utils.toJsonEntry(entry))));
		long open = SolrDocumentTodojobUtilsService.longValueOf(job.get(TodojobFieldNames.OFFENE_RUECKFRAGEN));
		modifiers.put(TodojobFieldNames.OFFENE_RUECKFRAGEN, open > 0 ? Map.of("inc", -1) : Map.of("set", 0L));
		Map<String, Object> logEntry = logEntry(now, TodojobLogAction.ANSWER);
		logEntry.put("nr", nr);
		return update(id, modifiers, logEntry);
	}

	/**
	 * Fertig melden: braucht einen nicht zurückgenommenen Eintrag der Rückmeldungsart des Jobs (außer LESEN) und keine
	 * offene Rückfrage. Mit bestaetigungNoetig wartet der Job in FERTIG, sonst ist er sofort ABGESCHLOSSEN mit
	 * GUT_GEMACHT.
	 */
	public Map<String, Object> respondTodojob(String id) {
		Map<String, Object> job = loadJobForAction(id);
		requireWorkingServant(job);
		if (SolrDocumentTodojobUtilsService.longValueOf(job.get(TodojobFieldNames.OFFENE_RUECKFRAGEN)) > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "erst die offene Rückfrage beantworten lassen");
		}
		String requiredType = SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.RUECKMELDUNGS_ART));
		if (!TodojobRueckmeldungEntryType.LESEN.name().equals(requiredType)) {
			List<Map<String, Object>> entries = utils.parseEntries(job.get(TodojobFieldNames.RUECKMELDUNG));
			Set<Integer> retracted = retractedNumbers(entries);
			boolean fulfilled = entries.stream().anyMatch(entry -> requiredType != null
					&& requiredType.equals(entry.get("type")) && !retracted.contains(numberOf(entry.get("nr"))));
			if (!fulfilled) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "die Rückmeldung braucht einen Eintrag der Art " + requiredType);
			}
		}
		Instant now = Instant.now();
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		boolean confirmationNeeded = Boolean.TRUE.equals(job.get(TodojobFieldNames.BESTAETIGUNG_NOETIG));
		if (confirmationNeeded) {
			modifiers.put(TodojobFieldNames.STATUS, Map.of("set", TodojobStatus.FERTIG));
		} else {
			putFinished(modifiers, TodojobAusgang.GUT_GEMACHT, now);
		}
		return update(id, modifiers, logEntry(now, TodojobLogAction.RESPONDED));
	}

	/** Der Owner nimmt die Rückmeldung an: ABGESCHLOSSEN mit GUT_GEMACHT. */
	public Map<String, Object> confirmTodojob(String id) {
		Map<String, Object> job = loadJobForAction(id);
		requireOwnerSide(job);
		requireStatus(job, TodojobStatus.FERTIG);
		Instant now = Instant.now();
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		putFinished(modifiers, TodojobAusgang.GUT_GEMACHT, now);
		return update(id, modifiers, logEntry(now, TodojobLogAction.CONFIRMED));
	}

	/**
	 * Der Owner nimmt die Rückmeldung nicht an: zurück nach IN_BEARBEITUNG, Claim bleibt, Zähler hoch. Im Log die
	 * Begründung und bis zu welcher nr die Rückmeldung abgelehnt war; ändert der Owner dabei Aufgabe oder Kontext, hängt
	 * der alte Stand als snapshot dran.
	 */
	public Map<String, Object> returnTodojob(String id, String text, String aufgabe, List<String> kontext) {
		Map<String, Object> job = loadJobForAction(id);
		requireOwnerSide(job);
		requireStatus(job, TodojobStatus.FERTIG);
		String reason = checkedText(text, "text");
		Instant now = Instant.now();
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.STATUS, Map.of("set", TodojobStatus.IN_BEARBEITUNG));
		modifiers.put(TodojobFieldNames.WIEDEREROEFFNET_ANZAHL, Map.of("inc", 1));
		Map<String, Object> entry = logEntry(now, TodojobLogAction.RETURNED);
		entry.put("text", reason);
		int lastNr = 0;
		for (Map<String, Object> rueckmeldungEntry : utils.parseEntries(job.get(TodojobFieldNames.RUECKMELDUNG))) {
			lastNr = Math.max(lastNr, numberOf(rueckmeldungEntry.get("nr")));
		}
		entry.put("rueckmeldungBis", lastNr);
		Map<String, Object> snapshot = new LinkedHashMap<>();
		if (aufgabe != null) {
			String newAufgabe = aufgabe.trim();
			String oldAufgabe = SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.AUFGABE));
			if (!newAufgabe.equals(oldAufgabe == null ? "" : oldAufgabe)) {
				snapshot.put(TodojobFieldNames.AUFGABE, oldAufgabe == null ? "" : oldAufgabe);
				modifiers.put(TodojobFieldNames.AUFGABE, newAufgabe.isEmpty() ? setNull() : Map.of("set", newAufgabe));
			}
		}
		if (kontext != null) {
			List<String> newKontext = checkedTexts(kontext, "kontext");
			List<String> oldKontext = SolrDocumentTodojobUtilsService.valuesOf(job.get(TodojobFieldNames.KONTEXT));
			if (!newKontext.equals(oldKontext)) {
				snapshot.put(TodojobFieldNames.KONTEXT, oldKontext);
				modifiers.put(TodojobFieldNames.KONTEXT, newKontext.isEmpty() ? setNull() : Map.of("set", newKontext));
			}
		}
		if (!snapshot.isEmpty()) {
			entry.put("snapshot", snapshot);
		}
		return update(id, modifiers, entry);
	}

	/** Der Owner zieht den Job zurück: ABGESCHLOSSEN mit ZURUECKGEZOGEN, aus jedem Status außer ABGESCHLOSSEN. */
	public Map<String, Object> withdrawTodojob(String id) {
		Map<String, Object> job = loadJobForAction(id);
		requireOwnerSide(job);
		requireNotFinished(job);
		Instant now = Instant.now();
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		putFinished(modifiers, TodojobAusgang.ZURUECKGEZOGEN, now);
		return update(id, modifiers, logEntry(now, TodojobLogAction.WITHDRAWN));
	}

	/** Ein Kommentar am Job, von Owner oder Servant, nur im Log. */
	public Map<String, Object> commentTodojob(String id, String text) {
		Map<String, Object> job = loadJobForAction(id);
		if (!utils.isOwnerSide(job) && !utils.isServantSide(job)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Partei dieses Jobs");
		}
		Map<String, Object> entry = logEntry(Instant.now(), TodojobLogAction.COMMENT);
		entry.put("text", checkedText(text, "text"));
		return update(id, new LinkedHashMap<>(), entry);
	}

	/** Die Frist ändern oder löschen (null), nur der Owner, nicht nach dem Abschluss. */
	public Map<String, Object> changeDueDate(String id, Instant fristAm) {
		Map<String, Object> job = loadJobForAction(id);
		requireOwnerSide(job);
		requireNotFinished(job);
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.FRIST_AM, fristAm == null ? setNull() : Map.of("set", fristAm));
		Map<String, Object> entry = logEntry(Instant.now(), TodojobLogAction.DUE_DATE_CHANGED);
		entry.put("text", fristAm == null ? "" : fristAm.toString());
		return update(id, modifiers, entry);
	}

	/** Labels (Anzeigeorte) und dauerhaftRelevant ändern, nur der Owner, bis zum Abschluss. */
	public Map<String, Object> changeLabels(String id, List<String> displayContextKeys, boolean dauerhaftRelevant) {
		Map<String, Object> job = loadJobForAction(id);
		requireOwnerSide(job);
		requireNotFinished(job);
		List<String> checkedKeys = checkedDisplayContextKeys(displayContextKeys);
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		modifiers.put(TodojobFieldNames.DISPLAY_CONTEXT_KEYS, checkedKeys.isEmpty() ? setNull() : Map.of("set", checkedKeys));
		modifiers.put(TodojobFieldNames.DAUERHAFT_RELEVANT, Map.of("set", dauerhaftRelevant));
		Map<String, Object> entry = logEntry(Instant.now(), TodojobLogAction.LABELS_CHANGED);
		entry.put("text", String.join(", ", checkedKeys) + (dauerhaftRelevant ? " dauerhaft" : ""));
		return update(id, modifiers, entry);
	}

	// ===== die Mechanik eines Vorgangs =====

	/** Der Job für einen Vorgang: alle Felder, nur für eine Partei; 404 sonst, ununterscheidbar von „gibt es nicht“ */
	private Map<String, Object> loadJobForAction(String id) {
		utils.requireCurrentUser();
		if (id == null || id.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", utils.partyFilterQueryOfCurrentUser());
		Map<String, Object> job = realtimeGet(id, params);
		if (job == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Job " + id);
		}
		return job;
	}

	private Map<String, Object> update(String id, Map<String, Map<String, Object>> modifiers, Map<String, Object> logEntry) {
		return update(id, modifiers, List.of(logEntry));
	}

	/**
	 * Ein Vorgang: die genannten Felder, dazu die Log-Einträge per ADD, lastChangedAt und changingUsers. Zurück kommt
	 * der Job, wie der User ihn sieht, per Echtzeit-Lesen schon mit der Änderung.
	 */
	private Map<String, Object> update(String id, Map<String, Map<String, Object>> modifiers,
			List<Map<String, Object>> logEntries) {
		Map<String, Map<String, Object>> all = new LinkedHashMap<>(modifiers);
		if (!logEntries.isEmpty()) {
			List<String> jsonEntries = new ArrayList<>();
			for (Map<String, Object> entry : logEntries) {
				jsonEntries.add(utils.toJsonEntry(entry));
			}
			all.put(TodojobFieldNames.LOG, Map.of("add-distinct", jsonEntries));
		}
		all.put(TodojobFieldNames.LAST_CHANGED_AT, Map.of("set", Instant.now()));
		all.put(TodojobFieldNames.CHANGING_USERS, Map.of("add-distinct", List.of(utils.currentUsersGuid())));
		solrDocumentService.atomicUpdateDocument(CORE_KEY, id, all);
		log.info("Job {}: {} durch {}", id, logEntries.isEmpty() ? "Rückmeldung" : logEntries.get(0).get("action"),
				utils.currentUsersGuid());
		return getById(id);
	}

	private Map<String, Object> logEntry(Instant at, TodojobLogAction action) {
		Map<String, Object> entry = utils.newEntry(at);
		entry.put("action", action.name());
		return entry;
	}

	private static void putFinished(Map<String, Map<String, Object>> modifiers, String ausgang, Instant now) {
		modifiers.put(TodojobFieldNames.STATUS, Map.of("set", TodojobStatus.ABGESCHLOSSEN));
		modifiers.put(TodojobFieldNames.AUSGANG, Map.of("set", ausgang));
		modifiers.put(TodojobFieldNames.ABGESCHLOSSEN_AM, Map.of("set", now));
	}

	/** {"set": null} nimmt das Feld raus; Map.of nimmt kein null */
	private static Map<String, Object> setNull() {
		Map<String, Object> modifier = new LinkedHashMap<>();
		modifier.put("set", null);
		return modifier;
	}

	// ===== Prüfungen an einem Job =====

	private void requireOwnerSide(Map<String, Object> job) {
		if (!utils.isOwnerSide(job)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "nur der Owner");
		}
	}

	private void requireServantSide(Map<String, Object> job) {
		if (!utils.isServantSide(job)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "nur ein Servant");
		}
	}

	/** Servant, Job noch in Arbeit, und bei Claim dessen Inhaber */
	private void requireWorkingServant(Map<String, Object> job) {
		requireServantSide(job);
		if (!TodojobStatus.SERVANT_WORKING_STATUSES.contains(statusOf(job))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "der Job ist " + statusOf(job));
		}
		String holder = SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.CLAIM_GUID));
		if (holder != null && !holder.equals(utils.currentUsersGuid())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "beansprucht von " + holder);
		}
	}

	private static void requireStatus(Map<String, Object> job, String status) {
		if (!status.equals(statusOf(job))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "der Job ist " + statusOf(job) + ", nicht " + status);
		}
	}

	private static void requireNotFinished(Map<String, Object> job) {
		if (TodojobStatus.ABGESCHLOSSEN.equals(statusOf(job))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "der Job ist abgeschlossen");
		}
	}

	private static String statusOf(Map<String, Object> job) {
		return SolrDocumentTodojobUtilsService.firstValueOf(job.get(TodojobFieldNames.STATUS));
	}

	private static Set<Integer> retractedNumbers(List<Map<String, Object>> entries) {
		Set<Integer> retracted = new HashSet<>();
		for (Map<String, Object> entry : entries) {
			if (TodojobRueckmeldungEntryType.RETRACTED.name().equals(entry.get("type"))) {
				retracted.add(numberOf(entry.get("retracts")));
			}
		}
		return retracted;
	}

	private static int numberOf(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return value == null ? -1 : Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	// ===== Prüfungen der Eingaben =====

	private static String checkedLabel(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " fehlt");
		}
		String trimmed = value.trim().replaceAll("\\s+", " ");
		if (trimmed.length() > MAX_LABEL_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " ist länger als " + MAX_LABEL_LENGTH + " Zeichen");
		}
		return trimmed;
	}

	private static String checkedText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " fehlt");
		}
		return value.trim();
	}

	/** Eine Liste von Texten ohne leere, in Reihenfolge; null heißt leer */
	private static List<String> checkedTexts(List<String> values, String fieldName) {
		List<String> checked = new ArrayList<>();
		if (values == null) {
			return checked;
		}
		for (String value : values) {
			if (value == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " enthält einen leeren Eintrag");
			}
			String trimmed = value.trim();
			if (!trimmed.isEmpty()) {
				checked.add(trimmed);
			}
		}
		return checked;
	}

	private List<String> checkedDisplayContextKeys(List<String> keys) {
		List<String> checked = new ArrayList<>(new LinkedHashSet<>(checkedTexts(keys, "displayContextKeys")));
		for (String key : checked) {
			if (!fileSubClassDestinationService.isFileSubClassDestinationKeyKnown(key)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " ist kein Anzeigeort");
			}
		}
		return checked;
	}

	private List<String> checkedAccessRoleKeys(List<String> keys, String fieldName) {
		List<String> checked = new ArrayList<>(new LinkedHashSet<>(checkedTexts(keys, fieldName)));
		if (checked.isEmpty()) {
			return checked;
		}
		Set<String> known = new HashSet<>();
		for (AccessRole accessRole : accessRoleService.getAllAccessRoles()) {
			known.add(accessRole.getKey());
		}
		for (String key : checked) {
			if (!known.contains(key)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + ": " + key + " ist keine bekannte AR");
			}
		}
		return checked;
	}

	private List<String> checkedArcKeys(List<String> keys, String fieldName) {
		List<String> checked = new ArrayList<>(new LinkedHashSet<>(checkedTexts(keys, fieldName)));
		for (String key : checked) {
			if (accessRoleCollectionService.findAccessRoleCollectionByKey(key).isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + ": " + key + " ist keine bekannte ARC");
			}
		}
		return checked;
	}

	private List<String> checkedUsersGuids(List<String> guids) {
		List<String> checked = new ArrayList<>();
		for (String guid : new LinkedHashSet<>(checkedTexts(guids, "servantGuids"))) {
			if (!HelperInputs.isValidGuid(guid)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "servantGuids: " + guid + " ist keine guid");
			}
			try {
				usersService.getUsersByGuid(UUID.fromString(guid));
			} catch (ResponseStatusException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "servantGuids: kein User mit guid " + guid);
			}
			checked.add(guid.toLowerCase(Locale.ROOT));
		}
		return checked;
	}

	/** Hooks beim Erteilen: nur Hook-Zeilen des Kerns, Werte ohne leere; null heißt keine */
	private Map<String, List<String>> checkedHookValues(Map<String, List<String>> hookValuesByHookKey) {
		Map<String, List<String>> checked = new LinkedHashMap<>();
		if (hookValuesByHookKey == null) {
			return checked;
		}
		List<String> hookKeys = utils.hookKeysOfCore();
		for (Map.Entry<String, List<String>> hook : hookValuesByHookKey.entrySet()) {
			if (!hookKeys.contains(hook.getKey())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, hook.getKey() + " ist kein Hook des Kerns " + CORE_KEY);
			}
			List<String> values = new ArrayList<>(new LinkedHashSet<>(checkedTexts(hook.getValue(), hook.getKey())));
			if (!values.isEmpty()) {
				checked.put(hook.getKey(), values);
			}
		}
		return checked;
	}

	/** Der Wert eines Eintrags nach seiner Art: Zahl, Datum, bool oder eine id; null, wo die Art keinen Wert verlangt */
	private static String checkedEntryValue(TodojobRueckmeldungEntryType type, String value) {
		String text = value == null ? "" : value.trim();
		switch (type) {
			case ZAHL -> {
				try {
					Double.parseDouble(text.replace(',', '.'));
				} catch (NumberFormatException e) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZAHL braucht eine Zahl als value");
				}
				return text.replace(',', '.');
			}
			case DATUM -> {
				try {
					LocalDate.parse(text);
				} catch (DateTimeParseException e) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DATUM braucht ein Datum JJJJ-MM-TT als value");
				}
				return text;
			}
			case BOOL -> {
				if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BOOL braucht true oder false als value");
				}
				return text.toLowerCase(Locale.ROOT);
			}
			case DMS_DATEI, ARTIKELNUMMER, STERNZEIT -> {
				if (text.isEmpty()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type + " braucht eine id als value");
				}
				return text;
			}
			default -> {
				return text.isEmpty() ? null : text;
			}
		}
	}

	private static void putIfNotEmpty(Map<String, Object> document, String fieldName, List<String> values) {
		if (!values.isEmpty()) {
			document.put(fieldName, values);
		}
	}

	// ===== Solr =====

	private Map<String, Object> realtimeGet(String id, ModifiableSolrParams params) {
		try {
			SolrDocument document = client().getById(id, params);
			return document == null ? null : SolrDocumentTodojobUtilsService.toMap(document);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	private TodojobResolvedDocumentsPage resolvePage(ModifiableSolrParams params, int page) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist kleiner als 0");
		}
		int start = page * resolvePageSize;
		int rows = Math.min(resolvePageSize, resolveMaxResults - start);
		if (rows <= 0) {
			return new TodojobResolvedDocumentsPage(0, page, resolvePageSize, List.of());
		}
		params.set("q", "*:*");
		params.set("start", start);
		params.set("rows", rows);
		QueryResponse response = query(params);
		return new TodojobResolvedDocumentsPage(response.getResults().getNumFound(), page, resolvePageSize,
				documentsOf(response));
	}

	private long count(ModifiableSolrParams params) {
		params.set("q", "*:*");
		params.set("rows", 0);
		return query(params).getResults().getNumFound();
	}

	private QueryResponse query(ModifiableSolrParams params) {
		try {
			return client().query(params);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	private static List<Map<String, Object>> documentsOf(QueryResponse response) {
		List<Map<String, Object>> documents = new ArrayList<>();
		for (SolrDocument document : response.getResults()) {
			documents.add(SolrDocumentTodojobUtilsService.toMap(document));
		}
		return documents;
	}

	private SolrClient client() {
		return solrClientRegistry.clientFor(CORE_KEY);
	}

	private static ResponseStatusException unavailable(Exception e) {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Solr nicht erreichbar (Kern " + CORE_KEY + "): " + e.getMessage(), e);
	}

}
