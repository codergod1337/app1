package codergod1337.app1.solr.grant.service;

import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService.SolrResolvedDocumentsPage;
import codergod1337.app1.solr.grant.model.GrantableDocumentsPreviewPage;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.repository.SolrDocumentGrantedDocsPerCoreRepository;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.service.UsersService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Dateifreigabe: Solr-Dokumente eines Kerns an einen User freigeben, kernunabhängig. Der Freigebende nennt exakte
 * Feldwerte wie beim Auflösen, sieht die Treffer, die er selbst sieht und ändern darf, und gibt genau die frei: die
 * guid des Empfängers per Atomic Update in userGrantedAccess jedes Treffers. Die Wahrheit für den Zugriff ist das
 * Dokument, die Zeile im Register ist Protokoll und Grundlage für Entzug und Ablauf.
 *
 * Schnappschuss: Ein später angelegtes Dokument mit denselben Werten trägt die guid nicht. Nur userGrantedAccess wird
 * angefasst, kein anderes Feld: Das Verfahren gilt in jedem Kern, auch in einem Spiegelkern ohne changingUsers.
 *
 * Überlappende Freigaben: Deckt eine zweite aktive Freigabe desselben Users im selben Kern ein Dokument ebenfalls ab,
 * bleibt die guid beim Entzug der ersten darin stehen. Dafür löst der Entzug die anderen aktiven Freigaben des Users
 * noch einmal auf und nimmt die guid nur aus den Dokumenten, die danach keine Freigabe mehr trägt.
 *
 * Wer freigeben darf: vorerst ADMIN. Später eine eigene AR, z. B. für die Datenpflege.
 */
@Service
public class SolrDocumentGrantedDocsPerCoreService {

	private static final Logger log = LoggerFactory.getLogger(SolrDocumentGrantedDocsPerCoreService.class);

