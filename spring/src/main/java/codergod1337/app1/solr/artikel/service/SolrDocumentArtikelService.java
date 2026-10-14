package codergod1337.app1.solr.artikel.service;

import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.solr.artikel.model.ArtikelAggregatedReference;
import codergod1337.app1.solr.artikel.model.ArtikelAggregatedReferencesForClient;
import codergod1337.app1.solr.artikel.model.ArtikelDocumentForClient;
import codergod1337.app1.solr.artikel.model.ArtikelReferenceFlag;
import codergod1337.app1.solr.artikel.model.ArtikelResolvedDocumentsPage;
import codergod1337.app1.solr.artikel.model.ArtikelSearchHit;
import codergod1337.app1.solr.artikel.model.ArtikelSearchHitReason;
import codergod1337.app1.solr.artikel.model.ArtikelSearchedDocumentsPage;
import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.document.model.SolrDocumentFieldChangeOperation;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.document.service.SolrMirrorCoreDocumentWriteService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Lesen und Schreiben im Kern ARTIKEL, einem Spiegelkern aus ABAS, nach der Blaupause des DMS. Schreiben: kein Upload,
 * kein Bearbeiten durch einen Menschen, nur das Dokument aus den gemappten Werten. Jedes Feld des Kerns mit
 * {@code command} bekommt den Wert dieses Keys, nach seinem Typ gewandelt; {@code id} ist der Wert des Eintrags mit der
 * Rolle PRIMARY, die Artikelnummer. Ein neuer Artikel wird voll angelegt, ein vorhandener per Atomic Update geändert
 * (User 2026-10-13): je Feld mit command SET, ohne Wert SET null, damit weggefallene Verbindungen verschwinden; alles
 * ohne command, die Ablagefelder der aufaggregierten Suche, bleibt stehen. Vorgang ARTIKEL_WRITE_FROM_MAPPING im
 * Register docs/backend/solr-zugriff.md. Vorerst nur mit ADMIN; sobald der Worker schreibt, läuft er als System-User
 * durch dieselbe Methode. Lesen: die Mappe über die id, die Familie über den Hook masterArtikelNummer, Auflösen nach
 * exakten Werten, die Suche und die aufaggregierten Referenzen je Flag (Alternativgruppe bis zu einer Tiefe, vorwärts
 * aus den Dokumenten, rückwärts per Solr-Bedingung, Ergebnis per Atomic Update im Dokument abgelegt, Vorgang
 * ARTIKEL_REFERENCE_CACHE_UPDATE); die Tür ist die AR ARTIKEL, die Suche braucht ARTIKEL_SUCHE. Alles, was keine
 * Anfrage an Solr ist (Rollen, fl, Debuffs, Bedingungen, Gründe), liegt daneben im SolrDocumentArtikelUtilsService.
 */
@Service
public class SolrDocumentArtikelService {

	private static final Logger log = LoggerFactory.getLogger(SolrDocumentArtikelService.class);

	public static final String CORE_KEY = "ARTIKEL";

	/** Das Ziel, das ein Mapping tragen muss, damit es hier landet */
	public static final String TARGET_KEY = "SOLR_ARTIKEL";

	/** Die Felder des Backends: wann zuletzt aus ABAS gespiegelt, und ob der Lauf sauber war */
	public static final String SYNC_DATE_FIELD_NAME = "syncDatum";
	public static final String SYNC_STATUS_FIELD_NAME = "syncStatus";
	public static final String SYNC_STATUS_OK = "OK";
	public static final String SYNC_STATUS_WARN = "WARN";
	public static final String SYNC_ERROR_FIELD_NAME = "syncFehler";

	/**
	 * Der Hook, über den die Familie zusammenhängt (User 2026-10-10): Der Master trägt dort seine eigene Nummer, jeder
	 * Slave die des Masters. Ein Hook, also in jedem Kern dasselbe Feld, auch im DMS an den Dateien des Artikels.
	 */
	public static final String MASTER_ARTICLE_NUMBER_HOOK_NAME = "masterArtikelNummer";

	/** Die Felder jeder Trefferbox der Suche: Nummer, Name, Suchbegriff, Artikelart, Master, Sync und die Vorschau */
	private static final List<String> DISPLAY_FIELD_NAMES = List.of("id", "name", "such", "zmart", "ymasterartikel",
			MASTER_ARTICLE_NUMBER_HOOK_NAME, "artikelNummer", SYNC_STATUS_FIELD_NAME, "artikelVorschauBildBase64Png");

	/** So viele Mitglieder einer Familie kommen höchstens; mehr Slaves hat kein Master */
	private static final int MASTER_GROUP_ROWS = 500;

	/** Der Name des Artikels, für die Treffer der aufaggregierten Suche */
	private static final String NAME_FIELD_NAME = "name";

	/** Die Sternzeit als Hook, in jedem Kern dasselbe Feld, für den ABAS-Knopf an jedem Treffer */
	private static final String STERNZEIT_HOOK_NAME = "sternzeit";

	/** So viele Werte gehen höchstens in eine terms-Bedingung; mehr Nummern laufen in mehreren Abfragen */
	private static final int TERMS_VALUES_PER_QUERY = 200;

	private final SolrFieldService solrFieldService;
	private final SolrDocumentService solrDocumentService;
	private final SolrClientRegistry solrClientRegistry;
	private final SolrDocumentArtikelUtilsService utils;
	private final CurrentUsersProvider currentUsersProvider;

	/** Schreibt und liest die JSON-Zeilen der Ablagefelder, wie das Mapping die Zeilen seiner Tabellen-Einträge */
	private final ObjectMapper objectMapper;

