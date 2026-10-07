package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsResolvedDocumentsPage;
import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.grant.model.GrantableDocumentsPreviewPage;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.service.GrantEntityStateService;
import codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.service.UsersService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Dateifreigabe im Kern DMS (Vorgabe 2026-10-07, je Kern ein eigener Service): DMS-Dokumente an einen User
 * freigeben, entziehen, ablaufen lassen. Der Freigebende nennt exakte Feldwerte wie beim Auflösen, sieht die Treffer,
 * die er selbst schreiben darf, und gibt genau die frei: die guid des Empfängers in userGrantedAccess jedes Treffers,
 * über den Schreibservice des DMS. Die Wahrheit für den Zugriff ist das Dokument, die Zeile im Register
 * (SolrDocumentGrantedDocsPerCore, kernübergreifende Tabelle) ist Protokoll und Grundlage für Entzug und Ablauf.
 *
 * Schnappschuss: Ein später angelegtes Dokument mit denselben Werten trägt die guid nicht. Überlappende Freigaben:
 * Deckt eine zweite aktive Freigabe desselben Users ein Dokument ebenfalls ab, bleibt die guid beim Entzug der ersten
 * darin stehen. Entzug und Ablauf laufen mit den Rechten des current users: Was er nicht schreiben darf, behält die
 * guid, das steht im Log. Später läuft der Ablauf als System-User, der in jeder Schreib-ACL steht.
 *
 * Wer freigeben darf: vorerst ADMIN. Später eine eigene AR, z. B. für die Datenpflege.
 */
@Service
public class DmsDocumentGrantService {

	private static final Logger log = LoggerFactory.getLogger(DmsDocumentGrantService.class);

	private final SolrDocumentGrantedDocsPerCoreService documentGrantRegisterService;
	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final DmsSolrDocumentWriteService dmsSolrDocumentWriteService;
	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final UsersService usersService;
	private final CurrentUsersProvider currentUsersProvider;
	private final ObjectMapper objectMapper;

	public DmsDocumentGrantService(SolrDocumentGrantedDocsPerCoreService documentGrantRegisterService,
			DmsExactFieldValueResolveService dmsExactFieldValueResolveService,
			DmsSolrDocumentWriteService dmsSolrDocumentWriteService,
			SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService, UsersService usersService,
			CurrentUsersProvider currentUsersProvider, ObjectMapper objectMapper) {
		this.documentGrantRegisterService = documentGrantRegisterService;
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.dmsSolrDocumentWriteService = dmsSolrDocumentWriteService;
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.usersService = usersService;
		this.currentUsersProvider = currentUsersProvider;
		this.objectMapper = objectMapper;
	}

