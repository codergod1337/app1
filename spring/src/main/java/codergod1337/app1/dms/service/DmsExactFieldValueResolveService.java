package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsExactFieldValueResolveRequest;
import codergod1337.app1.dms.model.DmsResolvedDocumentsPage;
import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.dms.model.DmsSolrDocumentForClient;
import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore;
import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore.SolrCurrentUserCoreAccessForClient;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.document.service.SolrDocumentService.SolrQueriedDocumentsPage;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Das Auflösen im Kern DMS, die zweite der drei Lesemethoden je Kern (Vorgabe 2026-10-07): Jeder Leseweg des DMS, der
 * nicht Existenzcheck oder Suche ist, hat hier seine Methode, benannt nach seinem Vorgang. Dieser Service baut seine
 * Solr-Abfragen selbst und ruft die rohe Solr-Schicht direkt, es gibt keinen gemeinsamen Leseservice für alle Kerne.
 * Was der User darf, rechnet der SolrCurrentUserCoreAccessService aus Token und Kopien im Speicher.
 *
 * Exakte Treffer auf einem oder mehreren Feldern, mehrere Werte ODER, mehrere Felder UND, keine Suggested-Zwillinge,
 * kein Freitext. Zwei Arten von Bedingungen: Bedingungen des Clients werden gegen das geprüft, wonach der current user
 * auflösen darf (id immer, Hooks mit HOOK_FULL oder Hook-Freigabe, andere Felder mit Feldfreigabe oder DMS_SUCHE); ein
 * Feld, nach dem er nicht auflösen darf, bekommt dieselbe Meldung wie eines, das es nicht gibt. Bedingungen des
 * Backends (id aus der URL, versionChain aus dem Upload, die eigene guid) brauchen kein Auflöserecht, nur die Form.
 * Ohne mindestens eine Bedingung mit Wert gibt es kein Auflösen: Auflösen gibt nie alles aus, das ist Sache der Suche.
 *
 * Das Lese-fq (readAcl oder userGrantedAccess) hängt an jeder Abfrage, oder mit Schreibcheck allein das Schreib-fq
 * (writeAcl), denn Schreiben impliziert kein Lesen. Dazu an jeder Abfrage der Status: Ein User bekommt nur ACTIVE und
 * ARCHIVED (User 2026-10-08), auch beim Auflösen über die Versionskette und beim Schreibcheck; ein gelöschtes Dokument
 * gibt es für ihn auf keinem Weg, zurückholen kann es der Admin im DMS-Manager. fl ist die Feldgrenze des Users oder,
 * fürs Backend, eine genannte Feldliste, die nie zum Client geht. Werte gehen nie als Query-String zusammengeklebt nach Solr: bei string, Zahlen,
 * boolean und date über den terms-Parser mit den Werten als eigenem Parameter, bei Textfeldern als escaped Phrasen.
 */
@Service
public class DmsExactFieldValueResolveService {

	public static final int DEFAULT_PAGE_SIZE = 25;
	public static final int MAX_PAGE_SIZE = 200;

	/**
	 * Eine Dateifreigabe braucht weniger als so viele Treffer (Regel 2026-10-03). Mehr heißt: ein weiterer Filter, der die
	 * Treffer enger fasst.
	 */
	public static final int MAX_DOCUMENTS_PER_DOCUMENT_GRANT = 500;

	/** So viele Dokumente liefert ein Backend-Auflösen höchstens, z. B. die Glieder einer Versionskette */
	private static final int MAX_BACKEND_ROWS = 1_000;

	/** Seitengröße beim Einsammeln von ids */
	private static final int ID_PAGE_SIZE = 1_000;

	/** Trennt die Werte eines terms-Parameters. Ein Steuerzeichen, das in keinem Wert vorkommt. */
	private static final String TERMS_PARSER_VALUE_SEPARATOR = "\u001F";

	private static final String NO_CONDITION_MESSAGE = "mindestens eine Bedingung mit Wert ist Pflicht: Auflösen gibt nie alles aus";

	/** Was der Download braucht, nie zum Client */
	private static final List<String> DOWNLOAD_FIELD_NAMES = List.of(DmsSolrDocumentFieldNames.SFTP_PATH,
			DmsSolrDocumentFieldNames.FILE_NAME, DmsSolrDocumentFieldNames.MIME_TYPE,
			DmsSolrDocumentFieldNames.FILE_SIZE_BYTES);

