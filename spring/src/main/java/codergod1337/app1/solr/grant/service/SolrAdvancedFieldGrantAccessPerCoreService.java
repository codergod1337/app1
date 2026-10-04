package codergod1337.app1.solr.grant.service;

import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import codergod1337.app1.solr.grant.repository.SolrAdvancedFieldGrantAccessPerCoreRepository;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.service.UsersService;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Feldfreigaben: das Recht, in einem Kern nach einem Feld oder Hook aufzulösen, obwohl der User weder
 * {@code <KERN>_SUCHE} noch (für Hooks) {@code HOOK_FULL} hat (Vorgabe 2026-10-04). Ein erweitertes Feld wird damit auch
 * lesbar. Pro Kern, nie pro Dokument, nichts davon in Solr. Die aktiven Zeilen liegen im Speicher, weil die
 * Zugriffsrechnung sie bei jedem Request eines eingeschränkten Users braucht: unveränderliche Liste, nur ersetzt, neu
 * geladen nach jedem Commit, wie bei Hooks und Feldern.
 *
 * Nichts wird gelöscht, eine Freigabe wechselt nur den Status (GrantEntityStateService). Beim Lesen zählen nur ACTIVE
 * und nicht abgelaufen: Eine abgelaufene Zeile ist sofort wirkungslos, auch bevor der Ablauf sie auf EXPIRED setzt.
 *
 * Wer freigeben darf: vorerst ADMIN. Später eine eigene AR, z. B. für die Datenpflege.
 */
@Service
public class SolrAdvancedFieldGrantAccessPerCoreService {

