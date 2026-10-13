package codergod1337.app1.solr.dms.service;

import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.dms.model.DmsAccessForClient;
import codergod1337.app1.solr.dms.model.DmsReindexOption;
import codergod1337.app1.solr.dms.model.DmsSolrDocumentRevision;
import codergod1337.app1.solr.dms.model.DmsSolrDocumentRevisionForClient;
import codergod1337.app1.solr.dms.repository.DmsSolrDocumentRevisionRepository;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.sftp.service.SftpPathService;
import codergod1337.app1.system.sftp.service.SftpService;
import codergod1337.app1.system.tika.TikaService;
import codergod1337.app1.system.user.model.UsersDebuffType;
import codergod1337.app1.system.user.model.UsersDebuffs;
import codergod1337.app1.system.user.service.UsersDebuffsService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Alles, was der SolrDocumentDmsService zum Lesen und Schreiben braucht, aber selbst kein Lesen oder Schreiben eines
 * Dokuments ist: die Rechte und Filter einer Anfrage, die Wörter und Gründe der Suche, die ACLs aus der Dateiart, die
 * Revision vor einem Schreiben, die Änderungen eines Nachzieh-Laufs. Spricht nie selbst mit Solr.
 */
@Service
public class SolrDocumentDmsUtilsService {

	private static final Logger log = LoggerFactory.getLogger(SolrDocumentDmsUtilsService.class);

	private static final String DMS_CORE_KEY = "DMS";

	private final CurrentUsersProvider currentUsersProvider;
	private final SolrFieldService solrFieldService;
	private final UsersDebuffsService usersDebuffsService;
	private final FileSubClassService fileSubClassService;
	private final DmsSolrDocumentRevisionRepository dmsSolrDocumentRevisionRepository;
	private final SftpService sftpService;
	private final SftpPathService sftpPathService;
	private final ObjectMapper objectMapper;
	private final TikaService tikaService;

	public SolrDocumentDmsUtilsService(CurrentUsersProvider currentUsersProvider, SolrFieldService solrFieldService,
			UsersDebuffsService usersDebuffsService, FileSubClassService fileSubClassService,
			DmsSolrDocumentRevisionRepository dmsSolrDocumentRevisionRepository, SftpService sftpService,
			SftpPathService sftpPathService, ObjectMapper objectMapper, TikaService tikaService) {
		this.currentUsersProvider = currentUsersProvider;
		this.solrFieldService = solrFieldService;
		this.usersDebuffsService = usersDebuffsService;
		this.fileSubClassService = fileSubClassService;
		this.dmsSolrDocumentRevisionRepository = dmsSolrDocumentRevisionRepository;
		this.sftpService = sftpService;
		this.sftpPathService = sftpPathService;
		this.objectMapper = objectMapper;
		this.tikaService = tikaService;
	}

	// ===== Rechte und Filter einer Anfrage =====

	public String buildUserReadAclFilterQuery() {
		// Der Rechte-Filter des angemeldeten Users als Klausel: Ein Dokument fordert mit readAcl eine seiner Rollen; leere
		// readAcl heißt keine Einschränkung; oder es trägt seine guid in userGrantedAccess, die Freigabe an den User
		// (User 2026-10-10, gilt damit beim Lesen, Suchen, Auflösen und Laden). Als fq direkt, oder mit ODER zu anderem.
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		String granted = "userGrantedAccess:"
				+ ClientUtils.escapeQueryChars(currentUsersProvider.getCurrentUsersGuid().toString());
		// -readAcl:* heißt NOT "irgendein Wert", also readAcl leer; das *:* davor braucht Solr für ein NOT innerhalb von OR
		if (accessRoleKeys.isEmpty()) {
			return "(*:* -readAcl:*) OR " + granted;
		}
		return "readAcl:(" + String.join(" OR ", accessRoleKeys) + ") OR (*:* -readAcl:*) OR " + granted;
	}

	public ModifiableSolrParams buildUserReadAclForSolrRequest(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das vor der Anfrage an Solr auf: hängt den Rechte-Filter des angemeldeten Users an die Params.
		// add statt set: ein fq, der schon drin ist (z. B. status:ACTIVE), bleibt; Solr verknüpft alle fq mit UND
		params.add("fq", buildUserReadAclFilterQuery());
		return params;
	}