	/** Treffer je Seite beim Auflösen, app1.solr.resolve.page-size */
	private final int resolvePageSize;

	/** Treffer insgesamt beim Auflösen, app1.solr.resolve.max-results; eine Seite darüber ist leer */
	private final int resolveMaxResults;

	/** Treffer der Suche, app1.solr.search.hits: nur die besten, keine Seiten */
	private final int searchHits;

	/** Höchste Tiefe über Alternativen bei der aufaggregierten Suche, app1.solr.artikel.references.max-depth */
	private final int referencesMaxDepth;

	/** Höchstens so viele Treffer je Flag, app1.solr.artikel.references.max-rows; darüber gilt das Ergebnis als gekürzt */
	private final int referencesMaxRows;

	/** Bauen und Schreiben aus den gemappten Werten, dieselbe Mechanik wie in jedem Spiegelkern */
	private final SolrMirrorCoreDocumentWriteService solrMirrorCoreDocumentWriteService;

	public SolrDocumentArtikelService(SolrFieldService solrFieldService, SolrDocumentService solrDocumentService,
			SolrMirrorCoreDocumentWriteService solrMirrorCoreDocumentWriteService,
			SolrClientRegistry solrClientRegistry, SolrDocumentArtikelUtilsService utils,
			CurrentUsersProvider currentUsersProvider, ObjectMapper objectMapper,
			@Value("${app1.solr.resolve.page-size}") int resolvePageSize,
			@Value("${app1.solr.resolve.max-results}") int resolveMaxResults,
			@Value("${app1.solr.search.hits}") int searchHits,
			@Value("${app1.solr.artikel.references.max-depth}") int referencesMaxDepth,
			@Value("${app1.solr.artikel.references.max-rows}") int referencesMaxRows) {
		this.solrFieldService = solrFieldService;
		this.solrDocumentService = solrDocumentService;
		this.solrMirrorCoreDocumentWriteService = solrMirrorCoreDocumentWriteService;
		this.solrClientRegistry = solrClientRegistry;
		this.utils = utils;
		this.currentUsersProvider = currentUsersProvider;
		this.objectMapper = objectMapper;
		this.resolvePageSize = resolvePageSize;
		this.resolveMaxResults = resolveMaxResults;
		this.searchHits = searchHits;
		this.referencesMaxDepth = referencesMaxDepth;
		this.referencesMaxRows = referencesMaxRows;
	}

	private SolrClient client() {
		// die HTTP-Verbindung zum Kern ARTIKEL, also zu http://<solr>/solr/ARTIKEL, je Kern genau ein Client
		return solrClientRegistry.clientFor(CORE_KEY);
	}

	private static ResponseStatusException unavailable(Exception e) {
		// SolrServerException oder IOException: Solr nicht erreichbar oder Verbindung abgebrochen. Wird HTTP 503.
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Solr nicht erreichbar (Kern " + CORE_KEY + "): " + e.getMessage(), e);
	}

	// ===== Lesen =====

	public ArtikelDocumentForClient getArtikelmappe(String id) {
		// Die Artikelmappe ruft das: das Dokument über seine id, die Artikelnummer, und seine Familie. null, wenn es den
		// Artikel nicht gibt oder der User ihn nicht sehen darf, der Controller macht daraus 404.
		Map<String, Object> document = getById(id);
		if (document == null) {
			return null;
		}
		List<Map<String, Object>> masterGroup = List.of();
		Object master = firstOf(document.get(MASTER_ARTICLE_NUMBER_HOOK_NAME));
		if (master != null && !String.valueOf(master).isBlank()) {
			masterGroup = getMasterGroup(String.valueOf(master));
		}
		return new ArtikelDocumentForClient(document, masterGroup);
	}

	public Map<String, Object> getById(String id) {
		// Schnellweg zum Auflösen: dieselbe Methode mit der einen Bedingung id, Seite 0; eine id trifft höchstens einmal
		List<Map<String, Object>> documents = resolveArtikelDocuments(Map.of("id", id), 0).documents();
		return documents.isEmpty() ? null : documents.get(0);
	}

	public List<Map<String, Object>> getMasterGroup(String masterArtikelNummer) {
		// Die Familie: alle Artikel, deren Hook masterArtikelNummer diese Nummer trägt, der Master selbst darunter,
		// aufsteigend nach Nummer. Die Bedingung setzt das Backend, der User braucht den Hook nicht lesen zu dürfen.
		ModifiableSolrParams params = baseParams();
		params.set("sort", "id asc");
		params.set("rows", MASTER_GROUP_ROWS);
		params.add("fq", MASTER_ARTICLE_NUMBER_HOOK_NAME + ":" + ClientUtils.escapeQueryChars(masterArtikelNummer));
		if (!utils.isEveryFieldReadable()) {
			utils.buildUserSolrResponseFieldList(params);
			utils.buildUserDebuffsForSolrRequest(params);
		}
		return query(params, "Familie " + masterArtikelNummer);
	}

