package codergod1337.app1.solr.nachrichten.service;

import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.nachrichten.model.CounterNachrichtenId;
import codergod1337.app1.solr.nachrichten.model.NachrichtenReindexOption;
import codergod1337.app1.solr.nachrichten.model.NachrichtenResolvedDocumentsPage;
import codergod1337.app1.solr.nachrichten.model.NachrichtenSearchHit;
import codergod1337.app1.solr.nachrichten.model.NachrichtenSearchHitReason;
import codergod1337.app1.solr.nachrichten.model.NachrichtenSearchedDocumentsPage;
import codergod1337.app1.solr.nachrichten.repository.CounterNachrichtenIdRepository;
import codergod1337.app1.solr.reindex.model.SolrReindexCheck;
import codergod1337.app1.solr.reindex.model.SolrReindexProgress;
import codergod1337.app1.solr.reindex.service.SolrReindexIterator;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lesen und Schreiben im Kern NACHRICHTEN (User 2026-10-14), dem Archiv der Mails.
 *
 * Schreiben wie createFromUpload im DMS: Wer archiviert, übergibt die Feldwerte unter den Namen aus solr_field, dieser
 * Service prüft jeden Namen gegen die Felder des Kerns und schreibt das neue Dokument voll. Keine Rechteprüfung dort: Die
 * Rolle des Archivierens und den Postfachzugriff prüft der Archivier-Service, bevor er schreibt.
 *
 * Lesen: Auflösen nach exakten Werten, eine Nachricht nach id, der Verlauf einer Nachricht und die Suche. Die Tür ist die
 * AR NACHRICHTEN (oder NACHRICHTEN_SUCHE), welche Nachricht der User sieht, entscheidet readAcl aus ihrer Nachrichtenart,
 * die Feldgrenze die Lese-ARs der Felder. Rollen, Rechte-Filter, fl, Debuffs und Bedingungen liegen im
 * SolrDocumentNachrichtenUtilsService.
 */
@Service
public class SolrDocumentNachrichtenService {

	/** Der Kern, zugleich sein Name in Solr */
	public static final String CORE_KEY = "NACHRICHTEN";

	private static final String INTERNET_MESSAGE_ID_FIELD_NAME = "internetMessageId";

	/** Die neueste Nachricht zuerst; die id nur bei gleichem Datum, damit die Seiten stabil sind */
	private static final String NEWEST_FIRST = "sentDateTime desc, id asc";

	/** Ein Verlauf liest sich von oben nach unten: die älteste Nachricht zuerst */
	private static final String OLDEST_FIRST = "sentDateTime asc, id asc";

	/** Die Felder der Trefferzeile, dazu kommen die durchsuchten */
	private static final List<String> DISPLAY_FIELD_NAMES = List.of("id", "messageSubClassKey", "mailbox",
			"mailboxFolder", "subject", "bodyPreview", "fromName", "fromAddress", "toRecipientNames",
			"toRecipientAddresses", "sentDateTime", "receivedDateTime", "hasAttachments", "conversationId");

	private final SolrDocumentService solrDocumentService;
	private final SolrFieldService solrFieldService;
	private final CounterNachrichtenIdRepository counterNachrichtenIdRepository;
	private final SolrClientRegistry solrClientRegistry;
	private final SolrDocumentNachrichtenUtilsService utils;
	private final CurrentUsersProvider currentUsersProvider;
	private final SolrReindexIterator solrReindexIterator;

	/** Treffer je Seite beim Auflösen, app1.solr.resolve.page-size */
	private final int resolvePageSize;

	/** Treffer insgesamt beim Auflösen, app1.solr.resolve.max-results; eine Seite darüber ist leer */
	private final int resolveMaxResults;

	/** Treffer der Suche, app1.solr.search.hits: nur die besten, keine Seiten */
	private final int searchHits;

