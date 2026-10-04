package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsDocumentLanguages;
import codergod1337.app1.dms.model.DmsSolrDocumentEditRequest;
import codergod1337.app1.dms.model.DmsSolrDocumentForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentGrantsForClient;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService;
import java.util.UUID;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService.SolrResolvedDocumentsPage;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Ein einzelnes DMS-Dokument lesen und bearbeiten.
 *
 * Lesen: das Dokument mit der Leseprüfung des current users, dazu das Flag, ob writeAcl eine seiner ARs enthält, damit
 * die Maske Bearbeiten und Löschen nur anbietet, wo es geht.
 *
 * Bearbeiten ist ein Schreibvorgang: Vor dem Atomic Update muss writeAcl eine AR des Users enthalten, sonst wird nichts
 * geschrieben. Geändert wird nur, was die Maske schickt: Dateiname (normalisiert wie beim Upload), Index, Beschreibung,
 * Erstelldatum, Dateiart (schreibt readAcl und writeAcl neu, braucht Schreibrolle an der neuen Dateiart und eine
 * freigeschaltete Endung), Hooks (ersetzt, geleert oder unberührt je Hook), Status (ein Status des Bereichs DMS;
 * DELETED ist das weiche Löschen) und Sprache (nur in Endungsgruppen mit Sprache, aus der Liste, leer löscht). Bei
 * jedem Schreiben kommt der current user in changingUsers und lastChangedAt auf
 * jetzt. Die Revision als ZIP vor dem Schreiben kommt später, bis dahin zählt fileIdRevisionNo nicht hoch.
 */
@Service
public class DmsSolrDocumentService {

	private static final Logger log = LoggerFactory.getLogger(DmsSolrDocumentService.class);

	private static final String FILE_NAME_FIELD_NAME = "fileName";
	private static final String FILE_INDEX_FIELD_NAME = "fileIndex";
	private static final String FILE_DESCRIPTION_FIELD_NAME = "fileDescription";
	private static final String FILE_EXTENSION_FIELD_NAME = "fileExtension";
	private static final String DOCUMENT_CREATED_AT_FIELD_NAME = "documentCreatedAt";
	private static final String CHANGING_USERS_FIELD_NAME = "changingUsers";
	private static final String LAST_CHANGED_AT_FIELD_NAME = "lastChangedAt";

	private final SolrExactFieldValueResolveService solrExactFieldValueResolveService;
	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final SolrDocumentService solrDocumentService;
	private final SolrHookService solrHookService;
	private final FileSubClassService fileSubClassService;
	private final DmsStateService dmsStateService;
	private final CurrentUsersProvider currentUsersProvider;
	private final DmsDocumentLanguageService dmsDocumentLanguageService;
	private final SolrDocumentGrantedDocsPerCoreService documentGrantService;
	private final DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService;

