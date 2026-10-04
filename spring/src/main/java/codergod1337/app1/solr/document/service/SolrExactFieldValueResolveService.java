package codergod1337.app1.solr.document.service;

import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.document.service.SolrDocumentService.SolrQueriedDocumentsPage;
import codergod1337.app1.solr.field.model.SolrFieldType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Das Auflösen nach exakten Feldwerten, fachfrei und in jedem Kern gleich: ein oder mehrere Felder, je Feld ein oder
 * mehrere Werte. Mehrere Werte ODER, mehrere Felder UND, keine Suggested-Zwillinge. Damit holt man die id eines
 * Dokuments (z. B. über versionChain und version) und lädt die Dokumentseite über id.
 *
 * Was der User darf, holt sich dieser Service selbst (SolrCurrentUserCoreAccessService), es ist kein Parameter:
 * Pflicht-fq der Leseprüfung, fl nur die lesbaren Felder, aufgelöst und sortiert wird nur nach lesbaren Feldern. Ein
 * Feld, nach dem der User nicht auflösen darf, bekommt dieselbe Meldung wie eines, das es nicht gibt: Dass es das Feld
 * gibt, erfährt er nicht. „Gibt es nicht“ und „darfst du nicht“ sehen gleich aus, null Treffer.
 *
 * Die Wege mit „Writable“ nehmen die Schreibprüfung dazu: nur Dokumente, deren writeAcl eine AR des Users enthält. Sie
 * dienen der Dateifreigabe, denn Freigeben ist ein Schreibvorgang.
 *
 * Werte gehen nie als Query-String zusammengeklebt nach Solr: Bei string, Zahlen, boolean und date über den terms-Parser
 * mit den Werten als eigenem Parameter, bei Textfeldern (analysiert, der terms-Parser trifft dort nur den rohen Token)
 * als escaped Phrasen.
 */
@Service
public class SolrExactFieldValueResolveService {

	public static final int DEFAULT_PAGE_SIZE = 25;
	public static final int MAX_PAGE_SIZE = 200;

	/**
	 * Eine Dateifreigabe braucht weniger als so viele Treffer (Regel 2026-10-03). Mehr heißt: ein weiterer Filter, der die
	 * Treffer enger fasst. resolveWritableDocumentIdsByExactFieldValues hält sich daran.
	 */
	public static final int MAX_DOCUMENTS_PER_DOCUMENT_GRANT = 500;

	/** Seitengröße beim Einsammeln von ids */
	private static final int ID_PAGE_SIZE = 1_000;

	/** Trennt die Werte eines terms-Parameters. Ein Steuerzeichen, das in keinem Wert vorkommt. */
	private static final String TERMS_PARSER_VALUE_SEPARATOR = "\u001F";

	/** Eine Trefferseite: wie viele Dokumente es insgesamt gibt, welche Seite das ist und ihre Dokumente als Maps mit _version_ */
	public record SolrResolvedDocumentsPage(long totalFound, int page, int size, List<Map<String, Object>> documents) {
	}

	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final SolrDocumentChecker solrDocumentChecker;
	private final SolrDocumentService solrDocumentService;

	public SolrExactFieldValueResolveService(SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService,
			SolrDocumentChecker solrDocumentChecker, SolrDocumentService solrDocumentService) {
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.solrDocumentChecker = solrDocumentChecker;
		this.solrDocumentService = solrDocumentService;
	}

	/**
	 * Löst im Kern auf, mit der Leseprüfung des current users. 403 ohne {@code <KERN>}, 400 bei einem Feld, nach dem er
	 * nicht auflösen darf oder das es nicht gibt, bei Werten, die nicht zum Typ passen, bei einer falschen Seite und bei
	 * einem Sortierfeld, das er nicht lesen darf.
	 */
	public SolrResolvedDocumentsPage resolveDocumentsPageByExactFieldValues(String coreKey,
			JsonNode fieldValuesByFieldName, int page, int size, String sortFieldName, boolean sortDescending) {
		return resolveDocumentsPage(coreKey, fieldValuesByFieldName, page, size, sortFieldName, sortDescending, false);
	}

