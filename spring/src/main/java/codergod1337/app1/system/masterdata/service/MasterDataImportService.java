package codergod1337.app1.system.masterdata.service;

import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.model.FileExtensionCollection;
import codergod1337.app1.file.fileextension.service.FileExtensionCollectionService;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.model.AccessRoleCollectionUsersAssignment;
import codergod1337.app1.system.access.model.AccessRoleUsersAssignment;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleCollectionUsersAssignmentService;
import codergod1337.app1.system.access.service.AccessRoleService;
import codergod1337.app1.system.access.service.AccessRoleUsersAssignmentService;
import codergod1337.app1.system.masterdata.model.MasterDataImportCheck;
import codergod1337.app1.system.masterdata.model.MasterDataImportCheck.ConflictItem;
import codergod1337.app1.system.masterdata.model.MasterDataImportCheck.RowCheck;
import codergod1337.app1.system.masterdata.model.MasterDataImportCheck.RowStatus;
import codergod1337.app1.system.masterdata.model.MasterDataImportCheck.SectionCheck;
import codergod1337.app1.system.masterdata.model.MasterDataImportCheck.UsersConflict;
import codergod1337.app1.system.masterdata.model.MasterDataImportRequest.UsersConflictDecision;
import codergod1337.app1.system.masterdata.model.MasterDataSection;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.model.UsersDetails;
import codergod1337.app1.system.user.model.UsersSettings;
import codergod1337.app1.system.user.service.UsersCredentialsService;
import codergod1337.app1.system.user.service.UsersDetailsService;
import codergod1337.app1.system.user.service.UsersService;
import codergod1337.app1.system.user.service.UsersSettingsService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * Import der Stammdaten aus einem ZIP des {@link MasterDataExportService}. Prüfen und Importieren rechnen genau gleich:
 * erst alles ansehen, dann, nur beim Import, in der Reihenfolge der Bereiche schreiben. Alles in einer Transaktion,
 * es kommt alles oder nichts.
 *
 * Die Regeln:
 * - Was es schon gibt, wird übersprungen und nie verändert. AR, ARC, FSC und Endungsgruppen erkennt der Import am key,
 * Endungen an der Endung, User an der guid oder der E-Mail, Details am User, Einstellungen an User und key.
 * - Ein User mit derselben E-Mail, aber anderer guid: Unser System bleibt, wie es ist. Was in der Datei an der fremden
 * guid hängt, geht an unsere guid, soweit es in der Auswahl zu diesem User angehakt ist.
 * - Verwaist ist, was auf etwas zeigt, das es weder bei uns noch angehakt im Import gibt: Es wird nicht geschrieben.
 * Verweise in Listen (z. B. die AR einer FSC) fallen einzeln weg, der Datensatz selbst kommt.
 * - Neue User bekommen das Startpasswort 123, aber nur, wenn es zu ihrer guid noch keine Zugangsdaten gibt.
 * - Geschrieben wird nur über die Services, mit denselben Prüfungen und Folgen wie in der Oberfläche, z. B. gesperrten
 * Tokens bei neuen Rechten. Seit wann eine Zuordnung besteht (assignedAt), zählt ab dem Import.
 */
@Service
public class MasterDataImportService {

	/** Startpasswort neuer User. Gespeichert wird wie beim Login BCrypt über den SHA-256 aus dem Browser. */
	private static final String START_PASSWORD = "123";
	private static final String ADMIN_ACCESS_ROLE_KEY = "ADMIN";

	/** Schutz vor ZIP-Bomben: höchstens so viele Einträge und so viele Bytes entpackt */
	private static final int MAX_ZIP_ENTRIES = 100;
	private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;

	/** Zählen beim Vergleich mit unserem Stand nicht: Zeitpunkte und der Verweis auf den User */
	private static final Set<String> NOT_COMPARED_FIELDS = Set.of("createdAt", "assignedAt", "usersGuid");

	private static final TypeReference<LinkedHashMap<String, Object>> LINE_TYPE = new TypeReference<>() {
	};

	private final AccessRoleService accessRoleService;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final FileExtensionCollectionService fileExtensionCollectionService;
	private final FileExtensionService fileExtensionService;
	private final FileSubClassService fileSubClassService;
	private final UsersService usersService;
	private final UsersCredentialsService usersCredentialsService;
	private final UsersDetailsService usersDetailsService;
	private final UsersSettingsService usersSettingsService;
	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;
	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;
	/** Für den Vergleich einer Zeile mit unserem Stand: dieselbe Zeile, wie der Export sie schriebe */
	private final MasterDataExportService masterDataExportService;
	private final JsonMapper jsonMapper;
	private final Validator validator;

	public MasterDataImportService(AccessRoleService accessRoleService,
			AccessRoleCollectionService accessRoleCollectionService,
			FileExtensionCollectionService fileExtensionCollectionService, FileExtensionService fileExtensionService,
			FileSubClassService fileSubClassService, UsersService usersService,
			UsersCredentialsService usersCredentialsService, UsersDetailsService usersDetailsService,
			UsersSettingsService usersSettingsService, AccessRoleUsersAssignmentService accessRoleUsersAssignmentService,
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService,
			MasterDataExportService masterDataExportService, JsonMapper jsonMapper, Validator validator) {
		this.accessRoleService = accessRoleService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.fileExtensionCollectionService = fileExtensionCollectionService;
		this.fileExtensionService = fileExtensionService;
		this.fileSubClassService = fileSubClassService;
		this.usersService = usersService;
		this.usersCredentialsService = usersCredentialsService;
		this.usersDetailsService = usersDetailsService;
		this.usersSettingsService = usersSettingsService;
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
		this.masterDataExportService = masterDataExportService;
		this.jsonMapper = jsonMapper;
		this.validator = validator;
	}

