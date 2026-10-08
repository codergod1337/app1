package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.dms.model.DmsSolrDocumentRevision;
import codergod1337.app1.dms.model.DmsSolrDocumentRevisionForClient;
import codergod1337.app1.dms.repository.DmsSolrDocumentRevisionRepository;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.sftp.service.SftpPathService;
import codergod1337.app1.system.sftp.service.SftpService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Revision der DMS-Dokumente (gebaut 2026-10-08 nach dem DmsRevisionService der Vorlage): Vor jedem Schreiben
 * eines Menschen sichert der Schreibservice den Stand davor als ZIP auf dem SFTP und trägt ihn hier ins Register ein.
 * Dieser Service schreibt nie in Solr: Er sichert, liest Sicherungen zurück und räumt sie weg. Er hängt nicht am
 * Schreibservice, sonst gäbe es einen Kreis, der Schreibservice ruft ihn.
 *
 * Das ZIP ist die Wahrheit, die Tabelle nur das Register: {@code document.json} trägt jedes stored field des Dokuments,
 * auch Volltext und Vorschaubild, Zurückladen braucht keinen Tika-Lauf; {@code manifest.json} macht das ZIP ohne
 * Datenbank lesbar. {@code _version_} steht nirgends: Solr vergibt es bei jedem Schreiben selbst, ein mitgeschickter
 * alter Wert wäre eine Bedingung und ein 409, und gespeichert sagt es nichts (User 2026-10-08). Im ZIP liegt der
 * komplette Volltext: {@code dmsBackup} ist so schützenswert wie die Dateien selbst.
 *
 * Ablage: {@code dmsBackup/JJJJ/MM/TT[_n]/<id>_<revisionNo>.zip}, Tagesordner und Zähler wie bei den Dateien
 * ({@link SftpPathService#relativePathForFileName}).
 */
@Service
public class DmsSolrDocumentRevisionService {

	/** Wurzel der Revisions-ZIPs auf dem SFTP, relativ zur SFTP-Wurzel, darunter die Tagesordner wie bei den Dateien */
	public static final String BACKUP_DIRECTORY = "dmsBackup";

	private static final String DOCUMENT_JSON = "document.json";
	private static final String MANIFEST_JSON = "manifest.json";
	private static final String FIELD_NAME_SEPARATOR = ",";

	private static final Logger log = LoggerFactory.getLogger(DmsSolrDocumentRevisionService.class);

	private static final TypeReference<Map<String, Object>> DOCUMENT_TYPE = new TypeReference<>() {
	};

	private final DmsSolrDocumentRevisionRepository dmsSolrDocumentRevisionRepository;
	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService;
	private final SftpService sftpService;
	private final SftpPathService sftpPathService;
	private final CurrentUsersProvider currentUsersProvider;
	private final ObjectMapper objectMapper;

	public DmsSolrDocumentRevisionService(DmsSolrDocumentRevisionRepository dmsSolrDocumentRevisionRepository,
			DmsExactFieldValueResolveService dmsExactFieldValueResolveService,
			DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService, SftpService sftpService,
			SftpPathService sftpPathService, CurrentUsersProvider currentUsersProvider, ObjectMapper objectMapper) {
		this.dmsSolrDocumentRevisionRepository = dmsSolrDocumentRevisionRepository;
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.dmsCurrentUsersGrantsService = dmsCurrentUsersGrantsService;
		this.sftpService = sftpService;
		this.sftpPathService = sftpPathService;
		this.currentUsersProvider = currentUsersProvider;
		this.objectMapper = objectMapper;
	}

	// ===== Sichern und Aufräumen, gerufen vom Schreibservice =====

	/**
	 * Sichern, der erste Schritt jedes revisionierten Schreibvorgangs, vor dem Schreiben in Solr: Scheitert die
	 * Sicherung, scheitert die ganze Änderung, es gibt nie eine Änderung ohne ihr ZIP. Wiederholbar: Bleibt nach einem
	 * halben Fehlschlag (das ZIP lag, Solr scheiterte danach) eine Registerzeile derselben Nummer zurück, wird sie
	 * ersetzt statt am UNIQUE zu scheitern. Keine Rechteprüfung hier, die hat der Schreibservice mit dem Schreib-fq
	 * gemacht; der Auslöser ist der current user.
	 *
	 * @param documentBeforeWrite das ganze Dokument vor der Änderung, alle stored fields
	 * @param changedFieldNames   die Felder, deren Wert der Vorgang wirklich ändert, fürs Register
	 */
	@Transactional
	public void saveRevisionBeforeWrite(Map<String, Object> documentBeforeWrite, Collection<String> changedFieldNames) {
		String documentId = String.valueOf(documentBeforeWrite.get(SolrCore.ID_FIELD_NAME));
		long revisionNo = longOf(documentBeforeWrite.get(DmsSolrDocumentFieldNames.FILE_ID_REVISION_NO));
		UUID changedByUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		Instant changedAt = Instant.now();
		List<String> fieldNames = new ArrayList<>(new LinkedHashSet<>(changedFieldNames));

		byte[] zip = zipOf(documentBeforeWrite, documentId, revisionNo, changedByUsersGuid, changedAt, fieldNames);
		String relativePath = BACKUP_DIRECTORY + "/"
				+ sftpPathService.relativePathForFileName(documentId + "_" + revisionNo + ".zip");
		sftpService.writeFileData(new ByteArrayInputStream(zip), relativePath);

		DmsSolrDocumentRevision revision = dmsSolrDocumentRevisionRepository
				.findByDocumentIdAndRevisionNo(documentId, revisionNo)
				.orElseGet(() -> new DmsSolrDocumentRevision(documentId, revisionNo));
		revision.setBackupPath(relativePath);
		revision.setChangedByUsersGuid(changedByUsersGuid);
		revision.setChangedAt(changedAt);
		revision.setChangedFieldNames(String.join(FIELD_NAME_SEPARATOR, fieldNames));
		dmsSolrDocumentRevisionRepository.save(revision);
		log.info("Revision {} von {} gesichert von {}: {} ({} Bytes), Felder {}", revisionNo, documentId,
				changedByUsersGuid, relativePath, zip.length, fieldNames);
	}

	/**
	 * Alle Revisionen eines Dokuments wegräumen, die ZIPs vom SFTP und die Registerzeilen: für das endgültige Löschen,
	 * das es noch nicht gibt. In jedem ZIP steht der Volltext, ein „endgültig gelöscht“, das sie liegen ließe, wäre
	 * keins. Erst die Bytes, dann das Register, sonst verlöre man bei einem Fehler auf halbem Weg den Pfad zum ZIP. Ein
	 * fehlendes ZIP bricht nichts ab, das steht im Log, die Zeile geht trotzdem weg. Keine Rechteprüfung hier.
	 *
	 * @return wie viele Revisionen weggeräumt wurden
	 */
	@Transactional
	public int deleteAllRevisionsOfDocument(String documentId) {
		List<DmsSolrDocumentRevision> revisions = dmsSolrDocumentRevisionRepository
				.findByDocumentIdOrderByRevisionNoDesc(documentId);
		for (DmsSolrDocumentRevision revision : revisions) {
			try {
				sftpService.deleteFileData(revision.getBackupPath());
			} catch (RuntimeException e) {
				log.warn("Revisions-ZIP {} von {} ließ sich nicht löschen ({}), die Registerzeile geht trotzdem weg: {}",
						revision.getRevisionNo(), documentId, revision.getBackupPath(), e.getMessage());
			}
		}
		dmsSolrDocumentRevisionRepository.deleteAll(revisions);
		if (!revisions.isEmpty()) {
			log.info("Revisionen von {} weggeräumt: {}", documentId, revisions.size());
		}
		return revisions.size();
	}

	// ===== Lesen =====

	/**
	 * Die Revisionsliste eines Dokuments, neueste zuerst, allein aus Postgres, ohne SFTP. Tor ist die Lese-ACL am
	 * Dokument: Darf der User das Dokument nicht sehen, 404 wie auf der Dokumentseite. Die ACLs im ZIP sind Daten, nie
	 * eine Erlaubnis.
	 */
	@PreAuthorize("hasRole('DMS')")
	public List<DmsSolrDocumentRevisionForClient> loadDmsSolrDocumentRevisions(String documentId) {
		requireReadableDocument(documentId);
		List<DmsSolrDocumentRevision> revisions = dmsSolrDocumentRevisionRepository
				.findByDocumentIdOrderByRevisionNoDesc(documentId);
		Set<UUID> usersGuids = new LinkedHashSet<>();
		for (DmsSolrDocumentRevision revision : revisions) {
			usersGuids.add(revision.getChangedByUsersGuid());
		}
		Map<String, String> displayNames = dmsCurrentUsersGrantsService.usersDisplayNameByGuid(usersGuids);
		List<DmsSolrDocumentRevisionForClient> forClient = new ArrayList<>(revisions.size());
		for (DmsSolrDocumentRevision revision : revisions) {
			String guid = revision.getChangedByUsersGuid().toString();
			forClient.add(new DmsSolrDocumentRevisionForClient(revision.getRevisionNo(), revision.getChangedAt(), guid,
					displayNames.getOrDefault(guid, guid), fieldNamesOf(revision.getChangedFieldNames())));
		}
		return forClient;
	}

	/**
	 * Eine Revision als fertiges JSON: Das Backend entpackt das ZIP, das Frontend rechnet den Unterschied selbst.
	 * Dasselbe Tor wie die Liste, dazu die Feldgrenze des Users wie auf der Dokumentseite: Das ZIP trägt jedes Feld,
	 * der User bekommt nur die, die er lesen darf, sonst wäre die Revision ein Weg an der Feldgrenze vorbei.
	 */
	@PreAuthorize("hasRole('DMS')")
	public Map<String, Object> loadDmsSolrDocumentRevision(String documentId, long revisionNo) {
		requireReadableDocument(documentId);
		List<String> readable = dmsExactFieldValueResolveService.currentUserDmsAccessForClient()
				.readableFieldAndHookNamesOfCoreForCurrentUser();
		Map<String, Object> limited = new LinkedHashMap<>();
		for (Map.Entry<String, Object> field : readRevisionDocument(documentId, revisionNo).entrySet()) {
			if (readable.contains(field.getKey())) {
				limited.put(field.getKey(), field.getValue());
			}
		}
		return limited;
	}

	/**
	 * Das document.json einer Revision, ohne Tor: für das Zurückladen des Schreibservice, der sein Schreib-fq selbst
	 * prüft. 404 ohne Registerzeile. Datumswerte kommen als ISO-8601-Text, Listen als Listen, so nimmt die Formprüfung
	 * sie an.
	 */
	public Map<String, Object> readRevisionDocument(String documentId, long revisionNo) {
		DmsSolrDocumentRevision revision = dmsSolrDocumentRevisionRepository
				.findByDocumentIdAndRevisionNo(documentId, revisionNo)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"keine Revision " + revisionNo + " zu diesem Dokument"));
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		sftpService.readFileData(revision.getBackupPath(), buffer);
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()),
				StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (DOCUMENT_JSON.equals(entry.getName())) {
					return objectMapper.readValue(zip.readAllBytes(), DOCUMENT_TYPE);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Revisions-ZIP nicht lesbar: " + revision.getBackupPath(), e);
		}
		throw new IllegalStateException("Revisions-ZIP ohne " + DOCUMENT_JSON + ": " + revision.getBackupPath());
	}

	/** Das Tor der Revisionen: die Dokumentseite mit der Leseprüfung des Users, 404 wenn unsichtbar oder unbekannt */
	private void requireReadableDocument(String documentId) {
		dmsExactFieldValueResolveService.loadDmsSolrDocumentForDocumentPage(documentId);
	}

	// ===== Das ZIP =====

	/**
	 * document.json plus manifest.json. Keine gepflegte Feldliste: Geschrieben wird, was das Dokument trägt, ein neuer
	 * Hook liegt von selbst mit im ZIP. Zeitpunkte als ISO-8601-Text, unabhängig von der Einstellung des ObjectMapper,
	 * damit das ZIP für immer gleich aussieht und die Formprüfung die Werte beim Zurückladen direkt annimmt.
	 */
	private byte[] zipOf(Map<String, Object> documentBeforeWrite, String documentId, long revisionNo,
			UUID changedByUsersGuid, Instant changedAt, List<String> changedFieldNames) {
		Map<String, Object> document = new LinkedHashMap<>();
		for (Map.Entry<String, Object> field : documentBeforeWrite.entrySet()) {
			if (!SolrDocumentService.VERSION_FIELD_NAME.equals(field.getKey())) {
				document.put(field.getKey(), jsonValue(field.getValue()));
			}
		}
		Map<String, Object> manifest = new LinkedHashMap<>();
		manifest.put("documentId", documentId);
		manifest.put("revisionNo", revisionNo);
		manifest.put("changedByUsersGuid", changedByUsersGuid.toString());
		manifest.put("changedAt", changedAt.toString());
		manifest.put("changedFieldNames", changedFieldNames);

		try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
			zip.putNextEntry(new ZipEntry(DOCUMENT_JSON));
			zip.write(objectMapper.writeValueAsBytes(document));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry(MANIFEST_JSON));
			zip.write(objectMapper.writeValueAsBytes(manifest));
			zip.closeEntry();
			zip.finish();
			return buffer.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Revisions-ZIP ließ sich nicht bauen: " + documentId + "_" + revisionNo, e);
		}
	}

	/** Instant als ISO-8601-Text, auch in Listen; alles andere unverändert */
	private static Object jsonValue(Object value) {
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

	private static long longOf(Object value) {
		return value instanceof Number number ? number.longValue() : 0L;
	}

	private static List<String> fieldNamesOf(String changedFieldNames) {
		if (changedFieldNames == null || changedFieldNames.isBlank()) {
			return List.of();
		}
		return List.of(changedFieldNames.split(FIELD_NAME_SEPARATOR));
	}

}