	/** Wie oben, aber nur Dokumente, die der current user auch ändern darf (Lese- und Schreibprüfung). */
	public SolrResolvedDocumentsPage resolveWritableDocumentsPageByExactFieldValues(String coreKey,
			JsonNode fieldValuesByFieldName, int page, int size) {
		return resolveDocumentsPage(coreKey, fieldValuesByFieldName, page, size, null, false, true);
	}

	/**
	 * Nur die ids aller Treffer, die der current user ändern darf: Was der Freigebende selbst sieht und schreiben darf,
	 * mehr kann er nicht weitergeben. 400, wenn es nicht weniger als MAX_DOCUMENTS_PER_DOCUMENT_GRANT sind.
	 */
	public List<String> resolveWritableDocumentIdsByExactFieldValues(String coreKey, JsonNode fieldValuesByFieldName) {
		SolrCurrentUserReadAndWriteAccessForCore access = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(coreKey);
		Map<String, List<Object>> fieldValues = exactResolveAllowedFieldValues(access, fieldValuesByFieldName);
		return collectDocumentIdsBelowGrantLimit(coreKey, queryWithCurrentUserChecks(access, coreKey, fieldValues, true));
	}

	/**
	 * Ein Dokument für das Backend selbst: mit der Leseprüfung des current users, aber mit den Feldern, die das Backend
	 * braucht und der User nicht sehen muss, z. B. sftpPath für den Download. Geht nie an den Client. null, wenn es das
	 * Dokument nicht gibt oder der User es nicht sehen darf, beides sieht gleich aus.
	 */
	public Map<String, Object> loadDocumentForBackendWithReadAclAndGrantedAccessCheck(String coreKey, String id,
			List<String> fieldNames) {
		SolrCurrentUserReadAndWriteAccessForCore access = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(coreKey);
		SolrQuery query = new SolrQuery(SolrDocumentService.ID_FIELD_NAME + ":" + ClientUtils.escapeQueryChars(id));
		for (String filterQuery : access.readAclAndGrantedAccessFilterQueries()) {
			query.addFilterQuery(filterQuery);
		}
		query.setFields(fieldNames.toArray(String[]::new));
		query.setRows(1);
		SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(coreKey, query);
		return queried.documents().isEmpty() ? null : queried.documents().get(0);
	}

	/**
	 * Die ids der Dokumente, die den Bedingungen entsprechen UND diesen User in userGrantedAccess tragen, ohne die
	 * Prüfungen des current users. Nur für Entzug und Ablauf einer Dateifreigabe: Was zurückkommt, hat genau diese
	 * Freigabe geschrieben, mehr verrät der Weg nicht. Die Bedingungen kommen aus dem Register, nicht vom Client.
	 */
	public List<String> resolveDocumentIdsCarryingGranteeUsersGuid(String coreKey, JsonNode fieldValuesByFieldName,
			UUID granteeUsersGuid) {
		Map<String, List<Object>> fieldValues = solrDocumentChecker.checkExactResolveFieldValues(coreKey,
				fieldValuesByFieldName);
		SolrQuery query = new SolrQuery("*:*");
		query.addFilterQuery(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME + ":"
				+ ClientUtils.escapeQueryChars(granteeUsersGuid.toString()));
		addExactFieldValueFilterQueries(query, coreKey, fieldValues);
		return collectDocumentIdsBelowGrantLimit(coreKey, query);
	}

	private static final String NO_CONDITION_MESSAGE = "mindestens eine Bedingung mit Wert ist Pflicht: Auflösen gibt nie alles aus";