	/** Probelauf: was der Import mit dieser Auswahl täte. Schreibt nichts. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public MasterDataImportCheck checkMasterDataImport(byte[] zip, Set<MasterDataSection> sections,
			Map<String, UsersConflictDecision> usersConflictDecisions) {
		return planImport(zip, sections, usersConflictDecisions).toCheck();
	}

	/**
	 * Der Import: rechnet wie der Probelauf und schreibt dann alles Neue der angehakten Bereiche. 400, wenn etwas den
	 * ganzen Import blockiert. Zurück kommt, was geschrieben wurde.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public MasterDataImportCheck importMasterData(byte[] zip, Set<MasterDataSection> sections,
			Map<String, UsersConflictDecision> usersConflictDecisions) {
		ImportPlan plan = planImport(zip, sections, usersConflictDecisions);
		if (!plan.blockers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import blockiert: " + String.join("; ", plan.blockers));
		}
		writePlan(plan);
		return plan.toCheck();
	}

	// ===== Planen: alles ansehen, nichts schreiben =====

	private ImportPlan planImport(byte[] zip, Set<MasterDataSection> sections,
			Map<String, UsersConflictDecision> usersConflictDecisions) {
		ImportPlan plan = new ImportPlan(sections != null ? sections : Set.of(),
				usersConflictDecisions != null ? usersConflictDecisions : Map.of());
		Map<String, String> files = readZip(zip, plan.blockers);
		if (plan.blockers.isEmpty()) {
			readManifest(files.get(MasterDataExportService.MANIFEST_FILE_NAME), plan);
		}
		if (!plan.blockers.isEmpty()) {
			return plan;
		}

		Map<MasterDataSection, List<Line>> linesBySection = new EnumMap<>(MasterDataSection.class);
		for (MasterDataSection section : MasterDataSection.values()) {
			String content = files.get(section.getFileName());
			if (content != null) {
				plan.presentSections.add(section);
			}
			linesBySection.put(section, content != null ? readLines(content) : List.of());
		}

		// in der Reihenfolge der Bereiche: jeder sieht, was die früheren zur Verfügung stellen
		planAccessRoles(plan, linesBySection.get(MasterDataSection.ACCESS_ROLES));
		planAccessRoleCollections(plan, linesBySection.get(MasterDataSection.ACCESS_ROLE_COLLECTIONS));
		planFileExtensionCollections(plan, linesBySection.get(MasterDataSection.FILE_EXTENSION_COLLECTIONS));
		planFileExtensions(plan, linesBySection.get(MasterDataSection.FILE_EXTENSIONS));
		planFileSubClasses(plan, linesBySection.get(MasterDataSection.FILE_SUB_CLASSES));
		planUsers(plan, linesBySection.get(MasterDataSection.USERS));
		planUsersDetails(plan, linesBySection.get(MasterDataSection.USERS_DETAILS));
		planUsersSettings(plan, linesBySection.get(MasterDataSection.USERS_SETTINGS));
		planAccessRoleUsersAssignments(plan, linesBySection.get(MasterDataSection.ACCESS_ROLE_USERS_ASSIGNMENTS));
		planAccessRoleCollectionUsersAssignments(plan,
				linesBySection.get(MasterDataSection.ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS));
		collectAdminGrants(plan);
		return plan;
	}

	private void planAccessRoles(ImportPlan plan, List<Line> lines) {
		Map<String, AccessRole> existing = byKey(accessRoleService.getAllAccessRoles(), AccessRole::getKey);
		List<PlannedRow> rows = planKeyedRows(lines, AccessRole.class, AccessRole::getKey, AccessRole::getDisplayName,
				existing, HelperInputs::isValidKey, "key fehlt oder ist ungültig");
		plan.rowsBySection.put(MasterDataSection.ACCESS_ROLES, rows);
		plan.accessRoleKeys.addAll(existing.keySet());
		plan.accessRoleKeys.addAll(newKeys(plan, MasterDataSection.ACCESS_ROLES));
	}

	/**
	 * ARC: Ihre AR und Slave-ARC müssen bei uns oder angehakt im Import existieren, sonst fallen sie weg. Eine ARC nennt
	 * sich nie selbst als Slave.
	 */
	private void planAccessRoleCollections(ImportPlan plan, List<Line> lines) {
		Map<String, AccessRoleCollection> existing = byKey(accessRoleCollectionService.getAllAccessRoleCollections(),
				AccessRoleCollection::getKey);
		List<PlannedRow> rows = planKeyedRows(lines, AccessRoleCollection.class, AccessRoleCollection::getKey,
				AccessRoleCollection::getDisplayName, existing,
				key -> HelperInputs.isValidKey(key) && key.startsWith(AccessRoleCollectionService.KEY_PREFIX),
				"key fehlt oder ist ungültig, er muss mit " + AccessRoleCollectionService.KEY_PREFIX + " beginnen");
		plan.rowsBySection.put(MasterDataSection.ACCESS_ROLE_COLLECTIONS, rows);
		plan.accessRoleCollectionKeys.addAll(existing.keySet());
		plan.accessRoleCollectionKeys.addAll(newKeys(plan, MasterDataSection.ACCESS_ROLE_COLLECTIONS));
		existing.values().forEach(accessRoleCollection -> plan.accessRoleKeysByAccessRoleCollectionKey
				.put(accessRoleCollection.getKey(), orEmpty(accessRoleCollection.getAccessRoleKeys())));

		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			AccessRoleCollection accessRoleCollection = (AccessRoleCollection) row.data;
			accessRoleCollection
					.setAccessRoleKeys(keepKnown(row, accessRoleCollection.getAccessRoleKeys(), plan.accessRoleKeys, "AR"));
			List<String> slaveArcKeys = new ArrayList<>();
			for (String slaveArcKey : orEmpty(accessRoleCollection.getSlaveArcKeys())) {
				if (slaveArcKey.equals(accessRoleCollection.getKey())) {
					row.addMessage("nennt sich selbst als Slave, fällt weg");
				} else if (!plan.accessRoleCollectionKeys.contains(slaveArcKey)) {
					row.addMessage("Slave-ARC " + slaveArcKey + " gibt es weder bei uns noch im Import, fällt weg");
				} else if (!slaveArcKeys.contains(slaveArcKey)) {
					slaveArcKeys.add(slaveArcKey);
				}
			}
			accessRoleCollection.setSlaveArcKeys(slaveArcKeys);
			if (plan.isSelected(MasterDataSection.ACCESS_ROLE_COLLECTIONS)) {
				plan.accessRoleKeysByAccessRoleCollectionKey.put(accessRoleCollection.getKey(),
						accessRoleCollection.getAccessRoleKeys());
			}
		}
	}

	private void planFileExtensionCollections(ImportPlan plan, List<Line> lines) {
		Map<String, FileExtensionCollection> existing = byKey(
				fileExtensionCollectionService.getAllFileExtensionCollections(), FileExtensionCollection::getKey);
		List<PlannedRow> rows = planKeyedRows(lines, FileExtensionCollection.class, FileExtensionCollection::getKey,
				FileExtensionCollection::getDisplayName, existing, HelperInputs::isValidKey, "key fehlt oder ist ungültig");
		plan.rowsBySection.put(MasterDataSection.FILE_EXTENSION_COLLECTIONS, rows);
		plan.fileExtensionCollectionKeys.addAll(existing.keySet());
		plan.fileExtensionCollectionKeys.addAll(newKeys(plan, MasterDataSection.FILE_EXTENSION_COLLECTIONS));
	}

	/** Endungen: Gibt es ihre Gruppe weder bei uns noch im Import, kommt die Endung ohne Gruppe. */
	private void planFileExtensions(ImportPlan plan, List<Line> lines) {
		Map<String, FileExtension> existing = byKey(fileExtensionService.getAllFileExtensions(),
				FileExtension::getExtension);
		List<PlannedRow> rows = planKeyedRows(lines, FileExtension.class, FileExtension::getExtension,
				FileExtension::getDescription, existing, FileExtensionService::isValidExtension,
				"Endung fehlt oder ist ungültig: nur a–z und 0–9, höchstens 20 Zeichen, ohne Punkt");
		plan.rowsBySection.put(MasterDataSection.FILE_EXTENSIONS, rows);
		for (PlannedRow row : rows) {
			FileExtension fileExtension = row.isNew() ? (FileExtension) row.data : null;
			if (fileExtension != null && fileExtension.getFileExtensionCollectionKey() != null
					&& !plan.fileExtensionCollectionKeys.contains(fileExtension.getFileExtensionCollectionKey())) {
				row.addMessage("Gruppe " + fileExtension.getFileExtensionCollectionKey()
						+ " gibt es weder bei uns noch im Import, die Endung kommt ohne Gruppe");
				fileExtension.setFileExtensionCollectionKey(null);
			}
		}
		plan.extensions.addAll(existing.keySet());
		plan.extensions.addAll(newKeys(plan, MasterDataSection.FILE_EXTENSIONS));
	}

	/** FSC: unbekannte AR und Endungen fallen weg. Schreiben schließt Lesen ein. */
	private void planFileSubClasses(ImportPlan plan, List<Line> lines) {
		Map<String, FileSubClass> existing = byKey(fileSubClassService.getAllFileSubClasses(), FileSubClass::getKey);
		List<PlannedRow> rows = planKeyedRows(lines, FileSubClass.class, FileSubClass::getKey,
				FileSubClass::getDisplayName, existing, HelperInputs::isValidKey, "key fehlt oder ist ungültig");
		plan.rowsBySection.put(MasterDataSection.FILE_SUB_CLASSES, rows);
		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			FileSubClass fileSubClass = (FileSubClass) row.data;
			List<String> writeAccessRoleKeys = keepKnown(row, fileSubClass.getWriteAccessRoleKeys(), plan.accessRoleKeys,
					"AR");
			List<String> readAccessRoleKeys = keepKnown(row, fileSubClass.getReadAccessRoleKeys(), plan.accessRoleKeys,
					"AR");
			for (String writeAccessRoleKey : writeAccessRoleKeys) {
				if (!readAccessRoleKeys.contains(writeAccessRoleKey)) {
					readAccessRoleKeys.add(writeAccessRoleKey);
				}
			}
			fileSubClass.setReadAccessRoleKeys(readAccessRoleKeys);
			fileSubClass.setWriteAccessRoleKeys(writeAccessRoleKeys);
			fileSubClass.setExtensions(keepKnown(row, fileSubClass.getExtensions(), plan.extensions, "Endung"));
		}
	}

	/**
	 * User: vorhanden über die guid, oder über die E-Mail unter anderer guid (Konflikt). Im Konflikt geht alles, was an
	 * der guid aus der Datei hängt, an unsere guid. Ein vergebener Username fällt bei neuen Usern weg.
	 */
	private void planUsers(ImportPlan plan, List<Line> lines) {
		List<Users> existingUsers = usersService.getAllUsers();
		Map<UUID, Users> existingByGuid = existingUsers.stream()
				.collect(Collectors.toMap(Users::getGuid, Function.identity()));
		Map<String, Users> existingByEmail = existingUsers.stream()
				.collect(Collectors.toMap(users -> users.getEmail().toLowerCase(Locale.ROOT), Function.identity(),
						(first, second) -> first));
		Set<String> existingUsernames = existingUsers.stream().map(Users::getUsername).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		existingUsers.forEach(users -> plan.emailByUsersGuid.put(users.getGuid(), users.getEmail()));

		List<PlannedRow> rows = new ArrayList<>();
		Map<UUID, Integer> countByGuid = new HashMap<>();
		Map<String, Integer> countByEmail = new HashMap<>();
		for (Line line : lines) {
			PlannedRow row = new PlannedRow(line);
			rows.add(row);
			if (line.values() == null) {
				row.reject(RowStatus.INVALID, "kein gültiges JSON");
				continue;
			}
			String guidText = text(line.values(), "guid");
			if (!HelperInputs.isValidGuid(guidText)) {
				row.reject(RowStatus.INVALID, "guid fehlt oder ist ungültig");
				continue;
			}
			UUID guid = UUID.fromString(guidText);
			row.id = guidText;
			String email = text(line.values(), "email");
			if (email == null || email.isBlank()) {
				row.reject(RowStatus.INVALID, "email fehlt");
				continue;
			}
			String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
			plan.emailByUsersGuid.putIfAbsent(guid, normalizedEmail);
			countByGuid.merge(guid, 1, Integer::sum);
			countByEmail.merge(normalizedEmail, 1, Integer::sum);

			OffsetDateTime createdAt = null;
			String createdAtText = text(line.values(), "createdAt");
			if (createdAtText != null) {
				try {
					createdAt = OffsetDateTime.parse(createdAtText);
				} catch (DateTimeParseException e) {
					row.addMessage("createdAt ist unlesbar, wird beim Anlegen neu gesetzt");
				}
			}
			String status = text(line.values(), "status");
			if (status != null && !HelperInputs.isValidKey(status)) {
				row.reject(RowStatus.INVALID, "status ist kein gültiger Key");
				continue;
			}
			Users candidate = new Users(guid, normalizedEmail, blankToNull(text(line.values(), "username")),
					text(line.values(), "vorname"), text(line.values(), "nachname"), createdAt,
					Boolean.TRUE.equals(line.values().get("serviceAccount")), status);
			row.data = candidate;
			row.label = usersLabel(candidate);
			rejectViolations(row, candidate);
		}

		// Usernames der neuen User: Zwei gleiche in der Datei scheiterten sonst erst beim Schreiben am Unique-Index
		Set<String> plannedUsernames = new HashSet<>();
		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			Users candidate = (Users) row.data;
			UUID guid = candidate.getGuid();
			if (countByGuid.get(guid) > 1 || countByEmail.get(candidate.getEmail()) > 1) {
				row.reject(RowStatus.INVALID, "guid oder E-Mail steht mehrfach in der Datei");
				continue;
			}
			Users sameGuid = existingByGuid.get(guid);
			if (sameGuid != null) {
				row.status = RowStatus.EXISTING;
				addDifferences(row, sameGuid);
				if (!sameGuid.getEmail().equalsIgnoreCase(candidate.getEmail())) {
					row.addMessage("E-Mail weicht ab, bei uns: " + sameGuid.getEmail());
				}
				plan.targetUsersGuidByFileGuid.put(guid, guid);
				continue;
			}
			Users sameEmail = existingByEmail.get(candidate.getEmail());
			if (sameEmail != null) {
				row.status = RowStatus.CONFLICT;
				row.addMessage("gibt es bei uns unter anderer guid (" + sameEmail.getGuid()
						+ "): was im Import an diesem User hängt, geht an unseren");
				plan.targetUsersGuidByFileGuid.put(guid, sameEmail.getGuid());
				plan.conflictsByFileGuid.put(guid,
						new ConflictDraft(guid, sameEmail.getGuid(), candidate.getEmail(), usersLabel(candidate)));
				continue;
			}
			if (candidate.getUsername() != null && (existingUsernames.contains(candidate.getUsername())
					|| !plannedUsernames.add(candidate.getUsername()))) {
				row.addMessage("Username " + candidate.getUsername()
						+ " ist bei uns vergeben oder steht mehrfach in der Datei, der User kommt ohne");
				row.data = new Users(guid, candidate.getEmail(), null, candidate.getVorname(), candidate.getNachname(),
						candidate.getCreatedAt(), candidate.isServiceAccount(), candidate.getStatus());
			}
			row.addMessage(usersCredentialsService.isUsersCredentialsAvailable(guid) ? "bekommt das Startpasswort 123"
					: "zu dieser guid gibt es schon Zugangsdaten, sie bleiben");
			if (plan.isSelected(MasterDataSection.USERS)) {
				plan.targetUsersGuidByFileGuid.put(guid, guid);
			}
		}
		plan.rowsBySection.put(MasterDataSection.USERS, rows);
	}

	private void planUsersDetails(ImportPlan plan, List<Line> lines) {
		Map<UUID, UsersDetails> existing = usersDetailsService.getAllUsersDetails().stream()
				.collect(Collectors.toMap(UsersDetails::getUsersGuid, Function.identity()));
		List<PlannedRow> rows = new ArrayList<>();
		Map<UUID, Integer> countByGuid = new HashMap<>();
		for (Line line : lines) {
			PlannedRow row = new PlannedRow(line);
			rows.add(row);
			UUID usersGuid = readUsersGuid(plan, row, line);
			if (usersGuid == null) {
				continue;
			}
			countByGuid.merge(usersGuid, 1, Integer::sum);
			Map<String, Object> values = line.values();
			UsersDetails candidate = new UsersDetails(usersGuid, text(values, "info"), text(values, "profilText"),
					text(values, "webseite"), text(values, "mobile"), text(values, "steam"), text(values, "discord"),
					text(values, "insta"), text(values, "kundenNummer"), text(values, "lieferantenNummer"), 0L);
			row.data = candidate;
			rejectViolations(row, candidate);
		}

		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			if (countByGuid.get(row.fileUsersGuid) > 1) {
				row.reject(RowStatus.INVALID, "mehrere Details für diesen User in der Datei");
				continue;
			}
			if (!resolveUsersTarget(plan, row)) {
				continue;
			}
			UsersDetails ours = existing.get(row.targetUsersGuid);
			if (ours != null) {
				row.status = RowStatus.EXISTING;
				addDifferences(row, ours);
			}
			ConflictDraft conflict = plan.conflictsByFileGuid.get(row.fileUsersGuid);
			if (conflict != null) {
				UsersConflictDecision decision = plan.decisionOf(row.fileUsersGuid);
				conflict.details = applyConflictChoice(row, "Details", decision == null || decision.details());
			}
		}
		plan.rowsBySection.put(MasterDataSection.USERS_DETAILS, rows);
	}

	private void planUsersSettings(ImportPlan plan, List<Line> lines) {
		Map<String, String> existingValueByUsersKey = new HashMap<>();
		for (UsersSettings usersSettings : usersSettingsService.getAllUsersSettings()) {
			existingValueByUsersKey.put(usersSettings.getUsersGuid() + "|" + usersSettings.getKey(),
					usersSettings.getValue());
		}
		List<PlannedRow> rows = new ArrayList<>();
		Map<String, Integer> countByUsersKey = new HashMap<>();
		for (Line line : lines) {
			PlannedRow row = new PlannedRow(line);
			rows.add(row);
			UUID usersGuid = readUsersGuid(plan, row, line);
			if (usersGuid == null) {
				continue;
			}
			String key = text(line.values(), "key");
			if (key == null || key.isBlank() || key.length() > 200) {
				row.reject(RowStatus.INVALID, "key fehlt oder ist länger als 200 Zeichen");
				continue;
			}
			row.id = usersGuid + " · " + key;
			row.label = row.label + ": " + key;
			countByUsersKey.merge(usersGuid + "|" + key, 1, Integer::sum);
			Object value = line.values().get("value");
			row.data = new SettingsData(key,
					value == null || value instanceof String ? (String) value : jsonMapper.writeValueAsString(value));
		}

		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			SettingsData settingsData = (SettingsData) row.data;
			if (countByUsersKey.get(row.fileUsersGuid + "|" + settingsData.key()) > 1) {
				row.reject(RowStatus.INVALID, "steht für diesen User mehrfach in der Datei");
				continue;
			}
			if (!resolveUsersTarget(plan, row)) {
				continue;
			}
			String usersKey = row.targetUsersGuid + "|" + settingsData.key();
			if (existingValueByUsersKey.containsKey(usersKey)) {
				row.status = RowStatus.EXISTING;
				if (!Objects.equals(existingValueByUsersKey.get(usersKey), settingsData.value())) {
					row.addMessage("gibt es schon, im Import mit anderem Wert");
				}
			}
			ConflictDraft conflict = plan.conflictsByFileGuid.get(row.fileUsersGuid);
			if (conflict != null) {
				UsersConflictDecision decision = plan.decisionOf(row.fileUsersGuid);
				boolean taken = decision == null
						|| (decision.settingKeys() != null && decision.settingKeys().contains(settingsData.key()));
				conflict.settings.add(applyConflictChoice(row, settingsData.key(), taken));
			}
		}
		plan.rowsBySection.put(MasterDataSection.USERS_SETTINGS, rows);
	}

	private void planAccessRoleUsersAssignments(ImportPlan plan, List<Line> lines) {
		Set<String> existingUsersKeys = new HashSet<>();
		for (AccessRoleUsersAssignment assignment : accessRoleUsersAssignmentService.getAllAccessRoleUsersAssignments()) {
			existingUsersKeys.add(assignment.getUsersGuid() + "|" + assignment.getAccessRoleKey());
		}
		List<PlannedRow> rows = new ArrayList<>();
		Map<String, Integer> countByUsersKey = new HashMap<>();
		for (Line line : lines) {
			PlannedRow row = new PlannedRow(line);
			rows.add(row);
			UUID usersGuid = readUsersGuid(plan, row, line);
			if (usersGuid == null) {
				continue;
			}
			String accessRoleKey = text(line.values(), "accessRoleKey");
			if (!HelperInputs.isValidKey(accessRoleKey)) {
				row.reject(RowStatus.INVALID, "accessRoleKey fehlt oder ist ungültig");
				continue;
			}
			row.id = usersGuid + " · " + accessRoleKey;
			row.label = row.label + " → " + accessRoleKey;
			row.data = accessRoleKey;
			countByUsersKey.merge(usersGuid + "|" + accessRoleKey, 1, Integer::sum);
		}

		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			String accessRoleKey = (String) row.data;
			if (countByUsersKey.get(row.fileUsersGuid + "|" + accessRoleKey) > 1) {
				row.reject(RowStatus.INVALID, "steht mehrfach in der Datei");
				continue;
			}
			if (!resolveUsersTarget(plan, row)) {
				continue;
			}
			if (!plan.accessRoleKeys.contains(accessRoleKey)) {
				row.reject(RowStatus.ORPHAN, "AR " + accessRoleKey + " gibt es weder bei uns noch angehakt im Import");
			} else if (existingUsersKeys.contains(row.targetUsersGuid + "|" + accessRoleKey)) {
				row.status = RowStatus.EXISTING;
			}
			ConflictDraft conflict = plan.conflictsByFileGuid.get(row.fileUsersGuid);
			if (conflict != null) {
				UsersConflictDecision decision = plan.decisionOf(row.fileUsersGuid);
				boolean taken = decision == null
						|| (decision.accessRoleKeys() != null && decision.accessRoleKeys().contains(accessRoleKey));
				conflict.accessRoles.add(applyConflictChoice(row, accessRoleKey, taken));
			}
		}
		plan.rowsBySection.put(MasterDataSection.ACCESS_ROLE_USERS_ASSIGNMENTS, rows);
	}

	/** Ein User hat höchstens eine ARC. Hat er bei uns schon eine andere, bleibt die. */
	private void planAccessRoleCollectionUsersAssignments(ImportPlan plan, List<Line> lines) {
		Map<UUID, String> existingKeyByUsersGuid = new HashMap<>();
		for (AccessRoleCollectionUsersAssignment assignment : accessRoleCollectionUsersAssignmentService
				.getAllAccessRoleCollectionUsersAssignments()) {
			existingKeyByUsersGuid.put(assignment.getUsersGuid(), assignment.getAccessRoleCollectionKey());
		}
		List<PlannedRow> rows = new ArrayList<>();
		Map<UUID, Integer> countByGuid = new HashMap<>();
		for (Line line : lines) {
			PlannedRow row = new PlannedRow(line);
			rows.add(row);
			UUID usersGuid = readUsersGuid(plan, row, line);
			if (usersGuid == null) {
				continue;
			}
			String accessRoleCollectionKey = text(line.values(), "accessRoleCollectionKey");
			if (!HelperInputs.isValidKey(accessRoleCollectionKey)) {
				row.reject(RowStatus.INVALID, "accessRoleCollectionKey fehlt oder ist ungültig");
				continue;
			}
			row.label = row.label + " → " + accessRoleCollectionKey;
			row.data = accessRoleCollectionKey;
			countByGuid.merge(usersGuid, 1, Integer::sum);
		}

		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			String accessRoleCollectionKey = (String) row.data;
			if (countByGuid.get(row.fileUsersGuid) > 1) {
				row.reject(RowStatus.INVALID, "mehrere ARC für diesen User in der Datei, ein User hat höchstens eine");
				continue;
			}
			if (!resolveUsersTarget(plan, row)) {
				continue;
			}
			String ours = existingKeyByUsersGuid.get(row.targetUsersGuid);
			if (!plan.accessRoleCollectionKeys.contains(accessRoleCollectionKey)) {
				row.reject(RowStatus.ORPHAN,
						"ARC " + accessRoleCollectionKey + " gibt es weder bei uns noch angehakt im Import");
			} else if (accessRoleCollectionKey.equals(ours)) {
				row.status = RowStatus.EXISTING;
			} else if (ours != null) {
				row.reject(RowStatus.CONFLICT, "hat bei uns schon die ARC " + ours + ", sie bleibt");
			}
			ConflictDraft conflict = plan.conflictsByFileGuid.get(row.fileUsersGuid);
			if (conflict != null) {
				UsersConflictDecision decision = plan.decisionOf(row.fileUsersGuid);
				conflict.accessRoleCollection = applyConflictChoice(row, accessRoleCollectionKey,
						decision == null || decision.accessRoleCollection());
			}
		}
		plan.rowsBySection.put(MasterDataSection.ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS, rows);
	}

	/** Wer durch den Import ADMIN bekäme, direkt oder über eine ARC: steht in der Vorschau ganz oben. */
	private static void collectAdminGrants(ImportPlan plan) {
		for (PlannedRow row : plan.rows(MasterDataSection.ACCESS_ROLE_USERS_ASSIGNMENTS)) {
			if (plan.writes(MasterDataSection.ACCESS_ROLE_USERS_ASSIGNMENTS, row)
					&& ADMIN_ACCESS_ROLE_KEY.equals(row.data)) {
				row.addMessage("bekommt ADMIN");
				plan.adminGrants.add(plan.emailOf(row.targetUsersGuid) + " bekommt ADMIN direkt");
			}
		}
		for (PlannedRow row : plan.rows(MasterDataSection.ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS)) {
			if (plan.writes(MasterDataSection.ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS, row)
					&& plan.accessRoleKeysByAccessRoleCollectionKey.getOrDefault((String) row.data, List.of())
							.contains(ADMIN_ACCESS_ROLE_KEY)) {
				row.addMessage("bekommt ADMIN über die ARC");
				plan.adminGrants.add(plan.emailOf(row.targetUsersGuid) + " bekommt ADMIN über " + row.data);
			}
		}
	}

	// ===== Bausteine des Planens =====

	/**
	 * Die Zeilen eines Bereichs mit eigenem Schlüssel (AR, ARC, Gruppen, Endungen, FSC): lesen, doppelte Schlüssel
	 * verwerfen, Vorhandenes erkennen und mit unserem Stand vergleichen, Neues wie beim Anlegen prüfen.
	 */
	private <T> List<PlannedRow> planKeyedRows(List<Line> lines, Class<T> entityClass, Function<T, String> keyOf,
			Function<T, String> displayNameOf, Map<String, ?> existingByKey, Predicate<String> isValidKey,
			String invalidKeyMessage) {
		// Felder, die diese Version nicht kennt, z. B. aus einem neueren Export, stören nicht
		ObjectReader reader = jsonMapper.readerFor(entityClass)
				.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		List<PlannedRow> rows = new ArrayList<>();
		Map<String, Integer> countByKey = new HashMap<>();
		for (Line line : lines) {
			PlannedRow row = new PlannedRow(line);
			rows.add(row);
			if (line.values() == null) {
				row.reject(RowStatus.INVALID, "kein gültiges JSON");
				continue;
			}
			T entity;
			try {
				entity = reader.readValue(line.text());
			} catch (JacksonException e) {
				row.reject(RowStatus.INVALID, "nicht lesbar: " + e.getOriginalMessage());
				continue;
			}
			row.data = entity;
			row.displayName = displayNameOf.apply(entity);
			String key = keyOf.apply(entity);
			if (key != null) {
				row.id = key;
				row.label = key;
				countByKey.merge(key, 1, Integer::sum);
			}
		}

		for (PlannedRow row : rows) {
			if (!row.isNew()) {
				continue;
			}
			T entity = entityClass.cast(row.data);
			String key = keyOf.apply(entity);
			if (key == null || !isValidKey.test(key)) {
				row.reject(RowStatus.INVALID, invalidKeyMessage);
			} else if (countByKey.get(key) > 1) {
				row.reject(RowStatus.INVALID, "steht mehrfach in der Datei");
			} else if (existingByKey.containsKey(key)) {
				row.status = RowStatus.EXISTING;
				addDifferences(row, existingByKey.get(key));
			} else {
				rejectViolations(row, entity);
			}
		}
		return rows;
	}

	/**
	 * Die guid des Users, an dem der Datensatz hängt, beschriftet mit seiner E-Mail. null, wenn sie fehlt: Dann ist
	 * die Zeile schon ungültig.
	 */
	private static UUID readUsersGuid(ImportPlan plan, PlannedRow row, Line line) {
		if (line.values() == null) {
			row.reject(RowStatus.INVALID, "kein gültiges JSON");
			return null;
		}
		String usersGuidText = text(line.values(), "usersGuid");
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			row.reject(RowStatus.INVALID, "usersGuid fehlt oder ist ungültig");
			return null;
		}
		row.id = usersGuidText;
		row.fileUsersGuid = UUID.fromString(usersGuidText);
		row.label = plan.emailOf(row.fileUsersGuid);
		return row.fileUsersGuid;
	}

	/**
	 * An welchen User ein Datensatz aus der Datei geht: an denselben, oder im Konflikt an unseren mit derselben
	 * E-Mail. false, wenn es keinen gibt: Dann ist die Zeile verwaist.
	 */
	private static boolean resolveUsersTarget(ImportPlan plan, PlannedRow row) {
		UUID targetUsersGuid = plan.targetUsersGuidByFileGuid.get(row.fileUsersGuid);
		if (targetUsersGuid == null) {
			row.reject(RowStatus.ORPHAN, "kein User mit dieser guid, weder bei uns noch angehakt im Import");
			return false;
		}
		row.targetUsersGuid = targetUsersGuid;
		return true;
	}

	/**
	 * Bei einem User im Konflikt: Was mit dem Teil passieren würde, kommt in die Auswahl. Abgewählt wird nichts
	 * geschrieben, übernommen geht es an unseren User.
	 */
	private static ConflictItem applyConflictChoice(PlannedRow row, String key, boolean taken) {
		ConflictItem conflictItem = new ConflictItem(key, row.status, taken,
				row.messages.isEmpty() ? null : String.join("; ", row.messages));
		if (!row.isNew()) {
			return conflictItem;
		}
		if (taken) {
			row.addMessage("geht an unseren User mit derselben E-Mail");
		} else {
			row.reject(RowStatus.SKIPPED, "beim User im Konflikt abgewählt");
		}
		return conflictItem;
	}

	/** Dieselben Prüfungen wie beim Anlegen über die Oberfläche (Bean Validation an der Entity) */
	private <T> void rejectViolations(PlannedRow row, T entity) {
		for (ConstraintViolation<T> violation : validator.validate(entity)) {
			row.reject(RowStatus.INVALID, violation.getPropertyPath() + ": " + violation.getMessage());
		}
	}

	/** Bei Vorhandenem: Welche Felder stehen im Import anders als bei uns? Geändert wird trotzdem nichts. */
	private void addDifferences(PlannedRow row, Object ours) {
		Map<String, Object> ourLine = masterDataExportService.toMasterDataLine(ours);
		Set<String> fields = new TreeSet<>(ourLine.keySet());
		fields.addAll(row.values.keySet());
		fields.removeAll(NOT_COMPARED_FIELDS);
		List<String> differingFields = fields.stream()
				.filter(field -> !Objects.equals(comparable(ourLine.get(field)), comparable(row.values.get(field))))
				.toList();
		if (!differingFields.isEmpty()) {
			row.addMessage("gibt es schon, im Import anders: " + String.join(", ", differingFields));
		}
	}

	/** Leeres zählt wie fehlend, Zahlen gleich unabhängig vom Typ (1 und 1L) */
	private static Object comparable(Object value) {
		if (value == null || (value instanceof String text && text.isBlank())
				|| (value instanceof Collection<?> collection && collection.isEmpty())
				|| (value instanceof Map<?, ?> map && map.isEmpty())) {
			return null;
		}
		if (value instanceof Number number) {
			return new BigDecimal(number.toString()).stripTrailingZeros();
		}
		return value;
	}

	/** Nur die Verweise, deren Ziel es gibt. Die übrigen fallen weg und stehen als Meldung an der Zeile. */
	private static List<String> keepKnown(PlannedRow row, List<String> keys, Set<String> knownKeys, String what) {
		List<String> kept = new ArrayList<>();
		for (String key : orEmpty(keys)) {
			if (!knownKeys.contains(key)) {
				row.addMessage(what + " " + key + " gibt es weder bei uns noch im Import, fällt weg");
			} else if (!kept.contains(key)) {
				kept.add(key);
			}
		}
		return kept;
	}

	/** Die Schlüssel der neuen Zeilen eines Bereichs, aber nur, wenn er angehakt ist: sonst entstehen sie nicht */
	private static List<String> newKeys(ImportPlan plan, MasterDataSection section) {
		if (!plan.isSelected(section)) {
			return List.of();
		}
		return plan.rows(section).stream().filter(PlannedRow::isNew).map(row -> row.id).toList();
	}

	// ===== Lesen der Datei =====

	/** Alle Dateien des ZIPs als Text, nach Dateinamen. Ordner im ZIP sind egal. */
	private static Map<String, String> readZip(byte[] zip, List<String> blockers) {
		Map<String, String> files = new HashMap<>();
		if (zip == null || zip.length == 0) {
			blockers.add("keine Datei");
			return files;
		}
		long totalBytes = 0;
		int entryCount = 0;
		try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zipInput.getNextEntry()) != null) {
				if (++entryCount > MAX_ZIP_ENTRIES) {
					blockers.add("mehr als " + MAX_ZIP_ENTRIES + " Dateien im ZIP");
					return files;
				}
				if (entry.isDirectory()) {
					continue;
				}
				long remainingBytes = MAX_UNCOMPRESSED_BYTES - totalBytes;
				byte[] content = zipInput.readNBytes((int) Math.min(remainingBytes + 1, Integer.MAX_VALUE - 8));
				if (content.length > remainingBytes) {
					blockers.add("das ZIP ist entpackt größer als 100 MB");
					return files;
				}
				totalBytes += content.length;
				String fileName = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				files.put(fileName, new String(content, StandardCharsets.UTF_8));
			}
		} catch (IOException | IllegalArgumentException e) {
			blockers.add("keine gültige ZIP-Datei");
		}
		return files;
	}

	private void readManifest(String manifestText, ImportPlan plan) {
		if (manifestText == null) {
			plan.blockers.add("manifest.json fehlt, das ist kein Export der Stammdaten");
			return;
		}
		Map<String, Object> manifest;
		try {
			manifest = jsonMapper.readValue(stripByteOrderMark(manifestText), LINE_TYPE);
		} catch (JacksonException e) {
			plan.blockers.add("manifest.json ist kein gültiges JSON");
			return;
		}
		if (!MasterDataExportService.FORMAT.equals(manifest.get("format"))) {
			plan.blockers.add("manifest.json: unbekanntes Format, das ist kein Export der Stammdaten");
		}
		if (manifest.get("formatVersion") instanceof Integer formatVersion) {
			plan.formatVersion = formatVersion;
			if (formatVersion > MasterDataExportService.FORMAT_VERSION) {
				plan.blockers.add("Format-Version " + formatVersion + " ist neuer als diese App (Version "
						+ MasterDataExportService.FORMAT_VERSION + ")");
			}
		} else {
			plan.blockers.add("manifest.json: formatVersion fehlt");
		}
		plan.exportedAt = text(manifest, "exportedAt");
		plan.exportedBy = text(manifest, "exportedBy");
	}

	/** Eine Zeile pro Datensatz, leere Zeilen zählen nicht. Die Zeilennummer bleibt für die Meldungen. */
	private List<Line> readLines(String content) {
		List<Line> lines = new ArrayList<>();
		String[] texts = stripByteOrderMark(content).split("\\R");
		for (int index = 0; index < texts.length; index++) {
			String text = texts[index].strip();
			if (text.isEmpty()) {
				continue;
			}
			Map<String, Object> values;
			try {
				values = jsonMapper.readValue(text, LINE_TYPE);
			} catch (JacksonException e) {
				values = null;
			}
			lines.add(new Line(index + 1, text, values));
		}
		return lines;
	}

	// ===== Schreiben: nur über die Services, in der Reihenfolge der Bereiche =====

	private void writePlan(ImportPlan plan) {
		for (PlannedRow row : plan.writtenRows(MasterDataSection.ACCESS_ROLES)) {
			AccessRole accessRole = (AccessRole) row.data;
			accessRoleService.createAccessRole(accessRole.getKey(), accessRole.getDisplayName(),
					accessRole.getDescription(), accessRole.isSystem(), accessRole.getBadgeTextColor(),
					accessRole.getBadgeTextShadowColor(), accessRole.getBadgeGradientStart(),
					accessRole.getBadgeGradientEnd(), accessRole.getBadgeBorderColor(), accessRole.getBadgeShadowColor(),
					accessRole.getBadgeGradient(), accessRole.getBadgeStyles(), accessRole.getSymbol(),
					accessRole.getBadgeOpacity(), accessRole.getListingPosition());
		}

		// ARC erst ohne Slaves anlegen: Ein Slave aus demselben Import gibt es sonst vielleicht noch nicht. Die AR
		// kommen wie in der Oberfläche über die ARC-AR-Matrix, alle auf einmal.
		List<AccessRoleCollection> withSlaveArcKeys = new ArrayList<>();
		Map<String, List<String>> accessRoleKeysByAccessRoleCollectionKey = new LinkedHashMap<>();
		for (PlannedRow row : plan.writtenRows(MasterDataSection.ACCESS_ROLE_COLLECTIONS)) {
			AccessRoleCollection accessRoleCollection = (AccessRoleCollection) row.data;
			writeAccessRoleCollection(accessRoleCollection, List.of(), true);
			if (!accessRoleCollection.getAccessRoleKeys().isEmpty()) {
				accessRoleKeysByAccessRoleCollectionKey.put(accessRoleCollection.getKey(),
						accessRoleCollection.getAccessRoleKeys());
			}
			if (!accessRoleCollection.getSlaveArcKeys().isEmpty()) {
				withSlaveArcKeys.add(accessRoleCollection);
			}
		}
		if (!accessRoleKeysByAccessRoleCollectionKey.isEmpty()) {
			accessRoleCollectionService.changeAccessRoleCollectionAccessRoleKeys(accessRoleKeysByAccessRoleCollectionKey);
		}
		for (AccessRoleCollection accessRoleCollection : withSlaveArcKeys) {
			writeAccessRoleCollection(accessRoleCollection, accessRoleCollection.getSlaveArcKeys(), false);
		}

		for (PlannedRow row : plan.writtenRows(MasterDataSection.FILE_EXTENSION_COLLECTIONS)) {
			FileExtensionCollection fileExtensionCollection = (FileExtensionCollection) row.data;
			fileExtensionCollectionService.createFileExtensionCollection(fileExtensionCollection.getKey(),
					fileExtensionCollection.getDisplayName(), fileExtensionCollection.getSymbolShape(),
					fileExtensionCollection.getListingPosition());
		}

		for (PlannedRow row : plan.writtenRows(MasterDataSection.FILE_EXTENSIONS)) {
			FileExtension fileExtension = (FileExtension) row.data;
			fileExtensionService.createFileExtension(fileExtension.getExtension(), fileExtension.getDescription(),
					fileExtension.getFileExtensionCollectionKey(), fileExtension.isDangerous(),
					fileExtension.getListingPosition(), fileExtension.isOcr(), fileExtension.isKi(),
					fileExtension.isImagePreview());
		}

		for (PlannedRow row : plan.writtenRows(MasterDataSection.FILE_SUB_CLASSES)) {
			FileSubClass fileSubClass = (FileSubClass) row.data;
			fileSubClassService.createFileSubClass(fileSubClass.getKey(), fileSubClass.getDisplayName(),
					fileSubClass.getDescription(), fileSubClass.getListingPosition(), fileSubClass.getSymbol(),
					fileSubClass.getColor(), fileSubClass.getReadAccessRoleKeys(), fileSubClass.getWriteAccessRoleKeys());
			if (!fileSubClass.getExtensions().isEmpty()) {
				fileSubClassService.changeFileSubClassExtensions(fileSubClass.getKey(), fileSubClass.getExtensions());
			}
		}

		String startPassword = sha256Hex(START_PASSWORD);
		for (PlannedRow row : plan.writtenRows(MasterDataSection.USERS)) {
			Users users = (Users) row.data;
			usersService.createUsers(users.getGuid(), users.getEmail(), users.getUsername(), users.getVorname(),
					users.getNachname(), users.getCreatedAt(), users.isServiceAccount(), users.getStatus());
			if (usersCredentialsService.isUsersCredentialsAvailable(users.getGuid())) {
				usersCredentialsService.createUsersCredentials(users.getGuid(), startPassword);
			}
		}

		for (PlannedRow row : plan.writtenRows(MasterDataSection.USERS_DETAILS)) {
			UsersDetails usersDetails = (UsersDetails) row.data;
			usersDetailsService.createUsersDetails(row.targetUsersGuid, usersDetails.getInfo(),
					usersDetails.getProfilText(), usersDetails.getWebseite(), usersDetails.getMobile(),
					usersDetails.getSteam(), usersDetails.getDiscord(), usersDetails.getInsta(),
					usersDetails.getKundenNummer(), usersDetails.getLieferantenNummer());
		}

		for (PlannedRow row : plan.writtenRows(MasterDataSection.USERS_SETTINGS)) {
			SettingsData settingsData = (SettingsData) row.data;
			usersSettingsService.createUsersSettings(row.targetUsersGuid, settingsData.key(), settingsData.value());
		}

		// Je User einmal: die vorhandenen AR plus die neuen, so sperrt jeder User seine Tokens nur einmal
		Map<UUID, List<String>> newAccessRoleKeysByUsersGuid = new LinkedHashMap<>();
		for (PlannedRow row : plan.writtenRows(MasterDataSection.ACCESS_ROLE_USERS_ASSIGNMENTS)) {
			newAccessRoleKeysByUsersGuid.computeIfAbsent(row.targetUsersGuid, usersGuid -> new ArrayList<>())
					.add((String) row.data);
		}
		newAccessRoleKeysByUsersGuid.forEach((usersGuid, newAccessRoleKeys) -> {
			List<String> accessRoleKeys = new ArrayList<>(
					accessRoleUsersAssignmentService.getAccessRoleKeysByUsersGuid(usersGuid));
			accessRoleKeys.addAll(newAccessRoleKeys);
			accessRoleUsersAssignmentService.changeAccessRoleUsersAssignments(usersGuid, accessRoleKeys);
		});

		for (PlannedRow row : plan.writtenRows(MasterDataSection.ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS)) {
			accessRoleCollectionUsersAssignmentService.changeAccessRoleCollectionUsersAssignment(row.targetUsersGuid,
					(String) row.data);
		}
	}

	/** Eine ARC anlegen (ohne AR) oder, im zweiten Durchgang, ihre Slaves nachtragen */
	private void writeAccessRoleCollection(AccessRoleCollection accessRoleCollection, List<String> slaveArcKeys,
			boolean create) {
		if (create) {
			accessRoleCollectionService.createAccessRoleCollection(accessRoleCollection.getKey(),
					accessRoleCollection.getDisplayName(), accessRoleCollection.getDescription(), slaveArcKeys,
					accessRoleCollection.getBadgeTextColor(), accessRoleCollection.getBadgeTextShadowColor(),
					accessRoleCollection.getBadgeGradientStart(), accessRoleCollection.getBadgeGradientEnd(),
					accessRoleCollection.getBadgeBorderColor(), accessRoleCollection.getBadgeShadowColor(),
					accessRoleCollection.getBadgeGradient(), accessRoleCollection.getBadgeStyles(),
					accessRoleCollection.getSymbol(), accessRoleCollection.getBadgeOpacity(),
					accessRoleCollection.getListingPosition());
		} else {
			accessRoleCollectionService.updateAccessRoleCollection(accessRoleCollection.getKey(),
					accessRoleCollection.getDisplayName(), accessRoleCollection.getDescription(), slaveArcKeys,
					accessRoleCollection.getBadgeTextColor(), accessRoleCollection.getBadgeTextShadowColor(),
					accessRoleCollection.getBadgeGradientStart(), accessRoleCollection.getBadgeGradientEnd(),
					accessRoleCollection.getBadgeBorderColor(), accessRoleCollection.getBadgeShadowColor(),
					accessRoleCollection.getBadgeGradient(), accessRoleCollection.getBadgeStyles(),
					accessRoleCollection.getSymbol(), accessRoleCollection.getBadgeOpacity(),
					accessRoleCollection.getListingPosition());
		}
	}

	// ===== Kleine Helfer =====

	/** Ein Textfeld der Zeile, null wenn es fehlt oder kein Text ist */
	private static String text(Map<String, Object> values, String field) {
		return values.get(field) instanceof String text ? text : null;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

	private static List<String> orEmpty(List<String> keys) {
		return keys != null ? keys : List.of();
	}

	private static String stripByteOrderMark(String text) {
		return text.startsWith("﻿") ? text.substring(1) : text;
	}

	/** z. B. „Paul Panzer <paul@firma.de>“, ohne Namen nur die E-Mail */
	private static String usersLabel(Users users) {
		String name = String.join(" ", Objects.toString(users.getVorname(), ""), Objects.toString(users.getNachname(), ""))
				.trim();
		return name.isEmpty() ? users.getEmail() : name + " <" + users.getEmail() + ">";
	}

	private static <T> Map<String, T> byKey(List<T> entities, Function<T, String> keyOf) {
		Map<String, T> entitiesByKey = new LinkedHashMap<>();
		entities.forEach(entity -> entitiesByKey.put(keyOf.apply(entity), entity));
		return entitiesByKey;
	}

	/** Wie der Browser ein Passwort vor dem Senden hasht: SHA-256 als Hex */
	private static String sha256Hex(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 fehlt in dieser JVM", e);
		}
	}

	// ===== Der Plan =====

	/** Eine Zeile einer Datei. values ist null, wenn sie kein gültiges JSON ist. */
	private record Line(int number, String text, Map<String, Object> values) {
	}

	/** Eine Einstellung aus der Datei */
	private record SettingsData(String key, String value) {
	}

	/** Eine Zeile der Datei und was die Prüfung über sie weiß */
	private static final class PlannedRow {

		private String id;
		private String label;
		private String displayName;
		private RowStatus status = RowStatus.NEW;
		private final List<String> messages = new ArrayList<>();
		/** die Werte der Zeile, wie sie in der Datei stehen, für den Vergleich mit unserem Stand */
		private final Map<String, Object> values;
		/** die geprüften Daten zum Schreiben, je Bereich eine andere Klasse */
		private Object data;
		/** bei allem, was an einem User hängt: seine guid laut Datei */
		private UUID fileUsersGuid;
		/** bei allem, was an einem User hängt: der User, an den geschrieben wird */
		private UUID targetUsersGuid;

		private PlannedRow(Line line) {
			this.id = "Zeile " + line.number();
			this.label = id;
			this.values = line.values() != null ? line.values() : Map.of();
		}

		private boolean isNew() {
			return status == RowStatus.NEW;
		}

		private void reject(RowStatus rejectedStatus, String message) {
			status = rejectedStatus;
			addMessage(message);
		}

		private void addMessage(String message) {
			if (!messages.contains(message)) {
				messages.add(message);
			}
		}

	}

	/** Ein User im Konflikt, während die abhängigen Bereiche ihn füllen */
	private static final class ConflictDraft {

		private final UUID fileGuid;
		private final UUID existingGuid;
		private final String email;
		private final String label;
		private ConflictItem details;
		private final List<ConflictItem> settings = new ArrayList<>();
		private final List<ConflictItem> accessRoles = new ArrayList<>();
		private ConflictItem accessRoleCollection;

		private ConflictDraft(UUID fileGuid, UUID existingGuid, String email, String label) {
			this.fileGuid = fileGuid;
			this.existingGuid = existingGuid;
			this.email = email;
			this.label = label;
		}

	}

	/** Alles, was die Prüfung über den Import weiß, und was die früheren Bereiche den späteren zur Verfügung stellen */
	private static final class ImportPlan {

		private final Set<MasterDataSection> selectedSections;
		private final Map<String, UsersConflictDecision> usersConflictDecisions;
		private final List<String> blockers = new ArrayList<>();
		private Integer formatVersion;
		private String exportedAt;
		private String exportedBy;
		private final Set<MasterDataSection> presentSections = EnumSet.noneOf(MasterDataSection.class);
		private final Map<MasterDataSection, List<PlannedRow>> rowsBySection = new EnumMap<>(MasterDataSection.class);
		private final List<String> adminGrants = new ArrayList<>();

		// Was es nach den bisherigen Bereichen gibt: bei uns, oder neu in einem angehakten Bereich des Imports
		private final Set<String> accessRoleKeys = new HashSet<>();
		private final Set<String> accessRoleCollectionKeys = new HashSet<>();
		private final Map<String, List<String>> accessRoleKeysByAccessRoleCollectionKey = new HashMap<>();
		private final Set<String> fileExtensionCollectionKeys = new HashSet<>();
		private final Set<String> extensions = new HashSet<>();
		/** guid aus der Datei → guid, an die geschrieben wird. Fehlt sie, ist alles an diesem User verwaist. */
		private final Map<UUID, UUID> targetUsersGuidByFileGuid = new HashMap<>();
		private final Map<UUID, String> emailByUsersGuid = new HashMap<>();
		private final Map<UUID, ConflictDraft> conflictsByFileGuid = new LinkedHashMap<>();

		private ImportPlan(Set<MasterDataSection> selectedSections,
				Map<String, UsersConflictDecision> usersConflictDecisions) {
			this.selectedSections = selectedSections;
			this.usersConflictDecisions = usersConflictDecisions;
		}

		private boolean isSelected(MasterDataSection section) {
			return selectedSections.contains(section);
		}

		private List<PlannedRow> rows(MasterDataSection section) {
			return rowsBySection.getOrDefault(section, List.of());
		}

		/** Wird diese Zeile geschrieben? Nur Neues aus angehakten Bereichen. */
		private boolean writes(MasterDataSection section, PlannedRow row) {
			return isSelected(section) && row.isNew();
		}

		private List<PlannedRow> writtenRows(MasterDataSection section) {
			return rows(section).stream().filter(row -> writes(section, row)).toList();
		}

		/** Die Auswahl zu einem User im Konflikt. null: keine geschickt, dann wird alles Neue übernommen. */
		private UsersConflictDecision decisionOf(UUID fileGuid) {
			return usersConflictDecisions.get(fileGuid.toString());
		}

		private String emailOf(UUID usersGuid) {
			return emailByUsersGuid.getOrDefault(usersGuid, String.valueOf(usersGuid));
		}

		private MasterDataImportCheck toCheck() {
			List<SectionCheck> sectionChecks = new ArrayList<>();
			for (MasterDataSection section : MasterDataSection.values()) {
				List<PlannedRow> rows = rows(section);
				Map<RowStatus, Long> countByStatus = rows.stream()
						.collect(Collectors.groupingBy(row -> row.status, Collectors.counting()));
				int problemCount = (int) (countByStatus.getOrDefault(RowStatus.INVALID, 0L)
						+ countByStatus.getOrDefault(RowStatus.ORPHAN, 0L)
						+ countByStatus.getOrDefault(RowStatus.CONFLICT, 0L));
				sectionChecks.add(new SectionCheck(section, presentSections.contains(section), isSelected(section),
						countByStatus.getOrDefault(RowStatus.NEW, 0L).intValue(),
						countByStatus.getOrDefault(RowStatus.EXISTING, 0L).intValue(),
						countByStatus.getOrDefault(RowStatus.SKIPPED, 0L).intValue(), problemCount,
						rows.stream().map(row -> new RowCheck(row.id, row.label, row.displayName, row.status,
								List.copyOf(row.messages))).toList()));
			}
			List<UsersConflict> usersConflicts = conflictsByFileGuid.values().stream()
					.map(conflict -> new UsersConflict(conflict.fileGuid.toString(), conflict.existingGuid.toString(),
							conflict.email, conflict.label, conflict.details, List.copyOf(conflict.settings),
							List.copyOf(conflict.accessRoles), conflict.accessRoleCollection))
					.toList();
			return new MasterDataImportCheck(blockers.isEmpty(), List.copyOf(blockers), formatVersion, exportedAt,
					exportedBy, sectionChecks, usersConflicts, List.copyOf(adminGrants));
		}

	}

}