	/** Was eine neue Fassung oder ein Dateiartwechsel über die Glieder der Kette wissen muss */
	private static final List<String> VERSION_CHAIN_MEMBER_FIELD_NAMES = List.of(DmsSolrDocumentFieldNames.VERSION,
			DmsSolrDocumentFieldNames.STATUS, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY);

	/** Neueste Dokumente zuerst, wenn der User das Feld lesen darf, sonst in Indexreihenfolge */
	private static final String MY_GRANTS_SORT_FIELD_NAME = DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT;

	/** Alle stored fields: der Stand eines Dokuments vor einem revisionierten Schreiben, fürs ZIP */
	private static final List<String> WHOLE_DOCUMENT_FIELD_NAMES = List.of("*");

	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final DmsSolrDocumentChecker dmsSolrDocumentChecker;
	private final SolrDocumentService solrDocumentService;
	private final CurrentUsersProvider currentUsersProvider;

	public DmsExactFieldValueResolveService(SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService,
			DmsSolrDocumentChecker dmsSolrDocumentChecker, SolrDocumentService solrDocumentService,
			CurrentUsersProvider currentUsersProvider) {
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.dmsSolrDocumentChecker = dmsSolrDocumentChecker;
		this.solrDocumentService = solrDocumentService;
		this.currentUsersProvider = currentUsersProvider;
	}

	// ===== Bedingungen des Backends bauen =====

	/** Eine Bedingung des Backends: ein Feld, ein Wert */
	public static ObjectNode backendCondition(String fieldName, String value) {
		return backendCondition(fieldName, List.of(value));
	}

	/** Eine Bedingung des Backends: ein Feld, mehrere Werte (ODER) */
	public static ObjectNode backendCondition(String fieldName, Collection<String> values) {
		ObjectNode condition = JsonNodeFactory.instance.objectNode();
		ArrayNode array = JsonNodeFactory.instance.arrayNode(values.size());
		for (String value : values) {
			array.add(value);
		}
		condition.set(fieldName, array);
		return condition;
	}

	// ===== Vorgänge der User =====

	/** Was der current user im Kern DMS darf und sieht: danach baut die Maske, welche Felder sie anbietet. */
	@PreAuthorize("hasRole('DMS')")
	public SolrCurrentUserCoreAccessForClient currentUserDmsAccessForClient() {
		return access().forClient();
	}

	/** Der Tab „Auflösen“: Bedingungen des Clients, geprüft gegen seine Auflöserechte, seitenweise, seine Feldgrenze */
	@PreAuthorize("hasRole('DMS')")
	public DmsResolvedDocumentsPage resolveDmsSolrDocumentsPageForResolveTab(
			DmsExactFieldValueResolveRequest resolveRequest) {
		int page = resolveRequest.page() != null ? resolveRequest.page() : 0;
		int size = resolveRequest.size() != null ? resolveRequest.size() : DEFAULT_PAGE_SIZE;
		String sortFieldName = resolveRequest.sortFieldName() != null && !resolveRequest.sortFieldName().isBlank()
				? resolveRequest.sortFieldName().strip()
				: null;
		return resolveClientPage(resolveRequest.fieldValuesByFieldName(), null, page, size, sortFieldName,
				Boolean.TRUE.equals(resolveRequest.sortDescending()), false);
	}

	/**
	 * Die Dokumentseite: das Dokument mit der Feldgrenze des current users und dem Flag, ob er es schreiben darf. 404,
	 * wenn es keins gibt oder er es nicht sehen darf.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsSolrDocumentForClient loadDmsSolrDocumentForDocumentPage(String id) {
		DmsSolrDocumentForClient loaded = loadDmsSolrDocumentForClientOrNull(id);
		if (loaded == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit dieser id");
		}
		return loaded;
	}

	/**
	 * Die Antwort des Uploads nach CREATED und MERGED: dasselbe Dokument wie auf der Dokumentseite, mit der Feldgrenze
	 * des Users. null, wenn er es nicht sehen darf, denn Anlegen braucht kein Lesen.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsSolrDocumentForClient loadDmsSolrDocumentForUploadAnswer(String id) {
		return loadDmsSolrDocumentForClientOrNull(id);
	}

	/**
	 * Der Download: sftpPath, Dateiname, MIME-Typ und Größe mit der Lesegrenze des Users, Felder fürs Backend, nie zum
	 * Client. null, wenn es die Datei nicht gibt oder er sie nicht sehen darf.
	 */
	@PreAuthorize("hasRole('DMS')")
	public Map<String, Object> loadDmsSolrDocumentFieldsForDownload(String id) {
		return firstOrNull(resolveBackendFields(idCondition(id), DOWNLOAD_FIELD_NAMES, false));
	}