	/**
	 * Die Dokumente des Kerns, die diese guid in userGrantedAccess tragen, mit der Leseprüfung und den lesbaren Feldern
	 * des current users, seitenweise: der Tab „Meine Freigaben“ (Vorgabe 2026-10-04). Die Bedingung setzt das Backend,
	 * nicht der Client, deshalb braucht sie keine Auflöse-Erlaubnis an userGrantedAccess. Der Aufrufer gibt die guid des
	 * current users mit, nie eine fremde: Wer die Freigaben eines anderen sehen will, geht über das Register.
	 */
	public SolrResolvedDocumentsPage resolveDocumentsPageCarryingGranteeUsersGuid(String coreKey, UUID granteeUsersGuid,
			int page, int size, String sortFieldName, boolean sortDescending) {
		SolrCurrentUserReadAndWriteAccessForCore access = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(coreKey);
		requirePageAndSize(page, size);
		requireSortable(access, sortFieldName);
		SolrQuery query = queryWithCurrentUserChecks(access, coreKey, Map.of(), false);
		query.addFilterQuery(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME + ":"
				+ ClientUtils.escapeQueryChars(granteeUsersGuid.toString()));
		return queryPage(access, coreKey, query, page, size, sortFieldName, sortDescending);
	}

	private SolrResolvedDocumentsPage resolveDocumentsPage(String coreKey, JsonNode fieldValuesByFieldName, int page,
			int size, String sortFieldName, boolean sortDescending, boolean withWriteAclCheck) {
		SolrCurrentUserReadAndWriteAccessForCore access = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(coreKey);
		Map<String, List<Object>> fieldValues = exactResolveAllowedFieldValues(access, fieldValuesByFieldName);
		requirePageAndSize(page, size);
		requireSortable(access, sortFieldName);
		SolrQuery query = queryWithCurrentUserChecks(access, coreKey, fieldValues, withWriteAclCheck);
		return queryPage(access, coreKey, query, page, size, sortFieldName, sortDescending);
	}

	/** fl aus den lesbaren Feldern plus _version_, Seite, Sortierung, dann die Anfrage */
	private SolrResolvedDocumentsPage queryPage(SolrCurrentUserReadAndWriteAccessForCore access, String coreKey,
			SolrQuery query, int page, int size, String sortFieldName, boolean sortDescending) {
		List<String> fieldNames = new ArrayList<>(access.readableFieldAndHookNamesOfCoreForCurrentUser());
		fieldNames.add(SolrDocumentService.VERSION_FIELD_NAME);
		query.setFields(fieldNames.toArray(String[]::new));
		query.setStart(page * size);
		query.setRows(size);
		if (sortFieldName != null) {
			query.addSort(sortFieldName, sortDescending ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc);
		}
		SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(coreKey, query);
		return new SolrResolvedDocumentsPage(queried.totalFound(), page, size, queried.documents());
	}