	public DmsSolrDocumentService(SolrExactFieldValueResolveService solrExactFieldValueResolveService,
			SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService, SolrDocumentService solrDocumentService,
			SolrHookService solrHookService, FileSubClassService fileSubClassService, DmsStateService dmsStateService,
			CurrentUsersProvider currentUsersProvider, DmsDocumentLanguageService dmsDocumentLanguageService,
			SolrDocumentGrantedDocsPerCoreService documentGrantService,
			DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService) {
		this.dmsDocumentLanguageService = dmsDocumentLanguageService;
		this.documentGrantService = documentGrantService;
		this.dmsCurrentUsersGrantsService = dmsCurrentUsersGrantsService;
		this.solrExactFieldValueResolveService = solrExactFieldValueResolveService;
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.solrDocumentService = solrDocumentService;
		this.solrHookService = solrHookService;
		this.fileSubClassService = fileSubClassService;
		this.dmsStateService = dmsStateService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/** Das Dokument mit der Leseprüfung des current users und dem Schreib-Flag. 404, wenn es keins gibt oder er es nicht sehen darf. */
	@PreAuthorize("hasRole('DMS')")
	public DmsSolrDocumentForClient loadDmsSolrDocumentForClient(String id) {
		SolrResolvedDocumentsPage resolved = solrExactFieldValueResolveService.resolveDocumentsPageByExactFieldValues(
				DmsUploadService.CORE_KEY, idCondition(id), 0, 1, null, false);
		if (resolved.documents().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit dieser id");
		}
		boolean writeAclMatch = solrCurrentUserCoreAccessService
				.hasWriteAclMatchOfCurrentUserForDocument(DmsUploadService.CORE_KEY, id);
		return new DmsSolrDocumentForClient(resolved.documents().get(0), writeAclMatch);
	}

	/**
	 * Der Kontrollblick auf die Freigaben eines Dokuments (Vorgabe 2026-10-04): die guids aus userGrantedAccess, gelesen
	 * fürs Backend mit der Leseprüfung des Users, dazu die wirksamen Dateifreigaben dieser User im Kern DMS und ihre
	 * Namen. Vorerst ADMIN, später weitere Rollen über denselben Endpunkt. 404, wenn er das Dokument nicht sehen darf.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public DmsSolrDocumentGrantsForClient loadGrantsOfDmsSolrDocument(String id) {
		Map<String, Object> document = solrExactFieldValueResolveService
				.loadDocumentForBackendWithReadAclAndGrantedAccessCheck(DmsUploadService.CORE_KEY, id,
						List.of(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME));
		if (document == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit dieser id");
		}
		Object granted = document.get(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME);
		List<String> grantedUsersGuids = new ArrayList<>();
		List<UUID> parsed = new ArrayList<>();
		for (Object value : granted instanceof List<?> list ? list : granted == null ? List.of() : List.of(granted)) {
			String text = value.toString();
			grantedUsersGuids.add(text);
			try {
				parsed.add(UUID.fromString(text));
			} catch (IllegalArgumentException ignored) {
				// ein Eintrag, der keine guid ist, bleibt als Text stehen und hat kein Register
			}
		}
		List<SolrDocumentGrantedDocsPerCore> inForce = parsed.isEmpty() ? List.of()
				: documentGrantService.documentGrantsInForceOfUsersInCore(parsed, DmsUploadService.CORE_KEY);
		return new DmsSolrDocumentGrantsForClient(grantedUsersGuids, inForce,
				dmsCurrentUsersGrantsService.usersDisplayNameByGuid(parsed));
	}

	/**
	 * Ändert, was die Maske schickt, per Atomic Update. 403 ohne writeAcl-Match, 404 ohne Dokument, 400 bei
	 * ungültigen Werten, alle Beanstandungen in einer Antwort. Zurück kommt das Dokument, wie es jetzt steht.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsSolrDocumentForClient editDmsSolrDocument(DmsSolrDocumentEditRequest editRequest) {
		String id = editRequest.id();
		solrCurrentUserCoreAccessService.requireWriteAclMatchOfCurrentUserForDocument(DmsUploadService.CORE_KEY, id);
		Map<String, Object> existing = solrExactFieldValueResolveService
				.loadDocumentForBackendWithReadAclAndGrantedAccessCheck(DmsUploadService.CORE_KEY, id,
						List.of(FILE_EXTENSION_FIELD_NAME, DmsUploadService.FILE_SUB_CLASS_FIELD_NAME,
								DmsUploadService.STATUS_FIELD_NAME));
		if (existing == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit dieser id");
		}
		String fileExtension = text(existing, FILE_EXTENSION_FIELD_NAME);

		List<String> problems = new ArrayList<>();
		Map<String, Map<String, Object>> changes = new LinkedHashMap<>();

		if (editRequest.fileName() != null) {
			if (editRequest.fileName().isBlank()) {
				problems.add("fileName darf nicht leer sein");
			} else {
				changes.put(FILE_NAME_FIELD_NAME, set(DmsUploadService.normalizeFileName(editRequest.fileName().strip(),
						fileExtension == null ? "" : fileExtension)));
			}
		}
		if (editRequest.fileIndex() != null) {
			changes.put(FILE_INDEX_FIELD_NAME, set(blankToNull(editRequest.fileIndex())));
		}
		if (editRequest.fileDescription() != null) {
			changes.put(FILE_DESCRIPTION_FIELD_NAME, set(blankToNull(editRequest.fileDescription())));
		}
		if (editRequest.documentCreatedAt() != null) {
			Instant createdAt = parseInstant(editRequest.documentCreatedAt());
			if (createdAt == null) {
				problems.add("documentCreatedAt ist kein Zeitpunkt nach ISO 8601");
			} else {
				changes.put(DOCUMENT_CREATED_AT_FIELD_NAME, set(createdAt));
			}
		}
		if (editRequest.status() != null) {
			if (!dmsStateService.isDmsState(editRequest.status())) {
				problems.add("status " + editRequest.status() + " ist kein Status des Bereichs " + DmsStateService.SCOPE);
			} else {
				changes.put(DmsUploadService.STATUS_FIELD_NAME, set(editRequest.status()));
			}
		}
		if (editRequest.documentLanguage() != null) {
			// leer löscht die Sprache; ob die Endungsgruppe eine vorsieht und der Code aus der Liste ist, prüft der Service
			String code = blankToNull(editRequest.documentLanguage());
			String problem = dmsDocumentLanguageService.problemWithDocumentLanguage(code, fileExtension);
			if (problem != null) {
				problems.add(problem);
			} else {
				changes.put(DmsUploadService.DOCUMENT_LANGUAGE_FIELD_NAME,
						set(code == null ? null : DmsDocumentLanguages.normalizeCode(code)));
			}
		}
		if (editRequest.fileSubClassKey() != null) {
			changeFileSubClass(editRequest.fileSubClassKey(), fileExtension, changes, problems);
		}
		if (editRequest.hookValuesByHookKey() != null) {
			changeHooks(editRequest.hookValuesByHookKey(), changes, problems);
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		if (changes.isEmpty()) {
			return loadDmsSolrDocumentForClient(id);
		}

		String usersGuid = currentUsersProvider.getCurrentUsersGuid().toString();
		changes.put(CHANGING_USERS_FIELD_NAME, Map.of("add-distinct", usersGuid));
		changes.put(LAST_CHANGED_AT_FIELD_NAME, set(Instant.now()));
		solrDocumentService.atomicUpdate(DmsUploadService.CORE_KEY, id, changes);
		log.info("DMS-Dokument {} bearbeitet von {}: {}", id, usersGuid, changes.keySet());
		return loadDmsSolrDocumentForClient(id);
	}

	/**
	 * Die neue Dateiart: muss es geben, die Endung des Dokuments muss bei ihr freigeschaltet sein, und der User braucht
	 * ihre Schreibrolle, sonst sperrte er sich selbst aus. readAcl und writeAcl des Dokuments folgen der neuen Dateiart.
	 */
	private void changeFileSubClass(String fileSubClassKey, String fileExtension,
			Map<String, Map<String, Object>> changes, List<String> problems) {
		FileSubClass fileSubClass = fileSubClassService.getFileSubClassByKey(fileSubClassKey);
		List<String> allowedExtensions = fileSubClass.getExtensions();
		if (allowedExtensions == null || fileExtension == null || !allowedExtensions.contains(fileExtension)) {
			problems.add("die Endung " + (fileExtension == null ? "(keine)" : fileExtension) + " ist bei der Dateiart "
					+ fileSubClassKey + " nicht freigeschaltet");
			return;
		}
		List<String> writeAccessRoleKeys = fileSubClass.getWriteAccessRoleKeys();
		List<String> own = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (writeAccessRoleKeys == null || writeAccessRoleKeys.stream().noneMatch(own::contains)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"kein Schreibrecht an der Dateiart " + fileSubClassKey);
		}
		changes.put(DmsUploadService.FILE_SUB_CLASS_FIELD_NAME, set(fileSubClassKey));
		changes.put(SolrCurrentUserCoreAccessService.READ_ACL_FIELD_NAME,
				set(copyOf(fileSubClass.getReadAccessRoleKeys())));
		changes.put(SolrCurrentUserCoreAccessService.WRITE_ACL_FIELD_NAME, set(copyOf(writeAccessRoleKeys)));
	}