	/** Neueste zuerst, in der Datenbank wie in der Liste */
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "grantedAt", "id");

	private final SolrAdvancedFieldGrantAccessPerCoreRepository repository;
	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;
	private final SolrHookService solrHookService;
	private final UsersService usersService;
	private final CurrentUsersProvider currentUsersProvider;

	/** Nur die aktiven Zeilen, für die Leseprüfung. Wird nur ersetzt, nie verändert. */
	private volatile List<SolrAdvancedFieldGrantAccessPerCore> activeGrants = List.of();

	public SolrAdvancedFieldGrantAccessPerCoreService(SolrAdvancedFieldGrantAccessPerCoreRepository repository,
			SolrCoreService solrCoreService, SolrFieldService solrFieldService, SolrHookService solrHookService,
			UsersService usersService, CurrentUsersProvider currentUsersProvider) {
		this.repository = repository;
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
		this.solrHookService = solrHookService;
		this.usersService = usersService;
		this.currentUsersProvider = currentUsersProvider;
	}

	@PostConstruct
	void loadActiveGrants() {
		reloadActiveGrants();
	}

	/**
	 * Die erweiterten Felder und Hooks, die diesem User in diesem Kern zusätzlich zu den Minimum-Feldern offenstehen:
	 * aktive, nicht abgelaufene Zeilen aus dem Speicher. Für die Leseprüfung, ohne @PreAuthorize: Der Aufrufer fragt nach
	 * sich selbst.
	 */
	public Set<String> activeGrantedAdvancedFieldNamesOfUsersInCore(UUID granteeUsersGuid, String coreKey) {
		Instant now = Instant.now();
		Set<String> fieldNames = new HashSet<>();
		for (SolrAdvancedFieldGrantAccessPerCore grant : activeGrants) {
			if (grant.getGranteeUsersGuid().equals(granteeUsersGuid) && grant.getCoreKey().equals(coreKey)
					&& isInForce(grant, now)) {
				fieldNames.add(grant.getFieldName());
			}
		}
		return fieldNames;
	}

	/** Alle Zeilen, auch entzogene und abgelaufene, neueste zuerst: das Protokoll für die Maske. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrAdvancedFieldGrantAccessPerCore> getAllFieldGrants() {
		return repository.findAll(NEWEST_FIRST);
	}

	/**
	 * Die Feldfreigaben des current users in diesem Kern, auch entzogene und abgelaufene, neueste zuerst: der Tab „Meine
	 * Freigaben“. Ohne @PreAuthorize, der User fragt nach sich selbst, die guid kommt aus dem Token.
	 */
	public List<SolrAdvancedFieldGrantAccessPerCore> fieldGrantsOfCurrentUserInCore(String coreKey) {
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		List<SolrAdvancedFieldGrantAccessPerCore> own = new ArrayList<>();
		for (SolrAdvancedFieldGrantAccessPerCore grant : repository.findByGranteeUsersGuid(usersGuid)) {
			if (grant.getCoreKey().equals(coreKey)) {
				own.add(grant);
			}
		}
		own.sort(Comparator.comparing(SolrAdvancedFieldGrantAccessPerCore::getGrantedAt)
				.thenComparing(SolrAdvancedFieldGrantAccessPerCore::getId).reversed());
		return own;
	}

	/**
	 * Gibt dem User die Felder dieses Kerns frei, eine Zeile je Feld. 404 bei unbekanntem User oder Kern, 400 bei
	 * einem Namen, der weder auflösbares Feld des Kerns noch Hook ist, bei einer schon aktiven Freigabe desselben Feldes
	 * und bei einem Ablauf in der Vergangenheit. Alles oder nichts: eine Transaktion.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<SolrAdvancedFieldGrantAccessPerCore> grantAdvancedFieldsToUsers(UUID granteeUsersGuid, String coreKey,
			List<String> fieldNames, Instant validUntil) {
		usersService.getUsersByGuid(granteeUsersGuid);
		solrCoreService.getSolrCoreByKey(coreKey);
		requireValidUntilInFuture(validUntil);
		if (fieldNames == null || fieldNames.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldNames fehlt oder ist leer");
		}
		Set<String> grantableFieldNames = grantableAdvancedFieldAndHookNamesOfCore(coreKey);
		Set<String> alreadyGranted = activeGrantedAdvancedFieldNamesOfUsersInCore(granteeUsersGuid, coreKey);
		List<String> problems = new ArrayList<>();
		for (String fieldName : new LinkedHashSet<>(fieldNames)) {
			if (!grantableFieldNames.contains(fieldName)) {
				problems.add(fieldName + ": kein auflösbares Feld des Kerns " + coreKey + " und kein Hook");
			} else if (alreadyGranted.contains(fieldName)) {
				problems.add(fieldName + ": schon freigegeben");
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		Instant now = Instant.now();
		UUID grantedByUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		List<SolrAdvancedFieldGrantAccessPerCore> created = new ArrayList<>();
		for (String fieldName : new LinkedHashSet<>(fieldNames)) {
			created.add(new SolrAdvancedFieldGrantAccessPerCore(granteeUsersGuid, coreKey, fieldName,
					grantedByUsersGuid, now, validUntil, GrantEntityStateService.ACTIVE));
		}
		List<SolrAdvancedFieldGrantAccessPerCore> saved = repository.saveAll(created);
		reloadActiveGrantsAfterCommit();
		return saved;
	}

	/** Entzieht die Freigabe: Status REVOKED, wer und wann. 404 ohne die Zeile, 409 wenn sie nicht mehr aktiv ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrAdvancedFieldGrantAccessPerCore revokeFieldGrant(Long id) {
		SolrAdvancedFieldGrantAccessPerCore grant = findForWrite(id);
		if (!GrantEntityStateService.ACTIVE.equals(grant.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Freigabe ist nicht mehr aktiv");
		}
		grant.changeStatus(GrantEntityStateService.REVOKED, Instant.now(), currentUsersProvider.getCurrentUsersGuid());
		SolrAdvancedFieldGrantAccessPerCore saved = repository.save(grant);
		reloadActiveGrantsAfterCommit();
		return saved;
	}

	/**
	 * Setzt alle aktiven Zeilen mit erreichtem Ablauf auf EXPIRED. Wirkungslos sind sie schon vorher (isInForce). Bis es
	 * den System-User gibt, stößt das der Admin an, danach ein Systemjob einmal am Tag. Gibt zurück, wie viele es waren.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public int expireFieldGrants() {
		Instant now = Instant.now();
		UUID byUsersGuid = currentUsersProvider.getCurrentUsersGuid();
		List<SolrAdvancedFieldGrantAccessPerCore> expired = new ArrayList<>();
		for (SolrAdvancedFieldGrantAccessPerCore grant : repository.findByStatus(GrantEntityStateService.ACTIVE)) {
			if (grant.getValidUntil() != null && !grant.getValidUntil().isAfter(now)) {
				grant.changeStatus(GrantEntityStateService.EXPIRED, now, byUsersGuid);
				expired.add(grant);
			}
		}
		repository.saveAll(expired);
		reloadActiveGrantsAfterCommit();
		return expired.size();
	}

	/**
	 * Die Namen, die sich in diesem Kern freigeben lassen: jedes indexierte Feld außer {@code id} (danach löst jeder auf)
	 * und alle Hooks. Auch Minimum-Felder, denn die Freigabe ist ein Auflöserecht, lesen kann sie jeder ohnehin. Für
	 * Maske und Prüfung.
	 */
	public Set<String> grantableAdvancedFieldAndHookNamesOfCore(String coreKey) {
		Set<String> names = new LinkedHashSet<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(coreKey)) {
			if (solrField.isIndexed() && !SolrDocumentService.ID_FIELD_NAME.equals(solrField.getName())) {
				names.add(solrField.getName());
			}
		}
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			names.add(solrHook.getKey());
		}
		return names;
	}

	private static boolean isInForce(SolrAdvancedFieldGrantAccessPerCore grant, Instant now) {
		return GrantEntityStateService.ACTIVE.equals(grant.getStatus())
				&& (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
	}

	private static void requireValidUntilInFuture(Instant validUntil) {
		if (validUntil != null && !validUntil.isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validUntil liegt in der Vergangenheit");
		}
	}

	/** Zum Schreiben aus der Datenbank, nicht aus dem Speicher: so bleibt die Entity an der Transaktion. */
	private SolrAdvancedFieldGrantAccessPerCore findForWrite(Long id) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Feldfreigabe mit dieser id"));
	}

	/** Nach dem Commit neu laden, nie davor. Gleiches Muster wie im SolrHookService. */
	private void reloadActiveGrantsAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadActiveGrants();
				}
			});
		} else {
			reloadActiveGrants();
		}
	}

	/** Nur die aktiven Zeilen in den Speicher, mehr braucht die Leseprüfung nicht. */
	private void reloadActiveGrants() {
		List<SolrAdvancedFieldGrantAccessPerCore> active = new ArrayList<>(
				repository.findByStatus(GrantEntityStateService.ACTIVE));
		active.sort(Comparator.comparing(SolrAdvancedFieldGrantAccessPerCore::getId));
		activeGrants = List.copyOf(active);
	}

}