	/**
	 * Vor einem Schreibvorgang: die genannten Felder des Dokuments, allein mit dem Schreib-fq. null, wenn es das
	 * Dokument nicht gibt oder der User es nicht schreiben darf, beides sieht gleich aus. Das ist der Schreibcheck.
	 */
	@PreAuthorize("hasRole('DMS')")
	public Map<String, Object> loadDmsSolrDocumentFieldsForWrite(String id, List<String> fieldNames) {
		return firstOrNull(resolveBackendFields(idCondition(id), fieldNames, true));
	}

	/**
	 * Vor einem revisionierten Schreibvorgang: das ganze Dokument, alle stored fields, allein mit dem Schreib-fq.
	 * Derselbe Schreibcheck wie {@link #loadDmsSolrDocumentFieldsForWrite}, nur dass der Stand danach als ZIP gesichert
	 * wird und der Schreibservice daran erkennt, welche Felder sich wirklich ändern. null, wenn es das Dokument nicht
	 * gibt oder der User es nicht schreiben darf. Nie zum Client.
	 */
	@PreAuthorize("hasRole('DMS')")
	public Map<String, Object> loadWholeDmsSolrDocumentForWrite(String id) {
		return firstOrNull(resolveBackendFields(idCondition(id), WHOLE_DOCUMENT_FIELD_NAMES, true));
	}

	/**
	 * Eine neue Fassung oder ein Dateiartwechsel: alle Glieder der Kette, die der User sehen darf, mit version, status
	 * und Dateiart, nach id sortiert. Leer, wenn es die Kette nicht gibt oder er sie nicht sieht.
	 */
	@PreAuthorize("hasRole('DMS')")
	public List<Map<String, Object>> loadDmsVersionChainMembers(String versionChain) {
		return resolveBackendFields(backendCondition(DmsSolrDocumentFieldNames.VERSION_CHAIN, versionChain),
				VERSION_CHAIN_MEMBER_FIELD_NAMES, false);
	}

	/**
	 * Der Fahrplan der Versionskette und die Maske der neuen Fassung (gebaut 2026-10-08): alle Glieder der Kette, die
	 * der User sehen darf, mit seiner Feldgrenze, aufsteigend nach version, wenn er das Feld lesen darf, sonst in
	 * Indexreihenfolge und die Maske sortiert selbst. Ein Auflösen, keine Suche: keine Such-Rolle, das Tor ist die
	 * Lese-ACL jeder Fassung. Leer, wenn es die Kette nicht gibt oder er nichts davon sieht.
	 */
	@PreAuthorize("hasRole('DMS')")
	public List<Map<String, Object>> resolveDmsVersionChainForClient(String versionChain) {
		String sortFieldName = access().isReadableByCurrentUser(DmsSolrDocumentFieldNames.VERSION)
				? DmsSolrDocumentFieldNames.VERSION
				: null;
		return resolveClientPage(null, backendCondition(DmsSolrDocumentFieldNames.VERSION_CHAIN, versionChain), 0,
				MAX_PAGE_SIZE, sortFieldName, false, false).documents();
	}

	/**
	 * Der Tab „Meine Freigaben“: die Dokumente mit der guid des current users in userGrantedAccess, seitenweise, mit
	 * seiner Feldgrenze. Die Bedingung setzt das Backend aus dem Token, nie der Client.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsResolvedDocumentsPage resolveDmsSolrDocumentsPageForMyGrants(int page, int size) {
		String sortFieldName = access().isReadableByCurrentUser(MY_GRANTS_SORT_FIELD_NAME) ? MY_GRANTS_SORT_FIELD_NAME : null;
		return resolveClientPage(null,
				backendCondition(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME, currentUsersProvider.getCurrentUsersGuid().toString()),
				page, size, sortFieldName, true, false);
	}

	/**
	 * Vor einem Update vieler Dokumente: welche dieser ids der current user schreiben darf. Nur diese werden
	 * geschrieben. Die ids kommen aus einem Auflösen von eben, nie vom Client.
	 */
	@PreAuthorize("hasRole('DMS')")
	public List<String> resolveWritableDmsSolrDocumentIdsAmong(List<String> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return resolveWritableIds(null, backendCondition(SolrCore.ID_FIELD_NAME, ids));
	}