	public SolrDocumentNachrichtenService(SolrDocumentService solrDocumentService, SolrFieldService solrFieldService,
			CounterNachrichtenIdRepository counterNachrichtenIdRepository, SolrClientRegistry solrClientRegistry,
			SolrDocumentNachrichtenUtilsService utils, CurrentUsersProvider currentUsersProvider,
			SolrReindexIterator solrReindexIterator,
			@Value("${app1.solr.resolve.page-size}") int resolvePageSize,
			@Value("${app1.solr.resolve.max-results}") int resolveMaxResults,
			@Value("${app1.solr.search.hits}") int searchHits) {
		this.solrDocumentService = solrDocumentService;
		this.solrFieldService = solrFieldService;
		this.counterNachrichtenIdRepository = counterNachrichtenIdRepository;
		this.solrClientRegistry = solrClientRegistry;
		this.utils = utils;
		this.currentUsersProvider = currentUsersProvider;
		this.solrReindexIterator = solrReindexIterator;
		this.resolvePageSize = resolvePageSize;
		this.resolveMaxResults = resolveMaxResults;
		this.searchHits = searchHits;
	}

	// ===== Lesen =====

	/**
	 * Eine Nachricht über ihre laufende Nummer, mit denselben Rechten wie das Auflösen; null, wenn es sie nicht gibt oder
	 * der User sie nicht sehen darf. Echtzeit-Lesen (/get) statt Suche: sieht auch eine gerade archivierte Nachricht, die
	 * Solr noch nicht aufgefrischt hat (User 2026-10-14, Auffrischen alle 10 s).
	 */
	public Map<String, Object> getById(String id) {
		utils.requireAccessRole();
		ModifiableSolrParams params = new ModifiableSolrParams();
		applyUserReadRules(params, Map.of());
		try {
			SolrDocument document = client().getById(id, params);
			return document == null ? null : new LinkedHashMap<>(document);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	/**
	 * Auflösen nach exakten Feldwerten, mehrere Felder UND: keine Suchtreffer mit Score, Seiten ab 0 mit der Größe aus
	 * der YAML, neueste Nachricht zuerst. Eine Bedingung auf ein Feld, das der User nicht lesen darf, ist 403.
	 */
	public NachrichtenResolvedDocumentsPage resolveNachrichtenDocuments(Map<String, String> exactValueByFieldName,
			int page) {
		if (exactValueByFieldName.isEmpty()) {
			// Alles ausgeben ist Sache der Suche, nicht des Auflösens
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auflösen braucht mindestens eine Bedingung");
		}
		Map<String, List<String>> valuesByFieldName = new LinkedHashMap<>();
		for (Map.Entry<String, String> condition : exactValueByFieldName.entrySet()) {
			valuesByFieldName.put(condition.getKey(), List.of(condition.getValue()));
		}
		return resolvePage(valuesByFieldName, List.of(), NEWEST_FIRST, page);
	}

	/**
	 * Der Verlauf einer Nachricht, älteste zuerst, die Nachricht selbst eingeschlossen (User 2026-10-14): alle mit
	 * derselben conversationId von Exchange, dazu über die Kopfzeilen die Mails, auf die sie sich bezieht (References,
	 * In-Reply-To), und die, die sich auf sie beziehen. So finden auch Mails aus einem anderen Postfach dazu. Was der User
	 * nicht sehen darf, fehlt. 404, wenn es die Nachricht nicht gibt oder er sie nicht sehen darf.
	 */
	public List<Map<String, Object>> getConversation(String id) {
		Map<String, Object> message = getById(id);
		if (message == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Nachricht " + id);
		}
		List<String> clauses = new ArrayList<>();
		clauses.add("id:" + ClientUtils.escapeQueryChars(id));
		String conversationId = firstValueOf(message.get("conversationId"));
		if (conversationId != null) {
			clauses.add("conversationId:" + ClientUtils.escapeQueryChars(conversationId));
		}
		Set<String> earlierInternetMessageIds = new LinkedHashSet<>(valuesOf(message.get("references")));
		earlierInternetMessageIds.addAll(valuesOf(message.get("inReplyTo")));
		if (!earlierInternetMessageIds.isEmpty()) {
			clauses.add(INTERNET_MESSAGE_ID_FIELD_NAME + ":(" + escapedAlternatives(earlierInternetMessageIds) + ")");
		}
		String internetMessageId = firstValueOf(message.get(INTERNET_MESSAGE_ID_FIELD_NAME));
		if (internetMessageId != null) {
			String escaped = ClientUtils.escapeQueryChars(internetMessageId);
			clauses.add("references:" + escaped);
			clauses.add("inReplyTo:" + escaped);
		}
		// ergibt z. B. id:17 OR conversationId:AAQk… OR internetMessageId:(\<a\@b\>) OR references:\<c\@d\>
		List<String> backendFilterQueries = List.of(String.join(" OR ", clauses));
		List<Map<String, Object>> messages = new ArrayList<>();
		for (int page = 0;; page++) {
			NachrichtenResolvedDocumentsPage resolvedPage = resolvePage(Map.of(), backendFilterQueries, OLDEST_FIRST, page);
			messages.addAll(resolvedPage.documents());
			// die letzte Seite ist kleiner als die Seitengröße, notfalls leer
			if (resolvedPage.documents().size() < resolvePageSize) {
				break;
			}
		}
		// eine gerade archivierte Nachricht kennt die Suche erst nach dem Auffrischen (bis 10 s): sie selbst gehört immer dazu
		boolean contained = messages.stream().anyMatch(candidate -> id.equals(String.valueOf(candidate.get("id"))));
		if (!contained) {
			messages.add(message);
			// Solr liefert das Datum als Date; ohne Datum ganz vorn
			messages.sort(Comparator.comparingLong(candidate -> candidate.get("sentDateTime") instanceof Date sent ? sent.getTime() : 0L));
		}
		return messages;
	}

	/**
	 * Eine Seite des Auflösens: die Bedingungen des Clients nach seinen Rechten, dazu fq, die das Backend selbst setzt.
	 * Wie im DMS drei Stufen: ADMIN alles; SOLR_USERS_VIEW_ALL alle Felder, aber readAcl; alle anderen fl nach den
	 * Lese-ARs der Felder, Debuffs, readAcl.
	 */
	private NachrichtenResolvedDocumentsPage resolvePage(Map<String, List<String>> valuesByFieldName,
			List<String> backendFilterQueries, String sort, int page) {
		utils.requireAccessRole();
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist kleiner als 0");
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "*:*");
		params.set("sort", sort);
		int start = page * resolvePageSize;
		int rows = Math.min(resolvePageSize, resolveMaxResults - start);
		if (rows <= 0) {
			return new NachrichtenResolvedDocumentsPage(0, page, resolvePageSize, List.of());
		}
		params.set("start", start);
		params.set("rows", rows);
		for (String filterQuery : backendFilterQueries) {
			params.add("fq", filterQuery);
		}
		applyUserReadRules(params, valuesByFieldName);
		try {
			QueryResponse response = client().query(params);
			List<Map<String, Object>> documents = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				documents.add(new LinkedHashMap<>(document));
			}
			return new NachrichtenResolvedDocumentsPage(response.getResults().getNumFound(), page, resolvePageSize,
					documents);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	/** Auflösen und getById rufen das: die drei Stufen ADMIN, SOLR_USERS_VIEW_ALL und alle anderen, dazu die Bedingungen */
	private void applyUserReadRules(ModifiableSolrParams params, Map<String, List<String>> valuesByFieldName) {
		if (utils.isAdmin()) {
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, null, List.of());
		} else if (utils.isEveryFieldReadable()) {
			utils.buildUserReadAclForSolrRequest(params);
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, null, List.of());
		} else {
			List<String> readableFieldNames = utils.buildUserSolrResponseFieldList(params);
			List<String> debuffedFieldNames = utils.buildUserDebuffsForSolrRequest(params);
			utils.buildUserReadAclForSolrRequest(params);
			utils.buildClientConditionsForSolrRequest(params, valuesByFieldName, readableFieldNames, debuffedFieldNames);
		}
	}

