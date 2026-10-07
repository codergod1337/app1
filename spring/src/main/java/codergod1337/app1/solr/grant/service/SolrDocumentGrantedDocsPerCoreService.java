package codergod1337.app1.solr.grant.service;

import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.repository.SolrDocumentGrantedDocsPerCoreRepository;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Register der Dateifreigaben, kernübergreifend und reines Postgres (Vorgabe 2026-10-07): wer, für wen, Kern,
 * Bedingungen, wann, gültig bis, Status. Was in Solr passiert, die guid des Empfängers in userGrantedAccess setzen und
 * entfernen, macht je Kern dessen eigener Freigabe-Service, im DMS der DmsDocumentGrantService. Dieser Service fasst
 * Solr nie an. Die Wahrheit für den Zugriff ist das Dokument, die Zeile hier ist Protokoll und Grundlage für Entzug und
 * Ablauf. Nichts wird gelöscht, eine Zeile wechselt nur den Status (GrantEntityStateService).
 */
@Service
public class SolrDocumentGrantedDocsPerCoreService {

	/** Neueste zuerst, in der Datenbank wie in der Liste */
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "grantedAt", "id");

	private final SolrDocumentGrantedDocsPerCoreRepository repository;
	private final CurrentUsersProvider currentUsersProvider;

	public SolrDocumentGrantedDocsPerCoreService(SolrDocumentGrantedDocsPerCoreRepository repository,
			CurrentUsersProvider currentUsersProvider) {
		this.repository = repository;
		this.currentUsersProvider = currentUsersProvider;
	}

	/** Alle Zeilen aller Kerne, auch entzogene und abgelaufene, neueste zuerst: das Protokoll für die Maske. */
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
				if (isInForce(grant, now) && grant.getCoreKey().equals(coreKey)) {
					inForce.add(grant);
				}
			}
		}
		inForce.sort(newestFirst());
		return inForce;
	}

	/**
	 * Die Zeile einer neuen Freigabe, ACTIVE, mit dem current user als Freigebendem. Der Freigabe-Service des Kerns
	 * hat vorher geprüft, dass es den Empfänger gibt, dass validUntil in der Zukunft liegt und dass die Bedingungen
	 * Dokumente treffen; er schreibt danach die guid in Solr. Scheitert das, rollt diese Zeile mit zurück.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrDocumentGrantedDocsPerCore recordDocumentGrant(UUID granteeUsersGuid, String coreKey,
			String fieldValuesJson, Instant validUntil, int documentCount) {
		return repository.save(new SolrDocumentGrantedDocsPerCore(granteeUsersGuid, coreKey, fieldValuesJson,
				currentUsersProvider.getCurrentUsersGuid(), Instant.now(), validUntil, GrantEntityStateService.ACTIVE,
				documentCount));
	}

	/** Die Zeile zum Entziehen, aus der Datenbank: 404 ohne die Zeile, 409 wenn sie nicht mehr aktiv ist. */
	@PreAuthorize("hasRole('ADMIN')")
	public SolrDocumentGrantedDocsPerCore findActiveDocumentGrantForWrite(Long id) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		SolrDocumentGrantedDocsPerCore grant = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Dateifreigabe mit dieser id"));
		if (!GrantEntityStateService.ACTIVE.equals(grant.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Freigabe ist nicht mehr aktiv");
		}
		return grant;
	}

	/** Die aktiven Zeilen dieses Kerns, deren Ablauf erreicht ist: die Arbeitsliste des Ablaufs. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrDocumentGrantedDocsPerCore> activeDocumentGrantsOfCoreWithReachedValidUntil(String coreKey,
			Instant now) {
		List<SolrDocumentGrantedDocsPerCore> reached = new ArrayList<>();
		for (SolrDocumentGrantedDocsPerCore grant : repository.findByStatus(GrantEntityStateService.ACTIVE)) {
			if (grant.getCoreKey().equals(coreKey) && grant.getValidUntil() != null && !grant.getValidUntil().isAfter(now)) {
				reached.add(grant);
			}
		}
		return reached;
	}

	/**
	 * Die anderen wirksamen Freigaben desselben Empfängers im selben Kern: Dokumente, die eine davon ebenfalls abdeckt,
	 * behalten beim Entzug dieser Freigabe die guid.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrDocumentGrantedDocsPerCore> otherDocumentGrantsInForceOfSameUsersInCore(
			SolrDocumentGrantedDocsPerCore grant, Instant now) {
		List<SolrDocumentGrantedDocsPerCore> others = new ArrayList<>();
		for (SolrDocumentGrantedDocsPerCore other : repository.findByGranteeUsersGuid(grant.getGranteeUsersGuid())) {
			if (!other.getId().equals(grant.getId()) && other.getCoreKey().equals(grant.getCoreKey())
					&& isInForce(other, now)) {
				others.add(other);
			}
		}
		return others;
	}

	/** Statuswechsel auf REVOKED oder EXPIRED, mit wann und wer. Die guid aus Solr hat der Kern-Service schon genommen. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrDocumentGrantedDocsPerCore endDocumentGrant(SolrDocumentGrantedDocsPerCore grant, String status,
			Instant now, UUID byUsersGuid) {
		grant.changeStatus(status, now, byUsersGuid);
		return repository.save(grant);
	}

	private static Comparator<SolrDocumentGrantedDocsPerCore> newestFirst() {
		return Comparator.comparing(SolrDocumentGrantedDocsPerCore::getGrantedAt)
				.thenComparing(SolrDocumentGrantedDocsPerCore::getId).reversed();
	}

	/** Wirksam heißt Status ACTIVE und Ablauf noch nicht erreicht, dieselbe Regel wie bei der Feldfreigabe */
	private static boolean isInForce(SolrDocumentGrantedDocsPerCore grant, Instant now) {
		return GrantEntityStateService.ACTIVE.equals(grant.getStatus())
				&& (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
	}

}