	// ===== Vorgänge des Admins =====

	/**
	 * Der Kontrollblick des Admins: die guids in userGrantedAccess eines Dokuments, mit der Lesegrenze des Users, fürs
	 * Backend. null, wenn er das Dokument nicht sehen darf.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public List<String> loadGranteesOfDmsSolrDocument(String id) {
		Map<String, Object> document = firstOrNull(resolveBackendFields(idCondition(id),
				List.of(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME), false));
		if (document == null) {
			return null;
		}
		Object granted = document.get(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME);
		List<?> values = granted instanceof List<?> list ? list : granted == null ? List.of() : List.of(granted);
		return values.stream().map(String::valueOf).toList();
	}

	/**
	 * Die Vorschau einer Dateifreigabe: die Treffer der Bedingungen des Admins, die er selbst schreiben darf, seitenweise
	 * mit seiner Feldgrenze. Freigeben ist ein Schreibvorgang, deshalb allein das Schreib-fq.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public DmsResolvedDocumentsPage resolveWritableDmsSolrDocumentsPageForDocumentGrantPreview(
			JsonNode clientFieldValuesByFieldName, int page, int size) {
		return resolveClientPage(clientFieldValuesByFieldName, null, page, size, null, false, true);
	}

	/** Die Dateifreigabe setzen: nur die ids aller Treffer der Bedingungen des Admins, die er schreiben darf, unter 500 */
	@PreAuthorize("hasRole('ADMIN')")
	public List<String> resolveWritableDmsSolrDocumentIdsForDocumentGrant(JsonNode clientFieldValuesByFieldName) {
		return resolveWritableIds(clientFieldValuesByFieldName, null);
	}

	/**
	 * Entzug und Ablauf einer Dateifreigabe: die ids der Dokumente, die die Bedingungen aus dem Register treffen UND die
	 * guid des Empfängers tragen, die der current user schreiben darf. Beide Bedingungen setzt das Backend aus dem
	 * Register, nicht der Client.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public List<String> resolveWritableDmsSolrDocumentIdsCarryingGrantee(JsonNode storedFieldValuesByFieldName,
			UUID granteeUsersGuid) {
		ObjectNode backendConditions = storedFieldValuesByFieldName instanceof ObjectNode objectNode
				? objectNode.deepCopy()
				: JsonNodeFactory.instance.objectNode();
		backendConditions.set(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME,
				backendCondition(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME, granteeUsersGuid.toString())
						.get(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME));
		return resolveWritableIds(null, backendConditions);
	}

	// ===== Der Abfragebau =====

	/** Alles, was der current user im Kern DMS darf. 403 ohne die Rolle DMS. */
	private SolrCurrentUserReadAndWriteAccessForCore access() {
		return solrCurrentUserCoreAccessService.readAndWriteAccessOfCurrentUserForCore(DmsSolrDocumentFieldNames.CORE_KEY);
	}

	private DmsSolrDocumentForClient loadDmsSolrDocumentForClientOrNull(String id) {
		DmsResolvedDocumentsPage resolved = resolveClientPage(null, idCondition(id), 0, 1, null, false, false);
		if (resolved.documents().isEmpty()) {
			return null;
		}
		boolean writeAclMatch = !resolveBackendFields(idCondition(id), List.of(), true).isEmpty();
		return new DmsSolrDocumentForClient(resolved.documents().get(0), writeAclMatch);
	}

