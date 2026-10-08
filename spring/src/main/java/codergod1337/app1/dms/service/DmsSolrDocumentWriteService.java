package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsDocumentLanguages;
import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.dms.model.DmsSolrDocumentForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentPatchRequest;
import codergod1337.app1.dms.model.DmsSolrDocumentUploadFacts;
import codergod1337.app1.dms.model.DmsSolrDocumentWriteProcess;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.document.model.SolrDocumentFieldChange;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldClientWriteRule;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Der eine Schreibservice des Kerns DMS (Vorgabe 2026-10-07): je Schreibvorgang eine Methode, benannt nach dem Vorgang,
 * nichts Allgemeines. Kein anderer Service schreibt in den Kern DMS. Er baut seine Updates selbst und ruft die rohe
 * Solr-Schicht direkt, es gibt keine gemeinsame Schreibmechanik für alle Kerne. Zwei Fälle, sonst nichts: ein neues
 * Dokument anlegen, oder ein vorhandenes per Atomic Update ändern, eins oder viele. Ein vorhandenes Dokument wird nie
 * voll neu geschrieben.
 *
 * Vor jedem Schreiben prüft dieser Service selbst:
 * <ul>
 * <li>Anlegen: Die id darf es noch nicht geben (Existenzcheck). Das Recht kommt aus der Schreibrolle der Dateiart.
 * Anlegen braucht kein Lesen.</li>
 * <li>Ändern: Das Auflösen nach id allein mit writeAcl muss das Dokument finden, sonst 403 (Schreiben impliziert kein
 * Lesen). Bei vielen Dokumenten werden die ids noch einmal gegen writeAcl aufgelöst, geschrieben nur die Treffer.</li>
 * <li>Die Schreibregel je Feld aus den Stammdaten ({@link SolrFieldClientWriteRule}): Felder des Clients, die er nicht
 * setzen darf, werden ignoriert, nicht abgelehnt. Felder des Backends laufen nicht durch die Regel. Das Backend setzt
 * nach dem Client, ein mitgeschickter Wert wird überschrieben.</li>
 * <li>Die Form über den {@link DmsSolrDocumentChecker}: Feld im Kern, Operation passt zu einwertig oder Liste, Werte
 * passen zum Typ.</li>
 * </ul>
 * Die Regeln des DMS: readAcl und writeAcl werden genau dann geschrieben, wenn fileSubClassKey geschrieben wird, beim
 * Anlegen und beim Dateiartwechsel, immer aus der Dateiart in Postgres, sonst nie. Jeder Schreibvorgang eines Menschen
 * trägt changingUsers ADD und lastChangedAt SET. Eine Versionskette hat durchgehend dieselbe Dateiart. Merge: nur Hooks
 * ADD und die Beschreibung angehängt. {@code _version_} geht beim Schreiben nie mit, keine Konfliktprüfung. Jeder
 * Vorgang steht mit seinem Namen ({@link DmsSolrDocumentWriteProcess}), current user, id und Feldnamen im Log.
 *
 * Die Revision (gebaut 2026-10-08): Ob ein Vorgang revisioniert wird, sagt der Vorgang selbst
 * ({@link DmsSolrDocumentWriteProcess#isRevisioned()}), beide Update-Methoden fragen ihn. Ein revisionierter Vorgang
 * lädt mit dem Schreib-fq das ganze Dokument, behält nur die Änderungen, die gegen diesen Stand wirklich etwas ändern
 * (eine Änderung ohne Änderung ist keine, dann gibt es weder Revision noch Schreiben), sichert den Stand über den
 * {@link DmsSolrDocumentRevisionService} als ZIP auf dem SFTP samt Registerzeile mit genau diesen Feldnamen, hängt
 * fileIdRevisionNo INC an dieselbe Änderungsliste und schreibt Solr. Scheitert das ZIP, scheitert die Änderung. Ein
 * Update vieler Dokumente mit revisioniertem Vorgang läuft je Dokument einzeln, jedes mit seinem eigenen ZIP.
 */
@Service
public class DmsSolrDocumentWriteService {

	private static final Logger log = LoggerFactory.getLogger(DmsSolrDocumentWriteService.class);

	/** Ein nackter Tag JJJJ-MM-TT, wie das Datumsfeld der Maske ihn schickt: wird Mitternacht UTC */
	private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	/** Trenner beim Anhängen einer Beschreibung im Merge */
	private static final String DESCRIPTION_SEPARATOR = " | ";

	/**
	 * Die AR der Internetfreigabe (User 2026-10-08): isPublic setzt und ändert nur, wer sie hat, in beide Richtungen,
	 * beim Upload, beim Bearbeiten und beim Zurückladen. Der Admin legt sie an wie jede AR.
	 */
	public static final String PUBLIC_ACCESS_ROLE_KEY = "DMS_PUBLIC";

	/**
	 * Was das Zurückladen nie aus dem ZIP nimmt, weil es technisch nicht kann (User 2026-10-08, alles andere wörtlich):
	 * id ist dieselbe, _version_ vergibt Solr bei jedem Schreiben selbst, fileIdRevisionNo zählt hoch wie bei jedem
	 * Schreiben, changingUsers und lastChangedAt setzt das Zurückladen als Stempel seiner eigenen Handlung.
	 */
	private static final Set<String> NOT_FROM_REVISION = Set.of(SolrCore.ID_FIELD_NAME,
			SolrDocumentService.VERSION_FIELD_NAME, DmsSolrDocumentFieldNames.FILE_ID_REVISION_NO,
			DmsSolrDocumentFieldNames.CHANGING_USERS, DmsSolrDocumentFieldNames.LAST_CHANGED_AT);

	/**
	 * Der Stempel jedes Schreibens eines Menschen: Er geht immer mit, zählt aber nicht als geändertes Feld im Register
	 * und entscheidet nicht, ob überhaupt etwas zu schreiben ist.
	 */
	private static final Set<String> STAMP_FIELD_NAMES = Set.of(DmsSolrDocumentFieldNames.CHANGING_USERS,
			DmsSolrDocumentFieldNames.LAST_CHANGED_AT);

	private final SolrDocumentService solrDocumentService;
	private final DmsSolrDocumentChecker dmsSolrDocumentChecker;
	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final DmsSolrDocumentExistenceService dmsSolrDocumentExistenceService;
	private final DmsSolrDocumentRevisionService dmsSolrDocumentRevisionService;
	private final SolrFieldService solrFieldService;
	private final SolrHookService solrHookService;
	private final FileSubClassService fileSubClassService;
	private final DmsStateService dmsStateService;
	private final DmsDocumentLanguageService dmsDocumentLanguageService;
	private final CurrentUsersProvider currentUsersProvider;
	private final ObjectMapper objectMapper;

	public DmsSolrDocumentWriteService(SolrDocumentService solrDocumentService,
			DmsSolrDocumentChecker dmsSolrDocumentChecker,
			DmsExactFieldValueResolveService dmsExactFieldValueResolveService,
			DmsSolrDocumentExistenceService dmsSolrDocumentExistenceService,
			DmsSolrDocumentRevisionService dmsSolrDocumentRevisionService, SolrFieldService solrFieldService,
			SolrHookService solrHookService, FileSubClassService fileSubClassService, DmsStateService dmsStateService,
			DmsDocumentLanguageService dmsDocumentLanguageService, CurrentUsersProvider currentUsersProvider,
			ObjectMapper objectMapper) {
		this.solrDocumentService = solrDocumentService;
		this.dmsSolrDocumentChecker = dmsSolrDocumentChecker;
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.dmsSolrDocumentExistenceService = dmsSolrDocumentExistenceService;
		this.dmsSolrDocumentRevisionService = dmsSolrDocumentRevisionService;
		this.solrFieldService = solrFieldService;
		this.solrHookService = solrHookService;
		this.fileSubClassService = fileSubClassService;
		this.dmsStateService = dmsStateService;
		this.dmsDocumentLanguageService = dmsDocumentLanguageService;
		this.currentUsersProvider = currentUsersProvider;
		this.objectMapper = objectMapper;
	}

	// ===== Die Schreibregel je Feld =====

	/**
	 * Das JSON des Clients ohne die Felder, die er nicht setzen darf: beim Anlegen alles außer BACKEND_ONLY, beim
	 * Bearbeiten nur CLIENT_SETS_AND_CHANGES. Hooks darf er immer, die eingebauten Felder nie. Ein Feld, das es im Kern
	 * nicht gibt, bleibt stehen, damit die Formprüfung es nennt. Was wegfällt, steht im Log, der Client erfährt nichts:
	 * Ignorieren, nicht ablehnen. Der Upload ruft das vor seiner Vorarbeit, damit Probe und Upload dasselbe sehen.
	 */
	public ObjectNode removeFieldsTheClientMayNotSet(JsonNode clientFieldValuesByFieldName, boolean creating) {
		ObjectNode allowed = JsonNodeFactory.instance.objectNode();
		if (clientFieldValuesByFieldName == null || !clientFieldValuesByFieldName.isObject()) {
			return allowed;
		}
		Map<String, SolrFieldClientWriteRule> rules = clientWriteRules();
		List<String> ignored = new ArrayList<>();
		for (Map.Entry<String, JsonNode> property : clientFieldValuesByFieldName.properties()) {
			SolrFieldClientWriteRule rule = rules.get(property.getKey());
			boolean clientMay = rule == null
					|| (creating ? rule.isClientAllowedToSetOnCreate() : rule.isClientAllowedToChangeOnUpdate());
			if (clientMay) {
				allowed.set(property.getKey(), property.getValue());
			} else {
				ignored.add(property.getKey());
			}
		}
		if (!ignored.isEmpty()) {
			log.debug("Kern DMS: Felder des Clients ignoriert, Schreibregel: {}", ignored);
		}
		return allowed;
	}

	// ===== Anlegen =====

	/**
	 * Upload neu: das ganze Dokument. Die Dateiart kommt vom Client und ist Pflicht, ihre Endungen müssen die Datei
	 * zulassen, der User braucht ihre Schreibrolle. readAcl und writeAcl aus der Dateiart, versionChain neu, version 1.
	 *
	 * @return die id des neuen Dokuments
	 */
	@PreAuthorize("hasRole('DMS')")
	public String createDmsSolrDocumentFromUpload(DmsSolrDocumentUploadFacts facts, JsonNode clientFieldValuesByFieldName) {
		String fileSubClassKey = text(clientFieldValuesByFieldName, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY);
		if (fileSubClassKey == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY + " fehlt");
		}
		FileSubClass fileSubClass = fileSubClassService.getFileSubClassByKey(fileSubClassKey);
		requireExtensionAllowedByFileSubClass(fileSubClass, facts.fileExtension());
		requireWriteRoleOfFileSubClass(fileSubClass);
		requireClientStatusValid(clientFieldValuesByFieldName);
		requireClientPublicAccessAllowed(clientFieldValuesByFieldName);
		createDmsSolrDocument(DmsSolrDocumentWriteProcess.DMS_UPLOAD_CREATE, clientFieldValuesByFieldName,
				newDocumentBackendFieldValues(facts, fileSubClass, UUID.randomUUID().toString(), 1L,
						clientFieldValuesByFieldName));
		return facts.sha256();
	}

	/**
	 * Neue Fassung einer Versionskette: Der Upload reicht die Kette als Parameter, kein Feld des Clients (2026-10-08),
	 * das Backend löst sie mit der Lesegrenze des Users auf, unbekannt und unsichtbar sehen gleich aus (404). Die
	 * Dateiart kommt vom Kopf der Kette, nie vom
	 * Client, der User braucht ihre Schreibrolle. Vor dem Anlegen muss er jedes ACTIVE-Glied, das er sieht, auch
	 * schreiben dürfen, sonst 403, bevor etwas geschrieben ist. Die neue Fassung bekommt version plus eins und ACTIVE,
	 * danach wird jedes ACTIVE-Glied der Kette auf ARCHIVED gesetzt, je Glied mit Revision. Scheitert ein Archivieren
	 * trotzdem, hat die Kette zwei ACTIVE: Das steht im Log, die neue Fassung bleibt.
	 *
	 * @return die id der neuen Fassung
	 */
	@PreAuthorize("hasRole('DMS')")
	public String createNextVersionInVersionChain(String versionChain, DmsSolrDocumentUploadFacts facts,
			JsonNode clientFieldValuesByFieldName) {
		requireUuid(versionChain);
		List<Map<String, Object>> members = dmsExactFieldValueResolveService.loadDmsVersionChainMembers(versionChain);
		if (members.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Versionskette mit dieser versionChain");
		}
		Map<String, Object> head = null;
		long highestVersion = 0;
		for (Map<String, Object> member : members) {
			long version = number(member, DmsSolrDocumentFieldNames.VERSION);
			if (head == null || version > highestVersion) {
				head = member;
				highestVersion = version;
			}
		}
		String fileSubClassKey = text(head, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY);
		if (fileSubClassKey == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "der Kopf der Kette hat keine Dateiart");
		}
		FileSubClass fileSubClass = fileSubClassService.getFileSubClassByKey(fileSubClassKey);
		requireExtensionAllowedByFileSubClass(fileSubClass, facts.fileExtension());
		requireWriteRoleOfFileSubClass(fileSubClass);
		requireClientStatusValid(clientFieldValuesByFieldName);
		requireClientPublicAccessAllowed(clientFieldValuesByFieldName);
		requireActiveMembersWritable(members, facts.sha256());
		createDmsSolrDocument(DmsSolrDocumentWriteProcess.DMS_NEW_VERSION_CREATE, clientFieldValuesByFieldName,
				newDocumentBackendFieldValues(facts, fileSubClass, versionChain, highestVersion + 1,
						clientFieldValuesByFieldName));

		for (Map<String, Object> member : members) {
			String memberId = text(member, SolrCore.ID_FIELD_NAME);
			if (!DmsStateService.ACTIVE.equals(text(member, DmsSolrDocumentFieldNames.STATUS))
					|| facts.sha256().equals(memberId)) {
				continue;
			}
			List<SolrDocumentFieldChange> archive = new ArrayList<>();
			archive.add(SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.STATUS, DmsStateService.ARCHIVED));
			archive.addAll(changedByCurrentUser());
			try {
				updateDmsSolrDocument(DmsSolrDocumentWriteProcess.DMS_NEW_VERSION_ARCHIVE_PREVIOUS, memberId, List.of(),
						archive);
			} catch (RuntimeException e) {
				log.error("Neue Fassung {} der Kette {}: das Glied {} ließ sich nicht archivieren, die Kette hat zwei ACTIVE: {}",
						facts.sha256(), versionChain, memberId, e.getMessage());
			}
		}
		return facts.sha256();
	}

	/**
	 * Vor dem Anlegen einer neuen Fassung (nach requireVorgaengerArchivierbar der Vorlage): Jedes ACTIVE-Glied, das der
	 * User sieht, muss er auch schreiben dürfen, denn gleich danach setzt er es auf ARCHIVED. Fehlt das Schreibrecht,
	 * kommt die Absage hier, bevor etwas geschrieben ist, statt danach mit zwei ACTIVE in der Kette. Ein ACTIVE-Glied,
	 * das der User nicht sieht, kennt die Kette hier nicht: Es bliebe ACTIVE, das steht dann im Log des Archivierens.
	 */
	private void requireActiveMembersWritable(List<Map<String, Object>> members, String newId) {
		List<String> activeIds = new ArrayList<>();
		for (Map<String, Object> member : members) {
			String memberId = text(member, SolrCore.ID_FIELD_NAME);
			if (memberId != null && !memberId.equals(newId)
					&& DmsStateService.ACTIVE.equals(text(member, DmsSolrDocumentFieldNames.STATUS))) {
				activeIds.add(memberId);
			}
		}
		if (activeIds.isEmpty()) {
			return;
		}
		Set<String> writableIds = Set.copyOf(dmsExactFieldValueResolveService.resolveWritableDmsSolrDocumentIdsAmong(activeIds));
		for (Map<String, Object> member : members) {
			String memberId = text(member, SolrCore.ID_FIELD_NAME);
			if (activeIds.contains(memberId) && !writableIds.contains(memberId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "die geltende Fassung v"
						+ number(member, DmsSolrDocumentFieldNames.VERSION) + " (id " + memberId
						+ ") ist für dich nicht schreibbar, sie kann nicht archiviert werden");
			}
		}
	}

	// ===== Ändern =====

	/**
	 * Dieselben Bytes noch einmal hochgeladen: nur bei gleicher Dateiart und Status ACTIVE oder ARCHIVED, mit
	 * Schreibcheck. Kommt der Upload mit versionChain, muss es die Kette des vorhandenen Dokuments sein. Ergänzt werden
	 * nur die Hooks (ADD, ohne Doppelte) und die Beschreibung (je Sprache hinten angehängt, wenn nicht identisch), dazu
	 * changingUsers und lastChangedAt, und das nur, wenn tatsächlich etwas ergänzt wurde. fileIndex wird nicht gemergt:
	 * ein anderer Index als der des Originals lehnt ab. Alles andere bleibt, auch readAcl und writeAcl.
	 *
	 * @return true, wenn etwas ergänzt und geschrieben wurde
	 */
	@PreAuthorize("hasRole('DMS')")
	public boolean mergeHookValuesFromRepeatedUpload(String id, JsonNode clientFieldValuesByFieldName) {
		Map<String, Object> existing = dmsExactFieldValueResolveService.loadDmsSolrDocumentFieldsForWrite(id,
				List.of(DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY, DmsSolrDocumentFieldNames.STATUS,
						DmsSolrDocumentFieldNames.FILE_INDEX, DmsSolrDocumentFieldNames.FILE_DESCRIPTION,
						DmsSolrDocumentFieldNames.VERSION_CHAIN));
		if (existing == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Schreibrecht an diesem Dokument");
		}
		String status = text(existing, DmsSolrDocumentFieldNames.STATUS);
		if (!DmsStateService.ACTIVE.equals(status) && !DmsStateService.ARCHIVED.equals(status)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "das Dokument hat den Status " + status + ", kein Merge");
		}
		String sentFileSubClassKey = text(clientFieldValuesByFieldName, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY);
		String existingFileSubClassKey = text(existing, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY);
		if (sentFileSubClassKey != null && !sentFileSubClassKey.equals(existingFileSubClassKey)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"die Bytes liegen unter der Dateiart " + existingFileSubClassKey + ", kein Merge");
		}
		String sentVersionChain = text(clientFieldValuesByFieldName, DmsSolrDocumentFieldNames.VERSION_CHAIN);
		if (sentVersionChain != null && !sentVersionChain.equals(text(existing, DmsSolrDocumentFieldNames.VERSION_CHAIN))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"die Bytes liegen schon in einer anderen Versionskette, falsche versionChain");
		}
		String sentFileIndex = text(clientFieldValuesByFieldName, DmsSolrDocumentFieldNames.FILE_INDEX);
		String existingFileIndex = text(existing, DmsSolrDocumentFieldNames.FILE_INDEX);
		if (sentFileIndex != null && existingFileIndex != null && !sentFileIndex.equals(existingFileIndex)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileIndex " + sentFileIndex
					+ " passt nicht zum vorhandenen Dokument (" + existingFileIndex + "), kein Merge");
		}

		List<SolrDocumentFieldChange> additions = new ArrayList<>();
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			JsonNode values = clientFieldValuesByFieldName.get(solrHook.getKey());
			if (values != null && values.isArray()) {
				Set<String> distinct = new LinkedHashSet<>();
				for (JsonNode value : values) {
					String valueText = value.isString() ? value.asString().strip() : value.toString();
					if (!valueText.isEmpty()) {
						distinct.add(valueText);
					}
				}
				if (!distinct.isEmpty()) {
					additions.add(SolrDocumentFieldChange.add(solrHook.getKey(), distinct));
				}
			}
		}
		String appended = appendedDescription(text(existing, DmsSolrDocumentFieldNames.FILE_DESCRIPTION),
				text(clientFieldValuesByFieldName, DmsSolrDocumentFieldNames.FILE_DESCRIPTION));
		if (appended != null) {
			additions.add(SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.FILE_DESCRIPTION, appended));
		}
		if (additions.isEmpty()) {
			log.info("Merge {}: nichts zu ergänzen", id);
			return false;
		}
		return updateDmsSolrDocument(DmsSolrDocumentWriteProcess.DMS_UPLOAD_MERGE_HOOKS, id, additions,
				changedByCurrentUser());
	}

	/**
	 * Die Bearbeiten-Maske: nur die geschickten Felder, je Feld der Endwert, Listen als SET_ALL. Darüber die Regeln des
	 * DMS: Dateiname normalisiert wie beim Upload, Status aus dem Bereich DMS, Sprache nach der Endungsgruppe,
	 * Erstelldatum auch als nackter Tag, neue Dateiart nur mit freigeschalteter Endung und ihrer Schreibrolle, dann
	 * readAcl und writeAcl neu aus ihr, für jedes Glied der Kette. 403 ohne Schreibrecht, 400 bei Werten, die eine Regel
	 * verletzen, alle Beanstandungen in einer Antwort.
	 *
	 * @return das Dokument, wie es jetzt steht
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsSolrDocumentForClient editDmsSolrDocument(DmsSolrDocumentPatchRequest patch) {
		String id = patch.id();
		Map<String, Object> existing = dmsExactFieldValueResolveService.loadDmsSolrDocumentFieldsForWrite(id,
				List.of(DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY, DmsSolrDocumentFieldNames.FILE_EXTENSION,
						DmsSolrDocumentFieldNames.VERSION_CHAIN));
		if (existing == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Schreibrecht an diesem Dokument");
		}
		String fileExtension = text(existing, DmsSolrDocumentFieldNames.FILE_EXTENSION);
		List<SolrDocumentFieldChange> clientChanges = dmsSolrDocumentChecker.checkPatchFieldValues(
				removeFieldsTheClientMayNotSet(withDayAsInstant(patch.fieldValuesByFieldName()), false));

		List<String> problems = new ArrayList<>();
		List<SolrDocumentFieldChange> checked = new ArrayList<>();
		List<SolrDocumentFieldChange> backendChanges = new ArrayList<>(changedByCurrentUser());
		FileSubClass newFileSubClass = null;
		for (SolrDocumentFieldChange change : clientChanges) {
			String value = change.values().isEmpty() ? null : change.values().get(0).toString();
			switch (change.fieldName()) {
				case DmsSolrDocumentFieldNames.FILE_NAME -> {
					if (value == null) {
						problems.add("fileName darf nicht leer sein");
					} else {
						checked.add(SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.FILE_NAME,
								DmsUploadService.normalizeFileName(value, fileExtension == null ? "" : fileExtension)));
					}
				}
				case DmsSolrDocumentFieldNames.STATUS -> {
					if (value == null || !dmsStateService.isDmsState(value)) {
						problems.add("status " + value + " ist kein Status des Bereichs " + DmsStateService.SCOPE);
					} else {
						checked.add(change);
					}
				}
				case DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE -> {
					String problem = dmsDocumentLanguageService.problemWithDocumentLanguage(value, fileExtension);
					if (problem != null) {
						problems.add(problem);
					} else {
						checked.add(SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE,
								value == null ? null : DmsDocumentLanguages.normalizeCode(value)));
					}
				}
				case DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY -> {
					if (value == null) {
						problems.add("fileSubClassKey darf nicht leer sein");
					} else if (!value.equals(text(existing, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY))) {
						newFileSubClass = fileSubClassService.getFileSubClassByKey(value);
						if (!isExtensionAllowedByFileSubClass(newFileSubClass, fileExtension)) {
							problems.add("die Endung " + (fileExtension == null ? "(keine)" : fileExtension)
									+ " ist bei der Dateiart " + value + " nicht freigeschaltet");
						} else {
							requireWriteRoleOfFileSubClass(newFileSubClass);
							checked.add(change);
							backendChanges.addAll(accessControlListsOf(newFileSubClass));
						}
					}
				}
				case DmsSolrDocumentFieldNames.IS_PUBLIC -> {
					requireCurrentUserMayChangePublicAccess();
					checked.add(change);
				}
				default -> checked.add(change);
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		updateDmsSolrDocument(DmsSolrDocumentWriteProcess.DMS_EDIT, id, checked, backendChanges);

		if (newFileSubClass != null) {
			changeFileSubClassOfOtherChainMembers(id, text(existing, DmsSolrDocumentFieldNames.VERSION_CHAIN),
					newFileSubClass);
		}
		return dmsExactFieldValueResolveService.loadDmsSolrDocumentForDocumentPage(id);
	}

	/**
	 * Weiches Löschen: status DELETED, sonst nichts. 403 ohne Schreibrecht. Danach gibt es das Dokument für User auf
	 * keinem Weg mehr (User 2026-10-08), deshalb kommt nichts zurück; zurückholen kann es der Admin im DMS-Manager.
	 */
	@PreAuthorize("hasRole('DMS')")
	public void softDeleteDmsSolrDocument(String id) {
		List<SolrDocumentFieldChange> changes = new ArrayList<>();
		changes.add(SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.STATUS, DmsStateService.DELETED));
		changes.addAll(changedByCurrentUser());
		updateDmsSolrDocument(DmsSolrDocumentWriteProcess.DMS_SOFT_DELETE, id, List.of(), changes);
	}

	/**
	 * Eine Revision zurückladen (User 2026-10-08): jedes Feld aus dem ZIP wörtlich zurück, als Atomic Update, je Feld
	 * SET oder SET_ALL, ein Feld, das heute da ist und im ZIP fehlt, kommt raus. Auch readAcl, writeAcl,
	 * userGrantedAccess, isPublic, status und die Dateiart. Nicht aus dem ZIP, weil sie technisch nicht können
	 * ({@link #NOT_FROM_REVISION}): id ist dieselbe, _version_ vergibt Solr selbst, ein alter Wert wäre eine Bedingung
	 * und ein 409, fileIdRevisionNo zählt hoch wie bei jedem Schreiben. Dazu der Stempel jedes Schreibens eines Menschen:
	 * changingUsers aus dem ZIP mit dem Wiederhersteller am Ende, lastChangedAt jetzt. Ein Feld, das der Kern nicht mehr
	 * kennt, wird übersprungen und steht im Log. Das Zurückladen ist ein revisionierter Vorgang: Der heutige Stand wird
	 * vorher selbst als Revision gesichert, ein Rückwärtsgang, der seine Spuren verwischt, wäre wertlos. 403 ohne
	 * Schreibrecht, 404 ohne diese Revision.
	 *
	 * @return das Dokument, wie es jetzt steht, mit der Lesegrenze des Users; null, wenn er es danach nicht mehr sehen
	 *         darf, denn das ZIP kann eine readAcl zurückbringen, in der er nicht steht
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsSolrDocumentForClient restoreDmsSolrDocumentRevision(String id, long revisionNo) {
		Map<String, Object> today = dmsExactFieldValueResolveService.loadWholeDmsSolrDocumentForWrite(id);
		if (today == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Schreibrecht an diesem Dokument");
		}
		Map<String, Object> revision = dmsSolrDocumentRevisionService.readRevisionDocument(id, revisionNo);

		Set<String> fieldNames = new LinkedHashSet<>(revision.keySet());
		fieldNames.addAll(today.keySet());
		List<SolrDocumentFieldChange> changes = new ArrayList<>();
		List<String> unknown = new ArrayList<>();
		boolean mayChangePublicAccess = currentUserMayChangePublicAccess();
		for (String fieldName : fieldNames) {
			if (NOT_FROM_REVISION.contains(fieldName)) {
				continue;
			}
			if (DmsSolrDocumentFieldNames.IS_PUBLIC.equals(fieldName) && !mayChangePublicAccess) {
				// die Internetfreigabe ändert nur DMS_PUBLIC: ohne die Rolle bleibt sie, wie sie heute ist
				log.info("Zurückladen von {} Revision {}: isPublic bleibt, {} hat kein {}", id, revisionNo,
						currentUsersProvider.getCurrentUsersGuid(), PUBLIC_ACCESS_ROLE_KEY);
				continue;
			}
			if (!dmsSolrDocumentChecker.isFieldOrHookOfDmsCore(fieldName)) {
				unknown.add(fieldName);
				continue;
			}
			Object then = revision.get(fieldName);
			// ob Liste oder Wert, sagt der Wert, der da ist: multiValued kommt aus Solr und aus dem ZIP immer als Liste
			Object shape = then != null ? then : today.get(fieldName);
			if (shape instanceof List<?>) {
				changes.add(SolrDocumentFieldChange.setAll(fieldName, then instanceof List<?> list ? list : List.of()));
			} else {
				changes.add(SolrDocumentFieldChange.set(fieldName, then));
			}
		}
		if (!unknown.isEmpty()) {
			log.warn("Zurückladen von {} Revision {}: Felder, die der Kern DMS nicht mehr kennt, übersprungen: {}", id,
					revisionNo, unknown);
		}
		String currentUsersGuid = currentUsersProvider.getCurrentUsersGuid().toString();
		List<Object> changingUsers = new ArrayList<>();
		Object thenChangingUsers = revision.get(DmsSolrDocumentFieldNames.CHANGING_USERS);
		for (Object user : thenChangingUsers instanceof List<?> list ? list : List.of()) {
			if (user != null && !currentUsersGuid.equals(user.toString())) {
				changingUsers.add(user);
			}
		}
		changingUsers.add(currentUsersGuid);
		changes.add(SolrDocumentFieldChange.setAll(DmsSolrDocumentFieldNames.CHANGING_USERS, changingUsers));
		changes.add(SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.LAST_CHANGED_AT, Instant.now()));

		updateDmsSolrDocument(DmsSolrDocumentWriteProcess.DMS_REVISION_RESTORE, id, List.of(), changes);
		log.info("Revision {} von {} zurückgeladen von {}", revisionNo, id, currentUsersGuid);
		return dmsExactFieldValueResolveService.loadDmsSolrDocumentForUploadAnswer(id);
	}

	/**
	 * Der Download-Zähler (User 2026-10-08): downloadCount INC 1, nach jedem Download und jedem Abruf durch einen Viewer,
	 * beide kommen über denselben Download-Weg. Die eine Ausnahme von der Regel „Atomic Update nur mit Schreibcheck und
	 * Stempel“: Ein Download ist keine Änderung durch einen Menschen, sondern Buchhaltung des Backends. Deshalb kein
	 * writeAcl-Check (wer nur lesen darf, lädt auch herunter, die Leseprüfung hat der Download gemacht), kein
	 * changingUsers, kein lastChangedAt, keine Revision. Scheitert Solr, meldet das der Aufrufer nur im Log: Die Bytes
	 * sind dann schon beim Client, ein Zähltick ist keinen Abbruch wert.
	 */
	@PreAuthorize("hasRole('DMS')")
	public void countDownloadOfDmsSolrDocument(String id) {
		SolrDocumentFieldChange increment = dmsSolrDocumentChecker.checkDocumentFieldChanges(
				List.of(SolrDocumentFieldChange.inc(DmsSolrDocumentFieldNames.DOWNLOAD_COUNT, 1))).get(0);
		solrDocumentService.atomicUpdateDocument(DmsSolrDocumentFieldNames.CORE_KEY, id, modifiersOf(List.of(increment)));
		log.debug("{} von {} an {}", DmsSolrDocumentWriteProcess.DMS_DOWNLOAD_COUNT,
				currentUsersProvider.getCurrentUsersGuid(), id);
	}

	/**
	 * Dateifreigabe setzen: die guid des Empfängers ADD in userGrantedAccess jedes dieser Dokumente. Nur
	 * userGrantedAccess, kein anderes Feld. Die ids kommen aus dem Auflösen des Freigabe-Service, geschrieben wird, was
	 * der current user schreiben darf.
	 *
	 * @return wie viele Dokumente geschrieben wurden
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public int addGranteeToDmsSolrDocuments(List<String> ids, UUID granteeUsersGuid) {
		return updateManyDmsSolrDocuments(DmsSolrDocumentWriteProcess.DMS_DOCUMENT_GRANT_ADD_GRANTEE, ids,
				List.of(SolrDocumentFieldChange.add(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME,
						List.of(granteeUsersGuid.toString()))));
	}

	/**
	 * Dateifreigabe entziehen oder abgelaufen: die guid des Empfängers REMOVE aus userGrantedAccess dieser Dokumente.
	 *
	 * @return wie viele Dokumente geschrieben wurden
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public int removeGranteeFromDmsSolrDocuments(List<String> ids, UUID granteeUsersGuid) {
		return updateManyDmsSolrDocuments(DmsSolrDocumentWriteProcess.DMS_DOCUMENT_GRANT_REMOVE_GRANTEE, ids,
				List.of(SolrDocumentFieldChange.remove(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME,
						List.of(granteeUsersGuid.toString()))));
	}

	// ===== Die Mechanik: Anlegen, Update, Update vieler =====

	/**
	 * Ein neues Dokument: Existenzcheck, Schreibregel, Form, dann die Felder des Backends darüber, dann das volle
	 * Schreiben. 409, wenn es die id schon gibt. Ein Backend-Feld, das es im Kern nicht gibt, ist ein Fehler im Code.
	 */
	private void createDmsSolrDocument(DmsSolrDocumentWriteProcess process, JsonNode clientFieldValuesByFieldName,
			Map<String, Object> backendFieldValues) {
		String id = String.valueOf(backendFieldValues.get(SolrCore.ID_FIELD_NAME));
		if (dmsSolrDocumentExistenceService.loadDmsSolrDocumentExistenceCheck(id).exists()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "ein Dokument mit dieser id gibt es schon");
		}
		Map<String, Object> document = new LinkedHashMap<>(dmsSolrDocumentChecker.checkNewDmsSolrDocument(
				removeFieldsTheClientMayNotSet(clientFieldValuesByFieldName, true)));
		for (Map.Entry<String, Object> backendField : backendFieldValues.entrySet()) {
			if (!dmsSolrDocumentChecker.isFieldOrHookOfDmsCore(backendField.getKey())) {
				throw new IllegalStateException(process + ": " + backendField.getKey() + " ist kein Feld des Kerns DMS");
			}
			// das Backend setzt nach dem Client, ein mitgeschickter Wert wird überschrieben
			document.put(backendField.getKey(), backendField.getValue());
		}
		solrDocumentService.writeNewDocument(DmsSolrDocumentFieldNames.CORE_KEY, document);
		log.info("{} von {}: {} angelegt mit {}", process, currentUsersProvider.getCurrentUsersGuid(), id,
				document.keySet());
	}

	/**
	 * Atomic Update eines Dokuments: Schreibcheck, die Änderungen des Clients durch die Schreibregel, dann beide Listen
	 * durch die Form, dann die Modifier an Solr. 403 ohne writeAcl-Match. Bei einem revisionierten Vorgang ist der
	 * Schreibcheck das Laden des ganzen Dokuments: Nur Änderungen, die gegen diesen Stand wirklich etwas ändern, bleiben
	 * (der Stempel changingUsers und lastChangedAt geht immer mit, zählt aber nicht); bleibt keine, wird weder gesichert
	 * noch geschrieben. Sonst wird der Stand als ZIP gesichert, mit genau diesen Feldnamen im Register, und
	 * fileIdRevisionNo INC kommt in dieselbe Änderungsliste.
	 *
	 * @return true, wenn geschrieben wurde; false, wenn nach der Schreibregel nichts übrig blieb oder nichts sich ändert
	 */
	private boolean updateDmsSolrDocument(DmsSolrDocumentWriteProcess process, String id,
			List<SolrDocumentFieldChange> clientFieldChanges, List<SolrDocumentFieldChange> backendFieldChanges) {
		Map<String, Object> before = process.isRevisioned()
				? dmsExactFieldValueResolveService.loadWholeDmsSolrDocumentForWrite(id)
				: dmsExactFieldValueResolveService.loadDmsSolrDocumentFieldsForWrite(id, List.of());
		if (before == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "kein Schreibrecht an diesem Dokument");
		}
		Map<String, SolrDocumentFieldChange> changes = new LinkedHashMap<>();
		for (SolrDocumentFieldChange change : dmsSolrDocumentChecker.checkDocumentFieldChanges(
				clientFieldChangesAllowedOnUpdate(clientFieldChanges))) {
			changes.put(change.fieldName(), change);
		}
		for (SolrDocumentFieldChange change : dmsSolrDocumentChecker.checkDocumentFieldChanges(backendFieldChanges)) {
			changes.put(change.fieldName(), change);
		}
		if (changes.isEmpty()) {
			log.debug("{}: nichts zu schreiben an {}", process, id);
			return false;
		}
		if (process.isRevisioned()) {
			// nur was gegen den Stand davor wirklich etwas ändert, zählt; ein Zurückladen schickt jedes Feld aus dem ZIP
			List<String> changedFieldNames = new ArrayList<>();
			for (SolrDocumentFieldChange change : new ArrayList<>(changes.values())) {
				if (STAMP_FIELD_NAMES.contains(change.fieldName())) {
					continue;
				}
				if (changesSomething(before, change)) {
					changedFieldNames.add(change.fieldName());
				} else {
					changes.remove(change.fieldName());
				}
			}
			if (changedFieldNames.isEmpty()) {
				log.info("{}: nichts ändert sich an {}, keine Revision, nichts geschrieben", process, id);
				return false;
			}
			// erst das ZIP, dann Solr: scheitert die Sicherung, gibt es keine Änderung
			dmsSolrDocumentRevisionService.saveRevisionBeforeWrite(before, changedFieldNames);
			SolrDocumentFieldChange increment = dmsSolrDocumentChecker.checkDocumentFieldChanges(
					List.of(SolrDocumentFieldChange.inc(DmsSolrDocumentFieldNames.FILE_ID_REVISION_NO, 1))).get(0);
			changes.put(increment.fieldName(), increment);
		}
		solrDocumentService.atomicUpdateDocument(DmsSolrDocumentFieldNames.CORE_KEY, id, modifiersOf(changes.values()));
		log.info("{} von {} an {}: {}", process, currentUsersProvider.getCurrentUsersGuid(), id, changes.keySet());
		return true;
	}

	/**
	 * Ändert diese Änderung am Dokument wirklich etwas? Gegen den Stand davor: SET gegen den Wert, SET_ALL als Menge, ADD
	 * nur, wenn ein Wert noch fehlt, REMOVE nur, wenn einer da ist, INC immer. Kein Wert und kein Feld sind derselbe
	 * Zustand.
	 */
	private static boolean changesSomething(Map<String, Object> before, SolrDocumentFieldChange change) {
		Object current = before.get(change.fieldName());
		Set<String> currentValues = comparableValues(
				current instanceof List<?> list ? list : current == null ? List.of() : List.of(current));
		Set<String> newValues = comparableValues(change.values());
		return switch (change.operation()) {
			case SET, SET_ALL -> !currentValues.equals(newValues);
			case ADD -> !currentValues.containsAll(newValues);
			case REMOVE -> newValues.stream().anyMatch(currentValues::contains);
			case INC -> true;
		};
	}

	/** Vergleichswerte: ganze Zahlen ohne Typunterschied (Integer gegen Long), Instant als ISO-Text, der Rest als Text */
	private static Set<String> comparableValues(Collection<?> values) {
		Set<String> comparable = new LinkedHashSet<>();
		for (Object value : values) {
			if (value == null) {
				continue;
			}
			if (value instanceof Integer || value instanceof Long) {
				comparable.add(Long.toString(((Number) value).longValue()));
			} else {
				comparable.add(value.toString());
			}
		}
		return comparable;
	}

	/**
	 * Dasselbe Atomic Update für viele Dokumente, nur Änderungen des Backends. Die ids werden noch einmal gegen writeAcl
	 * aufgelöst, geschrieben wird nur, was der current user schreiben darf. Ein revisionierter Vorgang läuft je Dokument
	 * über den Einzelweg, jedes mit seinem eigenen ZIP; nur ein nicht revisionierter geht als ein Request an Solr.
	 *
	 * @return wie viele Dokumente geschrieben wurden
	 */
	private int updateManyDmsSolrDocuments(DmsSolrDocumentWriteProcess process, List<String> ids,
			List<SolrDocumentFieldChange> backendFieldChanges) {
		if (ids.isEmpty() || backendFieldChanges.isEmpty()) {
			return 0;
		}
		List<String> writableIds = dmsExactFieldValueResolveService.resolveWritableDmsSolrDocumentIdsAmong(ids);
		if (process.isRevisioned()) {
			int written = 0;
			for (String id : writableIds) {
				if (updateDmsSolrDocument(process, id, List.of(), backendFieldChanges)) {
					written++;
				}
			}
			log.info("{} von {}: {} von {} Dokumenten einzeln mit Revision geschrieben", process,
					currentUsersProvider.getCurrentUsersGuid(), written, ids.size());
			return written;
		}
		Map<String, SolrDocumentFieldChange> changes = new LinkedHashMap<>();
		for (SolrDocumentFieldChange change : dmsSolrDocumentChecker.checkDocumentFieldChanges(backendFieldChanges)) {
			changes.put(change.fieldName(), change);
		}
		solrDocumentService.atomicUpdateManyDocuments(DmsSolrDocumentFieldNames.CORE_KEY, writableIds,
				modifiersOf(changes.values()));
		log.info("{} von {}: {} von {} Dokumenten geschrieben, {}", process, currentUsersProvider.getCurrentUsersGuid(),
				writableIds.size(), ids.size(), changes.keySet());
		return writableIds.size();
	}

	/** Die Operationen in die Modifier von Solr: je Feld {modifier: wert}, bei SET und SET_ALL ohne Wert null, das nimmt das Feld raus */
	private static Map<String, Map<String, Object>> modifiersOf(Iterable<SolrDocumentFieldChange> changes) {
		Map<String, Map<String, Object>> modifiers = new LinkedHashMap<>();
		for (SolrDocumentFieldChange change : changes) {
			Object value = switch (change.operation()) {
				case SET, INC -> change.values().isEmpty() ? null : change.values().get(0);
				case SET_ALL -> change.values().isEmpty() ? null : change.values();
				case ADD, REMOVE -> change.values();
			};
			// singletonMap, weil Map.of kein null kennt und null hier „Feld raus“ heißt
			modifiers.put(change.fieldName(), Collections.singletonMap(change.operation().getSolrModifier(), value));
		}
		return modifiers;
	}

	/** Die Änderungen des Clients ohne die Felder, die er beim Bearbeiten nicht ändern darf; was wegfällt, steht im Log */
	private List<SolrDocumentFieldChange> clientFieldChangesAllowedOnUpdate(List<SolrDocumentFieldChange> clientFieldChanges) {
		Map<String, SolrFieldClientWriteRule> rules = clientWriteRules();
		List<SolrDocumentFieldChange> allowed = new ArrayList<>();
		List<String> ignored = new ArrayList<>();
		for (SolrDocumentFieldChange change : clientFieldChanges) {
			SolrFieldClientWriteRule rule = rules.get(change.fieldName());
			if (rule == null || rule.isClientAllowedToChangeOnUpdate()) {
				allowed.add(change);
			} else {
				ignored.add(change.fieldName());
			}
		}
		if (!ignored.isEmpty()) {
			log.debug("Kern DMS: Änderungen des Clients ignoriert, Schreibregel: {}", ignored);
		}
		return allowed;
	}

	/** Je Feld des Kerns DMS seine Schreibregel aus den Stammdaten; Hooks sind immer Client, die eingebauten nie */
	private Map<String, SolrFieldClientWriteRule> clientWriteRules() {
		Map<String, SolrFieldClientWriteRule> rules = new LinkedHashMap<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DmsSolrDocumentFieldNames.CORE_KEY)) {
			rules.put(solrField.getName(), solrField.getClientWriteRule());
		}
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			rules.putIfAbsent(solrHook.getKey(), SolrFieldClientWriteRule.CLIENT_SETS_AND_CHANGES);
		}
		for (String builtIn : SolrCore.BUILT_IN_FIELD_NAMES) {
			rules.put(builtIn, SolrFieldClientWriteRule.BACKEND_ONLY);
		}
		return rules;
	}

	// ===== Die Regeln des DMS =====

	/**
	 * Eine Kette hat durchgehend dieselbe Dateiart: Nach dem Wechsel am einen Glied bekommen alle anderen Glieder, die
	 * der User sieht und schreiben darf, dieselbe Dateiart und ACLs. Was er nicht schreiben darf, bleibt stehen und
	 * steht im Log.
	 */
	private void changeFileSubClassOfOtherChainMembers(String id, String versionChain, FileSubClass fileSubClass) {
		if (versionChain == null) {
			return;
		}
		List<String> otherIds = new ArrayList<>();
		for (Map<String, Object> member : dmsExactFieldValueResolveService.loadDmsVersionChainMembers(versionChain)) {
			String memberId = text(member, SolrCore.ID_FIELD_NAME);
			if (memberId != null && !memberId.equals(id)
					&& !fileSubClass.getKey().equals(text(member, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY))) {
				otherIds.add(memberId);
			}
		}
		if (otherIds.isEmpty()) {
			return;
		}
		List<SolrDocumentFieldChange> changes = new ArrayList<>();
		changes.add(SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY, fileSubClass.getKey()));
		changes.addAll(accessControlListsOf(fileSubClass));
		changes.addAll(changedByCurrentUser());
		int written = updateManyDmsSolrDocuments(DmsSolrDocumentWriteProcess.DMS_EDIT_FILE_SUB_CLASS_OF_CHAIN, otherIds,
				changes);
		if (written < otherIds.size()) {
			log.warn("Dateiartwechsel der Kette {}: {} von {} Gliedern ohne Schreibrecht, sie behalten die alte Dateiart",
					versionChain, otherIds.size() - written, otherIds.size());
		}
	}

	/** Die Felder des Backends für ein neues Dokument, darüber kein Wert des Clients */
	private Map<String, Object> newDocumentBackendFieldValues(DmsSolrDocumentUploadFacts facts, FileSubClass fileSubClass,
			String versionChain, long version, JsonNode clientFieldValuesByFieldName) {
		Instant now = Instant.now();
		String usersGuid = currentUsersProvider.getCurrentUsersGuid().toString();
		Map<String, Object> backend = new LinkedHashMap<>();
		backend.put(SolrCore.ID_FIELD_NAME, facts.sha256());
		backend.put(DmsSolrDocumentFieldNames.FILE_NAME, facts.fileName());
		backend.put(DmsSolrDocumentFieldNames.FILE_EXTENSION, facts.fileExtension());
		backend.put(DmsSolrDocumentFieldNames.MIME_TYPE, facts.mimeType());
		backend.put(DmsSolrDocumentFieldNames.FILE_SIZE_BYTES, facts.sizeBytes());
		backend.put(DmsSolrDocumentFieldNames.SFTP_PATH, facts.sftpPath());
		backend.put(DmsSolrDocumentFieldNames.UPLOADER, usersGuid);
		backend.put(DmsSolrDocumentFieldNames.CHANGING_USERS, List.of(usersGuid));
		backend.put(DmsSolrDocumentFieldNames.DOCUMENT_UPLOADED_AT, now);
		backend.put(DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT, facts.documentCreatedAt());
		backend.put(DmsSolrDocumentFieldNames.LAST_CHANGED_AT, now);
		backend.put(DmsSolrDocumentFieldNames.DOWNLOAD_COUNT, 0L);
		backend.put(DmsSolrDocumentFieldNames.FILE_ID_REVISION_NO, 0L);
		backend.put(DmsSolrDocumentFieldNames.VERSION, version);
		backend.put(DmsSolrDocumentFieldNames.VERSION_CHAIN, versionChain);
		backend.put(DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY, fileSubClass.getKey());
		backend.put(SolrCurrentUserCoreAccessService.READ_ACL_FIELD_NAME, copyOf(fileSubClass.getReadAccessRoleKeys()));
		backend.put(SolrCurrentUserCoreAccessService.WRITE_ACL_FIELD_NAME, copyOf(fileSubClass.getWriteAccessRoleKeys()));
		backend.put(DmsSolrDocumentFieldNames.OCR, false);
		backend.put(DmsSolrDocumentFieldNames.KI, false);
		// status und isPublic darf der Client setzen; schickt er nichts, gilt der Standard
		if (text(clientFieldValuesByFieldName, DmsSolrDocumentFieldNames.STATUS) == null) {
			backend.put(DmsSolrDocumentFieldNames.STATUS, DmsStateService.ACTIVE);
		}
		if (!clientFieldValuesByFieldName.has(DmsSolrDocumentFieldNames.IS_PUBLIC)) {
			backend.put(DmsSolrDocumentFieldNames.IS_PUBLIC, false);
		}
		if (facts.documentLanguage() != null) {
			backend.put(DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE, facts.documentLanguage());
		}
		if (facts.searchableWords() != null && !facts.searchableWords().isBlank()) {
			backend.put(DmsSolrDocumentFieldNames.SEARCHABLE_WORDS, facts.searchableWords());
		}
		if (facts.imagePreviewBase64Png() != null) {
			backend.put(DmsSolrDocumentFieldNames.IMAGE_PREVIEW_BASE64_PNG, facts.imagePreviewBase64Png());
		}
		return backend;
	}

	/** readAcl und writeAcl als SET_ALL aus der Dateiart, die einzige Quelle dieser Felder */
	private static List<SolrDocumentFieldChange> accessControlListsOf(FileSubClass fileSubClass) {
		return List.of(
				SolrDocumentFieldChange.setAll(SolrCurrentUserCoreAccessService.READ_ACL_FIELD_NAME,
						copyOf(fileSubClass.getReadAccessRoleKeys())),
				SolrDocumentFieldChange.setAll(SolrCurrentUserCoreAccessService.WRITE_ACL_FIELD_NAME,
						copyOf(fileSubClass.getWriteAccessRoleKeys())));
	}

	/** Jeder Schreibvorgang eines Menschen: seine guid in changingUsers, lastChangedAt jetzt */
	private List<SolrDocumentFieldChange> changedByCurrentUser() {
		return List.of(
				SolrDocumentFieldChange.add(DmsSolrDocumentFieldNames.CHANGING_USERS,
						List.of(currentUsersProvider.getCurrentUsersGuid().toString())),
				SolrDocumentFieldChange.set(DmsSolrDocumentFieldNames.LAST_CHANGED_AT, Instant.now()));
	}

	/** Ein mitgeschickter Status muss einer des Bereichs DMS sein, sonst stünde im Dokument ein Status ohne Namen (400) */
	private void requireClientStatusValid(JsonNode clientFieldValuesByFieldName) {
		String status = text(clientFieldValuesByFieldName, DmsSolrDocumentFieldNames.STATUS);
		if (status != null && !dmsStateService.isDmsState(status)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"status " + status + " ist kein Status des Bereichs " + DmsStateService.SCOPE);
		}
	}

	/** Darf der current user die Internetfreigabe setzen? Nur mit der AR DMS_PUBLIC im Token. */
	private boolean currentUserMayChangePublicAccess() {
		return currentUsersProvider.getCurrentUsersAccessRoleKeys().contains(PUBLIC_ACCESS_ROLE_KEY);
	}

	/** Die Internetfreigabe ändern: 403 ohne DMS_PUBLIC, in beide Richtungen */
	private void requireCurrentUserMayChangePublicAccess() {
		if (!currentUserMayChangePublicAccess()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"die Internetfreigabe (" + DmsSolrDocumentFieldNames.IS_PUBLIC + ") ändert nur, wer die Rolle "
							+ PUBLIC_ACCESS_ROLE_KEY + " hat");
		}
	}

	/** Beim Anlegen: isPublic true nur mit DMS_PUBLIC. false ist der Standard, das darf jeder schicken. */
	private void requireClientPublicAccessAllowed(JsonNode clientFieldValuesByFieldName) {
		JsonNode isPublic = clientFieldValuesByFieldName == null ? null
				: clientFieldValuesByFieldName.get(DmsSolrDocumentFieldNames.IS_PUBLIC);
		if (isPublic != null && isPublic.isBoolean() && isPublic.booleanValue()) {
			requireCurrentUserMayChangePublicAccess();
		}
	}

	/** Schreibrecht an der Dateiart: eine ihrer Schreibrollen muss im Token stehen, sonst 403 */
	private void requireWriteRoleOfFileSubClass(FileSubClass fileSubClass) {
		List<String> writeAccessRoleKeys = fileSubClass.getWriteAccessRoleKeys();
		List<String> own = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (writeAccessRoleKeys == null || writeAccessRoleKeys.stream().noneMatch(own::contains)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"kein Schreibrecht an der Dateiart " + fileSubClass.getKey());
		}
	}

	/** Die Dateiart muss die Endung freigeschaltet haben, eine Dateiart ohne Endungen nimmt nichts an (400) */
	private static void requireExtensionAllowedByFileSubClass(FileSubClass fileSubClass, String fileExtension) {
		if (!isExtensionAllowedByFileSubClass(fileSubClass, fileExtension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Endung "
					+ (fileExtension == null || fileExtension.isEmpty() ? "(keine)" : fileExtension)
					+ " ist bei der Dateiart " + fileSubClass.getKey() + " nicht freigeschaltet");
		}
	}

	private static boolean isExtensionAllowedByFileSubClass(FileSubClass fileSubClass, String fileExtension) {
		List<String> allowed = fileSubClass.getExtensions();
		return allowed != null && fileExtension != null && allowed.contains(fileExtension);
	}

	private static void requireUuid(String versionChain) {
		if (versionChain == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DmsSolrDocumentFieldNames.VERSION_CHAIN + " fehlt");
		}
		try {
			UUID.fromString(versionChain);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DmsSolrDocumentFieldNames.VERSION_CHAIN + " ist keine UUID");
		}
	}

	/**
	 * Die Beschreibung des Merge: je Sprache hinten angehängt, wenn der neue Text nicht leer ist und nicht schon im
	 * alten steht. Beide sind mehrsprachiges JSON; ist eins davon kein JSON-Objekt, gilt dieselbe Regel auf dem rohen
	 * Text. null, wenn sich nichts ändert.
	 */
	private String appendedDescription(String existingDescription, String sentDescription) {
		if (sentDescription == null || sentDescription.isBlank()) {
			return null;
		}
		ObjectNode existing = objectNodeOrNull(existingDescription);
		ObjectNode sent = objectNodeOrNull(sentDescription);
		if (existing == null && existingDescription != null && !existingDescription.isBlank() || sent == null) {
			String appended = appendedText(existingDescription, sentDescription);
			return appended.equals(existingDescription) ? null : appended;
		}
		ObjectNode result = existing == null ? objectMapper.createObjectNode() : existing.deepCopy();
		boolean changed = false;
		for (Map.Entry<String, JsonNode> language : sent.properties()) {
			String sentText = language.getValue().isString() ? language.getValue().asString().strip() : "";
			if (sentText.isEmpty()) {
				continue;
			}
			JsonNode existingNode = result.get(language.getKey());
			String existingText = existingNode != null && existingNode.isString() ? existingNode.asString() : "";
			String appended = appendedText(existingText, sentText);
			if (!appended.equals(existingText)) {
				result.put(language.getKey(), appended);
				changed = true;
			}
		}
		return changed ? result.toString() : null;
	}

	private static String appendedText(String existingText, String sentText) {
		String existing = existingText == null ? "" : existingText.strip();
		String sent = sentText.strip();
		if (existing.isEmpty()) {
			return sent;
		}
		return existing.contains(sent) ? existing : existing + DESCRIPTION_SEPARATOR + sent;
	}

	private ObjectNode objectNodeOrNull(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			JsonNode node = objectMapper.readTree(json);
			return node instanceof ObjectNode objectNode ? objectNode : null;
		} catch (JacksonException e) {
			return null;
		}
	}

	/** Ein nackter Tag im Erstelldatum wird Mitternacht UTC, damit die Formprüfung ihn als Zeitpunkt nimmt */
	private static JsonNode withDayAsInstant(JsonNode fieldValuesByFieldName) {
		if (fieldValuesByFieldName == null || !fieldValuesByFieldName.isObject()) {
			return fieldValuesByFieldName;
		}
		JsonNode createdAt = fieldValuesByFieldName.get(DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT);
		if (createdAt == null || !createdAt.isString() || !DAY.matcher(createdAt.asString().strip()).matches()) {
			return fieldValuesByFieldName;
		}
		ObjectNode copy = ((ObjectNode) fieldValuesByFieldName).deepCopy();
		copy.put(DmsSolrDocumentFieldNames.DOCUMENT_CREATED_AT, createdAt.asString().strip() + "T00:00:00Z");
		return copy;
	}

	private static String text(JsonNode node, String name) {
		if (node == null || !node.isObject()) {
			return null;
		}
		JsonNode value = node.get(name);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.isString() ? value.asString().strip() : value.toString();
		return text.isEmpty() ? null : text;
	}

	private static String text(Map<String, Object> document, String name) {
		Object value = document.get(name);
		return value == null ? null : value.toString();
	}

	private static long number(Map<String, Object> document, String name) {
		return document.get(name) instanceof Number number ? number.longValue() : 0L;
	}

	private static List<String> copyOf(List<String> list) {
		return list == null ? List.of() : new ArrayList<>(list);
	}

}
