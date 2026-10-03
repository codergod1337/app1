package codergod1337.app1.system.masterdata.service;

import codergod1337.app1.file.fileextension.service.FileExtensionCollectionService;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.hook.service.SolrHookGroupService;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.access.model.AccessRoleUsersAssignment;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import codergod1337.app1.system.access.service.AccessRoleCollectionUsersAssignmentService;
import codergod1337.app1.system.access.service.AccessRoleService;
import codergod1337.app1.system.access.service.AccessRoleUsersAssignmentService;
import codergod1337.app1.system.masterdata.model.MasterDataExport;
import codergod1337.app1.system.masterdata.model.MasterDataSection;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.UsersDetails;
import codergod1337.app1.system.user.model.UsersSettings;
import codergod1337.app1.system.user.service.UsersDetailsService;
import codergod1337.app1.system.user.service.UsersService;
import codergod1337.app1.system.user.service.UsersSettingsService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Export der Stammdaten: alles, was im Admin gepflegt wird, als ZIP. Darin eine manifest.json und je Bereich eine
 * JSONL-Datei, ein Datensatz pro Zeile, die Felder heißen wie in der Entity. Leere Felder fallen weg.
 *
 * Nie im Export: Zugangsdaten (Passwort-Hash), Sitzungen, 2FA und der Zähler der Fehlversuche. Eine Datei mit Hashes
 * wäre ein Passwortspeicher, der im Downloads-Ordner liegt.
 */
@Service
public class MasterDataExportService {

	/** Kennung des Formats in der manifest.json */
	public static final String FORMAT = "app1-masterdata";
	/** Version des Formats. Ändert sich das Format, kann der Import ältere Exporte daran erkennen. */
	public static final int FORMAT_VERSION = 1;
	public static final String MANIFEST_FILE_NAME = "manifest.json";

	/** Technische Felder, die nie in die Datei gehören, je Entity */
	private static final Map<Class<?>, Set<String>> NOT_EXPORTED_FIELDS = Map.of(
			UsersDetails.class, Set.of("pwUnsuccessfull"),
			UsersSettings.class, Set.of("id"),
			AccessRoleUsersAssignment.class, Set.of("id"),
			SolrField.class, Set.of("id"));

	private static final TypeReference<LinkedHashMap<String, Object>> LINE_TYPE = new TypeReference<>() {
	};

	private final AccessRoleService accessRoleService;
	private final AccessRoleCollectionService accessRoleCollectionService;
	private final FileExtensionCollectionService fileExtensionCollectionService;
	private final FileExtensionService fileExtensionService;
	private final FileSubClassService fileSubClassService;
	private final SolrHookGroupService solrHookGroupService;
	private final SolrHookService solrHookService;
	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;
	private final UsersService usersService;
	private final UsersDetailsService usersDetailsService;
	private final UsersSettingsService usersSettingsService;
	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;
	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;
	private final CurrentUsersProvider currentUsersProvider;
	private final JsonMapper jsonMapper;

	public MasterDataExportService(AccessRoleService accessRoleService,
			AccessRoleCollectionService accessRoleCollectionService,
			FileExtensionCollectionService fileExtensionCollectionService, FileExtensionService fileExtensionService,
			FileSubClassService fileSubClassService, SolrHookGroupService solrHookGroupService,
			SolrHookService solrHookService, SolrCoreService solrCoreService,
			SolrFieldService solrFieldService, UsersService usersService, UsersDetailsService usersDetailsService,
			UsersSettingsService usersSettingsService, AccessRoleUsersAssignmentService accessRoleUsersAssignmentService,
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService,
			CurrentUsersProvider currentUsersProvider, JsonMapper jsonMapper) {
		this.accessRoleService = accessRoleService;
		this.accessRoleCollectionService = accessRoleCollectionService;
		this.fileExtensionCollectionService = fileExtensionCollectionService;
		this.fileExtensionService = fileExtensionService;
		this.fileSubClassService = fileSubClassService;
		this.solrHookGroupService = solrHookGroupService;
		this.solrHookService = solrHookService;
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
		this.usersService = usersService;
		this.usersDetailsService = usersDetailsService;
		this.usersSettingsService = usersSettingsService;
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
		this.currentUsersProvider = currentUsersProvider;
		this.jsonMapper = jsonMapper;
	}

