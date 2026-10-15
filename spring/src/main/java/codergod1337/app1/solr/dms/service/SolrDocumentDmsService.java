package codergod1337.app1.solr.dms.service;

import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.dms.model.DmsBinaryStartDownload;
import codergod1337.app1.solr.dms.model.DmsExistence;
import codergod1337.app1.solr.dms.model.DmsReindexOption;
import codergod1337.app1.solr.dms.model.DmsResolvedDocumentsPage;
import codergod1337.app1.solr.dms.model.DmsSearchHit;
import codergod1337.app1.solr.dms.model.DmsSearchHitReason;
import codergod1337.app1.solr.dms.model.DmsSearchedDocumentsPage;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.reindex.model.SolrReindexCheck;
import codergod1337.app1.solr.reindex.model.SolrReindexProgress;
import codergod1337.app1.solr.reindex.service.SolrReindexIterator;
import codergod1337.app1.solr.share.model.ShareToken;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lesen und Schreiben von Dokumenten des Kerns DMS, die Blaupause für jeden weiteren Kern. Nur Methoden, die direkt
 * ein Dokument lesen, anlegen oder ändern; alles andere (Rechte-Filter, Suchwörter, Dateiart, Revision, Nachziehen)
 * liegt daneben im SolrDocumentDmsUtilsService.
 */
@Service
public class SolrDocumentDmsService {

	public static final String DMS_CORE_KEY = "DMS";

	/** Die Felder jeder Trefferbox der Suche: Kopf, Dateiart, Status, Größe, Datum, Sprache und die Vorschau */
	// fileExtension: Daran entscheidet die Trefferzeile, ob es einen Viewer gibt und das Auge erscheint (User 2026-10-14)
	private static final List<String> DISPLAY_FIELD_NAMES = List.of("id", "fileName", "fileExtension", "fileIndex",
			"fileSubClassKey", "fileDescription", "status", "version", "fileSizeBytes", "documentCreatedAt",
			"documentLanguage", "bildVorschauBase64Png");

	private final SolrClientRegistry solrClientRegistry;
	private final CurrentUsersProvider currentUsersProvider;
	private final SolrFieldService solrFieldService;
	private final SolrDocumentDmsUtilsService utils;
	private final SolrReindexIterator solrReindexIterator;

	/** Treffer je Seite beim Auflösen, app1.solr.resolve.page-size */
	private final int resolvePageSize;

	/** Treffer insgesamt beim Auflösen, app1.solr.resolve.max-results; eine Seite darüber ist leer */
	private final int resolveMaxResults;

	/** Treffer der Suche, app1.solr.search.hits: nur die besten, keine Seiten */
	private final int searchHits;

	public SolrDocumentDmsService(SolrClientRegistry solrClientRegistry, CurrentUsersProvider currentUsersProvider,
			SolrFieldService solrFieldService, SolrDocumentDmsUtilsService utils, SolrReindexIterator solrReindexIterator,
			@Value("${app1.solr.resolve.page-size}") int resolvePageSize,
			@Value("${app1.solr.resolve.max-results}") int resolveMaxResults,
			@Value("${app1.solr.search.hits}") int searchHits) {
		this.solrClientRegistry = solrClientRegistry;
		this.currentUsersProvider = currentUsersProvider;
		this.solrFieldService = solrFieldService;
		this.utils = utils;
		this.solrReindexIterator = solrReindexIterator;
		this.resolvePageSize = resolvePageSize;
		this.resolveMaxResults = resolveMaxResults;
		this.searchHits = searchHits;
	}

	private SolrClient client() {
		// die HTTP-Verbindung zum Kern DMS, also zu http://<solr>/solr/DMS. SolrClient ist die SolrJ-Klasse, über die
		// jede Anfrage läuft (query, getById, add, commit). Die Registry baut je Kern genau einen und gibt immer denselben.
		return solrClientRegistry.clientFor(DMS_CORE_KEY);
	}

	private static ResponseStatusException unavailable(Exception e) {
		// SolrServerException oder IOException: Solr nicht erreichbar oder Verbindung abgebrochen. Wird HTTP 503.
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Solr nicht erreichbar (Kern " + DMS_CORE_KEY + "): " + e.getMessage(), e);
	}

	// ===== Lesen =====

