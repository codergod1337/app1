package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsSearchDateRange;
import codergod1337.app1.dms.model.DmsSearchFilterOffer;
import codergod1337.app1.dms.model.DmsSearchHint;
import codergod1337.app1.dms.model.DmsSearchHit;
import codergod1337.app1.dms.model.DmsSearchHitReason;
import codergod1337.app1.dms.model.DmsSearchRequest;
import codergod1337.app1.dms.model.DmsSearchResultPage;
import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.dms.model.DmsSuggestResult;
import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.document.model.SolrSearchText;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.document.service.SolrDocumentService.SolrQueriedDocumentsPage;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Die Suche im Kern DMS, die dritte der drei Lesemethoden je Kern (Vorgabe 2026-10-07), nach der Suchmaske der Vorlage:
 * Freitext plus eingeloggte Filter, Seite und Größe. Baut ihre Solr-Abfrage selbst und ruft die rohe Solr-Schicht
 * direkt, es gibt keinen gemeinsamen Suchservice für alle Kerne.
 *
 * Tür: die AR DMS und DMS_SUCHE, auch für den Admin, sonst 403 vor Solr. Der Freitext wird zerlegt und escaped
 * ({@link SolrSearchText}), nie roh durchgereicht. Filter nur aus der festen Liste dieses Kerns (die Hooks, dazu
 * {@link #EXACT_FILTER_FIELD_NAMES}), gleiches Feld ODER, verschiedene Felder UND; Zeiträume nur für
 * {@link #DATE_RANGE_FILTER_FIELD_NAMES}. Ein Feld außerhalb der Liste wird überlesen, denn die Maske schickt ihre Chips
 * an jeden Kern. Ohne Suchwort ab drei Zeichen und ohne Filter keine Abfrage, sondern eine leere Antwort mit Hinweis.
 *
 * Pflicht-fq, hart: readAcl enthält eine AR des Users ODER userGrantedAccess seine guid, dazu Status ACTIVE oder
 * ARCHIVED. Keine Feldgrenze: Wer suchen darf, sieht alles, was die Dokumentgrenze zulässt, außer dem Volltext. Mit
 * Freitext nach Relevanz mal Recency sortiert, sonst nach documentCreatedAt absteigend. Paging gedeckelt.
 *
 * Treffergrund (User 2026-10-07): je Treffer die Felder, in denen etwas traf, mit den Wörtern. Aus dem Dokument selbst
 * für die Suchwörter und für jeden Filter, denn die Werte stehen in der Antwort; aus Solrs Highlighting nur für den
 * Volltext, der nie zum Client geht, und auch dort nur die Wörter, nie ein Ausschnitt.
 */
@Service
public class DmsSearchService {

	public static final int DEFAULT_PAGE_SIZE = 25;
	public static final int MAX_PAGE_SIZE = 100;

	/** So viele Seiten sind blätterbar. Deep Paging kostet Solr überproportional, und wer so weit blättert, sucht falsch. */
	public static final int MAX_PAGES = 25;

	/** So viele Treffer liefert die Vervollständigung, mehr zeigt kein Dropdown */
	public static final int SUGGEST_HITS = 15;

	/** Mehr Filterangebote zeigt keine Maske sinnvoll an */
	public static final int MAX_FILTER_OFFERS = 10;

	/** Die kompakte fl der Vervollständigung: was eine Trefferzeile braucht; die Hooks kommen für die Angebote dazu */
	private static final List<String> SUGGEST_FIELD_NAMES = List.of(SolrCore.ID_FIELD_NAME,
			DmsSolrDocumentFieldNames.FILE_NAME, DmsSolrDocumentFieldNames.FILE_INDEX,
			DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY, DmsSolrDocumentFieldNames.FILE_DESCRIPTION,
			DmsSolrDocumentFieldNames.STATUS, DmsSolrDocumentFieldNames.VERSION, DmsSolrDocumentFieldNames.FILE_SIZE_BYTES,
			DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT, DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE,
			DmsSolrDocumentFieldNames.IMAGE_PREVIEW_BASE64_PNG);

	/**
	 * Die Felder, nach denen die Suche exakt filtert, dazu jeder Hook. Fest im Code, nach der Vorlage vervollständigt:
	 * Dateiart (der Chip aus dem Katalog, „fsc.“ in der Eingabe), Index, Name, Kette, Endung, Typ, Sprache, Uploader,
	 * Status (nur ACTIVE und ARCHIVED kommen durch), Internetfreigabe, id.
	 */
	public static final Set<String> EXACT_FILTER_FIELD_NAMES = Set.of(SolrCore.ID_FIELD_NAME,
			DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY, DmsSolrDocumentFieldNames.FILE_INDEX,
			DmsSolrDocumentFieldNames.FILE_NAME, DmsSolrDocumentFieldNames.VERSION_CHAIN,
			DmsSolrDocumentFieldNames.FILE_EXTENSION, DmsSolrDocumentFieldNames.MIME_TYPE,
			DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE, DmsSolrDocumentFieldNames.UPLOADER,
			DmsSolrDocumentFieldNames.STATUS, DmsSolrDocumentFieldNames.IS_PUBLIC);

	/** Die Datumsfelder, für die ein Zeitraum als Filter geht, nur hier, nie im Auflösen */
	public static final Set<String> DATE_RANGE_FILTER_FIELD_NAMES = Set.of(DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT,
			DmsSolrDocumentFieldNames.DOCUMENT_UPLOADED_AT, DmsSolrDocumentFieldNames.LAST_CHANGED_AT);

	/**
	 * Die Suchfelder mit Gewicht, genau wie die DMS-Suche der Vorlage: die id ganz oben (wer den Hash tippt, meint genau
	 * dieses Dokument), dann Name, Index, Beschreibung, der Volltext zuletzt. Jeder Hook kommt mit {@link #HOOK_WEIGHT}
	 * dazu, jeder Suggested-Zwilling mit {@link #TWIN_WEIGHT} für den Präfix-Treffer ab drei Zeichen. Ein festes Feld,
	 * das in der Felddefinition fehlt, bleibt weg, denn ein Feld, das es in Solr nicht gibt, kippt die ganze Abfrage.
	 */
	private static final Map<String, Integer> FIXED_SEARCH_FIELD_WEIGHTS = fixedSearchFieldWeights();
	private static final int HOOK_WEIGHT = 2;
	private static final int TWIN_WEIGHT = 1;

	/** Die Zwillinge der Vervollständigung, Gewichte wie der Suggest der Vorlage: Dateiname vor Index vor allem anderen */
	private static final int SUGGEST_FILE_NAME_WEIGHT = 4;
	private static final int SUGGEST_FILE_INDEX_WEIGHT = 3;
	private static final int SUGGEST_OTHER_WEIGHT = 2;

	private static final String STATUS_FILTER_QUERY = DmsStateService.VISIBLE_STATUS_FILTER_QUERY;

	/**
	 * Relevanz mal Recency, nach der Vorlage: recip(x, m, a, b) = a / (m*x + b), x das Alter in Millisekunden. Mit
	 * m = 3.16e-11 ist der Score nach einem Jahr halbiert, ein sanfter Verlauf, der bei ähnlicher Relevanz das jüngere
	 * Dokument nach oben zieht. NOW/HOUR statt NOW: auf die Stunde gerundet bleibt die Funktion im Solr-Cache brauchbar.
	 */
	private static final String RECENCY_BOOST = "recip(ms(NOW/HOUR," + DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT
			+ "),3.16e-11,1,1)";

	/** Trennt die Werte eines terms-Parameters. Ein Steuerzeichen, das in keinem Wert vorkommt. */
	private static final String TERMS_PARSER_VALUE_SEPARATOR = "\u001F";

	/** Dieselben Marken wie im Treffergrund, Solr setzt sie um die Fundstellen im Volltext; der Ausschnitt selbst wird nie ausgeliefert */
	private static final String HIGHLIGHT_OPEN = DmsSearchHitReason.MARK_OPEN;
	private static final String HIGHLIGHT_CLOSE = DmsSearchHitReason.MARK_CLOSE;
	private static final int HIGHLIGHT_SNIPPETS = 3;
	private static final int HIGHLIGHT_FRAGMENT_SIZE = 80;
	/** Wie weit der Highlighter in den Volltext hineinsieht, rund 20 Textseiten */
	private static final int HIGHLIGHT_MAX_ANALYZED_CHARACTERS = 50_000;

	private static final Logger log = LoggerFactory.getLogger(DmsSearchService.class);

	/** Zum Aufmachen mehrsprachiger Feldwerte (fileDescription) für den Treffergrund */
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final DmsSolrDocumentChecker dmsSolrDocumentChecker;
	private final SolrFieldService solrFieldService;
	private final SolrHookService solrHookService;
	private final SolrDocumentService solrDocumentService;
	private final CurrentUsersProvider currentUsersProvider;

	public DmsSearchService(SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService,
			DmsSolrDocumentChecker dmsSolrDocumentChecker, SolrFieldService solrFieldService,
			SolrHookService solrHookService, SolrDocumentService solrDocumentService,
			CurrentUsersProvider currentUsersProvider) {
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.dmsSolrDocumentChecker = dmsSolrDocumentChecker;
		this.solrFieldService = solrFieldService;
		this.solrHookService = solrHookService;
		this.solrDocumentService = solrDocumentService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/**
	 * Die Suche: Tür DMS und DMS_SUCHE, Freitext zerlegt, Filter gegen die feste Liste, Pflicht-fq, Status, Paging. Die
	 * Treffer tragen alle Felder außer dem Volltext, _version_ als Text und ihren Treffergrund.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsSearchResultPage searchDmsSolrDocumentsPage(DmsSearchRequest searchRequest) {
		SolrCurrentUserReadAndWriteAccessForCore access = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(DmsSolrDocumentFieldNames.CORE_KEY);
		if (!access.freeTextSearchAllowed()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Rolle " + DmsSolrDocumentFieldNames.CORE_KEY
					+ SolrCurrentUserCoreAccessService.SEARCH_ROLE_SUFFIX);
		}
		int page = searchRequest.page() != null ? searchRequest.page() : 0;
		int size = searchRequest.size() != null ? searchRequest.size() : DEFAULT_PAGE_SIZE;
		if (page < 0 || page >= MAX_PAGES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page muss zwischen 0 und " + (MAX_PAGES - 1) + " liegen");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size muss zwischen 1 und " + MAX_PAGE_SIZE + " liegen");
		}

		SolrSearchText searchText = SolrSearchText.parse(searchRequest.searchText());
		if (searchText.tooManyWords()) {
			return DmsSearchResultPage.withoutQuery(DmsSearchHint.TOO_MANY_SEARCH_WORDS, size);
		}
		List<String> hookKeys = hookKeys();
		Map<String, List<Object>> exactFilters = checkedExactFilters(searchRequest.exactFilterValuesByFieldName(), hookKeys);
		Map<String, DmsSearchDateRange> dateRanges = checkedDateRangeFilters(searchRequest.dateRangeFilterByFieldName());
		if (!searchText.searchable() && exactFilters.isEmpty() && dateRanges.isEmpty()) {
			return DmsSearchResultPage.withoutQuery(DmsSearchHint.NO_SEARCH_WORD_AND_NO_FILTER, size);
		}

		Map<String, SolrField> fieldsByName = fieldsByName();
		Map<String, Integer> searchFieldWeights = searchFieldWeights(fieldsByName, hookKeys);
		// Boost und Sortierung nach documentCreatedAt brauchen docValues am Feld, sonst lehnt Solr die ganze Abfrage ab
		boolean createdAtSortable = isSortable(fieldsByName.get(DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT));
		SolrQuery query = new SolrQuery();
		if (searchText.searchable()) {
			query.setQuery(searchText.query());
			query.set("defType", "edismax");
			query.set("qf", queryFieldsParameter(searchFieldWeights));
			if (createdAtSortable) {
				query.set("boost", RECENCY_BOOST);
			}
			query.setSort("score", SolrQuery.ORDER.desc);
			// Highlighting immer (User 2026-10-07, kein Schalter mehr): der Treffergrund gehört zu jedem Treffer
			if (searchFieldWeights.containsKey(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS)) {
				addFullTextHighlighting(query);
			}
		} else {
			query.setQuery("*:*");
			if (createdAtSortable) {
				query.setSort(DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT, SolrQuery.ORDER.desc);
			} else {
				// ohne docValues am Datum: zuletzt geschrieben zuerst, _version_ hat immer docValues
				query.setSort(SolrDocumentService.VERSION_FIELD_NAME, SolrQuery.ORDER.desc);
			}
		}
		for (String filterQuery : access.readAclAndGrantedAccessFilterQueries()) {
			query.addFilterQuery(filterQuery);
		}
		query.addFilterQuery(STATUS_FILTER_QUERY);
		addExactFilterQueries(query, exactFilters);
		addDateRangeFilterQueries(query, dateRanges);
		List<String> fieldNames = new ArrayList<>();
		for (String name : access.readableFieldAndHookNamesOfCoreForCurrentUser()) {
			if (!DmsSolrDocumentFieldNames.SEARCHABLE_WORDS.equals(name)) {
				fieldNames.add(name);
			}
		}
		fieldNames.add(SolrDocumentService.VERSION_FIELD_NAME);
		query.setFields(fieldNames.toArray(String[]::new));
		query.setStart(page * size);
		query.setRows(size);

		SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(DmsSolrDocumentFieldNames.CORE_KEY, query);
		Set<String> reasonFieldNames = new LinkedHashSet<>(searchFieldWeights.keySet());
		reasonFieldNames.remove(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS);
		List<DmsSearchHit> hits = new ArrayList<>(queried.documents().size());
		for (Map<String, Object> document : queried.documents()) {
			hits.add(hit(document, searchText, exactFilters, dateRanges, reasonFieldNames, queried.highlightingByDocumentId()));
		}
		log.info("DMS-Suche von {}: {} Treffer, Seite {}, q='{}', Filter {}, Zeiträume {}, fq {}, qf {}",
				currentUsersProvider.getCurrentUsersGuid(), queried.totalFound(), page, query.getQuery(),
				exactFilters.keySet(), dateRanges.keySet(), List.of(query.getFilterQueries()), searchFieldWeights.keySet());
		return new DmsSearchResultPage(queried.totalFound(), page, size, hits, null);
	}

	/**
	 * Die Vervollständigung beim Tippen, nach dem Suggest der Vorlage: dieselbe Tür und dieselben Pflicht-fq wie die
	 * Suche, aber eine edismax-Abfrage nur über die Suggested-Zwillinge (Präfix ab drei Zeichen) und den Volltext, 15
	 * Treffer, kompakte Felder, kein Highlighting. Zurück kommen die Treffer mit Treffergrund aus ihren Feldern und die
	 * Filterangebote: ganze Werte filterbarer Felder der Treffer, deren Anfang ein Suchwort trifft. Nicht suchbar heißt
	 * stumm leer: Der Suggest ist Beiwerk, seinen Hinweis gibt die Suche.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsSuggestResult suggestDmsSolrDocuments(DmsSearchRequest searchRequest) {
		SolrCurrentUserReadAndWriteAccessForCore access = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(DmsSolrDocumentFieldNames.CORE_KEY);
		if (!access.freeTextSearchAllowed()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Rolle " + DmsSolrDocumentFieldNames.CORE_KEY
					+ SolrCurrentUserCoreAccessService.SEARCH_ROLE_SUFFIX);
		}
		SolrSearchText searchText = SolrSearchText.parse(searchRequest.searchText());
		if (!searchText.searchable()) {
			return DmsSuggestResult.empty();
		}
		List<String> hookKeys = hookKeys();
		Map<String, List<Object>> exactFilters = checkedExactFilters(searchRequest.exactFilterValuesByFieldName(), hookKeys);
		Map<String, DmsSearchDateRange> dateRanges = checkedDateRangeFilters(searchRequest.dateRangeFilterByFieldName());
		Map<String, Integer> searchFieldWeights = searchFieldWeights(fieldsByName(), hookKeys);
		Map<String, Integer> suggestFieldWeights = suggestFieldWeights(searchFieldWeights);
		if (suggestFieldWeights.isEmpty()) {
			return DmsSuggestResult.empty();
		}

		SolrQuery query = new SolrQuery(searchText.query());
		query.set("defType", "edismax");
		query.set("qf", queryFieldsParameter(suggestFieldWeights));
		query.setSort("score", SolrQuery.ORDER.desc);
		for (String filterQuery : access.readAclAndGrantedAccessFilterQueries()) {
			query.addFilterQuery(filterQuery);
		}
		query.addFilterQuery(STATUS_FILTER_QUERY);
		addExactFilterQueries(query, exactFilters);
		addDateRangeFilterQueries(query, dateRanges);
		List<String> fieldNames = new ArrayList<>();
		for (String name : SUGGEST_FIELD_NAMES) {
			if (access.isReadableByCurrentUser(name)) {
				fieldNames.add(name);
			}
		}
		for (String hookKey : hookKeys) {
			if (access.isReadableByCurrentUser(hookKey)) {
				fieldNames.add(hookKey);
			}
		}
		fieldNames.add(SolrDocumentService.VERSION_FIELD_NAME);
		query.setFields(fieldNames.toArray(String[]::new));
		query.setRows(SUGGEST_HITS);

		SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(DmsSolrDocumentFieldNames.CORE_KEY, query);
		Set<String> reasonFieldNames = new LinkedHashSet<>(searchFieldWeights.keySet());
		reasonFieldNames.remove(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS);
		List<DmsSearchHit> hits = new ArrayList<>(queried.documents().size());
		for (Map<String, Object> document : queried.documents()) {
			hits.add(hit(document, searchText, exactFilters, dateRanges, reasonFieldNames, Map.of()));
		}
		return new DmsSuggestResult(hits, filterOffers(queried.documents(), searchText.positiveWords(), hookKeys, exactFilters));
	}

	// ===== Suchfelder =====

	/** Ohne id (User 2026-10-07: die braucht die Suche nicht), mit der Dateiart, denn nach ihr wird auch gefiltert */
	private static Map<String, Integer> fixedSearchFieldWeights() {
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put(DmsSolrDocumentFieldNames.FILE_NAME, 4);
		weights.put(DmsSolrDocumentFieldNames.FILE_INDEX, 3);
		weights.put(DmsSolrDocumentFieldNames.FILE_DESCRIPTION, 2);
		weights.put(DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY, 2);
		weights.put(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS, 1);
		return Map.copyOf(weights);
	}

	private List<String> hookKeys() {
		List<String> keys = new ArrayList<>();
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			keys.add(solrHook.getKey());
		}
		return keys;
	}

	/** Die Felddefinition des Kerns DMS nach Name, aus der Kopie im Speicher */
	private Map<String, SolrField> fieldsByName() {
		Map<String, SolrField> fieldsByName = new LinkedHashMap<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DmsSolrDocumentFieldNames.CORE_KEY)) {
			fieldsByName.put(solrField.getName(), solrField);
		}
		return fieldsByName;
	}

	/** Sortieren und Funktionen wie ms() brauchen docValues an einem einwertigen Feld, sonst antwortet Solr mit 400 */
	private static boolean isSortable(SolrField solrField) {
		return solrField != null && solrField.isDocValues() && !solrField.isMultiValued();
	}

	/**
	 * Die Felder der qf mit Gewicht, in dieser Reihenfolge: die festen Felder, soweit die Felddefinition sie hat, ihre
	 * Zwillinge, die Hooks und deren Zwillinge. Hooks haben immer einen Zwilling (SolrHook.FIELD_SUGGEST).
	 */
	private static Map<String, Integer> searchFieldWeights(Map<String, SolrField> fieldsByName, List<String> hookKeys) {
		Map<String, Integer> weights = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> fixed : FIXED_SEARCH_FIELD_WEIGHTS.entrySet()) {
			SolrField solrField = fieldsByName.get(fixed.getKey());
			if (solrField == null || !solrField.isIndexed()) {
				continue;
			}
			weights.put(fixed.getKey(), fixed.getValue());
			if (solrField.isSuggest() && solrField.getType().isSuggestable()) {
				weights.put(fixed.getKey() + SolrFieldName.SUGGEST_SUFFIX, TWIN_WEIGHT);
			}
		}
		for (String hookKey : hookKeys) {
			weights.put(hookKey, HOOK_WEIGHT);
			if (SolrHook.FIELD_SUGGEST && SolrHook.FIELD_TYPE.isSuggestable()) {
				weights.put(hookKey + SolrFieldName.SUGGEST_SUFFIX, TWIN_WEIGHT);
			}
		}
		return weights;
	}

	/**
	 * Die qf der Vervollständigung aus der qf der Suche: nur die Zwillinge, Dateiname 4, Index 3, alles andere 2, dazu
	 * der Volltext als exakter Token-Match. Felder ohne Zwilling fallen weg, denn ohne EdgeNGram gibt es keinen Präfix.
	 */
	private static Map<String, Integer> suggestFieldWeights(Map<String, Integer> searchFieldWeights) {
		Map<String, Integer> weights = new LinkedHashMap<>();
		for (String name : searchFieldWeights.keySet()) {
			if (name.endsWith(SolrFieldName.SUGGEST_SUFFIX) || DmsSolrDocumentFieldNames.SEARCHABLE_WORDS.equals(name)) {
				continue;
			}
			String twin = name + SolrFieldName.SUGGEST_SUFFIX;
			if (!searchFieldWeights.containsKey(twin)) {
				continue;
			}
			int weight = DmsSolrDocumentFieldNames.FILE_NAME.equals(name) ? SUGGEST_FILE_NAME_WEIGHT
					: DmsSolrDocumentFieldNames.FILE_INDEX.equals(name) ? SUGGEST_FILE_INDEX_WEIGHT
					: SUGGEST_OTHER_WEIGHT;
			weights.put(twin, weight);
		}
		Integer fullText = searchFieldWeights.get(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS);
		if (fullText != null) {
			weights.put(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS, fullText);
		}
		return weights;
	}

	private static String queryFieldsParameter(Map<String, Integer> searchFieldWeights) {
		List<String> parts = new ArrayList<>(searchFieldWeights.size());
		for (Map.Entry<String, Integer> weight : searchFieldWeights.entrySet()) {
			parts.add(weight.getKey() + "^" + weight.getValue());
		}
		return String.join(" ", parts);
	}

	/** Highlighting nur auf dem Volltext, mit Steuerzeichen als Markierung. requireFieldMatch false: getroffen hat oft ein Zwilling. */
	private static void addFullTextHighlighting(SolrQuery query) {
		query.setHighlight(true);
		query.set("hl.method", "unified");
		query.set("hl.fl", DmsSolrDocumentFieldNames.SEARCHABLE_WORDS);
		query.set("hl.requireFieldMatch", false);
		query.set("hl.snippets", HIGHLIGHT_SNIPPETS);
		query.set("hl.fragsize", HIGHLIGHT_FRAGMENT_SIZE);
		query.set("hl.maxAnalyzedChars", HIGHLIGHT_MAX_ANALYZED_CHARACTERS);
		query.set("hl.tag.pre", HIGHLIGHT_OPEN);
		query.set("hl.tag.post", HIGHLIGHT_CLOSE);
		query.set("hl.simple.pre", HIGHLIGHT_OPEN);
		query.set("hl.simple.post", HIGHLIGHT_CLOSE);
	}

	// ===== Filter =====

	/**
	 * Die exakten Filter: nur Felder der festen Liste und Hooks, alles andere wird überlesen. Die Werte kommen als Text
	 * und werden nach dem Typ des Feldes in JSON gewandelt, dann prüft der Checker sie wie beim Auflösen. Ein Wert, der
	 * nicht zum Typ passt, ist eine 400.
	 */
	private Map<String, List<Object>> checkedExactFilters(Map<String, List<String>> filterValuesByFieldName,
			List<String> hookKeys) {
		if (filterValuesByFieldName == null || filterValuesByFieldName.isEmpty()) {
			return Map.of();
		}
		ObjectNode conditions = JsonNodeFactory.instance.objectNode();
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, List<String>> filter : filterValuesByFieldName.entrySet()) {
			String fieldName = filter.getKey();
			if (!EXACT_FILTER_FIELD_NAMES.contains(fieldName) && !hookKeys.contains(fieldName)) {
				log.debug("DMS-Suche überliest den Filter {}: kein filterbares Feld des Kerns", fieldName);
				continue;
			}
			SolrFieldType type = dmsSolrDocumentChecker.solrFieldTypeOf(fieldName);
			if (type == null || filter.getValue() == null) {
				continue;
			}
			ArrayNode values = JsonNodeFactory.instance.arrayNode();
			for (String value : filter.getValue()) {
				if (value == null || value.isBlank()) {
					continue;
				}
				JsonNode typed = typedFilterValue(fieldName, type, value.strip(), problems);
				if (typed != null) {
					values.add(typed);
				}
			}
			if (!values.isEmpty()) {
				conditions.set(fieldName, values);
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		if (conditions.isEmpty()) {
			return Map.of();
		}
		return dmsSolrDocumentChecker.checkExactResolveFieldValues(conditions);
	}

	/** Ein Chip-Wert als JSON im Typ des Feldes, damit der Checker ihn prüfen kann. null mit Beanstandung, wenn er nicht passt. */
	private static JsonNode typedFilterValue(String fieldName, SolrFieldType type, String value, List<String> problems) {
		JsonNodeFactory nodes = JsonNodeFactory.instance;
		switch (type) {
			case BOOLEAN -> {
				if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
					return nodes.booleanNode(Boolean.parseBoolean(value));
				}
				problems.add(fieldName + ": erwartet true oder false");
				return null;
			}
			case LONG -> {
				try {
					return nodes.numberNode(Long.parseLong(value));
				} catch (NumberFormatException e) {
					problems.add(fieldName + ": erwartet eine ganze Zahl");
					return null;
				}
			}
			case DOUBLE -> {
				try {
					return nodes.numberNode(Double.parseDouble(value));
				} catch (NumberFormatException e) {
					problems.add(fieldName + ": erwartet eine Zahl");
					return null;
				}
			}
			default -> {
				return nodes.stringNode(value);
			}
		}
	}

	/** Zeiträume nur für die Datumsfelder der Liste, leere fallen weg, von nach bis ist eine 400 */
	private static Map<String, DmsSearchDateRange> checkedDateRangeFilters(
			Map<String, DmsSearchDateRange> dateRangeByFieldName) {
		if (dateRangeByFieldName == null || dateRangeByFieldName.isEmpty()) {
			return Map.of();
		}
		Map<String, DmsSearchDateRange> ranges = new LinkedHashMap<>();
		for (Map.Entry<String, DmsSearchDateRange> filter : dateRangeByFieldName.entrySet()) {
			String fieldName = filter.getKey();
			DmsSearchDateRange range = filter.getValue();
			if (!DATE_RANGE_FILTER_FIELD_NAMES.contains(fieldName)) {
				log.debug("DMS-Suche überliest den Zeitraum {}: kein Datumsfeld der Liste", fieldName);
				continue;
			}
			if (range == null || range.isEmpty()) {
				continue;
			}
			if (range.from() != null && range.to() != null && range.from().isAfter(range.to())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + ": von liegt nach bis");
			}
			ranges.put(fieldName, range);
		}
		return ranges;
	}

	/** Je Feld ein fq: Textfelder als escaped Phrasen, alles andere über den terms-Parser mit den Werten als eigenem Parameter */
	private void addExactFilterQueries(SolrQuery query, Map<String, List<Object>> exactFilters) {
		int index = 0;
		for (Map.Entry<String, List<Object>> filter : exactFilters.entrySet()) {
			String fieldName = filter.getKey();
			SolrFieldType type = dmsSolrDocumentChecker.solrFieldTypeOf(fieldName);
			if (type != null && type.isText()) {
				List<String> phrases = new ArrayList<>();
				for (Object value : filter.getValue()) {
					phrases.add("\"" + ClientUtils.escapeQueryChars(value.toString()) + "\"");
				}
				query.addFilterQuery(fieldName + ":(" + String.join(" OR ", phrases) + ")");
			} else {
				String parameter = "sf" + index++;
				List<String> texts = new ArrayList<>();
				for (Object value : filter.getValue()) {
					texts.add(value.toString());
				}
				query.addFilterQuery("{!terms f=" + fieldName + " separator=\"" + TERMS_PARSER_VALUE_SEPARATOR + "\" v=$"
						+ parameter + "}");
				query.set(parameter, String.join(TERMS_PARSER_VALUE_SEPARATOR, texts));
			}
		}
	}

	/** Je Datumsfeld ein Bereichs-fq mit Tagesgrenzen in UTC: von einschließlich, bis ausschließlich dem Folgetag */
	private static void addDateRangeFilterQueries(SolrQuery query, Map<String, DmsSearchDateRange> dateRanges) {
		for (Map.Entry<String, DmsSearchDateRange> filter : dateRanges.entrySet()) {
			DmsSearchDateRange range = filter.getValue();
			String from = range.from() == null ? "*" : DateTimeFormatter.ISO_INSTANT.format(startOfDay(range.from()));
			String to = range.to() == null ? "*" : DateTimeFormatter.ISO_INSTANT.format(startOfDay(range.to().plusDays(1)));
			query.addFilterQuery(filter.getKey() + ":[" + from + " TO " + to + (range.to() == null ? "]" : "}"));
		}
	}

	private static Instant startOfDay(LocalDate day) {
		return day.atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	// ===== Treffergrund =====

	/**
	 * Ein Treffer mit seinem Grund, nach dem Treffergrund der Vorlage, aus den Feldern des Treffers selbst, denn die
	 * Werte stehen in der Antwort und das trifft auch den Präfix des Zwillings: je durchsuchtem Feld der erste Wert, in
	 * dem ein Suchwort steht, mit allen Fundstellen markiert, Treffer am Anfang des Werts zuerst; dann je Filter der
	 * getroffene Wert ganz markiert, je Zeitraum der Tag; zuletzt der Volltext ohne Ausschnitt, wenn Solr darin
	 * markiert hat. Ein Feld höchstens einmal. Nichts wird erfunden: ohne Fund kein Eintrag.
	 */
	private static DmsSearchHit hit(Map<String, Object> document, SolrSearchText searchText,
			Map<String, List<Object>> exactFilters, Map<String, DmsSearchDateRange> dateRanges,
			Set<String> reasonFieldNames, Map<String, Map<String, List<String>>> highlightingByDocumentId) {
		List<String> needles = new ArrayList<>();
		for (String word : searchText.positiveWords()) {
			needles.add(word.toLowerCase(Locale.ROOT));
		}
		List<DmsSearchHitReason> atStart = new ArrayList<>();
		List<DmsSearchHitReason> inside = new ArrayList<>();
		Set<String> explained = new LinkedHashSet<>();
		if (!needles.isEmpty()) {
			for (String fieldName : reasonFieldNames) {
				for (String text : textsOf(document.get(fieldName))) {
					int first = firstMatch(text, needles);
					if (first < 0) {
						continue;
					}
					(first == 0 ? atStart : inside).add(new DmsSearchHitReason(fieldName, markAll(text, needles)));
					explained.add(fieldName);
					break;
				}
			}
		}
		List<DmsSearchHitReason> reasons = new ArrayList<>(atStart);
		reasons.addAll(inside);
		for (Map.Entry<String, List<Object>> filter : exactFilters.entrySet()) {
			if (explained.contains(filter.getKey())) {
				continue;
			}
			for (String text : textsOf(document.get(filter.getKey()))) {
				boolean matches = filter.getValue().stream().anyMatch(value -> value.toString().equalsIgnoreCase(text));
				if (matches) {
					reasons.add(new DmsSearchHitReason(filter.getKey(), HIGHLIGHT_OPEN + text + HIGHLIGHT_CLOSE));
					explained.add(filter.getKey());
					break;
				}
			}
		}
		for (Map.Entry<String, DmsSearchDateRange> filter : dateRanges.entrySet()) {
			if (!explained.contains(filter.getKey()) && document.get(filter.getKey()) instanceof Instant instant
					&& isWithin(instant, filter.getValue())) {
				String day = instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
				reasons.add(new DmsSearchHitReason(filter.getKey(), HIGHLIGHT_OPEN + day + HIGHLIGHT_CLOSE));
				explained.add(filter.getKey());
			}
		}
		Object id = document.get(SolrCore.ID_FIELD_NAME);
		Map<String, List<String>> highlighting = id == null ? null : highlightingByDocumentId.get(id.toString());
		List<String> snippets = highlighting == null ? null : highlighting.get(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS);
		if (snippets != null && snippets.stream().anyMatch(snippet -> snippet != null && snippet.contains(HIGHLIGHT_OPEN))) {
			// der Volltext geht nie zum Client: nur das Feld, kein Ausschnitt (User 2026-10-07)
			reasons.add(new DmsSearchHitReason(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS, null));
		}
		return new DmsSearchHit(withVersionAsText(document), List.copyOf(reasons));
	}

	/** Die Stelle des ersten Suchworts im Text, ohne Groß und Klein, -1 ohne Fund */
	private static int firstMatch(String text, List<String> needles) {
		String lower = text.toLowerCase(Locale.ROOT);
		int first = -1;
		for (String needle : needles) {
			int at = lower.indexOf(needle);
			if (at >= 0 && (first < 0 || at < first)) {
				first = at;
			}
		}
		return first;
	}

	/** Jede Fundstelle jedes Suchworts im Text markiert, überlappende zu einer Marke zusammengefasst */
	private static String markAll(String text, List<String> needles) {
		boolean[] marked = new boolean[text.length()];
		String lower = text.toLowerCase(Locale.ROOT);
		for (String needle : needles) {
			if (needle.isEmpty()) {
				continue;
			}
			int at = lower.indexOf(needle);
			while (at >= 0) {
				for (int index = at; index < at + needle.length() && index < marked.length; index++) {
					marked[index] = true;
				}
				at = lower.indexOf(needle, at + needle.length());
			}
		}
		StringBuilder snippet = new StringBuilder(text.length() + 8);
		boolean open = false;
		for (int index = 0; index < text.length(); index++) {
			if (marked[index] && !open) {
				snippet.append(HIGHLIGHT_OPEN);
				open = true;
			} else if (!marked[index] && open) {
				snippet.append(HIGHLIGHT_CLOSE);
				open = false;
			}
			snippet.append(text.charAt(index));
		}
		if (open) {
			snippet.append(HIGHLIGHT_CLOSE);
		}
		return snippet.toString();
	}

	// ===== Filterangebote =====

	/**
	 * Die Filterangebote aus den Treffern der Vervollständigung: Hook-Werte und fileIndex als Ganzwert-Präfix, der
	 * Dateiname als Wortanfang, je gegen die Suchwörter ohne Groß und Klein. Was schon als Filter gesetzt ist, wird
	 * nicht noch einmal angeboten. Der erste Treffer gewinnt, höchstens MAX_FILTER_OFFERS.
	 */
	private static List<DmsSearchFilterOffer> filterOffers(List<Map<String, Object>> documents, List<String> positiveWords,
			List<String> hookKeys, Map<String, List<Object>> exactFilters) {
		List<String> needles = new ArrayList<>();
		for (String word : positiveWords) {
			needles.add(word.toLowerCase(Locale.ROOT));
		}
		Map<String, DmsSearchFilterOffer> offers = new LinkedHashMap<>();
		for (Map<String, Object> document : documents) {
			for (String hookKey : hookKeys) {
				offerValues(offers, hookKey, textsOf(document.get(hookKey)), needles, false, null, null, exactFilters);
			}
			offerValues(offers, DmsSolrDocumentFieldNames.FILE_INDEX, textsOf(document.get(DmsSolrDocumentFieldNames.FILE_INDEX)),
					needles, false, null, null, exactFilters);
			Object fileSubClassKey = document.get(DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY);
			Object preview = document.get(DmsSolrDocumentFieldNames.IMAGE_PREVIEW_BASE64_PNG);
			offerValues(offers, DmsSolrDocumentFieldNames.FILE_NAME, textsOf(document.get(DmsSolrDocumentFieldNames.FILE_NAME)),
					needles, true, fileSubClassKey == null ? null : fileSubClassKey.toString(),
					preview == null ? null : preview.toString(), exactFilters);
			if (offers.size() >= MAX_FILTER_OFFERS) {
				break;
			}
		}
		return List.copyOf(offers.values());
	}

	private static void offerValues(Map<String, DmsSearchFilterOffer> offers, String fieldName, List<String> values,
			List<String> needles, boolean wordStart, String label, String imagePreviewBase64Png,
			Map<String, List<Object>> exactFilters) {
		for (String value : values) {
			if (offers.size() >= MAX_FILTER_OFFERS) {
				return;
			}
			String lower = value.toLowerCase(Locale.ROOT);
			boolean matches;
			if (wordStart) {
				matches = false;
				for (String word : lower.split("[^\\p{L}\\p{N}]+")) {
					if (!word.isEmpty() && needles.stream().anyMatch(word::startsWith)) {
						matches = true;
						break;
					}
				}
			} else {
				matches = needles.stream().anyMatch(lower::startsWith);
			}
			if (!matches || isAlreadyFiltered(exactFilters, fieldName, value)) {
				continue;
			}
			offers.putIfAbsent(fieldName + TERMS_PARSER_VALUE_SEPARATOR + value,
					new DmsSearchFilterOffer(fieldName, value, label, imagePreviewBase64Png));
		}
	}

	private static boolean isAlreadyFiltered(Map<String, List<Object>> exactFilters, String fieldName, String value) {
		List<Object> values = exactFilters.get(fieldName);
		return values != null && values.stream().anyMatch(filtered -> filtered.toString().equalsIgnoreCase(value));
	}

	private static boolean isWithin(Instant instant, DmsSearchDateRange range) {
		boolean afterFrom = range.from() == null || !instant.isBefore(startOfDay(range.from()));
		boolean beforeTo = range.to() == null || instant.isBefore(startOfDay(range.to().plusDays(1)));
		return afterFrom && beforeTo;
	}

	/**
	 * Die Texte eines Feldwerts: eine Liste wird elementweise genommen, ein Zeitpunkt als ISO-Text, ein mehrsprachiges
	 * JSON (fileDescription) als seine Werte je Sprache, denn „de“ ist kein Treffergrund, sondern der Name einer Schublade.
	 */
	private static List<String> textsOf(Object value) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof List<?> list) {
			List<String> texts = new ArrayList<>();
			for (Object element : list) {
				texts.addAll(textsOf(element));
			}
			return texts;
		}
		if (value instanceof Instant instant) {
			return List.of(instant.toString());
		}
		String text = value.toString();
		String stripped = text.strip();
		if (stripped.startsWith("{") && stripped.endsWith("}")) {
			try {
				JsonNode node = JSON.readTree(stripped);
				if (node.isObject()) {
					List<String> texts = new ArrayList<>();
					for (JsonNode entry : node) {
						if (entry.isString()) {
							texts.add(entry.asString());
						}
					}
					if (!texts.isEmpty()) {
						return texts;
					}
				}
			} catch (JacksonException ignored) {
				// kein JSON, nur ein Text in Klammern: dann der Rohwert
			}
		}
		return List.of(text);
	}

	/** _version_ hat 19 Stellen, mehr als JavaScript genau darstellt: zum Client als Text */
	private static Map<String, Object> withVersionAsText(Map<String, Object> document) {
		Object version = document.get(SolrDocumentService.VERSION_FIELD_NAME);
		if (version == null) {
			return document;
		}
		Map<String, Object> copy = new LinkedHashMap<>(document);
		copy.put(SolrDocumentService.VERSION_FIELD_NAME, version.toString());
		return copy;
	}

}