	/** Die angehakten Bereiche als ZIP, in der Reihenfolge des Imports. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public MasterDataExport exportMasterData(Set<MasterDataSection> sections) {
		Map<MasterDataSection, List<Map<String, Object>>> linesBySection = new EnumMap<>(MasterDataSection.class);
		for (MasterDataSection section : MasterDataSection.values()) {
			if (sections.contains(section)) {
				linesBySection.put(section, toMasterDataLines(entitiesOf(section)));
			}
		}

		Map<String, Integer> countByFileName = new LinkedHashMap<>();
		Map<MasterDataSection, Integer> counts = new EnumMap<>(MasterDataSection.class);
		linesBySection.forEach((section, lines) -> {
			countByFileName.put(section.getFileName(), lines.size());
			counts.put(section, lines.size());
		});
		Map<String, Object> manifest = new LinkedHashMap<>();
		manifest.put("format", FORMAT);
		manifest.put("formatVersion", FORMAT_VERSION);
		manifest.put("exportedAt", OffsetDateTime.now().toString());
		manifest.put("exportedBy", currentUsersProvider.getCurrentUsers().getEmail());
		manifest.put("files", countByFileName);

		ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(zipBytes, StandardCharsets.UTF_8)) {
			zip.setLevel(Deflater.BEST_COMPRESSION);
			writeZipEntry(zip, MANIFEST_FILE_NAME, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
			for (Map.Entry<MasterDataSection, List<Map<String, Object>>> sectionLines : linesBySection.entrySet()) {
				StringBuilder content = new StringBuilder();
				for (Map<String, Object> line : sectionLines.getValue()) {
					content.append(jsonMapper.writeValueAsString(line)).append('\n');
				}
				writeZipEntry(zip, sectionLines.getKey().getFileName(), content.toString());
			}
		} catch (IOException e) {
			// ein ZIP im Arbeitsspeicher: das passiert praktisch nie
			throw new UncheckedIOException(e);
		}

		String fileName = "stammdaten-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
				+ ".zip";
		return new MasterDataExport(fileName, zipBytes.toByteArray(), counts);
	}

	/**
	 * Eine Entity als Zeile der Datei: die Felder wie in der Entity, ohne die technischen, leere fallen weg. Auch der
	 * Import nutzt das, um eine Zeile der Datei mit unserem Stand zu vergleichen.
	 */
	public Map<String, Object> toMasterDataLine(Object entity) {
		LinkedHashMap<String, Object> line = jsonMapper.convertValue(entity, LINE_TYPE);
		line.keySet().removeAll(NOT_EXPORTED_FIELDS.getOrDefault(entity.getClass(), Set.of()));
		line.values().removeIf(value -> value == null || (value instanceof String text && text.isBlank())
				|| (value instanceof Collection<?> collection && collection.isEmpty())
				|| (value instanceof Map<?, ?> map && map.isEmpty()));
		return line;
	}

	private List<Map<String, Object>> toMasterDataLines(List<?> entities) {
		return entities.stream().map(this::toMasterDataLine).toList();
	}

	/** Alle Datensätze eines Bereichs, wie sie die Services liefern */
	private List<?> entitiesOf(MasterDataSection section) {
		return switch (section) {
		case ACCESS_ROLES -> accessRoleService.getAllAccessRoles();
		case ACCESS_ROLE_COLLECTIONS -> accessRoleCollectionService.getAllAccessRoleCollections();
		case FILE_EXTENSION_COLLECTIONS -> fileExtensionCollectionService.getAllFileExtensionCollections();
		case FILE_EXTENSIONS -> fileExtensionService.getAllFileExtensions();
		case FILE_SUB_CLASSES -> fileSubClassService.getAllFileSubClasses();
		case SOLR_HOOK_GROUPS -> solrHookGroupService.getAllSolrHookGroups();
		case SOLR_HOOKS -> solrHookService.getAllSolrHooks();
		case SOLR_CORES -> solrCoreService.getAllSolrCores();
		case SOLR_FIELDS -> solrFieldService.getAllSolrFields();
		case USERS -> usersService.getAllUsers();
		case USERS_DETAILS -> usersDetailsService.getAllUsersDetails();
		case USERS_SETTINGS -> usersSettingsService.getAllUsersSettings();
		case ACCESS_ROLE_USERS_ASSIGNMENTS -> accessRoleUsersAssignmentService.getAllAccessRoleUsersAssignments();
		case ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS ->
			accessRoleCollectionUsersAssignmentService.getAllAccessRoleCollectionUsersAssignments();
		};
	}

	private static void writeZipEntry(ZipOutputStream zip, String fileName, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(fileName));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

}