	private static void requirePageAndSize(int page, int size) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page muss 0 oder größer sein");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size muss zwischen 1 und " + MAX_PAGE_SIZE + " liegen");
		}
	}

	private static void requireSortable(SolrCurrentUserReadAndWriteAccessForCore access, String sortFieldName) {
		if (sortFieldName != null && !access.isReadableByCurrentUser(sortFieldName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					sortFieldName + ": kein Feld, nach dem du in diesem Kern sortieren darfst");
		}
	}

	/** q=*:*, die fq der Leseprüfung, auf Wunsch auch die der Schreibprüfung, und je Feld ein fq für die Bedingungen */
	private SolrQuery queryWithCurrentUserChecks(SolrCurrentUserReadAndWriteAccessForCore access, String coreKey,
			Map<String, List<Object>> fieldValues, boolean withWriteAclCheck) {
		SolrQuery query = new SolrQuery("*:*");
		for (String filterQuery : access.readAclAndGrantedAccessFilterQueries()) {
			query.addFilterQuery(filterQuery);
		}
		if (withWriteAclCheck) {
			for (String filterQuery : access.writeAclFilterQueries()) {
				query.addFilterQuery(filterQuery);
			}
		}
		addExactFieldValueFilterQueries(query, coreKey, fieldValues);
		return query;
	}

	/**
	 * Erst die Namen gegen das, was der User auflösen darf, dann die Werte gegen die Form: Nur erlaubte Namen gehen zum
	 * Checker, damit ein verborgenes Feld dieselbe Meldung bekommt wie ein unbekanntes. Ohne mindestens eine Bedingung
	 * mit Wert gibt es kein Auflösen (400): Auflösen heißt exakt nach Feld und Wert, nicht alles ausgeben (Vorgabe
	 * 2026-10-03). Alles ausgeben ist Sache der Suche mit ihren Rechten und Grenzen.
	 */
	private Map<String, List<Object>> exactResolveAllowedFieldValues(SolrCurrentUserReadAndWriteAccessForCore access,
			JsonNode fieldValuesByFieldName) {
		if (fieldValuesByFieldName == null || fieldValuesByFieldName.isNull()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_CONDITION_MESSAGE);
		}
		if (!fieldValuesByFieldName.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldValuesByFieldName ist kein JSON-Objekt");
		}
		List<String> problems = new ArrayList<>();
		ObjectNode allowed = JsonNodeFactory.instance.objectNode();
		for (Map.Entry<String, JsonNode> property : fieldValuesByFieldName.properties()) {
			if (access.isExactResolveAllowedForCurrentUser(property.getKey())) {
				allowed.set(property.getKey(), property.getValue());
			} else {
				problems.add(property.getKey() + ": kein Feld, nach dem du in diesem Kern auflösen darfst");
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		Map<String, List<Object>> fieldValues = solrDocumentChecker.checkExactResolveFieldValues(access.coreKey(), allowed);
		if (fieldValues.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_CONDITION_MESSAGE);
		}
		return fieldValues;
	}

	/**
	 * Je Feld ein fq. Textfelder als escaped Phrasen (der Analyzer läuft über die Eingabe wie über den Index), alles
	 * andere über den terms-Parser mit den Werten als eigenem Parameter rv0, rv1, …: Die Werte stehen nie im Query-String.
	 */
	private void addExactFieldValueFilterQueries(SolrQuery query, String coreKey, Map<String, List<Object>> fieldValues) {
		int index = 0;
		for (Map.Entry<String, List<Object>> condition : fieldValues.entrySet()) {
			String fieldName = condition.getKey();
			SolrFieldType type = solrDocumentChecker.solrFieldTypeOf(coreKey, fieldName);
			if (type == SolrFieldType.TEXT_MINIMAL || type == SolrFieldType.TEXT_WORD || type == SolrFieldType.TEXT_WORDS) {
				List<String> phrases = new ArrayList<>();
				for (Object value : condition.getValue()) {
					phrases.add("\"" + ClientUtils.escapeQueryChars(value.toString()) + "\"");
				}
				query.addFilterQuery(fieldName + ":(" + String.join(" OR ", phrases) + ")");
			} else {
				String parameter = "rv" + index++;
				List<String> texts = new ArrayList<>();
				for (Object value : condition.getValue()) {
					texts.add(value.toString());
				}
				query.addFilterQuery("{!terms f=" + fieldName + " separator=\"" + TERMS_PARSER_VALUE_SEPARATOR + "\" v=$"
						+ parameter + "}");
				query.set(parameter, String.join(TERMS_PARSER_VALUE_SEPARATOR, texts));
			}
		}
	}

	/** Alle ids einer Abfrage, seitenweise, solange es weniger als MAX_DOCUMENTS_PER_DOCUMENT_GRANT sind */
	private List<String> collectDocumentIdsBelowGrantLimit(String coreKey, SolrQuery query) {
		query.setFields(SolrDocumentService.ID_FIELD_NAME);
		query.setRows(ID_PAGE_SIZE);
		List<String> ids = new ArrayList<>();
		for (int start = 0;; start += ID_PAGE_SIZE) {
			query.setStart(start);
			SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(coreKey, query);
			if (queried.totalFound() >= MAX_DOCUMENTS_PER_DOCUMENT_GRANT) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, queried.totalFound()
						+ " Treffer, eine Freigabe braucht weniger als " + MAX_DOCUMENTS_PER_DOCUMENT_GRANT
						+ ": ein weiterer Filter muss die Treffer enger fassen");
			}
			for (Map<String, Object> document : queried.documents()) {
				ids.add(String.valueOf(document.get(SolrDocumentService.ID_FIELD_NAME)));
			}
			if (queried.documents().size() < ID_PAGE_SIZE || ids.size() >= queried.totalFound()) {
				return ids;
			}
		}
	}

}