	/** Je Hook im Objekt: Werteliste ersetzt, leere Liste leert. Ein unbekannter Hook ist ein Fehler. */
	private void changeHooks(JsonNode hookValuesByHookKey, Map<String, Map<String, Object>> changes,
			List<String> problems) {
		if (!hookValuesByHookKey.isObject()) {
			problems.add("hookValuesByHookKey ist kein JSON-Objekt");
			return;
		}
		Set<String> hookKeys = new LinkedHashSet<>();
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			hookKeys.add(solrHook.getKey());
		}
		for (Map.Entry<String, JsonNode> property : hookValuesByHookKey.properties()) {
			String hookKey = property.getKey();
			if (!hookKeys.contains(hookKey)) {
				problems.add(hookKey + ": kein Hook");
				continue;
			}
			JsonNode values = property.getValue();
			if (values == null || values.isNull()) {
				changes.put(hookKey, set(null));
				continue;
			}
			if (!values.isArray()) {
				problems.add(hookKey + ": erwartet eine Liste");
				continue;
			}
			Set<String> distinct = new LinkedHashSet<>();
			for (JsonNode value : (ArrayNode) values) {
				String text = value.isString() ? value.asString().strip() : value.toString();
				if (!text.isEmpty()) {
					distinct.add(text);
				}
			}
			changes.put(hookKey, set(distinct.isEmpty() ? null : List.copyOf(distinct)));
		}
	}

	/** Die Bedingung id gleich dieser id, in der Form des Auflösens */
	private static ObjectNode idCondition(String id) {
		ObjectNode condition = JsonNodeFactory.instance.objectNode();
		condition.set(SolrDocumentService.ID_FIELD_NAME, JsonNodeFactory.instance.arrayNode(1).add(id));
		return condition;
	}

	/** Der Modifier set, auch mit null: Dann nimmt Solr das Feld aus dem Dokument. */
	private static Map<String, Object> set(Object value) {
		return Collections.singletonMap("set", value);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static List<String> copyOf(List<String> list) {
		return list == null ? List.of() : new ArrayList<>(list);
	}

	private static String text(Map<String, Object> document, String name) {
		Object value = document.get(name);
		return value == null ? null : value.toString();
	}

	/** ISO 8601 mit Z oder Versatz, dazu ein nacktes Datum JJJJ-MM-TT als Mitternacht UTC, sonst null */
	private static Instant parseInstant(String text) {
		String trimmed = text.strip();
		try {
			return Instant.parse(trimmed);
		} catch (DateTimeParseException ignored) {
			// weiter
		}
		try {
			return OffsetDateTime.parse(trimmed).toInstant();
		} catch (DateTimeParseException ignored) {
			// weiter
		}
		try {
			return Instant.parse(trimmed + "T00:00:00Z");
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

}