	public List<String> buildUserSolrResponseFieldList(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das vor der Anfrage auf, außer bei ADMIN oder SOLR_USERS_VIEW_ALL: setzt fl, die field
		// list, auf die Felder, die der User sehen darf, und gibt dieselbe Liste zurück, z. B. für die Prüfung der
		// Bedingungen. Hooks sind Felder des Kerns wie alle anderen, mit eigener Leseliste je Kern.
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		List<String> fieldNames = new ArrayList<>();
		// alle Felder des Kerns DMS, der Reihe nach, Hooks eingeschlossen
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DMS_CORE_KEY)) {
			// match: eine der read_access_role_keys des Feldes ist eine Rolle des Users
			boolean match = solrField.getReadAccessRoleKeys().stream().anyMatch(accessRoleKeys::contains);
			if (match) {
				fieldNames.add(solrField.getName());
			}
		}
		params.set("fl", String.join(",", fieldNames));
		return fieldNames;
	}

	public ModifiableSolrParams buildUserSolrResponseStateVisibility(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das vor der Anfrage auf, außer bei ADMIN: ein User sieht nur Dokumente mit Status
		// ACTIVE oder ARCHIVED, PENDING_ADMIN und DELETED bleiben draußen.
		params.add("fq", "status:(ACTIVE OR ARCHIVED)");
		return params;
	}

	public List<String> buildUserDebuffsForSolrRequest(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das für jeden außer ADMIN auf: je Debuff vom Typ RESOLVE des angemeldeten Users ein fq
		// solrFieldName:(wert1 OR wert2). Gibt die Namen der Debuff-Felder zurück: Eine Bedingung des Clients auf so ein
		// Feld wird verworfen, der Debuff steht über jeder Rolle.
		List<String> debuffedFieldNames = new ArrayList<>();
		// das Flag steht im Token: ohne Debuffs kein Zugriff auf Postgres
		if (!currentUsersProvider.hasCurrentUsersDebuffs()) {
			return debuffedFieldNames;
		}
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		for (UsersDebuffs usersDebuffs : usersDebuffsService.getUsersDebuffsByUsersGuid(usersGuid)) {
			if (usersDebuffs.getType() != UsersDebuffType.RESOLVE) {
				continue;
			}
			List<String> escapedValues = new ArrayList<>();
			for (String value : usersDebuffs.getValues()) {
				// escapeQueryChars: Backslash vor jedes Solr-Sonderzeichen im Wert
				escapedValues.add(ClientUtils.escapeQueryChars(value));
			}
			// ergibt z. B. kundenNummer:(4711 OR 4712)
			params.add("fq", usersDebuffs.getSolrFieldName() + ":(" + String.join(" OR ", escapedValues) + ")");
			debuffedFieldNames.add(usersDebuffs.getSolrFieldName());
		}
		return debuffedFieldNames;
	}

	public void buildClientConditionsForSolrRequest(ModifiableSolrParams params,
			Map<String, List<String>> valuesByFieldName, List<String> readableFieldNames,
			List<String> debuffedFieldNames) {
		// Auflösen und Suche rufen das für die Bedingungen des Clients: je Feld ein fq feld:(wert1 OR wert2), gleiches
		// Feld heißt ODER. readableFieldNames null heißt, der User darf jedes Feld; ein Debuff-Feld lässt die Bedingung weg.
		for (Map.Entry<String, List<String>> condition : valuesByFieldName.entrySet()) {
			String fieldName = condition.getKey();
			// Bedingung auf ein Feld, das der User nicht lesen darf: 403, nicht still ignorieren
			if (readableFieldNames != null && !readableFieldNames.contains(fieldName)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Bedingung auf " + fieldName);
			}
			// auf einem Debuff-Feld zählt nur der Debuff, die Bedingung des Clients fällt weg
			if (debuffedFieldNames.contains(fieldName)) {
				continue;
			}
			// feld:() wäre für Solr ein Syntaxfehler
			if (condition.getValue() == null || condition.getValue().isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Werte für " + fieldName);
			}
			List<String> escapedValues = new ArrayList<>();
			for (String value : condition.getValue()) {
				escapedValues.add(ClientUtils.escapeQueryChars(value));
			}
			// ergibt z. B. fileSubClassKey:(DATENBLATT OR RECHNUNG)
			params.add("fq", fieldName + ":(" + String.join(" OR ", escapedValues) + ")");
		}
	}

	public List<String> readableFieldNamesOfCurrentUser() {
		// Der Freigabe-Service fragt das: ADMIN und SOLR_USERS_VIEW_ALL lesen jedes Feld des Kerns, alle anderen die mit
		// passender Lese-AR, dieselbe Regel wie für ihre fl
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (accessRoleKeys.contains("ADMIN") || accessRoleKeys.contains("SOLR_USERS_VIEW_ALL")) {
			List<String> fieldNames = new ArrayList<>();
			for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DMS_CORE_KEY)) {
				fieldNames.add(solrField.getName());
			}
			return fieldNames;
		}
		// die Params sind hier nur Mittel zum Zweck, die Liste ist das Ergebnis
		return buildUserSolrResponseFieldList(new ModifiableSolrParams());
	}

	public DmsAccessForClient accessForClient() {
		// Die Masken fragen das einmal nach dem Login: was der User im Kern DMS lesen und wonach er auflösen darf, ob
		// Vollsicht, ob Suche
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		boolean everyField = accessRoleKeys.contains("ADMIN") || accessRoleKeys.contains("SOLR_USERS_VIEW_ALL");
		boolean search = accessRoleKeys.contains("ADMIN") || accessRoleKeys.contains("DMS_SUCHE");
		List<String> readableFieldNames = readableFieldNamesOfCurrentUser();
		return new DmsAccessForClient(DMS_CORE_KEY, everyField, search, readableFieldNames, readableFieldNames);
	}

	public Map<String, Object> limitToReadableFields(Map<String, Object> document) {
		// Die Feldgrenze des Users auf eine Map, die alles trägt (eine Revision aus dem ZIP): ADMIN und
		// SOLR_USERS_VIEW_ALL bekommen alles, alle anderen nur die Felder mit passender Lese-AR
		List<String> readableFieldNames = readableFieldNamesOfCurrentUser();
		Map<String, Object> limited = new LinkedHashMap<>();
		for (Map.Entry<String, Object> field : document.entrySet()) {
			if (readableFieldNames.contains(field.getKey())) {
				limited.put(field.getKey(), field.getValue());
			}
		}
		return limited;
	}

	public boolean isWritableByCurrentUser(Map<String, Object> document) {
		// Schreibrecht an einem geladenen Dokument: ADMIN immer, sonst muss writeAcl eine Rolle des Users enthalten
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (accessRoleKeys.contains("ADMIN")) {
			return true;
		}
		if (document.get("writeAcl") instanceof Collection<?> writeAcl) {
			for (Object role : writeAcl) {
				if (accessRoleKeys.contains(role)) {
					return true;
				}
			}
		}
		return false;
	}

	// ===== Werte des Clients gegen die Felddefinition =====

	public Map<String, Object> checkClientFieldValues(Map<String, Object> clientFieldValues, boolean creating) {
		// Upload (creating) und Bearbeiten rufen das: nur Felder des Kerns, nur die mit erlaubter Schreibregel (die anderen
		// fallen still weg), required beim Anlegen, jeder Wert nach dem Typ seines Feldes; alle Beanstandungen in einer 400.
		// Die Antwort hat die Reihenfolge des Kerns.
		List<String> problems = new ArrayList<>();
		Map<String, Object> checked = new LinkedHashMap<>();
		Set<String> knownFieldNames = new LinkedHashSet<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DMS_CORE_KEY)) {
			String name = solrField.getName();
			knownFieldNames.add(name);
			boolean clientMay = creating ? solrField.getClientWriteRule().isClientAllowedToSetOnCreate()
					: solrField.getClientWriteRule().isClientAllowedToChangeOnUpdate();
			if (!clientFieldValues.containsKey(name)) {
				// required heißt: der Client muss es beim Anlegen mitschicken
				if (creating && clientMay && solrField.isRequired()) {
					problems.add(name + " fehlt");
				}
				continue;
			}
			if (!clientMay) {
				continue;
			}
			checked.put(name, checkedFieldValue(solrField, clientFieldValues.get(name), problems));
		}
		for (String name : clientFieldValues.keySet()) {
			if (!knownFieldNames.contains(name)) {
				problems.add(name + " ist kein Feld des Kerns DMS");
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		return checked;
	}

	private static Object checkedFieldValue(SolrField solrField, Object value, List<String> problems) {
		// multiValued braucht eine Liste, einwertig einen Wert; jeder Eintrag nach dem Typ des Feldes
		if (value == null) {
			return null;
		}
		if (solrField.isMultiValued()) {
			if (!(value instanceof List<?> values)) {
				problems.add(solrField.getName() + " braucht eine Liste");
				return null;
			}
			List<Object> converted = new ArrayList<>();
			for (Object entry : values) {
				Object scalar = checkedScalar(solrField, entry, problems);
				if (scalar != null) {
					converted.add(scalar);
				}
			}
			return converted;
		}
		if (value instanceof List<?>) {
			problems.add(solrField.getName() + " ist einwertig, bekam aber eine Liste");
			return null;
		}
		return checkedScalar(solrField, value, problems);
	}

	private static Object checkedScalar(SolrField solrField, Object value, List<String> problems) {
		// der Wert als das, was Solr für den Typ erwartet: Text, long, double, boolean, Date
		if (value == null) {
			return null;
		}
		String text = value.toString().trim();
		try {
			switch (solrField.getType()) {
				case STRING, EMAIL, TEXT_EDGE, TEXT_SUCHE -> {
					return text;
				}
				case LONG -> {
					return value instanceof Number number ? number.longValue() : Long.parseLong(text);
				}
				case DOUBLE -> {
					return value instanceof Number number ? number.doubleValue() : Double.parseDouble(text);
				}
				case BOOLEAN -> {
					if (value instanceof Boolean bool) {
						return bool;
					}
					if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
						throw new IllegalArgumentException(text);
					}
					return Boolean.parseBoolean(text);
				}
				case DATE -> {
					if (value instanceof Date date) {
						return date;
					}
					// ein nackter Tag (JJJJ-MM-TT) zählt als Mitternacht UTC, sonst ISO 8601 mit Zone
					if (text.length() == 10) {
						return Date.from(java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
					}
					return Date.from(Instant.parse(text));
				}
				default -> throw new IllegalArgumentException(text);
			}
		} catch (RuntimeException e) {
			problems.add(solrField.getName() + ": " + text + " passt nicht zum Typ " + solrField.getType());
			return null;
		}
	}

	// ===== Suche: Wörter und Gründe =====

	public List<String> searchWordsOf(String text) {
		// Die Suche ruft das für die Gründe in Java: die Wörter des Suchtexts klein und ohne + davor; Wörter mit - davor
		// sind ausgeschlossen und fehlen.
		List<String> words = new ArrayList<>();
		for (String word : text.split("\\s+")) {
			if (word.isEmpty() || word.startsWith("-")) {
				continue;
			}
			String bare = word.startsWith("+") ? word.substring(1) : word;
			if (!bare.isEmpty()) {
				words.add(bare.toLowerCase(Locale.ROOT));
			}
		}
		return words;
	}

	public String startingWithSearchWord(Object fieldValue, List<String> searchWords) {
		// Die Suche ruft das je durchsuchtem Feld ohne Highlighting: der erste Wert, der mit einem Suchwort beginnt (so
		// trifft der Zwilling), als Schnipsel mit STX und ETX um den Anfang; null, wenn kein Wert passt.
		if (fieldValue == null) {
			return null;
		}
		// multiValued kommt als Liste, ein einzelner Wert nicht
		Collection<?> values = fieldValue instanceof Collection<?> many ? many : List.of(fieldValue);
		for (Object value : values) {
			String valueText = String.valueOf(value);
			String lowerValueText = valueText.toLowerCase(Locale.ROOT);
			for (String word : searchWords) {
				if (lowerValueText.startsWith(word)) {
					return "\u0002" + valueText.substring(0, word.length()) + "\u0003" + valueText.substring(word.length());
				}
			}
		}
		return null;
	}

	// ===== Dateiart =====

	public void addAccessControlListsFromFileSubClass(SolrInputDocument update, Object fileSubClassKey) {
		// edit und das Nachziehen rufen das: readAcl und writeAcl als set aus der Dateiart, wie sie jetzt in Postgres
		// steht. Ohne Dateiart 400, eine unbekannte meldet der Dateiart-Service mit 404.
		if (fileSubClassKey == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileSubClassKey fehlt");
		}
		FileSubClass fileSubClass = fileSubClassService.getFileSubClassByKey(fileSubClassKey.toString());
		update.addField("readAcl", Map.of("set", List.copyOf(fileSubClass.getReadAccessRoleKeys())));
		update.addField("writeAcl", Map.of("set", List.copyOf(fileSubClass.getWriteAccessRoleKeys())));
	}

	// ===== Revision: der Stand vor jedem Schreiben eines Menschen =====

	public void saveRevisionBeforeWrite(Map<String, Object> wholeDocument, Collection<String> changedFieldNames) {
		// Jede Schreibmethode eines Menschen ruft das vor dem send mit dem ganzen Dokument: der Stand davor als ZIP auf
		// den SFTP und eine Zeile im Register. Scheitert das, wird nicht geschrieben.
		String id = String.valueOf(wholeDocument.get("id"));
		long revisionNo = wholeDocument.get("fileIdRevisionNo") instanceof Number number ? number.longValue() : 0L;
		UUID changedByUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		Instant changedAt = Instant.now();
		List<String> fieldNames = new ArrayList<>(new LinkedHashSet<>(changedFieldNames));
		byte[] zip = revisionZipOf(wholeDocument, id, revisionNo, changedByUsersGuid, changedAt, fieldNames);
		// dmsBackup/JJJJ/MM/TT/<id>_<revisionNo>.zip, Tagesordner wie bei den Dateien
		String relativePath = "dmsBackup/" + sftpPathService.relativePathForFileName(id + "_" + revisionNo + ".zip");
		sftpService.writeFileData(new ByteArrayInputStream(zip), relativePath);
		// nach einem halben Fehlschlag (ZIP lag, Solr scheiterte danach) wird die Zeile derselben Nummer ersetzt
		DmsSolrDocumentRevision revision = dmsSolrDocumentRevisionRepository.findByDocumentIdAndRevisionNo(id, revisionNo)
				.orElseGet(() -> new DmsSolrDocumentRevision(id, revisionNo));
		revision.setBackupPath(relativePath);
		revision.setChangedByUsersGuid(changedByUsersGuid);
		revision.setChangedAt(changedAt);
		revision.setChangedFieldNames(String.join(",", fieldNames));
		dmsSolrDocumentRevisionRepository.save(revision);
		log.info("Revision {} von {} gesichert von {}: {} ({} Bytes), Felder {}", revisionNo, id, changedByUsersGuid,
				relativePath, zip.length, fieldNames);
	}

	public List<DmsSolrDocumentRevisionForClient> getRevisions(String id) {
		// Die History-Box ruft das, nachdem der Controller das Dokument mit den Rechten des Users geladen hat: die Zeilen
		// des Registers, neueste zuerst, allein aus Postgres, ohne SFTP
		List<DmsSolrDocumentRevisionForClient> forClient = new ArrayList<>();
		for (DmsSolrDocumentRevision revision : dmsSolrDocumentRevisionRepository.findByDocumentIdOrderByRevisionNoDesc(id)) {
			String guid = revision.getChangedByUsersGuid().toString();
			List<String> fieldNames = revision.getChangedFieldNames() == null || revision.getChangedFieldNames().isBlank()
					? List.of() : List.of(revision.getChangedFieldNames().split(","));
			// nur die guid, den Namen löst das Frontend auf (User 2026-10-10)
			forClient.add(new DmsSolrDocumentRevisionForClient(revision.getRevisionNo(), revision.getChangedAt(), guid,
					fieldNames));
		}
		return forClient;
	}

	public Map<String, Object> readRevision(String id, long revisionNo) {
		// Das document.json einer Revision aus dem ZIP auf dem SFTP, alle Felder, Zeitpunkte als ISO-Text, Listen als
		// Listen. 404 ohne Registerzeile. Ohne Feldgrenze: die legt der Aufrufer, das Zurückladen braucht alles.
		DmsSolrDocumentRevision revision = dmsSolrDocumentRevisionRepository.findByDocumentIdAndRevisionNo(id, revisionNo)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"keine Revision " + revisionNo + " zu diesem Dokument"));
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		sftpService.readFileData(revision.getBackupPath(), buffer);
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()),
				StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if ("document.json".equals(entry.getName())) {
					return objectMapper.readValue(zip.readAllBytes(), new TypeReference<Map<String, Object>>() {
					});
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Revisions-ZIP nicht lesbar: " + revision.getBackupPath(), e);
		}
		throw new IllegalStateException("Revisions-ZIP ohne document.json: " + revision.getBackupPath());
	}

	private byte[] revisionZipOf(Map<String, Object> wholeDocument, String id, long revisionNo, UUID changedByUsersGuid,
			Instant changedAt, List<String> changedFieldNames) {
		// document.json mit jedem Feld außer _version_ (das vergibt Solr bei jedem Schreiben neu), manifest.json macht
		// das ZIP ohne Datenbank lesbar; Zeitpunkte als ISO-8601-Text, damit das ZIP für immer gleich aussieht
		Map<String, Object> document = new LinkedHashMap<>();
		for (Map.Entry<String, Object> field : wholeDocument.entrySet()) {
			if (!"_version_".equals(field.getKey())) {
				document.put(field.getKey(), jsonValue(field.getValue()));
			}
		}
		Map<String, Object> manifest = new LinkedHashMap<>();
		manifest.put("documentId", id);
		manifest.put("revisionNo", revisionNo);
		manifest.put("changedByUsersGuid", changedByUsersGuid.toString());
		manifest.put("changedAt", changedAt.toString());
		manifest.put("changedFieldNames", changedFieldNames);
		try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
			zip.putNextEntry(new ZipEntry("document.json"));
			zip.write(objectMapper.writeValueAsBytes(document));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("manifest.json"));
			zip.write(objectMapper.writeValueAsBytes(manifest));
			zip.closeEntry();
			zip.finish();
			return buffer.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Revisions-ZIP ließ sich nicht bauen: " + id + "_" + revisionNo, e);
		}
	}

	private static Object jsonValue(Object value) {
		// Solr liefert pdate als Date: als ISO-8601-Text ins ZIP, auch in Listen; alles andere unverändert
		if (value instanceof Date date) {
			return date.toInstant().toString();
		}
		if (value instanceof Instant instant) {
			return instant.toString();
		}
		if (value instanceof List<?> values) {
			List<Object> converted = new ArrayList<>(values.size());
			for (Object element : values) {
				converted.add(jsonValue(element));
			}
			return converted;
		}
		return value;
	}

	// ===== Nachziehen: die Änderungen eines Dokuments je Option =====

	public void addReindexChanges(SolrInputDocument update, Map<String, Object> document, Set<DmsReindexOption> options) {
		// Der Nachzieh-Lauf ruft das je Dokument: je Option ein Block, der seine Felder als set ins Update legt
		String id = String.valueOf(document.get("id"));
		if (options.contains(DmsReindexOption.ACLS_FROM_FILE_SUB_CLASS) && document.get("fileSubClassKey") != null) {
			try {
				addAccessControlListsFromFileSubClass(update, document.get("fileSubClassKey"));
			} catch (ResponseStatusException e) {
				// eine Dateiart, die es nicht mehr gibt: das Dokument behält seine ACLs, steht im Log
				log.warn("Nachziehen {}: Dateiart {} unbekannt, ACLs bleiben", id, document.get("fileSubClassKey"));
			}
		}
		if (options.contains(DmsReindexOption.SEARCHABLE_WORDS_FROM_BINARY) && document.get("sftpPath") != null) {
			try {
				// die Datei vom SFTP in den Speicher, dann Tika: der Text ist der neue Volltext
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				sftpService.readFileData(String.valueOf(document.get("sftpPath")), bytes);
				TikaService.TikaResult tika = tikaService.read(new ByteArrayInputStream(bytes.toByteArray()),
						String.valueOf(document.get("fileName")), String.valueOf(document.get("mimeType")));
				update.addField("searchableWords", Collections.singletonMap("set", tika.text().isBlank() ? null : tika.text()));
			} catch (RuntimeException e) {
				// Datei fehlt auf dem SFTP oder Tika liest sie nicht: Volltext bleibt, steht im Log
				log.warn("Nachziehen {}: Volltext nicht erneuert: {}", id, e.getMessage());
			}
		}
	}

}