	/**
	 * Wird aufgerufen, wenn ein User im Suchmodul tippt oder Chips setzt: die Treffer mit Score und Gründen für die
	 * Trefferbox, eine Seite ab 0 mit app1.solr.search.hits Treffern. Derselbe Ablauf wie die Suche im DMS: Tür
	 * NACHRICHTEN_SUCHE oder ADMIN, alle Felder und Hooks, readAcl gilt weiter, keine Debuffs.
	 *
	 * E-Mail-Modus wie im Kern KONTAKTE: Ist der Suchtext genau eine Adresse, trifft nur diese Adresse in den Feldern vom
	 * Typ EMAIL (Absender, Gesendet von, An, Cc, Bcc), ohne Rücksicht auf Groß- und Kleinschreibung.
	 */
	public NachrichtenSearchedDocumentsPage searchNachrichtenDocuments(String searchText,
			Map<String, List<String>> exactFilterValuesByFieldName, int page,
			boolean allWordsRequired) {
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
		List<String> emailFieldNames = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(CORE_KEY)) {
			if (solrField.getType() == SolrFieldType.EMAIL) {
				emailFieldNames.add(solrField.getName());
			}
			if (solrField.getSearchWeight() < 0) {
				continue;
			}
			searchedFields.add(solrField);
			// suggest und Hooks über den Zwilling <name>Suggested, der Wortanfänge kennt; Texttypen und string direkt
			String queriedName = solrField.isSuggest() ? solrField.getName() + "Suggested" : solrField.getName();
			// ergibt z. B. subject^10 oder fromAddressSuggested^5
			qf.add(queriedName + "^" + solrField.getSearchWeight());
			// Solr markiert Treffer nur in Texttypen, der Zwilling ist nicht stored
			if (solrField.getType().isText()) {
				highlightedFieldNames.add(solrField.getName());
			}
		}
		if (text.isEmpty()) {
			// nur Chips: alles trifft, die fq schränken ein; neueste zuerst
			params.set("q", "*:*");
			params.set("sort", NEWEST_FIRST);
		} else if (isEmailAddress(text) && !emailFieldNames.isEmpty()) {
			// E-Mail-Modus: nur genau diese Adresse, z. B. fromAddress:(/[iI][nN].../) OR toRecipientAddresses:(…)
			String exactAddress = SolrDocumentNachrichtenUtilsService.caseInsensitiveExactRegex(text);
			List<String> clauses = new ArrayList<>();
			for (String emailFieldName : emailFieldNames) {
				clauses.add(emailFieldName + ":(" + exactAddress + ")");
			}
			params.set("q", String.join(" OR ", clauses));
			params.set("sort", NEWEST_FIRST);
		} else {
			if (qf.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Feld im Kern " + CORE_KEY + " hat ein Suchgewicht");
			}
			// edismax: jedes Wort gegen jedes Feld der qf mit dessen Gewicht; + und - vor einem Wort versteht es selbst
			params.set("defType", "edismax");
			params.set("q", text);
			params.set("qf", String.join(" ", qf));
			// alle Wörter (Schalter über dem Suchfeld, User 2026-10-14): jedes Wort muss in irgendeinem Feld stehen, ein - schließt
			// weiter aus; sonst reicht eines, mehr Treffer heben den Treffer nach oben
			params.set("q.op", allWordsRequired ? "AND" : "OR");
			params.set("mm", allWordsRequired ? "100%" : "1");
			// sow: erst an Leerzeichen zerlegen, dann jedes Wort einzeln durch jeden Feld-Analyzer (User 2026-10-09)
			params.set("sow", "true");
			if (!highlightedFieldNames.isEmpty()) {
				params.set("hl", true);
				params.set("hl.fl", String.join(",", highlightedFieldNames));
				params.set("hl.method", "unified");
				// jedes Suchwort in jedem Feld markieren, in dem es steht (User 2026-10-14); sonst markiert Solr ein
				// Feld nur, wenn alle +Wörter in genau diesem Feld stehen
				params.set("hl.weightMatches", false);
				params.set("hl.tag.pre", "\u0002");
				params.set("hl.tag.post", "\u0003");
			}
			// das Treffergewicht entscheidet, das Datum nur bei gleichem Score
			params.set("sort", "score desc, " + NEWEST_FIRST);
		}
		// die Seite: „weitere laden“ in der Maske (User 2026-10-14)
		params.set("start", (int) start);
		params.set("rows", searchHits);
		// fl: die Felder der Trefferzeile, writeAcl für writable (geht nicht zum Client), die durchsuchten für die Gründe;
		// der Volltext bleibt draußen
		Set<String> fieldList = new LinkedHashSet<>(DISPLAY_FIELD_NAMES);
		fieldList.add("writeAcl");
		for (SolrField solrField : searchedFields) {
			if (solrField.getType() != SolrFieldType.TEXT_SUCHE) {
				fieldList.add(solrField.getName());
			}
		}
		params.set("fl", String.join(",", fieldList));
		if (!utils.isAdmin()) {
			// NACHRICHTEN_SUCHE: alle Felder und Hooks, nur readAcl gilt weiter, keine Debuffs
			utils.buildUserReadAclForSolrRequest(params);
		}
		// die Chips des Clients: jedes Feld erlaubt, kein Debuff
		utils.buildClientConditionsForSolrRequest(params, exactFilterValuesByFieldName, null, List.of());
		try {
			QueryResponse response = client().query(params);
			Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();
			List<String> searchWords = utils.searchWordsOf(text);
			List<NachrichtenSearchHit> hits = new ArrayList<>();
			for (SolrDocument document : response.getResults()) {
				Map<String, Object> fieldValues = new LinkedHashMap<>(document);
				boolean writable = isWritableByCurrentUser(fieldValues);
				fieldValues.remove("writeAcl");
				Map<String, List<String>> snippetsByFieldName = highlighting == null ? null
						: highlighting.get(String.valueOf(fieldValues.get("id")));
				List<NachrichtenSearchHitReason> reasons = new ArrayList<>();
				for (SolrField solrField : searchedFields) {
					String fieldName = solrField.getName();
					if (solrField.getType().isText()) {
						List<String> snippets = snippetsByFieldName == null ? null : snippetsByFieldName.get(fieldName);
						if (snippets != null && !snippets.isEmpty()) {
							String snippet = solrField.getType() == SolrFieldType.TEXT_SUCHE ? null : snippets.get(0);
							reasons.add(new NachrichtenSearchHitReason(fieldName, snippet));
						}
						continue;
					}
					// string, suggest, Hooks: Solr markiert dort nichts, der Vergleich läuft über die gelieferten Werte
					String snippet = utils.startingWithSearchWord(fieldValues.get(fieldName), searchWords);
					if (snippet != null) {
						reasons.add(new NachrichtenSearchHitReason(fieldName, snippet));
					}
				}
				hits.add(new NachrichtenSearchHit(fieldValues, writable, reasons));
			}
			return new NachrichtenSearchedDocumentsPage(response.getResults().getNumFound(), page, searchHits, hits);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	/** Schreibrecht an den Hooks einer geladenen Nachricht: ADMIN immer, sonst eine Rolle des Users in writeAcl */
	private boolean isWritableByCurrentUser(Map<String, Object> document) {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		return accessRoleKeys.contains(SolrDocumentNachrichtenUtilsService.ADMIN_ACCESS_ROLE_KEY)
				|| valuesOf(document.get("writeAcl")).stream().anyMatch(accessRoleKeys::contains);
	}

	/** Genau eine Adresse: ein @, nicht am Rand, kein Leerraum; wie im Kern KONTAKTE */
	private static boolean isEmailAddress(String text) {
		int at = text.indexOf('@');
		return at > 0 && at == text.lastIndexOf('@') && at < text.length() - 1
				&& text.chars().noneMatch(Character::isWhitespace);
	}

	// ===== Archiv-Abgleich, ohne Rechteprüfung =====

	/**
	 * Liegt diese Mail schon im Archiv? Der Schutz vor Dubletten: dieselbe Mail nie zweimal. Ohne Rechteprüfung, sonst
	 * könnte jemand eine Mail ein zweites Mal archivieren, nur weil er die erste nicht lesen darf.
	 */
	public boolean existsByInternetMessageId(String internetMessageId) {
		SolrQuery query = new SolrQuery("*:*");
		query.addFilterQuery(INTERNET_MESSAGE_ID_FIELD_NAME + ":" + ClientUtils.escapeQueryChars(internetMessageId));
		query.setRows(0);
		return solrDocumentService.queryDocumentsPage(CORE_KEY, query).totalFound() > 0;
	}

	/**
	 * Welche dieser Mails schon im Archiv liegen, als internetMessageId -> id der Nachricht: die Markierung in der
	 * Postfachliste. Ohne Rechteprüfung aus demselben Grund wie existsByInternetMessageId; heraus gehen nur die
	 * laufenden Nummern, öffnen lässt sich eine Nachricht nur mit Leserecht. Der Aufrufer prüft die Rolle.
	 */
	public Map<String, String> archivedIdsByInternetMessageIds(Collection<String> internetMessageIds) {
		Set<String> distinct = new LinkedHashSet<>();
		for (String internetMessageId : internetMessageIds) {
			if (internetMessageId != null && !internetMessageId.isBlank()) {
				distinct.add(internetMessageId.trim());
			}
		}
		Map<String, String> idsByInternetMessageId = new LinkedHashMap<>();
		if (distinct.isEmpty()) {
			return idsByInternetMessageId;
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "*:*");
		params.add("fq", INTERNET_MESSAGE_ID_FIELD_NAME + ":(" + escapedAlternatives(distinct) + ")");
		params.set("fl", "id," + INTERNET_MESSAGE_ID_FIELD_NAME);
		params.set("sort", "id asc");
		params.set("rows", distinct.size());
		try {
			for (SolrDocument document : client().query(params).getResults()) {
				String internetMessageId = firstValueOf(document.getFieldValue(INTERNET_MESSAGE_ID_FIELD_NAME));
				if (internetMessageId != null) {
					idsByInternetMessageId.putIfAbsent(internetMessageId, String.valueOf(document.getFieldValue("id")));
				}
			}
			return idsByInternetMessageId;
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	// ===== Schreiben =====

	/** Die nächste laufende Nummer: eine neue Zeile im Zähler, ihre id, um eins höher als die letzte */
	@Transactional
	public String nextNachrichtenId() {
		return String.valueOf(counterNachrichtenIdRepository.save(new CounterNachrichtenId()).getId());
	}

	/**
	 * Ein neues Dokument voll schreiben, sofort sichtbar. Jeder Name muss ein Feld des Kerns sein, sonst ist das ein
	 * Fehler im Code des Aufrufers (IllegalStateException). Ein einwertiges Feld nimmt keine Liste; ohne Wert oder mit
	 * leerer Liste fällt ein Feld weg. Die id muss dabei sein (nextNachrichtenId).
	 */
	public void createNachrichtenDocument(Map<String, Object> fieldValues) {
		Map<String, SolrField> solrFieldsByName = new LinkedHashMap<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(CORE_KEY)) {
			solrFieldsByName.put(solrField.getName(), solrField);
		}
		Map<String, Object> document = new LinkedHashMap<>();
		for (Map.Entry<String, Object> fieldValue : fieldValues.entrySet()) {
			SolrField solrField = solrFieldsByName.get(fieldValue.getKey());
			if (solrField == null) {
				throw new IllegalStateException("kein Feld des Kerns " + CORE_KEY + ": " + fieldValue.getKey());
			}
			Object value = fieldValue.getValue();
			if (value == null || (value instanceof Collection<?> values && values.isEmpty())) {
				continue;
			}
			if (!solrField.isMultiValued() && value instanceof Collection<?>) {
				throw new IllegalStateException("das Feld " + fieldValue.getKey() + " ist einwertig, bekommt aber eine Liste");
			}
			document.put(fieldValue.getKey(), solrField.isMultiValued() && !(value instanceof Collection<?>) ? List.of(value) : value);
		}
		if (!document.containsKey(SolrDocumentService.ID_FIELD_NAME)) {
			throw new IllegalStateException("eine Nachricht ohne id");
		}
		solrDocumentService.writeNewDocument(CORE_KEY, document);
	}

	// ===== Werte =====

	/** Die Werte escaped und mit OR verbunden, für feld:(a OR b) */
	private static String escapedAlternatives(Collection<String> values) {
		List<String> escaped = new ArrayList<>();
		for (String value : values) {
			escaped.add(ClientUtils.escapeQueryChars(value));
		}
		return String.join(" OR ", escaped);
	}

	/** Alle Werte eines Feldes als Texte, ein einzelner Wert als Liste mit einem Eintrag, leer bei nichts */
	private static List<String> valuesOf(Object fieldValue) {
		if (fieldValue == null) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (Object value : fieldValue instanceof Collection<?> many ? many : List.of(fieldValue)) {
			String text = String.valueOf(value);
			if (!text.isBlank()) {
				values.add(text);
			}
		}
		return values;
	}

	/** Der erste Wert eines Feldes als Text, null bei nichts */
	private static String firstValueOf(Object fieldValue) {
		List<String> values = valuesOf(fieldValue);
		return values.isEmpty() ? null : values.get(0);
	}

	// ===== Nachziehen =====

	/**
	 * Der SolrManager startet das über den SolrReindexService im Hintergrund, um alle Nachrichten auf den Sollstand zu
	 * bringen (User 2026-10-14), z. B. die ACLs nach einer geänderten Nachrichtenart. Je Option eine Prüfung, die ihren
	 * Sollstand einmal beim Bauen liest; die Schleife liest blockweise nur die Felder der Prüfungen und schreibt jede
	 * geänderte Nachricht einzeln über send. Nur ADMIN.
	 */
	public void reindexNachrichtenDocuments(Set<NachrichtenReindexOption> options, SolrReindexProgress progress) {
		if (!currentUsersProvider.getCurrentUsersAccessRoleKeys().contains("ADMIN")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nachziehen nur als ADMIN");
		}
		List<SolrReindexCheck> checks = new ArrayList<>();
		if (options.contains(NachrichtenReindexOption.ACLS_FROM_MESSAGE_SUB_CLASS)) {
			checks.add(utils.aclsFromMessageSubClassCheck());
		}
		solrReindexIterator.iterate(CORE_KEY, checks, this::send, progress);
	}

	/**
	 * Ein Atomic Update an …/solr/NACHRICHTEN/update, kein Commit: Suchbar wird es mit dem autoSoftCommit alle 10 s. Lehnt
	 * Solr ab, bleibt dessen 400 eine 400, alles andere ist 503.
	 */
	private void send(SolrInputDocument update) {
		try {
			client().add(update);
		} catch (SolrException e) {
			throw new ResponseStatusException(e.code() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE,
					"Solr lehnt das Schreiben ab (Kern " + CORE_KEY + "): " + e.getMessage(), e);
		} catch (SolrServerException | IOException e) {
			throw unavailable(e);
		}
	}

	private SolrClient client() {
		// die HTTP-Verbindung zum Kern NACHRICHTEN, je Kern genau ein Client
		return solrClientRegistry.clientFor(CORE_KEY);
	}

	private static ResponseStatusException unavailable(Exception e) {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Solr nicht erreichbar (Kern " + CORE_KEY + "): " + e.getMessage(), e);
	}

}