	public ArtikelResolvedDocumentsPage resolveArtikelDocuments(Map<String, String> exactValueByFieldName, int page) {
		// Wird aufgerufen, wenn ein User nach konkreten Feldwerten auflösen will: exakte Werte, keine Suchtreffer mit
		// Score, Seiten ab 0 mit der Größe aus der YAML, aufsteigend nach Artikelnummer. Tür ist die AR ARTIKEL.
		utils.requireAccessRole();
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist kleiner als 0");
		}
		Map<String, List<String>> valuesByFieldName = new LinkedHashMap<>();
		for (Map.Entry<String, String> condition : exactValueByFieldName.entrySet()) {
			valuesByFieldName.put(condition.getKey(), List.of(condition.getValue()));
		}
		ModifiableSolrParams params = baseParams();
		// feste Reihenfolge, sonst sind die Seiten nicht stabil: die Artikelnummer ist der uniqueKey
		params.set("sort", "id asc");
		int start = page * resolvePageSize;
		int rows = Math.min(resolvePageSize, resolveMaxResults - start);
		if (rows <= 0) {
			return new ArtikelResolvedDocumentsPage(0, page, resolvePageSize, List.of());
		}
		params.set("start", start);
		params.set("rows", rows);
		if (utils.isEveryFieldReadable()) {
			// ADMIN, SOLR_USERS_VIEW_ALL, ARTIKEL_SUCHE: alles, kein fl, keine Debuffs; der Spiegelkern hat weder readAcl noch Status
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, null, List.of());
		} else {
			// alle anderen, Kunden, Lieferanten: fl aus den Lese-ARs der Felder, 403 für fremde Bedingungen, Debuffs darüber
			List<String> readableFieldNames = utils.buildUserSolrResponseFieldList(params);
			List<String> debuffedFieldNames = utils.buildUserDebuffsForSolrRequest(params);
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, readableFieldNames, debuffedFieldNames);
		}
		try {
			QueryResponse response = client().query(params);
			List<Map<String, Object>> documents = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				documents.add(new LinkedHashMap<>(document));
			}
			return new ArtikelResolvedDocumentsPage(response.getResults().getNumFound(), page, resolvePageSize, documents);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	public ArtikelSearchedDocumentsPage searchArtikelDocuments(String searchText,
			Map<String, List<String>> exactFilterValuesByFieldName, int page) {
		// Wird aufgerufen, wenn ein User im Suchmodul tippt oder Chips setzt: die Treffer mit Score, Gründen und Vorschau
		// für die Trefferbox, eine Seite ab 0 mit app1.solr.search.hits Treffern; „weitere laden“ in der Maske fragt die
		// nächste Seite (User 2026-10-14). Tür ist ARTIKEL_SUCHE oder ADMIN.
		String text = searchText == null ? "" : searchText.trim();
		if (text.isEmpty() && exactFilterValuesByFieldName.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weder Suchtext noch Filter");
		}
		long start = (long) page * searchHits;
		if (page < 0 || start > Integer.MAX_VALUE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page muss zwischen 0 und " + Integer.MAX_VALUE / searchHits + " liegen");
		}
		utils.requireSearchAccessRole();
		ModifiableSolrParams params = new ModifiableSolrParams();
		// die durchsuchten Felder des Kerns, Gewicht ab 0, in Listenreihenfolge: daraus qf, fl und die Gründe
		List<SolrField> searchedFields = new ArrayList<>();
		List<String> qf = new ArrayList<>();
		List<String> highlightedFieldNames = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(CORE_KEY)) {
			if (solrField.getSearchWeight() < 0) {
				continue;
			}
			searchedFields.add(solrField);
			// suggest und Hooks über den Zwilling <name>Suggested, der Wortanfänge kennt; Texttypen und string direkt
			String queriedName = solrField.isSuggest() ? solrField.getName() + "Suggested" : solrField.getName();
			qf.add(queriedName + "^" + solrField.getSearchWeight());
			// Solr markiert Treffer nur in Texttypen, der Zwilling ist nicht stored
			if (solrField.getType().isText()) {
				highlightedFieldNames.add(solrField.getName());
			}
		}
		if (text.isEmpty()) {
			params.set("q", "*:*");
		} else {
			if (qf.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Feld im Kern " + CORE_KEY + " hat ein Suchgewicht");
			}
			// edismax: jedes Wort gegen jedes Feld der qf mit dessen Gewicht; + und - vor einem Wort versteht es selbst
			params.set("defType", "edismax");
			params.set("q", text);
			params.set("qf", String.join(" ", qf));
			params.set("q.op", "OR");
			params.set("mm", "1");
			// sow: erst an Leerzeichen zerlegen, dann jedes Wort einzeln durch jeden Feld-Analyzer (User 2026-10-09)
			params.set("sow", "true");
			if (!highlightedFieldNames.isEmpty()) {
				params.set("hl", true);
				params.set("hl.fl", String.join(",", highlightedFieldNames));
				params.set("hl.method", "unified");
				params.set("hl.tag.pre", "\u0002");
				params.set("hl.tag.post", "\u0003");
			}
		}
		// das Treffergewicht entscheidet, die Artikelnummer nur bei gleichem Score
		params.set("sort", "score desc, id asc");
		// die Seite: spätere Seiten kosten Solr kaum mehr als die erste (gemessen 2026-10-14)
		params.set("start", (int) start);
		params.set("rows", searchHits);
		Set<String> fieldList = new LinkedHashSet<>(DISPLAY_FIELD_NAMES);
		for (SolrField solrField : searchedFields) {
			if (solrField.getType() != SolrFieldType.TEXT_SUCHE) {
				fieldList.add(solrField.getName());
			}
		}
		params.set("fl", String.join(",", fieldList));
		// die Chips des Clients: jedes Feld erlaubt, denn ARTIKEL_SUCHE gibt alle Felder und Hooks frei, kein Debuff
		utils.buildClientConditionsForSolrRequest(params, exactFilterValuesByFieldName, null, List.of());
		try {
			QueryResponse response = client().query(params);
			Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();
			List<String> searchWords = utils.searchWordsOf(text);
			List<ArtikelSearchHit> hits = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				Map<String, Object> fieldValues = new LinkedHashMap<>(document);
				Map<String, List<String>> snippetsByFieldName = highlighting == null ? null
						: highlighting.get(String.valueOf(fieldValues.get("id")));
				List<ArtikelSearchHitReason> reasons = new ArrayList<>();
				for (SolrField solrField : searchedFields) {
					String fieldName = solrField.getName();
					if (solrField.getType().isText()) {
						List<String> snippets = snippetsByFieldName == null ? null : snippetsByFieldName.get(fieldName);
						if (snippets != null && !snippets.isEmpty()) {
							String snippet = solrField.getType() == SolrFieldType.TEXT_SUCHE ? null : snippets.get(0);
							reasons.add(new ArtikelSearchHitReason(fieldName, snippet));
						}
						continue;
					}
					// string, suggest, Hooks: Solr markiert dort nichts, der Vergleich läuft über die gelieferten Werte
					String snippet = utils.startingWithSearchWord(fieldValues.get(fieldName), searchWords);
					if (snippet != null) {
						reasons.add(new ArtikelSearchHitReason(fieldName, snippet));
					}
				}
				hits.add(new ArtikelSearchHit(fieldValues, reasons));
			}
			return new ArtikelSearchedDocumentsPage(response.getResults().getNumFound(), page, searchHits, hits);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	private static ModifiableSolrParams baseParams() {
		// *:* trifft alles, die Einschränkung machen allein die fq
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "*:*");
		return params;
	}

	private List<Map<String, Object>> query(ModifiableSolrParams params, String purpose) {
		try {
			QueryResponse response = client().query(params);
			List<Map<String, Object>> documents = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				// SolrDocument ist selbst eine Map Feldname -> Wert: multiValued als Liste, pdate als Date
				documents.add(new LinkedHashMap<>(document));
			}
			return documents;
		} catch (SolrServerException | IOException e) {
			log.warn("Abfrage {} im Kern {} gescheitert: {}", purpose, CORE_KEY, e.getMessage());
			throw unavailable(e);
		}
	}

	/** Der erste Wert eines Feldes, egal ob Solr einen Wert oder eine Liste liefert */
	private static Object firstOf(Object value) {
		if (value instanceof List<?> values) {
			return values.isEmpty() ? null : values.get(0);
		}
		return value;
	}

	// ===== Aufaggregierte Referenzen je Flag (User 2026-10-13) =====

	/**
	 * Die aufaggregierten Verbindungen eines Artikels nach einem Flag, bis zu alternativeDepth Alternativschritten: aus
	 * der Ablage im Dokument, wenn dort ein Stand mit mindestens dieser Tiefe liegt (Zeilen jenseits der Tiefe fallen
	 * weg, nichts wird gerechnet), sonst gerechnet und abgelegt wie bei recomputeAggregatedReferences. Tür ARTIKEL, 404
	 * ohne Artikel, 403 wenn der User das Nummernfeld des Flags nicht lesen darf, 400 bei einer Tiefe über dem Deckel.
	 */
	public ArtikelAggregatedReferencesForClient getAggregatedReferences(String artikelNummer, ArtikelReferenceFlag flag,
			int alternativeDepth) {
		requireReferencesAccess(artikelNummer, flag, alternativeDepth);
		Map<String, Object> store = loadAggregatedReferencesStore(artikelNummer, flag);
		Object storedDepth = firstOf(store.get(flag.aggregatedDepthFieldName()));
		Object storedAt = firstOf(store.get(flag.aggregatedAtFieldName()));
		if (!(storedDepth instanceof Number depth) || !(storedAt instanceof Date at) || depth.intValue() < alternativeDepth) {
			return recomputeAggregatedReferences(artikelNummer, flag, alternativeDepth);
		}
		List<String> rows = valuesOf(store.get(flag.aggregatedTableFieldName()));
		int maxDistance = maxDistanceOf(flag, alternativeDepth);
		List<ArtikelAggregatedReference> references = new ArrayList<>();
		for (String row : rows) {
			ArtikelAggregatedReference reference = parseAggregatedReference(row);
			if (reference != null && reference.distance() <= maxDistance) {
				references.add(reference);
			}
		}
		// ob der Stand gekürzt war, steht nicht in der Ablage: voll bis zum Deckel heißt, es fehlten wahrscheinlich Treffer
		boolean truncated = rows.size() >= referencesMaxRows;
		return new ArtikelAggregatedReferencesForClient(artikelNummer, flag, alternativeDepth, at.toInstant(),
				blankUnreadableNames(references), truncated);
	}

	/**
	 * Rechnet die Verbindungen neu und legt sie im Dokument ab. Schritt 1 die Alternativgruppe bis zur Tiefe, je Schritt
	 * vorwärts aus den Listen der Dokumente und rückwärts über eine terms-Bedingung auf alternativeYreferenzText, neue
	 * Dokumente per terms auf id nachgeladen. Schritt 2 je Mitglied die Liste des Flags vorwärts und rückwärts; bei
	 * ALTERNATIVE ist die Gruppe selbst das Ergebnis. Der kürzeste Weg gewinnt, der Startartikel ist nie dabei. Dann
	 * Atomic Update SET auf die vier Ablagefelder, Vorgang ARTIKEL_REFERENCE_CACHE_UPDATE: Buchhaltung des Backends wie
	 * der Download-Zähler im DMS, kein writeAcl (der Spiegelkern hat keins), keine Revision, im Log der User. SET ersetzt
	 * die ganze Liste, nie ADD: Was nicht mehr gefunden wird, steht nicht mehr drin. 409, wenn die Ablagefelder im Kern
	 * fehlen.
	 */
	public ArtikelAggregatedReferencesForClient recomputeAggregatedReferences(String artikelNummer,
			ArtikelReferenceFlag flag, int alternativeDepth) {
		requireReferencesAccess(artikelNummer, flag, alternativeDepth);
		requireAggregatedFieldsInCore(flag);
		AggregationRun run = aggregateReferences(artikelNummer, flag, alternativeDepth);
		Instant now = Instant.now();
		List<String> rows = new ArrayList<>(run.references().size());
		Set<String> numbers = new LinkedHashSet<>();
		for (ArtikelAggregatedReference reference : run.references()) {
			rows.add(objectMapper.writeValueAsString(reference));
			numbers.add(reference.artikelNummer());
		}
		Map<String, Map<String, Object>> modifiersByFieldName = new LinkedHashMap<>();
		modifiersByFieldName.put(flag.aggregatedTableFieldName(), setModifier(rows.isEmpty() ? null : rows));
		modifiersByFieldName.put(flag.aggregatedArticleNumberFieldName(),
				setModifier(numbers.isEmpty() ? null : new ArrayList<>(numbers)));
		modifiersByFieldName.put(flag.aggregatedDepthFieldName(), setModifier((long) alternativeDepth));
		modifiersByFieldName.put(flag.aggregatedAtFieldName(), setModifier(now));
		solrDocumentService.atomicUpdateDocument(CORE_KEY, artikelNummer, modifiersByFieldName);
		log.info("ARTIKEL_REFERENCE_CACHE_UPDATE: Artikel {} Flag {} Tiefe {}: {} Treffer{} abgelegt, User {}",
				artikelNummer, flag, alternativeDepth, run.references().size(), run.truncated() ? " (gekürzt)" : "",
				currentUsersProvider.getCurrentUsersGuid());
		return new ArtikelAggregatedReferencesForClient(artikelNummer, flag, alternativeDepth, now,
				blankUnreadableNames(run.references()), run.truncated());
	}

	/** Eine Verbindung aus dem 20er: Nummer und Sternzeit, index-parallel aus den beiden Feldern des Flags */
	private record ReferencePair(String artikelNummer, String sternzeit) {
	}

	/**
	 * Ein Mitglied der Alternativgruppe beim Rechnen: wie es erreicht wurde (Entfernung in Alternativschritten, Weg,
	 * Rückverweis) und sein Dokument, null wenn der Artikel nicht im Spiegel steht (dann geht es dort nicht weiter)
	 */
	private record GroupMember(String artikelNummer, String sternzeit, String name, int distance, List<String> path,
			boolean reverse, Map<String, Object> document) {
	}

	/** Das Ergebnis eines Laufs: die Treffer sortiert und ob der Deckel erreicht wurde */
	private record AggregationRun(List<ArtikelAggregatedReference> references, boolean truncated) {
	}

	private AggregationRun aggregateReferences(String startArtikelNummer, ArtikelReferenceFlag flag,
			int alternativeDepth) {
		ArtikelReferenceFlag alternative = ArtikelReferenceFlag.ALTERNATIVE;
		Map<String, Object> startDocument = loadReferenceDocumentsByIds(List.of(startArtikelNummer))
				.get(startArtikelNummer);
		if (startDocument == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Artikel " + startArtikelNummer + " im Kern");
		}
		// die Gruppe in Reihenfolge des Findens: kleinere Entfernung zuerst, so gewinnt immer der kürzeste Weg
		Map<String, GroupMember> members = new LinkedHashMap<>();
		GroupMember start = new GroupMember(startArtikelNummer, textOf(startDocument, STERNZEIT_HOOK_NAME),
				textOf(startDocument, NAME_FIELD_NAME), 0, List.of(startArtikelNummer), false, startDocument);
		members.put(startArtikelNummer, start);
		boolean truncated = false;
		List<GroupMember> frontier = List.of(start);
		for (int hop = 1; hop <= alternativeDepth && !frontier.isEmpty() && !truncated; hop++) {
			Map<String, GroupMember> found = new LinkedHashMap<>();
			// vorwärts: die Alternativen, die ein Mitglied der Front selbst führt
			for (GroupMember member : frontier) {
				for (ReferencePair pair : pairsOf(member.document(), alternative)) {
					if (!members.containsKey(pair.artikelNummer()) && !found.containsKey(pair.artikelNummer())) {
						found.put(pair.artikelNummer(), new GroupMember(pair.artikelNummer(), pair.sternzeit(), "", hop,
								appended(member.path(), pair.artikelNummer()), false, null));
					}
				}
			}
			// rückwärts: wer ein Mitglied der Front als Alternative führt, ABAS hat den Eintrag nur auf einer Seite
			List<String> frontierNumbers = frontier.stream().map(GroupMember::artikelNummer).toList();
			for (Map<String, Object> document : queryReferenceDocumentsByFieldValues(alternative.articleNumberFieldName(),
					frontierNumbers)) {
				String nummer = textOf(document, SolrDocumentService.ID_FIELD_NAME);
				if (nummer.isEmpty() || members.containsKey(nummer) || found.containsKey(nummer)) {
					continue;
				}
				GroupMember parent = firstListedOf(document, alternative, frontier);
				found.put(nummer, new GroupMember(nummer, textOf(document, STERNZEIT_HOOK_NAME),
						textOf(document, NAME_FIELD_NAME), hop, appended(parent.path(), nummer), true, document));
			}
			// die vorwärts gefundenen nachladen: ihre eigenen Listen für den nächsten Schritt und ihren Namen
			List<String> toLoad = found.values().stream().filter(candidate -> candidate.document() == null)
					.map(GroupMember::artikelNummer).toList();
			Map<String, Map<String, Object>> loaded = loadReferenceDocumentsByIds(toLoad);
			List<GroupMember> next = new ArrayList<>();
			for (GroupMember candidate : found.values()) {
				// der Deckel zählt die Mitglieder ohne den Start, denn bei ALTERNATIVE sind sie das Ergebnis
				if (members.size() > referencesMaxRows) {
					truncated = true;
					break;
				}
				Map<String, Object> document = candidate.document() != null ? candidate.document()
						: loaded.get(candidate.artikelNummer());
				GroupMember member = document == null ? candidate
						: new GroupMember(candidate.artikelNummer(),
								candidate.sternzeit().isEmpty() ? textOf(document, STERNZEIT_HOOK_NAME) : candidate.sternzeit(),
								textOf(document, NAME_FIELD_NAME), candidate.distance(), candidate.path(), candidate.reverse(),
								document);
				members.put(member.artikelNummer(), member);
				if (document != null) {
					next.add(member);
				}
			}
			frontier = next;
		}

		Map<String, ArtikelAggregatedReference> referenceByNummer = new LinkedHashMap<>();
		if (flag == alternative) {
			// die Gruppe selbst ist das Ergebnis, die Entfernung ist die Zahl der Alternativschritte
			for (GroupMember member : members.values()) {
				if (member.distance() > 0) {
					referenceByNummer.put(member.artikelNummer(), new ArtikelAggregatedReference(member.artikelNummer(),
							member.sternzeit(), member.name(), member.distance(), member.path(), member.reverse()));
				}
			}
		} else {
			// vorwärts: die Liste des Flags an jedem Mitglied, eine Beziehung weiter als das Mitglied
			for (GroupMember member : members.values()) {
				for (ReferencePair pair : pairsOf(member.document(), flag)) {
					if (!pair.artikelNummer().equals(startArtikelNummer)) {
						putIfShorter(referenceByNummer, new ArtikelAggregatedReference(pair.artikelNummer(), pair.sternzeit(),
								"", member.distance() + 1, member.path(), false));
					}
				}
			}
			// rückwärts: wer ein Mitglied in seiner Liste des Flags führt, in der Mappe der Crimpzange die Artikel dazu
			List<GroupMember> memberList = new ArrayList<>(members.values());
			List<String> memberNumbers = memberList.stream().map(GroupMember::artikelNummer).toList();
			for (Map<String, Object> document : queryReferenceDocumentsByFieldValues(flag.articleNumberFieldName(),
					memberNumbers)) {
				String nummer = textOf(document, SolrDocumentService.ID_FIELD_NAME);
				if (nummer.isEmpty() || nummer.equals(startArtikelNummer)) {
					continue;
				}
				GroupMember parent = firstListedOf(document, flag, memberList);
				putIfShorter(referenceByNummer, new ArtikelAggregatedReference(nummer, textOf(document, STERNZEIT_HOOK_NAME),
						textOf(document, NAME_FIELD_NAME), parent.distance() + 1, parent.path(), true));
			}
			// die vorwärts gefundenen tragen nur Nummer und Sternzeit aus dem 20er, der Name kommt aus ihrem Dokument
			List<String> toLoad = referenceByNummer.values().stream().filter(reference -> reference.name().isEmpty())
					.map(ArtikelAggregatedReference::artikelNummer).toList();
			Map<String, Map<String, Object>> loaded = loadReferenceDocumentsByIds(toLoad);
			for (Map.Entry<String, ArtikelAggregatedReference> entry : referenceByNummer.entrySet()) {
				Map<String, Object> document = loaded.get(entry.getKey());
				if (document != null) {
					ArtikelAggregatedReference reference = entry.getValue();
					entry.setValue(new ArtikelAggregatedReference(reference.artikelNummer(),
							reference.sternzeit().isEmpty() ? textOf(document, STERNZEIT_HOOK_NAME) : reference.sternzeit(),
							textOf(document, NAME_FIELD_NAME), reference.distance(), reference.pathArtikelNummern(),
							reference.foundByReverseLookup()));
				}
			}
		}
		List<ArtikelAggregatedReference> references = new ArrayList<>(referenceByNummer.values());
		references.sort(Comparator.comparingInt(ArtikelAggregatedReference::distance)
				.thenComparing(ArtikelAggregatedReference::artikelNummer));
		if (references.size() > referencesMaxRows) {
			references = new ArrayList<>(references.subList(0, referencesMaxRows));
			truncated = true;
		}
		return new AggregationRun(references, truncated);
	}

	/** Tür, Deckel der Tiefe, Artikel sichtbar, Feld des Flags lesbar; sonst 403, 400, 404, 403 */
	private void requireReferencesAccess(String artikelNummer, ArtikelReferenceFlag flag, int alternativeDepth) {
		utils.requireAccessRole();
		if (alternativeDepth < 0 || alternativeDepth > referencesMaxDepth) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"alternativeDepth muss zwischen 0 und " + referencesMaxDepth + " liegen");
		}
		if (getById(artikelNummer) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Artikel " + artikelNummer + " im Kern");
		}
		if (!utils.isEveryFieldReadable()
				&& !utils.readableFieldNamesOfCurrentUser().contains(flag.articleNumberFieldName())) {
			// dieselbe Regel wie beim Auflösen: keine Bedingung auf ein Feld, das der User nicht lesen darf
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"das Feld " + flag.articleNumberFieldName() + " ist für dich nicht lesbar");
		}
	}

	/** Die vier Ablagefelder muss der Admin im Kern-Tab angelegt haben, sonst lehnt Solr das Atomic Update ab */
	private void requireAggregatedFieldsInCore(ArtikelReferenceFlag flag) {
		Set<String> fieldNames = new HashSet<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(CORE_KEY)) {
			fieldNames.add(solrField.getName());
		}
		List<String> missing = flag.aggregatedFieldNames().stream().filter(name -> !fieldNames.contains(name)).toList();
		if (!missing.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "im Kern " + CORE_KEY + " fehlen die Ablagefelder "
					+ String.join(", ", missing) + ", im Kern-Tab anlegen");
		}
	}

	/** Die vier Ablagefelder eines Artikels, eine Anfrage des Backends ohne Feldgrenze; leer ohne Dokument */
	private Map<String, Object> loadAggregatedReferencesStore(String artikelNummer, ArtikelReferenceFlag flag) {
		ModifiableSolrParams params = baseParams();
		params.add("fq", SolrDocumentService.ID_FIELD_NAME + ":" + ClientUtils.escapeQueryChars(artikelNummer));
		params.set("fl", String.join(",", flag.aggregatedFieldNames()));
		params.set("rows", 1);
		List<Map<String, Object>> documents = query(params, "Ablage " + flag + " " + artikelNummer);
		return documents.isEmpty() ? Map.of() : documents.get(0);
	}

	/** Die Felder, die das Rechnen braucht: id, Name, Sternzeit und die acht Listen der vier Flags; fl intern fest */
	private static String referenceFieldList() {
		List<String> fieldNames = new ArrayList<>(
				List.of(SolrDocumentService.ID_FIELD_NAME, NAME_FIELD_NAME, STERNZEIT_HOOK_NAME));
		for (ArtikelReferenceFlag flag : ArtikelReferenceFlag.values()) {
			fieldNames.add(flag.articleNumberFieldName());
			fieldNames.add(flag.sternzeitFieldName());
		}
		return String.join(",", fieldNames);
	}

	/** Die Dokumente zu diesen Nummern, nach Nummer; was nicht im Spiegel steht, fehlt in der Map */
	private Map<String, Map<String, Object>> loadReferenceDocumentsByIds(List<String> ids) {
		Map<String, Map<String, Object>> documentById = new HashMap<>();
		for (Map<String, Object> document : queryReferenceDocumentsByFieldValues(SolrDocumentService.ID_FIELD_NAME, ids)) {
			documentById.put(textOf(document, SolrDocumentService.ID_FIELD_NAME), document);
		}
		return documentById;
	}

	/**
	 * Alle Artikel, deren Feld einen der Werte trägt, über den terms-Parser (die Werte als Liste, nie als Query-String
	 * zusammengeklebt), in Stücken von TERMS_VALUES_PER_QUERY. Die Bedingung setzt das Backend, der User braucht das
	 * Feld nicht zu lesen; seine Debuffs gelten trotzdem. Höchstens max-rows plus eins je Stück, damit der Deckel greift.
	 */
	private List<Map<String, Object>> queryReferenceDocumentsByFieldValues(String fieldName, List<String> values) {
		List<Map<String, Object>> documents = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int from = 0; from < values.size(); from += TERMS_VALUES_PER_QUERY) {
			List<String> chunk = values.subList(from, Math.min(values.size(), from + TERMS_VALUES_PER_QUERY));
			ModifiableSolrParams params = baseParams();
			params.add("fq", "{!terms f=" + fieldName + "}" + String.join(",", chunk));
			params.set("fl", referenceFieldList());
			params.set("sort", "id asc");
			params.set("rows", referencesMaxRows + 1);
			if (!utils.isEveryFieldReadable()) {
				utils.buildUserDebuffsForSolrRequest(params);
			}
			for (Map<String, Object> document : query(params, "Referenzen " + fieldName)) {
				if (seen.add(textOf(document, SolrDocumentService.ID_FIELD_NAME))) {
					documents.add(document);
				}
			}
		}
		return documents;
	}

	/** Die Paare Nummer und Sternzeit eines Flags aus einem Dokument, index-parallel; leer ohne Dokument */
	private static List<ReferencePair> pairsOf(Map<String, Object> document, ArtikelReferenceFlag flag) {
		if (document == null) {
			return List.of();
		}
		List<String> numbers = valuesOf(document.get(flag.articleNumberFieldName()));
		List<String> sternzeiten = valuesOf(document.get(flag.sternzeitFieldName()));
		List<ReferencePair> pairs = new ArrayList<>(numbers.size());
		for (int index = 0; index < numbers.size(); index++) {
			String nummer = numbers.get(index).trim();
			if (!nummer.isEmpty()) {
				pairs.add(new ReferencePair(nummer, index < sternzeiten.size() ? sternzeiten.get(index).trim() : ""));
			}
		}
		return pairs;
	}

	/** Das erste Mitglied, das dieses Dokument im Feld des Flags führt: der kürzeste Weg, denn die Liste ist nach Entfernung sortiert */
	private static GroupMember firstListedOf(Map<String, Object> document, ArtikelReferenceFlag flag,
			List<GroupMember> candidates) {
		Set<String> listed = new HashSet<>(valuesOf(document.get(flag.articleNumberFieldName())));
		for (GroupMember candidate : candidates) {
			if (listed.contains(candidate.artikelNummer())) {
				return candidate;
			}
		}
		// Solr hat das Dokument über genau diese Werte gefunden, der Rückfall ist nur Form
		return candidates.get(0);
	}

	/** Ein Treffer je Nummer, bei mehreren Wegen bleibt der kürzere */
	private static void putIfShorter(Map<String, ArtikelAggregatedReference> referenceByNummer,
			ArtikelAggregatedReference reference) {
		ArtikelAggregatedReference existing = referenceByNummer.get(reference.artikelNummer());
		if (existing == null || reference.distance() < existing.distance()) {
			referenceByNummer.put(reference.artikelNummer(), reference);
		}
	}

	/** Bis zu welcher Entfernung Treffer zur Tiefe gehören: bei ALTERNATIVE die Schritte selbst, sonst eine Beziehung mehr */
	private static int maxDistanceOf(ArtikelReferenceFlag flag, int alternativeDepth) {
		return flag == ArtikelReferenceFlag.ALTERNATIVE ? alternativeDepth : alternativeDepth + 1;
	}

	/** Wer das Feld name nicht lesen darf, bekommt die Treffer ohne Namen; die Ablage trägt ihn trotzdem */
	private List<ArtikelAggregatedReference> blankUnreadableNames(List<ArtikelAggregatedReference> references) {
		if (utils.isEveryFieldReadable() || utils.readableFieldNamesOfCurrentUser().contains(NAME_FIELD_NAME)) {
			return references;
		}
		List<ArtikelAggregatedReference> blanked = new ArrayList<>(references.size());
		for (ArtikelAggregatedReference reference : references) {
			blanked.add(new ArtikelAggregatedReference(reference.artikelNummer(), reference.sternzeit(), "",
					reference.distance(), reference.pathArtikelNummern(), reference.foundByReverseLookup()));
		}
		return blanked;
	}

	/** Eine Zeile der Ablage zurück in den Treffer; eine unlesbare Zeile fehlt, mit einer Zeile im Log */
	private ArtikelAggregatedReference parseAggregatedReference(String row) {
		try {
			return objectMapper.readValue(row, ArtikelAggregatedReference.class);
		} catch (JacksonException e) {
			log.warn("Ablagezeile im Kern {} nicht lesbar, wird übersprungen: {}", CORE_KEY, e.getMessage());
			return null;
		}
	}

	/** Die Werte eines Feldes als Texte, egal ob Solr einen Wert oder eine Liste liefert; leer bei nichts */
	private static List<String> valuesOf(Object value) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof Collection<?> values) {
			List<String> texts = new ArrayList<>(values.size());
			for (Object element : values) {
				if (element != null) {
					texts.add(String.valueOf(element));
				}
			}
			return texts;
		}
		return List.of(String.valueOf(value));
	}

	/** Der erste Wert eines Feldes als Text, leer bei nichts */
	private static String textOf(Map<String, Object> document, String fieldName) {
		Object value = firstOf(document.get(fieldName));
		return value == null ? "" : String.valueOf(value).trim();
	}

	/** Der Weg um eine Nummer verlängert, als neue Liste */
	private static List<String> appended(List<String> path, String artikelNummer) {
		List<String> longer = new ArrayList<>(path.size() + 1);
		longer.addAll(path);
		longer.add(artikelNummer);
		return longer;
	}

	// ===== Schreiben =====

	/**
	 * Das Dokument, wie es geschrieben würde: id aus dem PRIMARY-Key, je Feld mit command der Wert seines Keys, dazu die
	 * Sync-Felder. Jedes Feld mit command steht im Dokument, ohne Wert als null (User 2026-10-13): Beim Anlegen fällt
	 * null weg, beim Atomic Update wird es SET null und nimmt das Feld aus dem Dokument, sonst bliebe eine in ABAS
	 * gelöschte Verbindung für immer stehen. 400, wenn das Mapping nicht auf SOLR_ARTIKEL zielt, keinen PRIMARY hat oder
	 * dessen Wert leer ist. Die Mechanik liegt seit 2026-10-13 für alle Spiegelkerne im SolrMirrorCoreDocumentWriteService.
	 */
	public Map<String, Object> buildArtikelDocumentFromMappedValues(ResponseMappingExecutionResult result) {
		return solrMirrorCoreDocumentWriteService.buildMirrorCoreDocumentFromMappedValues(CORE_KEY, TARGET_KEY, result,
				SYNC_DATE_FIELD_NAME, SYNC_STATUS_FIELD_NAME, SYNC_ERROR_FIELD_NAME);
	}

	/**
	 * Schreibt das Dokument in den Kern ARTIKEL: einen neuen Artikel voll, einen vorhandenen per Atomic Update mit SET je
	 * Feld (null nimmt das Feld raus). Felder ohne command, die Ablage der aufaggregierten Suche, bleiben beim Update
	 * unberührt. Vorgang ARTIKEL_WRITE_FROM_MAPPING.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public void writeArtikelDocument(Map<String, Object> document) {
		solrMirrorCoreDocumentWriteService.writeMirrorCoreDocument(CORE_KEY, document, "ARTIKEL_WRITE_FROM_MAPPING");
	}

	/** Der Modifier „set“ eines Atomic Updates: ein Wert, eine Liste oder null, das das Feld aus dem Dokument nimmt */
	private static Map<String, Object> setModifier(Object value) {
		Map<String, Object> modifier = new LinkedHashMap<>();
		modifier.put(SolrDocumentFieldChangeOperation.SET.getSolrModifier(), value);
		return modifier;
	}

}