	public boolean exists(String id) {
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("fl", "id");
		try {
			return client().getById(id, params) != null;
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	public DmsExistence getExistence(String id) {
		// Die Kollisionserkennung des Uploads ruft das, ohne Rechteprüfung: Dateiart und Status des Dokuments zu diesen
		// Bytes, damit der Upload weiß, ob er mergen darf. /get mit fl, kein Filter.
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("fl", "id,fileSubClassKey,status");
		try {
			SolrDocument document = client().getById(id, params);
			if (document == null) {
				return new DmsExistence(false, null, null, null);
			}
			Object fileSubClassKey = document.getFieldValue("fileSubClassKey");
			Object status = document.getFieldValue("status");
			return new DmsExistence(true, id, fileSubClassKey == null ? null : fileSubClassKey.toString(),
					status == null ? null : status.toString());
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	public Map<String, Object> getById(String id) {
		// Ein Dokument über seine id, mit denselben Rechten wie das Auflösen (fq und fl). Echtzeit-Lesen (/get) statt
		// Suche: sieht auch ein gerade geschriebenes Dokument, das Solr noch nicht aufgefrischt hat (User 2026-10-14,
		// Auffrischen alle 10 s), z. B. nach Upload und Bearbeiten. null, wenn es keins gibt oder der User es nicht sieht.
		ModifiableSolrParams params = new ModifiableSolrParams();
		applyUserReadRules(params, Map.of());
		try {
			SolrDocument document = client().getById(id, params);
			return document == null ? null : new LinkedHashMap<>(document);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	public List<Map<String, Object>> getByVersionGuid(String versionGuid) {
		// Schnellweg zum Auflösen einer Versionskette: dieselbe Methode mit der einen Bedingung versionChain, immer alle
		// Fassungen, jüngste zuerst; eine ist ACTIVE, die anderen ARCHIVED, was der User nicht sehen darf, fehlt
		List<Map<String, Object>> documents = new ArrayList<>();
		for (int page = 0;; page++) {
			DmsResolvedDocumentsPage resolvedPage = resolveDmsDocuments(Map.of("versionChain", versionGuid), page);
			documents.addAll(resolvedPage.documents());
			// die letzte Seite ist kleiner als die Seitengröße, notfalls leer
			if (resolvedPage.documents().size() < resolvePageSize) {
				return documents;
			}
		}
	}

	public DmsResolvedDocumentsPage resolveDmsDocuments(Map<String, String> exactValueByFieldName, int page) {
		// Wird aufgerufen, wenn ein User nach konkreten Feldwerten auflösen will, aus fast jedem Modul des Backends.
		// Auflösen, nicht suchen: exakte Werte, keine Suchtreffer mit Score. Seiten ab 0, Größe aus der YAML.
		// die Bedingungen des Clients als Listen mit je einem Wert, die Hilfsmethode kennt nur Listen (Suche: ODER je Feld)
		Map<String, List<String>> valuesByFieldName = new LinkedHashMap<>();
		for (Map.Entry<String, String> condition : exactValueByFieldName.entrySet()) {
			valuesByFieldName.put(condition.getKey(), List.of(condition.getValue()));
		}
		return resolvePage(valuesByFieldName, List.of(), page);
	}

	public DmsResolvedDocumentsPage getGrantedToCurrentUser(int page) {
		// Der Tab „Meine Freigaben“ ruft das (User 2026-10-10): alle Dokumente, die die guid des Users in userGrantedAccess
		// tragen, mit seiner Leseprüfung und Feldgrenze wie beim Auflösen. Die Bedingung setzt das Backend, der Client
		// darf das Feld selbst nicht nennen.
		String guid = currentUsersProvider.getCurrentUsersGuid().toString();
		return resolvePage(Map.of(), List.of("userGrantedAccess:" + ClientUtils.escapeQueryChars(guid)), page);
	}

	private DmsResolvedDocumentsPage resolvePage(Map<String, List<String>> valuesByFieldName,
			List<String> backendFilterQueries, int page) {
		// eine Seite der Auflösung: die Bedingungen des Clients nach seinen Rechten, dazu fq, die das Backend selbst setzt
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist kleiner als 0");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		// *:* trifft alles, die Einschränkung machen allein die fq
		params.set("q", "*:*");
		// feste Reihenfolge, sonst sind die Seiten nicht stabil: jüngstes Dokument zuerst, pdate auf die Millisekunde
		params.set("sort", "documentCreatedAt desc");
		// start und rows: die Seite; über max-results hinaus keine Anfrage, die Seite ist dann leer
		int start = page * resolvePageSize;
		int rows = Math.min(resolvePageSize, resolveMaxResults - start);
		if (rows <= 0) {
			return new DmsResolvedDocumentsPage(0, page, resolvePageSize, List.of());
		}
		params.set("start", start);
		params.set("rows", rows);
		for (String filterQuery : backendFilterQueries) {
			params.add("fq", filterQuery);
		}
		applyUserReadRules(params, valuesByFieldName);

		try {
			// POST an …/solr/DMS/select mit allen Params
			QueryResponse response = client().query(params);
			List<Map<String, Object>> documents = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				// SolrDocument ist selbst eine Map Feldname -> Wert: multiValued als Liste, pdate als Date, plong als Long
				documents.add(new LinkedHashMap<>(document));
			}
			// getNumFound: Treffer insgesamt, auch über rows und max-results hinaus
			return new DmsResolvedDocumentsPage(response.getResults().getNumFound(), page, resolvePageSize, documents);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	private void applyUserReadRules(ModifiableSolrParams params, Map<String, List<String>> valuesByFieldName) {
		// Auflösen und getById rufen das: die Rechte des Users als fq und fl, dazu die Bedingungen des Clients
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (accessRoleKeys.contains("ADMIN")) {
			// ADMIN bekommt alles: kein fq, kein fl, keine Debuffs
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, null, List.of());
		} else if (accessRoleKeys.contains("SOLR_USERS_VIEW_ALL")) {
			// Mitarbeiter mit Vollsicht, alle Felder und Hooks: Dokumentfilter ja, keine fl, keine Debuffs
			utils.buildUserReadAclForSolrRequest(params);
			utils.buildUserSolrResponseStateVisibility(params);
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, null, List.of());
		} else {
			// alle anderen, Kunden, Lieferanten: fl, 403 für fremde Bedingungen, Debuffs über den Bedingungen, Dokumentfilter
			List<String> readableFieldNames = utils.buildUserSolrResponseFieldList(params);
			List<String> debuffedFieldNames = utils.buildUserDebuffsForSolrRequest(params);
			utils.buildUserReadAclForSolrRequest(params);
			utils.buildUserSolrResponseStateVisibility(params);
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, readableFieldNames, debuffedFieldNames);
		}
	}

	public DmsSearchedDocumentsPage searchDmsDocuments(String searchText,
			Map<String, List<String>> exactFilterValuesByFieldName, int page,
			boolean allWordsRequired) {
		// Wird aufgerufen, wenn ein User im Suchmodul tippt oder Chips setzt. Suchen, nicht auflösen: die Treffer mit
		// Score, Gründen und Vorschau für die Trefferbox, eine Seite ab 0 mit app1.solr.search.hits Treffern; „weitere
		// laden“ in der Maske fragt die nächste Seite (User 2026-10-14).
		String text = searchText == null ? "" : searchText.trim();
		if (text.isEmpty() && exactFilterValuesByFieldName.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weder Suchtext noch Filter");
		}
		long start = (long) page * searchHits;
		if (page < 0 || start > Integer.MAX_VALUE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page muss zwischen 0 und " + Integer.MAX_VALUE / searchHits + " liegen");
		}
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		boolean admin = accessRoleKeys.contains("ADMIN");
		// Suchrecht: DMS_SUCHE ist das Recht zu suchen und gibt alle Felder und Hooks frei, ADMIN darf immer
		if (!admin && !accessRoleKeys.contains("DMS_SUCHE")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Suche ohne DMS_SUCHE");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		// die durchsuchten Felder des Kerns, Gewicht ab 0, in Listenreihenfolge: daraus qf, fl und die Gründe
		List<SolrField> searchedFields = new ArrayList<>();
		List<String> qf = new ArrayList<>();
		List<String> highlightedFieldNames = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DMS_CORE_KEY)) {
			if (solrField.getSearchWeight() < 0) {
				continue;
			}
			searchedFields.add(solrField);
			// suggest und Hooks über den Zwilling <name>Suggested, der Wortanfänge kennt; Texttypen und string direkt
			String queriedName = solrField.isSuggest() ? solrField.getName() + "Suggested" : solrField.getName();
			// ergibt z. B. fileName^4 oder artikelNummerSuggested^2; ^0 trifft, zählt aber nicht
			qf.add(queriedName + "^" + solrField.getSearchWeight());
			// Solr markiert Treffer nur in Texttypen, der Zwilling ist nicht stored
			if (solrField.getType().isText()) {
				highlightedFieldNames.add(solrField.getName());
			}
		}
		if (text.isEmpty()) {
			// nur Chips: alles trifft, die fq schränken ein
			params.set("q", "*:*");
		} else {
			if (qf.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Feld im Kern DMS hat ein Suchgewicht");
			}
			// edismax: jedes Wort gegen jedes Feld der qf mit dessen Gewicht; + und - vor einem Wort versteht es selbst
			params.set("defType", "edismax");
			params.set("q", text);
			params.set("qf", String.join(" ", qf));
			// alle Wörter (Schalter über dem Suchfeld, User 2026-10-14): jedes Wort muss in irgendeinem Feld stehen, ein - schließt
			// weiter aus; sonst reicht eines, mehr Treffer heben den Treffer nach oben
			params.set("q.op", allWordsRequired ? "AND" : "OR");
			params.set("mm", allWordsRequired ? "100%" : "1");
			// sow (split on whitespace): erst an Leerzeichen zerlegen, dann jedes Wort einzeln durch jeden Feld-Analyzer.
			// Ohne das bekäme der Zwilling (KeywordTokenizer) "1150 777" als ein Wort und träfe nichts (User 2026-10-09).
			params.set("sow", "true");
			if (!highlightedFieldNames.isEmpty()) {
				// Gründe aus Solr: der getroffene Teil zwischen STX und ETX, das Frontend macht daraus die Hervorhebung
				params.set("hl", true);
				params.set("hl.fl", String.join(",", highlightedFieldNames));
				params.set("hl.method", "unified");
				// jedes Suchwort in jedem Feld markieren, in dem es steht (User 2026-10-14); sonst markiert Solr ein
				// Feld nur, wenn alle +Wörter in genau diesem Feld stehen
				params.set("hl.weightMatches", false);
				params.set("hl.tag.pre", "\u0002");
				params.set("hl.tag.post", "\u0003");
			}
		}
		// das Treffergewicht entscheidet, das Erstelldatum nur bei gleichem Score
		params.set("sort", "score desc, documentCreatedAt desc");
		// die Seite: spätere Seiten kosten Solr kaum mehr als die erste (gemessen 2026-10-14)
		params.set("start", (int) start);
		params.set("rows", searchHits);
		// fl: die Felder der Trefferbox, writeAcl für writable (geht nicht zum Client), die durchsuchten für die Gründe.
		// Der Volltext (text_suche) bleibt draußen: groß, und sein Grund braucht ihn nicht (User 2026-10-09)
		Set<String> fieldList = new LinkedHashSet<>(DISPLAY_FIELD_NAMES);
		fieldList.add("writeAcl");
		for (SolrField solrField : searchedFields) {
			if (solrField.getType() != SolrFieldType.TEXT_SUCHE) {
				fieldList.add(solrField.getName());
			}
		}
		params.set("fl", String.join(",", fieldList));
		if (!admin) {
			// DMS_SUCHE: alle Felder und Hooks, nur readAcl und Status gelten weiter, keine Debuffs
			utils.buildUserReadAclForSolrRequest(params);
			utils.buildUserSolrResponseStateVisibility(params);
		}
		// die Chips des Clients: jedes Feld erlaubt, kein Debuff
		utils.buildClientConditionsForSolrRequest(params, exactFilterValuesByFieldName, null, List.of());

		try {
			QueryResponse response = client().query(params);
			// Highlighting: id -> Feldname -> Schnipsel; null ohne hl
			Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();
			List<String> searchWords = utils.searchWordsOf(text);
			List<DmsSearchHit> hits = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				// SolrDocument ist selbst eine Map Feldname -> Wert, multiValued als Liste
				Map<String, Object> fieldValues = new LinkedHashMap<>(document);
				// writeAcl nur für writable, es geht nicht zum Client; leer heißt nicht schreibbar
				boolean writable = utils.isWritableByCurrentUser(fieldValues);
				fieldValues.remove("writeAcl");
				Map<String, List<String>> snippetsByFieldName = highlighting == null ? null
						: highlighting.get(String.valueOf(fieldValues.get("id")));
				List<DmsSearchHitReason> reasons = new ArrayList<>();
				for (SolrField solrField : searchedFields) {
					String fieldName = solrField.getName();
					if (solrField.getType().isText()) {
						// Texttyp: Solr sagt per Highlighting, ob das Feld getroffen hat. Beim Volltext nur der Feldname, kein
						// Ausschnitt, der Text geht nie zum Client (User 2026-10-09); sonst der Schnipsel von Solr
						List<String> snippets = snippetsByFieldName == null ? null : snippetsByFieldName.get(fieldName);
						if (snippets != null && !snippets.isEmpty()) {
							String snippet = solrField.getType() == SolrFieldType.TEXT_SUCHE ? null : snippets.get(0);
							reasons.add(new DmsSearchHitReason(fieldName, snippet));
						}
						continue;
					}
					// string, suggest, Hooks: Solr markiert dort nichts, der Vergleich läuft über die gelieferten Werte
					String snippet = utils.startingWithSearchWord(fieldValues.get(fieldName), searchWords);
					if (snippet != null) {
						reasons.add(new DmsSearchHitReason(fieldName, snippet));
					}
				}
				hits.add(new DmsSearchHit(fieldValues, writable, reasons));
			}
			return new DmsSearchedDocumentsPage(response.getResults().getNumFound(), page, searchHits, hits);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	// ===== Lesen für den Download: drei Zugriffsregeln, dahinter dieselbe Abfrage =====

	private DmsBinaryStartDownload queryBinaryStartDownload(ModifiableSolrParams params) {
		// Die drei Download-Methoden rufen das mit ihren fq: holt fest die Felder, die der Download braucht, egal welche
		// Feld-ARs der User hat, denn nichts davon geht zum Client, nur in den Stream. null, wenn die fq nichts treffen.
		params.set("q", "*:*");
		params.set("rows", 1);
		params.set("fl", "id,fileName,mimeType,fileSizeBytes,sftpPath");
		try {
			QueryResponse response = client().query(params);
			if (response.getResults().isEmpty()) {
				return null;
			}
			SolrDocument document = response.getResults().get(0);
			String id = String.valueOf(document.getFieldValue("id"));
			Object sftpPath = document.getFieldValue("sftpPath");
			// ohne Ablageort gibt es nichts zu streamen, das ist ein kaputtes Dokument, kein 404
			if (sftpPath == null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "zur Datei " + id + " fehlt der Ablageort");
			}
			Object fileName = document.getFieldValue("fileName");
			Object mimeType = document.getFieldValue("mimeType");
			Object fileSizeBytes = document.getFieldValue("fileSizeBytes");
			return new DmsBinaryStartDownload(id, fileName == null ? id : fileName.toString(),
					mimeType == null ? "application/octet-stream" : mimeType.toString(),
					fileSizeBytes instanceof Number number ? number.longValue() : null, sftpPath.toString());
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	public DmsBinaryStartDownload getDownloadById(String id) {
		// Der Download-Controller ruft das für einen angemeldeten User: die Datei zu dieser id, wenn er sie sehen darf, über
		// readAcl ODER seine guid in userGrantedAccess. null, wenn nicht, der Controller macht daraus 404.
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", "id:" + ClientUtils.escapeQueryChars(id));
		if (!currentUsersProvider.getCurrentUsersAccessRoleKeys().contains("ADMIN")) {
			// Rolle oder Freigabe an die Person, beides heißt "darf lesen"; die Klammern, weil die Klausel selbst ein OR hat
			params.add("fq", "(" + utils.buildUserReadAclFilterQuery() + ") OR userGrantedAccess:"
					+ currentUsersProvider.getCurrentUsersGuid());
			utils.buildUserSolrResponseStateVisibility(params);
		}
		return queryBinaryStartDownload(params);
	}

	public DmsBinaryStartDownload getPublicDownloadById(String id) {
		// Der öffentliche Download-Controller ruft das ohne angemeldeten User: nur ein Dokument mit isPublic true und Status
		// ACTIVE. Kein Griff zum CurrentUsersProvider, es gibt keinen User.
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", "id:" + ClientUtils.escapeQueryChars(id));
		params.add("fq", "isPublic:true");
		params.add("fq", "status:ACTIVE");
		return queryBinaryStartDownload(params);
	}

	public DmsBinaryStartDownload getDownloadByToken(ShareToken shareToken) {
		// Der öffentliche Download-Controller ruft das mit einem einlösbaren Schein, ohne angemeldeten User: das Dokument
		// des Scheins, das seinen Hash in downloadGrantedAccess trägt (Gegenprobe, ein Widerruf nimmt ihn weg). null sonst.
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.add("fq", "id:" + ClientUtils.escapeQueryChars(shareToken.getDocumentId()));
		params.add("fq", "downloadGrantedAccess:" + ClientUtils.escapeQueryChars(shareToken.getTokenHash()));
		utils.buildUserSolrResponseStateVisibility(params);
		return queryBinaryStartDownload(params);
	}

	// ===== Lesen für das Schreiben =====

	private Map<String, Object> getForWrite(String id) {
		// Jede Änderung an einem bestehenden Dokument lädt es damit genau einmal, mit allen Feldern: Leserecht über den
		// readAcl-Filter in der Anfrage, Schreibrecht aus writeAcl der Antwort, dieselbe Map geht danach in die Revision.
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "*:*");
		params.set("rows", 1);
		params.set("fl", "*");
		params.add("fq", "id:" + ClientUtils.escapeQueryChars(id));
		if (!currentUsersProvider.getCurrentUsersAccessRoleKeys().contains("ADMIN")) {
			utils.buildUserReadAclForSolrRequest(params);
			utils.buildUserSolrResponseStateVisibility(params);
		}
		Map<String, Object> document;
		try {
			QueryResponse response = client().query(params);
			if (response.getResults().isEmpty()) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
			}
			document = new LinkedHashMap<>(response.getResults().get(0));
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
		if (!utils.isWritableByCurrentUser(document)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Schreibrecht an " + id);
		}
		return document;
	}

	// ===== Schreiben =====

	private static SolrInputDocument atomicUpdate(String id) {
		// Jede Schreibmethode baut damit ihr Update: ein Dokument nur mit der id, danach je Feld Map.of(operation, wert),
		// z. B. Map.of("inc", 1). Solr ändert dann nur diese Felder und lässt den Rest des Dokuments stehen.
		SolrInputDocument update = new SolrInputDocument();
		update.addField("id", id);
		return update;
	}

	private void send(SolrInputDocument update) {
		// Jede Schreibmethode schickt damit ihr Update: add an …/solr/DMS/update, kein Commit (User 2026-10-14). Suchbar
		// wird die Änderung mit dem autoSoftCommit alle 10 s; getById liest sie per Echtzeit-Lesen sofort.
		try {
			client().add(update);
		} catch (SolrException e) {
			// Solr lehnt ab, z. B. unbekanntes Feld oder falscher Typ: dessen 400 bleibt 400, alles andere ist 503
			throw new ResponseStatusException(e.code() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE,
					"Solr lehnt das Schreiben ab (Kern " + DMS_CORE_KEY + "): " + e.getMessage(), e);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	public void countDownload(String id) {
		// Der Download-Controller ruft das nach jedem vollständig gestreamten Download: downloadCount um eins hoch. Keine
		// Rechteprüfung, die war beim Holen der Datei; kein Stempel, keine Revision, ein Download ist Buchhaltung.
		SolrInputDocument update = atomicUpdate(id);
		update.addField("downloadCount", Map.of("inc", 1));
		send(update);
	}

	public void createFromUpload(Map<String, Object> fieldValues) {
		// Der Upload-Service ruft das nach seiner Prüfung (exists, Felddefinition, Datei auf dem SFTP): speichert, was
		// kommt, ohne eigene Prüfung. Lehnt Solr ab, kommt dessen 400 durch send.
		SolrInputDocument document = new SolrInputDocument();
		for (Map.Entry<String, Object> fieldValue : fieldValues.entrySet()) {
			// addField nimmt einen Wert oder eine Liste, eine Liste wird mehrwertig
			document.addField(fieldValue.getKey(), fieldValue.getValue());
		}
		send(document);
	}

	public void mergeFieldsBecauseIdenticalBinary(String id, Map<String, Object> fieldValues) {
		// Der Upload-Service ruft das, wenn dieselben Bytes noch einmal kommen (das Frontend hat die Kollision erkannt und
		// den Bestand in seine Liste geladen): Hooks kommen dazu, fileIndex und fileDescription werden gesetzt, nichts
		// wird gelöscht. Alles andere in der Map wird ignoriert. Geprüft hat der Upload-Service davor.
		Map<String, Object> existing = getForWrite(id);
		SolrInputDocument update = atomicUpdate(id);
		List<String> changedFieldNames = new ArrayList<>();
		// über die Hook-Zeilen des Kerns, nicht über die Map: add-distinct, jeder Wert landet nur einmal im Feld
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DMS_CORE_KEY)) {
			if (solrField.isHookField() && fieldValues.containsKey(solrField.getName())) {
				update.addField(solrField.getName(), Map.of("add-distinct", fieldValues.get(solrField.getName())));
				changedFieldNames.add(solrField.getName());
			}
		}
		// die beiden hat der User in der Uploadliste am Bestand bearbeitet: set, nicht anhängen (User 2026-10-09)
		for (String fieldName : List.of("fileIndex", "fileDescription")) {
			if (fieldValues.containsKey(fieldName)) {
				update.addField(fieldName, Map.of("set", fieldValues.get(fieldName)));
				changedFieldNames.add(fieldName);
			}
		}
		// die Spur: wann zuletzt, und wer alles dran war; Date statt Instant, das ist das Datum, das SolrJ überträgt
		update.addField("lastChangedAt", Map.of("set", Date.from(Instant.now())));
		update.addField("changingUsers", Map.of("add-distinct", currentUsersProvider.getCurrentUsersGuid().toString()));
		utils.saveRevisionBeforeWrite(existing, changedFieldNames);
		send(update);
	}

	public void createNextVersionOfChain(String previousId, Map<String, Object> fieldValues) {
		// Der Upload-Service ruft das für neue Bytes zu einer bestehenden Fassung: die neue Fassung wird gespeichert, die
		// alte archiviert. Die Kette kommt zur Sicherheit aus der alten Fassung, nicht aus der Map.
		// die alte Fassung mit Lese- und Schreibrecht des Users, 404 oder 403 kommen aus getForWrite
		Map<String, Object> previous = getForWrite(previousId);
		// eine Kette trägt genau eine Dateiart: die neue Fassung muss dieselbe mitbringen
		Object previousFileSubClassKey = previous.get("fileSubClassKey");
		if (previousFileSubClassKey == null || !previousFileSubClassKey.equals(fieldValues.get("fileSubClassKey"))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileSubClassKey passt nicht zur Fassung " + previousId
					+ " (" + previousFileSubClassKey + ")");
		}
		Object versionChain = previous.get("versionChain");
		if (versionChain == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Fassung " + previousId + " hat keine versionChain");
		}
		// die neue Fassung: die Map, mit der Kette der alten Fassung darüber
		Map<String, Object> next = new LinkedHashMap<>(fieldValues);
		next.put("versionChain", versionChain);
		createFromUpload(next);
		// die alte Fassung: archiviert, mit Stempel und Spur, vorher ihre Revision
		SolrInputDocument archive = atomicUpdate(previousId);
		archive.addField("status", Map.of("set", "ARCHIVED"));
		archive.addField("lastChangedAt", Map.of("set", Date.from(Instant.now())));
		archive.addField("changingUsers", Map.of("add-distinct", currentUsersProvider.getCurrentUsersGuid().toString()));
		utils.saveRevisionBeforeWrite(previous, List.of("status"));
		send(archive);
	}

	public void editDmsDocument(String id, Map<String, Object> fieldValues) {
		// Die Bearbeiten-Maske ruft das über ihren Controller: jedes Feld der Map wird gesetzt, auch Hooks, die Map ist die
		// Endliste, ein leerer Wert leert das Feld. Geprüft hat der Controller davor (Schreibregel, Typen).
		editLoaded(getForWrite(id), fieldValues);
	}

	public Map<String, Object> restoreRevision(String id, long revisionNo) {
		// Die History-Box ruft das: der Stand einer Revision kommt zurück, über denselben Weg wie Bearbeiten, also mit
		// Revision des heutigen Standes davor. Nicht zurück kommen id, _version_, Zähler, Stempel und die ACLs.
		Map<String, Object> existing = getForWrite(id);
		Map<String, Object> revision = utils.readRevision(id, revisionNo);
		Map<String, Object> fieldValues = new LinkedHashMap<>();
		for (Map.Entry<String, Object> field : revision.entrySet()) {
			if (!RESTORE_SKIPPED_FIELD_NAMES.contains(field.getKey())) {
				fieldValues.put(field.getKey(), field.getValue());
			}
		}
		// Felder, die heute stehen und im ZIP fehlen, werden geleert, sonst wäre es kein Zurückladen
		for (String fieldName : existing.keySet()) {
			if (!RESTORE_SKIPPED_FIELD_NAMES.contains(fieldName) && !fieldValues.containsKey(fieldName)) {
				fieldValues.put(fieldName, null);
			}
		}
		editLoaded(existing, fieldValues);
		return getById(id);
	}

	/** Was das Zurückladen nie anfasst: Schlüssel, Solrs Zähler, unsere Zähler und Stempel, die ACLs setzt edit aus der Dateiart */
	private static final Set<String> RESTORE_SKIPPED_FIELD_NAMES = Set.of("id", "_version_", "fileIdRevisionNo",
			"downloadCount", "cursorDate", "lastChangedAt", "changingUsers", "readAcl", "writeAcl");

	private void editLoaded(Map<String, Object> existing, Map<String, Object> fieldValues) {
		// Bearbeiten und Zurückladen rufen das mit dem schon geladenen Dokument: set je Feld, ACLs aus der Dateiart, Spur,
		// Revision, send
		String id = String.valueOf(existing.get("id"));
		SolrInputDocument update = atomicUpdate(id);
		for (Map.Entry<String, Object> fieldValue : fieldValues.entrySet()) {
			// set mit null leert das Feld in Solr; singletonMap statt Map.of, weil Map.of kein null zulässt
			update.addField(fieldValue.getKey(), Collections.singletonMap("set", fieldValue.getValue()));
		}
		// readAcl und writeAcl bei jedem Edit neu aus der Dateiart, die sich täglich ändern kann (User 2026-10-10): die
		// Dateiart aus der Map, sonst die am Dokument
		utils.addAccessControlListsFromFileSubClass(update,
				fieldValues.containsKey("fileSubClassKey") ? fieldValues.get("fileSubClassKey") : existing.get("fileSubClassKey"));
		// die Spur: wann zuletzt, wer alles dran war, und die Nummer der Revision zählt hoch
		update.addField("lastChangedAt", Map.of("set", Date.from(Instant.now())));
		update.addField("changingUsers", Map.of("add-distinct", currentUsersProvider.getCurrentUsersGuid().toString()));
		update.addField("fileIdRevisionNo", Map.of("inc", 1));
		utils.saveRevisionBeforeWrite(existing, fieldValues.keySet());
		send(update);
	}

	public void softDeleteDmsDocument(String id) {
		// Der Mülleimer ruft das: status DELETED, die Datei bleibt auf dem SFTP. Danach sieht es nur noch ADMIN, denn nur
		// er hat keinen Status-Filter; für ihn gehen editDmsDocument und softDeleteDmsDocument auch an einem DELETED.
		Map<String, Object> existing = getForWrite(id);
		SolrInputDocument update = atomicUpdate(id);
		update.addField("status", Map.of("set", "DELETED"));
		update.addField("lastChangedAt", Map.of("set", Date.from(Instant.now())));
		update.addField("changingUsers", Map.of("add-distinct", currentUsersProvider.getCurrentUsersGuid().toString()));
		utils.saveRevisionBeforeWrite(existing, List.of("status"));
		send(update);
	}

	public void reindexDmsDocuments(Set<DmsReindexOption> options, SolrReindexProgress progress) {
		// Der SolrManager startet das über den SolrReindexService im Hintergrund, um alle Dokumente auf den Sollstand zu
		// bringen (User 2026-10-14), z. B. die ACLs nach einer geänderten Dateiart. Je Option eine Prüfung, die ihren
		// Sollstand einmal beim Bauen liest; die Schleife liest blockweise nur die Felder der Prüfungen und schreibt jedes
		// geänderte Dokument einzeln über send. Keine Revision, das ist nur Nachziehen.
		if (!currentUsersProvider.getCurrentUsersAccessRoleKeys().contains("ADMIN")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nachziehen nur als ADMIN");
		}
		List<SolrReindexCheck> checks = new ArrayList<>();
		if (options.contains(DmsReindexOption.ACLS_FROM_FILE_SUB_CLASS)) {
			checks.add(utils.aclsFromFileSubClassCheck());
		}
		if (options.contains(DmsReindexOption.SEARCHABLE_WORDS_FROM_BINARY)) {
			checks.add(utils.searchableWordsFromBinaryCheck());
		}
		solrReindexIterator.iterate(DMS_CORE_KEY, checks, this::send, progress);
	}

}