	/**
	 * Eine Trefferseite für den Client: Bedingungen des Clients gegen seine Auflöserechte, Bedingungen des Backends nur
	 * Form, Seite und Sortierung geprüft, fl aus seiner Feldgrenze plus _version_ als Text.
	 */
	private DmsResolvedDocumentsPage resolveClientPage(JsonNode clientFieldValuesByFieldName,
			JsonNode backendFieldValuesByFieldName, int page, int size, String sortFieldName, boolean sortDescending,
			boolean requireWriteAclMatch) {
		SolrCurrentUserReadAndWriteAccessForCore access = access();
		Map<String, List<Object>> fieldValues = checkedConditions(access, clientFieldValuesByFieldName,
				backendFieldValuesByFieldName);
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page muss 0 oder größer sein");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size muss zwischen 1 und " + MAX_PAGE_SIZE + " liegen");
		}
		if (sortFieldName != null && !access.isReadableByCurrentUser(sortFieldName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					sortFieldName + ": kein Feld, nach dem du in diesem Kern sortieren darfst");
		}
		SolrQuery query = queryWithCurrentUserCheck(access, fieldValues, requireWriteAclMatch);
		List<String> fieldNames = new ArrayList<>(access.readableFieldAndHookNamesOfCoreForCurrentUser());
		fieldNames.add(SolrDocumentService.VERSION_FIELD_NAME);
		query.setFields(fieldNames.toArray(String[]::new));
		query.setStart(page * size);
		query.setRows(size);
		if (sortFieldName != null) {
			query.addSort(sortFieldName, sortDescending ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc);
		}
		SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(DmsSolrDocumentFieldNames.CORE_KEY, query);
		return new DmsResolvedDocumentsPage(queried.totalFound(), page, size, withVersionAsText(queried.documents()));
	}

	/**
	 * Dokumente fürs Backend: Bedingungen des Backends, die genannten Felder, Lese-fq oder mit Schreibcheck allein das
	 * Schreib-fq. Höchstens MAX_BACKEND_ROWS Treffer, nach id sortiert. Geht nie an den Client.
	 */
	private List<Map<String, Object>> resolveBackendFields(JsonNode backendFieldValuesByFieldName,
			List<String> backendFieldNames, boolean requireWriteAclMatch) {
		SolrCurrentUserReadAndWriteAccessForCore access = access();
		Map<String, List<Object>> fieldValues = checkedConditions(access, null, backendFieldValuesByFieldName);
		SolrQuery query = queryWithCurrentUserCheck(access, fieldValues, requireWriteAclMatch);
		List<String> fieldNames = new ArrayList<>(backendFieldNames);
		if (!fieldNames.contains(SolrCore.ID_FIELD_NAME)) {
			fieldNames.add(0, SolrCore.ID_FIELD_NAME);
		}
		query.setFields(fieldNames.toArray(String[]::new));
		query.setRows(MAX_BACKEND_ROWS);
		query.addSort(SolrCore.ID_FIELD_NAME, SolrQuery.ORDER.asc);
		return solrDocumentService.queryDocumentsPage(DmsSolrDocumentFieldNames.CORE_KEY, query).documents();
	}

	/** Nur die ids aller Treffer, die der current user schreiben darf. 400, wenn es nicht weniger als 500 sind. */
	private List<String> resolveWritableIds(JsonNode clientFieldValuesByFieldName, JsonNode backendFieldValuesByFieldName) {
		SolrCurrentUserReadAndWriteAccessForCore access = access();
		Map<String, List<Object>> fieldValues = checkedConditions(access, clientFieldValuesByFieldName,
				backendFieldValuesByFieldName);
		SolrQuery query = queryWithCurrentUserCheck(access, fieldValues, true);
		query.setFields(SolrCore.ID_FIELD_NAME);
		query.setRows(ID_PAGE_SIZE);
		query.addSort(SolrCore.ID_FIELD_NAME, SolrQuery.ORDER.asc);
		List<String> ids = new ArrayList<>();
		for (int start = 0;; start += ID_PAGE_SIZE) {
			query.setStart(start);
			SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(DmsSolrDocumentFieldNames.CORE_KEY, query);
			if (queried.totalFound() >= MAX_DOCUMENTS_PER_DOCUMENT_GRANT) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, queried.totalFound()
						+ " Treffer, erlaubt sind weniger als " + MAX_DOCUMENTS_PER_DOCUMENT_GRANT
						+ ": ein weiterer Filter muss die Treffer enger fassen");
			}
			for (Map<String, Object> document : queried.documents()) {
				ids.add(String.valueOf(document.get(SolrCore.ID_FIELD_NAME)));
			}
			if (queried.documents().size() < ID_PAGE_SIZE || ids.size() >= queried.totalFound()) {
				return ids;
			}
		}
	}

	/**
	 * q=*:*, das Lese-fq des Users oder allein das Schreib-fq, der Status ACTIVE oder ARCHIVED, und je Feld ein fq für
	 * die Bedingungen
	 */
	private SolrQuery queryWithCurrentUserCheck(SolrCurrentUserReadAndWriteAccessForCore access,
			Map<String, List<Object>> fieldValues, boolean requireWriteAclMatch) {
		SolrQuery query = new SolrQuery("*:*");
		List<String> filterQueries = requireWriteAclMatch ? access.writeAclFilterQueries()
				: access.readAclAndGrantedAccessFilterQueries();
		for (String filterQuery : filterQueries) {
			query.addFilterQuery(filterQuery);
		}
		query.addFilterQuery(DmsStateService.VISIBLE_STATUS_FILTER_QUERY);
		int index = 0;
		for (Map.Entry<String, List<Object>> condition : fieldValues.entrySet()) {
			String fieldName = condition.getKey();
			SolrFieldType type = dmsSolrDocumentChecker.solrFieldTypeOf(fieldName);
			if (type != null && type.isText()) {
				// Textfelder als escaped Phrasen: der Analyzer läuft über die Eingabe wie über den Index
				List<String> phrases = new ArrayList<>();
				for (Object value : condition.getValue()) {
					phrases.add("\"" + ClientUtils.escapeQueryChars(value.toString()) + "\"");
				}
				query.addFilterQuery(fieldName + ":(" + String.join(" OR ", phrases) + ")");
			} else {
				// alles andere über den terms-Parser mit den Werten als eigenem Parameter: nie im Query-String
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
		return query;
	}

	/**
	 * Die Bedingungen des Clients gegen seine Auflöserechte, die des Backends nur gegen die Form, dann beide vereint.
	 * Nur erlaubte Namen gehen zum Checker, damit ein verborgenes Feld dieselbe Meldung bekommt wie ein unbekanntes.
	 */
	private Map<String, List<Object>> checkedConditions(SolrCurrentUserReadAndWriteAccessForCore access,
			JsonNode clientFieldValuesByFieldName, JsonNode backendFieldValuesByFieldName) {
		Map<String, List<Object>> fieldValues = new LinkedHashMap<>();
		if (clientFieldValuesByFieldName != null && !clientFieldValuesByFieldName.isNull()) {
			if (!clientFieldValuesByFieldName.isObject()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldValuesByFieldName ist kein JSON-Objekt");
			}
			List<String> problems = new ArrayList<>();
			ObjectNode allowed = JsonNodeFactory.instance.objectNode();
			for (Map.Entry<String, JsonNode> property : clientFieldValuesByFieldName.properties()) {
				if (access.isExactResolveAllowedForCurrentUser(property.getKey())) {
					allowed.set(property.getKey(), property.getValue());
				} else {
					problems.add(property.getKey() + ": kein Feld, nach dem du in diesem Kern auflösen darfst");
				}
			}
			if (!problems.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
			}
			fieldValues.putAll(dmsSolrDocumentChecker.checkExactResolveFieldValues(allowed));
		}
		if (backendFieldValuesByFieldName != null && !backendFieldValuesByFieldName.isNull()) {
			// dasselbe Feld in beiden: die Werte des Backends gelten, es engt ein, der Client weitet nicht
			fieldValues.putAll(dmsSolrDocumentChecker.checkExactResolveFieldValues(backendFieldValuesByFieldName));
		}
		if (fieldValues.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_CONDITION_MESSAGE);
		}
		return fieldValues;
	}

	/**
	 * _version_ ist ein long mit 19 Stellen, mehr als JavaScript genau darstellt (2^53): Als Zahl käme es verfälscht an.
	 * Zum Client geht es als Text. Es dient nur der Anzeige und später der Revision.
	 */
	private static List<Map<String, Object>> withVersionAsText(List<Map<String, Object>> documents) {
		List<Map<String, Object>> converted = new ArrayList<>(documents.size());
		for (Map<String, Object> document : documents) {
			Object version = document.get(SolrDocumentService.VERSION_FIELD_NAME);
			if (version == null) {
				converted.add(document);
				continue;
			}
			Map<String, Object> copy = new LinkedHashMap<>(document);
			copy.put(SolrDocumentService.VERSION_FIELD_NAME, version.toString());
			converted.add(copy);
		}
		return converted;
	}

	private static JsonNode idCondition(String id) {
		return backendCondition(SolrCore.ID_FIELD_NAME, id);
	}

	private static Map<String, Object> firstOrNull(List<Map<String, Object>> documents) {
		return documents.isEmpty() ? null : documents.get(0);
	}

}