	/**
	 * Die Treffer, die eine Freigabe mit diesen Bedingungen öffnen würde, seitenweise: nur Dokumente, die der
	 * Freigebende schreiben darf, denn Freigeben ist ein Schreibvorgang. grantAllowed nur mit Bedingung, mindestens
	 * einem und weniger als 500 Treffern.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public GrantableDocumentsPreviewPage previewGrantableDmsDocumentsPage(JsonNode fieldValuesByFieldName, int page,
			int size) {
		DmsResolvedDocumentsPage resolved = dmsExactFieldValueResolveService
				.resolveWritableDmsSolrDocumentsPageForDocumentGrantPreview(fieldValuesByFieldName, page, size);
		List<String> readableFieldAndHookNames = solrCurrentUserCoreAccessService
				.readAndWriteAccessOfCurrentUserForCore(DmsSolrDocumentFieldNames.CORE_KEY)
				.readableFieldAndHookNamesOfCoreForCurrentUser();
		boolean hasConditions = fieldValuesByFieldName != null && fieldValuesByFieldName.isObject()
				&& !fieldValuesByFieldName.isEmpty();
		boolean grantAllowed = hasConditions && resolved.totalFound() > 0
				&& resolved.totalFound() < DmsExactFieldValueResolveService.MAX_DOCUMENTS_PER_DOCUMENT_GRANT;
		return new GrantableDocumentsPreviewPage(resolved.totalFound(),
				DmsExactFieldValueResolveService.MAX_DOCUMENTS_PER_DOCUMENT_GRANT, grantAllowed, resolved.page(),
				resolved.size(), resolved.documents(), readableFieldAndHookNames);
	}

	/**
	 * Gibt dem User die DMS-Dokumente frei, die der Freigebende mit diesen Bedingungen selbst schreiben darf. Erst die
	 * Zeile im Register, dann Solr: Scheitert Solr, rollt die Zeile zurück. 404 bei unbekanntem User, 400 ohne
	 * Bedingung, bei einem Ablauf in der Vergangenheit oder wenn die Bedingungen kein Dokument treffen.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrDocumentGrantedDocsPerCore grantDmsDocumentsToUsers(UUID granteeUsersGuid, JsonNode fieldValuesByFieldName,
			Instant validUntil) {
		usersService.getUsersByGuid(granteeUsersGuid);
		if (validUntil != null && !validUntil.isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validUntil liegt in der Vergangenheit");
		}
		if (fieldValuesByFieldName == null || !fieldValuesByFieldName.isObject() || fieldValuesByFieldName.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"fieldValuesByFieldName braucht mindestens ein Feld mit Wert: eine Freigabe ohne Bedingung gibt es nicht");
		}
		List<String> ids = dmsExactFieldValueResolveService
				.resolveWritableDmsSolrDocumentIdsForDocumentGrant(fieldValuesByFieldName);
		if (ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"die Bedingungen treffen kein Dokument, das du selbst ändern darfst");
		}
		SolrDocumentGrantedDocsPerCore grant = documentGrantRegisterService.recordDocumentGrant(granteeUsersGuid,
				DmsSolrDocumentFieldNames.CORE_KEY, objectMapper.writeValueAsString(fieldValuesByFieldName), validUntil,
				ids.size());
		int written = dmsSolrDocumentWriteService.addGranteeToDmsSolrDocuments(ids, granteeUsersGuid);
		log.info("Dateifreigabe {}: {} DMS-Dokumente an {} von {}", grant.getId(), written, granteeUsersGuid,
				currentUsersProvider.getCurrentUsersGuid());
		return grant;
	}

	/**
	 * Entzieht die Freigabe: die guid aus allen DMS-Dokumenten, die die Bedingungen treffen und sie tragen, dann Status
	 * REVOKED. 404 ohne die Zeile, 409 wenn sie nicht mehr aktiv ist oder zu einem anderen Kern gehört.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrDocumentGrantedDocsPerCore revokeDmsDocumentGrant(Long id) {
		SolrDocumentGrantedDocsPerCore grant = documentGrantRegisterService.findActiveDocumentGrantForWrite(id);
		if (!DmsSolrDocumentFieldNames.CORE_KEY.equals(grant.getCoreKey())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Freigabe gehört zum Kern " + grant.getCoreKey());
		}
		Instant now = Instant.now();
		removeGranteeAndEndGrant(grant, GrantEntityStateService.REVOKED, now);
		return documentGrantRegisterService.endDocumentGrant(grant, GrantEntityStateService.REVOKED, now,
				currentUsersProvider.getCurrentUsersGuid());
	}

	/**
	 * Setzt alle aktiven DMS-Zeilen mit erreichtem Ablauf auf EXPIRED und nimmt die guid aus den Dokumenten. Eine
	 * abgelaufene Dateifreigabe wirkt bis dahin weiter, denn die Wahrheit steht im Dokument. Bis es den System-User
	 * gibt, stößt das der Admin an, danach ein Systemjob einmal am Tag.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public int expireDmsDocumentGrants() {
		Instant now = Instant.now();
		int expired = 0;
		for (SolrDocumentGrantedDocsPerCore grant : documentGrantRegisterService
				.activeDocumentGrantsOfCoreWithReachedValidUntil(DmsSolrDocumentFieldNames.CORE_KEY, now)) {
			removeGranteeAndEndGrant(grant, GrantEntityStateService.EXPIRED, now);
			documentGrantRegisterService.endDocumentGrant(grant, GrantEntityStateService.EXPIRED, now,
					currentUsersProvider.getCurrentUsersGuid());
			expired++;
		}
		return expired;
	}

	/**
	 * Die guid aus den Dokumenten dieser Freigabe nehmen. Dokumente, die eine andere noch wirksame Freigabe desselben
	 * Users im Kern DMS ebenfalls abdeckt, behalten die guid: Sonst bräche der Entzug von A die Freigabe B.
	 */
	private void removeGranteeAndEndGrant(SolrDocumentGrantedDocsPerCore grant, String status, Instant now) {
		UUID granteeUsersGuid = grant.getGranteeUsersGuid();
		List<String> ids = dmsExactFieldValueResolveService.resolveWritableDmsSolrDocumentIdsCarryingGrantee(
				readFieldValuesByFieldName(grant), granteeUsersGuid);
		Set<String> keep = new HashSet<>();
		for (SolrDocumentGrantedDocsPerCore other : documentGrantRegisterService
				.otherDocumentGrantsInForceOfSameUsersInCore(grant, now)) {
			keep.addAll(dmsExactFieldValueResolveService.resolveWritableDmsSolrDocumentIdsCarryingGrantee(
					readFieldValuesByFieldName(other), granteeUsersGuid));
		}
		List<String> remove = new ArrayList<>();
		for (String id : ids) {
			if (!keep.contains(id)) {
				remove.add(id);
			}
		}
		int written = dmsSolrDocumentWriteService.removeGranteeFromDmsSolrDocuments(remove, granteeUsersGuid);
		log.info("Dateifreigabe {} {}: guid aus {} von {} DMS-Dokumenten genommen, {} bleiben durch andere Freigaben",
				grant.getId(), status, written, ids.size(), ids.size() - remove.size());
	}

	private JsonNode readFieldValuesByFieldName(SolrDocumentGrantedDocsPerCore grant) {
		try {
			return objectMapper.readTree(grant.getFieldValues());
		} catch (JacksonException e) {
			throw new IllegalStateException("Dateifreigabe " + grant.getId() + ": fieldValues ist kein JSON", e);
		}
	}

}