	/** Neueste zuerst, in der Datenbank wie in der Liste */
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "grantedAt", "id");

	private final SolrDocumentGrantedDocsPerCoreRepository repository;
	private final SolrCoreService solrCoreService;
	private final UsersService usersService;
	private final SolrExactFieldValueResolveService solrExactFieldValueResolveService;
	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final SolrDocumentService solrDocumentService;
	private final CurrentUsersProvider currentUsersProvider;
	private final ObjectMapper objectMapper;

	public SolrDocumentGrantedDocsPerCoreService(SolrDocumentGrantedDocsPerCoreRepository repository,
			SolrCoreService solrCoreService, UsersService usersService,
			SolrExactFieldValueResolveService solrExactFieldValueResolveService,
			SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService, SolrDocumentService solrDocumentService,
			CurrentUsersProvider currentUsersProvider, ObjectMapper objectMapper) {
		this.repository = repository;
		this.solrCoreService = solrCoreService;
		this.usersService = usersService;
		this.solrExactFieldValueResolveService = solrExactFieldValueResolveService;
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.solrDocumentService = solrDocumentService;
		this.currentUsersProvider = currentUsersProvider;
		this.objectMapper = objectMapper;
	}

	/** Alle Zeilen, auch entzogene und abgelaufene, neueste zuerst: das Protokoll für die Maske. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrDocumentGrantedDocsPerCore> getAllDocumentGrants() {
		return repository.findAll(NEWEST_FIRST);
	}

	/**
	 * Die Dateifreigaben des current users in diesem Kern, auch entzogene und abgelaufene, neueste zuerst: der Tab „Meine
	 * Freigaben“. Ohne @PreAuthorize, der User fragt nach sich selbst, die guid kommt aus dem Token.
	 */
	public List<SolrDocumentGrantedDocsPerCore> documentGrantsOfCurrentUserInCore(String coreKey) {
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		List<SolrDocumentGrantedDocsPerCore> own = new ArrayList<>();
		for (SolrDocumentGrantedDocsPerCore grant : repository.findByGranteeUsersGuid(usersGuid)) {
			if (grant.getCoreKey().equals(coreKey)) {
				own.add(grant);
			}
		}
		own.sort(newestFirst());
		return own;
	}

	/**
	 * Die wirksamen Dateifreigaben dieser User in diesem Kern, neueste zuerst: der Kontrollblick des Admins auf ein
	 * Dokument, dessen userGrantedAccess diese guids trägt. Wirksam heißt ACTIVE und nicht abgelaufen. Kein Schutz, nur
	 * Anzeige: Die Wahrheit für den Zugriff steht im Dokument.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrDocumentGrantedDocsPerCore> documentGrantsInForceOfUsersInCore(Collection<UUID> usersGuids,
			String coreKey) {
		Instant now = Instant.now();
		List<SolrDocumentGrantedDocsPerCore> inForce = new ArrayList<>();
		for (UUID usersGuid : new LinkedHashSet<>(usersGuids)) {
			for (SolrDocumentGrantedDocsPerCore grant : repository.findByGranteeUsersGuid(usersGuid)) {
				boolean active = GrantEntityStateService.ACTIVE.equals(grant.getStatus())
						&& (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
				if (active && grant.getCoreKey().equals(coreKey)) {
					inForce.add(grant);
				}
			}
		}
		inForce.sort(newestFirst());
		return inForce;
	}

	private static Comparator<SolrDocumentGrantedDocsPerCore> newestFirst() {
		return Comparator.comparing(SolrDocumentGrantedDocsPerCore::getGrantedAt)
				.thenComparing(SolrDocumentGrantedDocsPerCore::getId).reversed();
	}

	/**
	 * Die Treffer, die eine Freigabe mit diesen Bedingungen öffnen würde, seitenweise: nur Dokumente, die der
	 * Freigebende sieht UND ändern darf, denn Freigeben ist ein Schreibvorgang. Ohne Bedingung zeigt die Vorschau alles,
	 * was er sieht, freigeben lässt sich das nicht. grantAllowed nur mit Bedingung, mindestens einem und weniger als
	 * MAX_DOCUMENTS_PER_DOCUMENT_GRANT Treffern.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public GrantableDocumentsPreviewPage previewGrantableDocumentsPage(String coreKey, JsonNode fieldValuesByFieldName,
			int page, int size) {
		SolrResolvedDocumentsPage resolved = solrExactFieldValueResolveService
				.resolveWritableDocumentsPageByExactFieldValues(coreKey, fieldValuesByFieldName, page, size);
		List<String> readableFieldAndHookNames = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(coreKey).readableFieldAndHookNamesOfCoreForCurrentUser();
		boolean hasConditions = fieldValuesByFieldName != null && fieldValuesByFieldName.isObject()
				&& !fieldValuesByFieldName.isEmpty();
		boolean grantAllowed = hasConditions && resolved.totalFound() > 0
				&& resolved.totalFound() < SolrExactFieldValueResolveService.MAX_DOCUMENTS_PER_DOCUMENT_GRANT;
		return new GrantableDocumentsPreviewPage(resolved.totalFound(),
				SolrExactFieldValueResolveService.MAX_DOCUMENTS_PER_DOCUMENT_GRANT, grantAllowed, resolved.page(),
				resolved.size(), resolved.documents(), readableFieldAndHookNames);
	}

	/**
	 * Gibt dem User die Dokumente frei, die der Freigebende mit diesen Bedingungen selbst sieht und ändern darf. Erst
	 * die Zeile im Register, dann Solr: Scheitert Solr, rollt die Zeile zurück. 404 bei unbekanntem User oder Kern, 400
	 * ohne Bedingung, bei einem Ablauf in der Vergangenheit oder wenn die Bedingungen kein Dokument treffen.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrDocumentGrantedDocsPerCore grantDocumentsToUsers(UUID granteeUsersGuid, String coreKey,
			JsonNode fieldValuesByFieldName, Instant validUntil) {
		usersService.getUsersByGuid(granteeUsersGuid);
		solrCoreService.getSolrCoreByKey(coreKey);
		requireValidUntilInFuture(validUntil);
		requireConditions(fieldValuesByFieldName);
		// Freigeben ist ein Schreibvorgang: writeAcl muss eine AR des Freigebenden enthalten, sonst wird nichts geschrieben
		List<String> ids = solrExactFieldValueResolveService.resolveWritableDocumentIdsByExactFieldValues(coreKey,
				fieldValuesByFieldName);
		if (ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"die Bedingungen treffen kein Dokument, das du selbst siehst und ändern darfst");
		}
		Instant now = Instant.now();
		UUID grantedByUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		SolrDocumentGrantedDocsPerCore grant = repository.save(new SolrDocumentGrantedDocsPerCore(granteeUsersGuid,
				coreKey, objectMapper.writeValueAsString(fieldValuesByFieldName), grantedByUsersGuid, now, validUntil,
				GrantEntityStateService.ACTIVE, ids.size()));
		solrDocumentService.atomicUpdateManyDocuments(coreKey, ids, Map.of(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME,
				Map.of("add-distinct", granteeUsersGuid.toString())));
		log.info("Dateifreigabe {}: {} Dokumente im Kern {} an {} von {}", grant.getId(), ids.size(), coreKey,
				granteeUsersGuid, grantedByUsersGuid);
		return grant;
	}

	/**
	 * Entzieht die Freigabe: die guid aus allen Dokumenten, die die Bedingungen treffen und sie tragen, dann Status
	 * REVOKED. 404 ohne die Zeile, 409 wenn sie nicht mehr aktiv ist. Ohne Schreibprüfung, mit Absicht: Eine Freigabe
	 * muss sich immer zurücknehmen lassen, auch wenn der Admin die Schreibrolle inzwischen verloren hat, und es wird
	 * nur entfernt, was diese Freigabe selbst geschrieben hat.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrDocumentGrantedDocsPerCore revokeDocumentGrant(Long id) {
		SolrDocumentGrantedDocsPerCore grant = findForWrite(id);
		if (!GrantEntityStateService.ACTIVE.equals(grant.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Freigabe ist nicht mehr aktiv");
		}
		removeGranteeGuidFromDocumentsAndEndGrant(grant, GrantEntityStateService.REVOKED, Instant.now(),
				currentUsersProvider.getCurrentUsersGuid());
		return repository.save(grant);
	}

	/**
	 * Setzt alle aktiven Zeilen mit erreichtem Ablauf auf EXPIRED und nimmt die guid aus den Dokumenten. Anders als bei
	 * der Feldfreigabe wirkt eine abgelaufene Dateifreigabe bis dahin weiter, denn die Wahrheit steht im Dokument. Bis
	 * es den System-User gibt, stößt das der Admin an, danach ein Systemjob einmal am Tag.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public int expireDocumentGrants() {
		Instant now = Instant.now();
		UUID byUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		int expired = 0;
		for (SolrDocumentGrantedDocsPerCore grant : repository.findByStatus(GrantEntityStateService.ACTIVE)) {
			if (grant.getValidUntil() != null && !grant.getValidUntil().isAfter(now)) {
				removeGranteeGuidFromDocumentsAndEndGrant(grant, GrantEntityStateService.EXPIRED, now, byUsersGuid);
				repository.save(grant);
				expired++;
			}
		}
		return expired;
	}

	/**
	 * Die guid aus den Dokumenten dieser Freigabe nehmen und den Status setzen. Dokumente, die eine andere noch
	 * wirksame Freigabe desselben Users im selben Kern ebenfalls abdeckt, behalten die guid: Sonst bräche der Entzug
	 * von A die Freigabe B, wenn beide dasselbe Dokument öffnen.
	 */
	private void removeGranteeGuidFromDocumentsAndEndGrant(SolrDocumentGrantedDocsPerCore grant, String status,
			Instant now, UUID byUsersGuid) {
		String coreKey = grant.getCoreKey();
		UUID granteeUsersGuid = grant.getGranteeUsersGuid();
		List<String> ids = solrExactFieldValueResolveService.resolveDocumentIdsCarryingGranteeUsersGuid(coreKey,
				readFieldValuesByFieldName(grant), granteeUsersGuid);

		Set<String> keep = new HashSet<>();
		for (SolrDocumentGrantedDocsPerCore other : repository.findByGranteeUsersGuid(granteeUsersGuid)) {
			boolean sameGrant = other.getId().equals(grant.getId());
			if (sameGrant || !other.getCoreKey().equals(coreKey) || !isInForce(other, now)) {
				continue;
			}
			keep.addAll(solrExactFieldValueResolveService.resolveDocumentIdsCarryingGranteeUsersGuid(coreKey,
					readFieldValuesByFieldName(other), granteeUsersGuid));
		}
		List<String> remove = new ArrayList<>();
		for (String id : ids) {
			if (!keep.contains(id)) {
				remove.add(id);
			}
		}

		solrDocumentService.atomicUpdateManyDocuments(coreKey, remove, Map.of(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME,
				Map.of("remove", granteeUsersGuid.toString())));
		grant.changeStatus(status, now, byUsersGuid);
		log.info("Dateifreigabe {} {}: guid aus {} von {} Dokumenten im Kern {} genommen, {} bleiben durch andere Freigaben",
				grant.getId(), status, remove.size(), ids.size(), coreKey, ids.size() - remove.size());
	}

	/** Wirksam heißt Status ACTIVE und Ablauf noch nicht erreicht, dieselbe Regel wie bei der Feldfreigabe */
	private static boolean isInForce(SolrDocumentGrantedDocsPerCore grant, Instant now) {
		return GrantEntityStateService.ACTIVE.equals(grant.getStatus())
				&& (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
	}

	private JsonNode readFieldValuesByFieldName(SolrDocumentGrantedDocsPerCore grant) {
		try {
			return objectMapper.readTree(grant.getFieldValues());
		} catch (JacksonException e) {
			throw new IllegalStateException("Dateifreigabe " + grant.getId() + ": fieldValues ist kein JSON", e);
		}
	}

	private static void requireConditions(JsonNode fieldValuesByFieldName) {
		if (fieldValuesByFieldName == null || !fieldValuesByFieldName.isObject() || fieldValuesByFieldName.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"fieldValuesByFieldName braucht mindestens ein Feld mit Wert: eine Freigabe ohne Bedingung gibt es nicht");
		}
	}

	private static void requireValidUntilInFuture(Instant validUntil) {
		if (validUntil != null && !validUntil.isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validUntil liegt in der Vergangenheit");
		}
	}

	private SolrDocumentGrantedDocsPerCore findForWrite(Long id) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Dateifreigabe mit dieser id"));
	}

}
